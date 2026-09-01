package com.example.litcompose.data.repository

import android.util.Log
import com.example.litcompose.data.db.CollectionDao
import com.example.litcompose.data.db.CollectionEntity
import com.example.litcompose.data.db.CollectionTrackCrossRef
import com.example.litcompose.data.db.FavoriteTrackDao
import com.example.litcompose.data.db.FavoriteTrackEntity
import com.example.litcompose.data.db.TrackDao
import com.example.litcompose.data.db.TrackEntity
import com.example.litcompose.data.local.MediaStoreDataSource
import com.example.litcompose.data.local.TrackCache
import com.example.litcompose.data.remote.CoCoApi
import com.example.litcompose.data.remote.ItunesApi
import com.example.litcompose.domain.model.LyricLine
import com.example.litcompose.domain.model.RemoteTrackMeta
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.repository.CollectionSummary
import com.example.litcompose.domain.repository.MusicRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultMusicRepository(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val itunesApi: ItunesApi,
    private val cocoApi: CoCoApi,
    private val trackCache: TrackCache,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val trackDao: TrackDao,
    private val collectionDao: CollectionDao,
) : MusicRepository {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val extraMapType =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

    private companion object {
        const val TAG = "LitComposeRepo"
    }

    /** 解析成功后后台缓存下载，不阻塞播放 */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val favoriteTrackIds: Flow<Set<String>> =
        favoriteTrackDao.observeAllIds().map { it.toSet() }

    override suspend fun getLocalTracks(): List<Track> {
        return mediaStoreDataSource.getLocalTracks()
    }

    override suspend fun searchRemoteTracks(query: String): List<Track> {
        val response = itunesApi.searchSongs(term = query)
        return response.results.mapNotNull { dto ->
            val id = dto.trackId ?: return@mapNotNull null
            val title = dto.trackName ?: return@mapNotNull null
            val artist = dto.artistName.orEmpty()
            val url = dto.previewUrl ?: return@mapNotNull null
            val durationMs = dto.trackTimeMillis ?: 0L
            Track(
                id = "remote:$id",
                title = title,
                artist = artist,
                durationMs = durationMs,
                sourceUri = url,
                artworkUrl = dto.artworkUrl100,
                isLocal = false,
            )
        }
    }

    override suspend fun searchCoCo(query: String, provider: String): List<Track> {
        val response =
            runCatching { cocoApi.search(query = query, provider = provider) }
                .onFailure { Log.e(TAG, "coco search failed: query=$query provider=$provider", it) }
                .getOrElse { return emptyList() }
        val tracks =
            response.items.mapNotNull { dto ->
                val title = dto.title.ifBlank { return@mapNotNull null }
                val id = dto.id.ifBlank { return@mapNotNull null }
                val p = dto.provider.ifBlank { return@mapNotNull null }
                Track(
                    id = "coco:$p:$id",
                    title = title,
                    artist = dto.artist?.takeIf { it.isNotBlank() } ?: "未知歌手",
                    durationMs = parseDurationMs(dto.duration.orEmpty()),
                    sourceUri = "",
                    artworkUrl = dto.cover,
                    isLocal = false,
                    remote =
                        RemoteTrackMeta(
                            provider = p,
                            songId = id,
                            extraJson = dto.extra?.let { moshi.adapter<Map<String, Any?>>(extraMapType).toJson(it) }.orEmpty(),
                        ),
                )
            }
        Log.d(TAG, "coco search done: query=$query provider=$provider items=${tracks.size}")
        return tracks
    }

    override suspend fun resolveRemoteTracks(tracks: List<Track>): List<Track> = coroutineScope {
        tracks.map { track ->
            async {
                val meta = track.remote ?: return@async track
                if (track.sourceUri.isNotBlank()) return@async track
                // 本地缓存优先：命中则直接播放本地文件
                trackCache.get(track.id)?.let { cached ->
                    return@async track.copy(sourceUri = cached)
                }
                val url =
                    runCatching {
                        cocoApi.resolveUrl(id = meta.songId, provider = meta.provider, extra = meta.extraJson).url
                    }.onFailure { Log.w(TAG, "resolve url failed: id=${meta.songId} provider=${meta.provider}", it) }
                        .getOrNull()
                if (url.isNullOrBlank()) {
                    null
                } else {
                    // 解析成功后在后台缓存到本地，下次播放直接走本地
                    cacheScope.launch { trackCache.cache(track, url) }
                    track.copy(sourceUri = url)
                }
            }
        }.mapNotNull { it.await() }
    }

    override suspend fun resolveTrackForRetry(track: Track): Track? {
        val meta = track.remote ?: return null
        // 本地缓存优先：命中则直接播放本地文件
        trackCache.get(track.id)?.let { cached ->
            return track.copy(sourceUri = cached)
        }
        val url =
            runCatching {
                cocoApi.resolveUrl(id = meta.songId, provider = meta.provider, extra = meta.extraJson).url
            }.onFailure { Log.w(TAG, "retry resolve url failed: id=${meta.songId} provider=${meta.provider}", it) }
                .getOrNull()
        if (url.isNullOrBlank()) return null
        cacheScope.launch { trackCache.cache(track, url) }
        return track.copy(sourceUri = url)
    }

    override suspend fun cacheTrack(track: Track) {
        if (track.remote == null) return
        val url = track.sourceUri.takeIf { it.isNotBlank() && !it.startsWith("file://") } ?: return
        runCatching { trackCache.cache(track, url) }
            .onSuccess { cached ->
                if (cached != null) {
                    Log.d(TAG, "track cached: ${track.id}")
                }
            }
            .onFailure { Log.w(TAG, "cache track failed: id=${track.id}", it) }
    }

    override suspend fun fetchLyrics(track: Track): List<LyricLine> {
        val meta = track.remote ?: return emptyList()
        val resp =
            runCatching {
                cocoApi.lyric(id = meta.songId, provider = meta.provider, extra = meta.extraJson)
            }.onFailure { Log.w(TAG, "fetch lyric failed: id=${meta.songId} provider=${meta.provider}", it) }
                .getOrNull() ?: return emptyList()
        return resp.lines
            .filter { it.text.isNotBlank() }
            .map { LyricLine(timeMs = (it.time * 1000).toLong(), text = it.text.trim()) }
    }

    override suspend fun toggleFavorite(trackId: String) {
        val exists = favoriteTrackDao.exists(trackId)
        if (exists) {
            favoriteTrackDao.deleteById(trackId)
        } else {
            favoriteTrackDao.insert(FavoriteTrackEntity(trackId = trackId))
        }
    }

    override fun observeCollections(): Flow<List<CollectionSummary>> {
        return collectionDao.observeCollectionsWithCount().map { list ->
            list.map { item ->
                CollectionSummary(
                    id = item.collectionId,
                    name = item.name,
                    trackCount = item.trackCount,
                )
            }
        }
    }

    override suspend fun createCollection(name: String): Long {
        return collectionDao.insert(
            CollectionEntity(
                name = name,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeTracksInCollection(collectionId: Long): Flow<List<Track>> {
        return collectionDao.observeTracksInCollection(collectionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addTrackToCollection(collectionId: Long, track: Track) {
        trackDao.upsert(track.toEntity())
        collectionDao.upsertCrossRef(
            CollectionTrackCrossRef(
                collectionId = collectionId,
                trackId = track.id,
                addedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removeTrackFromCollection(collectionId: Long, trackId: String) {
        collectionDao.removeTrack(collectionId = collectionId, trackId = trackId)
    }

    override suspend fun saveCollectionTrackOrder(collectionId: Long, orderedTrackIds: List<String>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            collectionDao.updateTrackPosition(
                collectionId = collectionId,
                trackId = trackId,
                position = index,
            )
        }
    }

    override suspend fun getAllCollectionTracks(): List<Track> {
        return collectionDao.getAllTracks().map { it.toDomain() }
    }
}

/** "03:58" -> 238000L */
private fun parseDurationMs(duration: String): Long {
    val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
    if (parts.size != 2) return 0L
    return (parts[0] * 60 + parts[1]) * 1000
}

private fun TrackEntity.toDomain(): Track {
    return Track(
        id = trackId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        sourceUri = sourceUri,
        artworkUrl = artworkUrl,
        isLocal = isLocal,
        remote = remote,
    )
}

private fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        trackId = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        sourceUri = sourceUri,
        artworkUrl = artworkUrl,
        isLocal = isLocal,
        remote = remote,
    )
}
