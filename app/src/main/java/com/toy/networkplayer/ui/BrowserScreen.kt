@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.toy.networkplayer.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import android.view.HapticFeedbackConstants
import com.toy.networkplayer.data.Favorites
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Favorite
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
import kotlinx.coroutines.coroutineScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.toy.networkplayer.data.Thumbnails
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.DisposableEffect
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
import com.toy.networkplayer.data.FileOps
import com.toy.networkplayer.data.NaturalOrder

/** 폴더는 언제나 위에, 그 안에서 고른 기준으로 줄 세운다. */
enum class SortOrder(val label: String) {
    NAME("이름순 (가나다)"),
    NAME_DESC("이름 역순"),
    NEWEST("최신 날짜순"),
    OLDEST("오래된 날짜순"),
    LARGEST("큰 파일순"),
    SMALLEST("작은 파일순");

    fun apply(list: List<SmbEntry>): List<SmbEntry> {
        val byName = compareBy(NaturalOrder) { e: SmbEntry -> e.name }
        val inner: Comparator<SmbEntry> = when (this) {
            NAME -> byName
            NAME_DESC -> byName.reversed()
            NEWEST -> compareByDescending<SmbEntry> { it.modified }.then(byName)
            OLDEST -> compareBy<SmbEntry> { it.modified }.then(byName)
            // 폴더는 크기가 0이라 크기 정렬일 때는 이름순으로 둔다.
            LARGEST -> compareByDescending<SmbEntry> { it.size }.then(byName)
            SMALLEST -> compareBy<SmbEntry> { it.size }.then(byName)
        }
        return list.sortedWith(compareBy<SmbEntry> { !it.isDirectory }.then(inner))
    }

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: NAME
    }
}

internal val VIEW_OPTIONS = listOf(
    5 to "아이콘 아주 작게 (5열)",
    4 to "아이콘 작게 (4열)",
    3 to "아이콘 보통 (3열)",
    2 to "아이콘 크게 (2열)",
    0 to "목록으로 보기",
)

/**
 * 다른 화면(재생, 사진, 하위 폴더)에 갔다 와도 그대로 보이도록 폴더 화면이 들고 있는 것들.
 * 화면 스택의 항목마다 하나씩 붙어 있다.
 */
class BrowseMemory {
    var entries: List<SmbEntry>? = null
    var shuffled: List<SmbEntry>? = null
    val scroll = ScrollMemory()
}

/** 목록/바둑판의 스크롤 위치. */
class ScrollMemory {
    var listIndex = 0
    var listOffset = 0
    var gridIndex = 0
    var gridOffset = 0
}

@Composable
fun rememberListState(memory: ScrollMemory): LazyListState {
    val state = remember { LazyListState(memory.listIndex, memory.listOffset) }
    DisposableEffect(state) {
        onDispose {
            memory.listIndex = state.firstVisibleItemIndex
            memory.listOffset = state.firstVisibleItemScrollOffset
        }
    }
    return state
}

