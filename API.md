# FreelyBase SDK 公共 API 文档

> 完整的 SDK API 参考文档，包含所有公共接口、方法签名和使用示例

**版本**: 1.0.2  
**包名**: `io.freelybase.kotlin`  
**最低 Android 版本**: API 29 (Android 10)

---

## 目录

1. [核心类](#核心类)
   - [FreelyBase](#freelybase) - SDK 主入口
   - [FreelyObject](#freelyobject) - 数据对象基类
   - [FreelyUser](#freelyuser) - 用户认证
   - [FBQuery](#fbquery) - 数据查询
   - [FBFile](#fbfile) - 文件管理
   - [FBGeoPoint](#fbgeopoint) - 地理位置
   - [FBLiveQuery](#fblivequery) - 实时数据监听
2. [异步处理](#异步处理)
   - [FBCall](#fbcall) - 异步调用封装
   - [FBResult](#fbresult) - 结果类型
3. [注解](#注解)
   - [@Pointer](#pointer) - 一对一关联
   - [@Relation](#relation) - 多对多关联
4. [枚举](#枚举)
   - [CachePolicy](#cachepolicy) - 缓存策略
   - [DistanceUnit](#distanceunit) - 距离单位
   - [FBLiveQuery.Event](#fblivequery-event) - 实时事件类型
5. [异常](#异常)
   - [FreelyException](#freelyexception) - SDK 异常
6. [使用示例](#使用示例)

---

## 核心类

### FreelyBase

SDK 主入口，提供初始化和全局配置。

#### 初始化

```kotlin
object FreelyBase {
    /**
     * 初始化 SDK（必须在 Application.onCreate() 中调用）
     *
     * @param context Android Context，建议传入 Application Context
     * @param appKey 应用 ID
     * @param enableEncryption 是否启用端到端加密，默认 true
     */
    fun initialize(
        context: Context,
        appKey: String,
        enableEncryption: Boolean = true
    )
}
```

**使用示例**：
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FreelyBase.initialize(
            context = this,
            appKey = "your_app_key",
            enableEncryption = true
        )
    }
}
```

#### 用户状态管理

```kotlin
/**
 * 检查用户是否已登录
 */
fun isLoggedIn(): Boolean

/**
 * 获取当前访问令牌
 */
fun getToken(): String?

/**
 * 设置访问令牌（通常由 SDK 内部自动管理）
 */
fun setToken(token: String?)

/**
 * 清除访问令牌和用户会话（退出登录）
 */
fun clearToken()
```

#### 工具方法

```kotlin
/**
 * 获取服务器当前时间
 *
 * @return ISO 8601 格式的时间字符串
 */
fun getServerTime(): FBCall<String>

/**
 * 清除所有查询缓存
 */
fun clearQueryCache()
```

#### 高级用法：直接 HTTP 请求

```kotlin
/**
 * 发起 GET 请求
 */
suspend fun get(path: String, params: Map<String, String> = emptyMap()): Map<String, Any?>

/**
 * 发起 POST 请求
 */
suspend fun post(path: String, body: Any? = null): Map<String, Any?>

/**
 * 发起 PUT 请求
 */
suspend fun put(path: String, body: Any? = null): Map<String, Any?>

/**
 * 发起 DELETE 请求
 */
suspend fun delete(path: String): Map<String, Any?>
```

---


### FreelyObject

数据对象基类，所有自定义数据类都应继承此类。

#### 类定义

```kotlin
abstract class FreelyObject(val tableName: String) {
    /** 对象唯一标识符 */
    var objectId: String = ""
    
    /** 创建时间（ISO 8601 格式，只读） */
    var createdAt: String = ""
    
    /** 最后更新时间（ISO 8601 格式，只读） */
    var updatedAt: String = ""
}
```

#### CRUD 操作

```kotlin
/**
 * 保存对象到服务器（新建）
 *
 * @return FBCall<T> 可链式调用 onSuccess/onFailure
 */
fun <T : FreelyObject> save(): FBCall<T>

/**
 * 从服务器获取最新数据
 *
 * @return FBCall<T> 可链式调用 onSuccess/onFailure
 */
fun <T : FreelyObject> fetch(): FBCall<T>

/**
 * 更新对象到服务器
 *
 * @return FBCall<T> 可链式调用 onSuccess/onFailure
 */
fun <T : FreelyObject> update(): FBCall<T>

/**
 * 从服务器删除对象
 *
 * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
 */
fun delete(): FBCall<Unit>
```

#### 使用示例

```kotlin
// 定义数据类
class Post : FreelyObject("Post") {
    var title: String = ""
    var content: String = ""
    var viewCount: Int = 0
    
    @Pointer
    var author: AppUser? = null
    
    @Relation
    var tags: MutableList<Tag> = mutableListOf()
}

// 保存
val post = Post().apply {
    title = "Hello World"
    content = "This is my first post"
}
post.save<Post>()
    .onSuccess { savedPost ->
        Log.d("Post", "保存成功: ${savedPost.objectId}")
    }
    .bindTo(this)

// 更新
post.viewCount++
post.update<Post>()
    .onSuccess { Log.d("Post", "更新成功") }
    .bindTo(this)

// 获取最新数据
post.fetch<Post>()
    .onSuccess { freshPost ->
        Log.d("Post", "最新数据: ${freshPost.title}")
    }
    .bindTo(this)

// 删除
post.delete()
    .onSuccess { Log.d("Post", "删除成功") }
    .bindTo(this)
```

---


### FreelyUser

用户类，封装用户认证和账户管理功能。

#### 类定义

```kotlin
open class FreelyUser : FreelyObject("_User") {
    var username: String = ""
    var email: String = ""
    var mobile: String = ""
    
    @Transient
    var password: String = ""  // 仅用于注册登录，不会持久化
}
```

#### 验证码

```kotlin
companion object {
    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱地址
     * @return FBCall<String> 返回验证码 ID
     */
    fun sendEmailCode(email: String): FBCall<String>
    
    /**
     * 验证邮箱验证码
     *
     * @param email 邮箱地址
     * @param emailCode 验证码
     * @return FBCall<Boolean> 验证是否成功
     */
    fun verifyEmailCode(email: String, emailCode: String): FBCall<Boolean>
    
    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     * @return FBCall<String> 返回验证码 ID
     */
    fun sendSmsCode(phone: String): FBCall<String>
    
    /**
     * 验证短信验证码
     *
     * @param phone 手机号
     * @param smsCode 验证码
     * @return FBCall<Boolean> 验证是否成功
     */
    fun verifySmsCode(phone: String, smsCode: String): FBCall<Boolean>
}
```

#### 注册

```kotlin
/**
 * 通过邮箱验证码注册
 *
 * @param emailCode 邮箱验证码
 * @return FBCall<FreelyUser> 注册成功后返回用户对象
 */
fun signUpByEmail(emailCode: String): FBCall<FreelyUser>

/**
 * 通过短信验证码注册
 *
 * @param smsCode 短信验证码
 * @return FBCall<FreelyUser> 注册成功后返回用户对象
 */
fun signUpBySms(smsCode: String): FBCall<FreelyUser>

/**
 * 通过用户名密码注册
 *
 * @return FBCall<FreelyUser> 注册成功后返回用户对象
 */
fun signUpByUsername(): FBCall<FreelyUser>
```

#### 登录

```kotlin
companion object {
    /**
     * 用户名/邮箱/手机号 + 密码登录
     *
     * @param account 用户名、邮箱或手机号
     * @param password 密码
     * @return FBCall<FreelyUser> 登录成功后返回用户对象
     */
    fun login(account: String, password: String): FBCall<FreelyUser>
    
    /**
     * 用户名/邮箱/手机号 + 密码登录（返回自定义用户类型）
     *
     * @param account 用户名、邮箱或手机号
     * @param password 密码
     * @return FBCall<T> 登录成功后返回自定义用户对象
     */
    fun <T : FreelyUser> loginAs(account: String, password: String): FBCall<T>
    
    /**
     * 邮箱验证码登录
     *
     * @param email 邮箱地址
     * @param emailCode 验证码
     * @return FBCall<FreelyUser> 登录成功后返回用户对象
     */
    fun loginWithEmailCode(email: String, emailCode: String): FBCall<FreelyUser>
    
    /**
     * 邮箱验证码登录（返回自定义用户类型）
     */
    fun <T : FreelyUser> loginWithEmailCodeAs(email: String, emailCode: String): FBCall<T>
    
    /**
     * 短信验证码登录
     *
     * @param phone 手机号
     * @param smsCode 验证码
     * @return FBCall<FreelyUser> 登录成功后返回用户对象
     */
    fun loginWithSmsCode(phone: String, smsCode: String): FBCall<FreelyUser>
    
    /**
     * 短信验证码登录（返回自定义用户类型）
     */
    fun <T : FreelyUser> loginWithSmsCodeAs(phone: String, smsCode: String): FBCall<T>
}
```

#### 第三方登录

```kotlin
companion object {
    /**
     * 第三方账号登录
     *
     * @param snsType 第三方平台类型（如 "weixin", "qq", "weibo"）
     * @param accessToken 第三方平台的 access_token
     * @param expiresIn 过期时间（秒）
     * @param userId 第三方平台的用户 ID
     * @return FBCall<FreelyUser> 登录成功后返回用户对象
     */
    fun loginWithAuthDataAs(
        snsType: String,
        accessToken: String,
        expiresIn: String,
        userId: String
    ): FBCall<FreelyUser>
    
    /**
     * 第三方账号登录（返回自定义用户类型）
     */
    fun <T : FreelyUser> loginWithAuthDataAs(
        snsType: String,
        accessToken: String,
        expiresIn: String,
        userId: String
    ): FBCall<T>
    
    /**
     * 关联第三方账号
     */
    fun associateWithAuthData(
        snsType: String,
        accessToken: String,
        expiresIn: String,
        userId: String
    ): FBCall<Unit>
    
    /**
     * 解除第三方账号关联
     */
    fun dissociateAuthData(snsType: String): FBCall<Unit>
}
```

#### 当前用户

```kotlin
companion object {
    /**
     * 获取当前登录用户（从服务器获取最新数据）
     *
     * @return FBCall<FreelyUser> 当前用户对象
     */
    fun currentUser(): FBCall<FreelyUser>
    
    /**
     * 获取当前登录用户（返回自定义用户类型）
     */
    fun <T : FreelyUser> currentUserAs(): FBCall<T>
    
    /**
     * 从本地缓存恢复当前用户（不发起网络请求）
     *
     * @return FreelyUser? 缓存的用户对象，未登录时返回 null
     */
    fun restoreCurrentUser(): FreelyUser?
    
    /**
     * 从本地缓存恢复当前用户（返回自定义用户类型）
     */
    fun <T : FreelyUser> restoreCurrentUserAs(): T?
    
    /**
     * 退出登录
     */
    fun logout()
}
```

#### 账户管理

```kotlin
/**
 * 更新用户资料
 *
 * @return FBCall<Unit> 更新成功
 */
fun updateProfile(): FBCall<Unit>

/**
 * 修改密码
 *
 * @param currentPassword 当前密码
 * @param newPassword 新密码
 * @return FBCall<Unit> 修改成功
 */
fun changePassword(currentPassword: String, newPassword: String): FBCall<Unit>

/**
 * 通过邮箱验证码重置密码
 *
 * @param emailCode 邮箱验证码
 * @param newPassword 新密码
 * @return FBCall<Unit> 重置成功
 */
fun resetPassword(emailCode: String, newPassword: String): FBCall<Unit>

/**
 * 通过短信验证码重置密码
 *
 * @param smsCode 短信验证码
 * @param newPassword 新密码
 * @return FBCall<Unit> 重置成功
 */
fun resetPasswordBySms(smsCode: String, newPassword: String): FBCall<Unit>
```

#### 使用示例

```kotlin
// 自定义用户类
class AppUser : FreelyUser() {
    var nickname: String = ""
    var avatar: String = ""
    var level: Int = 1
}

// 注册
val user = AppUser().apply {
    username = "zhangsan"
    email = "zhangsan@example.com"
    password = "123456"
}

// 1. 发送邮箱验证码
FreelyUser.sendEmailCode(user.email)
    .onSuccess { codeId ->
        Log.d("User", "验证码已发送: $codeId")
    }
    .bindTo(this)

// 2. 注册
user.signUpByEmail(emailCode)
    .onSuccess { registeredUser ->
        Log.d("User", "注册成功: ${registeredUser.username}")
    }
    .bindTo(this)

// 登录
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { user ->
        Log.d("User", "登录成功: ${user.nickname}")
    }
    .bindTo(this)

// 获取当前用户
FreelyUser.currentUserAs<AppUser>()
    .onSuccess { user ->
        Log.d("User", "当前用户: ${user.username}")
    }
    .bindTo(this)

// 更新资料
val currentUser = FreelyUser.restoreCurrentUserAs<AppUser>()
currentUser?.apply {
    nickname = "张三"
    avatar = "/uploads/avatars/zhangsan.jpg"
}?.updateProfile()
    ?.onSuccess { Log.d("User", "资料更新成功") }
    ?.bindTo(this)

// 退出登录
FreelyUser.logout()
```

---


### FBQuery

查询构建器，支持链式调用和丰富的查询条件。

**快速示例**：
```kotlin
FBQuery<Post>()
    .equalTo("status", "published")
    .greaterThan("viewCount", 100)
    .include("author")
    .order("-createdAt")
    .limit(20)
    .find()
    .onSuccess { posts -> updateUI(posts) }
    .bindTo(this)
```

---

### FBFile

文件管理，封装上传与 URL 解析。

**快速示例**：
```kotlin
// 上传文件
FBFile.upload(file)
    .onSuccess { fbFile ->
        Log.d("File", "URL: ${fbFile.url}")
    }

// 从路径构建
val fbFile = FBFile.fromPath("/uploads/data-files/xxx.jpg")
```

---

### FBGeoPoint

地理位置坐标点，用于存储和查询地理位置信息。

**构造函数**：
```kotlin
FBGeoPoint(latitude: Double, longitude: Double)
```

**主要方法**：
```kotlin
// 计算距离（千米）
fun distanceTo(other: FBGeoPoint): Double

// 计算距离（指定单位）
fun distanceTo(other: FBGeoPoint, unit: DistanceUnit): Double
```

**快速示例**：
```kotlin
// 创建地理位置
val location = FBGeoPoint(39.9042, 116.4074)  // 北京天安门

// 在数据对象中使用
class Store : FreelyObject("Store") {
    var name: String = ""
    var location: FBGeoPoint? = null
}

val store = Store().apply {
    name = "星巴克"
    location = FBGeoPoint(39.9042, 116.4074)
}
store.save()

// 查询附近的店铺
val myLocation = FBGeoPoint(39.9100, 116.4100)
FBQuery<Store>()
    .near("location", myLocation)
    .limit(10)
    .find()
    .onSuccess { stores ->
        stores.forEach { store ->
            val distance = myLocation.distanceTo(store.location!!)
            Log.d("Store", "${store.name}: ${distance}km")
        }
    }
```

**地理位置查询方法**：
```kotlin
// 查询最近的对象（按距离升序）
FBQuery<Store>()
    .near("location", point)
    .find()

// 查询指定范围内的对象（千米）
FBQuery<Store>()
    .withinKilometers("location", point, 5.0)  // 5 公里内
    .find()

// 查询指定范围内的对象（英里）
FBQuery<Store>()
    .withinMiles("location", point, 3.0)  // 3 英里内
    .find()

// 查询矩形区域内的对象
val southwest = FBGeoPoint(39.90, 116.40)
val northeast = FBGeoPoint(39.92, 116.42)
FBQuery<Store>()
    .withinGeoBox("location", southwest, northeast)
    .find()
```

### FBLiveQuery

实时数据监听，基于 WebSocket。

**快速示例**：
```kotlin
val liveQuery = FBLiveQuery<Message>()
    .whereEqualTo("roomId", "room_001")

liveQuery.on(FBLiveQuery.Event.CREATED) { message ->
    runOnUiThread { addMessage(message) }
}

liveQuery.subscribe()  // 开始监听

// 停止监听
liveQuery.unsubscribe()
```

---

## 异步处理

### FBCall

异步调用封装，行为与 Firebase Task 一致。

```kotlin
class FBCall<T>(block: suspend () -> T) {
    /**
     * 配置加载回调（主线程）
     */
    fun onLoading(block: () -> Unit): FBCall<T>
    
    /**
     * 配置成功回调（主线程）
     */
    fun onSuccess(block: (T) -> Unit): FBCall<T>
    
    /**
     * 配置失败回调（主线程）
     */
    fun onFailure(block: (FreelyException) -> Unit): FBCall<T>
    
    /**
     * 配置完成回调（主线程，在 onSuccess/onFailure 之后）
     */
    fun onFinally(block: () -> Unit): FBCall<T>
    
    /**
     * 绑定生命周期（推荐）
     * 使用 Activity/Fragment 的 lifecycleScope，页面销毁时自动取消请求
     */
    fun bindTo(owner: LifecycleOwner): FBCall<T>
    
    /**
     * 在指定 CoroutineScope 中执行（高级用法）
     */
    fun launch(scope: CoroutineScope): FBCall<T>
    
    /**
     * 取消当前请求
     */
    fun cancel()
    
    /**
     * 挂起等待结果，返回 FBResult（在已有协程中使用）
     */
    suspend fun await(): FBResult<T>
}

/**
 * 便捷构造函数
 */
fun <T> fbCall(block: suspend () -> T): FBCall<T>
```

**使用示例**：
```kotlin
// 回调风格
FreelyUser.login("zhangsan", "123456")
    .onLoading { showLoading() }
    .onSuccess { user -> Log.d("User", user.username) }
    .onFailure { error -> showError(error.detail) }
    .onFinally { hideLoading() }
    .bindTo(this)

// 协程风格
lifecycleScope.launch {
    val result = FreelyUser.login("zhangsan", "123456").await()
    when (result) {
        is FBResult.Success -> Log.d("User", result.data.username)
        is FBResult.Failure -> showError(result.error.detail)
    }
}
```

---

### FBResult

结果类型，用于协程中的返回值。

```kotlin
sealed class FBResult<out T> {
    data class Success<T>(val data: T) : FBResult<T>()
    data class Failure(val error: FreelyException) : FBResult<Nothing>()
    
    val isSuccess: Boolean
    val isFailure: Boolean
    
    fun getOrNull(): T?
    fun getOrThrow(): T
    fun getOrDefault(default: T): T
    fun <R> map(transform: (T) -> R): FBResult<R>
}
```

**使用示例**：
```kotlin
lifecycleScope.launch {
    val result = FBQuery<Post>().find().await()
    
    // 方式 1：when 表达式
    when (result) {
        is FBResult.Success -> updateUI(result.data)
        is FBResult.Failure -> showError(result.error)
    }
    
    // 方式 2：getOrNull
    val posts = result.getOrNull()
    if (posts != null) {
        updateUI(posts)
    }
    
    // 方式 3：getOrDefault
    val posts = result.getOrDefault(emptyList())
    updateUI(posts)
    
    // 方式 4：map 转换
    val titles = result.map { posts -> posts.map { it.title } }
}
```

---

## 注解

### @Pointer

标记一对一关联字段。

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pointer
```

**使用示例**：
```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    
    @Pointer
    var author: AppUser? = null  // 一对一关联
}

// 保存时：自动将 author 序列化为 author.objectId
val post = Post().apply {
    title = "Hello"
    author = currentUser
}
post.save<Post>().bindTo(this)

// 查询时（不带 include）：author 为 null
FBQuery<Post>().find()
    .onSuccess { posts ->
        // posts[0].author == null
    }

// 查询时（带 include）：author 自动填充为完整对象
FBQuery<Post>()
    .include("author")
    .find()
    .onSuccess { posts ->
        // posts[0].author?.username 可用
    }
```

---

### @Relation

标记多对多关联字段。

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Relation
```

**使用示例**：
```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    
    @Relation
    var tags: MutableList<Tag> = mutableListOf()  // 多对多关联
}

// 保存时：自动将 tags 序列化为 [tag1.objectId, tag2.objectId, ...]
val post = Post().apply {
    title = "Hello"
    tags.add(tag1)
    tags.add(tag2)
}
post.save<Post>().bindTo(this)

// 查询时（不带 include）：tags 为空列表
FBQuery<Post>().find()
    .onSuccess { posts ->
        // posts[0].tags.isEmpty() == true
    }

// 查询时（带 include）：tags 自动填充为完整对象列表
FBQuery<Post>()
    .include("tags")
    .find()
    .onSuccess { posts ->
        // posts[0].tags[0].name 可用
    }
```

---

## 枚举

### CachePolicy

查询缓存策略。

```kotlin
enum class CachePolicy {
    /**
     * 只走网络，不读写缓存（默认）
     */
    NETWORK_ONLY,
    
    /**
     * 只读缓存，无缓存时抛异常
     */
    CACHE_ONLY,
    
    /**
     * 先读缓存，无缓存再走网络
     */
    CACHE_ELSE_NETWORK,
    
    /**
     * 先走网络，网络失败再读缓存
     */
    NETWORK_ELSE_CACHE,
}
```

**使用示例**：
```kotlin
// 先读缓存，无缓存再走网络
FBQuery<Post>()
    .cachePolicy(CachePolicy.CACHE_ELSE_NETWORK)
    .maxCacheAge(5 * 60 * 1000)  // 5 分钟
    .find()
    .onSuccess { posts -> updateUI(posts) }
```

---

### DistanceUnit

距离单位枚举。

```kotlin
enum class DistanceUnit {
    /** 千米 */
    KILOMETERS,
    
    /** 英里 */
    MILES,
    
    /** 弧度 */
    RADIANS
}
```

**使用示例**：
```kotlin
val point1 = FBGeoPoint(39.9042, 116.4074)
val point2 = FBGeoPoint(39.9100, 116.4100)

// 计算距离（千米）
val distanceKm = point1.distanceTo(point2, DistanceUnit.KILOMETERS)

// 计算距离（英里）
val distanceMiles = point1.distanceTo(point2, DistanceUnit.MILES)
```

---

### FBLiveQuery.Event

实时查询事件类型。

```kotlin
enum class Event {
    CREATED,   // 数据创建
    UPDATED,   // 数据更新
    DELETED    // 数据删除
}
```

---

## 异常

### FreelyException

SDK 异常类。

```kotlin
class FreelyException(
    message: String,
    val statusCode: Int = 0,
    val detail: String = message
) : Exception(message)
```

**常见错误码**：
- `401`: 未授权，请先登录
- `403`: 权限不足
- `404`: 资源不存在
- `429`: 请求过于频繁
- `500`: 服务器内部错误
- `502`: 网关错误
- `503`: 服务不可用

**使用示例**：
```kotlin
FreelyUser.login("zhangsan", "123456")
    .onFailure { error ->
        when (error.statusCode) {
            401 -> showMessage("用户名或密码错误")
            404 -> showMessage("用户不存在")
            else -> showMessage(error.detail)
        }
    }
    .bindTo(this)
```

---

## 使用示例

### 完整的应用示例

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查登录状态
        if (!FreelyBase.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        // 加载数据
        loadPosts()
    }
    
    private fun loadPosts() {
        FBQuery<Post>()
            .equalTo("status", "published")
            .include("author")
            .order("-createdAt")
            .limit(20)
            .cachePolicy(CachePolicy.CACHE_ELSE_NETWORK)
            .find()
            .onLoading {
                showLoading(true)
            }
            .onSuccess { posts ->
                adapter.submitList(posts)
            }
            .onFailure { error ->
                Toast.makeText(this, error.detail, Toast.LENGTH_SHORT).show()
            }
            .onFinally {
                showLoading(false)
            }
            .bindTo(this)
    }
}
```

### 协程用法示例

```kotlin
class PostDetailActivity : AppCompatActivity() {
    
    private fun loadPostDetail(postId: String) {
        lifecycleScope.launch {
            try {
                // 获取文章详情
                val postResult = FBQuery<Post>()
                    .include("author")
                    .getObject(postId)
                    .await()
                
                val post = postResult.getOrThrow()
                
                // 增加浏览量
                post.viewCount++
                post.update<Post>().await()
                
                // 更新 UI
                updateUI(post)
                
            } catch (e: FreelyException) {
                Toast.makeText(this@PostDetailActivity, e.detail, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

---

## 内部实现（不对外暴露）

以下包和类标记为 `internal`，外部无法访问：

- `io.freelybase.kotlin.internal.http.*` - HTTP 客户端实现
- `io.freelybase.kotlin.internal.crypto.*` - 加密实现
- `QueryCache` - 查询缓存管理
- `FreelyObject.toDataMap()` - 序列化方法
- `FreelyObject.fillFromResponse()` - 反序列化方法
- `FreelyUser.applyAuthResponse()` - 认证响应处理
- `FreelyUser.persistSession()` - 会话持久化

---

## 相关文档

- [README.md](./README.md) - 快速开始指南

---

**版本**: 1.0.2  
**最后更新**: 2026-04-20
