# FreelyBase SDK Library

这是 FreelyBase 的 Android SDK 库模块，提供完整的后端服务接入能力。

## 模块说明

本模块是一个标准的 Android Library，可以被其他 Android 项目依赖使用。

## 公共 API

SDK 只暴露必要的公共接口，内部实现细节对外不可见。

### 核心类

- `FreelyBase` - SDK 主入口
- `FBObject` - 数据对象基类
- `FBUser` - 用户认证
- `FBQuery<T>` - 数据查询
- `FBFile` - 文件管理
- `FBLiveQuery<T>` - 实时监听
- `FBCall<T>` - 异步调用
- `FBResult<T>` - 结果封装

### 注解

- `@Pointer` - 一对一关联
- `@Relation` - 多对多关联

### 异常

- `FreelyBaseException` - SDK 异常

## 内部实现

以下包和类标记为 `internal`，外部无法访问：

- `com.freelybase.sdk.internal.http.*` - HTTP 客户端
- `com.freelybase.sdk.internal.crypto.*` - 加密实现
- `QueryCache` - 查询缓存管理

## 文档

- [PUBLIC_API.md](PUBLIC_API.md) - 公共 API 索引
- [../API.md](../API.md) - 完整 API 文档
- [../README.md](../README.md) - 使用指南
- [../CHANGELOG.md](../CHANGELOG.md) - 变更日志

## 构建

```bash
./gradlew :library:build
```

## 发布

```bash
./gradlew :library:publish
```

## 许可证

MIT License
