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
    val isDirectory: Boolean = false,
) {
    val entry: SmbEntry get() = SmbEntry(name = name, path = path, isDirectory = isDirectory, size = size)
}

/**
 * 좋아요 목록. 앱 내부 파일(favorites.json)에 저장한다.
 * 파일은 이름이 같으면 같은 파일로 보고(폴더는 이름이 겹치기 쉬워서 경로로 구분), 화면에서 바로 반영되도록 Compose 상태로 들고 있는다.
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
                        isDirectory = o.optBoolean("dir"),
                    )
                    items[keyOf(fav)] = fav
                }
            }
        }
    }

    private fun key(entry: SmbEntry) = if (entry.isDirectory) "dir|" + entry.path.lowercase() else entry.name.lowercase()

    private fun keyOf(f: Favorite) = key(f.entry)

    fun isFavorite(entry: SmbEntry): Boolean = items.containsKey(key(entry))

    /** 최근에 누른 것부터. */
    fun all(): List<Favorite> = items.values.sortedByDescending { it.addedAt }

    /** 좋아요를 켜거나 끈다. 켜졌으면 true. */
    fun toggle(server: ServerConfig, entry: SmbEntry): Boolean {
        val k = key(entry)
        val on = if (items.remove(k) != null) {
            false
        } else {
            items[k] = Favorite(entry.name, entry.path, entry.size, server.id, server.share, System.currentTimeMillis(), entry.isDirectory)
            true
        }
        save()
        return on
    }

    fun remove(fav: Favorite) {
        items.remove(keyOf(fav))
        save()
    }

    /** 이름이 바뀐 파일/폴더를 따라간다. 폴더면 그 안에 든 좋아요의 경로도 고친다. */
    fun renamed(server: ServerConfig, old: SmbEntry, newPath: String) {
        val newName = newPath.substringAfterLast('\\')
        val oldPrefix = old.path.lowercase() + "\\"
        val moved = items.entries.toList().mapNotNull { (k, f) ->
            val inShare = f.serverId == server.id && f.share == server.share
            when {
                k == key(old) && (inShare || !old.isDirectory) -> k to f.copy(name = newName, path = newPath)
                old.isDirectory && inShare && f.path.lowercase().startsWith(oldPrefix) ->
                    k to f.copy(path = newPath + f.path.substring(old.path.length))
                else -> null
            }
        }
        if (moved.isEmpty()) return
        moved.forEach { (k, _) -> items.remove(k) }
        moved.forEach { (_, f) -> items[keyOf(f)] = f }
        save()
    }

    /** 지운 파일/폴더의 좋아요를 뺀다. 폴더면 안에 든 것들도. */
    fun deleted(server: ServerConfig, entry: SmbEntry) {
        val prefix = entry.path.lowercase() + "\\"
        val gone = items.entries.filter { (k, f) ->
            k == key(entry) ||
                (entry.isDirectory && f.serverId == server.id && f.share == server.share && f.path.lowercase().startsWith(prefix))
        }.map { it.key }
        if (gone.isEmpty()) return
        gone.forEach { items.remove(it) }
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
                            .put("added", it.addedAt)
                            .put("dir", it.isDirectory),
                    )
                }
                val tmp = File(f.parentFile, "favorites.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(f)
            }
        }
    }
}
