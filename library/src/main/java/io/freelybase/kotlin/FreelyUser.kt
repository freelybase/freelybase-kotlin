package io.freelybase.kotlin

import io.freelybase.kotlin.internal.http.HttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户类，封装用户认证和账户管理功能。
 *
 * 基础用法：
 * ```kotlin
 * // 注册
 * val user = FBUser().apply {
 *     username = "zhangsan"
 *     email = "zhangsan@example.com"
 *     password = "123456"
 * }
 * user.signUpByEmail(emailCode)
 *     .onSuccess { log("注册成功") }
 *
 * // 登录
 * FBUser.loginAs<AppUser>("zhangsan", "123456")
 *     .onSuccess { user -> log(user.username) }
 *
 * // 获取当前用户
 * FBUser.currentUserAs<AppUser>()
 *     .onSuccess { user -> log(user.email) }
 *
 * // 退出登录
 * FBUser.logout()
 * ```
 *
 * 自定义用户类：
 * ```kotlin
 * class AppUser : FBUser() {
 *     var nickname: String = ""
 *     var avatar: FBFile? = null
 * }
 * ```
 */
open class FreelyUser : FreelyObject("_User") {

    /** 用户名 */
    var username: String = ""
    
    /** 邮箱 */
    var email: String = ""
    
    /** 手机号 */
    var mobile: String = ""

    /** 密码（仅用于注册和登录，不会从服务器返回） */
    @Transient
    var password: String = ""

    private val appId get() = FreelyBase.appKey
    private val gson = Gson()

    private val USER_BASE_FIELDS = listOf(
        "object_id", "created_at", "updated_at", "tableName",
        "username", "email", "mobile", "password", "gson", "auth"
    )

    // ── 注册 ──────────────────────────────────────────────────────────────────

    /**
     * 通过邮箱验证码注册。
     *
     * @param emailCode 邮箱验证码
     * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
     */
    fun signUpByEmail(emailCode: String): FBCall<FreelyUser> = fbCall {
        require(email.isNotBlank()) { "email 不能为空" }
        require(password.isNotBlank()) { "password 不能为空" }
        val body = mutableMapOf<String, Any?>(
            "email" to email, "password" to password, "email_code" to emailCode
        )
        if (username.isNotBlank()) body["username"] = username
        applyAuthResponse(HttpClient.post("/public/apps/$appId/auth/register", body))
        this
    }

    /**
     * 通过短信验证码注册。
     *
     * @param smsCode 短信验证码
     * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
     */
    fun signUpBySms(smsCode: String): FBCall<FreelyUser> = fbCall {
        require(mobile.isNotBlank()) { "mobile 不能为空" }
        require(password.isNotBlank()) { "password 不能为空" }
        val body = mutableMapOf<String, Any?>(
            "phone" to mobile, "password" to password, "sms_code" to smsCode
        )
        if (username.isNotBlank()) body["username"] = username
        applyAuthResponse(HttpClient.post("/public/apps/$appId/auth/sms/register", body))
        this
    }

    /**
     * 通过用户名注册（无需验证码）。
     *
     * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
     */
    fun signUpByUsername(): FBCall<FreelyUser> = fbCall {
        require(username.isNotBlank()) { "username 不能为空" }
        require(password.isNotBlank()) { "password 不能为空" }
        applyAuthResponse(
            HttpClient.post(
                "/public/apps/$appId/auth/register/username",
                mapOf("username" to username, "password" to password)
            )
        )
        this
    }

    // ── 更新 ──────────────────────────────────────────────────────────────────

    /**
     * 更新用户资料（包括基础字段和自定义字段）。
     *
     * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
     */
    fun updateProfile(): FBCall<Unit> = fbCall {
        val baseBody = mutableMapOf<String, Any?>()
        if (username.isNotBlank()) baseBody["username"] = username
        if (email.isNotBlank()) baseBody["email"] = email
        if (baseBody.isNotEmpty()) {
            val res = HttpClient.put("/public/apps/$appId/auth/me", baseBody)
            username = res["username"]?.toString() ?: username
            email = res["email"]?.toString() ?: email
        }
        val customFields = toCustomFieldsMap()
        if (customFields.isNotEmpty() && objectId.isNotBlank()) {
            HttpClient.put("/public/apps/$appId/classes/_User/$objectId", mapOf("data" to customFields))
        }
    }

