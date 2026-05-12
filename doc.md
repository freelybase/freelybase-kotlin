# FreelyBase 开发文档

## 快速开始

### 创建应用

1. 访问 [FreelyBase 控制台](https://www.freelybase.com)
2. 注册并登录账号
3. 点击「创建应用」按钮
4. 填写应用名称和描述
5. 创建成功后，在应用详情页获取 AppKey

### 获取 AppKey

在控制台的应用详情页面，可以看到：
- App Key：用于 SDK 初始化的密钥

### 安装与配置 SDK

在项目的 `settings.gradle.kts` 中添加 Maven 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

在 app 模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.freelybase:freelybase-kotlin-sdk:1.0.0")
}
```

在 Application 类中初始化 SDK：

```kotlin
class FreelyBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FreelyBase.initialize(
            context = this,
            appKey = "your_app_key"
        )
    }
}
```

在 `AndroidManifest.xml` 中注册 Application：

```xml
<application
    android:name=".FreelyBaseApp"
    ...>
</application>
```

### 增加一行数据

定义数据类：

```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var content: String = ""
    var viewCount: Int = 0
}
```

保存数据：

```kotlin
val post = Post().apply {
    title = "我的第一篇文章"
    content = "这是文章内容"
    viewCount = 0
}

post.save()
    .onSuccess { savedPost ->
        Log.d("Post", "保存成功，ID: ${savedPost.objectId}")
    }
    .onFailure { error ->
        Log.e("Post", "保存失败: ${error.detail}")
    }
```

### 获取一行数据

根据 objectId 获取：

```kotlin
val post = Post().apply {
    objectId = "objectId"
}

post.fetch()
    .onSuccess { fetchedPost ->
        Log.d("Post", "标题: ${fetchedPost.title}")
    }
    .onFailure { error ->
        Log.e("Post", "获取失败: ${error.detail}")
    }
```

通过查询获取：

```kotlin
FBQuery<Post>()
    .equalTo("title", "我的第一篇文章")
    .first()
    .onSuccess { post ->
        if (post != null) {
            Log.d("Post", "找到文章: ${post.title}")
        } else {
            Log.d("Post", "未找到匹配的文章")
        }
    }
```

### 修改一行数据

```kotlin
val post = Post().apply {
    objectId = "objectId"
}

// 先获取最新数据
post.fetch()
    .onSuccess { fetchedPost ->
        // 修改字段
        fetchedPost.viewCount += 1
        
        // 更新到服务器
        fetchedPost.update()
            .onSuccess { updatedPost ->
                Log.d("Post", "更新成功，浏览量: ${updatedPost.viewCount}")
            }
    }
```

或者直接修改并更新：

```kotlin
val post = Post().apply {
    objectId = "objectId"
    title = "修改后的标题"
    content = "修改后的内容"
}

post.update()
    .onSuccess { updatedPost ->
        Log.d("Post", "更新成功")
    }
```

### 删除一行数据

```kotlin
val post = Post().apply {
    objectId = "objectId"
}

post.delete()
    .onSuccess {
        Log.d("Post", "删除成功")
    }
    .onFailure { error ->
        Log.e("Post", "删除失败: ${error.detail}")
    }
```

## 用户与认证

### 注册

#### 1. 手机号注册

```kotlin
// 先发送短信验证码
FreelyUser.sendSmsCode("13800138000")
    .onSuccess { message ->
        Log.d("Auth", message)  // "验证码已发送"
    }

// 用户输入验证码后注册
val user = FreelyUser().apply {
    mobile = "13800138000"
    password = "123456"
    username = "张三"  // 可选
}

user.signUpBySms("123456")
    .onSuccess { registeredUser ->
        Log.d("Auth", "注册成功，用户名: ${registeredUser.username}")
    }
    .onFailure { error ->
        Log.e("Auth", "注册失败: ${error.detail}")
    }
```

#### 2. 邮箱注册

```kotlin
// 先发送邮箱验证码
FreelyUser.sendEmailCode("user@example.com")
    .onSuccess { message ->
        Log.d("Auth", message)
    }

// 用户输入验证码后注册
val user = FreelyUser().apply {
    email = "user@example.com"
    password = "123456"
    username = "张三"  // 可选
}

user.signUpByEmail("123456")
    .onSuccess { registeredUser ->
        Log.d("Auth", "注册成功")
    }
```

#### 3. 用户名密码注册

```kotlin
val user = FreelyUser().apply {
    username = "zhangsan"
    password = "123456"
}

user.signUpByUsername()
    .onSuccess { registeredUser ->
        Log.d("Auth", "注册成功")
    }
```

### 登录

#### 1. 手机号 + 验证码登录

```kotlin
// 先发送短信验证码
FreelyUser.sendSmsCode("13800138000")
    .onSuccess { message ->
        Log.d("Auth", message)
    }

// 用户输入验证码后登录
FreelyUser.loginWithSmsCodeAs<AppUser>("13800138000", "123456")
    .onSuccess { user ->
        Log.d("Auth", "登录成功，用户名: ${user.username}")
    }
```

#### 2. 手机号 + 密码登录

```kotlin
FreelyUser.loginAs<AppUser>("13800138000", "123456")
    .onSuccess { user ->
        Log.d("Auth", "登录成功")
    }
```

#### 3. 邮箱 + 验证码登录

```kotlin
// 先发送邮箱验证码
FreelyUser.sendEmailCode("user@example.com")
    .onSuccess { message ->
        Log.d("Auth", message)
    }

// 用户输入验证码后登录
FreelyUser.loginWithEmailCodeAs<AppUser>("user@example.com", "123456")
    .onSuccess { user ->
        Log.d("Auth", "登录成功")
    }
```

#### 4. 邮箱 + 密码登录

```kotlin
FreelyUser.loginAs<AppUser>("user@example.com", "123456")
    .onSuccess { user ->
        Log.d("Auth", "登录成功")
    }
