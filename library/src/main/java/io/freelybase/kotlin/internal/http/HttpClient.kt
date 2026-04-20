package io.freelybase.kotlin.internal.http

import io.freelybase.kotlin.FreelyBase
import io.freelybase.kotlin.FreelyBaseException
import io.freelybase.kotlin.internal.crypto.CryptoManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

internal object HttpClient {

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val WRITE_METHODS = listOf("POST", "PUT", "PATCH", "DELETE")
    private val SKIP_ENCRYPT_PREFIXES = listOf("/crypto/", "/token")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun shouldEncrypt(method: String, path: String, hasBody: Boolean): Boolean {
        if (!FreelyBase.enableEncryption || !CryptoManager.isReady() || !hasBody) return false
        if (method.uppercase() !in WRITE_METHODS) return false
        return SKIP_ENCRYPT_PREFIXES.none { path.startsWith(it) }
    }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): Map<String, Any?> =
        request("GET", path, null, params)

    suspend fun post(path: String, body: Any? = null): Map<String, Any?> =
        request("POST", path, body)

    suspend fun put(path: String, body: Any? = null): Map<String, Any?> =
        request("PUT", path, body)

    suspend fun delete(path: String): Map<String, Any?> =
        request("DELETE", path, null)

    private suspend fun request(
        method: String,
        path: String,
        body: Any?,
        params: Map<String, String> = emptyMap()
    ): Map<String, Any?> = withContext(Dispatchers.IO) {

        FreelyBase.requireInitialized()

        val urlSb = StringBuilder(FreelyBase.baseUrl).append(path)
        if (params.isNotEmpty()) {
            urlSb.append("?")
            urlSb.append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
        val url = urlSb.toString()
        val bodyJson = if (body != null) gson.toJson(body) else null
        val encrypt = shouldEncrypt(method, path, bodyJson != null)

        var aesKey: SecretKey? = null
        val requestBody = when {
            bodyJson == null -> null
            encrypt -> {
                val (envelope, key) = CryptoManager.encryptRequest(bodyJson, method.uppercase(), path)
                aesKey = key
                gson.toJson(envelope).toRequestBody(JSON)
            }
            else -> bodyJson.toRequestBody(JSON)
        }

        val reqBuilder = Request.Builder().url(url)
        FreelyBase.token?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }
        if (encrypt) reqBuilder.addHeader("x-enc", "1")

        when (method.uppercase()) {
            "GET"    -> reqBuilder.get()
            "POST"   -> reqBuilder.post(requestBody ?: "{}".toRequestBody(JSON))
            "PUT"    -> reqBuilder.put(requestBody ?: "{}".toRequestBody(JSON))
            "DELETE" -> if (requestBody != null) reqBuilder.delete(requestBody) else reqBuilder.delete()
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val rawBody = response.body?.string() ?: ""
        val isEncResp = response.header("x-enc") == "1"

        val plainJson = if (isEncResp && aesKey != null) {
            val envMap = gson.fromJson<Map<String, String>>(
                rawBody, object : TypeToken<Map<String, String>>() {}.type
            )
            CryptoManager.decryptResponse(envMap, aesKey, method.uppercase(), path)
        } else rawBody

        if (!response.isSuccessful) {
            val detail = runCatching {
                gson.fromJson<Map<String, Any?>>(plainJson, object : TypeToken<Map<String, Any?>>() {}.type)
                    .get("detail")?.toString() ?: plainJson
            }.getOrDefault(plainJson)
            throw FreelyBaseException("$method $url", response.code, detail)
        }

        if (plainJson.isBlank()) return@withContext emptyMap()
        return@withContext runCatching {
            gson.fromJson<Map<String, Any?>>(plainJson, object : TypeToken<Map<String, Any?>>() {}.type)
        }.getOrDefault(mapOf("_raw" to plainJson))
    }
}
