package com.example.litcompose.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.litcompose.domain.model.Track
import com.example.litcompose.ui.component.MarqueeText
import com.example.litcompose.ui.sheet.AddToCollectionSheet

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var pendingAddTrack by remember { mutableStateOf<Track?>(null) }
    var isSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        if (onBack != null) {
            SearchTopBar(onBack = onBack)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.query,
                onValueChange = viewModel::updateQuery,
                singleLine = true,
                label = { Text("搜索歌曲/歌手") },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            Button(onClick = viewModel::search, enabled = !state.isSearching) {
                Text(if (state.isSearching) "搜索中…" else "搜索")
            }
        }

        // 搜索引擎切换（对应 cocodownloader 的渠道选择）
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.providers.forEach { provider ->
                FilterChip(
                    selected = state.selectedProvider == provider.id,
                    onClick = { viewModel.selectProvider(provider.id) },
                    label = { Text(provider.name) },
                )
            }
        }

        Spacer(modifier = Modifier.padding(top = 4.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.results, key = { it.id }) { track ->
                    val index = state.results.indexOfFirst { it.id == track.id }
                    SearchResultRow(
                        track = track,
                        isDownloading = state.downloadingIds.contains(track.id),
                        onPlay = { viewModel.playTrack(track, index, state.results) },
                        onAddToCollection = {
                            pendingAddTrack = track
                            isSheetOpen = true
                        },
                        onDownload = { viewModel.download(track) },
                    )
                }
            }

            if (state.isPreparingPlay) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "正在解析播放链接…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val currentTrack = pendingAddTrack
    AddToCollectionSheet(
        isOpen = isSheetOpen,
        collections = state.collections,
        onDismiss = {
            isSheetOpen = false
            pendingAddTrack = null
        },
        onCreateCollection = viewModel::createCollection,
        onAddToCollection = { collectionId ->
            if (currentTrack != null) {
                viewModel.addToCollection(collectionId, currentTrack)
            }
            isSheetOpen = false
            pendingAddTrack = null
        },
        title = "添加搜索歌曲到歌单",
    )
}

@Composable
private fun SearchTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "网络搜索",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultRow(
    track: Track,
    isDownloading: Boolean,
    onPlay: () -> Unit,
    onAddToCollection: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MarqueeText(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = track.artist.ifBlank { "未知歌手" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddToCollection) {
                    Icon(
                        imageVector = Icons.Default.LibraryAdd,
                        contentDescription = "添加到歌单",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载到本地",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
