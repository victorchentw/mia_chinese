package mia.chinese.model

import com.google.gson.annotations.SerializedName

/** Content-only catalog model. User playback state is stored locally, never here. */
data class Catalog(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("contentVersion") val contentVersion: String = "",
    @SerializedName("updatedAt") val updatedAt: String = "",
    @SerializedName("editions") val editions: List<Edition> = emptyList()
)

data class Edition(
    val id: String = "",
    val name: String = "",
    val grade: String = "",
    val semester: String = "",
    val instructions: List<String> = emptyList(),
    val courses: List<Course> = emptyList()
)

data class Course(
    val id: String = "",
    val title: String = "",
    val instructions: List<String> = emptyList(),
    val sections: List<Section> = emptyList()
)

data class Section(
    val id: String = "",
    val order: Int = 0,
    val type: String = "",
    val title: String = "",
    val description: String? = null,
    val video: VideoItem? = null,
    val attachment: Attachment? = null
)

data class Attachment(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val url: String? = null
)

data class VideoItem(
    val id: String = "",
    val revision: Int = 0,
    val sourceType: String = "",
    val videoId: String? = null,
    val url: String? = null,
    val durationMs: Long? = null
) {
    val isMp4: Boolean
        get() = sourceType.equals("mp4", ignoreCase = true)

    val isYouTube: Boolean
        get() = sourceType.equals("youtube", ignoreCase = true)
}

data class VideoLocation(
    val edition: Edition,
    val course: Course,
    val section: Section,
    val video: VideoItem
)

fun Catalog.allVideoLocations(): List<VideoLocation> = editions.flatMap { edition ->
    edition.courses.flatMap { course ->
        course.videoSections().mapNotNull { section ->
            section.video?.let { video -> VideoLocation(edition, course, section, video) }
        }
    }
}

fun Catalog.findVideo(videoId: String): VideoLocation? =
    allVideoLocations().firstOrNull { it.video.id == videoId }

fun Edition.findCourse(courseId: String): Course? = courses.firstOrNull { it.id == courseId }

fun Course.orderedSections(): List<Section> = sections.sortedBy { it.order }

fun Course.videoSections(): List<Section> =
    orderedSections().filter { it.type.equals("video", ignoreCase = true) && it.video != null }

fun Course.noteSections(): List<Section> =
    orderedSections().filter { it.type.equals("note", ignoreCase = true) }

fun Course.attachmentSections(): List<Section> =
    orderedSections().filter { it.type.equals("attachment", ignoreCase = true) }
