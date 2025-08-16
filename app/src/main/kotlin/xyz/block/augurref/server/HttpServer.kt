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

package xyz.block.augurref.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import xyz.block.augurref.api.configureFeesEndpoint
import xyz.block.augurref.api.configureHistoricalFeesEndpoint
import xyz.block.augurref.config.ServerConfig
import xyz.block.augurref.service.MempoolCollector

/**
 * Manages the HTTP server
 */
class HttpServer(
  private val serverConfig: ServerConfig,
  private val mempoolCollector: MempoolCollector,
) {
  private val logger = LoggerFactory.getLogger(HttpServer::class.java)
  private var server: io.ktor.server.engine.ApplicationEngine? = null

  /**
   * Start the HTTP server
   */
  fun start() {
    logger.info("Starting HTTP server on ${serverConfig.host}:${serverConfig.port}")

    server = embeddedServer(Netty, host = serverConfig.host, port = serverConfig.port) {
      // Configure JSON serialization
      install(ContentNegotiation) {
        jackson {
          registerModule(JavaTimeModule())
          disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          enable(SerializationFeature.INDENT_OUTPUT)
        }
      }

      // Configure routes
      routing {
        configureFeesEndpoint(mempoolCollector)
        configureHistoricalFeesEndpoint(mempoolCollector)
      }
    }.start(wait = false)

    logger.info("HTTP server started at http://${serverConfig.host}:${serverConfig.port}/fees")
  }

  /**
   * Stop the HTTP server
   */
  fun stop() {
    logger.info("Stopping HTTP server")
    server?.stop(1000, 5000)
    server = null
    logger.info("HTTP server stopped")
  }
}
