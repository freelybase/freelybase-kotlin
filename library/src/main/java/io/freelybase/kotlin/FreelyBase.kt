package io.freelybase.kotlin

import android.content.Context
import android.content.SharedPreferences
import io.freelybase.kotlin.internal.crypto.CryptoManager
import io.freelybase.kotlin.internal.http.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FreelyBase SDK 主入口。
 *
 * 使用前必须在 Application.onCreate() 中初始化：
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         FreelyBase.initialize(
 *             context = this,
 *             appId = "your_app_id",
 *             enableEncryption = true
 *         )
 *     }
 * }
 * ```
 */
object FreelyBase {

    internal var baseUrl: String = "http://www.freelybase.com/api".trimEnd('/')
    internal var wsBaseUrl: String = "ws://www.freelybase.com".trimEnd('/')
    internal var appKey: String = ""
    internal var enableEncryption: Boolean = true
    internal var token: String? = null
    
    /** 调试模式，开启后会打印详细日志 */
    var debug: Boolean = false
    
    private var initialized = false
    private lateinit var prefs: SharedPreferences

    /**
     * 全局 CoroutineScope，绑定 Application 生命周期。
     * initialize() 后可用，FBCall 默认使用此 Scope 执行请求。
     * SupervisorJob 保证单个请求失败不影响其他请求。
     */
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private const val PREF_NAME = "freelybase_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER  = "user_json"
    private const val TAG = "FreelyBase"

    /**
     * 初始化 SDK。
     *
     * @param context Android Context，建议传入 Application Context
     * @param appKey 应用 ID
     * @param enableEncryption 是否启用端到端加密，默认 true
     */
    @JvmStatic
    fun initialize(
        context: Context,
        appKey: String,
        enableEncryption: Boolean = true,
    ) {
        this.appKey = appKey
        this.enableEncryption = enableEncryption
        this.initialized = true
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 恢复上次登录的 token
        token = prefs.getString(KEY_TOKEN, null)
        logDebug("initialize: appKey=$appKey, enableEncryption=$enableEncryption, restored token=${token?.take(20)}")

        if (enableEncryption) {
            appScope.launch(Dispatchers.IO) {
                runCatching { 
                    CryptoManager.fetchPublicKey()
                    logDebug("fetchPublicKey: success")
                }.onFailure {
                    logDebug("fetchPublicKey: failed - ${it.message}")
                }
            }
        }
    }

    internal fun requireInitialized() {
        check(initialized) { "FreelyBase 未初始化，请在 Application.onCreate() 中调用 FreelyBase.initialize()" }
    }

    /**
     * 设置访问令牌（通常由 SDK 内部自动管理，无需手动调用）。
     */
    @JvmStatic
    fun setToken(token: String?) {
        this.token = token
        logDebug("setToken: ${token?.take(20)}")
        if (::prefs.isInitialized) {
            val editor = prefs.edit()
            if (token != null) editor.putString(KEY_TOKEN, token) else editor.remove(KEY_TOKEN)
            editor.apply()
        } else {
            logDebug("setToken: prefs not initialized!")
        }
    }

    /**
     * 清除访问令牌和用户会话（退出登录）。
     */
    @JvmStatic
    fun clearToken() {
        token = null
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
        }
        logDebug("clearToken: token cleared")
    }

    /**
     * 检查用户是否已登录。
     */
    @JvmStatic 
    fun isLoggedIn() = !token.isNullOrBlank()
    
    /**
     * 获取当前访问令牌。
     */
    @JvmStatic 
    fun getToken() = token

    /** 持久化用户基础信息 JSON（由 FBUser 内部调用） */
    internal fun saveUserJson(json: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_USER, json).apply()
        }
    }

    /** 读取缓存的用户 JSON，未登录时返回 null */
    internal fun loadUserJson(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_USER, null) else null

    /**
     * 发起 GET 请求（高级用法，一般无需直接调用）。
     */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): Map<String, Any?> =
        HttpClient.get(path, params)

    /**
     * 发起 POST 请求（高级用法，一般无需直接调用）。
     */
    suspend fun post(path: String, body: Any? = null): Map<String, Any?> =
        HttpClient.post(path, body)

    /**
     * 发起 PUT 请求（高级用法，一般无需直接调用）。
     */
    suspend fun put(path: String, body: Any? = null): Map<String, Any?> =
        HttpClient.put(path, body)

    /**
     * 发起 DELETE 请求（高级用法，一般无需直接调用）。
     */
    suspend fun delete(path: String): Map<String, Any?> =
        HttpClient.delete(path)

    /**
     * 获取服务器当前时间。
     *
     * @return ISO 8601 格式的时间字符串，如 "2026-04-14T10:30:00.123456"
     */
    fun getServerTime(): FBCall<String> = fbCall {
        val res = HttpClient.get("/public/server-time")
        res["server_time"]?.toString()
            ?: throw FreelyException("获取服务器时间失败")
    }

    /**
     * 清除所有查询缓存。
     */
    fun clearQueryCache() = QueryCache.clear()
    
    // ── 内部日志工具 ─────────────────────────────────────────────────
    
    /**
     * 打印调试日志（仅在 debug 模式下输出）。
     */
    internal fun logDebug(message: String) {
        if (debug) {
            android.util.Log.d(TAG, message)
        }
    }
    
    /**
     * 打印信息日志（仅在 debug 模式下输出）。
     */
    internal fun logInfo(message: String) {
        if (debug) {
            android.util.Log.i(TAG, message)
        }
    }
    
    /**
     * 打印警告日志（仅在 debug 模式下输出）。
     */
    internal fun logWarn(message: String) {
        if (debug) {
            android.util.Log.w(TAG, message)
        }
    }
    
    /**
     * 打印错误日志（仅在 debug 模式下输出）。
     */
    internal fun logError(message: String, throwable: Throwable? = null) {
        if (debug) {
            if (throwable != null) {
                android.util.Log.e(TAG, message, throwable)
            } else {
                android.util.Log.e(TAG, message)
            }
        }
    }
}
