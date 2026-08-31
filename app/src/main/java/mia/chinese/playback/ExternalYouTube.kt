package mia.chinese.playback

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a YouTube watch URL in an installed TV player without attempting to
 * extract a stream URL or bypass YouTube restrictions.
 */
enum class ExternalYouTubePlayer {
    SMART_TUBE,
    OTHER,
    NONE
}

data class ExternalYouTubeLaunchResult(
    val player: ExternalYouTubePlayer,
    val message: String
)

private val smartTubePackages = listOf(
    // SmartTube stable and beta builds use different application IDs.
    "com.liskovsoft.smarttubetv",
    "com.liskovsoft.smarttubetv.beta"
)

fun youtubeWatchUri(videoId: String): Uri = Uri.Builder()
    .scheme("https")
    .authority("www.youtube.com")
    .path("watch")
    .appendQueryParameter("v", videoId)
    .build()

fun launchExternalYouTube(context: Context, videoId: String): ExternalYouTubeLaunchResult {
    val id = videoId.trim()
    if (id.isEmpty()) {
        return ExternalYouTubeLaunchResult(
            player = ExternalYouTubePlayer.NONE,
            message = "這支 YouTube 影片沒有有效的影片 ID。"
        )
    }

    val uri = youtubeWatchUri(id)
    val packageManager = context.packageManager
    smartTubePackages.forEach { packageName ->
        val intent = externalViewIntent(uri, packageName, context)
        if (intent.resolveActivity(packageManager) != null && startSafely(context, intent)) {
            return ExternalYouTubeLaunchResult(
                player = ExternalYouTubePlayer.SMART_TUBE,
                message = "已交給 SmartTube 播放；外部播放器不會回報即時播放秒數。"
            )
        }
    }

    val genericIntent = externalViewIntent(uri, packageName = null, context = context)
    if (genericIntent.resolveActivity(packageManager) != null && startSafely(context, genericIntent)) {
        return ExternalYouTubeLaunchResult(
            player = ExternalYouTubePlayer.OTHER,
            message = "已交給系統 YouTube／瀏覽器播放；外部播放器不會回報即時播放秒數。"
        )
    }

    return ExternalYouTubeLaunchResult(
        player = ExternalYouTubePlayer.NONE,
        message = "找不到 SmartTube 或其他可開啟 YouTube 的 Android TV App。"
    )
}

private fun externalViewIntent(uri: Uri, packageName: String?, context: Context): Intent =
    Intent(Intent.ACTION_VIEW, uri).apply {
        if (packageName != null) setPackage(packageName)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private fun startSafely(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
