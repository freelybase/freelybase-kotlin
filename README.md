# FreelyBase Android Kotlin SDK

基于 Kotlin 的 Android 客户端 SDK，所有网络操作返回 `FBCall<T>`，配置回调后**自动执行**，无需手写协程代码——与 Firebase 的 Task 行为完全一致。

---

## 安装
step.1 将 JitPack 仓库添加到您的构建文件中
```
dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
```
step.2 添加依赖
```
dependencies {
    implementation("com.github.freelybase:freelybase-kotlin-sdk:Tag")
}
```

---

## 初始化
创建Application子类
在 `Application.onCreate()` 中调用：

```
class FreelyBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FreelyBase.initialize(this, appKey = "your_app_key")
    }
}
```

## 创建模型
创建一个继承自 `FreelyObject` 的模型类对应控制台的 Post 表
```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var viewCount: Int = 0
}
```

## 增加一行数据
```
    val post = Post().apply {
        title = "Hello FreelyBase"
        viewCount = 1
    }
    post.save<Post>()
    .onLoading { binding.btnCreate.isEnabled = false }
    .onSuccess { log("✓ 新增成功\n  objectId: ${it.objectId}") }
    .onFailure { log("✗ ${it.detail}") }
    .onFinally { binding.btnCreate.isEnabled = true }
    .bindTo(this)
```
## 查询一行数据
```
FBQuery<Post>().getObject("objectId")
                .onLoading { binding.btnQuery.isEnabled = false }
                .onSuccess { post ->
                    log("✓ 查询成功\n  objectId: ${post.objectId}\n  title: ${post.title}")
                }
                .onFailure { log("✗ ${it.detail}") }
                .onFinally { binding.btnQuery.isEnabled = true }
                .bindTo(this)

```
## 更新一行数据
```

```
## 删除一行数据
```

```
---

## 调用方式

所有网络方法返回 `FBCall<T>`，配置回调后**自动在后台执行**，回调自动切回主线程——与 Firebase 完全一致：

```kotlin
// 直接调用，无需 launch（配置 onSuccess/onFailure 后自动执行）
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onLoading { btnLogin.isEnabled = false }    // 请求开始前（主线程）
    .onSuccess { user -> log(user.username) }     // 成功（主线程）
    .onFailure { e -> toast(e.detail) }           // 失败（主线程，e.statusCode / e.detail）
    .onFinally { btnLogin.isEnabled = true }      // 无论成败都执行（主线程）
```

推荐在 Activity/Fragment 中调用 `.bindTo(this)` 绑定生命周期，页面销毁时自动取消请求（比 Firebase 更安全）：

```kotlin
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { log(it.username) }
    .onFailure { toast(it.detail) }
    .bindTo(this)   // 绑定生命周期，页面销毁时自动取消
```

在已有协程中使用 `.await()` 拿 `FBResult<T>`：

```kotlin
val result = FreelyUser.loginAs<AppUser>("zhangsan", "123456").await()

result.getOrNull()           // AppUser?，失败返回 null
result.getOrThrow()          // AppUser，失败抛 FreelyException
result.getOrDefault(null)    // AppUser?，失败返回默认值

when (result) {
    is FBResult.Success -> startMain(result.data)
    is FBResult.Failure -> toast(result.error.detail)
}
```

---

## 用户认证（FreelyUser）

### 自定义用户类

```kotlin
class AppUser : FreelyUser() {
    var nickname: String = ""
    var avatar: String = ""
    var age: Int = 0
    var city: String = ""
}
```

### 注册
### 邮箱 + 验证码注册
```kotlin
// 邮箱 + 验证码注册
AppUser().apply { email = "user@example.com"; password = "123456"; nickname = "张三" }
    .signUpByEmail(emailCode = "123456")
    .onSuccess { user ->
        user.updateProfile()  // 将自定义字段写入 _User 表
        log("注册成功: ${user.objectId}")
    }
    .onFailure { toast(it.detail) }
```
### 手机号 + 短信验证码注册
```kotlin
// 手机号 + 短信验证码注册
AppUser().apply { mobile = "13800138000"; password = "123456" }
    .signUpBySms(smsCode = "654321")
    .onSuccess { log("注册成功: ${it.objectId}") }
    .onFailure { toast(it.detail) }
```
### 用户名 + 密码注册
```kotlin
// 用户名 + 密码注册（无需验证码）
AppUser().apply { username = "zhangsan"; password = "123456" }
    .signUpByUsername()
    .onSuccess { log("注册成功: ${it.objectId}") }
    .onFailure { toast(it.detail) }
```