    /**
     * 修改密码。
     *
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
     */
    fun changePassword(currentPassword: String, newPassword: String): FBCall<Unit> = fbCall {
        HttpClient.put(
            "/public/apps/$appId/auth/password",
            mapOf("current_password" to currentPassword, "new_password" to newPassword)
        )
    }

    /**
     * 通过邮箱验证码重置密码。
     *
     * @param emailCode 邮箱验证码
     * @param newPassword 新密码
     * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
     */
    fun resetPassword(emailCode: String, newPassword: String): FBCall<Unit> = fbCall {
        require(email.isNotBlank()) { "email 不能为空" }
        HttpClient.post(
            "/public/apps/$appId/auth/password/reset",
            mapOf("email" to email, "email_code" to emailCode, "new_password" to newPassword)
        )
    }

    /**
     * 通过短信验证码重置密码。
     *
     * @param smsCode 短信验证码
     * @param newPassword 新密码
     * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
     */
    fun resetPasswordBySms(smsCode: String, newPassword: String): FBCall<Unit> = fbCall {
        require(mobile.isNotBlank()) { "mobile 不能为空" }
        HttpClient.post(
            "/public/apps/$appId/auth/password/reset-by-sms",
            mapOf("phone" to mobile, "sms_code" to smsCode, "new_password" to newPassword)
        )
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun toCustomFieldsMap(): Map<String, Any?> {
        val json = gson.toJson(this)
        val all = gson.fromJson<Map<String, Any?>>(json, object : TypeToken<Map<String, Any?>>() {}.type)
        return all.filterKeys { it !in USER_BASE_FIELDS }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun applyAuthResponse(res: Map<String, Any?>) {
        val token = res["access_token"]?.toString()
            ?: throw FreelyException("认证响应缺少 access_token")
        FreelyBase.setToken(token)
        val userMap = res["user"] as? Map<String, Any?> ?: emptyMap()
        objectId = userMap["object_id"]?.toString() ?: ""
        username = userMap["username"]?.toString() ?: username
        email = userMap["email"]?.toString() ?: email
        persistSession()
    }

    /** 将当前用户基础信息写入持久化缓存 */
    internal fun persistSession() {
        val map = mapOf("object_id" to objectId, "username" to username, "email" to email, "mobile" to mobile)
        FreelyBase.saveUserJson(gson.toJson(map))
    }

    // ── 静态方法 ──────────────────────────────────────────────────────────────

    companion object {

        /**
         * 发送邮箱验证码。
         *
         * @param email 邮箱地址
         * @return FBCall<String> 返回提示信息
         */
        fun sendEmailCode(email: String): FBCall<String> = fbCall {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/password/send-code",
                mapOf("email" to email)
            )
            res["detail"]?.toString() ?: "验证码已发送"
        }

        /**
         * 验证邮箱验证码。
         *
         * @param email 邮箱地址
         * @param emailCode 验证码
         * @return FBCall<Boolean> 验证是否成功
         */
        fun verifyEmailCode(email: String, emailCode: String): FBCall<Boolean> = fbCall {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/email-code/verify",
                mapOf("email" to email, "email_code" to emailCode)
            )
            res["verified"] as? Boolean ?: false
        }

        /**
         * 发送短信验证码。
         *
         * @param phone 手机号
         * @return FBCall<String> 返回提示信息
         */
        fun sendSmsCode(phone: String): FBCall<String> = fbCall {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/sms/send-code",
                mapOf("phone" to phone)
            )
            res["detail"]?.toString() ?: "验证码已发送"
        }

