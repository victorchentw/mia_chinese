package mia.chinese.playback

import android.view.KeyEvent

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