### 登录

```kotlin
// 账号（用户名 / 邮箱 / 手机号）+ 密码
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { log(it.username) }
    .onFailure { toast(it.detail) }
```
```
// 邮箱 + 验证码
FreelyUser.loginWithEmailCodeAs<AppUser>("user@example.com", "123456")
    .onSuccess { log(it.username) }
    .onFailure { toast(it.detail) }
```
// 手机号 + 短信验证码
FreelyUser.loginWithSmsCodeAs<AppUser>("13800138000", "654321")
    .onSuccess { log(it.username) }
    .onFailure { toast(it.detail) }
```

### 发送 / 验证验证码

```kotlin
// 发送
FreelyUser.sendEmailCode("user@example.com")
    .onSuccess { toast(it) }
    .onFailure { toast(it.detail) }
```
```kotlin
FreelyUser.sendSmsCode("13800138000")
    .onSuccess { toast(it) }
    .onFailure { toast(it.detail) }
```
```kotlin
// 验证（不消耗，适合表单实时校验）
FreelyUser.verifyEmailCode("user@example.com", "123456")
    .onSuccess { if (it) log("验证码正确") }
```
```kotlin
FreelyUser.verifySmsCode("13800138000", "654321")
    .onSuccess { if (it) log("验证码正确") }
```

### 获取当前用户

```kotlin
// 从本地缓存恢复（不发网络请求，适合启动时判断登录状态）
val user = FreelyUser.restoreCurrentUserAs<AppUser>()
```
```kotlin
// 从服务端获取最新信息
FreelyUser.currentUserAs<AppUser>()
    .onSuccess { log(it.username) }
```

### 更新 / 修改密码

```kotlin
user.updateProfile()
    .onSuccess { log("更新成功") }
    .onFailure { toast(it.detail) }
```
```kotlin
user.changePassword("oldPass", "newPass")
    .onSuccess { log("修改成功") }
    .onFailure { toast(it.detail) }
```
```kotlin
// 重置密码（邮箱验证码）
user.resetPassword(emailCode = "123456", newPassword = "newPass")
    .onSuccess { log("重置成功") }
```
```kotlin
// 重置密码（短信验证码）
user.resetPasswordBySms(smsCode = "654321", newPassword = "newPass")
    .onSuccess { log("重置成功") }
```

### 退出登录

```kotlin
FreelyUser.logout()
```

---

## 第三方账号

支持平台：`weibo` / `qq` / `weixin`，SDK 与第三方平台 SDK 解耦。

```kotlin
// 一键注册或登录（有绑定记录则登录，否则自动注册）
FreelyUser.loginWithAuthDataAs<AppUser>(
    snsType = "weixin", accessToken = "xxx", expiresIn = "7200", userId = "openid_xxx"
).onSuccess { log(it.username) }.onFailure { toast(it.detail) }
```
```kotlin
// 关联第三方账号（需已登录）
FreelyUser.associateWithAuthData("qq", "xxx", "7200", "openid_xxx")
    .onSuccess { log("关联成功") }
```
```kotlin
// 解除关联（需已登录）
FreelyUser.dissociateAuthData("qq")
    .onSuccess { log("已解除") }
```

---

## 数据操作（FreelyObject + FBQuery）

### 定义数据模型

```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var viewCount: Int = 0
    var tags: List<String> = emptyList()

    @Pointer
    var author: AppUser? = null

    @Relation
    var likes: MutableList<AppUser> = mutableListOf()
}
```

### CRUD

```kotlin
// 创建
Post().apply { title = "Hello" }
    .save()
    .onSuccess { log("objectId: ${it.objectId}") }
    .onFailure { toast(it.detail) }

// 读取
post.fetch()
    .onSuccess { log(it.objectId) }

// 更新
post.title = "Updated"
post.update()
    .onSuccess { log("更新成功") }

// 删除
post.delete()
    .onSuccess { log("删除成功") }
```

### 查询

```kotlin
FBQuery<Post>()
    .equalTo("title", "Hello")
    .greaterThan("viewCount", 100)
    .contains("title", "关键词")
    .exists("avatar")
    .containedIn("status", listOf("active", "pending"))
    .after("createdAt", Date())
    .or { equalTo("status", "active").greaterThan("score", 90) }
    .include("author", "likes")
    .order("-createdAt")
    .page(1, pageSize = 20)
    .find()
    .onSuccess { posts -> log("共 ${posts.size} 条") }
    .onFailure { toast(it.detail) }

