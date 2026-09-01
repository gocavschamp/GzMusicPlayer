package com.example.litcompose.ui.screen.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory

@Composable
fun CollectionsRoute(
    onBack: (() -> Unit)? = null,
    onOpenCollection: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val viewModel: CollectionsViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        CollectionsViewModel::class.java ->
                            CollectionsViewModel(
                                repository = app.appContainer.musicRepository,
                                lyricsEnricher = app.appContainer.lyricsEnricher,
                                eventBus = app.appContainer.eventBus,
                            )

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    CollectionsScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenCollection = onOpenCollection,
    )
}

