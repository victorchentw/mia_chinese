package mia.chinese

import android.view.KeyEvent
import mia.chinese.playback.PlayerInputAction
import mia.chinese.playback.PlayerInputController
import mia.chinese.playback.PlayerInputSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInputControllerTest {
    @Test
    fun repeatedKeyDownIsIgnoredToPreventDuplicateCommands() {
        assertTrue(PlayerInputController.shouldHandle(KeyEvent.ACTION_DOWN, 0))
        assertTrue(!PlayerInputController.shouldHandle(KeyEvent.ACTION_DOWN, 1))
        assertTrue(!PlayerInputController.shouldHandle(KeyEvent.ACTION_UP, 0))
    }

    @Test
    fun mediaKeysMapToOneAction() {
        assertEquals(
            PlayerInputAction.TOGGLE_PLAY_PAUSE,
            PlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
        assertEquals(
            PlayerInputAction.PLAY,
            PlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY)
        )
        assertEquals(
            PlayerInputAction.PAUSE,
            PlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PAUSE)
        )
        assertEquals(
            PlayerInputAction.SEEK_BACKWARD,
            PlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_REWIND)
        )
        assertEquals(
            PlayerInputAction.SEEK_FORWARD,
            PlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        )
    }

    @Test
    fun mediaSessionDuplicateIsSuppressedAcrossInputRoutes() {
        assertTrue(
            PlayerInputController.accept(
                PlayerInputAction.TOGGLE_PLAY_PAUSE,
                PlayerInputSource.KEY_EVENT,
                nowMs = 10_000L
            )
        )
        assertTrue(
            !PlayerInputController.accept(
                PlayerInputAction.PAUSE,
                PlayerInputSource.MEDIA_SESSION,
                nowMs = 10_100L
            )
        )
        assertTrue(
            PlayerInputController.accept(
                PlayerInputAction.PAUSE,
                PlayerInputSource.MEDIA_SESSION,
                nowMs = 10_400L
            )
        )
    }
}
