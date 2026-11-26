package com.toy.networkplayer.ui

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIAL = 150.dp
/** 테두리에서 이만큼 안쪽까지 닿으면 재생 막대를 잡은 것으로 본다. */
private val RING_TOUCH = 30.dp
private val RING_STROKE = 7.dp

/**
 * 원형 플로팅 제어기(애플 TV 리모컨 느낌).
 * 테두리 = 재생 막대: 12시에서 시계방향으로 차오르고, 테두리를 잡고 돌리면 그 지점으로 이동한다.
 * 안쪽: 가운데 재생/일시정지, 왼쪽/오른쪽 10초, 아래쪽 이전/다음 영상. 안쪽을 잡고 끌면 다이얼이 옮겨진다.
 */
@Composable
fun ControlDial(
    prefs: SharedPreferences,
    playing: Boolean,
    time: Long,
    length: Long,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onTouch: () -> Unit,
    skipMs: Long,
) {
    var fx by remember { mutableFloatStateOf(prefs.getFloat("dial_x", 0.8f)) }
    var fy by remember { mutableFloatStateOf(prefs.getFloat("dial_y", 0.6f)) }
    val progress = if (length > 0) (time.toFloat() / length).coerceIn(0f, 1f) else 0f
    val state by rememberUpdatedState(DialState(progress, length, hasPrev, hasNext))
    val cb by rememberUpdatedState(DialCallbacks(onPlayPause, onSkip, onPrev, onNext, onSeek, onSeekEnd, onTouch))
    val density = LocalDensity.current
    val dialPx = with(density) { DIAL.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val x = (fx * w - dialPx / 2).coerceIn(0f, (w - dialPx).coerceAtLeast(0f))
        val y = (fy * h - dialPx / 2).coerceIn(0f, (h - dialPx).coerceAtLeast(0f))

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(DIAL)
                .pointerInput(w, h) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        cb.onTouch()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f
                        val fromCenter = down.position - center
                        if (fromCenter.getDistance() >= radius - RING_TOUCH.toPx() && state.length > 0) {
                            // 테두리: 잡은 지점으로 이동하고, 돌리는 대로 따라간다.
                            var last = angleFraction(fromCenter)
                            cb.onSeek(last * state.length)
                            drag(down.id) { change ->
                                var f = angleFraction(change.position - center)
                                // 12시를 넘어 처음/끝으로 튀지 않게 끝에서 멈춘다.
                                if (abs(f - last) > 0.5f) f = if (last > 0.5f) 1f else 0f
                                last = f
                                cb.onSeek(f * state.length)
                                cb.onTouch()
                                change.consume()
                            }
                            cb.onSeekEnd()
                            return@awaitEachGesture
                        }
                        // 안쪽: 움직이면 다이얼 옮기기, 그냥 떼면 위치에 맞는 버튼.
                        var moved = false
                        var lastUp = down.position
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) {
                                lastUp = c.position
                                break
                            }
                            if (!moved && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                val d = c.positionChange()
                                fx = (fx + d.x / w).coerceIn(0f, 1f)
                                fy = (fy + d.y / h).coerceIn(0f, 1f)
                                c.consume()
                                cb.onTouch()
                            }
                        }
                        if (moved) {
                            prefs.edit().putFloat("dial_x", fx).putFloat("dial_y", fy).apply()
                            return@awaitEachGesture
                        }
                        val inner = radius - RING_TOUCH.toPx()
                        val d = lastUp - center
                        when {
                            d.getDistance() < inner * 0.4f -> cb.onPlayPause()
                            d.y > inner * 0.45f -> if (d.x < 0) { if (state.hasPrev) cb.onPrev() } else { if (state.hasNext) cb.onNext() }
                            d.x < 0 -> cb.onSkip(-skipMs)
                            else -> cb.onSkip(skipMs)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = RING_STROKE.toPx()
                val r = size.minDimension / 2f
                drawCircle(Color(0xCC202020), r)
                val ringR = r - stroke / 2f - 2.dp.toPx()
                val topLeft = Offset(center.x - ringR, center.y - ringR)
                val arcSize = Size(ringR * 2, ringR * 2)
                drawArc(Color(0x44FFFFFF), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(Color.White, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // 현재 지점 손잡이
                val a = (progress * 2 * PI - PI / 2).toFloat()
                val knob = Offset(center.x + ringR * cos(a), center.y + ringR * sin(a))
                drawCircle(Color.White, stroke * 1.1f, knob)
                drawCircle(Color(0xFF202020), stroke * 0.45f, knob)
            }
            Text(
                formatTime(time),
                color = Color(0xCCFFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
            )
            Icon(Icons.Default.Replay10, "10초 뒤로", tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp).size(26.dp))
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "일시정지" else "재생",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
            Icon(Icons.Default.Forward10, "10초 앞으로", tint = Color.White, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).size(26.dp))
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
                Icon(Icons.Default.SkipPrevious, "이전", tint = dialTint(hasPrev), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(18.dp))
                Icon(Icons.Default.SkipNext, "다음", tint = dialTint(hasNext), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private class DialState(val progress: Float, val length: Long, val hasPrev: Boolean, val hasNext: Boolean)

private class DialCallbacks(
    val onPlayPause: () -> Unit,
    val onSkip: (Long) -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onSeekEnd: () -> Unit,
    val onTouch: () -> Unit,
)

/** 12시를 0으로 시계방향 0..1 */
private fun angleFraction(v: Offset): Float {
    val a = atan2(v.x, -v.y) / (2 * PI).toFloat()
    return (a + 1f) % 1f
}

private fun dialTint(enabled: Boolean) = if (enabled) Color.White else Color(0x55FFFFFF)
