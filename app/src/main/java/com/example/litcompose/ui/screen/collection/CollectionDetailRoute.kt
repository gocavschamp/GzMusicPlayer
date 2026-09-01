package com.example.litcompose.ui.screen.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun CollectionDetailRoute(
    collectionId: Long,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val scope = rememberCoroutineScope()
    val viewModel: CollectionDetailViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        CollectionDetailViewModel::class.java ->
                            CollectionDetailViewModel(
                                collectionId = collectionId,
                                repository = app.appContainer.musicRepository,
                                eventBus = app.appContainer.eventBus,
                            )

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    CollectionDetailScreen(
        viewModel = viewModel,
        onBack = onBack,
        onPlay = { track, index, tracks, title ->
            scope.launch {
                val resolved = app.appContainer.musicRepository.resolveRemoteTracks(tracks)
                val newIndex = resolved.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                app.appContainer.playerController.playQueue(
                    tracks = resolved,
                    startIndex = newIndex,
                    queueTitle = title.ifBlank { "歌单" },
                )
                onOpenNowPlaying()
            }
        },
        onPlayAll = { tracks, title ->
            scope.launch {
                val resolved = app.appContainer.musicRepository.resolveRemoteTracks(tracks)
                app.appContainer.playerController.playQueue(
                    tracks = resolved,
                    startIndex = 0,
                    queueTitle = title.ifBlank { "歌单" },
                )
                onOpenNowPlaying()
            }
        },
    )
}
