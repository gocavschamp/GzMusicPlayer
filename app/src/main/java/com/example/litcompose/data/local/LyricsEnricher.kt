package com.example.litcompose.data.local

import android.content.Context
import android.util.Log
import com.example.litcompose.data.db.LyricsCacheDao
import com.example.litcompose.data.db.LyricsCacheEntity
import com.example.litcompose.data.remote.CoCoApi
import com.example.litcompose.data.remote.CoCoTrackDto
import com.example.litcompose.domain.model.LyricLine
import com.example.litcompose.domain.model.Track
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/** 单曲匹配/下载结果 */
data class EnrichResult(
    val trackId: String,
    val matched: Boolean,
    val lyricCount: Int,
    val artworkSaved: Boolean,
    val error: String? = null,
)

/** 批量匹配进度 */
data class BatchProgress(
    val total: Int,
    val done: Int,
    val matched: Int,
    val failed: Int,
    val currentTitle: String = "",
)

/**
 * 歌词/封面补全器：针对没有歌词（主要是本地歌曲）的曲目，
 * 用「歌曲名 + 歌手」去 COCO 搜索，找到**时长相符**的结果后下载歌词与封面并持久化到
 * lyrics_cache 表（歌词存 Moshi JSON，封面存应用私有目录文件）。
 *
 * - 远程歌曲（remote != null）：直接用其 provider/songId/extra 拉歌词，封面取自身 artworkUrl；
 * - 本地歌曲：逐源搜索（netease → qq → kugou → migu），取首个时长匹配的结果。
 *
 * 下载均为流式写盘，封面为小图片直接落文件，不会把大资源读进内存。
 */
