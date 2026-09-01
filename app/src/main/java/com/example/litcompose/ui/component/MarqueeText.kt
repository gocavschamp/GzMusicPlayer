package com.example.litcompose.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 单行跑马灯文本：文本宽度超过可用宽度时自动横向循环滚动，否则静态显示（Ellipsis）。
 * 仅在文本超宽时才创建无限动画，列表里大多数短歌名不会产生动画开销。
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val textMeasurer = rememberTextMeasurer()
    val textWidthPx = remember(text, style) { textMeasurer.measure(text, style).size.width }
    // 初始为未测量状态（Int.MAX_VALUE），onSizeChanged 拿到真实容器宽度后重组成动画分支
    var containerWidthPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val distancePx = (textWidthPx - containerWidthPx).coerceAtLeast(0)

    if (containerWidthPx != Int.MAX_VALUE && distancePx > 0) {
        val gapPx = with(LocalDensity.current) { 48.dp.toPx() }
        val transition = rememberInfiniteTransition(label = "marquee")
        val offsetX by transition.animateFloat(
            initialValue = 0f,
            targetValue = -(distancePx + gapPx),
            // 速度与滚动距离挂钩：距离越长动画越久，避免忽快忽慢
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = (3000 + distancePx * 12).toInt(), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "marqueeOffset",
        )
        Box(modifier = modifier.onSizeChanged { containerWidthPx = it.width }.clipToBounds()) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer { translationX = offsetX },
            )
        }
    } else {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.onSizeChanged { containerWidthPx = it.width },
        )
    }
}
