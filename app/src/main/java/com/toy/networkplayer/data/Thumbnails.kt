package com.toy.networkplayer.data

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 바둑판 보기용 썸네일. 메모리에만 캐시하고, 동시에 너무 많이 읽지 않도록 제한한다. */
object Thumbnails {
    private const val SIZE = 384
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = mutableSetOf<String>()
    private val gate = Semaphore(3)

    private fun key(server: ServerConfig, entry: SmbEntry) = "${server.host}|${server.share}|${entry.path}"

    fun cached(server: ServerConfig, entry: SmbEntry): Bitmap? = cache.get(key(server, entry))

    suspend fun load(server: ServerConfig, entry: SmbEntry): Bitmap? {
        val k = key(server, entry)
        cache.get(k)?.let { return it }
        synchronized(failed) { if (k in failed) return null }
        val bmp = gate.withPermit {
            runCatching {
                when (entry.kind) {
                    MediaKind.IMAGE -> SmbRepository.loadBitmap(server, entry.path, SIZE)
                    MediaKind.VIDEO -> videoFrame(server, entry)
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) cache.put(k, bmp) else synchronized(failed) { failed += k }
        return bmp
    }

    private suspend fun videoFrame(server: ServerConfig, entry: SmbEntry): Bitmap? = withContext(Dispatchers.IO) {
        SmbRepository.openRead(server, entry.path).use { file ->
            val size = file.fileInformation.standardInformation.endOfFile
            val source = object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                    if (position >= size) return -1
                    val n = minOf(length.toLong(), size - position).toInt()
                    return file.read(buffer, position, offset, n)
                }

                override fun getSize(): Long = size
                override fun close() = Unit
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                WatchHistory.setDuration(server, entry.path, durationMs)
                // 첫 장면은 검은 화면인 경우가 많아서 조금 뒤 장면을 쓴다.
                val atUs = (durationMs * 100L).coerceAtMost(30_000_000L)
                val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@use null
                scaleDown(frame)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    /** 영상 길이만 빠르게 읽는다(랜덤 정렬의 길이 조건용). 결과는 시청 기록에 남긴다. */
    suspend fun probeDuration(server: ServerConfig, entry: SmbEntry): Long = gate.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                SmbRepository.openRead(server, entry.path).use { file ->
                    val size = file.fileInformation.standardInformation.endOfFile
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(object : MediaDataSource() {
                            override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                                if (position >= size) return -1
                                return file.read(buffer, position, offset, minOf(length.toLong(), size - position).toInt())
                            }

                            override fun getSize(): Long = size
                            override fun close() = Unit
                        })
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }.getOrDefault(0L).also { WatchHistory.setDuration(server, entry.path, it) }
        }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= SIZE) return src
        val ratio = SIZE.toFloat() / longest
        val out = Bitmap.createScaledBitmap(src, (src.width * ratio).toInt().coerceAtLeast(1), (src.height * ratio).toInt().coerceAtLeast(1), true)
        if (out !== src) src.recycle()
        return out
    }
}
