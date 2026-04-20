package com.freelybase.kotlin

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.freelybase.kotlin.data.AppUser
import com.freelybase.kotlin.data.Location

import com.freelybase.kotlin.databinding.ActivityDataBinding
import com.freelybase.kotlin.data.Post
import io.freelybase.kotlin.FBLiveQuery
import io.freelybase.kotlin.FBQuery
import io.freelybase.kotlin.FBUser
import io.freelybase.kotlin.FreelyBase

class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private var lastPost: Post? = null
    private var liveQuery: FBLiveQuery<Post>? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "数据操作"

        if (!FreelyBase.isLoggedIn()) { log("✗ 未登录，请先登录后再操作数据"); return }

        binding.btnCreate.setOnClickListener {
            Post(
                title = "Hello FreelyBase", count = 1, active = true,
                tags = mutableListOf("kotlin", "android", "sdk"),
                location = Location(city = "成都", lat = 30.57, lng = 104.07),
            ).apply { author = FBUser.restoreCurrentUserAs<AppUser>() }
                .save<Post>()
                .onLoading { binding.btnCreate.isEnabled = false }
                .onSuccess { post ->
                    lastPost = post
                    log("✓ 新增成功\n  objectId: ${post.objectId}\n  tags: ${post.tags}\n  location: ${post.location}")
                }
                .onFailure { log("✗ [${it.statusCode}] ${it.detail}") }
                .onFinally { binding.btnCreate.isEnabled = true }
                .bindTo(this)
        }

        binding.btnQuery.setOnClickListener {
            val objectId = lastPost?.objectId ?: run { log("✗ 请先新增一条数据"); return@setOnClickListener }
            FBQuery<Post>().include("author").getObject(objectId)
                .onLoading { binding.btnQuery.isEnabled = false }
                .onSuccess { post ->
                    log("✓ 查询成功\n  objectId: ${post.objectId}\n  title: ${post.title}\n  tags: ${post.tags}\n  location.city: ${post.location?.city}\n  author.username: ${post.author?.username}")
                }
                .onFailure { log("✗ ${it.detail}") }
                .onFinally { binding.btnQuery.isEnabled = true }
                .bindTo(this)
        }

        binding.btnQueryList.setOnClickListener {
            FBQuery<Post>().include("author", "likes").orderByDesc("created_at").limit(5).find()
                .onLoading { binding.btnQueryList.isEnabled = false }
                .onSuccess { posts ->
                    log("✓ 查询到 ${posts.size} 条\n" + posts.joinToString("\n") {
                        "  [${it.objectId}] ${it.title} author=${it.author?.username} likes=${it.likes.size}"
                    })
                }
                .onFailure { log("✗ ${it.detail}") }
                .onFinally { binding.btnQueryList.isEnabled = true }
                .bindTo(this)
        }

        binding.btnUpdate.setOnClickListener {
            val objectId = lastPost?.objectId ?: run { log("✗ 请先新增一条数据"); return@setOnClickListener }
            val currentUser = FBUser.restoreCurrentUserAs<AppUser>()
            FBQuery<Post>().getObject(objectId)
                .onLoading { binding.btnUpdate.isEnabled = false }
                .onSuccess { post ->
                    if (currentUser != null && post.likes.none { it.objectId == currentUser.objectId }) {
                        post.likes.add(currentUser)
                    }
                    post.update<Post>()
                        .onSuccess { log("✓ 点赞成功\n  objectId: ${post.objectId}\n  likes count: ${post.likes.size}") }
                        .onFailure { e -> log("✗ ${e.detail}") }
                        .onFinally { binding.btnUpdate.isEnabled = true }
                        .bindTo(this)
                }
                .onFailure { e -> log("✗ ${e.detail}"); binding.btnUpdate.isEnabled = true }
                .bindTo(this)
        }

        binding.btnDelete.setOnClickListener {
            val objectId = lastPost?.objectId ?: run { log("✗ 请先新增一条数据"); return@setOnClickListener }
            Post().apply { this.objectId = objectId }.delete()
                .onLoading { binding.btnDelete.isEnabled = false }
                .onSuccess { lastPost = null; log("✓ 删除成功\n  objectId: $objectId") }
                .onFailure { log("✗ ${it.detail}") }
                .onFinally { binding.btnDelete.isEnabled = true }
                .bindTo(this)
        }

        binding.btnLiveQuery.setOnClickListener {
            if (!isListening) startLiveQuery() else stopLiveQuery()
        }
    }

    private fun startLiveQuery() {
        liveQuery = FBLiveQuery<Post>()
            .onConnected { runOnUiThread { log("📡 监听已连接") } }
            .onDisconnected { runOnUiThread { log("📡 监听已断开") } }
            .onError { e -> runOnUiThread { log("📡 监听错误: ${e.message}") } }
            .on(FBLiveQuery.Event.CREATED) { post -> runOnUiThread { log("📥 新增\n  objectId: ${post.objectId}\n  title: ${post.title}") } }
            .on(FBLiveQuery.Event.UPDATED) { post -> runOnUiThread { log("✏️ 更新\n  objectId: ${post.objectId}\n  title: ${post.title}") } }
            .on(FBLiveQuery.Event.DELETED) { post -> runOnUiThread { log("🗑️ 删除\n  objectId: ${post.objectId}") } }
        liveQuery!!.subscribe()
        isListening = true
        binding.btnLiveQuery.text = "停止监听"
    }

    private fun stopLiveQuery() {
        liveQuery?.unsubscribe()
        liveQuery = null
        isListening = false
        binding.btnLiveQuery.text = "开始监听"
        log("📡 监听已停止")
    }

    override fun onDestroy() {
        super.onDestroy()
        liveQuery?.unsubscribe()
    }

    private fun log(msg: String) {
        Log.d("FB", msg)
        val current = binding.tvLog.text.toString()
        binding.tvLog.text = "$msg\n\n$current"
    }
}
