package com.example.litcompose.ui.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.core.AppEvent
import com.example.litcompose.core.AppEventBus
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.repository.CollectionSummary
import com.example.litcompose.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanLocalUiState(
    val isScanning: Boolean = false,
    val scanned: List<Track> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
    val isBatchMode: Boolean = false,
    val selectedTrackIds: Set<String> = emptySet(),
)

class ScanLocalViewModel(
    private val repository: MusicRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScanLocalUiState())
    val state: StateFlow<ScanLocalUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCollections().collect { list ->
                mutableState.update { it.copy(collections = list) }
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            mutableState.update { it.copy(isScanning = true) }
            runCatching { repository.getLocalTracks() }
                .onSuccess { tracks ->
                    mutableState.update {
                        it.copy(
                            isScanning = false,
                            scanned = tracks,
                            selectedTrackIds = it.selectedTrackIds.intersect(tracks.map(Track::id).toSet()),
                        )
                    }
                    eventBus.tryEmit(AppEvent.ShowSnackbar("扫描到 ${tracks.size} 首歌曲"))
                }
                .onFailure {
                    mutableState.update { it.copy(isScanning = false) }
                    eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "扫描失败"))
                }
        }
    }

    fun onPermissionDenied() {
        eventBus.tryEmit(AppEvent.ShowSnackbar("未授权读取音乐文件"))
    }

    fun toggleBatchMode() {
        mutableState.update { state ->
            state.copy(
                isBatchMode = !state.isBatchMode,
                selectedTrackIds = if (state.isBatchMode) emptySet() else state.selectedTrackIds,
            )
        }
    }

    fun toggleTrackSelection(trackId: String) {
        mutableState.update { state ->
            val next =
                if (state.selectedTrackIds.contains(trackId)) {
                    state.selectedTrackIds - trackId
                } else {
                    state.selectedTrackIds + trackId
                }
            state.copy(selectedTrackIds = next)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selectedTrackIds = emptySet()) }
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

    fun addSelectedToCollection(collectionId: Long) {
        val tracks =
            state.value.scanned.filter { track ->
                state.value.selectedTrackIds.contains(track.id)
            }
        if (tracks.isEmpty()) {
            eventBus.tryEmit(AppEvent.ShowSnackbar("请先选择歌曲"))
            return
        }
        viewModelScope.launch {
            runCatching {
                tracks.forEach { repository.addTrackToCollection(collectionId, it) }
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        isBatchMode = false,
                        selectedTrackIds = emptySet(),
                    )
                }
                eventBus.tryEmit(AppEvent.ShowSnackbar("已添加 ${tracks.size} 首到歌单"))
            }.onFailure {
                eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "批量添加失败"))
            }
        }
    }
}
