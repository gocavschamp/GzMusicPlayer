package com.example.litcompose.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.litcompose.data.remote.CoCoApi
import com.example.litcompose.data.remote.CoCoDownloadResponse
import com.example.litcompose.domain.model.RemoteTrackMeta
import com.example.litcompose.domain.model.Track
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 把网络歌曲下载到本地音乐库。
 * - iTunes 歌曲：直接下载 sourceUri
 * - COCO 远程歌曲：先请求 /api/download，服务端禁用时回退到解析出的 url
 * API 29+ 写入公共 Music 目录（会被 MediaStore 收录，扫描页可见）；
 * 低版本回退到应用专属目录。
 *
 * 下载采用流式写盘（8KB 分块），整首歌不会一次性读进内存，避免大文件下载把 Java 堆顶到 OOM。
 */
class TrackDownloader(
    private val context: Context,
    private val cocoApi: CoCoApi,
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val extraMapType =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

    private companion object {
        const val TAG = "LitComposeDL"
    }

    /** 下载成功返回本地 Uri，失败返回 null。阻塞 IO 全部切到 IO 线程。 */
    suspend fun download(track: Track): Uri? = withContext(Dispatchers.IO) {
        val fileName = buildFileName(track)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadToMediaStore(track, fileName)
        } else {
            downloadToLegacy(track, fileName)
        }
    }

    /** 流式下载到 MediaStore 条目；失败删除半截条目并重试（防盗链 Referer） */
    private suspend fun downloadToMediaStore(
        track: Track,
        fileName: String,
    ): Uri? {
        val resolver = context.contentResolver
        repeat(2) { attempt ->
            val uri = insertMediaStore(track, fileName) ?: return null
            val written =
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        streamTrack(track, out)
                    } ?: false
                }.getOrDefault(false)
            if (written) return uri
            runCatching { resolver.delete(uri, null, null) }
            if (attempt == 1) {
                Log.e(TAG, "download failed twice for ${track.id}")
            }
        }
        return null
    }

    private fun insertMediaStore(
        track: Track,
        fileName: String,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(fileName))
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/轻听")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist.ifBlank { "未知歌手" })
                put(MediaStore.Audio.Media.DURATION, track.durationMs.coerceAtLeast(0L))
            }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching { context.contentResolver.insert(collection, values) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private suspend fun downloadToLegacy(
        track: Track,
        fileName: String,
    ): Uri? {
        val dir =
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: return null
        dir.mkdirs()
        val file = File(dir, fileName)
        return runCatching {
            file.delete()
            file.outputStream().use { out ->
                streamTrack(track, out)
                out.flush()
            }
            Uri.fromFile(file)
        }.getOrNull()
    }

    /** 按歌曲渠道把音频流写入 out，成功返回 true */
    private suspend fun streamTrack(
        track: Track,
        out: OutputStream,
    ): Boolean {
        if (track.remote != null && track.sourceUri.isBlank()) {
            return downloadCoCoTo(track, out)
        }
        return downloadHttpTo(track.sourceUri, out)
    }

    /** COCO 渠道：/api/download 优先，禁用/失败时回退解析接口拿新鲜链接（流式写盘） */
    private suspend fun downloadCoCoTo(
        track: Track,
        out: OutputStream,
    ): Boolean {
        val meta = track.remote ?: return false
        val fileName = coCoFileName(track, meta)
        val response =
            runCatching {
                cocoApi.download(
                    id = meta.songId,
                    provider = meta.provider,
                    filename = fileName,
                    extra = meta.extraJson,
                )
            }.getOrNull() ?: run {
                Log.e(TAG, "download api call failed: id=${meta.songId} provider=${meta.provider}")
                return false
            }
        if (response.isSuccessful) {
            return response.body()?.use { body ->
                body.byteStream().use { input -> input.copyTo(out) }
                true
            } ?: false
        }
        Log.w(TAG, "download api status=${response.code()} for id=${meta.songId}, fallback to resolve")
        // 服务端禁用下载时返回 {"error":"Download disabled","url":"..."}
        val errorUrl =
            response.errorBody()?.string()?.let { body ->
                runCatching {
                    moshi.adapter(CoCoDownloadResponse::class.java).fromJson(body)?.url
                }.getOrNull()
            }
        if (downloadHttpTo(errorUrl, out)) return true
        // 兜底：重新调解析接口拿最新播放链接再下载
        val freshUrl =
            runCatching {
                cocoApi.resolveUrl(id = meta.songId, provider = meta.provider, extra = meta.extraJson).url
            }.getOrNull()
        if (freshUrl.isNullOrBlank()) {
            Log.e(TAG, "resolve fresh url failed for download: id=${meta.songId}")
            return false
        }
        return downloadHttpTo(freshUrl, out)
    }

    /** http 流式下载：先直连，失败带音乐站 Referer 重试 */
    private fun downloadHttpTo(
        url: String?,
        out: OutputStream,
    ): Boolean {
        if (url.isNullOrBlank()) return false
        if (runCatching { httpStreamTo(url, null, out) }.getOrDefault(false)) return true
        if (runCatching { httpStreamTo(url, "https://music.163.com/", out) }.getOrDefault(false)) return true
        Log.e(TAG, "download http failed for url=$url")
        return false
    }

    private fun httpStreamTo(
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

    private fun buildFileName(track: Track): String {
        val safeTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "audio" }
        if (track.remote != null) {
            val ext = parseExtraSelectedFormat(track.remote?.extraJson) ?: "mp3"
            return "$safeTitle.$ext"
        }
        return "$safeTitle-${track.id.hashCode().toUInt().toString(16)}.m4a"
    }

    private fun coCoFileName(
        track: Track,
        meta: RemoteTrackMeta,
    ): String {
        val safeTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "audio" }
        val ext = parseExtraSelectedFormat(meta.extraJson) ?: "mp3"
        return "$safeTitle.$ext"
    }

    private fun parseExtraSelectedFormat(extraJson: String?): String? {
        if (extraJson.isNullOrBlank()) return null
        return runCatching {
            val map = moshi.adapter<Map<String, Any?>>(extraMapType).fromJson(extraJson) ?: return null
            map["selectedFormat"]?.toString()
        }.getOrNull()
    }

    private fun mimeFor(fileName: String): String =
        when (fileName.substringAfterLast('.', "mp3").lowercase()) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
}
