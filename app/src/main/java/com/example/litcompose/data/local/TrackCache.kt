package com.example.litcompose.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.litcompose.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 远程歌曲的本地缓存：播放解析出的链接会在后台缓存到公共音乐目录（MediaStore），
 * 系统媒体扫描器及其他音乐软件可以扫描到这些文件；
 * 下次播放命中缓存直接走本地，未命中再走网络。
 *
 * 下载采用流式写盘（8KB 分块），不会把整首音频一次性读进内存——
 * 否则歌单“播放全部”时 14 首并发缓存会让 Java 堆飙升到接近 OOM。
 */
class TrackCache(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** trackId -> 已缓存文件的 content:// uri 索引 */
    private val index = appContext.getSharedPreferences("music_cache_index", Context.MODE_PRIVATE)

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

    /** 限制并发缓存下载数：避免歌单播放时多首同时全量下载 */
    private val downloadSemaphore = Semaphore(2)

    /** 返回缓存歌曲的 content:// uri；未命中或文件已失效返回 null */
    fun get(trackId: String): String? {
        val cached = index.getString(trackId, null) ?: return null
        if (!uriExists(cached)) {
            index.edit().remove(trackId).apply()
            return null
        }
        return cached
    }

    /** 下载 url 缓存到公共音乐目录，成功返回 content:// uri，失败返回 null（幂等） */
    suspend fun cache(
        track: Track,
        url: String,
    ): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        get(track.id)?.let { return@withContext it }
        downloadSemaphore.withPermit {
            // 排队等待期间可能已被其他协程缓存完成，二次检查
            get(track.id)?.let { return@withContext it }
            val uri = downloadToMediaStore(track, url) ?: return@withContext null
            index.edit().putString(track.id, uri.toString()).apply()
            uri.toString()
        }
    }

    /** 防盗链：先普通请求，失败带音乐站 Referer 重试一次（每次都是全新目标，避免半截文件叠加） */
    private fun downloadToMediaStore(
        track: Track,
        url: String,
    ): Uri? {
        val viaNormal = runCatching { streamToMediaStore(track, url, null) }.getOrNull()
        if (viaNormal != null) return viaNormal
        return runCatching { streamToMediaStore(track, url, "https://music.163.com/") }.getOrNull()
    }

    /** 流式下载整首歌到 MediaStore（API 29+）或公共 Music 目录文件（API 24-28），失败时清理半截目标 */
    private fun streamToMediaStore(
        track: Track,
        url: String,
        referer: String?,
    ): Uri? {
        val displayName = displayNameFor(track, url)
        val mime = mimeFromExtension(extensionFromUrl(url))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.artist)
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MUSIC}/$MUSIC_DIR",
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            val uri =
                runCatching {
                    resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                }.getOrNull() ?: return null
            val written =
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        downloadTo(url, referer, out)
                    } ?: false
                }.getOrDefault(false)
            if (!written) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
            return uri
        }
        // API 24-28：写入公共目录需要 WRITE_EXTERNAL_STORAGE 权限，使用 DATA 字段
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), MUSIC_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, displayName)
        val written =
            runCatching {
                file.delete()
                file.outputStream().use { out -> downloadTo(url, referer, out) }
            }.getOrDefault(false)
        if (!written) {
            file.delete()
            return null
        }
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist)
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, file.absolutePath)
            }
        return runCatching {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    /** 流式下载 url 到 out（8KB 分块，不进内存），成功返回 true */
    private fun downloadTo(
        url: String,
        referer: String?,
        out: OutputStream,
    ): Boolean {
        val builder = Request.Builder().url(url)
        if (referer != null) {
            builder.header("Referer", referer)
        }
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return false
            response.body?.byteStream()?.use { input ->
                input.copyTo(out)
            } ?: return false
        }
        return true
    }

    private fun displayNameFor(
        track: Track,
        url: String,
    ): String {
        val title = track.title.ifBlank { "未知歌曲" }
        val artist = track.artist.ifBlank { "未知歌手" }
        val ext = extensionFromUrl(url)
        return "${sanitizeFileName("$artist - $title")}.$ext"
    }

    /** 清洗文件名中的非法字符（Windows/Linux 保留字符），空串回退为 unknown */
    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifBlank { "unknown" }

    /** 校验 MediaStore 中的文件记录是否仍然存在 */
    private fun uriExists(uriString: String): Boolean =
        runCatching {
            resolver.query(
                Uri.parse(uriString),
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)

    /** 从源链接推断真实格式，无法识别时默认 mp3 */
    private fun extensionFromUrl(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (ext in KNOWN_EXTENSIONS) ext else "mp3"
    }

    private fun mimeFromExtension(ext: String): String =
        when (ext) {
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }

    private companion object {
        const val MUSIC_DIR = "轻听"
        val KNOWN_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav")
    }
}
