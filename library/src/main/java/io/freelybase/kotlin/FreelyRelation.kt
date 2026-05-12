package io.freelybase.kotlin

/**
 * 标记 FBObject 子类中的 Relation 多对多关联字段，字段类型必须是 MutableList<T>。
 *
 * 用法：
 * ```kotlin
 * class Post : FBObject("Post") {
 *     @Relation
 *     var likes: MutableList<AppUser> = mutableListOf()
 * }
 * ```
 *
 * 无需传入类型参数，SDK 自动从泛型参数推断元素类型。
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Relation
