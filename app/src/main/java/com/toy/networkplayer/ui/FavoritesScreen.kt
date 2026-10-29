package com.toy.networkplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.Favorites
import com.toy.networkplayer.data.MediaKind
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry

private val FILTERS = listOf("전체", "영상", "사진")

/** 하트를 누른 사진·영상만 모아 보는 화면. 꾹 누르면 목록에서 뺀다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    servers: List<ServerConfig>,
    onBack: () -> Unit,
    onOpenImage: (server: ServerConfig, images: List<SmbEntry>, index: Int) -> Unit,
    onOpenVideo: (server: ServerConfig, videos: List<SmbEntry>, index: Int) -> Unit,
    memory: FavoritesMemory = remember { FavoritesMemory() },
) {
    val context = LocalContext.current
    val columns = remember { context.getSharedPreferences("browser", android.content.Context.MODE_PRIVATE).getInt("columns", 3) }
    var filter by remember { mutableIntStateOf(memory.filter) }
    val byId = remember(servers) { servers.associateBy { it.id } }

    // 서버가 지워진 항목은 열 수 없으니 뺀다.
    val all = Favorites.all().mapNotNull { f -> byId[f.serverId]?.let { s -> s.copy(share = f.share) to f.entry } }
    val shown = all.filter { (_, e) ->
        when (filter) {
            1 -> e.kind == MediaKind.VIDEO
            2 -> e.kind == MediaKind.IMAGE
            else -> true
        }
    }

    fun open(server: ServerConfig, e: SmbEntry) {
        // 같은 공유폴더에 있는 즐겨찾기끼리 이어서 넘겨 볼 수 있게 묶는다.
        val same = all.filter { (s, x) -> s.id == server.id && s.share == server.share && x.kind == e.kind }.map { it.second }
        when (e.kind) {
            MediaKind.VIDEO -> onOpenVideo(server, same, same.indexOf(e))
            MediaKind.IMAGE -> onOpenImage(server, same, same.indexOf(e))
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("즐겨찾기") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTERS.forEachIndexed { i, label ->
                    FilterChip(selected = filter == i, onClick = { filter = i; memory.filter = i }, label = { Text(label) })
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("파일을 꾹 누르거나 재생 화면의 ♥를 눌러 추가하세요", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (columns == 0) 3 else columns),
                    modifier = Modifier.fillMaxSize(),
                    state = rememberGridState(memory.scroll),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shown, key = { it.second.name }) { (server, e) ->
                        Tile(
                            server,
                            e,
                            compact = columns >= 4,
                            onClick = { open(server, e) },
                            onLongClick = { toggleFavorite(context, server, e) },
                        )
                    }
                }
            }
        }
    }
}

/** 재생 화면에 갔다 와도 필터와 스크롤 위치를 그대로 두기 위한 보관함. */
class FavoritesMemory {
    var filter = 0
    val scroll = ScrollMemory()
}
