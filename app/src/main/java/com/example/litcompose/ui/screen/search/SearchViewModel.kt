package com.example.litcompose.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.core.AppEvent
import com.example.litcompose.core.AppEventBus
import com.example.litcompose.data.local.TrackDownloader
import com.example.litcompose.data.remote.CoCoProviders
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.repository.CollectionSummary
import com.example.litcompose.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedProvider: String = CoCoProviders.all.first().id,
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isPreparingPlay: Boolean = false,
    val collections: List<CollectionSummary> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
)

/** 解析完成待播放的请求 */
data class PlayRequest(
    val tracks: List<Track>,
    val startIndex: Int,
)

class SearchViewModel(
    private val repository: MusicRepository,
    private val trackDownloader: TrackDownloader,
    private val eventBus: AppEventBus,
) : ViewModel() {
    val providers: List<CoCoProviders.Provider> = CoCoProviders.all

    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val mutablePlayRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = mutablePlayRequest.asStateFlow()

    private var currentJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCollections().collect { list ->
                mutableState.update { it.copy(collections = list) }
            }
        }
    }

    fun updateQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    /** 一键清空搜索框内容 */
    fun clearQuery() {
        mutableState.update { it.copy(query = "") }
    }

    fun selectProvider(providerId: String) {
        mutableState.update { it.copy(selectedProvider = providerId) }
        search()
    }

    fun search() {
        val q = state.value.query.trim()
        if (q.isEmpty()) return
        val provider = state.value.selectedProvider
        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                mutableState.update { it.copy(isSearching = true) }
                runCatching { repository.searchCoCo(q, provider) }
                    .onSuccess { results ->
                        mutableState.update { it.copy(results = results, isSearching = false) }
                    }
                    .onFailure {
                        mutableState.update { it.copy(isSearching = false) }
                        eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "搜索失败"))
                    }
            }
    }

    /** 解析远程歌曲的播放链接后发起播放 */
    fun playTrack(track: Track, index: Int, tracks: List<Track>) {
        if (mutableState.value.isPreparingPlay) return
        viewModelScope.launch {
            mutableState.update { it.copy(isPreparingPlay = true) }
            val resolved = runCatching { repository.resolveRemoteTracks(tracks) }.getOrElse { emptyList() }
            mutableState.update { it.copy(isPreparingPlay = false) }
            if (resolved.isEmpty()) {
                eventBus.tryEmit(AppEvent.ShowSnackbar("解析失败，无法播放"))
                return@launch
            }
            val startIndex = resolved.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            mutablePlayRequest.value = PlayRequest(tracks = resolved, startIndex = startIndex)
        }
    }

    fun consumePlayRequest() {
        mutablePlayRequest.value = null
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            runCatching { repository.createCollection(name) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已创建歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "创建失败")) }
        }
    }

    fun addToCollection(collectionId: Long, track: Track) {
        viewModelScope.launch {
            runCatching { repository.addTrackToCollection(collectionId, track) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已添加到歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "添加失败")) }
        }
    }

    fun download(track: Track) {
        if (state.value.downloadingIds.contains(track.id)) return
        mutableState.update { it.copy(downloadingIds = it.downloadingIds + track.id) }
        viewModelScope.launch {
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
                .also {
                    mutableState.update { st -> st.copy(downloadingIds = st.downloadingIds - track.id) }
                }
        }
    }
}
