package com.freelybase.kotlin.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.freelybase.kotlin.databinding.ActivityLoginBinding
import com.freelybase.kotlin.data.AppUser
import io.freelybase.kotlin.FreelyUser

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "登录 / 注册"

        binding.btnSendCode.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            if (email.isEmpty()) { toast("请输入邮箱"); return@setOnClickListener }
            FreelyUser.sendEmailCode(email)
                .onSuccess { log("✓ $it") }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            val code = binding.etEmailCode.text?.toString()?.trim() ?: ""
            if (email.isEmpty() || password.isEmpty() || code.isEmpty()) {
                toast("请填写邮箱、密码和验证码"); return@setOnClickListener
            }
            AppUser().apply {
                this.email = email; this.password = password
                this.nickname = "新用户_${email.substringBefore("@")}"; this.city = "成都"; this.age = 18
            }.signUpByEmail(emailCode = code)
                .onSuccess { user ->
                    // 注册成功后重新获取完整的用户信息
                    FreelyUser.currentUserAs<AppUser>()
                        .onSuccess { appUser ->
                            appUser.nickname = "新用户_${email.substringBefore("@")}"
                            appUser.city = "成都"
                            appUser.age = 18
                            appUser.updateProfile()
                                .onSuccess {
                                    log("✓ 注册成功\n  objectId: ${appUser.objectId}\n  nickname: ${appUser.nickname}")
                                    finish()
                                }
                                .bindTo(this)
                        }
                        .bindTo(this)
                }
                .onFailure { e -> log("✗ ${e.detail}"); toast(e.detail) }
                .bindTo(this)
        }

        binding.btnLoginEmail.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            if (email.isEmpty() || password.isEmpty()) { toast("请填写邮箱和密码"); return@setOnClickListener }
            FreelyUser.loginAs<AppUser>(email, password)
                .onSuccess { log("✓ 登录成功\n  username: ${it.username}"); finish() }
                .onFailure { log("✗ ${it.detail}"); toast(it.detail) }
                .bindTo(this)
        }

        binding.btnRegisterUsername.setOnClickListener {
            val username = binding.etUsername.text?.toString()?.trim() ?: ""
            val password = binding.etUsernamePassword.text?.toString() ?: ""
            if (username.isEmpty() || password.isEmpty()) { toast("请填写用户名和密码"); return@setOnClickListener }
            AppUser().apply { this.username = username; this.password = password }
                .signUpByUsername()
                .onSuccess { log("✓ 注册成功\n  objectId: ${it.objectId}\n  username: ${it.username}"); finish() }
                .onFailure { log("✗ ${it.detail}"); toast(it.detail) }
                .bindTo(this)
        }

        binding.btnLoginUsername.setOnClickListener {
            val username = binding.etUsername.text?.toString()?.trim() ?: ""
            val password = binding.etUsernamePassword.text?.toString() ?: ""
            if (username.isEmpty() || password.isEmpty()) { toast("请填写用户名和密码"); return@setOnClickListener }
            FreelyUser.loginAs<AppUser>(username, password)
                .onSuccess { log("✓ 登录成功\n  username: ${it.username}"); finish() }
                .onFailure { log("✗ ${it.detail}"); toast(it.detail) }
                .bindTo(this)
        }

        binding.btnSendSms.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            if (phone.isEmpty()) { toast("请输入手机号"); return@setOnClickListener }
            FreelyUser.sendSmsCode(phone)
                .onSuccess { log("✓ $it") }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }

        binding.btnLoginSms.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            val code = binding.etSmsCode.text?.toString()?.trim() ?: ""
            if (phone.isEmpty() || code.isEmpty()) { toast("请填写手机号和验证码"); return@setOnClickListener }
            FreelyUser.loginWithSmsCodeAs<AppUser>(phone, code)
                .onSuccess { log("✓ 登录成功\n  username: ${it.username}"); finish() }
                .onFailure { log("✗ ${it.detail}"); toast(it.detail) }
                .bindTo(this)
        }

        binding.btnLoginPhone.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            val password = binding.etPhonePassword.text?.toString() ?: ""
            if (phone.isEmpty() || password.isEmpty()) { toast("请填写手机号和密码"); return@setOnClickListener }
            FreelyUser.loginAs<AppUser>(phone, password)
                .onSuccess { log("✓ 登录成功\n  username: ${it.username}"); finish() }
                .onFailure { log("✗ ${it.detail}"); toast(it.detail) }
                .bindTo(this)
        }
    }

    private fun log(msg: String) {
        val current = binding.tvResult.text.toString()
        binding.tvResult.text = "$msg\n$current"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
