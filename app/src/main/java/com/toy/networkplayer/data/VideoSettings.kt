package com.toy.networkplayer.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** 영상 하나에만 적용되는 화면 설정. 기본값은 원래 비율, 회전 없음, 폰 방향 따라가기. */
data class VideoSetting(
    val scale: Int = 0,
    val rotation: Int = 0,
    val orientation: Int = 0,
) {
    val isDefault: Boolean get() = this == VideoSetting()
}

/**
 * 영상별 화면비/회전/화면 방향. 앱 내부 파일(video_settings.json)에 저장한다.
 * 파일 이름이 같으면 같은 영상으로 본다. 바꾼 적 없는 영상은 저장하지 않는다.
 */
object VideoSettings {
    private val items = ConcurrentHashMap<String, VideoSetting>()
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "video_settings.json")
        file = f
        runCatching {
            if (f.exists()) {
                val root = JSONObject(f.readText())
                root.keys().forEach { k ->
                    val o = root.getJSONObject(k)
                    items[k] = VideoSetting(o.optInt("s"), o.optInt("r"), o.optInt("o"))
                }
            }
        }
    }

    private fun key(entry: SmbEntry) = entry.name.lowercase()

    fun get(entry: SmbEntry): VideoSetting = items[key(entry)] ?: VideoSetting()

    fun set(entry: SmbEntry, setting: VideoSetting) {
        val k = key(entry)
        if (items[k] ?: VideoSetting() == setting) return
        if (setting.isDefault) items.remove(k) else items[k] = setting
        save()
    }

    fun renamed(old: SmbEntry, newName: String) {
        items.remove(key(old))?.let {
            items[newName.lowercase()] = it
            save()
        }
    }

    fun deleted(entry: SmbEntry) {
        if (items.remove(key(entry)) != null) save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = HashMap(items)
        writer.execute {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (k, v) ->
                    root.put(k, JSONObject().put("s", v.scale).put("r", v.rotation).put("o", v.orientation))
                }
                val tmp = File(f.parentFile, "video_settings.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(f)
            }
        }
    }
}
