package com.toy.networkplayer.data

import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 공유폴더 파일을 휴대폰 안(127.0.0.1)의 작은 HTTP 서버로 다시 내보낸다.
 * 재생 엔진은 http 주소만 열면 되고, SMB 접속과 로그인은 이미 검증된 smbj가 맡는다.
 * Range 요청을 지원해서 탐색(seek)도 된다.
 */
object LocalStreamServer {
    private const val TAG = "LocalStream"
    private const val CHUNK = 512 * 1024

    private val servers = ConcurrentHashMap<String, ServerConfig>()
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var socket: ServerSocket? = null

    /** 재생 엔진에 넘길 주소. 경로 구분자 '\'는 '/'로 바꿔 각 조각을 인코딩한다. */
    fun url(server: ServerConfig, path: String): Uri {
        val port = ensureStarted()
        val token = server.id.ifBlank { "s" } + "-" + server.share.hashCode().toUInt().toString(16)
        servers[token] = server
        val encoded = path.split('\\').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
        return Uri.parse("http://127.0.0.1:$port/$token/$encoded")
    }

    @Synchronized
    private fun ensureStarted(): Int {
        socket?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        socket = s
        Thread({
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: SocketException) { break }
                pool.execute { handle(client) }
            }
        }, "local-stream-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            try {
                sock.soTimeout = 30_000
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readLine(input) ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val segments = parts[1].substringBefore('?').trimStart('/').split('/')
                val server = servers[segments.firstOrNull()]
                val out = BufferedOutputStream(sock.getOutputStream(), CHUNK)
                if (server == null || segments.size < 2) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
                val path = segments.drop(1).joinToString("\\") { Uri.decode(it) }

                SmbRepository.openRead(server, path).use { file ->
                    val size = file.fileInformation.standardInformation.endOfFile
                    val range = parseRange(headers["range"], size)
                    val start = range?.first ?: 0L
                    val end = range?.second ?: (size - 1)
                    val length = (end - start + 1).coerceAtLeast(0)

                    val head = StringBuilder()
                    if (range != null) {
                        head.append("HTTP/1.1 206 Partial Content\r\n")
                        head.append("Content-Range: bytes $start-$end/$size\r\n")
                    } else {
                        head.append("HTTP/1.1 200 OK\r\n")
                    }
                    head.append("Content-Type: ${mimeOf(path)}\r\n")
                    head.append("Content-Length: $length\r\n")
                    head.append("Accept-Ranges: bytes\r\n")
                    head.append("Connection: close\r\n\r\n")
                    out.write(head.toString().toByteArray())
                    if (method == "HEAD") {
                        out.flush()
                        return
                    }

                    val buf = ByteArray(CHUNK)
                    var pos = start
                    while (pos <= end) {
                        val want = minOf(CHUNK.toLong(), end - pos + 1).toInt()
                        val n = file.read(buf, pos, 0, want)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        pos += n
                    }
                    out.flush()
                }
            } catch (e: SocketException) {
                // 탐색할 때 재생 엔진이 이전 연결을 끊는 건 정상이다.
            } catch (e: Exception) {
                Log.w(TAG, "stream failed", e)
            }
        }
    }

    private fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
        if (header == null || !header.startsWith("bytes=") || size <= 0) return null
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val a = spec.substring(0, dash).trim()
        val b = spec.substring(dash + 1).trim()
        return if (a.isEmpty()) {
            val suffix = b.toLongOrNull() ?: return null
            (size - suffix).coerceAtLeast(0) to size - 1
        } else {
            val s = a.toLongOrNull() ?: return null
            val e = b.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
            if (s > e) null else s to e
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts", "mts" -> "video/mp2t"
        "srt" -> "application/x-subrip"
        "smi" -> "application/smil"
        else -> "application/octet-stream"
    }
}
