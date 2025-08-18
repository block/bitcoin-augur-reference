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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import xyz.block.augur.FeeEstimate
import xyz.block.augurref.service.MempoolCollector
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoricalFeeEstimateEndpointTest {
  private val mockMempoolCollector = mockk<MempoolCollector>()
  private val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    registerModule(JavaTimeModule())
  }

  companion object {
    object TestData {
      object FeeRates {
        const val LOW_BLOCK1 = 10.5
        const val HIGH_BLOCK1 = 15.25
        const val LOW_BLOCK6 = 5.75
        const val HIGH_BLOCK6 = 8.1234
      }

      object Probabilities {
        const val MEDIUM = 0.5
        const val HIGH = 0.9
      }

      object BlockTargets {
        const val TARGET_1 = 1
        const val TARGET_6 = 6
      }
    }
  }

  /**
   * Configures the test application with the same JSON serialization
   * settings as the main server and sets up the fees endpoint routing.
   */
  private fun TestApplicationBuilder.configureTestApplication() {
    application {
      // Configure JSON serialization (same as main server)
      install(ContentNegotiation) {
        jackson {
          registerModule(JavaTimeModule())
          disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          enable(SerializationFeature.INDENT_OUTPUT)
        }
      }

      routing {
        configureHistoricalFeesEndpoint(mockMempoolCollector)
      }
    }
  }

  @Test
  fun `should return historical fee estimates when available`() = testApplication {
    val fixedInstant = Instant.parse("2025-01-15T10:00:00.123Z")
    val fixedInstantUnixTimestamp = fixedInstant.toEpochMilli()
    val mockFeeEstimate = createMockFeeEstimate(fixedInstant)
    every { mockMempoolCollector.getFeeEstimateForTimestamp(fixedInstantUnixTimestamp) } returns mockFeeEstimate

    configureTestApplication()

    client.get("/historical_fee?timestamp=$fixedInstantUnixTimestamp").apply {
      assertEquals(HttpStatusCode.OK, status)
      val responseBody = bodyAsText()
      val response: FeeEstimateResponse = objectMapper.readValue(responseBody)

      assertEquals(fixedInstant, response.mempoolUpdateTime)
      assertEquals(2, response.estimates.size)
      assertTrue(response.estimates.containsKey("1"))
      assertTrue(response.estimates.containsKey("6"))

      val firstBlock = response.estimates["1"]!!
      assertEquals(2, firstBlock.probabilities.size)
      assertEquals(TestData.FeeRates.LOW_BLOCK1, firstBlock.probabilities["0.50"]?.feeRate)
      assertEquals(TestData.FeeRates.HIGH_BLOCK1, firstBlock.probabilities["0.90"]?.feeRate)
    }

    verify { mockMempoolCollector.getFeeEstimateForTimestamp(fixedInstantUnixTimestamp) }
  }

  @Test
  fun `should return 503 when no historical estimates available`() = testApplication {
    val fixedInstant = Instant.parse("2025-01-15T10:00:00.123Z")
    val fixedInstantUnixTimestamp = fixedInstant.toEpochMilli()
    every { mockMempoolCollector.getFeeEstimateForTimestamp(fixedInstantUnixTimestamp) } returns null

    configureTestApplication()

    client.get("/historical_fee?timestamp=$fixedInstantUnixTimestamp").apply {
      assertEquals(HttpStatusCode.ServiceUnavailable, status)
      assertEquals("No historical fee estimates available for $fixedInstantUnixTimestamp", bodyAsText())
    }

    verify { mockMempoolCollector.getFeeEstimateForTimestamp(fixedInstantUnixTimestamp) }
  }

  @Test
  fun `should return 400 when timestamp param is malformed or null`() = testApplication {
    val fixedInstant = Instant.parse("2025-01-15T10:00:00.123Z")
    val fixedInstantUnixTimestamp = fixedInstant.toEpochMilli()
    every { mockMempoolCollector.getFeeEstimateForTimestamp(fixedInstantUnixTimestamp) } returns null

    configureTestApplication()

    client.get("/historical_fee?timestamp=$fixedInstant").apply {
      assertEquals(HttpStatusCode.BadRequest, status)
      assertEquals("Failed to parse timestamp, please input a unix timestamp", bodyAsText())
    }
    client.get("/historical_fee").apply {
      assertEquals(HttpStatusCode.BadRequest, status)
      assertEquals("timestamp parameter is required", bodyAsText())
    }
  }

  private fun createMockFeeEstimate(timestamp: Instant): FeeEstimate {
    // Create mock data using the actual augur library structure
    val mockFeeEstimate = mockk<FeeEstimate>()

    // Mock the properties that are accessed in the transformation
    every { mockFeeEstimate.timestamp } returns timestamp

    // Create mock estimates map - this is what the transformation function expects
    val mockBlockTarget1 = mockk<xyz.block.augur.BlockTarget>()
    every { mockBlockTarget1.probabilities } returns mapOf(
      TestData.Probabilities.MEDIUM to TestData.FeeRates.LOW_BLOCK1,
      TestData.Probabilities.HIGH to TestData.FeeRates.HIGH_BLOCK1,
    )

    val mockBlockTarget6 = mockk<xyz.block.augur.BlockTarget>()
    every { mockBlockTarget6.probabilities } returns mapOf(
      TestData.Probabilities.MEDIUM to TestData.FeeRates.LOW_BLOCK6,
      TestData.Probabilities.HIGH to TestData.FeeRates.HIGH_BLOCK6,
    )

    every { mockFeeEstimate.estimates } returns mapOf(
      TestData.BlockTargets.TARGET_1 to mockBlockTarget1,
      TestData.BlockTargets.TARGET_6 to mockBlockTarget6,
    )

    return mockFeeEstimate
  }
}
