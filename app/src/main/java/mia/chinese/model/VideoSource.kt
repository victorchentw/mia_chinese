package mia.chinese.model

sealed interface VideoSource {
    data class Mp4(val url: String, val managedAssetId: String? = null) : VideoSource
    data class YouTube(val videoId: String) : VideoSource
}

fun VideoItem.sourceOrNull(): VideoSource? = when {
    isMp4 -> url?.takeIf { it.startsWith("https://") }?.let {
        VideoSource.Mp4(it, managedAssetId)
    }
    isYouTube -> videoId?.takeIf { it.isNotBlank() }?.let(VideoSource::YouTube)
    else -> null
}
