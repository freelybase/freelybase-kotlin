# FreelyBase SDK 公共 API 文档

本文档列出 SDK 对外暴露的所有公共接口。

## 核心类

### FreelyBase

SDK 主入口，提供初始化和全局配置。

```kotlin
object FreelyBase {
    // 初始化 SDK
    fun initialize(
        context: Context,
        appId: String,
        enableEncryption: Boolean = true
    )
    
    // 用户状态
    fun isLoggedIn(): Boolean
    fun getToken(): String?
    fun setToken(token: String?)
    fun clearToken()
    
    // 工具方法
    fun getServerTime(): FBCall<String>
    fun clearQueryCache()
    
    // 高级用法：直接 HTTP 请求
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): Map<String, Any?>
    suspend fun post(path: String, body: Any? = null): Map<String, Any?>
    suspend fun put(path: String, body: Any? = null): Map<String, Any?>
    suspend fun delete(path: String): Map<String, Any?>
}
```

### FBObject

数据对象基类，所有自定义数据类都应继承此类。

```kotlin
abstract class FBObject(val tableName: String) {
    // 基础字段
    var objectId: String
    var createdAt: String  // 只读
    var updatedAt: String  // 只读
    
    // CRUD 操作
    fun <T : FBObject> save(): FBCall<T>
    fun <T : FBObject> fetch(): FBCall<T>
    fun <T : FBObject> update(): FBCall<T>
    fun delete(): FBCall<Unit>
}
```

### FBUser

用户类，封装用户认证和账户管理功能。

```kotlin
open class FBUser : FBObject("_User") {
    // 基础字段
    var username: String
    var email: String
    var mobile: String
    var password: String  // @Transient，仅用于注册登录
    
    // 实例方法：注册
    fun signUpByEmail(emailCode: String): FBCall<FBUser>
    fun signUpBySms(smsCode: String): FBCall<FBUser>
    fun signUpByUsername(): FBCall<FBUser>
    
    // 实例方法：更新
    fun updateProfile(): FBCall<Unit>
    fun changePassword(currentPassword: String, newPassword: String): FBCall<Unit>
    fun resetPassword(emailCode: String, newPassword: String): FBCall<Unit>
    fun resetPasswordBySms(smsCode: String, newPassword: String): FBCall<Unit>
    
    companion object {
        // 验证码
        fun sendEmailCode(email: String): FBCall<String>
        fun verifyEmailCode(email: String, emailCode: String): FBCall<Boolean>
        fun sendSmsCode(phone: String): FBCall<String>
        fun verifySmsCode(phone: String, smsCode: String): FBCall<Boolean>
        
        // 登录
        fun login(account: String, password: String): FBCall<FBUser>
        fun <T : FBUser> loginAs(account: String, password: String): FBCall<T>
        fun loginWithEmailCode(email: String, emailCode: String): FBCall<FBUser>
        fun <T : FBUser> loginWithEmailCodeAs(email: String, emailCode: String): FBCall<T>
        fun loginWithSmsCode(phone: String, smsCode: String): FBCall<FBUser>
        fun <T : FBUser> loginWithSmsCodeAs(phone: String, smsCode: String): FBCall<T>
        
        // 第三方登录
        fun loginWithAuthDataAs(snsType: String, accessToken: String, expiresIn: String, userId: String): FBCall<FBUser>
        fun <T : FBUser> loginWithAuthDataAs(snsType: String, accessToken: String, expiresIn: String, userId: String): FBCall<T>
        fun associateWithAuthData(snsType: String, accessToken: String, expiresIn: String, userId: String): FBCall<Unit>
        fun dissociateAuthData(snsType: String): FBCall<Unit>
        
        // 当前用户
        fun currentUser(): FBCall<FBUser>
        fun <T : FBUser> currentUserAs(): FBCall<T>
        fun restoreCurrentUser(): FBUser?
        fun <T : FBUser> restoreCurrentUserAs(): T?
        
        // 退出
        fun logout()
    }
}
```

### FBQuery

查询构建器，支持链式调用。

