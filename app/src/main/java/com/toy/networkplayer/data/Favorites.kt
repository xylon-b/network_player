package com.toy.networkplayer.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** 하트(좋아요)를 누른 파일 하나. 다시 열 수 있게 서버와 위치를 같이 기억한다. */
data class Favorite(
    val name: String,
    val path: String,
    val size: Long,
    val serverId: String,
    val share: String,
    val addedAt: Long,
) {
    val entry: SmbEntry get() = SmbEntry(name = name, path = path, isDirectory = false, size = size)
}

/**
 * 좋아요 목록. 앱 내부 파일(favorites.json)에 저장한다.
 * 파일 이름이 같으면 같은 파일로 보고, 화면에서 바로 반영되도록 Compose 상태로 들고 있는다.
 */
object Favorites {
    private val items = mutableStateMapOf<String, Favorite>()
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "favorites.json")
        file = f
        runCatching {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val fav = Favorite(
                        name = o.getString("name"),
                        path = o.getString("path"),
                        size = o.optLong("size"),
                        serverId = o.getString("server"),
                        share = o.getString("share"),
                        addedAt = o.optLong("added"),
                    )
                    items[key(fav.name)] = fav
                }
            }
        }
    }

    private fun key(name: String) = name.lowercase()

    fun isFavorite(entry: SmbEntry): Boolean = items.containsKey(key(entry.name))

    /** 최근에 누른 것부터. */
    fun all(): List<Favorite> = items.values.sortedByDescending { it.addedAt }

    /** 좋아요를 켜거나 끈다. 켜졌으면 true. */
    fun toggle(server: ServerConfig, entry: SmbEntry): Boolean {
        val k = key(entry.name)
        val on = if (items.remove(k) != null) {
            false
        } else {
            items[k] = Favorite(entry.name, entry.path, entry.size, server.id, server.share, System.currentTimeMillis())
            true
        }
        save()
        return on
    }

    fun remove(fav: Favorite) {
        items.remove(key(fav.name))
        save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = items.values.toList()
        writer.execute {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach {
                    arr.put(
                        JSONObject()
                            .put("name", it.name)
                            .put("path", it.path)
                            .put("size", it.size)
                            .put("server", it.serverId)
                            .put("share", it.share)
                            .put("added", it.addedAt),
                    )
                }
                val tmp = File(f.parentFile, "favorites.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(f)
            }
        }
    }
}
