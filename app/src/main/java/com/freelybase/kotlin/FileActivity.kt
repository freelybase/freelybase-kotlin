package com.freelybase.kotlin

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.freelybase.kotlin.databinding.ActivityFileBinding
import com.freelybase.kotlin.data.Post
import io.freelybase.kotlin.FBFile
import io.freelybase.kotlin.FBQuery
import io.freelybase.kotlin.FreelyBase

class FileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileBinding
    private var lastUploadedPath: String? = null
    private var lastPostObjectId: String? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        log("⏳ 上传中...")
        FBFile.upload(contentResolver, uri)
            .onSuccess { file ->
                lastUploadedPath = file.path
                log("✓ 上传成功\n  path: ${file.path}\n  url:  ${file.url}")
            }
            .onFailure { log("✗ ${it.detail}") }
            .bindTo(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "文件管理"

        if (!FreelyBase.isLoggedIn()) { log("✗ 未登录，请先登录后再操作"); return }

        binding.btnPickAndUpload.setOnClickListener { pickFile.launch("*/*") }

        binding.btnUploadBytes.setOnClickListener {
            val bytes = "Hello FreelyBase! 时间: ${System.currentTimeMillis()}".toByteArray()
            FBFile.upload(bytes, "hello.txt", "text/plain")
                .onSuccess { file ->
                    lastUploadedPath = file.path
                    log("✓ 字节上传成功\n  path: ${file.path}\n  url:  ${file.url}")
                }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }

        binding.btnSaveWithFile.setOnClickListener {
            val path = lastUploadedPath ?: run { log("✗ 请先上传一个文件"); return@setOnClickListener }
            Post(title = "带图片的帖子", count = 1, image = FBFile.fromPath(path))
                .save<Post>()
                .onSuccess { post ->
                    lastPostObjectId = post.objectId
                    log("✓ 保存成功\n  objectId: ${post.objectId}\n  image.url: ${post.image?.url}")
                }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }

        binding.btnQueryWithFile.setOnClickListener {
            val objectId = lastPostObjectId ?: run { log("✗ 请先执行「存入数据对象」"); return@setOnClickListener }
            FBQuery<Post>().getObject(objectId)
                .onSuccess { post ->
                    log("✓ 查询成功\n  objectId: ${post.objectId}\n  title: ${post.title}\n  image.url: ${post.image?.url}\n  image.name: ${post.image?.name}")
                }
                .onFailure { log("✗ ${it.detail}") }
                .bindTo(this)
        }
    }

    private fun log(msg: String) {
        Log.d("FBFile", msg)
        val current = binding.tvLog.text.toString()
        binding.tvLog.text = "$msg\n\n$current"
    }
}
