package com.example.litcompose.data.local

import android.content.Context
import com.example.litcompose.domain.model.Track
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * 持久化"上次播放"状态：进入 App 后音乐条默认显示上次播放的歌曲。
 */
class LastPlaybackStore(context: Context) {

    private val prefs = context.getSharedPreferences("last_playback", Context.MODE_PRIVATE)
    private val trackListType = Types.newParameterizedType(List::class.java, Track::class.java)
    private val trackListAdapter =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter<List<Track>>(trackListType)

    data class SavedState(
        val tracks: List<Track>,
        val currentIndex: Int,
        val positionMs: Long,
        val queueTitle: String,
    )

    fun save(state: SavedState) {
        prefs.edit()
            .putString("tracks", trackListAdapter.toJson(state.tracks))
            .putInt("index", state.currentIndex)
            .putLong("position", state.positionMs)
            .putString("title", state.queueTitle)
            .apply()
    }

    fun load(): SavedState? {
        val tracksJson = prefs.getString("tracks", null) ?: return null
        val tracks = runCatching { trackListAdapter.fromJson(tracksJson) }.getOrNull() ?: return null
        if (tracks.isEmpty()) return null
        val safeIndex = prefs.getInt("index", 0).coerceIn(0, tracks.lastIndex)
        return SavedState(
            tracks = tracks,
            currentIndex = safeIndex,
            positionMs = prefs.getLong("position", 0L).coerceAtLeast(0L),
            queueTitle = prefs.getString("title", "") ?: "",
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