```

#### 5. 用户名 + 密码登录

```kotlin
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { user ->
        Log.d("Auth", "登录成功，邮箱: ${user.email}")
    }
```

### 找回密码

#### 1. 手机号 + 验证码重置密码

```kotlin
// 先发送短信验证码
FreelyUser.sendSmsCode("13800138000")
    .onSuccess { message ->
        Log.d("Auth", message)
    }

// 用户输入验证码后重置密码
val user = FreelyUser().apply {
    mobile = "13800138000"
}

user.resetPasswordBySms("123456", "new_password")
    .onSuccess {
        Log.d("Auth", "密码重置成功")
    }
```

#### 2. 邮箱 + 验证码重置密码

```kotlin
// 先发送邮箱验证码
FreelyUser.sendEmailCode("user@example.com")
    .onSuccess { message ->
        Log.d("Auth", message)
    }

// 用户输入验证码后重置密码
val user = FreelyUser().apply {
    email = "user@example.com"
}

user.resetPassword("123456", "new_password")
    .onSuccess {
        Log.d("Auth", "密码重置成功")
    }
```

### 验证码服务

#### 1. 发送短信验证码

```kotlin
FreelyUser.sendSmsCode("13800138000")
    .onSuccess { message ->
        Log.d("Auth", message)  // "验证码已发送"
    }
    .onFailure { error ->
        Log.e("Auth", "发送失败: ${error.detail}")
    }
```

#### 2. 验证短信验证码

```kotlin
FreelyUser.verifySmsCode("13800138000", "123456")
    .onSuccess { isValid ->
        if (isValid) {
            Log.d("Auth", "验证码正确")
        } else {
            Log.d("Auth", "验证码错误")
        }
    }
```

#### 3. 发送邮件验证码

```kotlin
FreelyUser.sendEmailCode("user@example.com")
    .onSuccess { message ->
        Log.d("Auth", message)
    }
```

#### 4. 验证邮件验证码

```kotlin
FreelyUser.verifyEmailCode("user@example.com", "123456")
    .onSuccess { isValid ->
        if (isValid) {
            Log.d("Auth", "验证码正确")
        } else {
            Log.d("Auth", "验证码错误")
        }
    }
```

### 自定义用户类

```kotlin
class AppUser : FreelyUser() {
    var nickname: String = ""
    var avatar: FBFile? = null
    var age: Int = 0
    var gender: String = ""
}

// 使用自定义用户类登录
FreelyUser.loginAs<AppUser>("zhangsan", "123456")
    .onSuccess { user ->
        Log.d("Auth", "昵称: ${user.nickname}")
        Log.d("Auth", "头像: ${user.avatar?.url}")
    }
```

### 用户信息管理

#### 1. 获取本地用户信息

```kotlin
// 从本地缓存恢复用户信息（不发起网络请求）
val user = FreelyUser.restoreCurrentUserAs<AppUser>()
if (user != null) {
    Log.d("Auth", "用户名: ${user.username}")
} else {
    Log.d("Auth", "未登录")
}
```

#### 2. 获取远程用户信息

```kotlin
// 从服务器获取最新用户信息
FreelyUser.currentUserAs<AppUser>()
    .onSuccess { user ->
        Log.d("Auth", "用户名: ${user.username}")
        Log.d("Auth", "邮箱: ${user.email}")
        Log.d("Auth", "昵称: ${user.nickname}")
    }
```

#### 3. 更新用户信息

```kotlin
FreelyUser.currentUserAs<AppUser>()
    .onSuccess { user ->
        // 修改用户信息
        user.nickname = "新昵称"
        user.age = 25
        
        // 更新到服务器
        user.updateProfile()
            .onSuccess {
                Log.d("Auth", "更新成功")
            }
    }
```

修改密码：

```kotlin
FreelyUser.currentUserAs<AppUser>()
    .onSuccess { user ->
        user.changePassword("old_password", "new_password")
            .onSuccess {
                Log.d("Auth", "密码修改成功")
            }
    }
```

### 第三方登录

#### 1. 第三方平台一键注册/登录

```kotlin
// 微博登录
FreelyUser.loginWithAuthDataAs<AppUser>(
    snsType = "weibo",
    accessToken = "weibo_access_token",
    expiresIn = "86400",
    userId = "weibo_uid"
).onSuccess { user ->
    Log.d("Auth", "微博登录成功")
}

// QQ 登录
FreelyUser.loginWithAuthDataAs<AppUser>(
    snsType = "qq",
    accessToken = "qq_access_token",
    expiresIn = "7776000",
    userId = "qq_openid"
).onSuccess { user ->
    Log.d("Auth", "QQ 登录成功")
}

// 微信登录
FreelyUser.loginWithAuthDataAs<AppUser>(
    snsType = "weixin",
    accessToken = "weixin_access_token",
    expiresIn = "7200",
    userId = "weixin_openid"
).onSuccess { user ->
    Log.d("Auth", "微信登录成功")
}
```

#### 2. 关联第三方平台

```kotlin
// 将微博账号关联到当前用户
FreelyUser.associateWithAuthData(
    snsType = "weibo",
    accessToken = "weibo_access_token",
    expiresIn = "86400",
    userId = "weibo_uid"
).onSuccess {
    Log.d("Auth", "关联成功")
}
```

#### 3. 解除关联

```kotlin
FreelyUser.dissociateAuthData("weibo")
    .onSuccess {
        Log.d("Auth", "解除关联成功")
    }
```

### 退出登录

```kotlin
FreelyUser.logout()
Log.d("Auth", "已退出登录")
```

## 数据操作

### 创建数据对象

定义数据类：

```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var content: String = ""
    var author: AppUser? = null  // Pointer 字段
    var tags: List<String> = emptyList()
    var viewCount: Int = 0
    var isPublished: Boolean = false
}
```

创建并保存：

```kotlin
val post = Post().apply {
    title = "Kotlin 协程入门"
    content = "协程是 Kotlin 的一大特色..."
    tags = listOf("Kotlin", "协程", "Android")
    viewCount = 0
    isPublished = true
}

