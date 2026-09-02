package com.example.litcompose.ui.screen.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.litcompose.ui.theme.AccentColor
import com.example.litcompose.ui.theme.ThemeController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onBack: (() -> Unit)?,
    onOpenScan: () -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的歌单", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            },
            actions = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
                // 本地音乐扫描入口
                IconButton(onClick = onOpenScan) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = "扫描本地音乐")
                }
                Button(onClick = { showCreateDialog = true }) {
                    Text("新建")
                }
                TextButton(
                    onClick = viewModel::startFetchAllLyrics,
                    enabled = !state.enriching,
                ) {
                    Text("获取歌词")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.collections, key = { it.id }) { c ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCollection(c.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = c.name)
                    Text(text = "${c.trackCount}首")
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createCollection(trimmed)
                            name = ""
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                Button(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("歌单名称") },
                )
            },
        )
    }

    val batchProgress = state.batchProgress
    if (state.enriching && batchProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            dismissButton = {},
            title = { Text("正在获取歌词和封面") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = batchProgress.currentTitle.ifBlank { "准备中…" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            "${batchProgress.done} / ${batchProgress.total} · " +
                                "成功 ${batchProgress.matched} · 失败 ${batchProgress.failed}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (batchProgress.total > 0) {
                                batchProgress.done.toFloat() / batchProgress.total
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ThemeSettingsSheet()
        }
    }
}

/** 主题设置面板：深色模式 + 主题主色切换 */
@Composable
private fun ThemeSettingsSheet() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("深色模式", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = ThemeController.darkTheme,
                onCheckedChange = ThemeController::setDark,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("主题色", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentColor.values().forEach { accent ->
                    val selected = ThemeController.accent == accent
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.light)
                                .clickable { ThemeController.setAccentColor(accent) }
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = accent.label,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