// 查询单条
FBQuery<Post>().getObject("objectId_xxx")
    .onSuccess { log(it.title) }

// 计数
FBQuery<Post>().equalTo("status", "active").count()
    .onSuccess { log("共 $it 条") }

// 第一条
FBQuery<Post>().order("-createdAt").first()
    .onSuccess { post -> post?.let { log(it.title) } }
```

### 子查询

```kotlin
// 在协程中使用 await() 串联子查询
fbCall {
    val userQuery = FBQuery<AppUser>().equalTo("city", "成都")
    FBQuery<Post>().matchesQuery("author", userQuery).find().await().getOrThrow()
}.onSuccess { posts -> log("${posts.size} 条") }
```

### 查询缓存

```kotlin
FBQuery<Post>()
    .cachePolicy(CachePolicy.CACHE_ELSE_NETWORK)
    .maxCacheAge(5 * 60 * 1000L)
    .find()
    .onSuccess { log("${it.size} 条") }

FreelyBase.clearQueryCache()
```

---

## 文件操作（FBFile）

```kotlin
// 上传 Android Uri（推荐）
FBFile.upload(contentResolver, uri)
    .onLoading { showProgress(true) }
    .onSuccess { file -> log("url: ${file.url}") }
    .onFailure { toast(it.detail) }
    .onFinally { showProgress(false) }

// 上传字节数组
FBFile.upload(bytes, "photo.jpg")
    .onSuccess { log(it.path) }

// 从路径构建（不发网络请求）
val file = FBFile.fromPath("/uploads/data-files/xxx.jpg")
println(file.url)
```

### 在数据模型中使用

```kotlin
class Post : FreelyObject("Post") {
    var cover: FBFile? = null
}

Post().apply { cover = FBFile.fromPath(uploadedPath) }
    .save()
    .onSuccess { log(it.objectId) }
```

---

## 数据监听（FBLiveQuery）

```kotlin
val liveQuery = FBLiveQuery<Message>()
    .whereEqualTo("roomId", "room_001")
    .on(FBLiveQuery.Event.CREATED) { msg -> runOnUiThread { log(msg.content) } }
    .on(FBLiveQuery.Event.UPDATED) { msg -> runOnUiThread { log(msg.objectId) } }
    .on(FBLiveQuery.Event.DELETED) { msg -> runOnUiThread { log(msg.objectId) } }
    .onConnected { runOnUiThread { log("已连接") } }
    .onDisconnected { runOnUiThread { log("已断开") } }
    .onError { e -> runOnUiThread { log(e.message ?: "") } }
```
```kotlin
liveQuery.subscribe()    // 开始监听（需已登录）
liveQuery.unsubscribe()  // 停止监听
```

---

## 关联关系

```kotlin
// @Pointer 多对一：保存时存 objectId，查询时需 include 展开
class Post : FreelyObject("Post") {
    @Pointer var author: AppUser? = null
}
FBQuery<Post>().include("author").find()
    .onSuccess { posts -> log(posts[0].author?.username ?: "") }

// @Relation 多对多：保存时存 objectId 列表，查询时需 include 展开
class Post : FreelyObject("Post") {
    @Relation var likes: MutableList<AppUser> = mutableListOf()
}
FBQuery<Post>().include("likes").find()
    .onSuccess { posts -> log("${posts[0].likes.size} 个点赞") }
```

---

## 其他工具

```kotlin
// 获取服务器时间
FreelyBase.getServerTime()
    .onSuccess { log(it) }

// 登录状态
FreelyBase.isLoggedIn()

// 底层 HTTP（高级用法，在协程中使用）
fbCall { FreelyBase.get("/public/apps/{appKey}/path") }
    .onSuccess { log(it.toString()) }
```

---

## 功能一览

| 模块 | 功能 |
|------|------|
| FreelyUser | 用户名/密码、邮箱验证码、手机短信 注册登录；修改/重置密码；更新资料；第三方账号关联；自定义用户类 |
| FreelyObject | save / fetch / update / delete；Pointer/Relation 自动序列化 |
| FBQuery | 条件/模糊/时间/子查询、OR、排序分页、关联展开、查询缓存 |
| FBFile | 上传 File / ByteArray / InputStream / Uri；与 FreelyObject 字段集成 |
| FBLiveQuery | WebSocket 实时监听增删改；客户端过滤；断线自动重连 |
| FBCall | 链式回调（onLoading/onSuccess/onFailure/onFinally）+ 自动执行；可选 bindTo() 绑定生命周期 |
| FBResult | await() 返回值；getOrNull/getOrThrow/getOrDefault；when 分支 |
