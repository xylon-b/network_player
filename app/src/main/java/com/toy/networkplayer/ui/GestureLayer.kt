package com.toy.networkplayer.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class TapZone { LEFT, CENTER, RIGHT }

/** 재생 화면 위의 투명 레이어에서 탭, 더블탭, 길게 누르기, 좌우/상하 드래그를 받는다. */
class GestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTap: (TapZone) -> Unit,
    val onHoldStart: () -> Unit,
    val onRelease: () -> Unit,
    val onSeekStart: () -> Unit,
    /** 드래그 시작점부터 누적된 가로 이동량. 화면 너비 기준 비율(-1..1). */
    val onSeek: (Float) -> Unit,
    val onSeekEnd: () -> Unit,
    /** 이번 이벤트의 세로 이동량. 위로 올리면 양수, 화면 높이 기준 비율. */
    val onVertical: (leftSide: Boolean, delta: Float) -> Unit,
    val onVerticalEnd: () -> Unit,
)

private enum class Axis { NONE, IGNORED, HORIZONTAL, VERTICAL }

@Composable
fun GestureLayer(locked: Boolean, callbacks: GestureCallbacks, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(callbacks)

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { cb.onTap() },
                    onDoubleTap = if (locked) null else { o: Offset ->
                        val third = size.width / 3f
                        cb.onDoubleTap(
                            when {
                                o.x < third -> TapZone.LEFT
                                o.x > third * 2 -> TapZone.RIGHT
                                else -> TapZone.CENTER
                            }
                        )
                    },
                    onLongPress = if (locked) null else { _: Offset -> cb.onHoldStart() },
                    onPress = {
                        tryAwaitRelease()
                        cb.onRelease()
                    },
                )
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                val edge = 28.dp.toPx()
                var axis = Axis.NONE
                var total = Offset.Zero
                var leftSide = true

                fun finish() {
                    when (axis) {
                        Axis.HORIZONTAL -> cb.onSeekEnd()
                        Axis.VERTICAL -> cb.onVerticalEnd()
                        else -> Unit
                    }
                    axis = Axis.NONE
                }

                detectDragGestures(
                    onDragStart = { o ->
                        total = Offset.Zero
                        leftSide = o.x < size.width / 2f
                        // 화면 가장자리는 시스템 뒤로가기/알림 제스처와 겹치니 무시한다.
                        val nearEdge = o.x < edge || o.x > size.width - edge || o.y < edge || o.y > size.height - edge
                        axis = if (nearEdge) Axis.IGNORED else Axis.NONE
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onDrag = { change, amount ->
                        if (axis == Axis.IGNORED) return@detectDragGestures
                        change.consume()
                        total += amount
                        if (axis == Axis.NONE) {
                            axis = if (abs(total.x) > abs(total.y)) Axis.HORIZONTAL else Axis.VERTICAL
                            if (axis == Axis.HORIZONTAL) cb.onSeekStart()
                        }
                        if (axis == Axis.HORIZONTAL) {
                            cb.onSeek(total.x / size.width)
                        } else {
                            cb.onVertical(leftSide, -amount.y / size.height)
                        }
                    },
                )
            },
    )
}
