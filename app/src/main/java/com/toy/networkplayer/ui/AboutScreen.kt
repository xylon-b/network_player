package com.toy.networkplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 앱에 들어간 오픈소스 하나. licenseFile은 assets/licenses/ 아래 전문 파일. */
private data class OssLib(val name: String, val license: String, val licenseFile: String, val url: String, val note: String = "")

private val OSS = listOf(
    OssLib(
        "libVLC (VideoLAN)", "LGPL 2.1 이상", "LGPL-2.1.txt", "https://code.videolan.org/videolan/vlc-android",
        "동영상 재생 엔진. 수정 없이 공식 배포본(org.videolan.android:libvlc-all)을 동적 라이브러리로 사용합니다. " +
            "소스 코드: https://code.videolan.org/videolan/vlc (3.0.x), https://code.videolan.org/videolan/vlc-android",
    ),
    OssLib("smbj", "Apache 2.0", "Apache-2.0.txt", "https://github.com/hierynomus/smbj"),
    OssLib("smbj-rpc (Rapid7 dcerpc)", "BSD 3-Clause", "BSD-3-Rapid7.txt", "https://github.com/rapid7/smbj-rpc"),
    OssLib("Bouncy Castle", "MIT", "MIT-BouncyCastle.txt", "https://www.bouncycastle.org"),
    OssLib("AndroidX / Jetpack Compose", "Apache 2.0", "Apache-2.0.txt", "https://developer.android.com/jetpack/androidx"),
    OssLib("Kotlin, kotlinx.coroutines", "Apache 2.0", "Apache-2.0.txt", "https://github.com/Kotlin/kotlinx.coroutines"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName} ($code)"
        }.getOrDefault("")
    }
    // 대화상자에 띄울 (제목, assets 경로)
    var shown by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("앱 정보") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(headlineContent = { Text(context.applicationInfo.loadLabel(context.packageManager).toString()) }, supportingContent = { Text("버전 $version") })
                ListItem(
                    modifier = Modifier.clickable { shown = "개인정보처리방침" to "privacy_ko.txt" },
                    headlineContent = { Text("개인정보처리방침") },
                    supportingContent = { Text("이 앱은 개인정보를 수집하거나 외부로 보내지 않습니다") },
                )
                HorizontalDivider()
                Text(
                    "오픈소스 라이선스",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(OSS) { lib ->
                ListItem(
                    modifier = Modifier.clickable { shown = "${lib.name} · ${lib.license}" to "licenses/${lib.licenseFile}" },
                    headlineContent = { Text(lib.name) },
                    supportingContent = {
                        Text(listOf(lib.license, lib.url, lib.note).filter { it.isNotBlank() }.joinToString("\n"))
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))) }
                        }) { Text("웹") }
                    },
                )
            }
        }
    }

    shown?.let { (title, asset) ->
        val text = remember(asset) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { shown = null },
            confirmButton = { TextButton(onClick = { shown = null }) { Text("닫기") } },
            title = { Text(title) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}
