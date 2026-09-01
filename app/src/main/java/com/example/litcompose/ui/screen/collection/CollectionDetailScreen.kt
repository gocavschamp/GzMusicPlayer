package com.example.litcompose.ui.screen.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.litcompose.domain.model.Track
import com.example.litcompose.ui.component.MarqueeText
import com.example.litcompose.ui.component.ReorderableLazyColumn
import com.example.litcompose.ui.sheet.AddToCollectionSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onBack: () -> Unit,
    onPlay: (Track, Int, List<Track>, String) -> Unit,
    onPlayAll: (List<Track>, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var menuTrackId by remember { mutableStateOf<String?>(null) }
    var isAddSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.title.ifBlank { "歌单" }, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.tracks.size}首",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::reverseOrder) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "切换正序/倒序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onPlayAll(state.tracks, state.title) },
                    enabled = state.tracks.isNotEmpty(),
                ) {
                    Text("播放全部")
                }
            }
        }

        ReorderableLazyColumn(
            items = state.tracks,
            key = { it.id },
            modifier = Modifier.fillMaxSize(),
            onMove = viewModel::moveTrack,
        ) { track, isDragging, dragModifier ->
            val index = state.tracks.indexOfFirst { it.id == track.id }
            Row(
                modifier =
                    dragModifier
                        .fillMaxWidth()
                        .alpha(if (isDragging) 0.85f else 1f)
                        .clickable { onPlay(track, index, state.tracks, state.title) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MarqueeText(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = track.artist.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuTrackId = track.id }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "歌曲操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuTrackId == track.id,
                        onDismissRequest = { menuTrackId = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加到其他歌单") },
                            onClick = {
                                menuTrackId = null
                                actionTrack = track
                                isAddSheetOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("从歌单移除") },
                            onClick = {
                                viewModel.removeFromCollection(track.id)
                                menuTrackId = null
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (track.id in state.favoriteTrackIds) {
                                        "取消收藏"
                                    } else {
                                        "加入我的收藏"
                                    },
                                )
                            },
                            onClick = {
                                viewModel.toggleFavorite(track.id)
                                menuTrackId = null
                            },
                        )
                    }
                }
            }
        }
    }

    AddToCollectionSheet(
        isOpen = isAddSheetOpen,
        collections = state.collections.filter { it.id != state.collectionId },
        onDismiss = {
            isAddSheetOpen = false
            actionTrack = null
        },
        onCreateCollection = viewModel::createCollection,
        onAddToCollection = { collectionId ->
            actionTrack?.let { viewModel.addToCollection(collectionId, it) }
            isAddSheetOpen = false
            actionTrack = null
        },
        title = "添加到其他歌单",
    )
}
