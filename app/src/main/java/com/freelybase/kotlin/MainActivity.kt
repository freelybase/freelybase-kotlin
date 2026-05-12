package com.freelybase.kotlin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.freelybase.kotlin.auth.LoginActivity
import com.freelybase.kotlin.databinding.ActivityMainBinding
import io.freelybase.kotlin.FreelyUser
import io.freelybase.kotlin.FreelyBase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateStatus()

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnData.setOnClickListener {
            if (!FreelyBase.isLoggedIn()) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, DataActivity::class.java))
        }

        binding.btnFile.setOnClickListener {
            if (!FreelyBase.isLoggedIn()) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, FileActivity::class.java))
        }

        binding.btnAi.setOnClickListener {
            if (!FreelyBase.isLoggedIn()) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, AiActivity::class.java))
        }

        binding.btnServerTime.setOnClickListener {
            FreelyBase.getServerTime()
                .onSuccess { log("服务器时间: $it") }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }

        binding.btnLogout.setOnClickListener {
            FreelyUser.logout()
            updateStatus()
            log("已登出")
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val user = FreelyUser.restoreCurrentUser()
        binding.tvStatus.text = if (user != null) "已登录 ✓  ${user.username.ifBlank { user.email }}" else "未登录"
    }

    private fun log(msg: String) {
        val current = binding.tvLog.text.toString()
        binding.tvLog.text = "▶ $msg\n$current"
    }
}
