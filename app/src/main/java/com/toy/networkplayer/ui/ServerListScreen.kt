package com.toy.networkplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.ServerStore
import com.toy.networkplayer.data.SmbRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(store: ServerStore, onOpen: (ServerConfig) -> Unit) {
    var servers by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun update(list: List<ServerConfig>) {
        servers = list
        store.save(list)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("공유폴더") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "서버 추가") }
        },
    ) { padding ->
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("+ 버튼을 눌러 윈도우 공유폴더를 추가하세요", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(servers, key = { it.id }) { s ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(s) },
                        leadingContent = { Icon(Icons.Default.Computer, null) },
                        headlineContent = { Text(s.displayName) },
                        supportingContent = { Text("smb://${s.host}/${s.share}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, "수정") }
                                IconButton(onClick = { update(servers.filter { it.id != s.id }) }) {
                                    Icon(Icons.Default.Delete, "삭제")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        ServerEditDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { saved ->
                val list = if (servers.any { it.id == saved.id }) {
                    servers.map { if (it.id == saved.id) saved else it }
                } else {
                    servers + saved
                }
                update(list)
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun ServerEditDialog(
    initial: ServerConfig?,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var share by remember { mutableStateOf(initial?.share ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun build() = ServerConfig(
        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(),
        // "\\PC\공유" 나 "PC/공유" 를 붙여넣어도 알아서 나눠준다.
        host = host.trim().trim('\\', '/').substringBefore('\\').substringBefore('/'),
        share = share.trim().trim('\\', '/').ifBlank {
            host.trim().trim('\\', '/').replace('/', '\\').substringAfter('\\', "")
        },
        username = user.trim(),
        password = pass,
        domain = domain.trim(),
    )

    val valid = host.isNotBlank() && build().share.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "공유폴더 추가" else "공유폴더 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    host, { host = it },
                    label = { Text("PC 주소 (예: 192.168.0.10)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(share, { share = it }, label = { Text("공유 이름 (예: Videos)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("윈도우 사용자 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text("비밀번호") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(domain, { domain = it }, label = { Text("도메인 (보통 비워두세요)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(
                    enabled = valid && !testing,
                    onClick = {
                        testing = true
                        status = "연결 확인 중..."
                        scope.launch {
                            status = runCatching { SmbRepository.test(build()) }
                                .fold({ "연결 성공!" }, { "연결 실패: ${it.message ?: it.javaClass.simpleName}" })
                            testing = false
                        }
                    },
                ) { Text("연결 테스트") }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(build()) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