post.save()
    .onSuccess { savedPost ->
        Log.d("Post", "保存成功，ID: ${savedPost.objectId}")
    }
```

### 数据关联

在程序设计中，不同类型的数据之间可能存在某种关系。比如：帖子和作者的关系，一篇帖子只属于某个作者，这是一对一的关系。比如：帖子和评论的关系，一条评论只属于某一篇帖子，而一篇帖子对应有很多条评论，这是一对多的关系。比如：学生和课程的关系，一个学生可以选择很多课程，一个课程也可以被很多学生所选择，这是多对多的关系。

FreelyBase 提供了 Pointer（一对一、一对多）和 Relation（多对多）两种数据类型来解决这种业务需求。

#### 一对一关联（Pointer）

定义数据类：

```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var content: String = ""
    
    @Pointer
    var author: AppUser? = null  // 一对一关联
}

class AppUser : FreelyUser() {
    var nickname: String = ""
}
```

创建关联数据：

```kotlin
// 方式 1：直接使用已有的用户对象
val currentUser = FreelyUser.restoreCurrentUserAs<AppUser>()
val post = Post().apply {
    title = "我的文章"
    content = "文章内容"
    author = currentUser  // 设置关联
}

post.save<Post>()
    .onSuccess { savedPost ->
        Log.d("Post", "文章保存成功，作者 ID: ${savedPost.author?.objectId}")
    }

// 方式 2：只设置 objectId（更简洁）
val post = Post().apply {
    title = "我的文章"
    content = "文章内容"
    author = AppUser().apply { 
        objectId = "user_object_id"  // 只需要 objectId
    }
}

post.save<Post>()
    .onSuccess { savedPost ->
        Log.d("Post", "文章保存成功")
    }
```

查询时展开关联字段：

```kotlin
FBQuery<Post>()
    .include("author")  // 展开 author 字段
    .find()
    .onSuccess { posts ->
        posts.forEach { post ->
            Log.d("Post", "标题: ${post.title}")
            Log.d("Post", "作者: ${post.author?.username}")
            Log.d("Post", "作者昵称: ${post.author?.nickname}")
        }
    }
```

#### 多对多关联（Relation）

定义数据类：

```kotlin
class Student : FreelyObject("Student") {
    var name: String = ""
    
    @Relation
    var courses: MutableList<Course> = mutableListOf()  // 多对多关联
}

class Course : FreelyObject("Course") {
    var name: String = ""
    var teacher: String = ""
}
```

创建关联数据：

```kotlin
// 方式 1：使用完整的对象
val course1 = Course().apply {
    name = "Kotlin 编程"
    teacher = "张老师"
}

val course2 = Course().apply {
    name = "Android 开发"
    teacher = "李老师"
}

// 先保存课程
course1.save<Course>()
    .onSuccess { savedCourse1 ->
        course2.save<Course>()
            .onSuccess { savedCourse2 ->
                // 创建学生并关联课程
                val student = Student().apply {
                    name = "张三"
                    courses = mutableListOf(savedCourse1, savedCourse2)
                }
                
                student.save<Student>()
                    .onSuccess { savedStudent ->
                        Log.d("Student", "学生保存成功，选课数: ${savedStudent.courses.size}")
                    }
            }
    }

// 方式 2：只使用 objectId（更简洁）
val student = Student().apply {
    name = "张三"
    courses = mutableListOf(
        Course().apply { objectId = "course_id_1" },
        Course().apply { objectId = "course_id_2" }
    )
}

student.save<Student>()
    .onSuccess { savedStudent ->
        Log.d("Student", "学生保存成功")
    }
```

查询时展开关联字段：

```kotlin
FBQuery<Student>()
    .include("courses")  // 展开 courses 字段
    .find()
    .onSuccess { students ->
        students.forEach { student ->
            Log.d("Student", "学生: ${student.name}")
            student.courses.forEach { course ->
                Log.d("Student", "  课程: ${course.name}, 老师: ${course.teacher}")
            }
        }
    }
```

### 查询数据

#### 比较查询

等于：

```kotlin
FBQuery<Post>()
    .equalTo("title", "Kotlin 协程入门")
    .find()
    .onSuccess { posts ->
        Log.d("Query", "找到 ${posts.size} 篇文章")
    }
```

不等于：

```kotlin
FBQuery<Post>()
    .notEqualTo("isPublished", false)
    .find()
```

小于：

```kotlin
FBQuery<Post>()
    .lessThan("viewCount", 100)
    .find()
```

小于等于：

```kotlin
FBQuery<Post>()
    .lessThanOrEqual("viewCount", 100)
    .find()
```

大于：

```kotlin
FBQuery<Post>()
    .greaterThan("viewCount", 1000)
    .find()
```

大于等于：

```kotlin
FBQuery<Post>()
    .greaterThanOrEqual("viewCount", 1000)
    .find()
```

#### 子查询

查询匹配多个值的数据（如查询 "Barbie"、"Joe"、"Julia" 三个人的成绩）：

```kotlin
FBQuery<Post>()
    .containedIn("author", listOf("Barbie", "Joe", "Julia"))
    .find()
    .onSuccess { posts ->
        Log.d("Query", "找到 ${posts.size} 篇文章")
    }
```

查询不匹配多个值的数据：

```kotlin
FBQuery<Post>()
    .notContainedIn("status", listOf("draft", "deleted"))
    .find()
```

#### 时间查询

某个时间：

```kotlin
val date = Date()
FBQuery<Post>()
    .atTime("createdAt", date)
    .find()
```

某个时间外：

```kotlin
FBQuery<Post>()
    .notAtTime("createdAt", date)
    .find()
```

某个时间前：

```kotlin
FBQuery<Post>()
    .before("createdAt", date)
    .find()
```

某个时间及以前：

```kotlin
FBQuery<Post>()
    .beforeOrAt("createdAt", date)
    .find()
```

某个时间后：

```kotlin
FBQuery<Post>()
    .after("createdAt", date)
    .find()
