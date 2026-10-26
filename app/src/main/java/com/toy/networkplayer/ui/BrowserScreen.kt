package com.toy.networkplayer.ui

import android.widget.Toast
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.toy.networkplayer.data.ShuffleBias
import com.toy.networkplayer.data.ShuffleOptions
import com.toy.networkplayer.data.SmartShuffle
import com.toy.networkplayer.data.WatchHistory
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.toy.networkplayer.data.Thumbnails
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
import com.toy.networkplayer.data.describe

private val VIEW_OPTIONS = listOf(
    5 to "아이콘 아주 작게 (5열)",
    4 to "아이콘 작게 (4열)",
    3 to "아이콘 보통 (3열)",
    2 to "아이콘 크게 (2열)",
    0 to "목록으로 보기",
)

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
    onOpenShare: (String) -> Unit,
    onOpenImage: (images: List<SmbEntry>, index: Int) -> Unit,
    onOpenVideo: (videos: List<SmbEntry>, index: Int, subtitles: List<SmbEntry>) -> Unit,
) {
    var state by remember(server, path) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser", Context.MODE_PRIVATE) }
    // 0 = 목록, 2~5 = 한 줄에 놓을 칸 수
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 3)) }
    var menuOpen by remember { mutableStateOf(false) }

    // 랜덤 정렬 결과. null이면 원래 순서.
    var shuffled by remember(server, path) { mutableStateOf<List<SmbEntry>?>(null) }
    var shuffleDialog by remember { mutableStateOf(false) }
    var shuffleProgress by remember { mutableStateOf<String?>(null) }
    var shuffleOptions by remember {
        mutableStateOf(
            ShuffleOptions(
                minMinutes = prefs.getInt("shuffle_min", 0),
                onlyUnwatched = prefs.getBoolean("shuffle_unwatched", false),
                bias = runCatching { ShuffleBias.valueOf(prefs.getString("shuffle_bias", null) ?: "") }.getOrDefault(ShuffleBias.EVEN),
            )
        )
    }
    val scope = rememberCoroutineScope()

    fun runShuffle(all: List<SmbEntry>, options: ShuffleOptions) {
        shuffleOptions = options
        prefs.edit()
            .putInt("shuffle_min", options.minMinutes)
            .putBoolean("shuffle_unwatched", options.onlyUnwatched)
            .putString("shuffle_bias", options.bias.name)
            .apply()
        val videos = all.filter { it.kind == MediaKind.VIDEO }
        scope.launch {
            if (SmartShuffle.needsDuration(options)) {
                val unknown = videos.filter { WatchHistory.get(server, it).durationMs <= 0 }
                unknown.forEachIndexed { i, v ->
                    shuffleProgress = "영상 길이 확인 중 ${i + 1}/${unknown.size}"
                    Thumbnails.probeDuration(server, v)
                }
            }
            shuffleProgress = null
            val result = SmartShuffle.shuffle(server, videos, options)
            if (result.isEmpty()) {
                Toast.makeText(context, "조건에 맞는 영상이 없어요", Toast.LENGTH_SHORT).show()
            } else {
                shuffled = result
            }
        }
    }
    fun setColumns(c: Int) {
        columns = c
        prefs.edit().putInt("columns", c).apply()
        menuOpen = false
    }

    LaunchedEffect(server, path, reload) {
        state = LoadState.Loading
        state = runCatching {
            if (server.share.isBlank()) {
                // 공유 이름 없이 저장한 PC: 공유 폴더 목록을 첫 화면으로 보여준다.
                SmbRepository.listShares(server).map { SmbEntry(name = it, path = "", isDirectory = true, size = 0) }
            } else {
                SmbRepository.list(server, path)
            }
        }.fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.describe()) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path.substringAfterLast('\\').ifEmpty { server.share.ifBlank { server.displayName } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                actions = {
                    if (server.share.isNotBlank()) {
                        IconButton(onClick = { shuffleDialog = true }) {
                            Icon(Icons.Default.Shuffle, "랜덤 정렬", tint = if (shuffled != null) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                    }
                    IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "새로고침") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(if (columns == 0) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, "보기 방식")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            VIEW_OPTIONS.forEach { (c, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (c == columns) "✓ $label" else "   $label") },
                                    onClick = { setColumns(c) },
                                )
                            }
                        }
                    }
                },
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
                    if (shuffleDialog) {
                        ShuffleDialog(
                            initial = shuffleOptions,
                            videoCount = s.entries.count { it.kind == MediaKind.VIDEO },
                            showReset = shuffled != null,
                            onDismiss = { shuffleDialog = false },
                            onReset = { shuffled = null; shuffleDialog = false },
                            onShuffle = { shuffleDialog = false; runShuffle(s.entries, it) },
                        )
                    }
                    val shown = shuffled ?: s.entries
                    val images = s.entries.filter { it.kind == MediaKind.IMAGE }
                    // 랜덤 정렬 중이면 섞인 순서 그대로 이어서 재생된다.
                    val videos = shown.filter { it.kind == MediaKind.VIDEO }
                    val subtitles = s.entries.filter { it.isSubtitle }
                    val open: (SmbEntry) -> Unit = { e ->
                        when (e.kind) {
                            MediaKind.FOLDER -> if (server.share.isBlank()) onOpenShare(e.name) else onOpenFolder(e)
                            MediaKind.IMAGE -> onOpenImage(images, images.indexOf(e))
                            MediaKind.VIDEO -> onOpenVideo(videos, videos.indexOf(e), subtitles)
                            MediaKind.OTHER -> Unit
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        shuffleProgress?.let { msg ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                        shuffled?.let { list ->
                            ShuffleBar(
                                count = list.size,
                                onPlay = { onOpenVideo(list, 0, subtitles) },
                                onReshuffle = { runShuffle(s.entries, shuffleOptions) },
                                onClear = { shuffled = null },
                            )
                        }
                        if (columns == 0) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(shown, key = { it.path + "|" + it.name }) { e ->
                                    ListItem(
                                        modifier = Modifier.clickable { open(e) },
                                        leadingContent = { Icon(iconOf(e), null) },
                                        headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = if (e.isDirectory) null else { { Text(formatSize(e.size)) } },
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(shown, key = { it.path + "|" + it.name }) { e ->
                                    Tile(server, e, compact = columns >= 4, onClick = { open(e) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun iconOf(e: SmbEntry): ImageVector = when (e.kind) {
    MediaKind.FOLDER -> Icons.Default.Folder
    MediaKind.VIDEO -> Icons.Default.Movie
    MediaKind.IMAGE -> Icons.Default.Image
    MediaKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
private fun ShuffleBar(count: Int, onPlay: () -> Unit, onReshuffle: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
        Text("랜덤 정렬 · ${count}개", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
        TextButton(onClick = onPlay) { Text("▶ 재생") }
        TextButton(onClick = onReshuffle) { Text("다시 섞기") }
        TextButton(onClick = onClear) { Text("해제") }
    }
}

/** 본 영상이면 썸네일 아래에 본 만큼 막대를 그린다. */
@Composable
private fun BoxScope.WatchedBar(server: ServerConfig, e: SmbEntry) {
    val r = remember(server, e.path) { WatchHistory.get(server, e) }
    if (r.playCount == 0) return
    val fraction = if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0.05f, 1f) else 1f
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(fraction)
            .height(4.dp)
            .background(MaterialTheme.colorScheme.primary),
    )
    Text(
        if (r.playCount > 1) "${r.playCount}회" else "봄",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 1:1 정사각형 칸. 사진과 영상은 썸네일, 나머지는 아이콘. */
@Composable
private fun Tile(server: ServerConfig, e: SmbEntry, compact: Boolean, onClick: () -> Unit) {
    val hasThumb = e.kind == MediaKind.IMAGE || e.kind == MediaKind.VIDEO
    val thumb by produceState(Thumbnails.cached(server, e), server, e.path) {
        if (value == null && hasThumb) value = Thumbnails.load(server, e)
    }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (e.kind == MediaKind.VIDEO) {
                    WatchedBar(server, e)
                    Icon(
                        Icons.Default.PlayCircle,
                        null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                    )
                }
            } else {
                Icon(
                    iconOf(e),
                    null,
                    tint = if (e.kind == MediaKind.FOLDER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.45f),
                )
            }
        }
        Text(
            e.name,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
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
