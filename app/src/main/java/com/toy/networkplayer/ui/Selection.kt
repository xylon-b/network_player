package com.toy.networkplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.SmbEntry

/** 선택 모드에서 항목을 구분하는 키. 공유 목록 화면처럼 path가 비어 있어도 겹치지 않게 이름까지 붙인다. */
fun selectKey(e: SmbEntry) = e.path + "|" + e.name

/** 선택 모드 상단 막대: 닫기, 몇 개 골랐는지, 모두 선택/해제. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, total: Int, onClose: () -> Unit, onSelectAll: (Boolean) -> Unit) {
    val all = total > 0 && count == total
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "선택 끝내기") } },
        title = { Text(if (count == 0) "항목을 고르세요" else "${count}개 선택") },
        actions = {
            IconButton(onClick = { onSelectAll(!all) }) {
                Icon(if (all) Icons.Default.Deselect else Icons.Default.SelectAll, if (all) "모두 해제" else "모두 선택")
            }
        },
    )
}

class SelectionAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** 선택 모드 하단 막대. 아무것도 안 골랐으면 버튼이 눌리지 않는다. */
@Composable
fun SelectionActionBar(enabled: Boolean, actions: List<SelectionAction>) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            actions.forEach { a ->
                val color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    a.danger -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    Modifier.clickable(enabled = enabled, onClick = a.onClick).padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(a.icon, null, tint = color)
                    Text(a.label, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

/** 여러 개를 한꺼번에 지우기 전에 묻는다. */
@Composable
fun BulkDeleteDialog(count: Int, local: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${count}개 항목을 지울까요?") },
        text = {
            Text(
                "폴더는 안에 든 파일까지 전부 지워져요. " +
                    if (local) "휴대폰에서 바로 지워져서 되돌릴 수 없어요." else "PC 휴지통에도 남지 않아서 되돌릴 수 없어요.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
