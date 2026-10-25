package com.toy.networkplayer.data

enum class MediaKind { FOLDER, VIDEO, IMAGE, OTHER }

/** 공유폴더 안의 파일이나 폴더 하나. path는 공유 루트 기준, 구분자는 '\'. */
data class SmbEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
) {
    val kind: MediaKind
        get() = when {
            isDirectory -> MediaKind.FOLDER
            extension in VIDEO_EXT -> MediaKind.VIDEO
            extension in IMAGE_EXT -> MediaKind.IMAGE
            else -> MediaKind.OTHER
        }

    val isSubtitle: Boolean get() = !isDirectory && extension in SUBTITLE_EXT

    /** 확장자를 뺀 이름. 같은 이름의 자막을 찾을 때 쓴다. */
    val baseName: String get() = name.substringBeforeLast('.')

    private val extension: String get() = name.substringAfterLast('.', "").lowercase()

    companion object {
        val VIDEO_EXT = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts", "mts",
            "mpg", "mpeg", "3gp", "rmvb", "asf", "vob", "ogv",
        )
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
        val SUBTITLE_EXT = setOf("smi", "srt", "ass", "ssa", "sub", "vtt")
    }
}
