package io.freelybase.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 文件管理，封装上传与 URL 解析。
 *
 * 用法：
 * ```kotlin
 * // 上传本地文件
 * val fbFile = FBFile.upload(file)
 * println(fbFile.path)   // /uploads/data-files/xxx.jpg
 * println(fbFile.url)    // https://www.freelybase.com/api/uploads/data-files/xxx.jpg
 *
 * // 上传字节数组
 * val fbFile = FBFile.upload(bytes, "photo.jpg")
 *
 * // 上传 InputStream
 * val fbFile = FBFile.upload(inputStream, "photo.jpg", "image/jpeg")
 *
 * // 从已有路径构建（如从数据库读取的字段值）
 * val fbFile = FBFile.fromPath("/uploads/data-files/xxx.jpg")
 * println(fbFile.url)
 * ```
 */
class FBFile private constructor(
    /** 服务端相对路径，如 /uploads/data-files/xxx.jpg */
    val path: String
) {
    /** 完整可访问 URL */
    val url: String get() = buildUrl(path)

    /** 文件名（不含路径） */
    val name: String get() = path.substringAfterLast("/")

    override fun toString() = path

    companion object {

        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val gson = Gson()

        /**
         * 上传本地 File
         */
        fun upload(file: File, mimeType: String? = null): FBCall<FBFile> = fbCall {
            val mime = mimeType ?: guessMime(file.name)
            val body = file.asRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            doUpload(part)
        }

        /**
         * 上传字节数组
         */
        fun upload(bytes: ByteArray, fileName: String, mimeType: String? = null): FBCall<FBFile> = fbCall {
            val mime = mimeType ?: guessMime(fileName)
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            doUpload(part)
        }

        /**
         * 上传 InputStream（会读取全部内容到内存）
         */
        fun upload(stream: InputStream, fileName: String, mimeType: String? = null): FBCall<FBFile> =
            fbCall { doUpload(MultipartBody.Part.createFormData("file", fileName, stream.readBytes().toRequestBody((mimeType ?: guessMime(fileName)).toMediaTypeOrNull()))) }

        /**
         * 上传 Android Uri（自动解析文件名和 MIME 类型）
         */
        fun upload(contentResolver: ContentResolver, uri: Uri): FBCall<FBFile> = fbCall {
            val fileName = resolveUriFileName(contentResolver, uri)
            val mimeType = contentResolver.getType(uri)
            val stream = contentResolver.openInputStream(uri)
                ?: throw FreelyBaseException("无法读取文件 Uri: $uri")
            doUpload(MultipartBody.Part.createFormData("file", fileName, stream.readBytes().toRequestBody(mimeType?.toMediaTypeOrNull())))
        }

        private fun resolveUriFileName(contentResolver: ContentResolver, uri: Uri): String {
            var name = "upload_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx) ?: name
                }
            }
            return name
        }

        /**
         * 从已有路径构建 FBFile（不发起网络请求）
         */
        fun fromPath(path: String): FBFile = FBFile(path)

        /**
         * 从完整 URL 构建 FBFile（提取相对路径）
         */
        fun fromUrl(url: String): FBFile {
            val base = FreelyBase.baseUrl.trimEnd('/')
            val path = if (url.startsWith(base)) url.removePrefix(base) else url
            return FBFile(path)
        }

        private suspend fun doUpload(part: MultipartBody.Part): FBFile = withContext(Dispatchers.IO) {
            FreelyBase.requireInitialized()
            val appId = FreelyBase.appId
            val url = "${FreelyBase.baseUrl}/public/apps/$appId/files"

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(part)
                .build()

            val reqBuilder = Request.Builder()
                .url(url)
                .post(multipart)
            FreelyBase.token?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }

            val response = client.newCall(reqBuilder.build()).execute()
            val rawBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val detail = runCatching {
                    val map = gson.fromJson<Map<String, Any?>>(rawBody, object : TypeToken<Map<String, Any?>>() {}.type)
                    map["detail"]?.toString() ?: rawBody
                }.getOrDefault(rawBody)
                throw FreelyBaseException("POST $url", response.code, detail)
            }

            val result = runCatching {
                gson.fromJson<Map<String, Any?>>(rawBody, object : TypeToken<Map<String, Any?>>() {}.type)
            }.getOrNull() ?: throw FreelyBaseException("文件上传响应解析失败")

            val path = result["path"]?.toString()
                ?: throw FreelyBaseException("文件上传响应缺少 path 字段")

            FBFile(path)
        }

        internal fun buildUrl(path: String): String {
            if (path.startsWith("http://") || path.startsWith("https://")) return path
            return "${FreelyBase.baseUrl.trimEnd('/')}$path"
        }

        private fun guessMime(fileName: String): String {
            return when (fileName.substringAfterLast(".").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png"         -> "image/png"
                "gif"         -> "image/gif"
                "webp"        -> "image/webp"
                "pdf"         -> "application/pdf"
                "mp4"         -> "video/mp4"
                "mp3"         -> "audio/mpeg"
                "txt"         -> "text/plain"
                "json"        -> "application/json"
                else          -> "application/octet-stream"
            }
        }
    }
}

/**
 * String 扩展，方便从 FBObject 的 File 字段直接获取 FBFile。
 *
 * 用法：
 * ```kotlin
 * class Post : FBObject("Post") {
 *     var image: String = ""  // 存储 /uploads/data-files/xxx.jpg
 * }
 *
 * val post = FBQuery<Post>().first()
 * val file = post.image.toFBFile()
 * println(file.url)  // 完整 URL
 * ```
 */
fun String.toFBFile(): FBFile = FBFile.fromPath(this)

/**
 * Gson TypeAdapter：FBFile <-> JSON 字符串
 * 序列化：FBFile -> "/uploads/data-files/xxx.jpg"
 * 反序列化："..." -> FBFile.fromPath(...)
 */
internal class FBFileTypeAdapter : com.google.gson.TypeAdapter<FBFile>() {
    override fun write(out: com.google.gson.stream.JsonWriter, value: FBFile?) {
        if (value == null) out.nullValue() else out.value(value.path)
    }

    override fun read(input: com.google.gson.stream.JsonReader): FBFile? {
        if (input.peek() == com.google.gson.stream.JsonToken.NULL) {
            input.nextNull()
            return null
        }
        val path = input.nextString()
        return if (path.isBlank()) null else FBFile.fromPath(path)
    }
}

/** 带 FBFile 支持的 Gson 实例，供 FBObject 内部使用 */
internal val fbGson: com.google.gson.Gson = com.google.gson.GsonBuilder()
    .registerTypeAdapter(FBFile::class.java, FBFileTypeAdapter())
    .create()
