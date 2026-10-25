package com.toy.networkplayer.data

import android.content.Context
import org.json.JSONArray

/** 서버 목록을 앱 전용 SharedPreferences에 JSON으로 저장한다. */
class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun load(): List<ServerConfig> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { ServerConfig.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(servers: List<ServerConfig>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}
