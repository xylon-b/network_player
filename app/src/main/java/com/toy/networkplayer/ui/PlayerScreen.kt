package com.toy.networkplayer.ui

import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import com.toy.networkplayer.data.Favorites
import android.app.Activity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.toy.networkplayer.data.ServerConfig
import com.toy.networkplayer.data.SmbEntry
import com.toy.networkplayer.data.WatchHistory
import com.toy.networkplayer.data.LocalStreamServer
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

private const val HIDE_DELAY_MS = 3500L
private const val SKIP_MS = 10_000L

@Composable
fun PlayerScreen(
    server: ServerConfig,
    videos: List<SmbEntry>,
    start: Int,
    subtitles: List<SmbEntry>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var index by remember { mutableIntStateOf(start.coerceIn(0, videos.lastIndex)) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    // 0: 폰 방향 따라가기, 1: 가로 고정, 2: 세로 고정
    var orientationMode by remember { mutableIntStateOf(0) }

    // 제스처/옵션 상태
    var locked by remember { mutableStateOf(false) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var gestureSeeking by remember { mutableStateOf(false) }
    var seekBase by remember { mutableLongStateOf(0L) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var lastLiveSeek by remember { mutableLongStateOf(0L) }
    var speedIndex by remember { mutableIntStateOf(SPEEDS.indexOf(1f)) }
    var holding by remember { mutableStateOf(false) }
    var scaleIndex by remember { mutableIntStateOf(0) }
    var rotation by remember { mutableIntStateOf(0) }
    var countedIndex by remember { mutableIntStateOf(-1) }
    // 재생 위치는 자주 바뀌니 화면 갱신을 일으키지 않는 보관함에 둔다.
    val lastSaved = remember { SavedPosition() }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudAt by remember { mutableLongStateOf(0L) }

    val libVlc = remember { LibVLC(context, arrayListOf("--network-caching=1500", "--audio-time-stretch")) }
    val player = remember { MediaPlayer(libVlc) }

    fun touch() {
        lastTouch = System.currentTimeMillis()
        controlsVisible = true
    }

    fun showHud(text: String) {
        hud = text
        hudAt = System.currentTimeMillis()
    }

    val liked = Favorites.isFavorite(videos[index])

    fun cycleOrientation() {
        orientationMode = (orientationMode + 1) % ORIENTATIONS.size
        showHud(ORIENTATIONS[orientationMode])
        touch()
    }

    fun like() {
        val on = Favorites.toggle(server, videos[index])
        showHud(if (on) "♥ 좋아요" else "좋아요 취소")
        touch()
    }

    fun clampTime(ms: Long) = if (length > 0) ms.coerceIn(0L, length) else ms.coerceAtLeast(0L)

    fun seekTo(ms: Long) {
        val target = clampTime(ms)
        player.setTime(target)
        time = target
    }

    fun skip(delta: Long) {
        seekTo(time + delta)
        showHud(if (delta > 0) "+${delta / 1000}초" else "${delta / 1000}초")
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun applySpeed() {
        player.setRate(if (holding) HOLD_SPEED else SPEEDS[speedIndex])
    }

    fun cycleTrack(tracks: Array<MediaPlayer.TrackDescription>?, current: Int, set: (Int) -> Unit, kind: String) {
        if (tracks.isNullOrEmpty()) {
            showHud("$kind 없음")
            return
        }
        val i = tracks.indexOfFirst { it.id == current }
        val next = tracks[(i + 1) % tracks.size]
        set(next.id)
        showHud("$kind: ${next.name}")
    }

    // 전체화면 + 화면 꺼짐 방지. 나갈 때 원래대로.
    DisposableEffect(Unit) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, view)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insets.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(orientationMode) {
        activity.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            // 들고 있는 방향 그대로. 폰의 자동 회전이 꺼져 있어도 센서를 따른다.
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    DisposableEffect(player) {
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    buffering = false
                    error = null
                    // 영상마다 처음 재생될 때 한 번만 시청 횟수를 올린다.
                    if (countedIndex != index) {
                        countedIndex = index
                        WatchHistory.markPlayed(server, videos[index].path)
                    }
                }
                MediaPlayer.Event.Vout -> { readVideoAspect(player)?.let { videoAspect = it } }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> { playing = false }
                MediaPlayer.Event.TimeChanged -> {
                    if (!dragging && !gestureSeeking) time = e.timeChanged
                    lastSaved.path = videos[index].path
                    lastSaved.positionMs = e.timeChanged
                }
                MediaPlayer.Event.LengthChanged -> {
                    length = e.lengthChanged
                    WatchHistory.setDuration(server, videos[index].path, e.lengthChanged)
                }
                MediaPlayer.Event.Buffering -> { buffering = e.buffering < 100f }
                MediaPlayer.Event.EndReached -> {
                    if (index < videos.lastIndex) {
                        index++
                    } else {
                        playing = false
                    }
                }
                MediaPlayer.Event.EncounteredError -> { error = "재생할 수 없는 파일이거나 연결이 끊겼어요"; buffering = false }
                else -> Unit
            }
        }
        onDispose {
            lastSaved.path?.let { WatchHistory.setPosition(server, it, lastSaved.positionMs) }
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    LaunchedEffect(index) {
        // 이전 영상의 위치를 남겨 둔다(바둑판의 진행 막대용).
        lastSaved.path?.let { WatchHistory.setPosition(server, it, lastSaved.positionMs) }
        time = 0L
        length = 0L
        buffering = true
        error = null
        val video = videos[index]
        // libVLC의 SMB 모듈 대신, 앱 안의 로컬 HTTP 서버를 거쳐 smbj로 읽는다.
        val media = Media(libVlc, LocalStreamServer.url(server, video.path))
        media.setHWDecoderEnabled(true, false)
        player.setMedia(media)
        media.release()
        // 같은 이름의 자막 파일(영상.smi, 영상.ko.srt 등)을 붙인다.
        subtitles
            .filter { it.baseName.equals(video.baseName, ignoreCase = true) || it.baseName.startsWith(video.baseName + ".", ignoreCase = true) }
            .forEachIndexed { i, sub ->
                player.addSlave(IMedia.Slave.Type.Subtitle, LocalStreamServer.url(server, sub.path), i == 0)
            }
        player.play()
        applySpeed()
        touch()
    }

    // 백그라운드로 가면 일시정지
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && player.isPlaying) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 재생 중에 손을 안 대면 제어바를 숨긴다. 옵션 패널이 열려 있으면 그대로 둔다.
    LaunchedEffect(controlsVisible, lastTouch, playing, dragging, panelOpen) {
        if (controlsVisible && playing && !dragging && !panelOpen) {
            delay(HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(hudAt, gestureSeeking) {
        if (!gestureSeeking && hud != null) {
            delay(900)
            hud = null
        }
    }

    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(HIDE_DELAY_MS)
            lockHintVisible = false
        }
    }

    val gestures = GestureCallbacks(
        onTap = {
            when {
                locked -> lockHintVisible = !lockHintVisible
                panelOpen -> panelOpen = false
                controlsVisible -> controlsVisible = false
                else -> touch()
            }
        },
        onDoubleTap = { zone ->
            when (zone) {
                TapZone.LEFT -> skip(-SKIP_MS)
                TapZone.RIGHT -> skip(SKIP_MS)
                TapZone.CENTER -> {
                    togglePlay()
                    showHud(if (player.isPlaying) "일시정지" else "재생")
                }
            }
        },
        onHoldStart = {
            holding = true
            applySpeed()
            hud = "${formatSpeed(HOLD_SPEED)} ▶▶"
            hudAt = Long.MAX_VALUE
        },
        onRelease = {
            if (holding) {
                holding = false
                applySpeed()
                showHud(formatSpeed(SPEEDS[speedIndex]))
            }
        },
        onSeekStart = {
            gestureSeeking = true
            seekBase = time
            seekTarget = time
            lastLiveSeek = 0L
        },
        onSeek = { fraction ->
            seekTarget = clampTime(seekBase + (fraction * SWIPE_RANGE_MS).toLong())
            time = seekTarget
            val diff = (seekTarget - seekBase) / 1000
            hud = "${if (diff >= 0) "+" else "-"}${formatTime(kotlin.math.abs(diff) * 1000)}\n${formatTime(seekTarget)} / ${formatTime(length)}"
            // 손가락을 따라 영상도 바로 움직이도록 짧은 간격으로 실제 탐색을 건다.
            val now = System.currentTimeMillis()
            if (now - lastLiveSeek > LIVE_SEEK_INTERVAL_MS) {
                lastLiveSeek = now
                player.setTime(seekTarget, true)
            }
        },
        onSeekEnd = {
            player.setTime(seekTarget)
            time = seekTarget
            gestureSeeking = false
            hudAt = System.currentTimeMillis()
        },
        onVertical = { leftSide, delta ->
            if (leftSide) {
                val window = activity.window
                if (brightness < 0f) {
                    val current = window.attributes.screenBrightness
                    brightness = if (current >= 0f) current else {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                    }
                }
                brightness = (brightness + delta * 1.3f).coerceIn(0.01f, 1f)
                window.attributes = window.attributes.apply { screenBrightness = brightness }
                showHud("밝기 ${(brightness * 100).roundToInt()}%")
            } else {
                volume = (volume + delta * maxVolume * 1.3f).coerceIn(0f, maxVolume.toFloat())
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.roundToInt(), 0)
                showHud("볼륨 ${(volume / maxVolume * 100).roundToInt()}%")
            }
        },
        onVerticalEnd = { hudAt = System.currentTimeMillis() },
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            // 회전을 위해 TextureView를 쓰고, 화면비는 영상 상자 크기로 직접 맞춘다(엔진은 상자를 꽉 채움).
            factory = { ctx ->
                VLCVideoLayout(ctx).also {
                    player.attachViews(it, null, true, true)
                    player.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
                }
            },
            modifier = Modifier.fillMaxSize().clipToBounds().videoBox(SCALES[scaleIndex], videoAspect, rotation),
        )

        GestureLayer(locked = locked, callbacks = gestures)

        if (buffering && error == null && !gestureSeeking) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        hud?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (locked) {
            if (lockHintVisible) {
                IconButton(
                    onClick = { locked = false; lockHintVisible = false; showHud("잠금 해제"); touch() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp)
                        .size(64.dp)
                        .background(Color(0x99000000), CircleShape),
                ) { Icon(Icons.Default.Lock, "잠금 해제", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
            return@Box
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    title = videos[index].name,
                    onBack = onBack,
                    liked = liked,
                    onLike = { like() },
                    onRotate = { cycleOrientation() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BottomBar(
                    playing = playing,
                    time = if (dragging) dragValue.toLong() else time,
                    length = length,
                    hasPrev = index > 0,
                    hasNext = index < videos.lastIndex,
                    onPlayPause = { togglePlay(); touch() },
                    onSkip = { delta -> seekTo(time + delta); touch() },
                    onPrev = { index--; touch() },
                    onNext = { index++; touch() },
                    onDrag = { v ->
                        dragging = true
                        dragValue = v
                        // 끌고 있는 동안에도 영상이 따라오도록 바로 이동시킨다.
                        player.setTime(v.toLong(), true)
                        touch()
                    },
                    onDragEnd = {
                        seekTo(dragValue.toLong())
                        dragging = false
                        touch()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        FloatingOptions(
            visible = controlsVisible,
            open = panelOpen,
            onOpenChange = { panelOpen = it; touch() },
            actions = FloatingActions(
                playing = playing,
                speedLabel = formatSpeed(SPEEDS[speedIndex]),
                scaleLabel = SCALES[scaleIndex].label,
                onPlayPause = { togglePlay(); touch() },
                onBack10 = { skip(-SKIP_MS); touch() },
                onForward10 = { skip(SKIP_MS); touch() },
                onSpeed = {
                    speedIndex = (speedIndex + 1) % SPEEDS.size
                    applySpeed()
                    showHud(formatSpeed(SPEEDS[speedIndex]))
                    touch()
                },
                onScale = {
                    scaleIndex = (scaleIndex + 1) % SCALES.size
                    showHud(SCALES[scaleIndex].label)
                    touch()
                },
                onSubtitle = {
                    cycleTrack(player.spuTracks, player.spuTrack, { player.setSpuTrack(it) }, "자막")
                    touch()
                },
                onAudio = {
                    cycleTrack(player.audioTracks, player.audioTrack, { player.setAudioTrack(it) }, "음성")
                    touch()
                },
                onRotate = {
                    rotation = (rotation + 90) % 360
                    showHud("영상 회전 ${rotation}°")
                    touch()
                },
                onLock = {
                    locked = true
                    panelOpen = false
                    controlsVisible = false
                    showHud("화면 잠금")
                },
                liked = liked,
                onLike = { like() },
                orientationLabel = ORIENTATIONS[orientationMode],
                onOrientation = { cycleOrientation() },
            ),
        )
    }
}

private val ORIENTATIONS = listOf("자동 회전", "가로 고정", "세로 고정")
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private const val HOLD_SPEED = 2f
private const val SWIPE_RANGE_MS = 120_000L
private const val LIVE_SEEK_INTERVAL_MS = 80L

private class SavedPosition {
    var path: String? = null
    var positionMs = 0L
}

private enum class FitMode { FIT, COVER, STRETCH }

/** 화면비 옵션. ratio가 null이면 영상 원래 비율을 쓴다. */
private class AspectOption(val label: String, val mode: FitMode, val ratio: Float?)

private val SCALES = listOf(
    AspectOption("맞춤", FitMode.FIT, null),
    AspectOption("꽉 차게", FitMode.COVER, null),
    AspectOption("늘이기", FitMode.STRETCH, null),
    AspectOption("16:9", FitMode.FIT, 16f / 9f),
    AspectOption("9:16", FitMode.FIT, 9f / 16f),
    AspectOption("4:3", FitMode.FIT, 4f / 3f),
    AspectOption("1:1", FitMode.FIT, 1f),
)

/** 영상 트랙의 표시 비율. 휴대폰 세로 영상처럼 회전 정보가 있으면 가로세로를 바꾼다. */
private fun readVideoAspect(player: MediaPlayer): Float? {
    val t = player.currentVideoTrack ?: return null
    if (t.width <= 0 || t.height <= 0) return null
    val sar = if (t.sarNum > 0 && t.sarDen > 0) t.sarNum.toFloat() / t.sarDen else 1f
    val aspect = t.width * sar / t.height
    val sideways = t.orientation == IMedia.VideoTrack.Orientation.LeftBottom ||
        t.orientation == IMedia.VideoTrack.Orientation.RightTop ||
        t.orientation == IMedia.VideoTrack.Orientation.LeftTop ||
        t.orientation == IMedia.VideoTrack.Orientation.RightBottom
    return if (sideways) 1f / aspect else aspect
}

/**
 * 영상 상자를 화면비 옵션과 회전(0/90/180/270)에 맞춰 배치한다.
 * 90/270도일 때는 가로세로를 바꿔 잰 뒤 돌려서 화면 가운데 놓는다.
 */
private fun Modifier.videoBox(option: AspectOption, videoAspect: Float, rotation: Int) = layout { measurable, constraints ->
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val sideways = rotation % 180 != 0
    val aspect = option.ratio ?: videoAspect
    // 돌린 뒤 화면에 보이는 비율
    val shown = if (sideways) 1f / aspect else aspect
    val (bw, bh) = when (option.mode) {
        FitMode.STRETCH -> w to h
        FitMode.FIT -> if (w / h > shown) h * shown to h else w to w / shown
        FitMode.COVER -> if (w / h > shown) w to w / shown else h * shown to h
    }
    val cw = (if (sideways) bh else bw).roundToInt().coerceAtLeast(1)
    val ch = (if (sideways) bw else bh).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(cw, ch))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeWithLayer(
            ((w - cw) / 2).roundToInt(),
            ((h - ch) / 2).roundToInt(),
        ) { rotationZ = rotation.toFloat() }
    }
}

private fun formatSpeed(rate: Float) = if (rate % 1f == 0f) "${rate.toInt()}.0배속" else "${rate}배속"

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    liked: Boolean,
    onLike: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White) }
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "좋아요",
                tint = if (liked) HEART else Color.White,
            )
        }
        IconButton(onClick = onRotate) { Icon(Icons.Default.ScreenRotation, "화면 방향", tint = Color.White) }
    }
}

@Composable
private fun BottomBar(
    playing: Boolean,
    time: Long,
    length: Long,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(time), color = Color.White)
            Slider(
                value = time.toFloat().coerceIn(0f, length.toFloat().coerceAtLeast(1f)),
                onValueChange = onDrag,
                onValueChangeFinished = onDragEnd,
                valueRange = 0f..length.toFloat().coerceAtLeast(1f),
                enabled = length > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x55FFFFFF),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(formatTime(length), color = Color.White)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = hasPrev) { Icon(Icons.Default.SkipPrevious, "이전", tint = barTint(hasPrev)) }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onSkip(-SKIP_MS) }) { Icon(Icons.Default.Replay10, "10초 뒤로", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "일시정지" else "재생",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onSkip(SKIP_MS) }) { Icon(Icons.Default.Forward10, "10초 앞으로", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onNext, enabled = hasNext) { Icon(Icons.Default.SkipNext, "다음", tint = barTint(hasNext)) }
        }
    }
}

private fun barTint(enabled: Boolean) = if (enabled) Color.White else Color(0x55FFFFFF)

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
