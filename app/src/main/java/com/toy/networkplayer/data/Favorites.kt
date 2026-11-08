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

    /** 이름이 바뀐 파일/폴더를 따라간다. 폴더면 그 안에 든 좋아요의 경로도 고친다. */
    fun renamed(server: ServerConfig, old: SmbEntry, newPath: String) {
        var changed = false
        val newName = newPath.substringAfterLast('\\')
        items.values.filter { it.serverId == server.id && it.share == server.share }.forEach { f ->
            when {
                !old.isDirectory && key(f.name) == key(old.name) -> {
                    items.remove(key(f.name))
                    items[key(newName)] = f.copy(name = newName, path = newPath)
                    changed = true
                }
                old.isDirectory && f.path.lowercase().startsWith(old.path.lowercase() + "\\") -> {
                    items[key(f.name)] = f.copy(path = newPath + f.path.substring(old.path.length))
                    changed = true
                }
            }
        }
        if (changed) save()
    }

    /** 지운 파일/폴더의 좋아요를 뺀다. */
    fun deleted(server: ServerConfig, entry: SmbEntry) {
        val gone = items.values.filter {
            if (entry.isDirectory) {
                it.serverId == server.id && it.share == server.share && it.path.lowercase().startsWith(entry.path.lowercase() + "\\")
            } else {
                key(it.name) == key(entry.name)
            }
        }
        if (gone.isEmpty()) return
        gone.forEach { items.remove(key(it.name)) }
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
