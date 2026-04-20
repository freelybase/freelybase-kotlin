package io.freelybase.kotlin

import android.util.LruCache
import io.freelybase.kotlin.internal.http.HttpClient
import com.google.gson.Gson
import java.util.Date

/**
 * 缓存策略。
 */
enum class CachePolicy {
    /** 只走网络，不读写缓存（默认） */
    NETWORK_ONLY,
    
    /** 只读缓存，无缓存时抛异常 */
    CACHE_ONLY,
    
    /** 先读缓存，无缓存再走网络 */
    CACHE_ELSE_NETWORK,
    
    /** 先走网络，网络失败再读缓存 */
    NETWORK_ELSE_CACHE,
}

/**
 * 数据查询构建器，支持链式调用。
 *
 * 用法：
 * ```kotlin
 * // 基础查询
 * FBQuery<Post>()
 *     .equalTo("status", "published")
 *     .greaterThan("viewCount", 100)
 *     .orderByDesc("createdAt")
 *     .limit(20)
 *     .find()
 *     .onSuccess { posts -> log(posts.size) }
 *
 * // 关联查询
 * FBQuery<Post>()
 *     .include("author")  // 展开 Pointer 字段
 *     .find()
 *     .onSuccess { posts ->
 *         posts.forEach { log(it.author?.username) }
 *     }
 *
 * // 缓存策略
 * FBQuery<Post>()
 *     .cachePolicy(CachePolicy.CACHE_ELSE_NETWORK)
 *     .maxCacheAge(5 * 60 * 1000)  // 5 分钟
 *     .find()
 * ```
 */
class FBQuery<T : FBObject>(private val clazz: Class<T>) {

    private val tableName: String = clazz.getDeclaredConstructor().newInstance().tableName
    private val appId get() = FreelyBase.appId

    // ── 条件 ────────────────────────────────────────────────────────
    private val andConditions = mutableListOf<Map<String, Any?>>()
    private val orConditions  = mutableListOf<Map<String, Any?>>()

    // ── 排序/分页 ────────────────────────────────────────────────────
    private var orderByField: String? = null
    private var skipVal: Int = 0
    private var limitVal: Int = 100

    // ── 字段选择 / 关联展开 ──────────────────────────────────────────
    private var keys: List<String>? = null
    private var includeFields: List<String>? = null

    // ── 缓存 ─────────────────────────────────────────────────────────
    private var cachePolicy: CachePolicy = CachePolicy.NETWORK_ONLY
    private var maxCacheAge: Long = 5 * 60 * 1000L  // 默认 5 分钟

    // ── 内部工具 ─────────────────────────────────────────────────────
    private val gson = Gson()

    private fun and(field: String, op: String, value: Any? = null): FBQuery<T> {
        andConditions.add(mapOf("field" to field, "op" to op, "value" to value))
        return this
    }

    // ── 基础比较条件 ─────────────────────────────────────────────────

    /** 字段等于指定值 */
    fun equalTo(field: String, value: Any?)            = and(field, "eq", value)
    
    /** 字段不等于指定值 */
    fun notEqualTo(field: String, value: Any?)         = and(field, "ne", value)
    
    /** 字段大于指定值 */
    fun greaterThan(field: String, value: Any?)        = and(field, "gt", value)
    
    /** 字段大于等于指定值 */
    fun greaterThanOrEqual(field: String, value: Any?) = and(field, "gte", value)
    
    /** 字段小于指定值 */
    fun lessThan(field: String, value: Any?)           = and(field, "lt", value)
    
    /** 字段小于等于指定值 */
    fun lessThanOrEqual(field: String, value: Any?)    = and(field, "lte", value)

    // ── 存在性 ───────────────────────────────────────────────────────

    /** 字段存在（不为 null） */
    fun exists(field: String)    = and(field, "exists")
    
    /** 字段不存在（为 null） */
    fun notExists(field: String) = and(field, "nexists")

    // ── 模糊查询 ─────────────────────────────────────────────────────

    /** 字段包含指定字符串 */
    fun contains(field: String, value: String)    = and(field, "contains", value)
    
    /** 字段以指定字符串开头 */
    fun startsWith(field: String, value: String)  = and(field, "startswith", value)
    
    /** 字段以指定字符串结尾 */
    fun endsWith(field: String, value: String)    = and(field, "endswith", value)

    // ── 数组查询 ─────────────────────────────────────────────────────

    /** 字段值在给定列表中 */
    fun containedIn(field: String, values: List<Any?>)    = and(field, "in", values)
    /** 字段值不在给定列表中 */
    fun notContainedIn(field: String, values: List<Any?>) = and(field, "nin", values)

