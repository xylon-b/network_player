package com.toy.networkplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.SmbRepository
import com.toy.networkplayer.data.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 검색 화면이 들고 있는 것들. 화면 스택 항목에 붙어 있어서
 * 결과에서 영상을 보고 돌아와도 검색어, 결과, 스크롤 위치가 그대로 남는다.
 * 검색은 화면이 잠시 가려져도 끝까지 이어서 돈다.
 */
class SearchMemory {
    var query by mutableStateOf("")
    var searchedFor by mutableStateOf("")
    val results = mutableStateListOf<SmbEntry>()
    // 결과 영상에 같은 이름 자막을 붙이기 위해 지나가며 본 자막 파일도 모아 둔다.
    val subtitles = mutableListOf<SmbEntry>()
    var foldersScanned by mutableIntStateOf(0)
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val scroll = ScrollMemory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun start(server: ServerConfig, root: String) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        stop()
        results.clear()
        subtitles.clear()
        foldersScanned = 0
        error = null
        scroll.gridIndex = 0
        scroll.gridOffset = 0
        searchedFor = query.trim()
        if (words.isEmpty()) return
        running = true
        job = scope.launch {
            // 지금 폴더부터 하위 폴더까지 차례로 훑는다. 읽을 수 없는 폴더는 건너뛴다.
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val dir = queue.removeFirst()
                val entries = runCatching { SmbRepository.list(server, dir) }
                    .onFailure { if (dir == root) error = it.describe() }
                    .getOrNull() ?: continue
                foldersScanned++
                for (e in entries) {
                    if (e.isDirectory) queue.add(e.path)
                    if (e.isSubtitle) subtitles.add(e)
                    val name = e.name.lowercase()
                    if (!e.isSubtitle && e.kind != MediaKind.OTHER && words.all { it in name }) results.add(e)
                }
            }
            running = false
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        running = false
    }

    fun dispose() = scope.cancel()
}

/** 지금 폴더와 그 아래 폴더에서 이름에 검색어가 들어간 폴더·영상·사진을 찾는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    server: ServerConfig,
    path: String,
    memory: SearchMemory,
    onBack: () -> Unit,
    onOpenFolder: (SmbEntry) -> Unit,
    onOpenImage: (images: List<SmbEntry>, index: Int) -> Unit,
    onOpenVideo: (videos: List<SmbEntry>, index: Int, subtitles: List<SmbEntry>) -> Unit,
) {
    val context = LocalContext.current
    val columns = remember { context.getSharedPreferences("browser", android.content.Context.MODE_PRIVATE).getInt("columns", 3) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val where = if (path.isBlank()) server.share else path.substringAfterLast('\\')

    // 처음 열었을 때만 바로 입력할 수 있게 한다. 돌아왔을 때는 결과를 그대로 보여준다.
    LaunchedEffect(Unit) { if (memory.searchedFor.isEmpty()) focus.requestFocus() }

    fun search() {
        keyboard?.hide()
        memory.start(server, path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                title = {
                    TextField(
                        value = memory.query,
                        onValueChange = { memory.query = it },
                        placeholder = { Text("$where 에서 검색") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        trailingIcon = if (memory.query.isNotEmpty()) {
                            { IconButton(onClick = { memory.query = ""; focus.requestFocus() }) { Icon(Icons.Default.Close, "지우기") } }
                        } else null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = { IconButton(onClick = { search() }) { Icon(Icons.Default.Search, "검색") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (memory.searchedFor.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (memory.running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        if (memory.running) {
                            "  찾는 중 · ${memory.results.size}개 (폴더 ${memory.foldersScanned}개 확인)"
                        } else {
                            "'${memory.searchedFor}' ${memory.results.size}개 (폴더 ${memory.foldersScanned}개)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (memory.running) TextButton(onClick = { memory.stop() }) { Text("그만") }
                }
            }
            val results = memory.results.toList()
            when {
                memory.error != null && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(memory.error!!, style = MaterialTheme.typography.bodyMedium)
                }
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            memory.searchedFor.isEmpty() -> "파일 이름의 일부를 입력하세요\n이 폴더와 하위 폴더에서 찾아요"
                            memory.running -> "찾는 중…"
                            else -> "찾는 결과가 없어요"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    val images = results.filter { it.kind == MediaKind.IMAGE }
                    val videos = results.filter { it.kind == MediaKind.VIDEO }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (columns == 0) 3 else columns),
                        modifier = Modifier.fillMaxSize(),
                        state = rememberGridState(memory.scroll),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(results, key = { it.path }) { e ->
                            Tile(
                                server,
                                e,
                                compact = columns >= 4,
                                onClick = {
                                    when (e.kind) {
                                        MediaKind.FOLDER -> onOpenFolder(e)
                                        MediaKind.IMAGE -> onOpenImage(images, images.indexOf(e))
                                        MediaKind.VIDEO -> onOpenVideo(videos, videos.indexOf(e), memory.subtitles.toList())
                                        MediaKind.OTHER -> Unit
                                    }
                                },
                                onLongClick = { toggleFavorite(context, server, e) },
                            )
                        }
                    }
                }
            }
        }
    }
}
