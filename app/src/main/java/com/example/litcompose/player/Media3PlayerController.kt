package com.example.litcompose.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.litcompose.data.local.LastPlaybackStore
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.player.PlayMode
import com.example.litcompose.domain.player.PlayerController
import com.example.litcompose.domain.player.PlayerState
import com.example.litcompose.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "PlayerCtrl"

@OptIn(UnstableApi::class)
class Media3PlayerController(
    context: Context,
    private val lastPlaybackStore: LastPlaybackStore,
    private val repository: MusicRepository,
) : PlayerController {
    /** 频谱分析器：挂在音频处理链上，供 UI 波形动效实时读取高低音数据 */
    private val spectrumProcessor = SpectrumAudioProcessor()

    // media3 1.4.1 无 DefaultRenderersFactory.setAudioSink，通过覆盖 buildAudioSink 注入自定义处理链
    private val renderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(spectrumProcessor))
                    .build()
        }

    /** 全局唯一的播放器实例，通知栏服务（MusicPlaybackService）复用同一个实例 */
    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var lastSavedPositionMs = -1L

    /** 远程链接过期/防盗链触发 403 时自动重解析，限制连续重试次数避免死循环 */
    private var retrying = false
    private var retryAttempts = 0

    /** 正在后台缓存的曲目 id，避免同一首歌并发重复下载 */
    private val cachingTrackIds = mutableSetOf<String>()

    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState

    /** 实时频谱（对数频率分桶 0..1），驱动播放页波形动效；未播放时为全 0 */
    override val spectrum: StateFlow<FloatArray> = spectrumProcessor.spectrum

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.update { it.copy(isPlaying = isPlaying) }
                ensureProgressUpdates()
                syncProgress()
                if (isPlaying) {
                    // 任何路径进入播放状态都确保通知栏服务已启动（幂等）
                    ensurePlaybackServiceRunning()
                } else {
                    saveCurrentState()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncQueueState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncProgress()
                syncQueueState()
                if (playbackState == Player.STATE_READY) {
                    retryAttempts = 0
                    cacheCurrentTrack()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                retryCurrentWithFreshUrl(error)
            }
        }

    init {
        player.addListener(listener)
        // 耳机断开（蓝牙/有线）时自动暂停播放，避免音频外放
        player.setHandleAudioBecomingNoisy(true)
        // 默认列表循环
        player.repeatMode = Player.REPEAT_MODE_ALL
        restoreLastPlayback()
        ensureProgressUpdates()
    }

    override fun play(track: Track) {
        playQueue(
            tracks = listOf(track),
            startIndex = 0,
            queueTitle = "单曲播放",
        )
    }

    override fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
        queueTitle: String,
    ) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        player.setMediaItems(
            tracks.map(::trackToMediaItem),
            safeIndex,
            0L,
        )
        player.prepare()
        player.playWhenReady = true
        ensurePlaybackServiceRunning()
        mutableState.update {
            it.copy(
                current = tracks[safeIndex],
                durationMs = tracks[safeIndex].durationMs.coerceAtLeast(0L),
                queueTitle = queueTitle,
                currentIndex = safeIndex,
                queueSize = tracks.size,
                queue = tracks,
                positionMs = 0L,
            )
        }
        saveCurrentState()
        ensureProgressUpdates()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            // 出错后播放器处于 IDLE，需要先 prepare 才能恢复
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        syncProgress()
    }

    override fun skipTo(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.playWhenReady = true
        }
    }

    /**
     * 上一曲：手动取模实现列表循环。hasPreviousMediaItem 在列表开头返回 false，
     * 若直接依赖它，列表循环模式下无法从头跳到末尾。
     */
    override fun skipToPrevious() {
        val count = player.mediaItemCount
        if (count > 0) {
            player.seekTo((player.currentMediaItemIndex - 1 + count) % count, 0L)
            player.playWhenReady = true
        }
    }

    /**
     * 下一曲：手动取模实现列表循环。hasNextMediaItem 在列表末尾返回 false，
     * 若直接依赖它，播放到最后一首再点下一曲不会回到列表开头。
     */
    override fun skipToNext() {
        val count = player.mediaItemCount
        if (count > 0) {
            player.seekTo((player.currentMediaItemIndex + 1) % count, 0L)
            player.playWhenReady = true
        }
    }

    override fun setPlayMode(mode: PlayMode) {
        when (mode) {
            PlayMode.LIST_LOOP -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ALL
            }

            PlayMode.SINGLE_LOOP -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }

            PlayMode.SHUFFLE -> {
                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        mutableState.update { it.copy(playMode = mode) }
    }

    /**
     * 从队列移除歌曲：移除正在播放的曲目时 ExoPlayer 自动顺延到下一首；
     * 队列清空时重置播放状态。
     */
    override fun removeFromQueue(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        syncQueueState()
        if (player.mediaItemCount == 0) {
            mutableState.update {
                it.copy(
                    current = null,
                    currentIndex = -1,
                    queueSize = 0,
                    queue = emptyList(),
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlaying = false,
                )
            }
        }
        saveCurrentState()
    }

    override fun release() {
        saveCurrentState()
        progressJob?.cancel()
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun restoreLastPlayback() {
        val saved = lastPlaybackStore.load() ?: return
        if (saved.tracks.isEmpty()) return
        val safeIndex = saved.currentIndex.coerceIn(0, saved.tracks.lastIndex)
        player.setMediaItems(
            saved.tracks.map(::trackToMediaItem),
            safeIndex,
            saved.positionMs,
        )
        player.prepare()
        player.playWhenReady = false
        mutableState.update {
            it.copy(
                current = saved.tracks[safeIndex],
                durationMs = saved.tracks[safeIndex].durationMs.coerceAtLeast(0L),
                queueTitle = saved.queueTitle,
                currentIndex = safeIndex,
                queueSize = saved.tracks.size,
                queue = saved.tracks,
                positionMs = saved.positionMs,
                isPlaying = false,
            )
        }
    }

    private fun ensureProgressUpdates() {
        if (progressJob != null) return
        progressJob =
            scope.launch {
                while (true) {
                    syncProgress()
                    val s = mutableState.value
                    if (player.isPlaying && s.positionMs - lastSavedPositionMs >= 3_000L) {
                        saveCurrentState()
                    }
                    // 播放中 250ms 一次：进度条/歌词高亮延迟更小
                    delay(if (player.isPlaying) 250L else 1000L)
                }
            }
    }

    private fun saveCurrentState() {
        val tracks = currentQueueTracks()
        if (tracks.isEmpty()) return
        val index = player.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
        val position = player.currentPosition.coerceAtLeast(0L)
        lastPlaybackStore.save(
            LastPlaybackStore.SavedState(
                tracks = tracks,
                currentIndex = index,
                positionMs = position,
                queueTitle = mutableState.value.queueTitle,
            ),
        )
        lastSavedPositionMs = position
    }

    private fun syncProgress() {
        val duration = player.duration.takeIf { it >= 0L } ?: mutableState.value.durationMs
        val position = player.currentPosition.takeIf { it >= 0L } ?: 0L
        mutableState.update {
            it.copy(
                positionMs = position,
                durationMs = duration.coerceAtLeast(0L),
            )
        }
    }

    private fun syncQueueState() {
        val index = player.currentMediaItemIndex
        val track = player.currentMediaItem?.localConfiguration?.tag as? Track
        val queue = currentQueueTracks()
        mutableState.update {
            it.copy(
                current = track ?: it.current,
                currentIndex = if (index >= 0) index else it.currentIndex,
                queueSize = queue.size,
                queue = queue,
            )
        }
    }

    private fun currentQueueTracks(): List<Track> {
        return (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).localConfiguration?.tag as? Track
        }
    }

    private fun trackToMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setUri(track.sourceUri)
            .setTag(track)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist.ifBlank { "未知歌手" })
                    .setArtworkUri(track.artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
    }

    private fun ensurePlaybackServiceRunning() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, MusicPlaybackService::class.java),
        )
    }

    /**
     * 播放成功后将当前远程歌曲缓存到本地（幂等），下次播放命中缓存直接走本地，
     * 无需再次解析。本地/正在缓存的歌曲跳过。
     */
    private fun cacheCurrentTrack() {
        val track = player.currentMediaItem?.localConfiguration?.tag as? Track ?: return
        if (track.remote == null) return
        if (track.sourceUri.isBlank() || track.sourceUri.startsWith("file://")) return
        if (!cachingTrackIds.add(track.id)) return
        scope.launch {
            try {
                repository.cacheTrack(track)
            } finally {
                cachingTrackIds.remove(track.id)
            }
        }
    }

    /**
     * 远程歌曲链接过期（如 403 防盗链）时，强制重新解析并替换当前媒体项续播。
     * 本地歌曲 / 非 Source 类型错误不处理。
     */
    private fun retryCurrentWithFreshUrl(error: PlaybackException) {
        val exoError = error as? ExoPlaybackException ?: return
        if (exoError.type != ExoPlaybackException.TYPE_SOURCE) return
        if (retrying || retryAttempts >= 2) return
        val track = player.currentMediaItem?.localConfiguration?.tag as? Track ?: return
        if (track.remote == null || track.sourceUri.startsWith("file://")) return
        retrying = true
        retryAttempts++
        scope.launch {
            try {
                val fresh = repository.resolveTrackForRetry(track)
                if (fresh != null && fresh.sourceUri != track.sourceUri) {
                    retryAttempts = 0
                    val positionMs = player.currentPosition.coerceAtLeast(0L)
                    player.replaceMediaItem(player.currentMediaItemIndex, trackToMediaItem(fresh))
                    player.seekTo(positionMs)
                    player.prepare()
                    player.playWhenReady = true
                    mutableState.update { it.copy(current = fresh) }
                }
            } finally {
                retrying = false
            }
        }
    }
}
