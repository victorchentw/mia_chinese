package mia.chinese.ui

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mia.chinese.data.ProgressRepository
import mia.chinese.data.ProgressStatus
import mia.chinese.model.VideoLocation
import mia.chinese.playback.PlayerInputAction
import mia.chinese.playback.PlayerInputController
import mia.chinese.playback.PlayerInputSource
import mia.chinese.playback.TvMediaSession
import mia.chinese.playback.seekPosition
import org.json.JSONObject

private const val YOUTUBE_ASSET_ORIGIN = "https://appassets.androidplatform.net"
private const val YOUTUBE_PLAYER_PATH = "/assets/player/youtube_player.html"
private const val YOUTUBE_READY_TIMEOUT_MS = 15_000L
private const val YOUTUBE_CHECKPOINT_INTERVAL_MS = 5_000L
private const val YOUTUBE_PLAYBACK_WATCHDOG_MS = 12_000L

@Composable
internal fun YouTubePlayerScreen(
    location: VideoLocation,
    startPositionMs: Long,
    progressRepository: ProgressRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val sessionId = remember(location.video.id, location.video.revision) {
        "${location.video.id}:${location.video.revision}"
    }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var isReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(startPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableStateOf(location.video.durationMs) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var backInProgress by remember { mutableStateOf(false) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    fun evaluate(functionCall: String) {
        webViewState.value?.post {
            webViewState.value?.evaluateJavascript("$functionCall;", null)
        }
    }

    fun save(
        status: ProgressStatus = if (hasEnded) {
            ProgressStatus.COMPLETED
        } else if (hasStarted) {
            ProgressStatus.IN_PROGRESS
        } else {
            ProgressStatus.NOT_STARTED
        }
    ) {
        scope.launch {
            progressRepository.saveCheckpoint(
                location = location,
                positionMs = positionMs,
                durationMs = durationMs,
                status = status
            )
        }
    }

    fun play() {
        hasStarted = true
        hasEnded = false
        errorMessage = null
        evaluate("window.miaYouTubePlay()")
        save(ProgressStatus.IN_PROGRESS)
    }

    fun pause() {
        evaluate("window.miaYouTubePause()")
        save(if (hasEnded) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS)
    }

    fun seekBy(deltaMs: Long) {
        val target = seekPosition(positionMs, deltaMs, durationMs)
        positionMs = target
        seekFeedback = if (deltaMs < 0L) "↶ 5 秒" else "5 秒 ↷"
        overlayVisible = true
        evaluate("window.miaYouTubeSeekTo(${target / 1000.0})")
        save(if (hasEnded) ProgressStatus.COMPLETED else if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED)
    }

    fun replay() {
        positionMs = 0L
        hasEnded = false
        evaluate("window.miaYouTubeSeekTo(0)")
        play()
    }

    val latestSave = rememberUpdatedState { save() }
    val latestPlay = rememberUpdatedState { play() }
    val latestPause = rememberUpdatedState { pause() }
    val latestSeekBy = rememberUpdatedState { deltaMs: Long -> seekBy(deltaMs) }
    val latestReplay = rememberUpdatedState { replay() }
    val latestHasEnded = rememberUpdatedState(hasEnded)
    val latestDurationMs = rememberUpdatedState(durationMs)

    val mediaSession = remember(sessionId) {
        TvMediaSession(
            context = context,
            onPlay = {
                if (PlayerInputController.accept(PlayerInputAction.PLAY, PlayerInputSource.MEDIA_SESSION)) {
                    if (latestHasEnded.value) latestReplay.value() else latestPlay.value()
                }
            },
            onPause = {
                if (PlayerInputController.accept(PlayerInputAction.PAUSE, PlayerInputSource.MEDIA_SESSION)) {
                    latestPause.value()
                }
            },
            onRewind = {
                if (PlayerInputController.accept(PlayerInputAction.SEEK_BACKWARD, PlayerInputSource.MEDIA_SESSION)) {
                    latestSeekBy.value(-5_000L)
                }
            },
            onFastForward = {
                if (PlayerInputController.accept(PlayerInputAction.SEEK_FORWARD, PlayerInputSource.MEDIA_SESSION)) {
                    latestSeekBy.value(5_000L)
                }
            }
        )
    }

    DisposableEffect(mediaSession) {
        onDispose { mediaSession.close() }
    }

    LaunchedEffect(mediaSession, isPlaying, positionMs, hasEnded) {
        mediaSession.update(isPlaying = isPlaying, positionMs = positionMs)
    }

    fun checkpointAndClose() {
        if (backInProgress) return
        backInProgress = true
        scope.launch {
            progressRepository.saveCheckpoint(
                location = location,
                positionMs = positionMs,
                durationMs = durationMs,
                status = if (hasEnded) ProgressStatus.COMPLETED else if (hasStarted) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED
            )
            evaluate("window.miaYouTubePause()")
            webViewState.value?.stopLoading()
            onBack()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(rootView) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) {
                evaluate("window.miaYouTubePause()")
                latestSave.value()
            }
        }
        rootView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { rootView.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, webViewState.value) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewState.value?.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webViewState.value?.onPause()
                    evaluate("window.miaYouTubePause()")
                    latestSave.value()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(location.video.id, location.video.revision) {
        focusRequester.requestFocus()
        delay(YOUTUBE_READY_TIMEOUT_MS)
        if (!isReady && errorMessage == null) {
            errorMessage = "YouTube 影片載入逾時，請檢查網路或稍後重試。"
        }
    }

    LaunchedEffect(sessionId, hasStarted, hasEnded) {
        while (isActive) {
            delay(YOUTUBE_CHECKPOINT_INTERVAL_MS)
            if (hasStarted && !hasEnded) latestSave.value()
        }
    }

    LaunchedEffect(isReady, isPlaying) {
        if (isReady && isPlaying && errorMessage == null) {
            val positionAtStart = latestPositionMs
            delay(YOUTUBE_PLAYBACK_WATCHDOG_MS)
            if (latestIsPlaying && latestPositionMs <= positionAtStart + 500L) {
                isPlaying = false
                errorMessage = "YouTube 影片未開始播放，請重新嘗試或返回課程。"
            }
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1_000L)
            seekFeedback = null
        }
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
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (!PlayerInputController.shouldHandle(event.nativeKeyEvent)) {
                return@onPreviewKeyEvent true
            }
            val action = PlayerInputController.actionFor(event.nativeKeyEvent.keyCode)
                ?: return@onPreviewKeyEvent false
            if (!PlayerInputController.accept(action, PlayerInputSource.KEY_EVENT)) {
                return@onPreviewKeyEvent true
            }
            overlayVisible = true
            when (action) {
                PlayerInputAction.SHOW_CONTROLS -> Unit
                PlayerInputAction.TOGGLE_PLAY_PAUSE -> if (isPlaying) pause() else play()
                PlayerInputAction.PLAY -> play()
                PlayerInputAction.PAUSE -> pause()
                PlayerInputAction.SEEK_BACKWARD -> seekBy(-5_000L)
                PlayerInputAction.SEEK_FORWARD -> seekBy(5_000L)
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
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(viewContext))
                    .build()
                WebView(viewContext).apply {
                    setBackgroundColor(Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // TV remotes do not generate a WebView touch gesture; the
                    // focused Play action is the explicit user consent instead.
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = !isAllowedUrl(request.url)

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail
                        ): Boolean {
                            isReady = false
                            isPlaying = false
                            isBuffering = false
                            errorMessage = "YouTube 播放元件已停止，請重新嘗試或返回課程。"
                            return true
                        }
                    }
                    addJavascriptInterface(
                        YouTubeBridge(sessionId) { message ->
                            handleYouTubeMessage(
                                message = message,
                                expectedSession = sessionId,
                                onReady = { duration ->
                                    isReady = true
                                    durationMs = duration ?: latestDurationMs.value
                                    errorMessage = null
                                    if (startPositionMs > 0L) {
                                        evaluate("window.miaYouTubeSeekTo(${startPositionMs / 1000.0})")
                                    }
                                },
                                onProgress = { position, duration ->
                                    positionMs = position.coerceAtLeast(0L)
                                    if (duration > 0L) durationMs = duration
                                },
                                onPlaying = {
                                    isPlaying = true
                                    isBuffering = false
                                    hasStarted = true
                                    hasEnded = false
                                },
                                onPaused = {
                                    isPlaying = false
                                    isBuffering = false
                                },
                                onBuffering = {
                                    isPlaying = false
                                    isBuffering = true
                                },
                                onEnded = {
                                    isPlaying = false
                                    isBuffering = false
                                    hasEnded = true
                                    scope.launch {
                                        progressRepository.markCompleted(location, latestDurationMs.value)
                                    }
                                },
                                onError = {
                                    isPlaying = false
                                    isBuffering = false
                                    errorMessage = "YouTube 影片目前無法播放，可能已下架、禁止嵌入或受到地區限制。"
                                    latestSave.value()
                                }
                            )
                        },
                        "AndroidPlayer"
                    )
                    webViewState.value = this
                    loadUrl(youtubePlayerUrl(location.video.videoId.orEmpty(), sessionId))
                }
            },
            update = { webViewState.value = it },
            modifier = Modifier.fillMaxSize()
        )
        if (overlayVisible || !isPlaying || !isReady || isBuffering || errorMessage != null) {
            PlayerOverlay(
                location = location,
                isPlaying = isPlaying,
                isCompleted = hasEnded,
                isBuffering = isBuffering,
                seekFeedback = seekFeedback,
                positionMs = positionMs,
                durationMs = durationMs,
                errorMessage = errorMessage,
                onToggle = {
                    if (hasEnded) {
                        replay()
                    } else if (isPlaying) {
                        pause()
                    } else {
                        play()
                    }
                },
                onSeekBack = { seekBy(-5_000L) },
                onSeekForward = { seekBy(5_000L) },
                onRetry = {
                    errorMessage = null
                    isReady = false
                    hasEnded = false
                    webViewState.value?.loadUrl(youtubePlayerUrl(location.video.videoId.orEmpty(), sessionId))
                },
                onBack = ::checkpointAndClose
            )
        }
    }

    DisposableEffect(location.video.id, location.video.revision) {
        onDispose {
            webViewState.value?.removeJavascriptInterface("AndroidPlayer")
            webViewState.value?.onPause()
            webViewState.value?.stopLoading()
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }
}

