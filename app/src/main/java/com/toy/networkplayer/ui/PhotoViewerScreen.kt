package com.toy.networkplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.SmbRepository

/** 같은 폴더의 사진을 좌우로 넘겨 본다. 핀치로 확대, 더블탭으로 원래 크기. */
@Composable
fun PhotoViewerScreen(server: ServerConfig, images: List<SmbEntry>, start: Int, onBack: () -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
    var chrome by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            ZoomablePhoto(server, images[page], onTap = { chrome = !chrome })
        }
        if (chrome) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x88000000)).statusBarsPadding().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White) }
                Text(
                    "${pager.currentPage + 1}/${images.size}  ${images.getOrNull(pager.currentPage)?.name ?: ""}",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(server: ServerConfig, entry: SmbEntry, onTap: () -> Unit) {
    val bitmap by produceState<Result<Bitmap?>?>(null, entry.path) {
        value = runCatching { SmbRepository.loadBitmap(server, entry.path) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(scale > 1f) {
                // 확대 안 된 상태에서는 좌우 스와이프를 페이저에 양보하려고, 확대 중일 때만 이동을 받는다.
                if (scale > 1f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                } else {
                    // 손가락 두 개일 때만 확대를 잡는다. 한 손가락 스와이프는 그대로 페이저로 간다.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed } && scale == 1f)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val result = bitmap
        when {
            result == null -> CircularProgressIndicator(color = Color.White)
            result.getOrNull() != null -> Image(
                bitmap = result.getOrNull()!!.asImageBitmap(),
                contentDescription = entry.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
            else -> Text(
                "사진을 불러오지 못했어요\n${result.exceptionOrNull()?.message ?: "지원하지 않는 형식"}",
                color = Color.White,
            )
        }
    }
}
