package com.toy.networkplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.toy.networkplayer.data.Favorites
import com.toy.networkplayer.data.FileOps
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.SmbEntry

/** 파일을 꾹 눌렀을 때 뜨는 메뉴. */
@Composable
fun FileActionDialog(
    entry: SmbEntry,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val canLike = entry.kind != MediaKind.OTHER
    val liked = Favorites.isFavorite(entry)
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (canLike) {
                    ListItem(
                        colors = itemColors,
                        modifier = Modifier.clickableRow(onLike),
                        leadingContent = { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = HEART) },
                        headlineContent = { Text(if (liked) "좋아요 취소" else "좋아요") },
                    )
                }
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickableRow(onRename),
                    leadingContent = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    headlineContent = { Text("이름 바꾸기") },
                )
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickableRow(onDelete),
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("지우기", color = MaterialTheme.colorScheme.error) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.fillMaxWidth().clickable(onClick = onClick)

/** 이름 바꾸기. 처음엔 확장자를 뺀 부분만 골라 둬서 바로 고쳐 쓸 수 있다. */
@Composable
fun RenameDialog(entry: SmbEntry, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val baseEnd = if (entry.isDirectory || !entry.name.contains('.')) entry.name.length else entry.name.lastIndexOf('.')
    var value by remember { mutableStateOf(TextFieldValue(entry.name, TextRange(0, baseEnd))) }
    val focus = remember { FocusRequester() }
    val name = value.text.trim()
    val problem = FileOps.checkName(name)
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 바꾸기") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (problem == null) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = problem == null) { Text("바꾸기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 지우기 전에 한 번 더 묻는다. PC에서도 사라지고 휴지통에도 안 남는다. */
@Composable
fun DeleteDialog(entry: SmbEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.isDirectory) "폴더를 지울까요?" else "파일을 지울까요?") },
        text = {
            Text(
                buildString {
                    append(entry.name)
                    append("\n\n")
                    if (entry.isDirectory) append("폴더 안에 든 파일까지 전부 지워져요. ")
                    append("PC 휴지통에도 남지 않아서 되돌릴 수 없어요.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("지우기", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
