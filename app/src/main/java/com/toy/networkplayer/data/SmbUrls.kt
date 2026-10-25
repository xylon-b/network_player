package com.toy.networkplayer.data

import android.net.Uri

/** libVLC가 직접 여는 smb:// 주소를 만든다. */
object SmbUrls {
    fun forVlc(server: ServerConfig, path: String): Uri {
        val segments = (listOf(server.share) + path.split('\\'))
            .filter { it.isNotEmpty() }
            .joinToString("/") { Uri.encode(it) }
        val auth = if (server.username.isBlank()) "" else {
            Uri.encode(server.username) + ":" + Uri.encode(server.password) + "@"
        }
        val port = if (server.port != 445) ":${server.port}" else ""
        return Uri.parse("smb://$auth${server.host}$port/$segments")
    }

    /** 주소에 넣은 계정이 무시되는 경우를 대비해 libVLC 옵션으로도 넘긴다. */
    fun vlcOptions(server: ServerConfig): List<String> = buildList {
        if (server.username.isNotBlank()) {
            add(":smb-user=${server.username}")
            add(":smb-pwd=${server.password}")
        }
        if (server.domain.isNotBlank()) add(":smb-domain=${server.domain}")
    }
}