private fun youtubePlayerUrl(videoId: String, sessionId: String): String =
    Uri.Builder()
        .scheme("https")
        .authority("appassets.androidplatform.net")
        .path(YOUTUBE_PLAYER_PATH)
        .appendQueryParameter("videoId", videoId)
        .appendQueryParameter("session", sessionId)
        .build()
        .toString()

private fun isAllowedUrl(uri: Uri): Boolean {
    if (uri.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return host == "appassets.androidplatform.net" ||
        host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
}

private class YouTubeBridge(
    private val expectedSession: String,
    private val onMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(message: String) {
        val session = runCatching { JSONObject(message).optString("session") }.getOrNull()
        if (session != expectedSession) return
        mainHandler.post { onMessage(message) }
    }
}

private fun handleYouTubeMessage(
    message: String,
    expectedSession: String,
    onReady: (Long?) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onPlaying: () -> Unit,
    onPaused: () -> Unit,
    onBuffering: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit
) {
    val json = runCatching { JSONObject(message) }.getOrNull() ?: return
    if (json.optString("session") != expectedSession) return
    when (json.optString("event")) {
        "ready" -> onReady(json.optLong("durationMs").takeIf { it > 0L })
        "progress" -> onProgress(
            json.optLong("positionMs").coerceAtLeast(0L),
            json.optLong("durationMs").coerceAtLeast(0L)
        )
        "state" -> when (json.optString("state")) {
            "playing" -> onPlaying()
            "paused", "cued", "unstarted" -> onPaused()
            "buffering" -> onBuffering()
            "ended" -> onEnded()
        }
        "error", "autoplayBlocked" -> onError()
    }
}