```

某个时间及以后：

```kotlin
FBQuery<Post>()
    .afterOrAt("createdAt", date)
    .find()
```

期间：

```kotlin
val startDate = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)  // 7 天前
val endDate = Date()

FBQuery<Post>()
    .between("createdAt", startDate, endDate)
    .find()
    .onSuccess { posts ->
        Log.d("Query", "最近 7 天的文章: ${posts.size} 篇")
    }
```

#### 数组查询

查询数组字段包含指定值：

```kotlin
FBQuery<Post>()
    .containedIn("tags", listOf("Kotlin", "Android"))
    .find()
    .onSuccess { posts ->
        Log.d("Query", "找到包含 Kotlin 或 Android 标签的文章")
    }
```

#### 模糊查询

查询 username 字段的值含有 "sm" 的数据：

```kotlin
FBQuery<Post>()
    .contains("username", "sm")
    .find()
```

查询 username 字段的值是以 "sm" 字开头的数据：

```kotlin
FBQuery<Post>()
    .startsWith("username", "sm")
    .find()
```

查询 username 字段的值是以 "ile" 字结尾的数据：

```kotlin
FBQuery<Post>()
    .endsWith("username", "ile")
    .find()
```

使用正则表达式查询：

```kotlin
// 查询邮箱格式的数据
FBQuery<Post>()
    .matches("email", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    .find()

// 查询手机号格式的数据
FBQuery<Post>()
    .matches("mobile", "^1[3-9]\\d{9}$")
    .find()
```

#### 分页查询

```kotlin
// 第 1 页，每页 20 条
FBQuery<Post>()
    .page(1, 20)
    .find()
    .onSuccess { posts ->
        Log.d("Query", "第 1 页: ${posts.size} 条")
    }

// 第 2 页
FBQuery<Post>()
    .page(2, 20)
    .find()
```

或者使用 skip 和 limit：

```kotlin
FBQuery<Post>()
    .skip(20)   // 跳过前 20 条
    .limit(20)  // 返回 20 条
    .find()
```

#### 排序查询

升序排序：

```kotlin
FBQuery<Post>()
    .order("viewCount")  // 按浏览量升序
    .find()
```

降序排序：

```kotlin
FBQuery<Post>()
    .order("-createdAt")  // 按创建时间降序（使用 - 前缀）
    .find()
```

#### 复合查询

与查询（AND）：

```kotlin
FBQuery<Post>()
    .equalTo("isPublished", true)
    .greaterThan("viewCount", 100)
    .contains("title", "Kotlin")
    .find()
    .onSuccess { posts ->
        Log.d("Query", "已发布且浏览量>100且标题包含Kotlin的文章")
    }
```

或查询（OR）：

```kotlin
FBQuery<Post>()
    .equalTo("status", "published")
    .or {
        greaterThan("viewCount", 1000)
        contains("title", "热门")
    }
    .find()
    .onSuccess { posts ->
        Log.d("Query", "已发布或浏览量>1000或标题包含热门的文章")
    }
```

#### 查询结果计数

```kotlin
FBQuery<Post>()
    .equalTo("isPublished", true)
    .count()
    .onSuccess { count ->
        Log.d("Query", "已发布的文章数: $count")
    }
```

#### 查询指定列

只返回指定字段，减少数据传输量：

```kotlin
FBQuery<Post>()
    .select("title", "viewCount")  // 只返回标题和浏览量
    .find()
    .onSuccess { posts ->
        posts.forEach { post ->
            Log.d("Query", "标题: ${post.title}, 浏览量: ${post.viewCount}")
            // post.content 为空
        }
    }
```

#### 缓存查询

设置缓存策略：

```kotlin
// 先读缓存，无缓存再走网络
FBQuery<Post>()
    .cachePolicy(CachePolicy.CACHE_ELSE_NETWORK)
    .maxCacheAge(5 * 60 * 1000)  // 缓存有效期 5 分钟
    .find()
    .onSuccess { posts ->
        Log.d("Query", "查询结果（可能来自缓存）")
    }
```

缓存策略：
- `NETWORK_ONLY`：只走网络，不读写缓存（默认）
- `CACHE_ONLY`：只读缓存，无缓存时抛异常
- `CACHE_ELSE_NETWORK`：先读缓存，无缓存再走网络
- `NETWORK_ELSE_CACHE`：先走网络，网络失败再读缓存

#### 关联表数据查询

展开 Pointer 字段：

```kotlin
FBQuery<Post>()
    .include("author")  // 展开 author 字段
    .find()
    .onSuccess { posts ->
        posts.forEach { post ->
            Log.d("Query", "作者: ${post.author?.username}")
        }
    }
```

展开多个字段：

```kotlin
FBQuery<Post>()
    .include("author", "category")
    .find()
```

### 修改数据

```kotlin
// 方式 1：先查询再修改
FBQuery<Post>()
    .equalTo("objectId", "post_id")
    .first()
    .onSuccess { post ->
        if (post != null) {
            post.viewCount += 1
            post.update()
                .onSuccess { updatedPost ->
                    Log.d("Post", "浏览量更新成功")
                }
        }
    }

// 方式 2：直接修改
val post = Post().apply {
    objectId = "post_id"
    title = "新标题"
    content = "新内容"
}

post.update()
    .onSuccess { updatedPost ->
        Log.d("Post", "更新成功")
    }
```

### 删除数据

```kotlin
val post = Post().apply {
    objectId = "post_id"
}

post.delete()
    .onSuccess {
        Log.d("Post", "删除成功")
    }
```

批量删除：

```kotlin
FBQuery<Post>()
    .lessThan("viewCount", 10)
    .find()
    .onSuccess { posts ->
        posts.forEach { post ->
            post.delete()
                .onSuccess {
                    Log.d("Post", "删除文章: ${post.title}")
                }
        }
    }
```

### 数据监听

实时监听数据变化（需要开通数据监听套餐）。

使用方法：

1. 创建 FBLiveQuery 对象并设置过滤条件
2. 使用 `on()` 方法监听事件（CREATED、UPDATED、DELETED）
3. 使用 `onConnected()`、`onDisconnected()`、`onError()` 监听连接状态
4. 调用 `subscribe()` 开始监听
5. 在页面销毁时调用 `unsubscribe()` 停止监听

```kotlin
// 创建实时查询并设置过滤条件
val liveQuery = FBLiveQuery<Message>()
    .whereEqualTo("roomId", "room_001")

// 监听事件
liveQuery.on(FBLiveQuery.Event.CREATED) { message ->
    runOnUiThread { /* 更新 UI */ }
}

liveQuery.on(FBLiveQuery.Event.UPDATED) { message ->
    runOnUiThread { /* 更新 UI */ }
}

liveQuery.on(FBLiveQuery.Event.DELETED) { message ->
    runOnUiThread { /* 更新 UI */ }
}

// 监听连接状态
liveQuery.onConnected { /* 连接成功 */ }
liveQuery.onDisconnected { /* 连接断开 */ }
liveQuery.onError { error -> /* 处理错误 */ }

// 开始监听
liveQuery.subscribe()

// 停止监听（在页面销毁时调用）
liveQuery.unsubscribe()
```

支持的事件类型：
- `FBLiveQuery.Event.CREATED` - 新增数据
- `FBLiveQuery.Event.UPDATED` - 更新数据
- `FBLiveQuery.Event.DELETED` - 删除数据

支持的过滤方法：
- `whereEqualTo(field, value)` - 字段等于指定值
- `whereNotEqualTo(field, value)` - 字段不等于指定值
- `whereGreaterThan(field, value)` - 字段大于指定值
- `whereLessThan(field, value)` - 字段小于指定值
- `whereContains(field, value)` - 字段包含指定字符串

注意事项：
- 监听回调在后台线程执行，更新 UI 需要切换到主线程
- 记得在页面销毁时取消监听，避免内存泄漏
- 可以链式调用多个过滤条件

## 文件管理

### 上传文件

上传本地文件：

```kotlin
val file = File("/path/to/photo.jpg")

FBFile.upload(file)
    .onSuccess { fbFile ->
        Log.d("File", "上传成功")
        Log.d("File", "路径: ${fbFile.path}")
        Log.d("File", "URL: ${fbFile.url}")
        Log.d("File", "文件名: ${fbFile.name}")
    }
    .onFailure { error ->
        Log.e("File", "上传失败: ${error.detail}")
    }
```

上传字节数组：

```kotlin
val bytes = byteArrayOf(/* ... */)

FBFile.upload(bytes, "photo.jpg", "image/jpeg")
    .onSuccess { fbFile ->
        Log.d("File", "上传成功: ${fbFile.url}")
    }
```

上传 InputStream：

```kotlin
val inputStream = contentResolver.openInputStream(uri)

FBFile.upload(inputStream!!, "photo.jpg", "image/jpeg")
    .onSuccess { fbFile ->
        Log.d("File", "上传成功: ${fbFile.url}")
    }
```

上传 Android Uri（自动解析文件名和 MIME 类型）：

```kotlin
// 从相册选择图片后
val uri: Uri = data.data!!

FBFile.upload(contentResolver, uri)
    .onSuccess { fbFile ->
        Log.d("File", "上传成功: ${fbFile.url}")
    }
```

### 在数据对象中使用文件

定义包含文件字段的数据类：

```kotlin
class Post : FreelyObject("Post") {
    var title: String = ""
    var cover: FBFile? = null  // 封面图片
}
```

上传文件并保存到数据对象：

```kotlin
val file = File("/path/to/cover.jpg")

FBFile.upload(file)
    .onSuccess { fbFile ->
        // 创建文章并设置封面
        val post = Post().apply {
            title = "我的文章"
            cover = fbFile
        }
        
        post.save<Post>()
            .onSuccess { savedPost ->
                Log.d("Post", "文章保存成功")
                Log.d("Post", "封面URL: ${savedPost.cover?.url}")
            }
    }
```

查询并显示文件：

```kotlin
FBQuery<Post>()
    .first()
    .onSuccess { post ->
        if (post != null && post.cover != null) {
            // 使用 Glide 或其他图片加载库加载图片
            Glide.with(this)
                .load(post.cover!!.url)
                .into(imageView)
        }
    }
```

### 从已有路径构建 FBFile

```kotlin
// 从数据库读取的路径字符串
val path = "/uploads/data-files/xxx.jpg"
val fbFile = FBFile.fromPath(path)

Log.d("File", "URL: ${fbFile.url}")
Log.d("File", "文件名: ${fbFile.name}")
```

从完整 URL 构建：

```kotlin
val url = "https://www.freelybase.com/api/uploads/data-files/xxx.jpg"
val fbFile = FBFile.fromUrl(url)

Log.d("File", "路径: ${fbFile.path}")
```

### 文件访问权限

文件上传后默认是公开访问的，可以通过完整 URL 直接访问。如需设置访问权限，请在控制台的「文件管理」中配置。

## 工具方法

### 获取服务器时间

获取服务器当前时间，用于时间同步或时间戳校验：

```kotlin
FreelyBase.getServerTime()
    .onSuccess { serverTime ->
        Log.d("Time", "服务器时间: $serverTime")
        // serverTime 格式: "2026-04-21T10:30:00.123Z"
    }
```

使用场景：

1. **时间同步**：客户端时间可能不准确，使用服务器时间作为基准
2. **时间戳校验**：验证数据的创建或更新时间
3. **倒计时功能**：基于服务器时间计算剩余时间

完整示例：

```kotlin
class CountdownActivity : AppCompatActivity() {
    
    private var serverTimeOffset: Long = 0  // 服务器时间与本地时间的偏移量
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 同步服务器时间
        syncServerTime()
    }
    
    private fun syncServerTime() {
        val localTime = System.currentTimeMillis()
        
        FreelyBase.getServerTime()
            .onSuccess { serverTimeStr ->
                // 解析服务器时间
                val serverTime = parseIsoTime(serverTimeStr)
                
                // 计算偏移量
                serverTimeOffset = serverTime - localTime
                
                Log.d("Time", "时间偏移: ${serverTimeOffset}ms")
                
                // 开始倒计时
                startCountdown()
            }
    }
    
    private fun parseIsoTime(isoString: String): Long {
        // 解析 ISO 8601 格式时间
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.parse(isoString)?.time ?: System.currentTimeMillis()
    }
    
    private fun getCurrentServerTime(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }
    
    private fun startCountdown() {
        // 活动结束时间（服务器时间）
        val endTime = parseIsoTime("2026-12-31T23:59:59.999Z")
        
        val timer = object : CountDownTimer(endTime - getCurrentServerTime(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                
                binding.tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, secs)
            }
            
            override fun onFinish() {
                binding.tvCountdown.text = "活动已结束"
            }
        }
        timer.start()
    }
}
```

### 清除查询缓存

清除所有查询缓存：

```kotlin
FreelyBase.clearQueryCache()
Log.d("Cache", "查询缓存已清除")
```

使用场景：
- 用户退出登录时清除缓存
- 数据更新后强制刷新
- 内存优化

### 检查登录状态

检查用户是否已登录：

```kotlin
if (FreelyBase.isLoggedIn()) {
    Log.d("Auth", "用户已登录")
    // 跳转到主页
} else {
    Log.d("Auth", "用户未登录")
    // 跳转到登录页
}
```

### 获取访问令牌

获取当前用户的访问令牌：

```kotlin
val token = FreelyBase.getToken()
if (token != null) {
    Log.d("Auth", "Token: ${token.take(20)}...")
}
```

### 调试模式

启用调试模式以查看详细日志：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        FreelyBase.initialize(
            context = this,
            appKey = "your_app_key",
            enableEncryption = true
        )
        
        // 启用调试模式（仅在开发环境）
        if (BuildConfig.DEBUG) {
            FreelyBase.debug = true
        }
    }
}
```

