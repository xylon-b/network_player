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
    val displayName: String get() = name.ifBlank { if (share.isBlank()) host else "$host/$share" }

    /** 휴대폰 안의 파일을 가리키는 가짜 서버. 이때 경로는 절대 경로이고 구분자는 '/'. */
    val isLocal: Boolean get() = id == LOCAL_ID

    /** 경로 구분자. 공유폴더는 '\\', 휴대폰은 '/'. */
    val separator: Char get() = if (isLocal) '/' else '\\'

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
        const val LOCAL_ID = "local"

        /** 휴대폰 저장공간. 좋아요/시청 기록/썸네일을 공유폴더와 같은 방식으로 쓰기 위한 자리. */
        val LOCAL = ServerConfig(id = LOCAL_ID, name = "내 폰", host = "", share = "local")

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
