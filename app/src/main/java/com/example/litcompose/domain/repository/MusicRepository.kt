package com.example.litcompose.domain.repository

import com.example.litcompose.domain.model.LyricLine
import com.example.litcompose.domain.model.Track
import kotlinx.coroutines.flow.Flow

data class CollectionSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
)

interface MusicRepository {
    val favoriteTrackIds: Flow<Set<String>>

    suspend fun getLocalTracks(): List<Track>

    suspend fun searchRemoteTracks(query: String): List<Track>

    /** 通过 COCO 渠道搜索（可指定搜索引擎） */
    suspend fun searchCoCo(query: String, provider: String): List<Track>

    /** 批量解析远程歌曲的真实播放链接，失败的歌曲会被过滤掉 */
    suspend fun resolveRemoteTracks(tracks: List<Track>): List<Track>

    /** 播放失败重试时强制重新解析（忽略旧链接，本地缓存优先），失败返回 null */
    suspend fun resolveTrackForRetry(track: Track): Track?

    /** 把已解析的远程歌曲缓存到本地（幂等，已有缓存直接跳过），下次播放本地优先 */
    suspend fun cacheTrack(track: Track)

    /** 获取远程歌曲歌词（空列表表示无歌词或本地歌曲） */
    suspend fun fetchLyrics(track: Track): List<LyricLine>

    suspend fun toggleFavorite(trackId: String)

    fun observeCollections(): Flow<List<CollectionSummary>>

    suspend fun createCollection(name: String): Long

    fun observeTracksInCollection(collectionId: Long): Flow<List<Track>>

    suspend fun addTrackToCollection(collectionId: Long, track: Track)

    /** 从指定歌单中移除歌曲 */
    suspend fun removeTrackFromCollection(collectionId: Long, trackId: String)

    /** 保存歌单内歌曲的手动排序顺序（index 即 position） */
    suspend fun saveCollectionTrackOrder(collectionId: Long, orderedTrackIds: List<String>)

    /** 所有歌单中的全部歌曲（跨歌单按 trackId 去重） */
    suspend fun getAllCollectionTracks(): List<Track>
}
