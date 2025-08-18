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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.block.augur.FeeEstimate
import xyz.block.augurref.service.MempoolCollector
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeeEstimateEndpointTest {
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
        configureFeesEndpoint(mockMempoolCollector)
      }
    }
  }

  @Test
  fun `should return fee estimates when available`() = testApplication {
    val fixedInstant = Instant.parse("2025-01-15T10:00:00.123Z")
    val mockFeeEstimate = createMockFeeEstimate(fixedInstant)
    every { mockMempoolCollector.getLatestFeeEstimate() } returns mockFeeEstimate

    configureTestApplication()

    client.get("/fees").apply {
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

    verify { mockMempoolCollector.getLatestFeeEstimate() }
  }

  @Test
  fun `should return 503 when no estimates available`() = testApplication {
    every { mockMempoolCollector.getLatestFeeEstimate() } returns null

    configureTestApplication()

    client.get("/fees").apply {
      assertEquals(HttpStatusCode.ServiceUnavailable, status)
      assertEquals("No fee estimates available yet", bodyAsText())
    }

    verify { mockMempoolCollector.getLatestFeeEstimate() }
  }

  @Test
  fun `should return estimates for valid num_blocks parameter`() = testApplication {
    val fixedInstant = Instant.parse("2025-01-15T12:30:45.678Z")
    val mockFeeEstimate = createMockFeeEstimate(fixedInstant)
    every { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(3.0) } returns mockFeeEstimate

    configureTestApplication()

    client.get("/fees/target/3").apply {
      assertEquals(HttpStatusCode.OK, status)
      val responseBody = bodyAsText()
      val response: FeeEstimateResponse = objectMapper.readValue(responseBody)

      assertEquals(fixedInstant, response.mempoolUpdateTime)
      assertNotNull(response.estimates)
    }

    verify { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(3.0) }
  }

  @ParameterizedTest
  @ValueSource(strings = ["abc"])
  fun `should return 400 for invalid num_blocks parameter`(invalidValue: String) = testApplication {
    configureTestApplication()

    client.get("/fees/target/$invalidValue").apply {
      assertEquals(HttpStatusCode.BadRequest, status)
      assertEquals("Invalid or missing number of blocks", bodyAsText())
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["-1", "0"])
  fun `should handle edge case num_blocks parameters`(edgeValue: String) = testApplication {
    val numBlocks = edgeValue.toDouble()
    // Mock return null for edge cases (could be valid behavior)
    every { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(numBlocks) } returns null

    configureTestApplication()

    client.get("/fees/target/$edgeValue").apply {
      assertEquals(HttpStatusCode.ServiceUnavailable, status)
      assertEquals("No fee estimates available yet", bodyAsText())
    }

    verify { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(numBlocks) }
  }

  @Test
  fun `should return 404 for empty num_blocks parameter`() = testApplication {
    configureTestApplication()

    // Empty string in URL path results in /fees/target/ which doesn't match the route
    client.get("/fees/target/").apply {
      assertEquals(HttpStatusCode.NotFound, status)
    }
  }

  @Test
  fun `should return 503 when no estimates available for target`() = testApplication {
    every { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(2.0) } returns null

    configureTestApplication()

    client.get("/fees/target/2").apply {
      assertEquals(HttpStatusCode.ServiceUnavailable, status)
      assertEquals("No fee estimates available yet", bodyAsText())
    }

    verify { mockMempoolCollector.getLatestFeeEstimateForBlockTarget(2.0) }
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
