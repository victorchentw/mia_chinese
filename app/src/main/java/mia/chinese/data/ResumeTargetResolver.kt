package mia.chinese.data

import mia.chinese.model.Catalog
import mia.chinese.model.VideoLocation
import mia.chinese.model.allVideoLocations
import mia.chinese.model.findVideo
import mia.chinese.playback.PlaybackPolicy

/** Result used by Home so stale content never opens an empty player route. */
data class ResumeTargetResolution(
    val location: VideoLocation?,
    val fallback: VideoLocation?,
    val staleReason: String?
)

object ResumeTargetResolver {
    fun resolve(
        catalog: Catalog,
        pointer: LastResumePointerEntity?,
        progress: List<VideoProgressEntity> = emptyList()
    ): ResumeTargetResolution {
        if (pointer == null) return ResumeTargetResolution(null, null, null)

        val candidate = catalog.findVideo(pointer.videoId)
        val sameCourseVideos = catalog.editions
            .firstOrNull { it.id == pointer.editionId }
            ?.courses
            ?.firstOrNull { it.id == pointer.courseId }
            ?.let { course ->
                catalog.allVideoLocations().filter {
                    it.edition.id == pointer.editionId &&
                        it.course.id == course.id &&
                        it.video.id != pointer.videoId
                }
            }
            .orEmpty()
        val fallback = sameCourseVideos.firstOrNull(::isPlayable)

        if (candidate == null) {
            return ResumeTargetResolution(null, fallback, "教材內容已更新，找不到上次的影片。")
        }
        if (candidate.edition.id != pointer.editionId ||
            candidate.course.id != pointer.courseId ||
            candidate.section.id != pointer.sectionId
        ) {
            return ResumeTargetResolution(null, fallback, "教材內容已更新，上次影片的位置已變更。")
        }
        if (candidate.video.revision != pointer.revision) {
            return ResumeTargetResolution(null, fallback, "教材影片已更新，請從新版本重新開始。")
        }
        if (!isPlayable(candidate)) {
            return ResumeTargetResolution(null, fallback, "上次影片目前沒有可用的播放來源。")
        }

        // A malformed old row must not make the Home card claim a bad time.
        val saved = progress.firstOrNull {
            it.videoId == candidate.video.id && it.revision == candidate.video.revision
        }
        if (saved?.status == ProgressStatus.STALE.name ||
            (saved != null && (saved.positionMs < 0L || saved.durationMs?.let { it <= 0L } == true))
        ) {
            return ResumeTargetResolution(null, fallback, "上次播放進度資料需要重新建立。")
        }
        return ResumeTargetResolution(candidate, null, null)
    }

    private fun isPlayable(location: VideoLocation): Boolean =
        PlaybackPolicy.isPlayable(location.video)
}
