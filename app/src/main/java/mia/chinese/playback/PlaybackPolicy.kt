package mia.chinese.playback

import mia.chinese.BuildConfig

/**
 * YouTube remains a gate until a supported target TV is signed off. This test
 * build uses the system WebView first in both variants; set
 * -PmiaEnableYoutubeWebView=false for a conservative MP4-only build.
 */
object PlaybackPolicy {
    val youtubeEnabled: Boolean
        get() = BuildConfig.YOUTUBE_WEBVIEW_ENABLED

    fun isPlayable(video: mia.chinese.model.VideoItem): Boolean = when {
        video.isMp4 -> video.url?.startsWith("https://") == true
        video.isYouTube -> youtubeEnabled && !video.videoId.isNullOrBlank()
        else -> false
    }
}
