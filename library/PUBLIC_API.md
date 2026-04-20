# FreelyBase SDK 公共 API 索引

本文件列出所有对外暴露的公共接口，供开发者快速查找。

## 包结构

```
com.freelybase.sdk/
├── FreelyBase              - SDK 主入口
├── FBObject                - 数据对象基类
├── FBUser                  - 用户类
├── FBQuery                 - 查询构建器
├── FBFile                  - 文件管理
├── FBCall                  - 异步调用封装
├── FBResult                - 结果类型
├── FBPointer               - 一对一关联注解
├── FBRelation              - 多对多关联注解
├── FreelyBaseException     - SDK 异常
├── CachePolicy             - 缓存策略枚举
└── internal/               - 内部实现（不对外暴露）
```

## 快速索引

### 初始化

```kotlin
FreelyBase.initialize(context, appId, enableEncryption)
```

### 用户认证

```kotlin
// 注册
user.signUpByEmail(emailCode)
user.signUpBySms(smsCode)
user.signUpByUsername()

// 登录
FBUser.login(account, password)
FBUser.loginAs<T>(account, password)
FBUser.loginWithEmailCodeAs<T>(email, emailCode)
FBUser.loginWithSmsCodeAs<T>(phone, smsCode)

// 当前用户
FBUser.currentUserAs<T>()
FBUser.restoreCurrentUserAs<T>()

// 退出
FBUser.logout()
```

### 数据操作

```kotlin
// CRUD
object.save<T>()
object.fetch<T>()
object.update<T>()
object.delete()

// 查询
FBQuery<T>()
    .equalTo(field, value)
    .greaterThan(field, value)
    .contains(field, value)
    .include(fields...)
    .orderByDesc(field)
    .limit(n)
    .find()
```

### 文件上传

```kotlin
FBFile.upload(file)
FBFile.upload(contentResolver, uri)
FBFile.fromPath(path)
```

### 异步调用

```kotlin
call.onSuccess { result -> }
call.onFailure { error -> }
call.bindTo(lifecycleOwner)
call.await()  // 协程中使用
```

## 详细文档

完整的 API 文档请参考：
- [API.md](../API.md) - 详细的 API 说明
- [README.md](../README.md) - 使用指南和示例
