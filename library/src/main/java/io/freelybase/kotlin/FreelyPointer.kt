package io.freelybase.kotlin

/**
 * 标记 FBObject 子类中的 Pointer 关联字段。
 *
 * 用法：
 * ```kotlin
 * class Post : FBObject("Post") {
 *     var title: String = ""
 *
 *     @Pointer
 *     var author: AppUser? = null
 * }
 * ```
 *
 * - 保存时：自动将 author 序列化为 author.objectId 字符串
 * - 查询时（不带 include）：author 为 null，objectId 存在 authorId 中（字段名 + "Id"）
 * - 查询时（带 include("author")）：author 自动填充为完整对象
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pointer