    // ── 时间查询 ─────────────────────────────────────────────────────

    /** 等于某个时间（ISO 8601 字符串） */
    fun atTime(field: String, date: Date)       = and(field, "eq", date.toIso())
    /** 不等于某个时间 */
    fun notAtTime(field: String, date: Date)    = and(field, "ne", date.toIso())
    /** 在某个时间之前（不含） */
    fun before(field: String, date: Date)       = and(field, "lt", date.toIso())
    /** 在某个时间之后（不含） */
    fun after(field: String, date: Date)        = and(field, "gt", date.toIso())
    /** 在某个时间之前（含） */
    fun beforeOrAt(field: String, date: Date)   = and(field, "lte", date.toIso())
    /** 在某个时间之后（含） */
    fun afterOrAt(field: String, date: Date)    = and(field, "gte", date.toIso())
    /** 在两个时间之间（含两端） */
    fun between(field: String, from: Date, to: Date): FBQuery<T> {
        and(field, "gte", from.toIso())
        and(field, "lte", to.toIso())
        return this
    }
    /** 在两个时间之外 */
    fun notBetween(field: String, from: Date, to: Date): FBQuery<T> {
        orWhere(field, "lt", from.toIso())
        orWhere(field, "gt", to.toIso())
        return this
    }

    // ── 子查询（Pointer 字段匹配另一个查询的结果） ────────────────────

    /**
     * 子查询：field 指向的对象满足 subQuery 的条件。
     * 在客户端执行：先查子查询结果，再用 objectId 列表过滤。
     */
    suspend fun <S : FBObject> matchesQuery(field: String, subQuery: FBQuery<S>): FBQuery<T> {
        val ids = subQuery.select("objectId").findInternal().map { it.objectId }.filter { it.isNotBlank() }
        return containedIn(field, ids)
    }

    /**
     * 子查询（不匹配）：field 指向的对象不满足 subQuery 的条件。
     */
    suspend fun <S : FBObject> doesNotMatchQuery(field: String, subQuery: FBQuery<S>): FBQuery<T> {
        val ids = subQuery.select("objectId").findInternal().map { it.objectId }.filter { it.isNotBlank() }
        return notContainedIn(field, ids)
    }

    // ── 复合查询 ─────────────────────────────────────────────────────

    /**
     * 添加 OR 条件（与已有 AND 条件并列）。
     * 用法：query.or { equalTo("status", "active").greaterThan("count", 0) }
     */
    fun or(block: FBQuery<T>.() -> Unit): FBQuery<T> {
        val sub = FBQuery(clazz)
        sub.block()
        orConditions.addAll(sub.andConditions)
        return this
    }

    private fun orWhere(field: String, op: String, value: Any? = null): FBQuery<T> {
        orConditions.add(mapOf("field" to field, "op" to op, "value" to value))
        return this
    }

    // ── 排序 / 分页 ──────────────────────────────────────────────────

    /** 按字段升序排序 */
    fun orderBy(field: String): FBQuery<T>  { orderByField = field; return this }
    
    /** 按字段降序排序 */
    fun orderByDesc(field: String): FBQuery<T> { orderByField = "-$field"; return this }

    /** 跳过前 n 条记录 */
    fun skip(n: Int): FBQuery<T>  { skipVal = n; return this }
    
    /** 限制返回条数 */
    fun limit(n: Int): FBQuery<T> { limitVal = n; return this }

    /** 
     * 分页查询。
     * 
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数，默认 20
     */
    fun page(page: Int, pageSize: Int = 20): FBQuery<T> {
        limitVal = pageSize
        skipVal = (page - 1) * pageSize
        return this
    }

    // ── 字段选择 / 关联展开 ──────────────────────────────────────────

    /** 
     * 只返回指定字段（可减少传输量）。
     * 
     * @param fields 字段名列表
     */
    fun select(vararg fields: String): FBQuery<T> { keys = fields.toList(); return this }

    /** 
     * 展开 Pointer / Relation 字段，返回完整对象。
     * 
     * @param fields 需要展开的字段名
     */
    fun include(vararg fields: String): FBQuery<T> { includeFields = fields.toList(); return this }

    // ── 缓存策略 ─────────────────────────────────────────────────────

    /** 设置缓存策略 */
    fun cachePolicy(policy: CachePolicy): FBQuery<T> { cachePolicy = policy; return this }

    /** 
     * 设置缓存最大有效时长。
     * 
     * @param ms 毫秒数
     */
    fun maxCacheAge(ms: Long): FBQuery<T> { maxCacheAge = ms; return this }

    // ── 执行 ─────────────────────────────────────────────────────────

