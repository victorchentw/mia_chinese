package mia.chinese

import mia.chinese.playback.clampPosition
import mia.chinese.playback.seekPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {
    @Test
    fun clampHandlesKnownAndUnknownDuration() {
        assertEquals(0L, clampPosition(-10L, 100L))
        assertEquals(100L, clampPosition(120L, 100L))
        assertEquals(120L, clampPosition(120L, null))
    }

    @Test
    fun seekUsesLatestPositionAndDurationBounds() {
        assertEquals(5_000L, seekPosition(10_000L, -5_000L, 20_000L))
        assertEquals(20_000L, seekPosition(19_000L, 5_000L, 20_000L))
        assertEquals(0L, seekPosition(2_000L, -5_000L, 20_000L))
    }
}
