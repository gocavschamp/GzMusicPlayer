package com.example.litcompose.ui.screen.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.litcompose.core.util.formatDurationMs
import com.example.litcompose.domain.model.Track
import com.example.litcompose.ui.component.MarqueeText
import com.example.litcompose.ui.sheet.AddToCollectionSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanLocalScreen(
    viewModel: ScanLocalViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCollections: () -> Unit,
    onPlayQueue: (Track, Int, List<Track>) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pendingAddTrack by remember { mutableStateOf<Track?>(null) }
    var isSheetOpen by remember { mutableStateOf(false) }
    var isBatchSheetOpen by remember { mutableStateOf(false) }

    val permission = remember {
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.scan()
            } else {
                viewModel.onPermissionDenied()
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("扫描本地音乐", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            },
            actions = {
                Text(
                    text = if (state.isBatchMode) "已选 ${state.selectedTrackIds.size}" else "歌单",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        if (state.isBatchMode) {
                            if (state.selectedTrackIds.isNotEmpty()) {
                                isBatchSheetOpen = true
                            }
                        } else {
                            onOpenCollections()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(if (state.isBatchMode) "加入歌单" else "我的歌单")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "把设备里的本地音频整理进歌单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "支持单首添加，也支持批量勾选后一次加入歌单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(permission) },
                        enabled = !state.isScanning,
                    ) {
                        Text(if (state.isScanning) "扫描中…" else "一键扫描")
                    }
                    FilterChip(
                        selected = state.isBatchMode,
                        onClick = { viewModel.toggleBatchMode() },
                        label = { Text(if (state.isBatchMode) "退出批量" else "批量选择") },
                        leadingIcon = {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(label = "已扫描", value = "${state.scanned.size} 首")
                    StatPill(label = "歌单", value = "${state.collections.size} 个")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.scanned, key = { it.id }) { track ->
                val index = state.scanned.indexOfFirst { it.id == track.id }
                ScanTrackRow(
                    track = track,
                    selected = state.selectedTrackIds.contains(track.id),
                    batchMode = state.isBatchMode,
                    onClickRow = {
                        if (state.isBatchMode) {
                            viewModel.toggleTrackSelection(track.id)
                        } else {
                            onPlayQueue(track, index, state.scanned)
                        }
                    },
                    onAddToCollection = {
                        if (!state.isBatchMode) {
                            pendingAddTrack = track
                            isSheetOpen = true
                        }
                    },
                )
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
        title = "添加单曲到歌单",
    )

    AddToCollectionSheet(
        isOpen = isBatchSheetOpen,
        collections = state.collections,
        onDismiss = { isBatchSheetOpen = false },
        onCreateCollection = viewModel::createCollection,
        onAddToCollection = { collectionId ->
            viewModel.addSelectedToCollection(collectionId)
            isBatchSheetOpen = false
        },
        title = "批量加入歌单",
    )
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ScanTrackRow(
    track: Track,
    selected: Boolean,
    batchMode: Boolean,
    onClickRow: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 3.dp else 1.dp,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClickRow)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (batchMode) {
                    Box(
                        modifier =
                            Modifier
                                .size(22.dp)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = 1.5.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatDurationMs(track.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (batchMode) {
                    Text(
                        text = if (selected) "已选择" else "选择",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "播放",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "加歌单",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onAddToCollection),
                        )
                    }
                }
            }
        }
    }
}
