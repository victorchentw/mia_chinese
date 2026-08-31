package mia.chinese

import android.view.KeyEvent
import mia.chinese.playback.PlayerInputAction
import mia.chinese.playback.PlayerInputController
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerInputControllerTest {
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
}
