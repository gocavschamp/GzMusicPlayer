package com.example.litcompose.ui.screen.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val viewModel: NowPlayingViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        NowPlayingViewModel::class.java ->
                            NowPlayingViewModel(
                                playerController = app.appContainer.playerController,
                                repository = app.appContainer.musicRepository,
                                trackDownloader = app.appContainer.trackDownloader,
                                lyricsEnricher = app.appContainer.lyricsEnricher,
                                eventBus = app.appContainer.eventBus,
                            )

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    NowPlayingScreen(viewModel = viewModel, onBack = onBack)
}

