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
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
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

    private class Live(val key: ServerConfig, val connection: Connection, val session: Session) {
        val shares = mutableMapOf<String, DiskShare>()
    }

    private var live: Live? = null

    /** 공유 이름과 상관없이 같은 PC/계정이면 연결을 재사용한다. */
    private fun sessionKey(server: ServerConfig) = server.copy(id = "", name = "", share = "")

    @Synchronized
    private fun session(server: ServerConfig): Session {
        live?.let {
            if (it.key == sessionKey(server) && it.connection.isConnected) return it.session
        }
        disconnect()
        val connection = client.connect(server.host, server.port)
        val auth = if (server.username.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(server.username, server.password.toCharArray(), server.domain.ifBlank { null })
        }
        val session = connection.authenticate(auth)
        live = Live(sessionKey(server), connection, session)
        return session
    }

    @Synchronized
    private fun share(server: ServerConfig): DiskShare {
        val session = session(server)
        val shares = live!!.shares
        shares[server.share]?.let { if (it.isConnected) return it }
        val share = session.connectShare(server.share) as DiskShare
        shares[server.share] = share
        return share
    }

    @Synchronized
    fun disconnect() {
        live?.let { runCatching { it.connection.close(true) } }
        live = null
    }

    /** 연결 확인용. 실패하면 예외를 던진다. */
    suspend fun test(server: ServerConfig) = withContext(Dispatchers.IO) {
        if (server.share.isBlank()) listSharesBlocking(server) else share(server).list("")
        Unit
    }

    /** PC가 공유 중인 폴더 이름들. C$ 같은 관리용 숨김 공유는 뺀다. */
    suspend fun listShares(server: ServerConfig): List<String> = withContext(Dispatchers.IO) {
        listSharesBlocking(server)
    }

    @Synchronized
    private fun listSharesBlocking(server: ServerConfig): List<String> {
        val transport = SMBTransportFactories.SRVSVC.getTransport(session(server))
        return ServerService(transport).shares0
            .map { it.netName }
            .filter { !it.endsWith("$") }
            .sortedWith(NaturalOrder)
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

    /** 읽기 전용으로 파일을 연다. 호출한 쪽에서 close 해야 한다. 블로킹 함수. */
    fun openRead(server: ServerConfig, path: String): com.hierynomus.smbj.share.File =
        share(server).openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )

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

/** 사람이 읽을 오류 문구. 메시지가 비어 있으면 원인 예외까지 따라가 본다. */
fun Throwable.describe(): String {
    var t: Throwable? = this
    while (t != null) {
        val msg = t.message
        if (!msg.isNullOrBlank()) return explain(msg)
        t = t.cause
    }
    return javaClass.simpleName
}

private fun explain(msg: String): String = when {
    "STATUS_LOGON_FAILURE" in msg -> "사용자 이름이나 비밀번호가 맞지 않아요"
    "STATUS_BAD_NETWORK_NAME" in msg -> "그 공유 이름을 찾을 수 없어요. 공유 이름을 비워두고 목록에서 골라보세요"
    "STATUS_ACCESS_DENIED" in msg -> "접근 권한이 없어요. 윈도우 공유 설정에서 이 계정을 허용해주세요"
    "timed out" in msg.lowercase() || "timeout" in msg.lowercase() -> "PC에 연결할 수 없어요. 주소와 같은 와이파이인지 확인해주세요 ($msg)"
    else -> msg
}
