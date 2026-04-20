package com.freelybase.kotlin

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.freelybase.kotlin.FBAI

/**
 * AI 聊天演示页面。
 */
class AiActivity : AppCompatActivity() {

    private lateinit var tvQuota: TextView
    private lateinit var etMessage: EditText
    private lateinit var tvResponse: TextView
    private lateinit var btnSend: Button
    private lateinit var btnStop: Button
    private lateinit var btnRefreshQuota: Button
    private lateinit var btnClearHistory: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai)

        tvQuota = findViewById(R.id.tv_quota)
        etMessage = findViewById(R.id.et_message)
        tvResponse = findViewById(R.id.tv_response)
        btnSend = findViewById(R.id.btn_send)
        btnStop = findViewById(R.id.btn_stop)
        btnRefreshQuota = findViewById(R.id.btn_refresh_quota)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        btnRefreshQuota.setOnClickListener { loadQuota() }
        btnSend.setOnClickListener { sendMessage() }
        btnStop.setOnClickListener { stopGeneration() }
        btnClearHistory.setOnClickListener { clearHistory() }

        // 初始状态停止按钮不可用
        btnStop.isEnabled = false

        loadQuota()
    }

    private fun loadQuota() {
        FBAI.getQuota()
            .onLoading { btnRefreshQuota.isEnabled = false }
            .onSuccess { quota ->
                tvQuota.text = "剩余 Token: ${quota.remaining}\n" +
                        "购买单位: ${quota.tokenUnit}\n" +
                        "单价: ${quota.priceFenPerUnit / 100.0} 元"
            }
            .onFailure { e ->
                Toast.makeText(this, "获取配额失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .onFinally { btnRefreshQuota.isEnabled = true }
            .bindTo(this)
    }

    private fun sendMessage() {
        val content = etMessage.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show()
            return
        }

        etMessage.text.clear()

        // 如果是第一条消息，设置系统提示词
        val chatBuilder = if (FBAI.getHistory().isEmpty()) {
            FBAI.chat(content)
                .prompt("你是一个友好的 AI 助手，请用简洁、清晰的语言回答问题。")
        } else {
            FBAI.chat(content)
        }

        chatBuilder.onLoading {
                btnSend.isEnabled = false
                btnStop.isEnabled = true
                tvResponse.text = "AI 思考中..."
            }
            .onSuccess { response ->
                tvResponse.text = "AI: ${response.content}\n\n" +
                        "消耗 Token: ${response.tokensUsed}\n" +
                        "剩余 Token: ${response.remaining}\n" +
                        "对话轮数: ${FBAI.getHistory().count { it.role == "user" }}"
                tvQuota.text = "剩余 Token: ${response.remaining}"
            }
            .onFailure { e ->
                Toast.makeText(this, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .onFinally { 
                btnSend.isEnabled = true
                btnStop.isEnabled = false
            }
            .bindTo(this)
    }

    private fun stopGeneration() {
        FBAI.stop()
        btnStop.isEnabled = false
        btnSend.isEnabled = true
        Toast.makeText(this, "已停止 AI 输出", Toast.LENGTH_SHORT).show()
    }

    private fun clearHistory() {
        FBAI.clearHistory()
        tvResponse.text = "对话历史已清除"
        Toast.makeText(this, "对话历史已清除", Toast.LENGTH_SHORT).show()
    }
}
