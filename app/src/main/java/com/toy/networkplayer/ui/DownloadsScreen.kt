package com.toy.networkplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.toy.networkplayer.data.AppSettings
import com.toy.networkplayer.data.DownloadStatus
import com.toy.networkplayer.data.DownloadTask
import com.toy.networkplayer.data.Downloads
import com.toy.networkplayer.data.LocalFiles
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.formatBytes
import java.io.File

/**
 * 공유폴더 화면에서 '다운로드'를 눌렀을 때 쓰는 함수를 돌려준다.
 * 저장공간 허용이 없으면 먼저 안내하고, 알림 허용은 처음 한 번만 묻는다(거절해도 다운로드는 된다).
 */
@Composable
fun rememberDownloadStarter(): (ServerConfig, List<SmbEntry>) -> Unit {
    val context = LocalContext.current
    var needAccess by remember { mutableStateOf(false) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    if (needAccess) {
        AlertDialog(
            onDismissRequest = { needAccess = false },
            title = { Text("저장하려면 허용이 필요해요") },
            text = { Text("받은 파일을 휴대폰에 저장하려면 '모든 파일 접근'을 허용해 주세요. 허용하고 돌아와서 다시 눌러주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    needAccess = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { context.startActivity(LocalFiles.allFilesAccessIntent(context)) }
                    } else {
                        legacyLauncher.launch(LocalFiles.legacyPermissions)
                    }
                }) { Text("허용하러 가기") }
            },
            dismissButton = { TextButton(onClick = { needAccess = false }) { Text("취소") } },
        )
    }

    return { server, entries ->
        if (!LocalFiles.hasAccess(context)) {
            needAccess = true
        } else {
            if (Build.VERSION.SDK_INT >= 33) {
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted && !prefs.getBoolean("asked_notify", false)) {
                    prefs.edit().putBoolean("asked_notify", true).apply()
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Downloads.start(context, server, entries)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onPlay: (SmbEntry) -> Unit,
) {
    val tasks by Downloads.tasks.collectAsState()
    val context = LocalContext.current
    val shown = tasks.asReversed()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("다운로드") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = { onOpenFolder(AppSettings.downloadDir(context).absolutePath) }) {
                        Icon(Icons.Default.Folder, "저장 폴더 열기")
                    }
                    if (tasks.any { !it.active }) {
                        TextButton(onClick = { Downloads.clearFinished() }) { Text("끝난 것 지우기") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (shown.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("받는 중인 파일이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "공유폴더에서 파일이나 폴더를 꾹 눌러 '다운로드'를 고르세요.\n저장 위치: ${LocalFiles.pretty(AppSettings.downloadDir(context).absolutePath)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { t -> DownloadRow(t, onPlay, onOpenFolder) }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(t: DownloadTask, onPlay: (SmbEntry) -> Unit, onOpenFolder: (String) -> Unit) {
    val context = LocalContext.current
    val file = File(t.dest)
    val entry = remember(t.dest) { SmbEntry(file.name, t.dest, false, t.total) }
    ListItem(
        modifier = Modifier.clickable(enabled = t.status == DownloadStatus.DONE) {
            if (entry.kind == MediaKind.VIDEO) onPlay(entry) else file.parentFile?.let { onOpenFolder(it.absolutePath) }
        },
        leadingContent = {
            when (t.status) {
                DownloadStatus.DONE -> Icon(Icons.Default.CheckCircle, "완료", tint = MaterialTheme.colorScheme.primary)
                DownloadStatus.FAILED -> Icon(Icons.Default.Error, "실패", tint = MaterialTheme.colorScheme.error)
                DownloadStatus.CANCELED -> Icon(Icons.Default.Close, "취소됨")
                else -> Icon(Icons.Default.Download, "받는 중")
            }
        },
        headlineContent = { Text(if (t.status == DownloadStatus.DONE) file.name else t.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.status == DownloadStatus.RUNNING) {
                    LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                Text(
                    when (t.status) {
                        DownloadStatus.QUEUED -> "대기 중 · ${formatBytes(t.total)} · ${t.source}"
                        DownloadStatus.RUNNING -> buildString {
                            append("${formatBytes(t.done)} / ${formatBytes(t.total)}")
                            if (t.speed > 0) {
                                append(" · ${formatBytes(t.speed)}/s")
                                val leftSec = (t.total - t.done) / t.speed
                                append(" · 남은 시간 ${leftSec / 60}분 ${leftSec % 60}초")
                            }
                        }
                        DownloadStatus.DONE -> "완료 · ${formatBytes(t.total)} · ${LocalFiles.pretty(file.parent ?: "")}"
                        DownloadStatus.FAILED -> "실패: ${t.error ?: "알 수 없는 오류"}"
                        DownloadStatus.CANCELED -> "취소됨"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            when {
                t.active -> IconButton(onClick = { Downloads.cancel(t.id) }) { Icon(Icons.Default.Close, "취소") }
                t.status == DownloadStatus.FAILED || t.status == DownloadStatus.CANCELED ->
                    IconButton(onClick = { Downloads.retry(context, t.id) }) { Icon(Icons.Default.Refresh, "다시 받기") }
                else -> Unit
            }
        },
    )
}

/** 앱 설정: 다운로드 저장 위치, 파일 접근 허용 상태, 앱 정보. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPickDownloadDir: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    var dir by remember { mutableStateOf(AppSettings.downloadDir(context)) }
    var isDefault by remember { mutableStateOf(AppSettings.isDefaultDownloadDir(context)) }
    var access by remember { mutableStateOf(LocalFiles.hasAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = LocalFiles.hasAccess(context)
                dir = AppSettings.downloadDir(context)
                isDefault = AppSettings.isDefaultDownloadDir(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = LocalFiles.hasAccess(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("다운로드")
            ListItem(
                modifier = Modifier.clickable(onClick = onPickDownloadDir),
                leadingContent = { Icon(Icons.Default.Folder, null) },
                headlineContent = { Text("저장 위치") },
                supportingContent = { Text(LocalFiles.pretty(dir.absolutePath) + if (isDefault) " (기본)" else "") },
                trailingContent = { Text("바꾸기", color = MaterialTheme.colorScheme.primary) },
            )
            if (!isDefault) {
                ListItem(
                    modifier = Modifier.clickable {
                        AppSettings.setDownloadDir(context, null)
                        dir = AppSettings.downloadDir(context)
                        isDefault = true
                    },
                    leadingContent = { Icon(Icons.Default.Refresh, null) },
                    headlineContent = { Text("기본 위치로 되돌리기") },
                    supportingContent = { Text(LocalFiles.pretty(LocalFiles.defaultDownloadDir.absolutePath)) },
                )
            }
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenDownloads),
                leadingContent = { Icon(Icons.Default.Download, null) },
                headlineContent = { Text("다운로드 목록") },
            )
            HorizontalDivider()
            SectionTitle("저장공간")
            ListItem(
                modifier = Modifier.clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { context.startActivity(LocalFiles.allFilesAccessIntent(context)) }
                    } else {
                        legacyLauncher.launch(LocalFiles.legacyPermissions)
                    }
                },
                leadingContent = { Icon(Icons.Default.SdStorage, null) },
                headlineContent = { Text("휴대폰 파일 접근") },
                supportingContent = { Text(if (access) "허용됨 · 내 폰 탐색기와 다운로드를 쓸 수 있어요" else "허용 안 됨 · 눌러서 허용하기") },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenAbout),
                leadingContent = { Icon(Icons.Default.Info, null) },
                headlineContent = { Text("앱 정보 · 오픈소스 라이선스") },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
