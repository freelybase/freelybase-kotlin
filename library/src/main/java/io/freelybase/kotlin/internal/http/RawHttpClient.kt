package io.freelybase.kotlin.internal.http

import io.freelybase.kotlin.FreelyBase
import io.freelybase.kotlin.FreelyException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object RawHttpClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getPublicKeyPem(): String = withContext(Dispatchers.IO) {
        val url = "${FreelyBase.baseUrl}/crypto/public-key"
        val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
        val body = resp.body?.string() ?: ""
        if (!resp.isSuccessful) throw FreelyException("获取公钥失败: ${resp.code}")
        val map = gson.fromJson<Map<String, Any?>>(body, object : TypeToken<Map<String, Any?>>() {}.type)
        map["public_key_pem"]?.toString() ?: throw FreelyException("公钥响应格式错误")
    }
}
