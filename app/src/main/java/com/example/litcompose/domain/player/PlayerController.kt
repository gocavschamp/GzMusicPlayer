package com.example.litcompose.domain.player

import com.example.litcompose.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/** 播放模式：列表循环 / 单曲循环 / 随机 */
enum class PlayMode {
    LIST_LOOP,
    SINGLE_LOOP,
    SHUFFLE,
}

data class PlayerState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueTitle: String = "",
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val queue: List<Track> = emptyList(),
    val playMode: PlayMode = PlayMode.LIST_LOOP,
)

interface PlayerController {
    val state: StateFlow<PlayerState>

    /** 实时频谱（对数频率分桶 0..1），驱动播放页波形动效；未播放时为全 0 */
    val spectrum: StateFlow<FloatArray>

    fun play(track: Track)

    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        queueTitle: String = "",
    )

    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    fun skipTo(index: Int)

    fun skipToPrevious()

    fun skipToNext()

    /** 从当前播放队列中移除指定位置的歌曲（可移除正在播放的曲目，播放自动顺延） */
    fun removeFromQueue(index: Int)

    /** 切换播放模式：列表循环 / 单曲循环 / 随机 */
    fun setPlayMode(mode: PlayMode)

    fun release()
}
