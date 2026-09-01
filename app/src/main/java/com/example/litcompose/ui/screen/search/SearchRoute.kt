package com.example.litcompose.ui.screen.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory

@Composable
fun SearchRoute(
    onBack: (() -> Unit)? = null,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val viewModel: SearchViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        SearchViewModel::class.java ->
                            SearchViewModel(
                                repository = app.appContainer.musicRepository,
                                trackDownloader = app.appContainer.trackDownloader,
                                eventBus = app.appContainer.eventBus,
                            )

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    val playRequest by viewModel.playRequest.collectAsState()
    LaunchedEffect(playRequest) {
        playRequest?.let { req ->
            app.appContainer.playerController.playQueue(
                tracks = req.tracks,
                startIndex = req.startIndex.coerceAtLeast(0),
                queueTitle = "网络搜索",
            )
            viewModel.consumePlayRequest()
            onOpenNowPlaying()
        }
    }

    SearchScreen(
        viewModel = viewModel,
        onBack = onBack,
    )
}
