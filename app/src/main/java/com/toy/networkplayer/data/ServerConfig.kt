package com.toy.networkplayer.data

import org.json.JSONObject
import java.util.UUID

/** 저장된 SMB 서버(윈도우 공유폴더) 하나. */
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val share: String,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 445,
) {
    val displayName: String get() = name.ifBlank { "$host/$share" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("share", share)
        .put("username", username)
        .put("password", password)
        .put("domain", domain)
        .put("port", port)

    companion object {
        fun fromJson(o: JSONObject) = ServerConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            host = o.optString("host"),
            share = o.optString("share"),
            username = o.optString("username"),
            password = o.optString("password"),
            domain = o.optString("domain"),
            port = o.optInt("port", 445),
        )
    }
}
