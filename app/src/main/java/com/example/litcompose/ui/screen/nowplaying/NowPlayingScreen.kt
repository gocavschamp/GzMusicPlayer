package com.example.litcompose.ui.screen.nowplaying

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.litcompose.core.util.formatDurationMs
import com.example.litcompose.domain.model.LyricLine
import com.example.litcompose.domain.model.Track
import com.example.litcompose.domain.player.PlayMode
import com.example.litcompose.domain.player.PlayerState
import com.example.litcompose.ui.sheet.AddToCollectionSheet

private const val TAG = "Spectrum"

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.playerState.collectAsState()
    val current = state.current
    val hasPrevious = state.queueSize > 1
    val hasNext = state.queueSize > 1
    val lyrics by viewModel.lyrics.collectAsState()
    val artworkUri by viewModel.artworkPath.collectAsState()
    val spectrum by viewModel.spectrum.collectAsState()
    val collections by viewModel.collections.collectAsState()
    var addSheetTrack by remember { mutableStateOf<Track?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val lyricListState = rememberLazyListState()

    // 帧级歌词进度：歌词面板打开且播放中时，基于底层 250ms 轮询的 positionMs 做帧插值，
    // 让歌词行高亮/滚动即时跟随演唱进度，避免明显滞后
    var displayPositionMs by remember { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(showLyrics, current?.id, state.isPlaying) {
        if (!showLyrics || !state.isPlaying) {
            displayPositionMs = state.positionMs
            return@LaunchedEffect
        }
        var basePos = state.positionMs
        var baseNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (baseNanos == 0L) baseNanos = now
                val latest = state.positionMs
                if (latest != basePos) {
                    basePos = latest
                    baseNanos = now
                }
                displayPositionMs = basePos + (now - baseNanos) / 1_000_000
            }
        }
    }

    // 当前歌词行：帧级进度驱动，切行即时
    val activeLyricIndex by remember(displayPositionMs, lyrics) {
        derivedStateOf {
            val idx = lyrics.indexOfLast { it.timeMs <= displayPositionMs }
            if (idx < 0) 0 else idx
        }
    }

    // 唱片旋转角：仅播放时随时间累加，暂停/停止时停在当前角度，恢复播放后无缝续转（12 秒一圈）
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            var lastFrame = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastFrame != 0L) {
                        val deltaSeconds = (now - lastFrame) / 1_000_000_000f
                        rotation = (rotation + deltaSeconds * 30f) % 360f
                    }
                    lastFrame = now
                }
            }
        }
    }

    // 打开歌词面板时立即定位到当前行，避免默认停在第一条
    LaunchedEffect(showLyrics) {
        if (showLyrics && lyrics.isNotEmpty()) {
            lyricListState.scrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(current?.id, activeLyricIndex) {
        if (lyrics.isNotEmpty()) {
            lyricListState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.queueTitle.ifBlank { "单曲播放" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (state.queueSize > 0 && state.currentIndex >= 0) {
                                "${state.currentIndex + 1} / ${state.queueSize}"
                            } else {
                                "未开始"
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier.width(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            viewModel.setPlayMode(
                                PlayMode.entries[
                                    (state.playMode.ordinal + 1) % PlayMode.entries.size
                                ],
                            )
                        },
                    ) {
                        Icon(
                            imageVector = playModeIcon(state.playMode),
                            contentDescription = playModeLabel(state.playMode),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
                    label = "lyricsSwitch",
                ) { show ->
                    if (show) {
                        LyricsPanel(
                            lines = lyrics,
                            activeIndex = activeLyricIndex,
                            listState = lyricListState,
                            title = current?.title,
                            onClose = { showLyrics = false },
                        )
                    } else {
                        AlbumSection(
                            rotation = rotation,
                            current = current,
                            artworkOverride = artworkUri,
                            isPlaying = state.isPlaying,
                            spectrum = spectrum,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDurationMs(state.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThinSeekBar(
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        fraction =
                            if (state.durationMs > 0L) {
                                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        enabled = current != null,
                        onSeekFraction = { fraction ->
                            viewModel.seekTo((fraction * state.durationMs).toLong())
                        },
                    )
                    Text(
                        text = formatDurationMs(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 控制行：左侧下载，中间控制组（居中），右侧歌词切换，避免按钮溢出屏幕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val downloading by viewModel.downloading.collectAsState()
                    if (downloading) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        IconButton(onClick = viewModel::downloadCurrent, enabled = current != null) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "下载到本地",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerActionButton(
                            icon = Icons.Default.SkipPrevious,
                            onClick = viewModel::skipToPrevious,
                            enabled = hasPrevious,
                        )
                        PlayerPrimaryButton(
                            isPlaying = state.isPlaying,
                            enabled = current != null,
                            onClick = viewModel::togglePlayPause,
                        )
                        PlayerActionButton(
                            icon = Icons.Default.SkipNext,
                            onClick = viewModel::skipToNext,
                            enabled = hasNext,
                        )
                    }

                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "歌词",
                            tint =
                                if (showLyrics) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }

                // 辅助行：获取歌词（本地歌曲按歌名+歌手+时长匹配接口）/ 播放列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fetchingLyrics by viewModel.fetchingLyrics.collectAsState()
                    if (fetchingLyrics) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::fetchLyricsForCurrent,
                            enabled = current != null,
                        ) {
                            Text("获取歌词")
                        }
                    }
                    TextButton(onClick = { showQueue = true }) {
                        Text("播放列表")
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueDialog(
            state = state,
            onDismiss = { showQueue = false },
            onSelect = { index ->
                viewModel.skipTo(index)
                showQueue = false
            },
            onRemove = { index -> viewModel.removeFromQueue(index) },
            onAddToCollection = { track -> addSheetTrack = track },
        )
    }

    // 添加到歌单弹窗（复用底部弹窗：选歌单 / 新建歌单）
    AddToCollectionSheet(
        isOpen = addSheetTrack != null,
        collections = collections,
        onDismiss = { addSheetTrack = null },
        onCreateCollection = viewModel::createCollection,
        onAddToCollection = { id ->
            addSheetTrack?.let { viewModel.addToCollection(id, it) }
            addSheetTrack = null
        },
    )
}

private fun playModeIcon(mode: PlayMode): ImageVector =
    when (mode) {
        PlayMode.LIST_LOOP -> Icons.Default.Repeat
        PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
        PlayMode.SHUFFLE -> Icons.Default.Shuffle
    }

private fun playModeLabel(mode: PlayMode): String =
    when (mode) {
        PlayMode.LIST_LOOP -> "列表循环"
        PlayMode.SINGLE_LOOP -> "单曲循环"
        PlayMode.SHUFFLE -> "随机播放"
    }

@Composable
private fun AlbumSection(
    rotation: Float,
    current: Track?,
    artworkOverride: String?,
    isPlaying: Boolean,
    spectrum: FloatArray,
) {
    // 优先使用已保存到本地的补全封面（本地歌曲默认无 artworkUrl）
    val artworkUrl =
        artworkOverride?.takeIf { it.isNotBlank() }
            ?: current?.artworkUrl?.takeIf { it.isNotBlank() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = if (current != null) rotation else 0f
                            transformOrigin = TransformOrigin.Center
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUrl != null) {
                    SubcomposeAsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        content = {
                            if (painter.state is AsyncImagePainter.State.Success) {
                                SubcomposeAsyncImageContent()
                            } else {
                                // 加载中/加载失败都显示唱片占位
                                VinylPlaceholder(Modifier.fillMaxSize())
                            }
                        },
                    )
                } else {
                    VinylPlaceholder(Modifier.fillMaxSize())
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = current?.title ?: "还没有正在播放的歌曲",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = current?.artist?.ifBlank { "未知歌手" } ?: "从歌单或列表中选择一首开始播放",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // 音乐律动：歌手名下方的频谱动效（基于播放器 PCM FFT，随高低音跳动）
        MusicWaveform(isPlaying = isPlaying, spectrum = spectrum)
    }
}

/** 音乐律动波形：读取播放器实时频谱（PCM FFT 对数分桶），随高低音跳动；暂停平滑回落 */
@Composable
private fun MusicWaveform(
    isPlaying: Boolean,
    spectrum: FloatArray,
    modifier: Modifier = Modifier,
) {
    val barCount = 28
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    // 跳动增益：FFT 归一化幅度偏小，放大让波形更明显
    val gain = 2.2f
    // 平滑参数：上升 35% 步进逼近（避免突变），回落每帧保留 92%（衰减更慢、拖尾更柔）
    val attack = 0.35f
    val release = 0.92f
    // 平滑后的频谱（仅主线程帧循环访问）
    val smoothedBars = remember { FloatArray(barCount) }
    var bars by remember { mutableStateOf(FloatArray(barCount)) }
    // 帧循环需要读取最新频谱：rememberUpdatedState 保证重组时更新引用而不重启协程
    val currentSpectrum by rememberUpdatedState(spectrum)

    // 帧循环：平滑（上升缓逼近、回落缓释，形成声浪拖尾）并驱动重绘
    LaunchedEffect(isPlaying) {
        var frame = 0
        while (true) {
            withFrameNanos { _ ->
                val src = currentSpectrum
                val sm = smoothedBars
                val next = FloatArray(barCount)
                for (i in 0 until barCount) {
                    val target = if (isPlaying && src.size == barCount) src[i] * gain else 0f
                    val v = if (target > sm[i]) {
                        sm[i] + (target - sm[i]) * attack
                    } else {
                        sm[i] * release
                    }
                    sm[i] = v
                    next[i] = v
                }
                bars = next
                frame++
                if (frame % 60 == 0) { // 60fps 帧循环，约每秒一条
                    Log.d(TAG, "ui bars: max=${next.maxOrNull()} sum=${next.sum()}")
                }
            }
        }
    }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth(0.72f)
                .height(76.dp),
    ) {
        val barWidth = size.width / barCount
        val gap = barWidth * 0.4f
        val barW = barWidth - gap
        val midY = size.height / 2f
        val minH = 2.dp.toPx()
        val maxH = size.height
        repeat(barCount) { i ->
            val h = (minH + bars[i] * (maxH - minH)).coerceIn(minH, maxH)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * barWidth + gap / 2f, midY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

@Composable
private fun VinylPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFF3C3C3C),
                            Color(0xFF151515),
                            Color(0xFF060606),
                        ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 唱片纹路
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val step = maxRadius / 20f
            var radius = maxRadius
            while (radius > maxRadius * 0.18f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.5f),
                )
                radius -= step
            }
            // 高光弧：唱片旋转时位置随之变化，让旋转肉眼可见
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 25f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft =
                    Offset(
                        center.x - maxRadius * 0.72f,
                        center.y - maxRadius * 0.72f,
                    ),
                size = Size(maxRadius * 1.44f, maxRadius * 1.44f),
                style = Stroke(width = maxRadius * 0.09f),
            )
        }
        // 中心标签
        Box(
            modifier =
                Modifier
                    .fillMaxSize(0.28f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(0.5f)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        ),
            )
        }
    }
}

@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    activeIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    title: String?,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "歌词 · ${title ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) {
                    Text("收起歌词")
                }
            }
            if (lines.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无歌词",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 36.dp),
                ) {
                    items(lines.size) { index ->
                        val isActive = index == activeIndex
                        Text(
                            text = lines[index].text,
                            style =
                                if (isActive) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color =
                                if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinSeekBar(
    fraction: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbWhite = Color.White
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = false
                        drag(down.id) { change ->
                            change.consume()
                            dragged = true
                            onSeekFraction((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                        if (!dragged) {
                            onSeekFraction((down.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackThickness = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, centerY - trackThickness / 2f),
                size = Size(size.width, trackThickness),
                cornerRadius = CornerRadius(trackThickness / 2f),
            )
            val activeWidth = size.width * fraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackThickness / 2f),
                    size = Size(activeWidth, trackThickness),
                    cornerRadius = CornerRadius(trackThickness / 2f),
                )
            }
            val thumbX = size.width * fraction
            drawCircle(
                color = activeColor.copy(alpha = 0.2f),
                radius = thumbRadius + 5.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = thumbWhite,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun QueueDialog(
    state: PlayerState,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddToCollection: (Track) -> Unit,
) {
    val listState = rememberLazyListState()
    // 打开/切歌时滚动到当前播放曲目（点击查看列表跳转到正在播放一条）
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
            listState.scrollToItem((state.currentIndex - 1).coerceAtLeast(0))
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "当前播放列表 · ${state.queueSize} 首",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                if (state.queue.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无播放列表",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                            QueueRow(
                                index = index,
                                track = track,
                                isCurrent = index == state.currentIndex,
                                isPlaying = index == state.currentIndex && state.isPlaying,
                                onClick = { onSelect(index) },
                                onRemove = { onRemove(index) },
                                onAddToCollection = { onAddToCollection(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDurationMs(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 添加到歌单 / 从播放列表移除（按钮自带点击消费，不影响行点击播放）
        IconButton(onClick = onAddToCollection, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加到歌单",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "从播放列表移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PlayerPrimaryButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 10.dp,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
