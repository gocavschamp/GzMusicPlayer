package com.example.litcompose.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 支持长按拖拽排序的 LazyColumn。
 * 拖拽中的行跟随手指平移并置顶，其余行自动让位；
 * 松手后回调 [onMove]，由调用方负责持久化新顺序。
 */
@Composable
fun <T> ReorderableLazyColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onMove: (from: Int, to: Int) -> Unit,
    itemContent: @Composable (item: T, isDragging: Boolean, dragModifier: Modifier) -> Unit,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val isDragging = index == draggingIndex
            // 非拖动行：为拖拽中的行让出位置，实现跟手效果
            val shift =
                if (draggingIndex >= 0 && !isDragging && itemHeightPx > 0f) {
                    val target = draggingIndex + (draggingOffset / itemHeightPx).roundToInt()
                    when {
                        index > draggingIndex && index <= target -> -itemHeightPx
                        index < draggingIndex && index >= target -> itemHeightPx
                        else -> 0f
                    }
                } else {
                    0f
                }
            val animatedShift by animateFloatAsState(
                targetValue = shift,
                label = "reorderShift",
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = if (isDragging) draggingOffset else animatedShift
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .shadow(
                            elevation = if (isDragging) 8.dp else 0.dp,
                            shape = RoundedCornerShape(8.dp),
                        ),
            ) {
                itemContent(
                    item,
                    isDragging,
                    Modifier.pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    draggingOffset = 0f
                                    itemHeightPx =
                                        listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == index }
                                            ?.size
                                            ?.toFloat()
                                            ?: 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    draggingOffset += amount.y
                                },
                                onDragEnd = {
                                    if (itemHeightPx > 0f) {
                                        val deltaIndex = (draggingOffset / itemHeightPx).roundToInt()
                                        val to = (index + deltaIndex).coerceIn(0, items.lastIndex.coerceAtLeast(0))
                                        if (to != index) {
                                            onMove(index, to)
                                        }
                                    }
                                    draggingIndex = -1
                                    draggingOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    draggingOffset = 0f
                                },
                            )
                        },
                )
            }
        }
    }
}
