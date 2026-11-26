package com.toy.networkplayer.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 플로팅 메뉴에 넣을 수 있는 버튼들. 순서대로 3개씩 줄을 채운다. */
enum class PanelItem(val key: String, val label: String) {
    BACK10("back10", "10초 뒤로"),
    PLAY("play", "재생 / 일시정지"),
    FORWARD10("fwd10", "10초 앞으로"),
    SPEED("speed", "배속"),
    SCALE("scale", "화면 비율"),
    LOCK("lock", "화면 잠금"),
    SUBTITLE("subtitle", "자막 바꾸기"),
    AUDIO("audio", "음성 바꾸기"),
    ROTATE("rotate", "영상 회전"),
    LIKE("like", "좋아요"),
    ORIENTATION("orientation", "화면 방향"),
    DECODER("decoder", "디코더"),
    SHUFFLE("shuffle", "무작위 재생 ON/OFF"),
}

/** 재생 화면 밝기를 어떻게 정할지. */
enum class BrightnessMode(val label: String) { REMEMBER("마지막 밝기 기억"), FIXED("고정 밝기"), SYSTEM("폰 밝기 따르기") }

/** 하단 제어버튼(이전, 10초, 재생, 10초, 다음)을 어디에 모을지. */
enum class ControlAlign(val label: String) { START("왼쪽"), CENTER("가운데"), END("오른쪽"), FLOAT("원형 다이얼") }

private const val KEY_HIDDEN = "panel_hidden"
private const val KEY_ALIGN = "control_align"
private const val KEY_SHUFFLE = "shuffle_play"
private const val KEY_BRIGHT_MODE = "brightness_mode"
private const val KEY_BRIGHT_LAST = "brightness_last"
private const val KEY_BRIGHT_FIXED = "brightness_fixed"

object PlayerPrefs {
    fun hiddenItems(prefs: SharedPreferences): Set<PanelItem> {
        val keys = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        return PanelItem.entries.filter { it.key in keys }.toSet()
    }

    fun setHiddenItems(prefs: SharedPreferences, hidden: Set<PanelItem>) {
        prefs.edit().putStringSet(KEY_HIDDEN, hidden.map { it.key }.toSet()).apply()
    }

    fun controlAlign(prefs: SharedPreferences): ControlAlign =
        ControlAlign.entries.getOrElse(prefs.getInt(KEY_ALIGN, ControlAlign.CENTER.ordinal)) { ControlAlign.CENTER }

    fun setControlAlign(prefs: SharedPreferences, align: ControlAlign) {
        prefs.edit().putInt(KEY_ALIGN, align.ordinal).apply()
    }

    fun brightnessMode(prefs: SharedPreferences): BrightnessMode =
        BrightnessMode.entries.getOrElse(prefs.getInt(KEY_BRIGHT_MODE, 0)) { BrightnessMode.REMEMBER }

    fun setBrightnessMode(prefs: SharedPreferences, mode: BrightnessMode) {
        prefs.edit().putInt(KEY_BRIGHT_MODE, mode.ordinal).apply()
    }

    /** 마지막으로 맞춘 밝기(0.01..1). 없으면 -1. */
    fun lastBrightness(prefs: SharedPreferences) = prefs.getFloat(KEY_BRIGHT_LAST, -1f)

    fun setLastBrightness(prefs: SharedPreferences, v: Float) {
        prefs.edit().putFloat(KEY_BRIGHT_LAST, v).apply()
    }

    fun fixedBrightness(prefs: SharedPreferences) = prefs.getFloat(KEY_BRIGHT_FIXED, 1f)

    fun setFixedBrightness(prefs: SharedPreferences, v: Float) {
        prefs.edit().putFloat(KEY_BRIGHT_FIXED, v).apply()
    }

    /** 재생 화면을 열 때 적용할 밝기. -1이면 폰 밝기 그대로. */
    fun startBrightness(prefs: SharedPreferences): Float = when (brightnessMode(prefs)) {
        BrightnessMode.REMEMBER -> lastBrightness(prefs)
        BrightnessMode.FIXED -> fixedBrightness(prefs)
        BrightnessMode.SYSTEM -> -1f
    }

    fun shufflePlay(prefs: SharedPreferences) = prefs.getBoolean(KEY_SHUFFLE, false)

    fun setShufflePlay(prefs: SharedPreferences, on: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE, on).apply()
    }
}

/** 플레이어 화면 설정. 고르는 즉시 적용되고 저장된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsDialog(
    align: ControlAlign,
    onAlign: (ControlAlign) -> Unit,
    hidden: Set<PanelItem>,
    onHiddenChange: (Set<PanelItem>) -> Unit,
    brightnessMode: BrightnessMode,
    onBrightnessMode: (BrightnessMode) -> Unit,
    fixedBrightness: Float,
    onFixedBrightness: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("플레이어 설정") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("하단 제어버튼 위치", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ControlAlign.entries.forEach { a ->
                        FilterChip(selected = a == align, onClick = { onAlign(a) }, label = { Text(a.label) })
                    }
                }
                if (align == ControlAlign.FLOAT) {
                    Text(
                        "테두리를 돌리면 감기/되감기, 가운데 재생, 좌우 10초, 아래 이전/다음 영상이에요. 안쪽을 끌어 옮기면 위치를 기억해요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("재생 화면 밝기", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrightnessMode.entries.forEach { m ->
                        FilterChip(selected = m == brightnessMode, onClick = { onBrightnessMode(m) }, label = { Text(m.label) })
                    }
                }
                Text(
                    when (brightnessMode) {
                        BrightnessMode.REMEMBER -> "왼쪽 위아래로 밀어 맞춘 밝기를 다음에도 그대로 써요."
                        BrightnessMode.FIXED -> "재생할 때마다 이 밝기로 시작해요: ${(fixedBrightness * 100).roundToInt()}%"
                        BrightnessMode.SYSTEM -> "폰에 설정된 밝기로 시작해요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (brightnessMode == BrightnessMode.FIXED) {
                    Slider(
                        value = fixedBrightness,
                        onValueChange = { onFixedBrightness((it * 20).roundToInt() / 20f) },
                        valueRange = 0.05f..1f,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("플로팅 메뉴에 보일 버튼", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onHiddenChange(emptySet()) }) { Text("모두 보이기") }
                }
                Text(
                    "설정 버튼은 항상 보여요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PanelItem.entries.forEach { item ->
                    val shown = item !in hidden
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onHiddenChange(if (shown) hidden + item else hidden - item) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = shown, onCheckedChange = { onHiddenChange(if (it) hidden - item else hidden + item) })
                        Text(item.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
