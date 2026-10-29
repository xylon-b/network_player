package com.toy.networkplayer

import com.toy.networkplayer.ui.FavoritesScreen
import com.toy.networkplayer.data.Favorites
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.ServerStore
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.Thumbnails
import com.toy.networkplayer.data.WatchHistory
import com.toy.networkplayer.ui.BrowserScreen
import com.toy.networkplayer.ui.BrowseMemory
import com.toy.networkplayer.ui.FavoritesMemory
import com.toy.networkplayer.ui.PhotoViewerScreen
import com.toy.networkplayer.ui.PlayerScreen
import com.toy.networkplayer.ui.ServerListScreen

/** 화면 스택. 맨 위가 지금 보이는 화면. */
sealed interface Screen {
    data object Servers : Screen
    // memory: 이 화면에서 다른 화면으로 갔다 돌아왔을 때 목록과 스크롤 위치를 그대로 보여주기 위한 보관함.
    data class Favorites(val memory: FavoritesMemory = FavoritesMemory()) : Screen
    data class Browse(val server: ServerConfig, val path: String, val memory: BrowseMemory = BrowseMemory()) : Screen
    data class Photos(val server: ServerConfig, val images: List<SmbEntry>, val start: Int) : Screen
    data class Player(
        val server: ServerConfig,
        val videos: List<SmbEntry>,
        val start: Int,
        val subtitles: List<SmbEntry>,
    ) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ServerStore(applicationContext)
        WatchHistory.init(applicationContext)
        Thumbnails.init(applicationContext)
        Favorites.init(applicationContext)

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val stack = remember { mutableStateListOf<Screen>(Screen.Servers) }
                fun push(s: Screen) = stack.add(s)
                fun pop() = stack.removeAt(stack.lastIndex)

                BackHandler(enabled = stack.size > 1) { pop() }

                // 화면마다 따로 상태를 갖도록 스택 항목으로 묶는다(폴더 A의 스크롤이 폴더 B로 새지 않게).
                val top = stack.last()
                key(top) {
                    when (top) {
                        Screen.Servers -> ServerListScreen(
                            store = store,
                            onOpen = { push(Screen.Browse(it, "")) },
                            onOpenFavorites = { push(Screen.Favorites()) },
                        )
                        is Screen.Favorites -> FavoritesScreen(
                            servers = store.load(),
                            onBack = { pop() },
                            onOpenImage = { server, images, index -> push(Screen.Photos(server, images, index)) },
                            onOpenVideo = { server, videos, index -> push(Screen.Player(server, videos, index, emptyList())) },
                            memory = top.memory,
                        )
                        is Screen.Browse -> BrowserScreen(
                            server = top.server,
                            path = top.path,
                            onBack = { pop() },
                            onOpenFolder = { push(Screen.Browse(top.server, it.path)) },
                            onOpenShare = { push(Screen.Browse(top.server.copy(share = it), "")) },
                            onOpenImage = { images, index -> push(Screen.Photos(top.server, images, index)) },
                            onOpenVideo = { videos, index, subs -> push(Screen.Player(top.server, videos, index, subs)) },
                            onOpenFavorites = { push(Screen.Favorites()) },
                            memory = top.memory,
                        )
                        is Screen.Photos -> PhotoViewerScreen(
                            server = top.server,
                            images = top.images,
                            start = top.start,
                            onBack = { pop() },
                        )
                        is Screen.Player -> PlayerScreen(
                            server = top.server,
                            videos = top.videos,
                            start = top.start,
                            subtitles = top.subtitles,
                            onBack = { pop() },
                        )
                    }
                }
            }
        }
    }
}
