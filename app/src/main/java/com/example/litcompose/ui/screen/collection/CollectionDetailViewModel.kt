package com.example.litcompose.ui.screen.collection

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

data class CollectionDetailUiState(
    val collectionId: Long,
    val title: String = "",
    val tracks: List<Track> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
)

class CollectionDetailViewModel(
    private val collectionId: Long,
    private val repository: MusicRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectionDetailUiState(collectionId = collectionId))
    val state: StateFlow<CollectionDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCollections().collect { list ->
                val target = list.firstOrNull { it.id == collectionId }
                if (target != null) {
                    mutableState.update { it.copy(title = target.name) }
                }
                mutableState.update { it.copy(collections = list) }
            }
        }
        viewModelScope.launch {
            repository.observeTracksInCollection(collectionId).collect { tracks ->
                mutableState.update { it.copy(tracks = tracks) }
            }
        }
        viewModelScope.launch {
            repository.favoriteTrackIds.collect { ids ->
                mutableState.update { it.copy(favoriteTrackIds = ids) }
            }
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            runCatching { repository.createCollection(name) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已创建歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "创建失败")) }
        }
    }

    /** 把歌曲添加到其他歌单 */
    fun addToCollection(collectionId: Long, track: Track) {
        viewModelScope.launch {
            runCatching { repository.addTrackToCollection(collectionId, track) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已添加到歌单")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "添加失败")) }
        }
    }

    /** 从当前歌单移除歌曲 */
    fun removeFromCollection(trackId: String) {
        viewModelScope.launch {
            runCatching { repository.removeTrackFromCollection(collectionId, trackId) }
                .onSuccess { eventBus.tryEmit(AppEvent.ShowSnackbar("已从歌单移除")) }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "移除失败")) }
        }
    }

    /** 收藏 / 取消收藏 */
    fun toggleFavorite(trackId: String) {
        val wasFavorite = trackId in mutableState.value.favoriteTrackIds
        viewModelScope.launch {
            runCatching { repository.toggleFavorite(trackId) }
                .onSuccess {
                    eventBus.tryEmit(
                        AppEvent.ShowSnackbar(if (wasFavorite) "已取消收藏" else "已加入我的收藏"),
                    )
                }
                .onFailure { eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "操作失败")) }
        }
    }

    /** 手动排序：把 from 位置的歌曲移动到 to 位置，并持久化 */
    fun moveTrack(from: Int, to: Int) {
        val list = mutableState.value.tracks.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        persistOrder(list)
    }

    /** 正序 / 倒序切换：翻转当前顺序并持久化 */
    fun reverseOrder() {
        persistOrder(mutableState.value.tracks.reversed())
    }

    private fun persistOrder(ordered: List<Track>) {
        mutableState.update { it.copy(tracks = ordered) }
        viewModelScope.launch {
            runCatching {
                repository.saveCollectionTrackOrder(collectionId, ordered.map(Track::id))
            }.onFailure {
                eventBus.tryEmit(AppEvent.ShowSnackbar(it.message ?: "保存排序失败"))
            }
        }
    }
}
