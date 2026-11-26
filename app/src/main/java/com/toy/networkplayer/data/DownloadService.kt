package com.toy.networkplayer.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.toy.networkplayer.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 다운로드하는 동안 알림을 띄워 두는 서비스.
 * 앱을 뒤로 보내거나 화면을 꺼도 안드로이드가 다운로드를 끊지 않게 한다. 다 받으면 스스로 끝난다.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var lastNotify = 0L
    private var lastRunningId: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            Downloads.cancelAll()
        }
        if (!started) {
            started = true
            ensureChannel()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build(Downloads.tasks.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            scope.launch {
                Downloads.tasks.collect { tasks ->
                    if (tasks.none { it.active }) {
                        finish(tasks)
                    } else {
                        // 알림은 너무 자주 고치면 시스템이 막아서 1초에 한 번쯤만, 받는 파일이 바뀔 땐 바로.
                        val now = System.currentTimeMillis()
                        val runningId = tasks.firstOrNull { it.status == DownloadStatus.RUNNING }?.id
                        if (now - lastNotify >= 1000 || runningId != lastRunningId) {
                            lastNotify = now
                            lastRunningId = runningId
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(tasks))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** 안드로이드 15부터 이런 서비스는 하루 6시간까지만 돈다. 시간이 다 되면 남은 다운로드를 멈춘다. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Downloads.cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish(tasks: List<DownloadTask>) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // 끝났다는 알림을 하나 남긴다(눌러서 지울 수 있게).
        val done = tasks.count { it.status == DownloadStatus.DONE }
        val failed = tasks.count { it.status == DownloadStatus.FAILED }
        if (done + failed > 0) {
            val text = buildString {
                append("${done}개 받음")
                if (failed > 0) append(" · ${failed}개 실패")
            }
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("다운로드 완료")
                .setContentText(text)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(DONE_NOTIFICATION_ID, n)
        }
        stopSelf()
    }

    private fun build(tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.active }
        val running = active.firstOrNull { it.status == DownloadStatus.RUNNING }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, "모두 취소", PendingIntent.getService(
                this, 1,
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        if (running == null) {
            b.setContentTitle("다운로드 준비 중").setProgress(0, 0, true)
        } else {
            val left = active.size - 1
            b.setContentTitle(running.name)
                .setContentText(buildString {
                    append("${(running.progress * 100).toInt()}%")
                    if (running.speed > 0) append(" · ${formatBytes(running.speed)}/s")
                    if (left > 0) append(" · ${left}개 대기")
                })
                .setProgress(1000, (running.progress * 1000).toInt(), running.total <= 0)
        }
        return b.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "다운로드", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID = 1002
        private const val ACTION_CANCEL_ALL = "com.toy.networkplayer.CANCEL_ALL_DOWNLOADS"
    }
}

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else "%.1f %s".format(v, units[i])
}
