package mia.chinese.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mia.chinese.data.ProgressRepository
import mia.chinese.data.ProgressStatus
import mia.chinese.model.VideoLocation
import mia.chinese.playback.PlayerInputAction
import mia.chinese.playback.PlayerInputController

private const val SEEK_MS = 5_000L
private const val CHECKPOINT_INTERVAL_MS = 5_000L

@Composable
fun PlayerScreen(
    location: VideoLocation,
    startPositionMs: Long,
    progressRepository: ProgressRepository,
    onBack: () -> Unit
) {
    if (!location.video.isMp4 || location.video.url.isNullOrBlank()) {
        UnsupportedPlayerScreen(location = location, onBack = onBack)
    } else {
        Mp4PlayerScreen(
            location = location,
            startPositionMs = startPositionMs,
            progressRepository = progressRepository,
            onBack = onBack
        )
    }
}

@Composable
private fun UnsupportedPlayerScreen(location: VideoLocation, onBack: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(72.dp),
        contentAlignment = Alignment.Center
    ) {
        TvPanel(modifier = Modifier.width(720.dp)) {
            Text("影片目前無法播放", style = MaterialTheme.typography.h5)
            Text(
                "${location.section.title} 使用 YouTube 來源；v0.1.0 尚未通過 Go/No-Go，因此暫不啟用。",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 12.dp)
            )
            TvAction(
                onClick = onBack,
                focusRequester = requester,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            ) {
                Text("返回課程")
            }
        }
    }
}

@Composable
private fun Mp4PlayerScreen(
    location: VideoLocation,
    startPositionMs: Long,
    progressRepository: ProgressRepository,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val player = remember(location.video.id, location.video.revision) {
        ExoPlayer.Builder(context).build()
    }
    val focusRequester = remember { FocusRequester() }
    var isPlaying by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(startPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableStateOf(location.video.durationMs) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var backInProgress by remember { mutableStateOf(false) }

    fun currentPosition(): Long = player.currentPosition.coerceAtLeast(0L)
    fun currentDuration(): Long? = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }

    fun save(status: ProgressStatus = if (hasEnded) ProgressStatus.COMPLETED else if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED) {
        val position = currentPosition()
        val duration = currentDuration() ?: durationMs
        scope.launch {
            progressRepository.saveCheckpoint(
                location = location,
                positionMs = position,
                durationMs = duration,
                status = status
            )
        }
    }

    fun play() {
        hasStarted = true
        hasEnded = false
        errorMessage = null
        player.play()
        save(ProgressStatus.IN_PROGRESS)
    }

    fun pause() {
        player.pause()
        save(if (hasEnded) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS)
    }

    fun seekBy(deltaMs: Long) {
        val current = currentPosition()
        val duration = currentDuration()
        val target = if (duration != null) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        player.seekTo(target)
        positionMs = target
        overlayVisible = true
        save(if (hasEnded) ProgressStatus.COMPLETED else if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED)
    }

    fun checkpointAndClose() {
        if (backInProgress) return
        backInProgress = true
        scope.launch {
            progressRepository.saveCheckpoint(
                location = location,
                positionMs = currentPosition(),
                durationMs = currentDuration() ?: durationMs,
                status = if (hasEnded) ProgressStatus.COMPLETED else if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED
            )
            player.pause()
            onBack()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(player, location.video.id, location.video.revision) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isReady = true
                        durationMs = currentDuration() ?: durationMs
                    }
                    Player.STATE_ENDED -> {
                        hasEnded = true
                        isPlaying = false
                        val duration = currentDuration() ?: durationMs
                        durationMs = duration
                        scope.launch {
                            progressRepository.markCompleted(location, duration)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) hasStarted = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMessage = "教材影片連結失效或目前無法播放。"
                save(if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED)
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(location.video.url!!))
        player.seekTo(startPositionMs.coerceAtLeast(0L))
        player.playWhenReady = false
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        focusRequester.requestFocus()
        while (isActive) {
            positionMs = currentPosition()
            durationMs = currentDuration() ?: durationMs
            delay(250L)
        }
    }

    LaunchedEffect(player, hasStarted, hasEnded) {
        while (isActive) {
            delay(CHECKPOINT_INTERVAL_MS)
            if (hasStarted && !hasEnded) save(ProgressStatus.IN_PROGRESS)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) save()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(overlayVisible, isPlaying) {
        if (overlayVisible && isPlaying) {
            delay(4_000L)
            overlayVisible = false
        }
    }

    BackHandler(enabled = true, onBack = ::checkpointAndClose)

    val inputModifier = Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onFocusChanged { if (it.isFocused) overlayVisible = true }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val action = PlayerInputController.actionFor(event.nativeKeyEvent.keyCode)
                ?: return@onKeyEvent false
            overlayVisible = true
            when (action) {
                PlayerInputAction.SHOW_CONTROLS -> Unit
                PlayerInputAction.TOGGLE_PLAY_PAUSE -> if (isPlaying) pause() else play()
                PlayerInputAction.PLAY -> play()
                PlayerInputAction.PAUSE -> pause()
                PlayerInputAction.SEEK_BACKWARD -> seekBy(-SEEK_MS)
                PlayerInputAction.SEEK_FORWARD -> seekBy(SEEK_MS)
            }
            true
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .then(inputModifier)
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (overlayVisible || !isPlaying || !isReady || errorMessage != null) {
            PlayerOverlay(
                location = location,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                errorMessage = errorMessage,
                onToggle = { if (isPlaying) pause() else play() },
                onSeekBack = { seekBy(-SEEK_MS) },
                onSeekForward = { seekBy(SEEK_MS) },
                onRetry = {
                    errorMessage = null
                    player.seekTo(positionMs)
                    player.prepare()
                },
                onBack = ::checkpointAndClose
            )
        }
    }
}

@Composable
private fun PlayerOverlay(
    location: VideoLocation,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long?,
    errorMessage: String?,
    onToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 64.dp, vertical = 42.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Text(
                "${location.course.title}・${location.section.title}",
                style = MaterialTheme.typography.h6,
                color = androidx.compose.ui.graphics.Color.White
            )
            Text(
                "${formatDuration(positionMs)} / ${durationMs?.let(::formatDuration) ?: "--:--"}",
                style = MaterialTheme.typography.body1,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvAction(onClick = onSeekBack, modifier = Modifier.width(150.dp)) {
                    Text("↶ 5 秒")
                }
                TvAction(onClick = onToggle, modifier = Modifier.width(190.dp)) {
                    Text(if (isPlaying) "❚❚ 暫停" else "▶ 播放")
                }
                TvAction(onClick = onSeekForward, modifier = Modifier.width(150.dp)) {
                    Text("5 秒 ↷")
                }
                if (errorMessage != null) {
                    TvAction(onClick = onRetry, modifier = Modifier.width(170.dp)) {
                        Text("重新嘗試")
                    }
                }
                Spacer(Modifier.weight(1f))
                TvAction(onClick = onBack, modifier = Modifier.width(160.dp)) {
                    Text("返回課程")
                }
            }
            if (!isPlaying && errorMessage == null) {
                Text(
                    "請完成課本標注後按播放繼續",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            errorMessage?.let {
                Text(
                    text = "$it 可重新嘗試或返回課程。",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
