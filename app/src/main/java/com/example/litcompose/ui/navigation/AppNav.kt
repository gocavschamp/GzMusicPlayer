package com.example.litcompose.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.litcompose.LitComposeApp
import com.example.litcompose.ui.screen.main.MainTabsScreen
import com.example.litcompose.ui.screen.nowplaying.NowPlayingRoute
import com.example.litcompose.ui.screen.collection.CollectionDetailRoute
import com.example.litcompose.ui.screen.collection.CollectionsRoute
import com.example.litcompose.ui.player.AppMiniPlayer
import com.example.litcompose.ui.screen.scan.ScanLocalRoute

object Routes {
    const val Library = "library"
    const val NowPlaying = "now_playing"
    const val Collections = "collections"
    const val CollectionDetail = "collection"
    const val ScanLocal = "scan_local"
}

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController(),
) {
    val app = LocalContext.current.applicationContext as LitComposeApp
    val playerState by app.appContainer.playerController.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showMiniPlayer =
        playerState.current != null &&
            currentRoute != Routes.NowPlaying &&
            currentRoute != Routes.Library

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Library,
            modifier = Modifier.fillMaxSize().padding(bottom = if (showMiniPlayer) 88.dp else 0.dp),
        ) {
            composable(Routes.Library) {
                MainTabsScreen(
                    playerState = playerState,
                    onOpenNowPlaying = { navController.navigate(Routes.NowPlaying) },
                    onTogglePlayPause = { app.appContainer.playerController.togglePlayPause() },
                    onSkipPrevious = { app.appContainer.playerController.skipToPrevious() },
                    onSkipNext = { app.appContainer.playerController.skipToNext() },
                    onOpenScan = { navController.navigate(Routes.ScanLocal) },
                    onOpenCollection = { id -> navController.navigate("${Routes.CollectionDetail}/$id") },
                )
            }
            composable(Routes.NowPlaying) {
                NowPlayingRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.Collections) {
                CollectionsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenCollection = { id -> navController.navigate("${Routes.CollectionDetail}/$id") },
                )
            }
            composable(Routes.ScanLocal) {
                ScanLocalRoute(
                    onBack = { navController.popBackStack() },
                    onOpenCollections = { navController.navigate(Routes.Collections) },
                    onOpenNowPlaying = { navController.navigate(Routes.NowPlaying) },
                )
            }
            composable(
                route = "${Routes.CollectionDetail}/{collectionId}",
                arguments =
                    listOf(
                        navArgument("collectionId") { type = NavType.LongType },
                    ),
            ) {
                val id = it.arguments?.getLong("collectionId") ?: 0L
                CollectionDetailRoute(
                    collectionId = id,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = { navController.navigate(Routes.NowPlaying) },
                )
            }
        }

        if (showMiniPlayer) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AppMiniPlayer(
                    state = playerState,
                    onOpenNowPlaying = { navController.navigate(Routes.NowPlaying) },
                    onTogglePlayPause = { app.appContainer.playerController.togglePlayPause() },
                    onSkipPrevious = { app.appContainer.playerController.skipToPrevious() },
                    onSkipNext = { app.appContainer.playerController.skipToNext() },
                )
            }
        }
    }
}
