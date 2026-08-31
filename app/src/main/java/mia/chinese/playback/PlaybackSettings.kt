package mia.chinese.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user-selected way to start YouTube videos on the TV. */
enum class YoutubePlaybackMode {
    WEBVIEW,
    EXTERNAL
}

class PlaybackSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _youtubePlaybackMode = MutableStateFlow(readMode())

    val youtubePlaybackMode: StateFlow<YoutubePlaybackMode> = _youtubePlaybackMode.asStateFlow()

    fun setYoutubePlaybackMode(mode: YoutubePlaybackMode) {
        preferences.edit()
            .putString(KEY_YOUTUBE_MODE, mode.name)
            .apply()
        _youtubePlaybackMode.value = mode
    }

    private fun readMode(): YoutubePlaybackMode = runCatching {
        YoutubePlaybackMode.valueOf(
            preferences.getString(KEY_YOUTUBE_MODE, null).orEmpty()
        )
    }.getOrDefault(YoutubePlaybackMode.WEBVIEW)

    private companion object {
        const val PREFERENCES_NAME = "playback_settings"
        const val KEY_YOUTUBE_MODE = "youtube_playback_mode"
    }
}
