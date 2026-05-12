package io.freelybase.kotlin

/**
 * FreelyBase SDK 异常类。
 *
 * @property message 错误消息
 * @property statusCode HTTP 状态码或自定义错误码
 * @property detail 详细错误信息
 */
class FreelyException(
    message: String,
    val statusCode: Int = 0,
    val detail: String = message
) : Exception(message) {
    override fun toString() = "FreelyBaseException[$statusCode]: $detail"
    
    companion object {
        // ── HTTP 状态码 ──────────────────────────────────────────────
        /** 错误请求 */
        const val BAD_REQUEST = 400
        
        /** 未授权（未登录或 token 无效） */
        const val UNAUTHORIZED = 401
        
        /** 禁止访问（权限不足） */
        const val FORBIDDEN = 403
        
        /** 资源未找到 */
        const val NOT_FOUND = 404
        
        /** 请求过于频繁 */
        const val TOO_MANY_REQUESTS = 429
        
        /** 服务器内部错误 */
        const val INTERNAL_SERVER_ERROR = 500
        
        /** 网关错误 */
        const val BAD_GATEWAY = 502
        
        /** 服务不可用 */
        const val SERVICE_UNAVAILABLE = 503
        
        // ── 业务错误码 ──────────────────────────────────────────────
        /** 参数错误 */
        const val INVALID_PARAMETER = 9001
        
        /** 资源未找到 */
        const val RESOURCE_NOT_FOUND = 9002
        
        /** 权限不足 */
        const val PERMISSION_DENIED = 9003
        
        /** 数据已存在 */
        const val ALREADY_EXISTS = 9004
        
        /** 数据验证失败 */
        const val VALIDATION_FAILED = 9005
        
        /** 操作失败 */
        const val OPERATION_FAILED = 9006
        
        /** 网络错误 */
        const val NETWORK_ERROR = 9007
        
        /** 超时 */
        const val TIMEOUT = 9008
        
        /** 未初始化 */
        const val NOT_INITIALIZED = 9009
        
        /** 未登录 */
        const val NOT_LOGGED_IN = 9010
        
        /** 验证码错误 */
        const val INVALID_CODE = 9011
        
        /** 密码错误 */
        const val INVALID_PASSWORD = 9012
        
        /** 用户不存在 */
        const val USER_NOT_FOUND = 9013
        
        /** 用户已存在 */
        const val USER_ALREADY_EXISTS = 9014
        
        /** Token 无效或已过期 */
        const val INVALID_TOKEN = 9015
        
        /** 应用不存在或已禁用 */
        const val APP_NOT_FOUND = 9016
        
        /** AppKey 错误 */
        const val INVALID_APP_KEY = 9017
        
        /** 文件上传失败 */
        const val FILE_UPLOAD_FAILED = 9018
        
        /** 文件过大 */
        const val FILE_TOO_LARGE = 9019
        
        /** AI Token 余额不足 */
        const val AI_QUOTA_EXCEEDED = 9020
        
        /** AI 服务错误 */
        const val AI_SERVICE_ERROR = 9021
        
        /** 缓存未命中 */
        const val CACHE_MISS = 9022
        
        /** 数据监听未开通 */
        const val LIVE_QUERY_NOT_ENABLED = 9023
    }
}
