package com.toy.networkplayer.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 영상별 시청 기록과 길이. 앱 내부 파일(history.json)에 저장한다.
 * 영상 길이는 파일 이름 기준으로 따로(durations.json) 저장해서, 한 번 읽은 영상은 다시 읽지 않는다.
 */
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
    private val durations = ConcurrentHashMap<String, Long>()
    private var durationFile: File? = null

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
        val df = File(context.filesDir, "durations.json")
        durationFile = df
        runCatching {
            if (df.exists()) {
                val root = JSONObject(df.readText())
                root.keys().forEach { k -> durations[k] = root.getLong(k) }
            }
        }
    }

    private fun nameKey(path: String) = path.substringAfterLast('\\').substringAfterLast('/').lowercase()

    fun key(server: ServerConfig, path: String) = "${server.host}|${server.share}|$path".lowercase()

    fun get(server: ServerConfig, entry: SmbEntry): WatchRecord {
        val r = records[key(server, entry.path)] ?: WatchRecord()
        val d = durations[nameKey(entry.path)] ?: return r
        return if (d > 0) r.copy(durationMs = d) else r
    }

    /** 길이를 이미 확인했는지(읽기 실패도 확인한 것으로 친다). */
    fun hasDuration(entry: SmbEntry): Boolean = durations.containsKey(nameKey(entry.path))

    fun markPlayed(server: ServerConfig, path: String) = update(key(server, path)) {
        it.copy(playCount = it.playCount + 1, lastPlayed = System.currentTimeMillis())
    }

    /** ms가 0 이하면 "읽을 수 없는 영상"으로 기록해서 다음에 다시 시도하지 않는다. */
    fun setDuration(server: ServerConfig, path: String, ms: Long) {
        val k = nameKey(path)
        val value = if (ms > 0) ms else -1L
        val old = durations[k]
        if (old == value || (value < 0 && old != null)) return
        durations[k] = value
        saveDurations()
    }

    fun setPosition(server: ServerConfig, path: String, ms: Long) = update(key(server, path)) { it.copy(positionMs = ms) }

    private fun update(k: String, change: (WatchRecord) -> WatchRecord) {
        records[k] = change(records[k] ?: WatchRecord())
        save()
    }

    private fun saveDurations() {
        val f = durationFile ?: return
        val snapshot = HashMap(durations)
        writer.execute {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (k, v) -> root.put(k, v) }
                val tmp = File(f.parentFile, "durations.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(f)
            }
        }
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
