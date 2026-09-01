package mia.chinese.data

import mia.chinese.model.Catalog
import mia.chinese.model.VideoItem
import mia.chinese.model.allVideoLocations
import java.time.Instant

object CatalogValidator {
    private val youtubeIdPattern = Regex("^[A-Za-z0-9_-]{6,}$")
    fun errors(catalog: Catalog): List<String> {
        val errors = mutableListOf<String>()
        if (catalog.schemaVersion != 2) {
            errors += "unsupported schemaVersion=${catalog.schemaVersion}"
        }
        if (catalog.contentVersion.isBlank()) errors += "contentVersion is blank"
        if (catalog.updatedAt.isBlank()) {
            errors += "updatedAt is blank"
        } else if (!catalog.updatedAt.endsWith("Z") ||
            runCatching { Instant.parse(catalog.updatedAt) }.isFailure
        ) {
            errors += "updatedAt must be ISO-8601 UTC"
        }
        if (catalog.editions.isEmpty()) errors += "editions is empty"

        val ids = mutableMapOf<String, String>()
        fun checkId(id: String, kind: String) {
            if (id.isBlank()) {
                errors += "$kind id is blank"
            } else {
                val previous = ids.put(id, kind)
                if (previous != null) errors += "duplicate id=$id ($previous/$kind)"
            }
        }

        catalog.editions.forEach { edition ->
            checkId(edition.id, "edition")
            if (edition.name.isBlank()) errors += "edition ${edition.id} name is blank"
            edition.courses.forEach { course ->
                checkId(course.id, "course")
                if (course.title.isBlank()) errors += "course ${course.id} title is blank"
                val orders = course.sections.groupingBy { it.order }.eachCount()
                orders.filterValues { it > 1 }.keys.forEach { order ->
                    errors += "course ${course.id} has duplicate section order=$order"
                }
                course.sections.forEach { section ->
                    checkId(section.id, "section")
                    if (section.order < 1) errors += "section ${section.id} order must be >= 1"
                    if (section.title.isBlank()) errors += "section ${section.id} title is blank"
                    when {
                        section.type.equals("video", ignoreCase = true) -> {
                            val video = section.video
                            if (video == null) {
                                errors += "video section ${section.id} has no video"
                            } else {
                                validateVideo(video, errors)
                                checkId(video.id, "video")
                            }
                            if (section.attachment != null) {
                                errors += "video section ${section.id} must not have attachment"
                            }
                        }
                        section.type.equals("attachment", ignoreCase = true) -> {
                            if (section.attachment == null) {
                                errors += "attachment section ${section.id} has no attachment"
                            } else {
                                checkId(section.attachment.id, "attachment")
                                if (section.attachment.title.isBlank()) {
                                    errors += "attachment ${section.attachment.id} title is blank"
                                }
                                section.attachment.url?.let { url ->
                                    if (!url.startsWith("https://")) {
                                        errors += "attachment ${section.attachment.id} must use HTTPS URL"
                                    } else if (isNotionFileSource(url) &&
                                        section.attachment.notionBlockId.isNullOrBlank()
                                    ) {
                                        errors += "attachment ${section.attachment.id} is a Notion file but has no notionBlockId"
                                    }
                                }
                            }
                            if (section.video != null) {
                                errors += "attachment section ${section.id} must not have video"
                            }
                        }
                        section.type.equals("heading", ignoreCase = true) ||
                            section.type.equals("note", ignoreCase = true) -> {
                            if (section.video != null) {
                                errors += "${section.type} section ${section.id} must not have video"
                            }
                            if (section.attachment != null) {
                                errors += "${section.type} section ${section.id} must not have attachment"
                            }
                        }
                        else -> errors += "section ${section.id} has unsupported type=${section.type}"
                    }
                }
            }
        }

        val locations = catalog.allVideoLocations()
        if (locations.any { it.video.revision < 1 }) {
            errors += "video revision must be >= 1"
        }
        return errors.distinct()
    }

    fun requireValid(catalog: Catalog): Catalog {
        val errors = errors(catalog)
        require(errors.isEmpty()) { "Invalid catalog: ${errors.joinToString("; ")}" }
        return catalog
    }

    private fun isNotionFileSource(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val current = host.startsWith("prod-files-secure.s3.") && host.endsWith(".amazonaws.com")
        val legacy = host.endsWith(".amazonaws.com") &&
            uri.path?.contains("/secure.notion-static.com/") == true
        return current || legacy
    }

    private fun validateVideo(video: VideoItem, errors: MutableList<String>) {
        if (video.id.isBlank()) return
        if (video.revision < 1) errors += "video ${video.id} revision must be >= 1"
        if (video.durationMs != null && video.durationMs <= 0L) {
            errors += "video ${video.id} durationMs must be positive when present"
        }
        when {
            video.isMp4 -> {
                val url = video.url.orEmpty()
                if (!url.startsWith("https://")) errors += "MP4 ${video.id} must use HTTPS URL"
            }
            video.isYouTube -> {
                if (video.videoId.isNullOrBlank()) {
                    errors += "YouTube ${video.id} has no videoId"
                } else if (!youtubeIdPattern.matches(video.videoId)) {
                    errors += "YouTube ${video.id} has invalid videoId"
                }
                if (video.url != null && !video.url.startsWith("https://")) {
                    errors += "YouTube ${video.id} URL must use HTTPS"
                }
            }
            else -> errors += "video ${video.id} has unsupported sourceType=${video.sourceType}"
        }
    }
}
