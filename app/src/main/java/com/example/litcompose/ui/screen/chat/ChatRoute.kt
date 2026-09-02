package com.example.litcompose.ui.screen.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litcompose.LitComposeApp
import com.example.litcompose.core.ViewModelFactory

@Composable
fun ChatRoute() {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LitComposeApp }
    val viewModel: ChatViewModel =
        viewModel(
            factory =
                ViewModelFactory { modelClass, _ ->
                    when (modelClass) {
                        ChatViewModel::class.java ->
                            ChatViewModel(deepSeekApi = app.appContainer.deepSeekApi)

                        else -> error("Unknown ViewModel: $modelClass")
                    }
                },
        )

    ChatScreen(viewModel = viewModel)
}