class LyricsEnricher(
    context: Context,
    private val cocoApi: CoCoApi,
    private val lyricsCacheDao: LyricsCacheDao,
) {
    private val appContext = context.applicationContext
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val linesType = Types.newParameterizedType(List::class.java, LyricLine::class.java)
    private val extraMapType =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    private companion object {
        const val TAG = "LitComposeLyrics"
        /** 本地歌曲搜索时依次尝试的源 */
        val PROVIDERS = listOf("netease", "qq", "kugou", "migu")
    }

    /** 读取已缓存的歌词：null 表示从未匹配过；空列表表示匹配过但接口无歌词 */
    suspend fun cachedLyrics(trackId: String): List<LyricLine>? {
        val entity = lyricsCacheDao.getById(trackId) ?: return null
        val json = entity.linesJson ?: return emptyList()
        return runCatching {
            moshi.adapter<List<LyricLine>>(linesType).fromJson(json)
        }.getOrDefault(emptyList())
    }

    /** 读取已保存的封面 file:// URI，未保存返回 null */
    suspend fun cachedArtworkPath(trackId: String): String? =
        lyricsCacheDao.getById(trackId)?.artworkPath

    /** 单曲匹配 + 下载歌词/封面 + 保存；失败时 matched=false 并带错误信息 */
    suspend fun enrich(track: Track): EnrichResult = withContext(Dispatchers.IO) {
        // 1. 定位目标歌曲：远程歌曲直接用自身标识，本地歌曲走搜索匹配
        val match = findMatch(track)
        if (match == null) {
            Log.w(TAG, "no match: title=${track.title} artist=${track.artist}")
            return@withContext EnrichResult(track.id, matched = false, lyricCount = 0, artworkSaved = false, error = "未匹配到「${track.title}」")
        }

        // 2. 拉歌词（COCO 返回秒，转毫秒；过滤空行避免占位行干扰高亮）
        val lines =
            runCatching {
                cocoApi.lyric(id = match.songId, provider = match.provider, extra = match.extraJson)
                    .lines
                    .filter { it.text.isNotBlank() }
                    .map { LyricLine(timeMs = (it.time * 1000).toLong(), text = it.text.trim()) }
            }.onFailure { Log.w(TAG, "lyric failed: id=${match.songId} provider=${match.provider}", it) }
                .getOrElse { return@withContext EnrichResult(track.id, matched = true, lyricCount = 0, artworkSaved = false, error = "歌词接口失败") }

        // 3. 封面下载并保存到应用私有目录
        val artworkPath = match.cover?.takeIf { it.isNotBlank() }?.let { saveArtwork(track.id, it) }

        // 4. 持久化（重复获取直接覆盖，幂等）
        val linesJson = moshi.adapter<List<LyricLine>>(linesType).toJson(lines)
        lyricsCacheDao.upsert(
            LyricsCacheEntity(
                trackId = track.id,
                linesJson = linesJson,
                artworkPath = artworkPath,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        Log.d(TAG, "enriched: trackId=${track.id} lines=${lines.size} artwork=$artworkPath")
        EnrichResult(
            trackId = track.id,
            matched = true,
            lyricCount = lines.size,
            artworkSaved = artworkPath != null,
        )
    }

    /**
     * 批量处理所有歌曲，返回实时进度流。内部并发限流（2 路）、全部在 IO 线程，
     * 供主页「一键获取歌词封面」后台任务使用。
     */
    fun enrichAll(tracks: List<Track>): Flow<BatchProgress> = flow {
        if (tracks.isEmpty()) {
            emit(BatchProgress(0, 0, 0, 0))
            return@flow
        }
        val semaphore = Semaphore(2)
        var done = 0
        var matched = 0
        var failed = 0
        coroutineScope {
            val deferred =
                tracks.map { track ->
                    async {
                        track to
                            runCatching { enrich(track) }.getOrElse {
                                EnrichResult(track.id, matched = false, lyricCount = 0, artworkSaved = false, error = it.message)
                            }
                    }
                }
            deferred.forEach { d ->
                val (track, result) = d.await()
                done++
                if (result.error != null || !result.matched) {
                    failed++
                } else {
                    matched++
                }
                emit(BatchProgress(tracks.size, done, matched, failed, currentTitle = track.title))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 定位目标歌曲（远程直接返回自身；本地搜索时长相符的首个结果） */
    private suspend fun findMatch(track: Track): MatchTarget? {
        track.remote?.let { meta ->
            return MatchTarget(
                songId = meta.songId,
                provider = meta.provider,
                extraJson = meta.extraJson,
                cover = track.artworkUrl,
            )
        }
        val query =
            listOf(track.title, track.artist)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
        if (query.isBlank()) return null
        for (provider in PROVIDERS) {
            val hit =
                runCatching { cocoApi.search(query, provider, limit = 20).items }
                    .onFailure { Log.w(TAG, "search failed: query=$query provider=$provider", it) }
                    .getOrNull()
                    ?.firstOrNull { durationMatches(track.durationMs, it) }
            if (hit != null) {
                Log.d(TAG, "matched: query=$query provider=${hit.provider} id=${hit.id}")
                return MatchTarget(
                    songId = hit.id,
                    provider = hit.provider.ifBlank { provider },
                    extraJson =
                        hit.extra
                            ?.let { moshi.adapter<Map<String, Any?>>(extraMapType).toJson(it) }
                            .orEmpty(),
                    cover = hit.cover,
                )
            }
        }
        return null
    }

    /**
     * 时长匹配：未知时长放宽；误差 ≤ 1.5 秒或 2.5%（取较大者）。
     * 容差收紧后命中的版本更接近实际音频，歌词时间戳偏移更小（避免"慢几秒"）。
     */
    private fun durationMatches(targetMs: Long, dto: CoCoTrackDto): Boolean {
        if (targetMs <= 0L) return true
        val candidateMs = parseDurationMs(dto.duration.orEmpty())
        if (candidateMs <= 0L) return true
        return abs(targetMs - candidateMs) <= max(1_500L, targetMs / 40)
    }

    /** 下载封面到应用私有目录，已存在直接复用；返回 file:// URI */
    private suspend fun saveArtwork(
        trackId: String,
        url: String,
    ): String? = withContext(Dispatchers.IO) {
        val file = File(appContext.filesDir, "artwork/${sanitizeFileName(trackId)}.jpg")
        if (file.exists() && file.length() > 0L) return@withContext file.toURI().toString()
        runCatching {
            file.parentFile?.mkdirs()
            val builder = Request.Builder().url(url)
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                } ?: return@runCatching null
            }
            file.toURI().toString()
        }.getOrNull()
    }

    /** "03:58" -> 238000L */
    private fun parseDurationMs(duration: String): Long {
        val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.size != 2) return 0L
        return (parts[0] * 60 + parts[1]) * 1000
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifBlank { "unknown" }

    private data class MatchTarget(
        val songId: String,
        val provider: String,
        val extraJson: String,
        val cover: String?,
    )
}
