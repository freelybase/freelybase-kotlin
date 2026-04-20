package io.freelybase.kotlin

import io.freelybase.kotlin.internal.http.HttpClient
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI 服务，提供聊天和配额管理功能。
 *
 * 使用示例：
 * ```kotlin
 * // 获取配额
 * FBAI.getQuota()
 *     .onSuccess { quota -> log("剩余 Token: ${quota.remaining}") }
 *     .bindTo(this)
 *
 * // 非流式响应（默认）
 * FBAI.chat("如何使用协程？")
 *     .prompt("你是一个专业的 Kotlin 开发助手")
 *     .onSuccess { response -> log(response.content) }
 *     .bindTo(this)
 *
 * // 流式响应（设置 onChunk 自动启用）
 * FBAI.chat("写一篇文章")
 *     .prompt("你是写作专家")
 *     .onChunk { chunk -> appendText(chunk) }
 *     .onSuccess { response -> log("完成") }
 *     .bindTo(this)
 *
 * // 停止输出
 * FBAI.stop()
 *
 * // 清除对话历史
 * FBAI.clearHistory()
 * ```
 */
object FBAI {

    // 内部维护的对话历史
    private val conversationHistory = mutableListOf<AiMessage>()
    
    // 当前正在执行的请求
    private var currentCall: FBCall<AiChatResponse>? = null
    
    // 当前正在执行的 SSE 连接
    private var currentEventSource: EventSource? = null

    /**
     * 获取应用的 AI Token 配额信息。
     *
     * @return AI 配额信息
     */
    fun getQuota(): FBCall<AiQuota> = fbCall {
        FreelyBase.requireInitialized()
        val res = HttpClient.get("/apps/${FreelyBase.appId}/ai/quota")
        AiQuota(
            remaining = (res["ai_token_remaining"] as? Number)?.toInt() ?: 0,
            tokenUnit = (res["token_unit"] as? Number)?.toInt() ?: 1_000_000,
            priceFenPerUnit = (res["price_fen_per_unit"] as? Number)?.toInt() ?: 100
        )
    }

    /**
     * 发起 AI 聊天对话（自动管理多轮对话历史）。
     *
     * @param message 用户消息内容
     * @return AI 聊天构建器
     */
    fun chat(message: String): AiChatBuilder {
        return AiChatBuilder(message)
    }

    /**
     * 停止当前正在执行的 AI 请求。
     * 
     * 用于中断 AI 内容输出，特别是在流式响应时。
     */
    fun stop() {
        currentCall?.cancel()
        currentCall = null
        currentEventSource?.cancel()
        currentEventSource = null
    }

    /**
     * 清除对话历史。
     */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * 获取当前对话历史。
     */
    fun getHistory(): List<AiMessage> = conversationHistory.toList()

    /**
     * AI 聊天构建器，支持链式调用。
     */
    class AiChatBuilder internal constructor(private val userMessage: String) {
        private var systemPrompt: String? = null
        private var onChunkCallback: ((String) -> Unit)? = null

        /**
         * 设置系统提示词。
         *
         * @param prompt 提示词内容
         * @return 当前构建器
         */
        fun prompt(prompt: String): AiChatBuilder {
            this.systemPrompt = prompt
            return this
        }

        /**
         * 设置流式响应的数据块回调。
         * 
         * 调用此方法会自动启用流式响应。
         *
         * @param block 每收到一个数据块时的回调
         * @return 当前构建器
         */
        fun onChunk(block: (String) -> Unit): AiChatBuilder {
            this.onChunkCallback = block
            return this
        }

        /**
         * 配置成功回调并执行请求。
         */
        fun onSuccess(block: (AiChatResponse) -> Unit): FBCall<AiChatResponse> {
            return execute().onSuccess(block)
        }

        /**
         * 配置失败回调。
         */
        fun onFailure(block: (FreelyBaseException) -> Unit): FBCall<AiChatResponse> {
            return execute().onFailure(block)
        }

        /**
         * 配置加载回调。
         */
        fun onLoading(block: () -> Unit): FBCall<AiChatResponse> {
            return execute().onLoading(block)
        }

        /**
         * 配置完成回调。
         */
        fun onFinally(block: () -> Unit): FBCall<AiChatResponse> {
            return execute().onFinally(block)
        }

        /**
         * 绑定生命周期。
         */
        fun bindTo(owner: androidx.lifecycle.LifecycleOwner): FBCall<AiChatResponse> {
            return execute().bindTo(owner)
        }

