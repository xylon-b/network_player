package com.toy.networkplayer.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** 영상별 시청 기록과 길이. 앱 내부 파일(history.json)에 저장한다. */
data class WatchRecord(
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
)

object WatchHistory {
    private val records = ConcurrentHashMap<String, WatchRecord>()
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "history.json")
        file = f
        runCatching {
            if (f.exists()) {
                val root = JSONObject(f.readText())
                root.keys().forEach { k ->
                    val o = root.getJSONObject(k)
                    records[k] = WatchRecord(
                        playCount = o.optInt("c"),
                        lastPlayed = o.optLong("l"),
                        durationMs = o.optLong("d"),
                        positionMs = o.optLong("p"),
                    )
                }
            }
        }
    }

    fun key(server: ServerConfig, path: String) = "${server.host}|${server.share}|$path".lowercase()

    fun get(server: ServerConfig, entry: SmbEntry): WatchRecord = records[key(server, entry.path)] ?: WatchRecord()

    fun markPlayed(server: ServerConfig, path: String) = update(key(server, path)) {
        it.copy(playCount = it.playCount + 1, lastPlayed = System.currentTimeMillis())
    }

    fun setDuration(server: ServerConfig, path: String, ms: Long) {
        if (ms <= 0) return
        val k = key(server, path)
        if (records[k]?.durationMs == ms) return
        update(k) { it.copy(durationMs = ms) }
    }

    fun setPosition(server: ServerConfig, path: String, ms: Long) = update(key(server, path)) { it.copy(positionMs = ms) }

    private fun update(k: String, change: (WatchRecord) -> WatchRecord) {
        records[k] = change(records[k] ?: WatchRecord())
        save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = HashMap(records)
        writer.execute {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (k, r) ->
                    root.put(k, JSONObject().put("c", r.playCount).put("l", r.lastPlayed).put("d", r.durationMs).put("p", r.positionMs))
                }
                val tmp = File(f.parentFile, "history.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(f)
            }
        }
    }
}
