package io.freelybase.kotlin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * 数据实时监听，基于 WebSocket。
 *
 * 使用示例：
 * ```kotlin
 * val liveQuery = FBLiveQuery(Message::class.java)
 *     .whereEqualTo("roomId", "room_001")
 *
 * liveQuery.on(FBLiveQuery.Event.CREATED) { obj -> ... }
 * liveQuery.on(FBLiveQuery.Event.UPDATED) { obj -> ... }
 * liveQuery.on(FBLiveQuery.Event.DELETED) { obj -> ... }
 *
 * liveQuery.subscribe()   // 开始监听
 * // ...
 * liveQuery.unsubscribe() // 停止监听
 * ```
 *
 * 注意：需要应用已开通「数据监听」套餐，并使用 App 用户 token 连接。
 */
class FBLiveQuery<T : FBObject>(private val clazz: Class<T>) {

    enum class Event { CREATED, UPDATED, DELETED }

    private val tableName: String = clazz.getDeclaredConstructor().newInstance().tableName
    private val filters = mutableMapOf<String, Any?>()

    private var onCreated: ((T) -> Unit)? = null
    private var onUpdated: ((T) -> Unit)? = null
    private var onDeleted: ((T) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    @Volatile private var active = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // 长连接不超时
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    // ── 过滤条件（客户端过滤，服务端推送全表事件）──────────────────

    fun whereEqualTo(field: String, value: Any?): FBLiveQuery<T> {
        filters[field] = value
        return this
    }

    // ── 事件回调 ────────────────────────────────────────────────────

    fun on(event: Event, callback: (T) -> Unit): FBLiveQuery<T> {
        when (event) {
            Event.CREATED -> onCreated = callback
            Event.UPDATED -> onUpdated = callback
            Event.DELETED -> onDeleted = callback
        }
        return this
    }

    fun onConnected(callback: () -> Unit): FBLiveQuery<T> {
        onConnected = callback
        return this
    }

    fun onDisconnected(callback: () -> Unit): FBLiveQuery<T> {
        onDisconnected = callback
        return this
    }

    fun onError(callback: (Throwable) -> Unit): FBLiveQuery<T> {
        onError = callback
        return this
    }

    // ── 连接管理 ────────────────────────────────────────────────────

    fun subscribe() {
        FreelyBase.requireInitialized()
        active = true
        connect()
    }

    fun unsubscribe() {
        active = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "unsubscribed")
        webSocket = null
        scope.cancel()
    }

    private fun connect() {
        val token = FreelyBase.token
        if (token.isNullOrBlank()) {
            onError?.invoke(IllegalStateException("未登录，请先调用 FBUser.loginAs() 获取 token"))
            return
        }
        val appId = FreelyBase.appId
        // wsBaseUrl 优先；为空时从 baseUrl 推导（去掉 /api 后缀，替换协议）
        val wsBase = if (FreelyBase.wsBaseUrl.isNotBlank()) {
            FreelyBase.wsBaseUrl
        } else {
            val httpBase = FreelyBase.baseUrl.trimEnd('/')
            val hostBase = if (httpBase.endsWith("/api")) httpBase.dropLast(4) else httpBase
            hostBase
                .replace("^https://".toRegex(), "wss://")
                .replace("^http://".toRegex(), "ws://")
        }

        val url = "$wsBase/ws/apps/$appId/tables?token=$token"
        android.util.Log.d("FBLiveQuery", "connecting to: $url")
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onError?.invoke(t)
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
                if (active) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!active) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            if (active) connect()
        }
    }

    private fun handleMessage(text: String) {
        runCatching {
            val msg = gson.fromJson<Map<String, Any?>>(
                text, object : TypeToken<Map<String, Any?>>() {}.type
            )
            val event = msg["event"] as? String ?: return@runCatching
            val data = msg["data"] as? Map<String, Any?> ?: return@runCatching

            val msgTableName = data["table_name"] as? String
            if (msgTableName != null && msgTableName != tableName) return@runCatching

            when (event) {
                "table.data.created" -> {
                    val obj = buildObject(data) ?: return@runCatching
                    if (matchesFilters(obj)) onCreated?.invoke(obj)
                }
                "table.data.updated" -> {
                    val obj = buildObject(data) ?: return@runCatching
                    if (matchesFilters(obj)) onUpdated?.invoke(obj)
                }
                "table.data.deleted" -> {
                    val obj = clazz.getDeclaredConstructor().newInstance()
                    obj.objectId = data["object_id"]?.toString() ?: return@runCatching
                    onDeleted?.invoke(obj)
                }
                else -> return@runCatching
            }
        }.onFailure { onError?.invoke(it) }
    }

    private fun buildObject(data: Map<String, Any?>): T? {
        return runCatching {
            clazz.getDeclaredConstructor().newInstance().also { obj ->
                val responseMap = mapOf(
                    "object_id" to data["object_id"],
                    "created_at" to data["created_at"],
                    "updated_at" to data["updated_at"],
                    "data" to (data["data"] ?: emptyMap<String, Any?>())
                )
                obj.fillFromResponse(responseMap)
            }
        }.getOrNull()
    }

    private fun matchesFilters(obj: T): Boolean {
        if (filters.isEmpty()) return true
        val dataMap = obj.toDataMap()
        return filters.all { (key, value) ->
            val actual = dataMap[key]
            when {
                value == null -> actual == null
                value is Number && actual is Number -> value.toDouble() == actual.toDouble()
                else -> actual?.toString() == value.toString()
            }
        }
    }
}

/** 用法：FBLiveQuery<Post>() 代替 FBLiveQuery(Post::class.java) */
inline fun <reified T : FBObject> FBLiveQuery(): FBLiveQuery<T> = FBLiveQuery(T::class.java)
