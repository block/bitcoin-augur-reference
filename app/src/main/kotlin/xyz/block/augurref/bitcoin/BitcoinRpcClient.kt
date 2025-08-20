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

package xyz.block.augurref.bitcoin

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import xyz.block.augur.MempoolTransaction
import xyz.block.augurref.config.BitcoinRpcConfig
import kotlin.math.roundToLong

/**
 * Bitcoin RPC client for fetching mempool data
 */
class BitcoinRpcClient(private val config: BitcoinRpcConfig) {
  private val logger = LoggerFactory.getLogger(BitcoinRpcClient::class.java)
  private val client = OkHttpClient()
  private val mapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  /**
   * Get current blockchain height and mempool entries directly from Bitcoin Core
   * @return Pair of (blockHeight, list of Augur-compatible MempoolTransactions)
   */
  fun getHeightAndMempoolTransactions(): Pair<Int, List<MempoolTransaction>> {
    logger.debug("Fetching blockchain height and mempool data")

    val requestBody = mapper.writeValueAsString(
      listOf(
        mapOf(
          "jsonrpc" to "1.0",
          "id" to "blockchain-info",
          "method" to "getblockchaininfo",
          "params" to emptyList<String>(),
        ),
        mapOf(
          "jsonrpc" to "1.0",
          "id" to "mempool",
          "method" to "getrawmempool",
          "params" to listOf(true),
        ),
        mapOf(
          "jsonrpc" to "1.0",
          "id" to "mempool-info",
          "method" to "getmempoolinfo",
          "params" to emptyList<String>(),
        ),
      ),
    )

    val request = Request.Builder()
      .url(config.url)
      .post(requestBody.toRequestBody(jsonMediaType))
      .header("Authorization", Credentials.basic(config.username, config.password))
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw Exception("Failed to get batch response: ${response.code} ${response.message}")
      }

      val results: List<Map<String, Any>> = mapper.readValue(
        response.body?.byteStream() ?: throw Exception("Empty response"),
      )

      // Parse blockchain info
      val blockchainResponse: BlockchainInfoResponse = mapper.convertValue(
        results[0],
        BlockchainInfoResponse::class.java,
      )
      if (blockchainResponse.error != null) {
        throw Exception("RPC error (blockchain): ${blockchainResponse.error}")
      }
      val height = blockchainResponse.result?.blocks ?: throw Exception("No height in response")

      // Parse mempool and convert directly to Augur format
      val mempoolResponse: MempoolResponse = mapper.convertValue(results[1], MempoolResponse::class.java)
      if (mempoolResponse.error != null) {
        throw Exception("RPC error (mempool): ${mempoolResponse.error}")
      }
      val mempoolInfoResponse: MempoolInfoResponse = mapper.convertValue(results[2], MempoolInfoResponse::class.java)
      if (mempoolInfoResponse.error != null) {
        throw Exception("RPC error (mempool-info): ${mempoolInfoResponse.error}")
      }
      if (mempoolInfoResponse.result?.loaded == false) {
        throw Exception("RPC error (mempool-info): mempool is not loaded")
      }

      // Convert directly from Bitcoin Core MempoolEntry to Augur MempoolTransaction
      val transactions = mempoolResponse.result?.map { (_, entry) ->
        MempoolTransaction(
          weight = entry.weight.toLong(),
          fee = (entry.fees.base * 100000000).roundToLong(),
        )
      }?.toList() ?: emptyList()

      logger.debug("Fetched blockchain height: $height and ${transactions.size} mempool transactions")
      return Pair(height, transactions)
    }
  }
}

data class MempoolEntry(
  val weight: Int,
  val vsize: Int,
  val fees: Fees,
)

data class Fees(
  val base: Double,
)

data class MempoolResponse(
  val result: Map<String, MempoolEntry>?,
  val error: Any?,
)

data class BlockchainInfo(
  val chain: String,
  val blocks: Int,
  val headers: Int,
  val bestblockhash: String,
  val difficulty: Double,
  val mediantime: Long,
  val verificationprogress: Double,
  val initialblockdownload: Boolean,
  val size_on_disk: Long,
)

data class BlockchainInfoResponse(
  val result: BlockchainInfo?,
  val error: Any?,
)

data class MempoolInfo(
  val loaded: Boolean,
  val size: Int,
  val bytes: Int,
  val usage: Int,
  val totalFee: Double,
  val maxmempool: Int,
  val mempoolminfee: Double,
  val minrelaytxfee: Double,
  val incrementalrelayfee: Double,
  val unbroadcastcount: Int,
  val fullrbf: Boolean,
)

data class MempoolInfoResponse(
  val result: MempoolInfo?,
  val error: Any?,
)