    /** 
     * 执行查询，返回结果列表。
     * 
     * @return FBCall<List<T>> 可链式调用 onSuccess/onFailure
     */
    fun find(): FBCall<List<T>> = fbCall { findInternal() }

    /** 
     * 统计符合条件的记录数。
     * 
     * @return FBCall<Int> 可链式调用 onSuccess/onFailure
     */
    fun count(): FBCall<Int> = fbCall {
        val res = HttpClient.post("/public/apps/$appId/classes/$tableName/query", buildBody(true))
        (res["count"] as? Double)?.toInt() ?: 0
    }

    /** 
     * 获取第一条记录。
     * 
     * @return FBCall<T?> 可链式调用 onSuccess/onFailure，无结果时返回 null
     */
    fun first(): FBCall<T?> = fbCall { limit(1).skip(0).findInternal().firstOrNull() }

    /** 
     * 根据 objectId 获取对象。
     * 
     * @param objectId 对象 ID
     * @return FBCall<T> 可链式调用 onSuccess/onFailure
     */
    fun getObject(objectId: String): FBCall<T> = fbCall {
        val path = "/public/apps/$appId/classes/$tableName/$objectId"
        val params = buildGetParams()
        val res = HttpClient.get(path, params)
        clazz.getDeclaredConstructor().newInstance().also { it.fillFromResponse(res) }
    }

    // ── 内部实现 ─────────────────────────────────────────────────────

    /** 内部使用，直接返回 List<T>，供 first() 和子查询调用 */
    private suspend fun findInternal(): List<T> {
        val cacheKey = buildCacheKey(false)
        return when (cachePolicy) {
            CachePolicy.CACHE_ONLY ->
                QueryCache.get(cacheKey, maxCacheAge)?.let { parseResults(it) }
                    ?: throw FreelyBaseException("缓存未命中")
            CachePolicy.CACHE_ELSE_NETWORK -> {
                QueryCache.get(cacheKey, maxCacheAge)?.let { return parseResults(it) }
                fetchAndCache(cacheKey, false)
            }
            CachePolicy.NETWORK_ELSE_CACHE ->
                runCatching { fetchAndCache(cacheKey, false) }.getOrElse {
                    QueryCache.get(cacheKey, Long.MAX_VALUE)?.let { parseResults(it) } ?: throw it
                }
            CachePolicy.NETWORK_ONLY -> fetchAndCache(cacheKey, false)
        }
    }

    private fun buildBody(countOnly: Boolean): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>(
            "skip" to skipVal,
            "limit" to limitVal,
            "count" to countOnly,
        )
        if (andConditions.isNotEmpty()) body["where"] = andConditions
        if (orConditions.isNotEmpty())  body["or_"]   = orConditions
        orderByField?.let { body["order_by"] = it }
        keys?.let { body["keys"] = it }
        includeFields?.let { body["include"] = it }
        return body
    }

    private fun buildGetParams(): Map<String, String> {
        val params = mutableMapOf<String, String>()
        includeFields?.let { params["include"] = it.joinToString(",") }
        keys?.let { params["keys"] = it.joinToString(",") }
        return params
    }

    private fun buildCacheKey(countOnly: Boolean): String =
        "$tableName:${gson.toJson(buildBody(countOnly))}"

    private suspend fun fetchAndCache(cacheKey: String, countOnly: Boolean): List<T> {
        val res = HttpClient.post("/public/apps/$appId/classes/$tableName/query", buildBody(countOnly))
        @Suppress("UNCHECKED_CAST")
        val rawResults = res["results"] as? List<*> ?: emptyList<Any>()
        QueryCache.put(cacheKey, rawResults)
        return parseResults(rawResults)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResults(rawResults: List<*>): List<T> =
        rawResults.mapNotNull { item ->
            val map = item as? Map<String, Any?> ?: return@mapNotNull null
            clazz.getDeclaredConstructor().newInstance().also { it.fillFromResponse(map) }
        }

    private fun Date.toIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(this)
}

// ── 全局查询缓存 ──────────────────────────────────────────────────────

internal object QueryCache {
    private data class Entry(val data: List<*>, val timestamp: Long)
    private val cache = LruCache<String, Entry>(50)

    fun put(key: String, data: List<*>) {
        cache.put(key, Entry(data, System.currentTimeMillis()))
    }

    fun get(key: String, maxAgeMs: Long): List<*>? {
        val entry = cache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > maxAgeMs) {
            cache.remove(key)
            return null
        }
        return entry.data
    }

    fun clear() = cache.evictAll()
}

/** 用法：FBQuery<Post>() 代替 FBQuery(Post::class.java) */
inline fun <reified T : FBObject> FBQuery(): FBQuery<T> = FBQuery(T::class.java)
