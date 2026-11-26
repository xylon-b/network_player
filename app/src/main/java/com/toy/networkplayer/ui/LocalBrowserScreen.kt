@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.toy.networkplayer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.toy.networkplayer.data.Favorites
import com.toy.networkplayer.data.FileOps
import com.toy.networkplayer.data.LocalFiles
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.describe
import kotlinx.coroutines.launch
import java.io.File

/** 내 폰 폴더 화면이 다른 화면에 갔다 와도 그대로 보이도록 들고 있는 것들. */
class LocalMemory {
    var entries: List<SmbEntry>? = null
    val scroll = ScrollMemory()

    /** 옮길 폴더를 고르고 돌아왔을 때 처리할 이동. (옮길 항목, 옮길 폴더) */
    var pendingMove: Pair<List<SmbEntry>, String>? = null
}

private sealed interface LocalState {
    data object Loading : LocalState
    data class Loaded(val entries: List<SmbEntry>) : LocalState
    data class Failed(val message: String) : LocalState
}

/**
 * 저장공간 권한이 있을 때만 [content]를 보여주고, 없으면 왜 필요한지 설명하고 허용 화면으로 보낸다.
 * 설정에서 허용하고 돌아오면 바로 다시 확인한다.
 */
@Composable
fun StorageAccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalFiles.hasAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = LocalFiles.hasAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = LocalFiles.hasAccess(context)
    }
    if (granted) {
        content()
        return
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.SdStorage, null, modifier = Modifier.padding(bottom = 4.dp))
            Text("휴대폰 파일에 접근하려면 허용이 필요해요", style = MaterialTheme.typography.titleMedium)
            Text(
                "폴더를 보고, 이름을 바꾸고, 지우고, 공유폴더에서 받은 영상을 저장하는 데만 써요. " +
                    "다음 화면에서 '모든 파일 접근 권한 허용'을 켜고 돌아와 주세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    runCatching { context.startActivity(LocalFiles.allFilesAccessIntent(context)) }.onFailure {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }
                } else {
                    legacyLauncher.launch(LocalFiles.legacyPermissions)
                }
            }) { Text("허용하러 가기") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBrowserScreen(
    path: String,
    memory: LocalMemory,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenImage: (images: List<SmbEntry>, index: Int) -> Unit,
    onOpenVideo: (videos: List<SmbEntry>, index: Int, subtitles: List<SmbEntry>) -> Unit,
    onPickMoveTarget: (entries: List<SmbEntry>, start: String) -> Unit,
) {
    val server = ServerConfig.LOCAL
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser", Context.MODE_PRIVATE) }
    var state by remember(path) { mutableStateOf<LocalState>(memory.entries?.let { LocalState.Loaded(it) } ?: LocalState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var columns by remember { mutableIntStateOf(prefs.getInt("local_columns", 0)) }
    var sort by remember { mutableStateOf(SortOrder.from(prefs.getString("local_sort", null))) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("local_hidden", false)) }
    var menuOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var renameTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    // 여러 개 선택 모드. null이면 선택 모드 아님. 값은 고른 항목의 경로.
    var selection by remember(path) { mutableStateOf<Set<String>?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selection != null) { selection = null }
    val scope = rememberCoroutineScope()

    fun toast(msg: String, long: Boolean = false) =
        Toast.makeText(context, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    fun setEntries(list: List<SmbEntry>) {
        memory.entries = list
        state = LocalState.Loaded(list)
    }

    fun current() = (state as? LocalState.Loaded)?.entries

    fun doRename(e: SmbEntry, newName: String) {
        renameTarget = null
        if (newName == e.name) return
        working = true
        scope.launch {
            runCatching { LocalFiles.rename(e, newName) }
                .onSuccess { n -> current()?.let { setEntries(it.map { x -> if (x == e) n else x }) }; toast("이름을 바꿨어요") }
                .onFailure { toast("이름을 못 바꿨어요: ${it.describe()}", true) }
            working = false
        }
    }

    fun doDelete(e: SmbEntry) {
        deleteTarget = null
        working = true
        scope.launch {
            runCatching { LocalFiles.delete(e) }
                .onSuccess { current()?.let { setEntries(it.filter { x -> x != e }) }; toast("지웠어요") }
                .onFailure { toast("못 지웠어요: ${it.describe()}", true); reload++ }
            working = false
        }
    }

    fun doCreate(name: String) {
        newFolder = false
        working = true
        scope.launch {
            runCatching { LocalFiles.createFolder(path, name) }
                .onSuccess { n -> current()?.let { setEntries(it + n) }; toast("폴더를 만들었어요") }
                .onFailure { toast("폴더를 못 만들었어요: ${it.describe()}", true) }
            working = false
        }
    }

    fun selectedEntries(): List<SmbEntry> {
        val keys = selection ?: return emptyList()
        return current()?.filter { it.path in keys } ?: emptyList()
    }

    fun doBulkDelete() {
        bulkDelete = false
        val targets = selectedEntries()
        selection = null
        working = true
        scope.launch {
            var failed = 0
            targets.forEach { e ->
                runCatching { LocalFiles.delete(e) }
                    .onSuccess { current()?.let { setEntries(it.filter { x -> x != e }) } }
                    .onFailure { failed++ }
            }
            toast(if (failed == 0) "${targets.size}개를 지웠어요" else "${targets.size - failed}개 지움, ${failed}개는 못 지웠어요", failed > 0)
            working = false
        }
    }

    fun bulkLike() {
        val media = selectedEntries().filter { it.kind == MediaKind.VIDEO || it.kind == MediaKind.IMAGE }
        if (media.isEmpty()) {
            toast("좋아요는 영상과 사진에만 할 수 있어요")
            return
        }
        val allLiked = media.all { Favorites.isFavorite(it) }
        media.filter { Favorites.isFavorite(it) == allLiked }.forEach { Favorites.toggle(server, it) }
        toast(if (allLiked) "${media.size}개 좋아요 취소" else "♥ ${media.size}개 좋아요")
        selection = null
    }

    // 옮길 폴더를 고르고 돌아왔으면 옮긴다.
    LaunchedEffect(Unit) {
        val (targets, dir) = memory.pendingMove ?: return@LaunchedEffect
        memory.pendingMove = null
        working = true
        var failed: Throwable? = null
        var moved = 0
        targets.forEach { e ->
            runCatching { LocalFiles.move(e, dir) }
                .onSuccess { moved++; if (it.path != e.path) current()?.let { list -> setEntries(list.filter { x -> x != e }) } }
                .onFailure { failed = it }
        }
        val err = failed
        when {
            err == null -> toast("${LocalFiles.pretty(dir)}(으)로 ${if (targets.size > 1) "${moved}개를 " else ""}옮겼어요")
            moved == 0 -> toast("못 옮겼어요: ${err.describe()}", true)
            else -> toast("${moved}개 옮김, ${targets.size - moved}개는 못 옮겼어요: ${err.describe()}", true)
        }
        working = false
    }

    LaunchedEffect(path, reload, showHidden) {
        if (reload == 0 && memory.entries != null) return@LaunchedEffect
        if (memory.entries == null) state = LocalState.Loading
        state = runCatching { LocalFiles.list(path, showHidden) }
            .fold({ memory.entries = it; LocalState.Loaded(it) }, { LocalState.Failed(it.describe()) })
    }

    val isRoot = path == LocalFiles.root.absolutePath
    Scaffold(
        bottomBar = {
            val sel = selection
            if (sel != null) {
                SelectionActionBar(
                    enabled = sel.isNotEmpty(),
                    actions = listOf(
                        SelectionAction(Icons.Default.DriveFileMove, "옮기기") { val t = selectedEntries(); selection = null; onPickMoveTarget(t, path) },
                        SelectionAction(Icons.Default.Share, "공유") { shareFiles(context, selectedEntries().filter { !it.isDirectory }) },
                        SelectionAction(Icons.Default.Favorite, "좋아요") { bulkLike() },
                        SelectionAction(Icons.Default.Delete, "지우기", danger = true) { bulkDelete = true },
                    ),
                )
            }
        },
        topBar = {
            val sel = selection
            if (sel != null) {
                val all = current().orEmpty()
                SelectionTopBar(
                    count = sel.size,
                    total = all.size,
                    onClose = { selection = null },
                    onSelectAll = { on -> selection = if (on) all.map { it.path }.toSet() else emptySet() },
                )
            } else TopAppBar(
                title = {
                    Column {
                        Text(if (isRoot) "내 폰" else File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!isRoot) {
                            Text(LocalFiles.pretty(path), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = { newFolder = true }) { Icon(Icons.Default.CreateNewFolder, "새 폴더") }
                    IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "새로고침") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(if (columns == 0) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, "보기 방식")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            VIEW_OPTIONS.forEach { (c, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (c == columns) "✓ $label" else "   $label") },
                                    onClick = { columns = c; prefs.edit().putInt("local_columns", c).apply(); menuOpen = false },
                                )
                            }
                            HorizontalDivider()
                            SortOrder.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(if (o == sort) "✓ ${o.label}" else "   ${o.label}") },
                                    onClick = { sort = o; prefs.edit().putString("local_sort", o.name).apply(); menuOpen = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("   여러 개 선택") },
                                onClick = { menuOpen = false; selection = emptySet() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (showHidden) "✓ 숨김 파일 보기" else "   숨김 파일 보기") },
                                onClick = {
                                    showHidden = !showHidden
                                    prefs.edit().putBoolean("local_hidden", showHidden).apply()
                                    memory.entries = null
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StorageAccessGate {
                when (val s = state) {
                    LocalState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is LocalState.Failed -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("폴더를 열 수 없어요\n${s.message}")
                        Button(onClick = { reload++ }) { Text("다시 시도") }
                    }
                    is LocalState.Loaded -> {
                        if (s.entries.isEmpty()) Text("빈 폴더예요", Modifier.align(Alignment.Center))
                        val shown = remember(s.entries, sort) { sort.apply(s.entries) }
                        val images = shown.filter { it.kind == MediaKind.IMAGE }
                        val videos = shown.filter { it.kind == MediaKind.VIDEO }
                        val subtitles = s.entries.filter { it.isSubtitle }
                        val toggle: (SmbEntry) -> Unit = { e ->
                            selection = selection?.let { if (e.path in it) it - e.path else it + e.path }
                        }
                        val open: (SmbEntry) -> Unit = { e ->
                            if (selection != null) toggle(e) else when (e.kind) {
                                MediaKind.FOLDER -> onOpenFolder(e.path)
                                MediaKind.IMAGE -> onOpenImage(images, images.indexOf(e))
                                MediaKind.VIDEO -> onOpenVideo(videos, videos.indexOf(e), subtitles)
                                MediaKind.OTHER -> openWithOtherApp(context, e)
                            }
                        }
                        val longPress: (SmbEntry) -> Unit = { e ->
                            if (selection != null) {
                                toggle(e)
                            } else {
                                (context as? android.app.Activity)?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                actionTarget = e
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (columns == 0) {
                                LazyColumn(Modifier.fillMaxSize(), state = rememberListState(memory.scroll)) {
                                    items(shown, key = { it.path }) { e ->
                                        ListItem(
                                            modifier = Modifier.combinedClickable(onLongClick = { longPress(e) }, selected = selection?.let { e.path in it }) { open(e) },
                                            leadingContent = { Icon(iconOf(e), null, tint = if (e.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                            trailingContent = when {
                                                selection != null -> { { SelectMark(e.path in selection.orEmpty()) } }
                                                Favorites.isFavorite(e) -> { { Icon(Icons.Default.Favorite, "좋아요", tint = HEART) } }
                                                else -> null
                                            },
                                            headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                            supportingContent = {
                                                Text(
                                                    buildString {
                                                        append(formatDate(e.modified))
                                                        if (!e.isDirectory) append(" · ").append(sizeAndDuration(e))
                                                    },
                                                )
                                            },
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
                                    items(shown, key = { it.path }) { e ->
                                        Tile(server, e, compact = columns >= 4, onClick = { open(e) }, onLongClick = { longPress(e) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { e ->
        LocalFileActionDialog(
            entry = e,
            onDismiss = { actionTarget = null },
            onLike = { actionTarget = null; toggleFavorite(context, server, e) },
            onRename = { actionTarget = null; renameTarget = e },
            onMove = { actionTarget = null; onPickMoveTarget(listOf(e), path) },
            onSelect = { actionTarget = null; selection = setOf(e.path) },
            onShare = { actionTarget = null; shareFile(context, e) },
            onOpenWith = { actionTarget = null; openWithOtherApp(context, e) },
            onDelete = { actionTarget = null; deleteTarget = e },
        )
    }
    renameTarget?.let { e -> RenameDialog(entry = e, onDismiss = { renameTarget = null }, onConfirm = { doRename(e, it) }) }
    deleteTarget?.let { e -> DeleteDialog(entry = e, onDismiss = { deleteTarget = null }, onConfirm = { doDelete(e) }, local = true) }
    if (bulkDelete) BulkDeleteDialog(selection?.size ?: 0, local = true, onDismiss = { bulkDelete = false }, onConfirm = { doBulkDelete() })
    if (newFolder) NewFolderDialog(onDismiss = { newFolder = false }, onConfirm = { doCreate(it) })
}

/** 내 폰 파일을 꾹 눌렀을 때 뜨는 메뉴. */
@Composable
private fun LocalFileActionDialog(
    entry: SmbEntry,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    val canLike = entry.kind == MediaKind.VIDEO || entry.kind == MediaKind.IMAGE
    val liked = Favorites.isFavorite(entry)
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    @Composable
    fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
        ListItem(
            colors = colors,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            leadingContent = { if (tint != null) Icon(icon, null, tint = tint) else Icon(icon, null) },
            headlineContent = { if (tint == MaterialTheme.colorScheme.error) Text(label, color = tint) else Text(label) },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (canLike) Action(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "좋아요 취소" else "좋아요", HEART, onLike)
                Action(Icons.Default.CheckCircle, "여러 개 선택하기", onClick = onSelect)
                Action(Icons.Default.DriveFileRenameOutline, "이름 바꾸기", onClick = onRename)
                Action(Icons.Default.DriveFileMove, "다른 폴더로 옮기기", onClick = onMove)
                if (!entry.isDirectory) {
                    Action(Icons.Default.Share, "공유하기", onClick = onShare)
                    Action(Icons.Default.OpenInNew, "다른 앱으로 열기", onClick = onOpenWith)
                }
                Action(Icons.Default.Delete, "지우기", MaterialTheme.colorScheme.error, onDelete)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("새 폴더") }
    val focus = remember { FocusRequester() }
    val problem = FileOps.checkName(name.trim())
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 폴더") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (problem == null) onConfirm(name.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = problem == null) { Text("만들기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * 폴더 고르기(다운로드 저장 위치, 옮길 곳). 폴더만 보이고, 들어가서 '이 폴더 선택'을 누르면 끝난다.
 * 뒤로 가기는 상위 폴더로, 맨 위에서 뒤로 가면 취소.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(title: String, start: String, confirmLabel: String, onPick: (String) -> Unit, onCancel: () -> Unit) {
    val rootPath = LocalFiles.root.absolutePath
    var dir by remember {
        // 처음 열 폴더가 아직 없으면(기본 다운로드 폴더 등) 있는 상위 폴더부터 연다.
        var f: File? = File(start)
        while (f != null && !f.isDirectory) f = f.parentFile
        mutableStateOf(f?.absolutePath?.takeIf { it.startsWith(rootPath) } ?: rootPath)
    }
    var folders by remember { mutableStateOf<List<SmbEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val atRoot = dir == rootPath

    fun up() {
        if (atRoot) onCancel() else dir = LocalFiles.parent(dir) ?: rootPath
    }
    BackHandler { up() }

    LaunchedEffect(dir, reload) {
        folders = null
        error = null
        runCatching { LocalFiles.list(dir, showHidden = false).filter { it.isDirectory } }
            .onSuccess { folders = it }
            .onFailure { error = it.describe() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1)
                        Text(LocalFiles.pretty(dir), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = { up() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "상위 폴더") } },
                actions = { IconButton(onClick = { newFolder = true }) { Icon(Icons.Default.CreateNewFolder, "새 폴더") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("취소") }
                    Button(onClick = { onPick(dir) }) { Text(confirmLabel) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StorageAccessGate {
                val list = folders
                when {
                    error != null -> Text("폴더를 열 수 없어요\n$error", Modifier.align(Alignment.Center).padding(24.dp))
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    list.isEmpty() -> Text("하위 폴더가 없어요. 이 폴더를 고르거나 새 폴더를 만드세요.", Modifier.align(Alignment.Center).padding(24.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.path }) { e ->
                            ListItem(
                                modifier = Modifier.clickable { dir = e.path },
                                leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (newFolder) {
        NewFolderDialog(onDismiss = { newFolder = false }, onConfirm = { name ->
            newFolder = false
            scope.launch {
                runCatching { LocalFiles.createFolder(dir, name) }
                    .onSuccess { dir = it.path }
                    .onFailure { Toast.makeText(context, "폴더를 못 만들었어요: ${it.describe()}", Toast.LENGTH_LONG).show() }
            }
        })
    }
}

private fun uriOf(context: Context, e: SmbEntry) =
    FileProvider.getUriForFile(context, context.packageName + ".files", File(e.path))

private fun mimeOf(e: SmbEntry): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(e.name.substringAfterLast('.', "").lowercase()) ?: "*/*"

fun openWithOtherApp(context: Context, e: SmbEntry) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriOf(context, e), mimeOf(e))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "다른 앱으로 열기"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "이 파일을 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFiles(context: Context, files: List<SmbEntry>) {
    if (files.isEmpty()) {
        Toast.makeText(context, "폴더는 공유할 수 없어요. 파일을 골라주세요", Toast.LENGTH_SHORT).show()
        return
    }
    if (files.size == 1) return shareFile(context, files[0])
    val uris = ArrayList(files.map { uriOf(context, it) })
    val types = files.map { mimeOf(it) }.distinct()
    val type = types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType(type)
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "공유하기")) }
}

private fun shareFile(context: Context, e: SmbEntry) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeOf(e))
        .putExtra(Intent.EXTRA_STREAM, uriOf(context, e))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "공유하기")) }
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "" else java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA).format(java.util.Date(ms))
