package com.toy.networkplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.SmbRepository

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val entries: List<SmbEntry>) : LoadState
    data class Failed(val message: String) : LoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    server: ServerConfig,
    path: String,
    onBack: () -> Unit,
    onOpenFolder: (SmbEntry) -> Unit,
    onOpenImage: (images: List<SmbEntry>, index: Int) -> Unit,
    onOpenVideo: (videos: List<SmbEntry>, index: Int) -> Unit,
) {
    var state by remember(server, path) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(server, path, reload) {
        state = LoadState.Loading
        state = runCatching { SmbRepository.list(server, path) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: it.javaClass.simpleName) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path.substringAfterLast('\\').ifEmpty { server.displayName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (path.isNotEmpty()) {
                            Text(
                                "${server.share}\\$path",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "새로고침") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is LoadState.Failed -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("폴더를 열 수 없어요\n${s.message}")
                    Button(onClick = { reload++ }) { Text("다시 시도") }
                }
                is LoadState.Loaded -> {
                    if (s.entries.isEmpty()) {
                        Text("빈 폴더예요", Modifier.align(Alignment.Center))
                    }
                    val images = s.entries.filter { it.kind == MediaKind.IMAGE }
                    val videos = s.entries.filter { it.kind == MediaKind.VIDEO }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(s.entries, key = { it.path }) { e ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    when (e.kind) {
                                        MediaKind.FOLDER -> onOpenFolder(e)
                                        MediaKind.IMAGE -> onOpenImage(images, images.indexOf(e))
                                        MediaKind.VIDEO -> onOpenVideo(videos, videos.indexOf(e))
                                        MediaKind.OTHER -> Unit
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        when (e.kind) {
                                            MediaKind.FOLDER -> Icons.Default.Folder
                                            MediaKind.VIDEO -> Icons.Default.Movie
                                            MediaKind.IMAGE -> Icons.Default.Image
                                            MediaKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
                                        },
                                        null,
                                    )
                                },
                                headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = if (e.isDirectory) null else { { Text(formatSize(e.size)) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else "%.1f %s".format(v, units[i])
}
