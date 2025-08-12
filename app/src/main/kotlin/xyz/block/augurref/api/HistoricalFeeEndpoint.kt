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
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Configure historical fee estimate endpoint
 */
fun Route.configureHistoricalFeesEndpoint(mempoolCollector: MempoolCollector) {
  val logger = LoggerFactory.getLogger("xyz.block.augurref.api.HistoricalFeeEstimateEndpoint")

  get("/historical_fee") {
    logger.info("Received request for historical fee estimates")

    // Extract date param from query parameters
    val dateParam = call.request.queryParameters["date"]

    if (dateParam == null) {
      call.respondText(
        "Date parameter is required",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    // Validate and parse the date
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

    // Fetch historical fee estimate based on date
    val feeEstimate = if (date != null) {
      logger.info("Fetching historical fee estimate for date: $date")
      mempoolCollector.getFeeEstimateForDate(date)
    } else {
      logger.warn("date is null")
      call.respondText(
        "Failed to parse date",
        status = HttpStatusCode.InternalServerError,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    if (feeEstimate == null) {
      logger.warn("No historical fee estimates available for $date")
      call.respondText(
        "No historical fee estimates available for $date",
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

  get("/historical_fees") {
    logger.info("Received request for historical fee estimates")

    // Extract params from query start_date, end_date, interval (seconds)
    val startDateParam = call.request.queryParameters["start_date"]
    val endDateParam = call.request.queryParameters["end_date"]
    val intervalParam = call.request.queryParameters["interval"]
    val interval: Int = intervalParam?.toIntOrNull() ?: 3600

    if (startDateParam == null) {
      call.respondText(
        "start_date parameter is required",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    if (endDateParam == null) {
      call.respondText(
        "end_date parameter is required",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    // Validate and parse the date
    val startDate = try {
      startDateParam?.let { LocalDateTime.parse(it) }
    } catch (e: DateTimeParseException) {
      logger.warn("Invalid date format: $startDateParam")
      call.respondText(
        "Invalid date format. Use YYYY-MM-DDTHH:MM:SS",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }
    val endDate = try {
      endDateParam?.let { LocalDateTime.parse(it) }
    } catch (e: DateTimeParseException) {
      logger.warn("Invalid date format: $endDateParam")
      call.respondText(
        "Invalid date format. Use YYYY-MM-DDTHH:MM:SS",
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    // Fetch historical fee estimate based on date
    val feeEstimate = if (startDate != null && endDate != null && interval != null) {
      logger.info(
        "Fetching historical fee estimate for start_date: $startDate to " +
          "end_date: $endDate for intervals: $interval seconds",
      )
      mempoolCollector.getFeeEstimateForDateRange(startDate, endDate, interval)
    } else {
      logger.warn("internal error")
      call.respondText(
        "internal error",
        status = HttpStatusCode.InternalServerError,
        contentType = ContentType.Text.Plain,
      )
      return@get
    }

    if (feeEstimate == null) {
      logger.warn(
        "No historical fee estimates available for start_date $startDate to " +
          "end_date: $endDate for interval: $interval seconds",
      )
      call.respondText(
        "No historical fee estimates available for start_date $startDate to " +
          "end_date: $endDate for interval: $interval seconds",
        status = HttpStatusCode.ServiceUnavailable,
        contentType = ContentType.Text.Plain,
      )
    } else {
      var mutableResponse = mutableListOf<FeeEstimateResponse>()
      logger.info("Transforming historical fee estimates for response")
      for (estimate in feeEstimate) {
        val response = transformFeeEstimate(estimate)
        mutableResponse.add(response)
      }
      logger.debug("Returning historical fee estimates")
      call.respond(mutableResponse)
    }
  }
}