        /**
         * 验证短信验证码。
         *
         * @param phone 手机号
         * @param smsCode 验证码
         * @return FBCall<Boolean> 验证是否成功
         */
        fun verifySmsCode(phone: String, smsCode: String): FBCall<Boolean> = fbCall {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/sms/verify",
                mapOf("phone" to phone, "sms_code" to smsCode)
            )
            res["verified"] as? Boolean ?: false
        }

        // ── 第三方授权 ────────────────────────────────────────────────────────

        /**
         * 第三方账号一键注册或登录。
         * 已有该第三方绑定记录则直接登录，否则自动注册新账号。
         *
         * @param snsType 平台标识：weibo / qq / weixin
         * @param accessToken 第三方平台返回的接口调用凭证
         * @param expiresIn access_token 有效时间（秒），由第三方平台返回
         * @param userId 用户唯一标识（微博为 uid，QQ/微信为 openid）
         * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
         */
        @JvmName("loginWithAuthData")
        fun loginWithAuthDataAs(snsType: String, accessToken: String, expiresIn: String, userId: String): FBCall<FreelyUser> =
            fbCall { loginWithAuthDataInternal(FreelyUser::class.java, snsType, accessToken, expiresIn, userId) }

        /**
         * 第三方账号一键注册或登录（泛型版本）。
         *
         * @param T 自定义用户类型
         */
        inline fun <reified T : FreelyUser> loginWithAuthDataAs(
            snsType: String, accessToken: String, expiresIn: String, userId: String
        ): FBCall<T> = fbCall { loginWithAuthDataInternal(T::class.java, snsType, accessToken, expiresIn, userId) }

        @PublishedApi
        internal suspend fun <T : FreelyUser> loginWithAuthDataInternal(
            clazz: Class<T>, snsType: String, accessToken: String, expiresIn: String, userId: String
        ): T {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/third-party/login",
                mapOf("sns_type" to snsType, "access_token" to accessToken, "expires_in" to expiresIn, "user_id" to userId)
            )
            return parseAuthAs(clazz, res)
        }

        /**
         * 关联第三方账号到当前用户。
         *
         * @param snsType 平台标识
         * @param accessToken 第三方平台返回的接口调用凭证
         * @param expiresIn 有效时间（秒）
         * @param userId 用户唯一标识
         * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
         */
        fun associateWithAuthData(snsType: String, accessToken: String, expiresIn: String, userId: String): FBCall<Unit> =
            fbCall {
                HttpClient.post(
                    "/public/apps/${FreelyBase.appKey}/auth/third-party/associate",
                    mapOf("sns_type" to snsType, "access_token" to accessToken, "expires_in" to expiresIn, "user_id" to userId)
                )
            }

        /**
         * 解除第三方账号绑定。
         *
         * @param snsType 平台标识
         * @return FBCall<Unit> 可链式调用 onSuccess/onFailure
         */
        fun dissociateAuthData(snsType: String): FBCall<Unit> = fbCall {
            HttpClient.delete("/public/apps/${FreelyBase.appKey}/auth/third-party/$snsType")
        }

        // ── 登录 ─────────────────────────────────────────────────────────────

        /**
         * 用户名/邮箱/手机号登录。
         *
         * @param account 用户名、邮箱或手机号
         * @param password 密码
         * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
         */
        @JvmName("login")
        fun login(account: String, password: String): FBCall<FreelyUser> =
            fbCall { loginAsInternal(FreelyUser::class.java, account, password) }

        /**
         * 用户名/邮箱/手机号登录（泛型版本）。
         *
         * @param T 自定义用户类型
         */
        inline fun <reified T : FreelyUser> loginAs(account: String, password: String): FBCall<T> =
            fbCall { loginAsInternal(T::class.java, account, password) }

        @PublishedApi
        internal suspend fun <T : FreelyUser> loginAsInternal(clazz: Class<T>, account: String, password: String): T {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/login",
                mapOf("account" to account, "password" to password)
            )
            return parseAuthAs(clazz, res)
        }

        /**
         * 邮箱验证码登录。
         *
         * @param email 邮箱地址
         * @param emailCode 验证码
         * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
         */
        @JvmName("loginWithEmailCode")
        fun loginWithEmailCode(email: String, emailCode: String): FBCall<FreelyUser> =
            fbCall { loginWithEmailCodeInternal(FreelyUser::class.java, email, emailCode) }

        /**
         * 邮箱验证码登录（泛型版本）。
         */
        inline fun <reified T : FreelyUser> loginWithEmailCodeAs(email: String, emailCode: String): FBCall<T> =
            fbCall { loginWithEmailCodeInternal(T::class.java, email, emailCode) }

        @PublishedApi
        internal suspend fun <T : FreelyUser> loginWithEmailCodeInternal(clazz: Class<T>, email: String, emailCode: String): T {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/login/email-code",
                mapOf("email" to email, "email_code" to emailCode)
            )
            return parseAuthAs(clazz, res)
        }

        /**
         * 短信验证码登录。
         *
         * @param phone 手机号
         * @param smsCode 验证码
         * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
         */
        @JvmName("loginWithSmsCode")
        fun loginWithSmsCode(phone: String, smsCode: String): FBCall<FreelyUser> =
            fbCall { loginWithSmsCodeInternal(FreelyUser::class.java, phone, smsCode) }

        /**
         * 短信验证码登录（泛型版本）。
         */
        inline fun <reified T : FreelyUser> loginWithSmsCodeAs(phone: String, smsCode: String): FBCall<T> =
            fbCall { loginWithSmsCodeInternal(T::class.java, phone, smsCode) }

        @PublishedApi
        internal suspend fun <T : FreelyUser> loginWithSmsCodeInternal(clazz: Class<T>, phone: String, smsCode: String): T {
            val res = HttpClient.post(
                "/public/apps/${FreelyBase.appKey}/auth/sms/login",
                mapOf("phone" to phone, "sms_code" to smsCode)
            )
            return parseAuthAs(clazz, res)
        }

        /**
         * 获取当前登录用户信息。
         *
         * @return FBCall<FBUser> 可链式调用 onSuccess/onFailure
         */
        @JvmName("currentUser")
        fun currentUser(): FBCall<FreelyUser> = fbCall { currentUserInternal(FreelyUser::class.java) }

        /**
         * 获取当前登录用户信息（泛型版本）。
         */
        inline fun <reified T : FreelyUser> currentUserAs(): FBCall<T> = fbCall { currentUserInternal(T::class.java) }

        @PublishedApi
        internal suspend fun <T : FreelyUser> currentUserInternal(clazz: Class<T>): T {
            val res = HttpClient.get("/public/apps/${FreelyBase.appKey}/auth/me")
            val user = clazz.getDeclaredConstructor().newInstance()
            user.objectId = res["object_id"]?.toString() ?: ""
            user.username = res["username"]?.toString() ?: ""
            user.email = res["email"]?.toString() ?: ""
            if (clazz != FreelyUser::class.java && user.objectId.isNotBlank()) {
                runCatching {
                    val dataRes = HttpClient.get("/public/apps/${FreelyBase.appKey}/classes/_User/${user.objectId}")
                    user.fillFromResponse(dataRes)
                }
            }
            return user
        }

        /**
         * 退出登录。
         */
        fun logout() = FreelyBase.clearToken()

        /**
         * 从本地缓存恢复用户信息（不发起网络请求）。
         *
         * @return 用户对象，未登录时返回 null
         */
        fun restoreCurrentUser(): FreelyUser? = restoreCurrentUserAs(FreelyUser::class.java)

        /**
         * 从本地缓存恢复用户信息（泛型版本）。
         */
        inline fun <reified T : FreelyUser> restoreCurrentUserAs(): T? = restoreCurrentUserAs(T::class.java)

        fun <T : FreelyUser> restoreCurrentUserAs(clazz: Class<T>): T? {
            if (!FreelyBase.isLoggedIn()) return null
            val json = FreelyBase.loadUserJson() ?: return null
            return runCatching {
                val map = Gson().fromJson<Map<String, Any?>>(
                    json, object : TypeToken<Map<String, Any?>>() {}.type
                )
                clazz.getDeclaredConstructor().newInstance().apply {
                    objectId = map["object_id"]?.toString() ?: ""
                    username = map["username"]?.toString() ?: ""
                    email = map["email"]?.toString() ?: ""
                    mobile = map["mobile"]?.toString() ?: ""
                }
            }.getOrNull()
        }

        @PublishedApi
        @Suppress("UNCHECKED_CAST")
        internal fun <T : FreelyUser> parseAuthAs(clazz: Class<T>, res: Map<String, Any?>): T {
            val token = res["access_token"]?.toString()
                ?: throw FreelyException("认证响应缺少 access_token")
            FreelyBase.setToken(token)
            val userMap = res["user"] as? Map<String, Any?> ?: emptyMap()
            return clazz.getDeclaredConstructor().newInstance().apply {
                objectId = userMap["object_id"]?.toString() ?: ""
                username = userMap["username"]?.toString() ?: ""
                email = userMap["email"]?.toString() ?: ""
                persistSession()
            }
        }
    }
}