        /**
         * 执行 AI 聊天请求。
         */
        private fun execute(): FBCall<AiChatResponse> {
            // 根据是否设置了 onChunk 回调来决定是否使用流式响应
            val streamMode = onChunkCallback != null
            
            val call = fbCall {
                FreelyBase.requireInitialized()

                // 如果有系统提示词且历史为空，添加到历史开头
                if (systemPrompt != null && conversationHistory.isEmpty()) {
                    conversationHistory.add(AiMessage("system", systemPrompt!!))
                }

                // 添加用户消息到历史
                conversationHistory.add(AiMessage("user", userMessage))

                // 发送请求
                val body = mapOf(
                    "app_id" to FreelyBase.appId,
                    "messages" to conversationHistory.map { mapOf("role" to it.role, "content" to it.content) },
                    "stream" to streamMode
                )

                if (streamMode) {
                    // 流式响应处理
                    executeStreamRequest(body)
                } else {
                    // 非流式响应
                    val res = HttpClient.post("/apps/${FreelyBase.appId}/ai/chat", body)
                    val response = AiChatResponse(
                        content = res["content"]?.toString() ?: "",
                        tokensUsed = (res["tokens_used"] as? Number)?.toInt() ?: 0,
                        remaining = (res["ai_token_remaining"] as? Number)?.toInt() ?: 0
                    )

                    // 添加 AI 回复到历史
                    conversationHistory.add(AiMessage("assistant", response.content))

                    response
                }
            }
            
            // 保存当前请求引用
            currentCall = call
            
            return call
        }

        /**
         * 执行流式请求（SSE）。
         */
        private suspend fun executeStreamRequest(body: Map<String, Any?>): AiChatResponse = 
            suspendCancellableCoroutine { continuation ->
                val gson = Gson()
                val JSON = "application/json; charset=utf-8".toMediaType()
                
                // 构建 OkHttp 客户端
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                // 构建请求
                val url = "${FreelyBase.baseUrl}/apps/${FreelyBase.appId}/ai/chat"
                val requestBody = gson.toJson(body).toRequestBody(JSON)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .apply {
                        FreelyBase.token?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .build()

                // 累积完整内容
                val fullContent = StringBuilder()
                var tokensUsed = 0
                var remaining = 0
                var eventSource: EventSource? = null

                // 创建 SSE 监听器
                val listener = object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                        // 连接已打开
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        try {
                            // 解析 SSE 数据：data: {"content": "...", "done": false}
                            val jsonData = gson.fromJson(data, Map::class.java) as Map<*, *>
                            
                            val isDone = jsonData["done"] as? Boolean ?: false
                            
                            if (isDone) {
                                // 流式响应完成
                                tokensUsed = (jsonData["tokens_used"] as? Number)?.toInt() ?: 0
                                remaining = (jsonData["ai_token_remaining"] as? Number)?.toInt() ?: 0
                                
                                // 检查是否有错误
                                val error = jsonData["error"] as? String
                                if (error != null) {
                                    continuation.resumeWithException(
                                        FreelyBaseException("AI 服务错误: $error", 502)
                                    )
                                } else {
                                    // 添加 AI 回复到历史
                                    val content = fullContent.toString()
                                    conversationHistory.add(AiMessage("assistant", content))
                                    
                                    // 返回完整响应
                                    continuation.resume(
                                        AiChatResponse(
                                            content = content,
                                            tokensUsed = tokensUsed,
                                            remaining = remaining
                                        )
                                    )
                                }
                                
                                eventSource.cancel()
                            } else {
                                // 接收到内容块
                                val content = jsonData["content"] as? String
                                if (content != null) {
                                    fullContent.append(content)
                                    // 调用 onChunk 回调
                                    onChunkCallback?.invoke(content)
                                }
                            }
                        } catch (e: Exception) {
                            continuation.resumeWithException(
                                FreelyBaseException("解析 SSE 数据失败: ${e.message}", 500)
                            )
                            eventSource.cancel()
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        // 连接已关闭
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: okhttp3.Response?
                    ) {
                        val errorMsg = when {
                            response != null -> {
                                val body = response.body?.string() ?: ""
                                try {
                                    val errorData = gson.fromJson(body, Map::class.java) as Map<*, *>
                                    errorData["detail"]?.toString() ?: body
                                } catch (e: Exception) {
                                    body
                                }
                            }
                            t != null -> t.message ?: "未知错误"
                            else -> "连接失败"
                        }
                        
                        continuation.resumeWithException(
                            FreelyBaseException(
                                "流式请求失败: $errorMsg",
                                response?.code ?: 500
                            )
                        )
                    }
                }

                // 创建 EventSource
                eventSource = EventSources.createFactory(client)
                    .newEventSource(request, listener)
                
                // 保存当前 EventSource 引用以支持 stop()
                currentEventSource = eventSource

                // 支持取消
                continuation.invokeOnCancellation {
                    eventSource?.cancel()
                    currentEventSource = null
                }
            }
    }
}

/**
 * AI 消息。
 *
 * @property role 角色，可选值：system, user, assistant
 * @property content 消息内容
 */
data class AiMessage(
    val role: String,
    val content: String
)

/**
 * AI 配额信息。
 *
 * @property remaining 剩余 Token 数量
 * @property tokenUnit Token 购买单位（默认 1,000,000）
 * @property priceFenPerUnit 每单位价格（分）
 */
data class AiQuota(
    val remaining: Int,
    val tokenUnit: Int,
    val priceFenPerUnit: Int
)

/**
 * AI 聊天响应。
 *
 * @property content AI 回复内容
 * @property tokensUsed 本次消耗的 Token 数量
 * @property remaining 剩余 Token 数量
 */
data class AiChatResponse(
    val content: String,
    val tokensUsed: Int,
    val remaining: Int
)
