package io.freelybase.kotlin

import io.freelybase.kotlin.internal.http.HttpClient

/**
 * 数据对象基类，对应后端的一张数据表。
 *
 * 所有自定义数据类都应继承此类，并在构造函数中指定表名：
 * ```kotlin
 * class Post : FBObject("Post") {
 *     var title: String = ""
 *     var content: String = ""
 *     var viewCount: Int = 0
 * }
 * ```
 *
 * @param tableName 对应的后端表名
 */
abstract class FBObject(val tableName: String) {

    /** 对象唯一标识符 */
    var objectId: String = ""
    
    /** 创建时间（ISO 8601 格式） */
    var createdAt: String = ""
        internal set
    
    /** 最后更新时间（ISO 8601 格式） */
    var updatedAt: String = ""
        internal set

    private val appId get() = FreelyBase.appId

    private val BASE_FIELD_NAMES = setOf("objectId", "createdAt", "updatedAt", "tableName")

    /**
     * 序列化为 data map，用于 save/update 请求体。
     * - FBFile 字段 -> path 字符串
     * - @Pointer 字段 -> objectId 字符串
     * - @Relation 字段 -> [objectId, ...] 字符串数组
     */
    internal fun toDataMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        collectFields(this::class.java).forEach { field ->
            if (field.name in BASE_FIELD_NAMES) return@forEach
            field.isAccessible = true
            val value = runCatching { field.get(this) }.getOrNull()
            map[field.name] = when {
                value is FBFile -> value.path
                field.isAnnotationPresent(Pointer::class.java) && value is FBObject ->
                    value.objectId.ifBlank { null }
                field.isAnnotationPresent(Relation::class.java) && value is List<*> ->
                    value.filterIsInstance<FBObject>().map { it.objectId }.filter { it.isNotBlank() }
                else -> value
            }
        }
        return map
    }

    /**
     * 从后端响应填充字段。
     * - 普通字段：直接赋值
     * - @Pointer 字段：
     *   - 若后端返回了展开的对象 map -> 填充完整对象
     *   - 若后端返回了 objectId 字符串 -> 只设置 objectId
     */
    @Suppress("UNCHECKED_CAST")
    internal fun fillFromResponse(res: Map<String, Any?>) {
        objectId = res["object_id"]?.toString() ?: objectId
        createdAt = res["created_at"]?.toString() ?: createdAt
        updatedAt = res["updated_at"]?.toString() ?: updatedAt
        val dataMap = res["data"] as? Map<String, Any?> ?: return

        collectFields(this::class.java).forEach { field ->
            if (field.name in BASE_FIELD_NAMES) return@forEach
            field.isAccessible = true
            val rawValue = dataMap[field.name] ?: return@forEach

            if (field.isAnnotationPresent(Pointer::class.java)) {
                val fieldType = field.type
                when {
                    // 后端返回了展开的对象 map
                    rawValue is Map<*, *> && FBObject::class.java.isAssignableFrom(fieldType) -> {
                        runCatching {
                            val obj = fieldType.getDeclaredConstructor().newInstance() as FBObject
                            val wrapped = mapOf(
                                "object_id" to rawValue["object_id"],
                                "created_at" to rawValue["created_at"],
                                "updated_at" to rawValue["updated_at"],
                                "data" to rawValue.filterKeys { it !in setOf("object_id", "created_at", "updated_at") }
                            )
                            obj.fillFromResponse(wrapped)
                            field.set(this, obj)
                        }
                    }
                    // 后端返回了 objectId 字符串
                    rawValue is String && FBObject::class.java.isAssignableFrom(fieldType) -> {
                        runCatching {
                            val existing = field.get(this) as? FBObject
                            if (existing != null) {
                                existing.objectId = rawValue
                            } else {
                                val obj = fieldType.getDeclaredConstructor().newInstance() as FBObject
                                obj.objectId = rawValue
                                field.set(this, obj)
                            }
                        }
                    }
                }
                return@forEach
            }

            // @Relation 字段：List<FBObject>
            if (field.isAnnotationPresent(Relation::class.java)) {
                // 从泛型参数提取 item 类型，如 MutableList<AppUser> -> AppUser
                val itemClass = runCatching {
                    val genericType = field.genericType as? java.lang.reflect.ParameterizedType
                    val typeArg = genericType?.actualTypeArguments?.firstOrNull()
                    (typeArg as? Class<*>)?.takeIf { FBObject::class.java.isAssignableFrom(it) }
                }.getOrNull() ?: return@forEach

                val list = mutableListOf<FBObject>()
                when {
                    rawValue is List<*> && rawValue.firstOrNull() is Map<*, *> -> {
                        rawValue.filterIsInstance<Map<*, *>>().forEach { itemMap ->
                            runCatching {
                                val obj = itemClass.getDeclaredConstructor().newInstance() as FBObject
                                val wrapped = mapOf(
                                    "object_id" to itemMap["object_id"],
                                    "created_at" to itemMap["created_at"],
                                    "updated_at" to itemMap["updated_at"],
                                    "data" to itemMap.filterKeys { it !in setOf("object_id", "created_at", "updated_at") }
                                )
                                obj.fillFromResponse(wrapped)
                                list.add(obj)
                            }
                        }
                    }
                    rawValue is List<*> -> {
                        rawValue.filterIsInstance<String>().forEach { oid ->
                            runCatching {
                                val obj = itemClass.getDeclaredConstructor().newInstance() as FBObject
                                obj.objectId = oid
                                list.add(obj)
                            }
                        }
                    }
                }
                runCatching { field.set(this, list) }
                return@forEach
            }

            // FBFile 字段
            if (field.type == FBFile::class.java) {
                runCatching {
                    val path = rawValue.toString()
                    if (path.isNotBlank()) field.set(this, FBFile.fromPath(path))
                }
                return@forEach
            }

            // 普通字段：用 fbGson 反序列化
            runCatching {
                val json = fbGson.toJson(rawValue)
                @Suppress("UNCHECKED_CAST")
                val converted = fbGson.fromJson<Any?>(json, field.genericType)
                field.set(this, converted)
            }
        }
    }

    /** 收集类及其父类（到 FBObject 为止）的所有声明字段 */
    private fun collectFields(clazz: Class<*>): List<java.lang.reflect.Field> {
        val fields = mutableListOf<java.lang.reflect.Field>()
        var c: Class<*>? = clazz
        while (c != null && c != FBObject::class.java) {
            fields.addAll(c.declaredFields)
            c = c.superclass
        }
        return fields
    }

    /**
     * 保存对象到服务器（新建）。
     *
     * @return FBCall<T> 可链式调用 onSuccess/onFailure
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : FBObject> save(): FBCall<T> = fbCall {
        val res = HttpClient.post("/public/apps/$appId/classes/$tableName", mapOf("data" to toDataMap()))
        fillFromResponse(res)
        this as T
    }

    /**
     * 从服务器获取最新数据。
     *
     * @return FBCall<T> 可链式调用 onSuccess/onFailure
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : FBObject> fetch(): FBCall<T> = fbCall {
        require(objectId.isNotBlank()) { "objectId 为空，请先调用 save() 或通过查询获取对象" }
        val res = HttpClient.get("/public/apps/$appId/classes/$tableName/$objectId")
        fillFromResponse(res)
        this as T
    }

    /**
     * 更新对象到服务器。
     *
     * @return FBCall<T> 可链式调用 onSuccess/onFailure
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : FBObject> update(): FBCall<T> = fbCall {
        require(objectId.isNotBlank()) { "objectId 为空，请先调用 save() 或通过查询获取对象" }
        val res = HttpClient.put("/public/apps/$appId/classes/$tableName/$objectId", mapOf("data" to toDataMap()))
        fillFromResponse(res)
        this as T
    }

    /**
     * 从服务器删除对象。
     *
     * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
     */
    fun delete(): FBCall<Unit> = fbCall {
        require(objectId.isNotBlank()) { "objectId 为空，请先调用 save() 或通过查询获取对象" }
        HttpClient.delete("/public/apps/$appId/classes/$tableName/$objectId")
        objectId = ""
    }
}
