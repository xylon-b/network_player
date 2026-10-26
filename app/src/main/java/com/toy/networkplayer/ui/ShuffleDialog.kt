package com.toy.networkplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.ShuffleBias
import com.toy.networkplayer.data.ShuffleOptions

/** 랜덤 정렬 조건을 고르는 창. */
@Composable
fun ShuffleDialog(
    initial: ShuffleOptions,
    videoCount: Int,
    showReset: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onShuffle: (ShuffleOptions) -> Unit,
) {
    var useLength by remember { mutableStateOf(initial.minMinutes > 0) }
    var minutes by remember { mutableStateOf(if (initial.minMinutes > 0) initial.minMinutes.toString() else "10") }
    var onlyUnwatched by remember { mutableStateOf(initial.onlyUnwatched) }
    var bias by remember { mutableStateOf(initial.bias) }

    fun build() = ShuffleOptions(
        minMinutes = if (useLength) minutes.toIntOrNull()?.coerceAtLeast(0) ?: 0 else 0,
        onlyUnwatched = onlyUnwatched,
        bias = bias,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("랜덤 정렬") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이 폴더의 영상 ${videoCount}개 중에서 골라 섞어요", style = MaterialTheme.typography.bodySmall)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useLength, onCheckedChange = { useLength = it })
                    Text("영상 길이")
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(4); useLength = true },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(76.dp).padding(horizontal = 8.dp),
                    )
                    Text("분 이상")
                }
                Row(Modifier.fillMaxWidth().clickable { onlyUnwatched = !onlyUnwatched }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyUnwatched, onCheckedChange = { onlyUnwatched = it })
                    Text("한 번도 안 본 영상만")
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("섞는 방식", style = MaterialTheme.typography.labelLarge)
                ShuffleBias.entries.forEach { b ->
                    Row(Modifier.fillMaxWidth().clickable { bias = b }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = bias == b, onClick = { bias = b })
                        Text(b.label)
                    }
                }
                if (useLength) {
                    Text(
                        "길이를 모르는 영상은 처음 한 번 확인하느라 조금 걸릴 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onShuffle(build()) }) { Text("섞기") } },
        dismissButton = {
            Row {
                if (showReset) TextButton(onClick = onReset) { Text("원래 순서로") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
