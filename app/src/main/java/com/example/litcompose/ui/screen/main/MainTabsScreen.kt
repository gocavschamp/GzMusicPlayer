package com.example.litcompose.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.litcompose.domain.player.PlayerState
import com.example.litcompose.ui.player.AppMiniPlayer
import com.example.litcompose.ui.screen.chat.ChatRoute
import com.example.litcompose.ui.screen.collection.CollectionsRoute
import com.example.litcompose.ui.screen.search.SearchRoute
import com.example.litcompose.ui.screen.web.WebBrowserScreen

private data class TabItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainTabsScreen(
    playerState: PlayerState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()

    val tabs =
        listOf(
            TabItem("主页", Icons.Default.MusicNote),
            TabItem("搜索", Icons.Default.Search),
            TabItem("AI 问答", Icons.Default.SmartToy),
            TabItem("网页", Icons.Default.Language),
        )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            // 用 SaveableStateHolder 保持各 tab 的 rememberSaveable 状态（如搜索词、网页地址）
            tabStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    0 ->
                        CollectionsRoute(
                            onBack = null,
                            onOpenScan = onOpenScan,
                            onOpenCollection = onOpenCollection,
                        )

                    1 ->
                        SearchRoute(
                            onBack = null,
                            onOpenNowPlaying = onOpenNowPlaying,
                        )

                    2 -> ChatRoute()

                    else -> WebBrowserScreen()
                }
            }
        }

        // 网页 / AI 问答 tab 不展示播放条，避免遮挡页面底部内容
        if (playerState.current != null && selectedTab != 2 && selectedTab != 3) {
            AppMiniPlayer(
                state = playerState,
                onOpenNowPlaying = onOpenNowPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
            )
        }

        NavigationBar(modifier = Modifier.height(64.dp)) {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                )
            }
        }
    }
}