调试模式会输出：
- 网络请求和响应
- 加密/解密过程
- Token 管理
- 错误详情

查看日志：

```bash
adb logcat | grep FreelyBase
```

## 常见问题（FAQ）

### 验证码收不到怎么办？

1. 检查手机号或邮箱是否正确
2. 查看垃圾邮件箱（邮箱验证码）
3. 确认手机信号正常（短信验证码）
4. 等待 1-2 分钟后重试
5. 检查控制台的短信/邮件服务配置是否正确
6. 查看控制台的发送记录和错误日志

### 文件上传大小限制？

默认限制：
- 单个文件最大 10MB
- 图片文件推荐不超过 5MB
- 视频文件推荐不超过 50MB

如需调整限制，请在控制台的「应用设置」中配置。

### 常见错误码解决方法

FreelyBase SDK 使用标准的 HTTP 状态码和自定义业务错误码。可以通过 `error.statusCode` 获取错误码。

#### HTTP 状态码（400-599）

#### 400 - 错误请求
- 检查请求参数是否完整和正确
- 确认参数类型是否匹配
- 查看 `error.detail` 获取具体错误信息

#### 401 - 未授权
- 确认用户是否已登录
- 检查 token 是否有效
- 重新登录获取新 token

```kotlin
.onFailure { error ->
    if (error.statusCode == FreelyException.UNAUTHORIZED) {
        // 跳转到登录页面
        FreelyUser.logout()
        startActivity(Intent(this, LoginActivity::class.java))
    }
}
```

