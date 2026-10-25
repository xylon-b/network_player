package com.toy.networkplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB 연결을 서버별로 하나씩 재사용한다.
 * 모든 함수는 IO 스레드에서 돈다.
 */
object SmbRepository {

    private val client = SMBClient(
        SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
    )

    private class Live(val server: ServerConfig, val connection: Connection, val session: Session, val share: DiskShare)

    private var live: Live? = null

    @Synchronized
    private fun share(server: ServerConfig): DiskShare {
        live?.let {
            if (it.server == server && it.connection.isConnected && it.share.isConnected) return it.share
        }
        disconnect()
        val connection = client.connect(server.host, server.port)
        val auth = if (server.username.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(server.username, server.password.toCharArray(), server.domain.ifBlank { null })
        }
        val session = connection.authenticate(auth)
        val share = session.connectShare(server.share) as DiskShare
        live = Live(server, connection, session, share)
        return share
    }

    @Synchronized
    fun disconnect() {
        live?.let { runCatching { it.connection.close(true) } }
        live = null
    }

    /** 연결 확인용. 실패하면 예외를 던진다. */
    suspend fun test(server: ServerConfig) = withContext(Dispatchers.IO) {
        share(server).list("")
        Unit
    }

    suspend fun list(server: ServerConfig, path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        val hiddenMask = FileAttributes.FILE_ATTRIBUTE_HIDDEN.value or FileAttributes.FILE_ATTRIBUTE_SYSTEM.value
        val dirMask = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
        share(server).list(path)
            .asSequence()
            .filter { it.fileName != "." && it.fileName != ".." }
            .filter { it.fileAttributes and hiddenMask == 0L }
            .map {
                SmbEntry(
                    name = it.fileName,
                    path = join(path, it.fileName),
                    isDirectory = it.fileAttributes and dirMask != 0L,
                    size = it.endOfFile,
                )
            }
            .sortedWith(compareBy<SmbEntry> { !it.isDirectory }.thenBy(NaturalOrder) { it.name })
            .toList()
    }

    suspend fun readBytes(server: ServerConfig, path: String): ByteArray = withContext(Dispatchers.IO) {
        share(server).openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use { file -> file.inputStream.use { it.readBytes() } }
    }

    /** 사진을 화면 크기에 맞게 줄여서 읽는다. */
    suspend fun loadBitmap(server: ServerConfig, path: String, maxSide: Int = 2560): Bitmap? {
        val bytes = readBytes(server, path)
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent\\$name"

    fun parent(path: String) = path.substringBeforeLast('\\', "")
}

/** "2.mp4"가 "10.mp4"보다 앞에 오도록 숫자를 숫자로 비교한다. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val r = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
            if (r != 0) return r
        }
        return ca.size - cb.size
    }
}
