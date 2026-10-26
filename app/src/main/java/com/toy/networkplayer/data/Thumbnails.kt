package com.toy.networkplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 바둑판 보기용 썸네일.
 * 한 번 만든 썸네일은 앱 저장공간(files/thumbs)에 JPEG로 남겨서 앱을 다시 켜도 다시 만들지 않는다.
 * 파일 이름이 같으면 같은 영상/사진으로 보고 어느 폴더에 있든 같은 썸네일을 쓴다.
 */
object Thumbnails {
    private const val SIZE = 384
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = mutableSetOf<String>()
    private val gate = Semaphore(3)
    private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "thumbs").apply { mkdirs() }
    }

    private fun key(entry: SmbEntry) = entry.name.lowercase()

    private fun diskFile(k: String): File? {
        val d = dir ?: return null
        val hash = MessageDigest.getInstance("SHA-1").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(d, "$hash.jpg")
    }

    fun cached(server: ServerConfig, entry: SmbEntry): Bitmap? = cache.get(key(entry))

    suspend fun load(server: ServerConfig, entry: SmbEntry): Bitmap? {
        val k = key(entry)
        cache.get(k)?.let { return it }
        synchronized(failed) { if (k in failed) return null }

        // 저장해 둔 썸네일이 있으면 SMB를 건드리지 않고 바로 쓴다.
        val file = diskFile(k)
        if (file != null && file.exists()) {
            val saved = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
            if (saved != null) {
                cache.put(k, saved)
                return saved
            }
        }

        val bmp = gate.withPermit {
            runCatching {
                when (entry.kind) {
                    MediaKind.IMAGE -> SmbRepository.loadBitmap(server, entry.path, SIZE)
                    MediaKind.VIDEO -> videoFrame(server, entry)
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) {
            cache.put(k, bmp)
            if (file != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = File(file.path + ".tmp")
                        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        tmp.renameTo(file)
                    }
                }
            }
        } else {
            synchronized(failed) { failed += k }
        }
        return bmp
    }

    /** 저장된 썸네일 전체 크기(바이트). */
    fun diskUsage(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearDisk() {
        cache.evictAll()
        synchronized(failed) { failed.clear() }
        dir?.listFiles()?.forEach { it.delete() }
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