#### 403 - 禁止访问
- 检查用户是否有操作权限
- 确认数据的访问权限设置
- 联系管理员分配权限

#### 404 - 资源未找到
- 确认 objectId 是否正确
- 检查数据是否已被删除
- 确认表名是否正确

#### 429 - 请求过于频繁
- 降低请求频率
- 实现请求节流
- 等待一段时间后重试

#### 500 - 服务器内部错误
- 稍后重试
- 检查请求参数是否合法
- 联系技术支持

#### 502 - 网关错误
- 检查网络连接
- 稍后重试
- 确认服务器状态

#### 503 - 服务不可用
- 服务器维护中
- 稍后重试
- 查看官方公告

#### 业务错误码（9001-9099）

#### 9001 - 参数错误
- 检查请求参数是否完整
- 确认参数类型是否正确
- 查看 SDK 文档确认正确的调用方式

```kotlin
.onFailure { error ->
    if (error.statusCode == FreelyException.INVALID_PARAMETER) {
        Log.e("Error", "参数错误: ${error.detail}")
    }
}
```

#### 9002 - 资源未找到
- 确认 objectId 是否正确
- 检查数据是否已被删除
- 确认表名是否正确

#### 9003 - 权限不足
- 确认用户是否已登录
- 检查数据的访问权限设置
- 确认用户是否有操作权限

#### 9004 - 数据已存在
- 检查是否重复创建
- 使用 update() 而不是 save()
- 确认唯一性约束

#### 9005 - 数据验证失败
- 检查必填字段是否填写
- 确认字段类型是否正确
- 查看字段验证规则

#### 9006 - 操作失败
- 查看 `error.detail` 获取具体原因
- 检查操作是否符合业务规则
- 稍后重试

#### 9007 - 网络错误
- 检查网络连接
- 确认网络权限已授予
- 重试请求

```kotlin
.onFailure { error ->
    if (error.statusCode == FreelyException.NETWORK_ERROR) {
        Toast.makeText(this, "网络错误，请检查网络连接", Toast.LENGTH_SHORT).show()
    }
}
```

#### 9008 - 超时
- 检查网络连接速度
- 增加超时时间
- 重试请求

#### 9009 - 未初始化
- 确认已在 Application 中调用 `FreelyBase.initialize()`
- 检查初始化参数是否正确

#### 9010 - 未登录
- 跳转到登录页面
- 提示用户登录

#### 9011 - 验证码错误
- 提示用户重新输入验证码
- 重新发送验证码

