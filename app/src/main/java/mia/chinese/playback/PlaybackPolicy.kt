package mia.chinese.playback

import mia.chinese.BuildConfig

/**
 * YouTube remains a debug-gate feature until a supported target TV is signed
 * off. Debug builds expose it for the spike; release builds stay MP4-only
 * rather than presenting a half-working production entry point.
 */
object PlaybackPolicy {
    val youtubeEnabled: Boolean
        get() = BuildConfig.DEBUG

    fun isPlayable(video: mia.chinese.model.VideoItem): Boolean = when {
        video.isMp4 -> video.url?.startsWith("https://") == true
        video.isYouTube -> youtubeEnabled && !video.videoId.isNullOrBlank()
        else -> false
    }
}
