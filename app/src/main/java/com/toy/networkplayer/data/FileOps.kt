package com.toy.networkplayer.data

/**
 * 공유폴더의 파일 이름 바꾸기/지우기. PC에서 실제로 바꾼 다음,
 * 앱이 따로 들고 있는 것들(좋아요, 시청 기록, 영상 길이, 화면 설정, 썸네일)도 같이 맞춘다.
 */
object FileOps {
    private val badChars = Regex("[\\\\/:*?\"<>|]")

    /** 쓸 수 없는 이름이면 이유를, 괜찮으면 null. */
    fun checkName(name: String): String? = when {
        name.isBlank() -> "이름을 적어주세요"
        badChars.containsMatchIn(name) -> "\\ / : * ? \" < > | 는 이름에 쓸 수 없어요"
        name.endsWith(".") || name.endsWith(" ") -> "이름 끝에 점이나 공백은 쓸 수 없어요"
        else -> null
    }

    /** 이름을 바꾸고 바뀐 항목을 돌려준다. */
    suspend fun rename(server: ServerConfig, entry: SmbEntry, newName: String): SmbEntry {
        val newPath = SmbRepository.rename(server, entry, newName)
        Favorites.renamed(server, entry, newPath)
        WatchHistory.renamed(server, entry, newPath)
        if (!entry.isDirectory) {
            VideoSettings.renamed(entry, newName)
            Thumbnails.renamed(entry, newName)
        }
        return entry.copy(name = newName, path = newPath)
    }

    suspend fun delete(server: ServerConfig, entry: SmbEntry) {
        SmbRepository.delete(server, entry)
        Favorites.deleted(server, entry)
        WatchHistory.deleted(server, entry)
        if (!entry.isDirectory) {
            VideoSettings.deleted(entry)
            Thumbnails.deleted(entry)
        }
    }
}
