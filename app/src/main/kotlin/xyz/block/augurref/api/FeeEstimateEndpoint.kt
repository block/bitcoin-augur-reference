/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.block.augurref.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.slf4j.LoggerFactory
import xyz.block.augur.FeeEstimate
import xyz.block.augurref.service.MempoolCollector
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Data model for fee estimation response
 */
data class FeeEstimateResponse(
  @JsonProperty("mempool_update_time")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
  val mempoolUpdateTime: Instant,
  val estimates: Map<String, BlockTarget>,
)

data class BlockTarget(
  val probabilities: Map<String, Probability>,
)

data class Probability(
  @JsonProperty("fee_rate")
  val feeRate: Double,
)

/**
 * Configure fee estimate endpoint
 */
fun Route.configureFeesEndpoint(mempoolCollector: MempoolCollector) {
  val logger = LoggerFactory.getLogger("xyz.block.augurref.api.FeeEstimateEndpoint")

  get("/fees") {
    logger.info("Received request for fee estimates")

    // Extract date param from query parameters
    val dateParam = call.request.queryParameters["date"]

    // Validate and parse the date if provided
    val date = try {
      dateParam?.let { LocalDateTime.parse(it) }
    } catch (e: DateTimeParseException) {
      logger.warn("Invalid date format: $dateParam")
      call.respondText(
        "Invalid date format. Use YYYY-MM-DDTHH:MM:SS",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    // Fetch fee estimate based on date if provided, otherwise use latest
    val feeEstimate = if (date != null) {
      logger.info("Fetching fee estimate for date: $date")
      mempoolCollector.getFeeEstimateForDate(date)
    } else {
      logger.info("Fetching latest fee estimate")
      mempoolCollector.getLatestFeeEstimate()
    }

    if (feeEstimate == null) {
      logger.warn("No fee estimates available yet for ${date ?: "latest"}")
      call.respondText(
        "No fee estimates available yet for ${date ?: "latest"}",
        status = HttpStatusCode.ServiceUnavailable,
        contentType = ContentType.Text.Plain,
      )
    } else {
      logger.info("Transforming fee estimates for response")
      val response = transformFeeEstimate(feeEstimate)
      logger.debug("Returning fee estimates with ${response.estimates.size} targets")
      call.respond(response)
    }
  }

  get("/fees/target/{num_blocks}") {
    val numBlocks = call.parameters["num_blocks"]?.toDoubleOrNull()
    if (numBlocks == null) {
      logger.warn("Invalid or missing num_blocks parameter")
      call.respondText(
        "Invalid or missing number of blocks",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }
    logger.info("Received request for fee estimates targeting {numBlocks} blocks")
    val currentEstimate = mempoolCollector.getLatestFeeEstimateForBlockTarget(numBlocks)

    if (currentEstimate == null) {
      logger.warn("No fee estimates available yet")
      call.respondText(
        "No fee estimates available yet",
        status = HttpStatusCode.ServiceUnavailable,
        contentType = ContentType.Text.Plain,
      )
    } else {
      logger.info("Transforming fee estimates for response")
      val response = transformFeeEstimate(currentEstimate)
      logger.debug("Returning fee estimates with ${response.estimates.size} targets")
      call.respond(response)
    }
  }
}

/**
 * Transform FeeEstimate into the response format
 */
fun transformFeeEstimate(feeEstimate: FeeEstimate): FeeEstimateResponse {
  // Get the latest fee estimates
  val estimatesResponse = feeEstimate.estimates.mapKeys {
    it.key.toString()
  }.mapValues { (_, blockTarget) ->
    BlockTarget(
      probabilities = blockTarget.probabilities.mapKeys {
        String.format("%.2f", it.key)
      }.mapValues {
        Probability(String.format("%.4f", it.value).toDouble())
      },
    )
  }

  return FeeEstimateResponse(
    mempoolUpdateTime = feeEstimate.timestamp,
    estimates = estimatesResponse,
  )
}
