package com.toy.networkplayer.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
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
    /** 길게 누르기. 가운데는 꾹 누르는 동안 배속, 왼쪽/오른쪽은 이전/다음 영상. */
    val onLongPress: (TapZone) -> Unit,
    /** 두 번째 탭을 떼지 않고 누르고 있을 때(더블탭 후 꾹). 떼면 onRelease. */
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

/** 더블탭의 두 번째 탭을 이만큼 누르고 있으면 배속으로 본다. */
private const val DOUBLE_TAP_HOLD_MS = 300L

private enum class Axis { NONE, IGNORED, HORIZONTAL, VERTICAL }

@Composable
fun GestureLayer(locked: Boolean, callbacks: GestureCallbacks, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(callbacks)

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(locked) {
                fun zoneOf(o: Offset): TapZone {
                    val third = size.width / 3f
                    return when {
                        o.x < third -> TapZone.LEFT
                        o.x > third * 2 -> TapZone.RIGHT
                        else -> TapZone.CENTER
                    }
                }

                /** 손가락이 모두 떨어질 때까지 기다린다(중간에 드래그로 바뀌어도). */
                suspend fun AwaitPointerEventScope.awaitAllUp() {
                    do {
                        val e = awaitPointerEvent()
                    } while (e.changes.any { it.pressed })
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val longMs = viewConfiguration.longPressTimeoutMillis
                    // 시간 안에 손을 떼면 탭, 드래그로 바뀌면 취소, 시간이 지나면 길게 누르기.
                    var timedOut = true
                    val up = withTimeoutOrNull(longMs) {
                        val r = waitForUpOrCancellation()
                        timedOut = false
                        r
                    }
                    if (timedOut) {
                        val c = currentEvent.changes.firstOrNull { it.id == down.id }
                        val moved = c == null || c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (locked || moved) {
                            awaitAllUp()
                            return@awaitEachGesture
                        }
                        val zone = zoneOf(down.position)
                        cb.onLongPress(zone)
                        awaitAllUp()
                        if (zone == TapZone.CENTER) cb.onRelease()
                        return@awaitEachGesture
                    }
                    if (up == null) return@awaitEachGesture
                    if (locked) {
                        cb.onTap()
                        return@awaitEachGesture
                    }

                    // 두 번째 탭을 기다린다. 없으면 그냥 한 번 탭.
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                    if (second == null) {
                        cb.onTap()
                        return@awaitEachGesture
                    }
                    var held = true
                    val up2 = withTimeoutOrNull(DOUBLE_TAP_HOLD_MS) {
                        val r = waitForUpOrCancellation()
                        held = false
                        r
                    }
                    if (held) {
                        // 더블탭 후 떼지 않고 누르고 있으면 그동안 배속.
                        cb.onHoldStart()
                        awaitAllUp()
                        cb.onRelease()
                    } else if (up2 != null) {
                        cb.onDoubleTap(zoneOf(second.position))
                    }
                }
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
