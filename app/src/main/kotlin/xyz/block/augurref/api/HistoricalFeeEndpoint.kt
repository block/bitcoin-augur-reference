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

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.slf4j.LoggerFactory
import xyz.block.augurref.service.MempoolCollector

/**
 * Configure historical fee estimate endpoint
 */
fun Route.configureHistoricalFeesEndpoint(mempoolCollector: MempoolCollector) {
  val logger = LoggerFactory.getLogger("xyz.block.augurref.api.HistoricalFeeEstimateEndpoint")

  get("/historical_fee") {
    logger.info("Received request for historical fee estimates")

    // Extract unix timestamp param from query parameters
    val timestampParam = call.request.queryParameters["timestamp"]

    if (timestampParam == null) {
      call.respondText(
        "timestamp parameter is required",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }
    val timestamp = timestampParam.toLongOrNull()

    // Fetch historical fee estimate based on unix timestamp
    val feeEstimate = if (timestamp != null) {
      logger.info("Fetching historical fee estimate for timestamp: $timestamp")
      mempoolCollector.getFeeEstimateForTimestamp(timestamp)
    } else {
      logger.warn("timestamp is null")
      call.respondText(
        "Failed to parse timestamp, please input a unix timestamp",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    if (feeEstimate == null) {
      logger.warn("No historical fee estimates available for $timestamp")
      call.respondText(
        "No historical fee estimates available for $timestamp",
        status = HttpStatusCode.ServiceUnavailable,
        contentType = ContentType.Text.Plain,
      )
    } else {
      logger.info("Transforming historical fee estimates for response")
      val response = transformFeeEstimate(feeEstimate)
      logger.debug("Returning historical fee estimates with ${response.estimates.size} targets")
      call.respond(response)
    }
  }
}
