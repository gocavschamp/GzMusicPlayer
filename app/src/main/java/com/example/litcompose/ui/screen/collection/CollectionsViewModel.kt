package com.example.litcompose.ui.screen.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.core.AppEvent
import com.example.litcompose.core.AppEventBus
import com.example.litcompose.data.local.BatchProgress
import com.example.litcompose.data.local.LyricsEnricher
import com.example.litcompose.domain.repository.CollectionSummary
import com.example.litcompose.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val collections: List<CollectionSummary> = emptyList(),
    /** 批量获取歌词/封面是否进行中 */
    val enriching: Boolean = false,
    /** 批量进度（null 表示尚未开始） */
    val batchProgress: BatchProgress? = null,
)

class CollectionsViewModel(
    private val repository: MusicRepository,
    private val lyricsEnricher: LyricsEnricher,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCollections().collect { list ->
                mutableState.update { it.copy(collections = list) }
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

    /**
     * 一键获取所有歌单内歌曲的歌词和封面：遍历全部歌曲，后台逐首匹配下载并保存，
     * 通过 state.batchProgress 实时暴露进度。
     */
    fun startFetchAllLyrics() {
        if (mutableState.value.enriching) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(enriching = true, batchProgress = BatchProgress(0, 0, 0, 0))
            }
            val tracks = runCatching { repository.getAllCollectionTracks() }.getOrDefault(emptyList())
            if (tracks.isEmpty()) {
                mutableState.update { it.copy(enriching = false, batchProgress = null) }
                eventBus.tryEmit(AppEvent.ShowSnackbar("歌单里还没有歌曲"))
                return@launch
            }
            lyricsEnricher.enrichAll(tracks).collect { progress ->
                mutableState.update { it.copy(batchProgress = progress) }
            }
            val done = mutableState.value.batchProgress
            mutableState.update { it.copy(enriching = false, batchProgress = null) }
            eventBus.tryEmit(
                AppEvent.ShowSnackbar(
                    if (done == null || done.total == 0) {
                        "没有需要处理的歌曲"
                    } else {
                        "完成：${done.matched} 首成功，${done.failed} 首失败"
                    },
                ),
            )
        }
    }
}
