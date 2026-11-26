package com.toy.networkplayer.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class DownloadTask(
    val id: Long,
    val name: String,
    /** 어느 PC에서 받았는지 보여주기용. */
    val source: String,
    val total: Long,
    val done: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    /** 받은 파일(또는 받는 중인 파일)의 최종 경로. */
    val dest: String = "",
    /** 초당 바이트. 받는 중일 때만. */
    val speed: Long = 0L,
) {
    val active get() = status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * 공유폴더 → 휴대폰 다운로드 대기열. 한 번에 하나씩 받는다.
 * 받는 동안은 [DownloadService]가 알림을 띄워 앱이 뒤로 가도 멈추지 않게 붙잡아 둔다.
 * 받는 중에는 "이름.part"로 쓰다가 다 받으면 원래 이름으로 바꾼다.
 */
object Downloads {
    private data class Request(val id: Long, val server: ServerConfig, val path: String, val dir: File)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Request>(Channel.UNLIMITED)
    private val nextId = AtomicLong(1)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    @Volatile private var current: kotlinx.coroutines.Job? = null
    @Volatile private var currentId = 0L
    private var worker: kotlinx.coroutines.Job? = null

    private const val BUFFER = 1024 * 1024

    /**
     * 파일이나 폴더를 대기열에 넣는다. 폴더면 안에 든 파일을 폴더 구조 그대로 받는다.
     * 넣은 파일 수를 돌려준다.
     */
    suspend fun enqueue(context: Context, server: ServerConfig, entry: SmbEntry): Int {
        val base = AppSettings.downloadDir(context)
        val jobs = mutableListOf<Pair<Request, DownloadTask>>()
        withContext(Dispatchers.IO) {
            fun add(e: SmbEntry, dir: File) {
                val id = nextId.getAndIncrement()
                jobs += Request(id, server, e.path, dir) to DownloadTask(id, e.name, server.displayName, e.size, dest = File(dir, e.name).absolutePath)
            }
            if (entry.isDirectory) {
                suspend fun walk(folder: SmbEntry, dir: File) {
                    SmbRepository.list(server, folder.path).forEach { child ->
                        if (child.isDirectory) walk(child, File(dir, child.name)) else add(child, dir)
                    }
                }
                walk(entry, File(base, entry.name))
            } else {
                add(entry, base)
            }
        }
        if (jobs.isEmpty()) return 0
        _tasks.update { it + jobs.map { j -> j.second } }
        ensureWorker()
        jobs.forEach { retryInfo[it.first.id] = it.first; queue.trySend(it.first) }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        return jobs.size
    }

    /** 화면을 벗어나도 폴더 훑기가 끊기지 않도록 앱 쪽에서 대기열에 넣고, 결과를 짧은 안내로 알려준다. */
    fun start(context: Context, server: ServerConfig, entry: SmbEntry) {
        val app = context.applicationContext
        if (entry.isDirectory) android.widget.Toast.makeText(app, "폴더 안의 파일을 확인하는 중…", android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            val r = runCatching { enqueue(app, server, entry) }
            withContext(Dispatchers.Main) {
                val msg = r.fold(
                    { n -> if (n == 0) "받을 파일이 없어요" else if (entry.isDirectory) "${n}개 파일을 받기 시작해요" else "다운로드를 시작해요" },
                    { "다운로드를 시작하지 못했어요: ${it.describe()}" },
                )
                android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancel(id: Long) {
        if (currentId == id) current?.cancel()
        set(id) { if (it.active) it.copy(status = DownloadStatus.CANCELED, speed = 0) else it }
    }

    fun cancelAll() {
        _tasks.value.filter { it.active }.forEach { cancel(it.id) }
    }

    /** 끝난 것(완료/실패/취소)을 목록에서 치운다. */
    fun clearFinished() {
        _tasks.update { list -> list.filter { it.active } }
    }

    /** 실패하거나 취소한 것을 다시 받는다. */
    fun retry(context: Context, id: Long) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        val job = retryInfo[id] ?: return
        set(id) { it.copy(status = DownloadStatus.QUEUED, done = 0, error = null, dest = File(job.dir, t.name).absolutePath) }
        ensureWorker()
        queue.trySend(job)
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    private val retryInfo = java.util.concurrent.ConcurrentHashMap<Long, Request>()

    @Synchronized
    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            for (job in queue) {
                val task = _tasks.value.firstOrNull { it.id == job.id } ?: continue
                if (task.status != DownloadStatus.QUEUED) continue
                currentId = job.id
                val j = launch { run(job, task) }
                current = j
                j.join()
                current = null
                currentId = 0
            }
        }
    }

    private fun set(id: Long, change: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private suspend fun run(job: Request, task: DownloadTask) {
        set(job.id) { it.copy(status = DownloadStatus.RUNNING) }
        var part: File? = null
        try {
            if (!job.dir.exists() && !job.dir.mkdirs()) throw IOException("저장 폴더를 만들 수 없어요 (${job.dir.absolutePath})")
            val target = LocalFiles.uniqueFile(job.dir, task.name)
            val tmp = File(target.path + ".part")
            part = tmp
            set(job.id) { it.copy(dest = target.absolutePath) }
            SmbRepository.openRead(job.server, job.path).use { file ->
                val size = file.fileInformation.standardInformation.endOfFile
                val buf = ByteArray(BUFFER)
                var pos = 0L
                var lastUi = 0L
                var speedFrom = System.currentTimeMillis()
                var speedBytes = 0L
                var speed = 0L
                FileOutputStream(tmp).use { out ->
                    while (pos < size) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val want = minOf(BUFFER.toLong(), size - pos).toInt()
                        val n = file.read(buf, pos, 0, want)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        pos += n
                        speedBytes += n
                        val now = System.currentTimeMillis()
                        if (now - speedFrom >= 1000) {
                            speed = speedBytes * 1000 / (now - speedFrom)
                            speedFrom = now
                            speedBytes = 0
                        }
                        if (now - lastUi >= 300) {
                            lastUi = now
                            val p = pos
                            val sp = speed
                            set(job.id) { it.copy(done = p, total = size, speed = sp) }
                        }
                    }
                }
                if (pos < size) throw IOException("파일을 끝까지 받지 못했어요")
            }
            if (!tmp.renameTo(target)) throw IOException("받은 파일을 저장하지 못했어요")
            part = null
            LocalFiles.scan(target)
            set(job.id) { it.copy(status = DownloadStatus.DONE, done = it.total, speed = 0) }
        } catch (e: CancellationException) {
            set(job.id) { it.copy(status = DownloadStatus.CANCELED, speed = 0) }
        } catch (e: Throwable) {
            set(job.id) { it.copy(status = DownloadStatus.FAILED, error = e.describe(), speed = 0) }
        } finally {
            part?.delete()
        }
    }
}
