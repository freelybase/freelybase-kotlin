package io.freelybase.kotlin

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── 结果类型 ──────────────────────────────────────────────────────────────────

sealed class FBResult<out T> {
    data class Success<T>(val data: T) : FBResult<T>()
    data class Failure(val error: FreelyException) : FBResult<Nothing>()

    val isSuccess get() = this is Success
    val isFailure get() = this is Failure

    fun getOrNull(): T? = if (this is Success) data else null
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }
    fun getOrDefault(default: @UnsafeVariance T): T = if (this is Success) data else default

    inline fun <R> map(transform: (T) -> R): FBResult<R> = when (this) {
        is Success -> try { Success(transform(data)) } catch (e: Exception) {
            Failure(FreelyException(e.message ?: "转换失败", detail = e.message ?: "转换失败"))
        }
        is Failure -> this
    }
}

// ── FBCall：链式调用构建器 ────────────────────────────────────────────────────

/**
 * 封装一个异步操作，自动在后台执行，回调切回主线程。
 *
 * 用法：
 * ```kotlin
 * // 简洁用法（推荐）
 * post.save()
 *     .onSuccess { savedPost -> log(savedPost.title) }
 *     .onFailure { error -> toast(error.detail) }
 *
 * // 可选：绑定生命周期（页面销毁时自动取消）
 * post.save()
 *     .onSuccess { log(it.title) }
 *     .bindTo(this)
 *
 * // 协程中使用
 * val result = post.save().await()
 * ```
 */
class FBCall<T>(internal val block: suspend () -> T) {

    private var onSuccess: ((T) -> Unit)? = null
    private var onFailure: ((FreelyException) -> Unit)? = null
    
    // 保存当前执行的 Job，用于取消
    private var job: kotlinx.coroutines.Job? = null

    /**
     * 请求成功回调（主线程）。
     * 配置后自动在全局 scope 中执行。
     */
    fun onSuccess(block: (T) -> Unit): FBCall<T> {
        onSuccess = block
        execute(FreelyBase.appScope)
        return this
    }

    /**
     * 请求失败回调（主线程）。
     * 配置后自动在全局 scope 中执行。
     */
    fun onFailure(block: (FreelyException) -> Unit): FBCall<T> {
        onFailure = block
        execute(FreelyBase.appScope)
        return this
    }

    /**
     * 绑定生命周期（可选）。
     * 使用 Activity/Fragment 的 lifecycleScope，页面销毁时自动取消请求。
     */
    fun bindTo(owner: LifecycleOwner): FBCall<T> {
        execute(owner.lifecycleScope)
        return this
    }

    /**
     * 在指定 CoroutineScope 中执行（高级用法）。
     */
    fun launch(scope: CoroutineScope): FBCall<T> {
        execute(scope)
        return this
    }
    
    /**
     * 取消当前请求。
     */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * 挂起等待结果，返回 FBResult（在已有协程中使用）。
     */
    suspend fun await(): FBResult<T> = try {
        FBResult.Success(block())
    } catch (e: FreelyException) {
        FBResult.Failure(e)
    } catch (e: Exception) {
        FBResult.Failure(FreelyException(e.message ?: "未知错误", detail = e.message ?: "未知错误"))
    }

    // ── 内部 ─────────────────────────────────────────────────────────────────

    private var executed = false

    private fun execute(scope: CoroutineScope) {
        if (executed) return   // 防止重复执行
        executed = true
        job = scope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                try {
                    FBResult.Success(block())
                } catch (e: FreelyException) {
                    FBResult.Failure(e)
                } catch (e: Exception) {
                    FBResult.Failure(
                        FreelyException(e.message ?: "未知错误", detail = e.message ?: "未知错误")
                    )
                }
            }
            when (result) {
                is FBResult.Success -> onSuccess?.invoke(result.data)
                is FBResult.Failure -> onFailure?.invoke(result.error)
            }
        }
    }
}

/** 将 suspend 块包装为 FBCall */
fun <T> fbCall(block: suspend () -> T): FBCall<T> = FBCall(block)