@Composable
fun rememberGridState(memory: ScrollMemory): LazyGridState {
    val state = remember { LazyGridState(memory.gridIndex, memory.gridOffset) }
    DisposableEffect(state) {
        onDispose {
            memory.gridIndex = state.firstVisibleItemIndex
            memory.gridOffset = state.firstVisibleItemScrollOffset
        }
    }
    return state
}

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
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    memory: BrowseMemory = remember { BrowseMemory() },
) {
    // 이미 읽어 둔 목록이 있으면 다시 읽지 않고 바로 보여준다(돌아왔을 때 스크롤 위치 유지).
    var state by remember(server, path) { mutableStateOf<LoadState>(memory.entries?.let { LoadState.Loaded(it) } ?: LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser", Context.MODE_PRIVATE) }
    // 0 = 목록, 2~5 = 한 줄에 놓을 칸 수
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 3)) }
    var menuOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortOrder.from(prefs.getString("sort", null))) }
    // 꾹 누른 파일(메뉴), 이름 바꾸는 중인 파일, 지우기 확인 중인 파일
    var actionTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var renameTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var working by remember { mutableStateOf(false) }
    // 여러 개 선택 모드. null이면 선택 모드가 아니고, 고른 항목의 selectKey 모음.
    var selection by remember(server, path) { mutableStateOf<Set<String>?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selection != null) { selection = null }

    // 랜덤 정렬 결과. null이면 원래 순서.
    var shuffled by remember(server, path) { mutableStateOf(memory.shuffled) }
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
    val startDownload = rememberDownloadStarter()

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
                // 이미 확인한 영상은 건너뛰고, 처음 보는 영상만 여러 개 동시에 읽는다.
                val unknown = videos.filter { !WatchHistory.hasDuration(it) }
                var done = 0
                if (unknown.isNotEmpty()) shuffleProgress = "영상 길이 확인 중 0/${unknown.size}"
                coroutineScope {
                    unknown.forEach { v ->
                        launch {
                            Thumbnails.probeDuration(server, v)
                            done++
                            shuffleProgress = "영상 길이 확인 중 $done/${unknown.size}"
                        }
                    }
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

    fun setSort(o: SortOrder) {
        sort = o
        prefs.edit().putString("sort", o.name).apply()
        menuOpen = false
    }

    /** 바꾸거나 지운 결과를 다시 읽지 않고 목록에 바로 반영한다. */
    fun replaceEntry(old: SmbEntry, new: SmbEntry?) {
        val loaded = (state as? LoadState.Loaded)?.entries ?: return
        val updated = if (new == null) loaded.filter { it != old } else loaded.map { if (it == old) new else it }
        memory.entries = updated
        state = LoadState.Loaded(updated)
        shuffled = shuffled?.let { list -> if (new == null) list.filter { it != old } else list.map { if (it == old) new else it } }
    }

    fun doRename(e: SmbEntry, newName: String) {
        renameTarget = null
        if (newName == e.name) return
        working = true
        scope.launch {
            runCatching { FileOps.rename(server, e, newName) }
                .onSuccess { replaceEntry(e, it); Toast.makeText(context, "이름을 바꿨어요", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "이름을 못 바꿨어요: ${it.describe()}", Toast.LENGTH_LONG).show() }
            working = false
        }
    }

    fun doDelete(e: SmbEntry) {
        deleteTarget = null
        working = true
        scope.launch {
            runCatching { FileOps.delete(server, e) }
                .onSuccess { replaceEntry(e, null); Toast.makeText(context, "지웠어요", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "못 지웠어요: ${it.describe()}", Toast.LENGTH_LONG).show() }
            working = false
        }
    }

    fun selectedEntries(): List<SmbEntry> {
        val keys = selection ?: return emptyList()
        return (state as? LoadState.Loaded)?.entries?.filter { selectKey(it) in keys } ?: emptyList()
    }

    fun doBulkDelete() {
        bulkDelete = false
        val targets = selectedEntries()
        selection = null
        working = true
        scope.launch {
            var failed = 0
            targets.forEach { e ->
                runCatching { FileOps.delete(server, e) }
                    .onSuccess { replaceEntry(e, null) }
                    .onFailure { failed++ }
            }
            Toast.makeText(context, if (failed == 0) "${targets.size}개를 지웠어요" else "${targets.size - failed}개 지움, ${failed}개는 못 지웠어요", Toast.LENGTH_LONG).show()
            working = false
        }
    }

    fun bulkLike() {
        val media = selectedEntries().filter { it.kind == MediaKind.VIDEO || it.kind == MediaKind.IMAGE }
        if (media.isEmpty()) {
            Toast.makeText(context, "좋아요는 영상과 사진에만 할 수 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        // 전부 좋아요 상태면 모두 취소, 아니면 아직 안 누른 것만 좋아요.
        val allLiked = media.all { Favorites.isFavorite(it) }
        media.filter { Favorites.isFavorite(it) == allLiked }.forEach { Favorites.toggle(server, it) }
        Toast.makeText(context, if (allLiked) "${media.size}개 좋아요 취소" else "♥ ${media.size}개 좋아요", Toast.LENGTH_SHORT).show()
        selection = null
    }

    LaunchedEffect(shuffled) { memory.shuffled = shuffled }

    LaunchedEffect(server, path, reload) {
        if (reload == 0 && memory.entries != null) return@LaunchedEffect
        state = LoadState.Loading
        state = runCatching {
            if (server.share.isBlank()) {
                // 공유 이름 없이 저장한 PC: 공유 폴더 목록을 첫 화면으로 보여준다.
                SmbRepository.listShares(server).map { SmbEntry(name = it, path = "", isDirectory = true, size = 0) }
            } else {
                SmbRepository.list(server, path)
            }
        }.fold({ memory.entries = it; LoadState.Loaded(it) }, { LoadState.Failed(it.describe()) })
    }

    Scaffold(
        bottomBar = {
            if (selection != null) {
                SelectionActionBar(
                    enabled = selection!!.isNotEmpty(),
                    actions = listOf(
                        SelectionAction(Icons.Default.Download, "내 폰에 받기") { startDownload(server, selectedEntries()); selection = null },
                        SelectionAction(Icons.Default.Favorite, "좋아요") { bulkLike() },
                        SelectionAction(Icons.Default.Delete, "지우기", danger = true) { bulkDelete = true },
                    ),
                )
            }
        },
        topBar = {
            val sel = selection
            if (sel != null) {
                val all = (state as? LoadState.Loaded)?.entries.orEmpty()
                SelectionTopBar(
                    count = sel.size,
                    total = all.size,
                    onClose = { selection = null },
                    onSelectAll = { on -> selection = if (on) all.map { selectKey(it) }.toSet() else emptySet() },
                )
            } else TopAppBar(
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
                    if (server.share.isNotBlank()) {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "검색") }
                    }
                    IconButton(onClick = onOpenFavorites) { Icon(Icons.Default.Favorite, "즐겨찾기") }
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
                            if (server.share.isNotBlank()) {
                                HorizontalDivider()
                                SortOrder.entries.forEach { o ->
                                    DropdownMenuItem(
                                        text = { Text(if (o == sort) "✓ ${o.label}" else "   ${o.label}") },
                                        onClick = { setSort(o) },
                                    )
                                }
                            }
                            if (server.share.isNotBlank()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("   여러 개 선택") },
                                    onClick = { menuOpen = false; selection = emptySet() },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("   다운로드 목록") },
                                onClick = { menuOpen = false; onOpenDownloads() },
                            )
                            DropdownMenuItem(
                                text = { Text("   썸네일 저장 비우기 (${Thumbnails.diskUsage() / (1024 * 1024)}MB)") },
                                onClick = {
                                    Thumbnails.clearDisk()
                                    menuOpen = false
                                    reload++
                                    Toast.makeText(context, "썸네일을 비웠어요. 다시 만들어요", Toast.LENGTH_SHORT).show()
                                },
                            )
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
                    val sorted = remember(s.entries, sort) { if (server.share.isBlank()) s.entries else sort.apply(s.entries) }
                    val shown = shuffled ?: sorted
                    val images = sorted.filter { it.kind == MediaKind.IMAGE }
                    // 랜덤 정렬 중이면 섞인 순서 그대로 이어서 재생된다.
                    val videos = shown.filter { it.kind == MediaKind.VIDEO }
                    val subtitles = s.entries.filter { it.isSubtitle }
                    val toggle: (SmbEntry) -> Unit = { e ->
                        val k = selectKey(e)
                        selection = selection?.let { if (k in it) it - k else it + k }
                    }
                    val open: (SmbEntry) -> Unit = { e ->
                        if (selection != null) toggle(e) else when (e.kind) {
                            MediaKind.FOLDER -> if (server.share.isBlank()) onOpenShare(e.name) else onOpenFolder(e)
                            MediaKind.IMAGE -> onOpenImage(images, images.indexOf(e))
                            MediaKind.VIDEO -> onOpenVideo(videos, videos.indexOf(e), subtitles)
                            MediaKind.OTHER -> Unit
                        }
                    }
                    // 꾹 누르면 좋아요/이름 바꾸기/지우기 메뉴. 공유 목록 화면에서는 기존처럼 아무것도 안 한다.
                    val like: (SmbEntry) -> Unit = { e ->
                        if (selection != null) {
                            toggle(e)
                        } else if (server.share.isNotBlank()) {
                            (context as? android.app.Activity)?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            actionTarget = e
                        }
                    }
                    actionTarget?.let { e ->
                        FileActionDialog(
                            entry = e,
                            onDismiss = { actionTarget = null },
                            onLike = { actionTarget = null; toggleFavorite(context, server, e) },
                            onRename = { actionTarget = null; renameTarget = e },
                            onDelete = { actionTarget = null; deleteTarget = e },
                            onDownload = { actionTarget = null; startDownload(server, listOf(e)) },
                            onSelect = { actionTarget = null; selection = setOf(selectKey(e)) },
                        )
                    }
                    renameTarget?.let { e ->
                        RenameDialog(entry = e, onDismiss = { renameTarget = null }, onConfirm = { doRename(e, it) })
                    }
                    deleteTarget?.let { e ->
                        DeleteDialog(entry = e, onDismiss = { deleteTarget = null }, onConfirm = { doDelete(e) })
                    }
                    if (bulkDelete) {
                        BulkDeleteDialog(selection?.size ?: 0, local = false, onDismiss = { bulkDelete = false }, onConfirm = { doBulkDelete() })
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (working) {
                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
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
                            LazyColumn(Modifier.fillMaxSize(), state = rememberListState(memory.scroll)) {
                                items(shown, key = { it.path + "|" + it.name }) { e ->
                                    val sel = selection
                                    ListItem(
                                        modifier = Modifier.combinedClickable(onLongClick = { like(e) }) { open(e) },
                                        leadingContent = { Icon(iconOf(e), null) },
                                        trailingContent = when {
                                            sel != null -> { { SelectMark(selectKey(e) in sel) } }
                                            Favorites.isFavorite(e) -> { { Icon(Icons.Default.Favorite, "좋아요", tint = HEART) } }
                                            else -> null
                                        },
                                        headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = if (e.isDirectory) null else { { Text(sizeAndDuration(e)) } },
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                state = rememberGridState(memory.scroll),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(shown, key = { it.path + "|" + it.name }) { e ->
                                    Tile(server, e, compact = columns >= 4, onClick = { open(e) }, onLongClick = { like(e) }, selected = selection?.let { selectKey(e) in it })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun iconOf(e: SmbEntry): ImageVector = when (e.kind) {
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
fun Tile(
    server: ServerConfig,
    e: SmbEntry,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** null이면 선택 모드가 아님. */
    selected: Boolean? = null,
) {
    val hasThumb = e.kind == MediaKind.IMAGE || e.kind == MediaKind.VIDEO
    val thumb by produceState(Thumbnails.cached(server, e), server, e.path) {
        if (value == null && hasThumb) value = Thumbnails.load(server, e)
    }
    // 썸네일을 만들 때 영상 길이도 같이 기록되므로 썸네일이 생기면 다시 읽는다.
    val durationMs = remember(e.path, thumb) { if (e.kind == MediaKind.VIDEO) WatchHistory.duration(e) else 0L }
    Column(Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
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
                    if (durationMs > 0) {
                        Text(
                            formatDuration(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 8.dp)
                                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            } else {
                Icon(
                    iconOf(e),
                    null,
                    tint = if (e.kind == MediaKind.FOLDER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.45f),
                )
            }
            if (Favorites.isFavorite(e)) {
                Icon(
                    Icons.Default.Favorite,
                    "좋아요",
                    tint = HEART,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(if (compact) 16.dp else 22.dp),
                )
            }
            if (selected != null) {
                if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
                SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(4.dp).size(if (compact) 20.dp else 26.dp))
            }
        }
        Text(
            e.name,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (!e.isDirectory && e.size > 0) {
            Text(
                formatSize(e.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** 선택 모드에서 칸/줄 오른쪽 위에 보이는 동그란 체크. */
@Composable
fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        if (selected) "선택됨" else "선택 안 됨",
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = modifier.background(Color(0x55000000), CircleShape),
    )
}

/** 목록 보기의 보조 줄: "500MB · 42분" */
fun sizeAndDuration(e: SmbEntry): String {
    val d = if (e.kind == MediaKind.VIDEO) WatchHistory.duration(e) else 0L
    return if (d > 0) formatSize(e.size) + " · " + formatDuration(d) else formatSize(e.size)
}

/** 영상 길이를 짧게: 45초, 12분, 1시간 5분. */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "${h}시간 ${m}분" else "${h}시간"
        m > 0 -> "${m}분"
        else -> "${totalSec}초"
    }
}

val HEART = Color(0xFFFF4D6D)

/** 사진·영상만 좋아요를 누를 수 있다. 눌렀다는 걸 진동과 짧은 안내로 알려준다. */
fun toggleFavorite(context: Context, server: ServerConfig, e: SmbEntry) {
    if (e.kind != MediaKind.VIDEO && e.kind != MediaKind.IMAGE) return
    val on = Favorites.toggle(server, e)
    (context as? android.app.Activity)?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    Toast.makeText(context, if (on) "♥ 좋아요" else "좋아요 취소", Toast.LENGTH_SHORT).show()
}

/** 사람이 한눈에 보기 쉬운 용량: 1.1GB, 500MB, 820KB. */
fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024
    fun oneDecimal(v: Double) = if (v >= 10) "%.0f".format(v) else "%.1f".format(v).removeSuffix(".0")
    return when {
        bytes >= tb -> oneDecimal(bytes / tb) + "TB"
        bytes >= gb -> oneDecimal(bytes / gb) + "GB"
        bytes >= mb -> "%.0f".format(bytes / mb) + "MB"
        bytes >= kb -> "%.0f".format(bytes / kb) + "KB"
        else -> "${bytes}B"
    }
}
