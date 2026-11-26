package com.toy.networkplayer.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 휴대폰 저장공간 파일 다루기(내 폰 파일탐색기, 다운로드 저장).
 * 경로는 절대 경로, 항목은 공유폴더와 같은 [SmbEntry]로 돌려줘서 화면/재생/썸네일 코드를 같이 쓴다.
 * 앱이 따로 들고 있는 것들(좋아요, 시청 기록, 화면 설정, 썸네일)도 [FileOps]처럼 같이 맞춘다.
 */
object LocalFiles {
    private val server get() = ServerConfig.LOCAL

    /** 내부 저장공간 맨 위(/storage/emulated/0). */
    val root: File get() = Environment.getExternalStorageDirectory()

    /** 따로 정하지 않았을 때 다운로드가 저장되는 곳: Download/NetworkPlayer */
    val defaultDownloadDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NetworkPlayer")

    /** 저장공간 전체를 읽고 쓸 수 있는지. 안드로이드 11부터는 '모든 파일 접근' 허용이 필요하다. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** 안드로이드 10 이하에서 런타임으로 요청할 권한. 11 이상은 [allFilesAccessIntent]로 설정 화면을 연다. */
    val legacyPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun allFilesAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    fun entryOf(f: File) = SmbEntry(
        name = f.name,
        path = f.absolutePath,
        isDirectory = f.isDirectory,
        size = if (f.isDirectory) 0L else f.length(),
        modified = f.lastModified(),
    )

    suspend fun list(path: String, showHidden: Boolean): List<SmbEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        val files = dir.listFiles() ?: throw IOException(if (dir.exists()) "이 폴더는 열어볼 수 없어요" else "폴더가 없어요")
        files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { entryOf(it) }
            .sortedWith(compareBy<SmbEntry> { !it.isDirectory }.thenBy(NaturalOrder) { it.name })
            .toList()
    }

    suspend fun createFolder(parent: String, name: String): SmbEntry = withContext(Dispatchers.IO) {
        val dir = File(parent, name)
        if (dir.exists()) throw IOException("같은 이름이 이미 있어요")
        if (!dir.mkdirs()) throw IOException("폴더를 만들 수 없어요")
        entryOf(dir)
    }

    suspend fun rename(entry: SmbEntry, newName: String): SmbEntry = withContext(Dispatchers.IO) {
        val src = File(entry.path)
        val dst = File(src.parentFile, newName)
        // 대소문자만 바꾸는 경우(a.mp4 → A.mp4)는 같은 파일로 잡히니 그대로 진행한다.
        if (dst.exists() && !dst.name.equals(src.name, ignoreCase = true)) throw IOException("같은 이름이 이미 있어요")
        if (!src.renameTo(dst)) throw IOException("이름을 바꿀 수 없어요")
        Favorites.renamed(server, entry, dst.absolutePath)
        WatchHistory.renamed(server, entry, dst.absolutePath)
        if (!entry.isDirectory) {
            VideoSettings.renamed(entry, newName)
            Thumbnails.renamed(entry, newName)
        }
        scan(src, dst)
        entryOf(dst)
    }

    suspend fun delete(entry: SmbEntry) = withContext(Dispatchers.IO) {
        val f = File(entry.path)
        if (!f.deleteRecursively()) throw IOException("일부 파일을 지우지 못했어요")
        Favorites.deleted(server, entry)
        WatchHistory.deleted(server, entry)
        if (!entry.isDirectory) {
            VideoSettings.deleted(entry)
            Thumbnails.deleted(entry)
        }
        scan(f)
    }

    /** 다른 폴더로 옮긴다. 같은 저장공간 안이면 바로 옮기고, 아니면 복사 후 원본을 지운다. */
    suspend fun move(entry: SmbEntry, targetDir: String): SmbEntry = withContext(Dispatchers.IO) {
        val src = File(entry.path)
        val dst = File(targetDir, src.name)
        if (dst.absolutePath == src.absolutePath) return@withContext entry
        if (entry.isDirectory && (dst.absolutePath + "/").startsWith(src.absolutePath + "/")) {
            throw IOException("폴더를 자기 안으로 옮길 수 없어요")
        }
        if (dst.exists()) throw IOException("옮길 폴더에 같은 이름이 이미 있어요")
        if (!src.renameTo(dst)) {
            if (!src.copyRecursively(dst, overwrite = false)) throw IOException("옮기지 못했어요")
            src.deleteRecursively()
        }
        Favorites.renamed(server, entry, dst.absolutePath)
        WatchHistory.renamed(server, entry, dst.absolutePath)
        scan(src, dst)
        entryOf(dst)
    }

    /** 갤러리 같은 다른 앱에도 바뀐 내용이 바로 보이도록 미디어 목록을 갱신한다. */
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun scan(vararg files: File) {
        val ctx = appContext ?: return
        val paths = files.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() else listOf(f.absolutePath) }
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null) }
    }

    /** "영상.mp4"가 이미 있으면 "영상 (1).mp4", "영상 (2).mp4"... 처럼 비어 있는 이름을 찾는다. */
    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists() && !File(dir, "$name.part").exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists() && !File(dir, f.name + ".part").exists()) return f
            i++
        }
    }

    fun parent(path: String): String? = File(path).parentFile?.absolutePath

    /** 화면에 보여줄 짧은 경로. /storage/emulated/0 은 '내 폰'으로 줄인다. */
    fun pretty(path: String): String {
        val r = root.absolutePath
        return when {
            path == r -> "내 폰"
            path.startsWith("$r/") -> "내 폰/" + path.removePrefix("$r/")
            else -> path
        }
    }
}

/** 다운로드 저장 위치 같은 앱 전체 설정. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_DOWNLOAD_DIR = "download_dir"

    fun downloadDir(context: Context): File =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DOWNLOAD_DIR, null)
            ?.let { File(it) } ?: LocalFiles.defaultDownloadDir

    fun setDownloadDir(context: Context, dir: File?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (dir == null) remove(KEY_DOWNLOAD_DIR) else putString(KEY_DOWNLOAD_DIR, dir.absolutePath) }
            .apply()
    }

    fun isDefaultDownloadDir(context: Context) =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_DOWNLOAD_DIR)
}
