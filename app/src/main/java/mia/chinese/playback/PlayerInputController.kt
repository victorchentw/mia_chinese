package mia.chinese.playback

import android.os.SystemClock
import android.view.KeyEvent

enum class PlayerInputSource {
    KEY_EVENT,
    MEDIA_SESSION
}

enum class PlayerInputAction {
    SHOW_CONTROLS,
    TOGGLE_PLAY_PAUSE,
    PLAY,
    PAUSE,
    SEEK_BACKWARD,
    SEEK_FORWARD
}

/**
 * Keeps TV remote mapping in one place so D-pad and media-key handling cannot
 * drift between MP4 and the future YouTube player.
 */
object PlayerInputController {
    private const val DEDUPE_WINDOW_MS = 250L
    private var lastAction: PlayerInputAction? = null
    private var lastSource: PlayerInputSource? = null
    private var lastDispatchAtMs: Long = Long.MIN_VALUE

    /** Treat long-press repeats as consumed but not as extra commands. */
    fun shouldHandle(keyEvent: KeyEvent): Boolean =
        shouldHandle(keyEvent.action, keyEvent.repeatCount)

    /**
     * A platform may deliver one media-key press to both the focused view and
     * MediaSession. Suppress only the cross-route duplicate (and identical
     * same-route callbacks); normal D-pad/key repeat filtering remains above.
     */
    @Synchronized
    fun accept(
        action: PlayerInputAction,
        source: PlayerInputSource,
        nowMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        val elapsed = nowMs - lastDispatchAtMs
        val duplicate = elapsed in 0 until DEDUPE_WINDOW_MS &&
            (source != lastSource || action == lastAction)
        if (duplicate) return false
        lastAction = action
        lastSource = source
        lastDispatchAtMs = nowMs
        return true
    }

    fun shouldHandle(action: Int, repeatCount: Int): Boolean =
        action == KeyEvent.ACTION_DOWN && repeatCount == 0

    fun actionFor(keyCode: Int): PlayerInputAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerInputAction.SHOW_CONTROLS

        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerInputAction.SEEK_BACKWARD

        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerInputAction.SEEK_FORWARD

        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerInputAction.TOGGLE_PLAY_PAUSE

        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerInputAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerInputAction.PAUSE
        else -> null
    }
}