```kotlin
class FBQuery<T : FBObject>(clazz: Class<T>) {
    // 基础比较
    fun equalTo(field: String, value: Any?): FBQuery<T>
    fun notEqualTo(field: String, value: Any?): FBQuery<T>
    fun greaterThan(field: String, value: Any?): FBQuery<T>
    fun greaterThanOrEqual(field: String, value: Any?): FBQuery<T>
    fun lessThan(field: String, value: Any?): FBQuery<T>
    fun lessThanOrEqual(field: String, value: Any?): FBQuery<T>
    
    // 存在性
    fun exists(field: String): FBQuery<T>
    fun notExists(field: String): FBQuery<T>
    
    // 模糊查询
    fun contains(field: String, value: String): FBQuery<T>
    fun startsWith(field: String, value: String): FBQuery<T>
    fun endsWith(field: String, value: String): FBQuery<T>
    
    // 数组查询
    fun containedIn(field: String, values: List<Any?>): FBQuery<T>
    fun notContainedIn(field: String, values: List<Any?>): FBQuery<T>
    
    // 时间查询
    fun atTime(field: String, date: Date): FBQuery<T>
    fun notAtTime(field: String, date: Date): FBQuery<T>
    fun before(field: String, date: Date): FBQuery<T>
    fun after(field: String, date: Date): FBQuery<T>
    fun beforeOrAt(field: String, date: Date): FBQuery<T>
    fun afterOrAt(field: String, date: Date): FBQuery<T>
    fun between(field: String, from: Date, to: Date): FBQuery<T>
    fun notBetween(field: String, from: Date, to: Date): FBQuery<T>
    
    // 子查询
    suspend fun <S : FBObject> matchesQuery(field: String, subQuery: FBQuery<S>): FBQuery<T>
    suspend fun <S : FBObject> doesNotMatchQuery(field: String, subQuery: FBQuery<S>): FBQuery<T>
    
    // 复合查询
    fun or(block: FBQuery<T>.() -> Unit): FBQuery<T>
    
    // 排序/分页
    fun orderBy(field: String): FBQuery<T>
    fun orderByDesc(field: String): FBQuery<T>
    fun skip(n: Int): FBQuery<T>
    fun limit(n: Int): FBQuery<T>
    fun page(page: Int, pageSize: Int = 20): FBQuery<T>
    
    // 字段选择/关联展开
    fun select(vararg fields: String): FBQuery<T>
    fun include(vararg fields: String): FBQuery<T>
    
    // 缓存策略
    fun cachePolicy(policy: CachePolicy): FBQuery<T>
    fun maxCacheAge(ms: Long): FBQuery<T>
    
    // 执行
    fun find(): FBCall<List<T>>
    fun count(): FBCall<Int>
    fun first(): FBCall<T?>
    fun getObject(objectId: String): FBCall<T>
}

// 便捷构造函数
inline fun <reified T : FBObject> FBQuery(): FBQuery<T>
```

### FBFile

文件管理，封装上传与 URL 解析。

```kotlin
class FBFile private constructor(val path: String) {
    val url: String
    val name: String
    
    companion object {
        // 上传
        fun upload(file: File, mimeType: String? = null): FBCall<FBFile>
        fun upload(bytes: ByteArray, fileName: String, mimeType: String? = null): FBCall<FBFile>
        fun upload(stream: InputStream, fileName: String, mimeType: String? = null): FBCall<FBFile>
        fun upload(contentResolver: ContentResolver, uri: Uri): FBCall<FBFile>
        
        // 构建
        fun fromPath(path: String): FBFile
        fun fromUrl(url: String): FBFile
    }
}

// 扩展函数
fun String.toFBFile(): FBFile
```

### FBAI

AI 服务，提供聊天和配额管理功能。

```kotlin
object FBAI {
    // 获取 AI Token 配额
    fun getQuota(): FBCall<AiQuota>
    
    // 发起 AI 聊天（自动管理对话历史）
    fun chat(message: String): AiChatBuilder
    
    // 停止当前正在执行的 AI 请求
    fun stop()
    
    // 清除对话历史
    fun clearHistory()
    
    // 获取对话历史
    fun getHistory(): List<AiMessage>
}

// AI 聊天构建器
class AiChatBuilder {
    // 设置系统提示词（仅第一次对话时有效）
    fun prompt(prompt: String): AiChatBuilder
    
    // 设置流式响应的数据块回调（调用此方法自动启用流式响应）
    fun onChunk(block: (String) -> Unit): AiChatBuilder
    
    // 直接执行（不设置系统提示词）
    fun onSuccess(block: (AiChatResponse) -> Unit): FBCall<AiChatResponse>
    fun onFailure(block: (FreelyBaseException) -> Unit): FBCall<AiChatResponse>
    fun onLoading(block: () -> Unit): FBCall<AiChatResponse>
    fun onFinally(block: () -> Unit): FBCall<AiChatResponse>
    fun bindTo(owner: LifecycleOwner): FBCall<AiChatResponse>
}

// AI 消息
data class AiMessage(
    val role: String,      // system, user, assistant
    val content: String
)

// AI 配额信息
data class AiQuota(
    val remaining: Int,           // 剩余 Token 数量
    val tokenUnit: Int,           // Token 购买单位
    val priceFenPerUnit: Int      // 每单位价格（分）
)

// AI 聊天响应
data class AiChatResponse(
    val content: String,          // AI 回复内容
    val tokensUsed: Int,          // 本次消耗的 Token 数量
    val remaining: Int            // 剩余 Token 数量
)
```