#### 9012 - 密码错误
- 提示用户重新输入密码
- 提供找回密码功能

#### 9013 - 用户不存在
- 提示用户注册
- 检查用户名是否正确

#### 9014 - 用户已存在
- 提示用户该账号已注册
- 引导用户登录或找回密码

#### 9015 - Token 无效或已过期
- 重新登录获取新 token
- 清除本地缓存

```kotlin
.onFailure { error ->
    if (error.statusCode == FreelyException.INVALID_TOKEN) {
        FreelyUser.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
```

#### 9016 - 应用不存在或已禁用
- 检查 AppKey 是否正确
- 确认应用是否已在控制台创建
- 检查应用状态是否正常

#### 9017 - AppKey 错误
- 检查 AppKey 是否正确
- 确认是否使用了正确环境的 AppKey
- 重新从控制台复制 AppKey

#### 9018 - 文件上传失败
- 检查文件是否存在
- 确认文件格式是否支持
- 检查网络连接

#### 9019 - 文件过大
- 压缩文件后重试
- 检查文件大小限制
- 联系管理员提高限制

#### 9022 - 缓存未命中
- 这是正常情况，SDK 会自动走网络请求
- 无需特殊处理

#### 9023 - 数据监听未开通
- 在控制台开通数据监听套餐
- 检查套餐状态

### 错误码常量

SDK 提供了错误码常量，方便使用：

```kotlin
import io.freelybase.kotlin.FreelyException

// HTTP 状态码
FreelyException.BAD_REQUEST              // 400
FreelyException.UNAUTHORIZED             // 401
FreelyException.FORBIDDEN                // 403
FreelyException.NOT_FOUND                // 404
FreelyException.TOO_MANY_REQUESTS        // 429
FreelyException.INTERNAL_SERVER_ERROR    // 500
FreelyException.BAD_GATEWAY              // 502
FreelyException.SERVICE_UNAVAILABLE      // 503

// 业务错误码
FreelyException.INVALID_PARAMETER        // 9001
FreelyException.RESOURCE_NOT_FOUND       // 9002
FreelyException.PERMISSION_DENIED        // 9003
FreelyException.ALREADY_EXISTS           // 9004
FreelyException.VALIDATION_FAILED        // 9005
FreelyException.OPERATION_FAILED         // 9006
FreelyException.NETWORK_ERROR            // 9007
FreelyException.TIMEOUT                  // 9008
FreelyException.NOT_INITIALIZED          // 9009
FreelyException.NOT_LOGGED_IN            // 9010
FreelyException.INVALID_CODE             // 9011
FreelyException.INVALID_PASSWORD         // 9012
FreelyException.USER_NOT_FOUND           // 9013
FreelyException.USER_ALREADY_EXISTS      // 9014
FreelyException.INVALID_TOKEN            // 9015
FreelyException.APP_NOT_FOUND            // 9016
FreelyException.INVALID_APP_KEY          // 9017
FreelyException.FILE_UPLOAD_FAILED       // 9018
FreelyException.FILE_TOO_LARGE           // 9019
FreelyException.CACHE_MISS               // 9022
FreelyException.LIVE_QUERY_NOT_ENABLED   // 9023
```

使用示例：

```kotlin
post.save<Post>()
    .onSuccess { savedPost ->
        Log.d("Post", "保存成功")
    }
    .onFailure { error ->
        when (error.statusCode) {
            FreelyException.UNAUTHORIZED -> {
                // 未登录，跳转到登录页面
                startActivity(Intent(this, LoginActivity::class.java))
            }
            FreelyException.VALIDATION_FAILED -> {
                // 数据验证失败
                Toast.makeText(this, "数据验证失败: ${error.detail}", Toast.LENGTH_SHORT).show()
            }
            FreelyException.NETWORK_ERROR -> {
                // 网络错误
                Toast.makeText(this, "网络错误，请检查网络连接", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // 其他错误
                Toast.makeText(this, "错误: ${error.detail}", Toast.LENGTH_SHORT).show()
            }
        }
    }
```

### 如何调试网络请求？

启用调试模式：

```kotlin
// 在 Application 中初始化时启用调试模式
class FreelyBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FreelyBase.initialize(
            context = this,
            appKey = "your_app_key"
        )
        
        // 启用调试模式（建议仅在开发环境启用）
        FreelyBase.debug = true
    }
}
```

或者在运行时动态开启：

```kotlin
// 仅在 Debug 构建时启用

    FreelyBase.debug = true

```

查看日志：

```bash
adb logcat | grep FreelyBase
```

调试模式会输出以下信息：
- SDK 初始化状态
- Token 变更记录
- HTTP 请求和响应（包括 URL、方法、状态码）
- 请求体和响应体（加密请求会显示 [encrypted]）
- WebSocket 连接状态
- 错误详情

日志示例：

```
D/FreelyBase: initialize: appKey=xxx, enableEncryption=true, restored token=eyJhbGciOiJIUzI1NiIs...
D/FreelyBase: HTTP Request: POST /public/apps/xxx/classes/Post
D/FreelyBase: Request Body: {"data":{"title":"Hello"}}
D/FreelyBase: HTTP Response: 200 /public/apps/xxx/classes/Post
D/FreelyBase: Response Body: {"object_id":"abc123","created_at":"2026-04-21T10:30:00Z"}
D/FreelyBase: LiveQuery connecting to: ws://www.freelybase.com/ws/apps/xxx/tables?token=xxx
D/FreelyBase: LiveQuery connected
```

注意事项：
- 调试模式会输出敏感信息（如 token），请勿在生产环境启用
- 建议使用 `BuildConfig.DEBUG` 来控制调试模式
- 日志输出可能会影响性能，仅在需要时启用

### 如何处理网络错误？

