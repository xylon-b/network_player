package com.toy.networkplayer.data

import android.media.MediaDataSource
import com.hierynomus.smbj.share.File

/**
 * 썸네일/길이 읽기용 데이터 소스.
 * 안드로이드 디코더는 몇 KB씩 잘게, 여기저기 뛰어다니며 읽는데 SMB에서는 한 번 읽을 때마다 왕복이 생긴다.
 * 그래서 256KB 덩어리로 크게 읽어 몇 개를 들고 있다가 같은 곳을 다시 읽으면 메모리에서 바로 준다.
 */
class SmbDataSource(private val file: File, private val size: Long) : MediaDataSource() {
    private val blocks = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>) = this.size > MAX_BLOCKS
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val want = minOf(length.toLong(), size - position).toInt()
        var copied = 0
        while (copied < want) {
            val pos = position + copied
            val index = pos / BLOCK
            val block = blocks[index] ?: load(index).also { blocks[index] = it }
            val inBlock = (pos - index * BLOCK).toInt()
            if (inBlock >= block.size) break
            val n = minOf(want - copied, block.size - inBlock)
            System.arraycopy(block, inBlock, buffer, offset + copied, n)
            copied += n
        }
        return if (copied == 0) -1 else copied
    }

    private fun load(index: Long): ByteArray {
        val start = index * BLOCK
        val len = minOf(BLOCK.toLong(), size - start).toInt()
        val buf = ByteArray(len)
        var got = 0
        // 한 번의 SMB 읽기가 덩어리보다 작게 돌아올 수 있어서 다 찰 때까지 읽는다.
        while (got < len) {
            val n = file.read(buf, start + got, got, len - got)
            if (n <= 0) break
            got += n
        }
        return if (got == len) buf else buf.copyOf(got)
    }

    override fun getSize(): Long = size
    override fun close() = Unit

    private companion object {
        const val BLOCK = 256 * 1024
        const val MAX_BLOCKS = 24
    }
}
