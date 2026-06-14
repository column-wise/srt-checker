package io.github.columnwise.trainchecker.data.api.srt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetFunnelHelper @Inject constructor(
    private val client: OkHttpClient,
) {
    var baseUrl: String = "https://nf.letskorail.com"

    private var cachedKey: String? = null
    private var lastFetchTime: Long = 0
    private val cacheTtlMs = 48_000L

    suspend fun getToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedKey?.let { if (now - lastFetchTime < cacheTtlMs) return@withContext it }

        val (status, key, nwait, ip) = makeRequest("5101")
        var curStatus = status
        var curKey = key
        var curIp = ip

        while (curStatus == "201") {
            delay(1000)
            val r = makeRequest("5002", curIp, curKey)
            curStatus = r[0]; curKey = r[1]; curIp = r[3]
        }

        makeRequest("5004", curIp, curKey)

        cachedKey = curKey
        lastFetchTime = System.currentTimeMillis()
        curKey
    }

    fun clear() { cachedKey = null; lastFetchTime = 0 }

    private fun makeRequest(opcode: String, ip: String? = null, key: String? = null): List<String> {
        val baseHost = java.net.URI(baseUrl).host
        val url = if (ip != null && ip != baseHost) "https://$ip/ts.wseq" else "$baseUrl/ts.wseq"
        val params = buildString {
            append("opcode=$opcode&nfid=0&prefix=NetFunnel.gRtype%3D$opcode%3B&js=true")
            append("&${System.currentTimeMillis()}=")
            when (opcode) {
                "5101" -> append("&sid=service_1&aid=act_10")
                "5002" -> append("&sid=service_1&aid=act_10&key=${key}&ttl=1")
                "5004" -> append("&key=${key}")
            }
        }
        val req = Request.Builder().url("$url?$params").get().build()
        val body = client.newCall(req).execute().body!!.string()
        return parse(body)
    }

    private fun parse(response: String): List<String> {
        val match = Regex("NetFunnel\\.gControl\\.result='([^']+)'").find(response)
            ?: return listOf("200", "", "0", "nf.letskorail.com")
        val parts = match.groupValues[1].split(":", limit = 3)
        val status = parts[0]
        val paramMap = if (parts.size > 2) parts[2].split("&")
            .filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
        else emptyMap()
        return listOf(
            status,
            paramMap["key"] ?: "",
            paramMap["nwait"] ?: "0",
            paramMap["ip"] ?: "nf.letskorail.com",
        )
    }
}