```kotlin
post.save()
    .onSuccess { savedPost ->
        Log.d("Post", "保存成功")
    }
    .onFailure { error ->
        when {
            error.detail.contains("timeout") -> {
                Toast.makeText(this, "网络超时，请检查网络连接", Toast.LENGTH_SHORT).show()
            }
            error.detail.contains("network") -> {
                Toast.makeText(this, "网络错误，请检查网络连接", Toast.LENGTH_SHORT).show()
            }
            error.statusCode in 500..599 -> {
                Toast.makeText(this, "服务器错误，请稍后重试", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "错误: ${error.detail}", Toast.LENGTH_SHORT).show()
            }
        }
    }
```






### 技术支持

如有其他问题，请通过以下方式联系我们：
- 官方文档：https://www.freelybase.com/docs
- 技术论坛：https://forum.freelybase.com
- 客服邮箱：support@freelybase.com
- QQ 群：123456789

## 地理位置查询

### 创建地理位置

FBGeoPoint 用于存储和查询地理位置信息：

```kotlin
// 创建地理位置（纬度，经度）
val location = FBGeoPoint(39.9042, 116.4074)  // 北京天安门

// 纬度范围：-90.0 到 90.0
// 经度范围：-180.0 到 180.0
```

### 在数据对象中使用地理位置

定义包含地理位置字段的数据类：

```kotlin
class Store : FreelyObject("Store") {
    var name: String = ""
    var address: String = ""
    var location: FBGeoPoint? = null
}
```

保存带地理位置的数据：

```kotlin
val store = Store().apply {
    name = "星巴克（国贸店）"
    address = "北京市朝阳区建国门外大街1号"
    location = FBGeoPoint(39.9088, 116.4577)
}

store.save()
    .onSuccess { savedStore ->
        Log.d("Store", "店铺保存成功")
    }
```

### 查询附近的对象

查询距离当前位置最近的店铺：

```kotlin
// 当前位置
val myLocation = FBGeoPoint(39.9100, 116.4100)

// 查询最近的 10 家店铺（按距离升序排序）
FBQuery<Store>()
    .near("location", myLocation)
    .limit(10)
    .find()
    .onSuccess { stores ->
        stores.forEach { store ->
            // 计算距离
            val distance = myLocation.distanceTo(store.location!!)
            Log.d("Store", "${store.name}: ${String.format("%.2f", distance)}km")
        }
    }
```

### 查询指定范围内的对象

查询 5 公里内的店铺：

```kotlin
val myLocation = FBGeoPoint(39.9100, 116.4100)

FBQuery<Store>()
    .withinKilometers("location", myLocation, 5.0)  // 5 公里内
    .find()
    .onSuccess { stores ->
        Log.d("Store", "找到 ${stores.size} 家店铺")
    }
```

查询 3 英里内的店铺：

```kotlin
FBQuery<Store>()
    .withinMiles("location", myLocation, 3.0)  // 3 英里内
    .find()
```

### 查询矩形区域内的对象

查询指定矩形区域内的店铺：

```kotlin
// 定义矩形区域（西南角和东北角）
val southwest = FBGeoPoint(39.90, 116.40)  // 西南角
val northeast = FBGeoPoint(39.92, 116.42)  // 东北角

FBQuery<Store>()
    .withinGeoBox("location", southwest, northeast)
    .find()
    .onSuccess { stores ->
        Log.d("Store", "区域内有 ${stores.size} 家店铺")
    }
```

### 计算两点之间的距离

```kotlin
val point1 = FBGeoPoint(39.9042, 116.4074)  // 天安门
val point2 = FBGeoPoint(39.9088, 116.4577)  // 国贸

// 计算距离（千米）
val distanceKm = point1.distanceTo(point2)
Log.d("Distance", "距离: ${String.format("%.2f", distanceKm)}km")

// 计算距离（英里）
val distanceMiles = point1.distanceTo(point2, DistanceUnit.MILES)
Log.d("Distance", "距离: ${String.format("%.2f", distanceMiles)}英里")
```

### 完整示例：附近的店铺

```kotlin
class NearbyStoresActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityNearbyStoresBinding
    private lateinit var adapter: StoreAdapter
    private var myLocation: FBGeoPoint? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyStoresBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取当前位置（使用 Android Location API）
        getCurrentLocation { latitude, longitude ->
            myLocation = FBGeoPoint(latitude, longitude)
            loadNearbyStores()
        }
        
        // 刷新按钮
        binding.btnRefresh.setOnClickListener {
            loadNearbyStores()
        }
    }
    
    private fun loadNearbyStores() {
        val location = myLocation ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        FBQuery<Store>()
            .near("location", location)
            .limit(20)
            .find()
            .onSuccess { stores ->
                binding.progressBar.visibility = View.GONE
                
                // 计算距离并排序
                val storesWithDistance = stores.map { store ->
                    val distance = location.distanceTo(store.location!!)
                    StoreWithDistance(store, distance)
                }.sortedBy { it.distance }
                
                adapter.submitList(storesWithDistance)
            }
            .onFailure { error ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "加载失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        // 使用 Android Location API 获取当前位置
        // 这里简化处理，实际应用中需要处理权限和位置服务
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                { location ->
                    callback(location.latitude, location.longitude)
                },
                null
            )
        }
    }
}

data class StoreWithDistance(
    val store: Store,
    val distance: Double
)
```

### 地理位置查询注意事项

1. **坐标范围**：
   - 纬度：-90.0 到 90.0
   - 经度：-180.0 到 180.0

2. **距离计算**：
   - 使用 Haversine 公式计算球面距离
   - 默认单位为千米
   - 支持千米、英里、弧度三种单位

3. **查询性能**：
   - 地理位置查询需要在后端建立地理位置索引
   - 建议限制查询范围（使用 withinKilometers 或 withinMiles）
   - 使用 limit() 限制返回数量

4. **权限要求**：
   - 获取用户位置需要申请 Android 位置权限
   - 在 AndroidManifest.xml 中添加：
     ```xml
     <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
     <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
     ```

5. **最佳实践**：
   - 缓存用户位置，避免频繁请求
   - 提供手动刷新功能
   - 显示距离信息给用户
   - 处理位置服务不可用的情况
