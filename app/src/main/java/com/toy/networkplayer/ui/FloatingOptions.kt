package com.toy.networkplayer.ui

import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class FloatingActions(
    val playing: Boolean,
    val speedLabel: String,
    val scaleLabel: String,
    val onPlayPause: () -> Unit,
    val onBack10: () -> Unit,
    val onForward10: () -> Unit,
    val onSpeed: () -> Unit,
    val onScale: () -> Unit,
    val onSubtitle: () -> Unit,
    val onAudio: () -> Unit,
    val onRotate: () -> Unit,
    val onLock: () -> Unit,
    val liked: Boolean,
    val onLike: () -> Unit,
    val orientationLabel: String,
    val onOrientation: () -> Unit,
    val decoderLabel: String,
    val onDecoder: () -> Unit,
)

/**
 * 한 손 조작용으로 화면 위에 떠 있는 옵션 버튼.
 * 끌어서 옮길 수 있고 위치는 기억된다. 누르면 옆에 조작 패널이 열린다.
 */
@Composable
fun FloatingOptions(
    visible: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: FloatingActions,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("player", Context.MODE_PRIVATE) }
    // 화면 비율로 저장해서 가로/세로가 바뀌어도 비슷한 자리에 둔다. 기본은 오른쪽 아래, 엄지 닿는 곳.
    var fx by remember { mutableStateOf(prefs.getFloat("fab_x", 0.88f)) }
    var fy by remember { mutableStateOf(prefs.getFloat("fab_y", 0.62f)) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    if (!visible && !open) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val fab = with(density) { 56.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val fabX = (fx * w - fab / 2).coerceIn(0f, w - fab)
        val fabY = (fy * h - fab / 2).coerceIn(0f, h - fab)

        if (open) {
            // 버튼이 오른쪽에 있으면 패널을 왼쪽에, 왼쪽에 있으면 오른쪽에 연다.
            val px = if (fx > 0.5f) fabX - gap - panelSize.width else fabX + fab + gap
            val py = (fabY + fab / 2 - panelSize.height / 2).coerceIn(0f, (h - panelSize.height).coerceAtLeast(0f))
            Panel(
                actions = actions,
                modifier = Modifier
                    .offset { IntOffset(px.coerceIn(0f, (w - panelSize.width).coerceAtLeast(0f)).roundToInt(), py.roundToInt()) }
                    .onSizeChanged { panelSize = it },
            )
        }

        Box(
            Modifier
                .offset { IntOffset(fabX.roundToInt(), fabY.roundToInt()) }
                .size(56.dp)
                .background(Color(0xCC202020), CircleShape)
                .border(1.dp, Color(0x55FFFFFF), CircleShape)
                .pointerInput(w, h) {
                    detectDragGestures(
                        onDragEnd = { prefs.edit().putFloat("fab_x", fx).putFloat("fab_y", fy).apply() },
                    ) { change, amount ->
                        change.consume()
                        fx = (fx + amount.x / w).coerceIn(0.05f, 0.95f)
                        fy = (fy + amount.y / h).coerceIn(0.08f, 0.92f)
                    }
                }
                .clickable { onOpenChange(!open) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (open) Icons.Default.Close else Icons.Default.Tune, "옵션", tint = Color.White)
        }
    }
}

@Composable
private fun Panel(actions: FloatingActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xE6202020), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(Icons.Default.Replay10, "10초 뒤로", actions.onBack10)
            PanelButton(if (actions.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (actions.playing) "일시정지" else "재생", actions.onPlayPause)
            PanelButton(Icons.Default.Forward10, "10초 앞으로", actions.onForward10)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(Icons.Default.Speed, actions.speedLabel, actions.onSpeed)
            PanelButton(Icons.Default.AspectRatio, actions.scaleLabel, actions.onScale)
            PanelButton(Icons.Default.Lock, "잠금", actions.onLock)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(Icons.Default.Subtitles, "자막", actions.onSubtitle)
            PanelButton(Icons.Default.Audiotrack, "음성", actions.onAudio)
            PanelButton(Icons.Default.Rotate90DegreesCw, "회전", actions.onRotate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(
                if (actions.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "좋아요",
                actions.onLike,
                tint = if (actions.liked) HEART else Color.White,
            )
            PanelButton(Icons.Default.ScreenRotation, actions.orientationLabel, actions.onOrientation)
            PanelButton(Icons.Default.Memory, actions.decoderLabel, actions.onDecoder)
        }
    }
}

@Composable
private fun PanelButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White) {
    Column(
        Modifier
            .width(64.dp)
            .background(Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
