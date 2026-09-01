package com.example.litcompose.ui.screen.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory

@Composable
fun ScanLocalRoute(
    onBack: (() -> Unit)? = null,
    onOpenCollections: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val viewModel: ScanLocalViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        ScanLocalViewModel::class.java ->
                            ScanLocalViewModel(
                                repository = app.appContainer.musicRepository,
                                eventBus = app.appContainer.eventBus,
                            )

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    ScanLocalScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenCollections = onOpenCollections,
        onPlayQueue = { track, index, tracks ->
            app.appContainer.playerController.playQueue(
                tracks = tracks,
                startIndex = index.coerceAtLeast(0),
                queueTitle = "本地扫描",
            )
            onOpenNowPlaying()
        },
    )
}