### FBCall

异步调用封装，行为与 Firebase Task 一致。

```kotlin
class FBCall<T>(block: suspend () -> T) {
    // 配置回调（配置后自动执行）
    fun onLoading(block: () -> Unit): FBCall<T>
    fun onSuccess(block: (T) -> Unit): FBCall<T>
    fun onFailure(block: (FreelyBaseException) -> Unit): FBCall<T>
    fun onFinally(block: () -> Unit): FBCall<T>
    
    // 生命周期绑定
    fun bindTo(owner: LifecycleOwner): FBCall<T>
    fun launch(scope: CoroutineScope): FBCall<T>
    
    // 协程支持
    suspend fun await(): FBResult<T>
}

// 便捷构造函数
fun <T> fbCall(block: suspend () -> T): FBCall<T>
```

### FBResult

结果类型，用于协程中的返回值。

```kotlin
sealed class FBResult<out T> {
    data class Success<T>(val data: T) : FBResult<T>()
    data class Failure(val error: FreelyBaseException) : FBResult<Nothing>()
    
    val isSuccess: Boolean
    val isFailure: Boolean
    
    fun getOrNull(): T?
    fun getOrThrow(): T
    fun getOrDefault(default: T): T
    fun <R> map(transform: (T) -> R): FBResult<R>
}
```

### FreelyBaseException

SDK 异常类。

```kotlin
class FreelyBaseException(
    message: String,
    val statusCode: Int = 0,
    val detail: String = message
) : Exception(message)
```

## 注解

### @Pointer

标记一对一关联字段。

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pointer
```

### @Relation

标记多对多关联字段。

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Relation
```

## 枚举

### CachePolicy

查询缓存策略。

```kotlin
enum class CachePolicy {
    NETWORK_ONLY,        // 只走网络（默认）
    CACHE_ONLY,          // 只读缓存
    CACHE_ELSE_NETWORK,  // 先读缓存，无缓存再走网络
    NETWORK_ELSE_CACHE,  // 先走网络，失败再读缓存
}
```

## 内部实现（不对外暴露）

以下包和类标记为 `internal`，外部无法访问：

- `com.freelybase.sdk.internal.http.*` - HTTP 客户端实现
- `com.freelybase.sdk.internal.crypto.*` - 加密实现
- `QueryCache` - 查询缓存管理
- `FBObject.toDataMap()` - 序列化方法
- `FBObject.fillFromResponse()` - 反序列化方法
- `FBUser.applyAuthResponse()` - 认证响应处理
- `FBUser.persistSession()` - 会话持久化

## 使用示例

### 基础用法

```kotlin
// 初始化
FreelyBase.initialize(this, "your_app_id")

// 登录
FBUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { user -> log(user.username) }
    .bindTo(this)

// 查询
FBQuery<Post>()
    .equalTo("status", "published")
    .include("author")
    .find()
    .onSuccess { posts -> updateUI(posts) }

// 保存
Post().apply { title = "Hello" }
    .save<Post>()
    .onSuccess { log(it.objectId) }
```

### 协程用法

```kotlin
lifecycleScope.launch {
    val result = FBQuery<Post>().find().await()
    when (result) {
        is FBResult.Success -> updateUI(result.data)
        is FBResult.Failure -> showError(result.error)
    }
}
```

### AI 聊天用法

```kotlin
// 获取配额
FBAI.getQuota()
    .onSuccess { quota -> 
        log("剩余 Token: ${quota.remaining}")
    }
    .bindTo(this)

// 第一次对话（带系统提示词）
FBAI.chat("如何使用协程？")
    .prompt("你是一个专业的 Kotlin 开发助手")
    .onSuccess { response -> 
        log("AI: ${response.content}")
        log("消耗: ${response.tokensUsed} tokens")
    }
    .bindTo(this)

// 继续对话（自动带上历史）
FBAI.chat("能举个例子吗？")
    .onSuccess { response -> 
        log("AI: ${response.content}")
    }
    .bindTo(this)

// 流式响应（设置 onChunk 自动启用流式）
FBAI.chat("写一篇关于 Kotlin 的文章")
    .onChunk { chunk -> 
        appendText(chunk)  // 逐步显示内容
    }
    .onSuccess { response ->
        log("完成，消耗: ${response.tokensUsed} tokens")
    }
    .bindTo(this)

// 停止 AI 输出
FBAI.stop()

// 清除对话历史
FBAI.clearHistory()
```
