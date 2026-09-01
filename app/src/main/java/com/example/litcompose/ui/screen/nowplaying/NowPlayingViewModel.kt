package com.example.litcompose.ui.screen.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.core.AppEvent
import com.example.litcompose.core.AppEventBus
import com.example.litcompose.data.local.LyricsEnricher
import com.example.litcompose.data.local.TrackDownloader
import com.example.litcompose.domain.model.LyricLine
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.player.PlayMode
import com.example.litcompose.domain.player.PlayerController
import com.example.litcompose.domain.player.PlayerState
import com.example.litcompose.domain.repository.CollectionSummary
import com.example.litcompose.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val playerController: PlayerController,
    private val repository: MusicRepository,
    private val trackDownloader: TrackDownloader,
    private val lyricsEnricher: LyricsEnricher,
    private val eventBus: AppEventBus,
) : ViewModel() {
    val playerState: StateFlow<PlayerState> =
        playerController.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            playerController.state.value,
        )

    /** 实时频谱（对数频率分桶 0..1），驱动播放页波形动效 */
    val spectrum: StateFlow<FloatArray> = playerController.spectrum

    private val mutableLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = mutableLyrics.asStateFlow()

    /** 已保存到本地的封面 file:// URI（本地歌曲通常无 artworkUrl，补全后在此展示） */
    private val mutableArtworkPath = MutableStateFlow<String?>(null)
    val artworkPath: StateFlow<String?> = mutableArtworkPath.asStateFlow()

    private val mutableDownloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = mutableDownloading.asStateFlow()

    /** 手动"获取歌词"是否进行中 */
    private val mutableFetchingLyrics = MutableStateFlow(false)
    val fetchingLyrics: StateFlow<Boolean> = mutableFetchingLyrics.asStateFlow()

    /** 全部歌单（含收藏），供"添加到歌单"弹窗选择 */
    val collections: StateFlow<List<CollectionSummary>> =
        repository.observeCollections().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    init {
        // 切歌时重新加载歌词：优先读本地缓存（含本地歌曲补全的），无缓存再实时请求远程歌曲
        viewModelScope.launch {
            playerController.state
                .map { it.current?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    val track = playerController.state.value.current
                    mutableArtworkPath.value =
                        track?.let { runCatching { lyricsEnricher.cachedArtworkPath(it.id) }.getOrNull() }
                    if (track != null) {
                        loadLyrics(track)
                    } else {
                        mutableLyrics.value = emptyList()
                    }
                }
        }
    }

    private suspend fun loadLyrics(track: Track) {
        mutableLyrics.value = emptyList()
        // 已有本地缓存（含“已确认无歌词”的空结果）直接使用，不再请求网络
        val cached = runCatching { lyricsEnricher.cachedLyrics(track.id) }.getOrNull()
        if (cached != null) {
            mutableLyrics.value = cached
            return
        }
        if (track.remote != null) {
            val lines = runCatching { repository.fetchLyrics(track) }.getOrDefault(emptyList())
            mutableLyrics.value = lines
        }
    }

    /**
     * 手动获取当前歌曲的歌词和封面：走接口匹配（本地歌曲按歌名+歌手+时长识别），
     * 成功后保存本地并立即刷新歌词与封面展示。
     */
    fun fetchLyricsForCurrent() {
        val track = playerController.state.value.current ?: return
        if (mutableFetchingLyrics.value) return
        viewModelScope.launch {
            mutableFetchingLyrics.value = true
            runCatching { lyricsEnricher.enrich(track) }
                .onSuccess { result ->
                    if (result.matched) {
                        // 从缓存读取最新数据刷新 UI
                        mutableLyrics.value =
                            lyricsEnricher.cachedLyrics(track.id) ?: emptyList()
                        mutableArtworkPath.value = lyricsEnricher.cachedArtworkPath(track.id)
                        eventBus.tryEmit(
                            AppEvent.ShowSnackbar(
                                "已获取歌词（${result.lyricCount}行）" +
                                    if (result.artworkSaved) "和封面" else "",
                            ),
                        )
                    } else {
                        eventBus.tryEmit(AppEvent.ShowSnackbar(result.error ?: "未匹配到这首歌"))
                    }
                }
                .onFailure {
                    eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "获取歌词失败"))
                }
            mutableFetchingLyrics.value = false
        }
    }

    /** 下载当前播放的歌曲到本地音乐库 */
    fun downloadCurrent() {
        val track = playerController.state.value.current ?: return
        if (mutableDownloading.value) return
        viewModelScope.launch {
            mutableDownloading.value = true
            runCatching { trackDownloader.download(track) }
                .onSuccess { uri ->
                    eventBus.tryEmit(
                        AppEvent.ShowSnackbar(
                            if (uri != null) "已下载到本地，可在「扫描」页查看" else "下载失败：音频地址无效",
                        ),
                    )
                }
                .onFailure {
                    eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "下载失败"))
                }
            mutableDownloading.value = false
        }
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun skipToPrevious() {
        playerController.skipToPrevious()
    }

    fun skipToNext() {
        playerController.skipToNext()
    }

    fun skipTo(index: Int) {
        playerController.skipTo(index)
    }

    /** 从当前播放队列中移除指定位置歌曲 */
    fun removeFromQueue(index: Int) {
        playerController.removeFromQueue(index)
    }

    /** 新建歌单 */
    fun createCollection(name: String) {
        viewModelScope.launch {
            runCatching { repository.createCollection(name) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已创建歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "创建失败")) }
        }
    }

    /** 把指定歌曲添加到歌单 */
    fun addToCollection(collectionId: Long, track: Track) {
        viewModelScope.launch {
            runCatching { repository.addTrackToCollection(collectionId, track) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已添加到歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "添加失败")) }
        }
    }

    fun setPlayMode(mode: PlayMode) {
        playerController.setPlayMode(mode)
    }
}
