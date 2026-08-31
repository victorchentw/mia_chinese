package mia.chinese.playback

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState

/** Small native MediaSession adapter for TV remotes that do not dispatch keys to Compose. */
@Suppress("DEPRECATION")
internal class TvMediaSession(
    context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onRewind: () -> Unit,
    private val onFastForward: () -> Unit
) : AutoCloseable {
    private val session = MediaSession(context, "MiaChinesePlayer")

    init {
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = this@TvMediaSession.onPlay()
            override fun onPause() = this@TvMediaSession.onPause()
            override fun onSeekTo(pos: Long) {
                // Seek keys are handled by the player host; do not invent a
                // second absolute-position path here.
            }
            override fun onRewind() = this@TvMediaSession.onRewind()
            override fun onFastForward() = this@TvMediaSession.onFastForward()
        })
        session.isActive = true
    }

    fun update(isPlaying: Boolean, positionMs: Long) {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0L),
                    if (isPlaying) 1f else 0f
                )
                .build()
        )
    }

    override fun close() {
        session.isActive = false
        session.release()
    }
}
