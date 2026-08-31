package mia.chinese.ui

import mia.chinese.data.ProgressStatus
import mia.chinese.data.VideoProgressEntity
import mia.chinese.model.VideoItem

fun formatDuration(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun VideoItem.progressFrom(progress: List<VideoProgressEntity>): VideoProgressEntity? =
    progress.firstOrNull { it.videoId == id && it.revision == revision }

fun progressLabel(progress: VideoProgressEntity?): String = when {
    progress?.status == ProgressStatus.COMPLETED.name -> "已完成・重新觀看"
    progress != null && progress.positionMs > 0L -> "進行中 ${formatDuration(progress.positionMs)}"
    else -> "尚未觀看"
}
