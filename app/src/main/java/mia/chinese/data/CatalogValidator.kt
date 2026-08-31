package mia.chinese.data

import mia.chinese.model.Catalog
import mia.chinese.model.VideoItem
import mia.chinese.model.allVideoLocations

object CatalogValidator {
    fun errors(catalog: Catalog): List<String> {
        val errors = mutableListOf<String>()
        if (catalog.schemaVersion != 2) {
            errors += "unsupported schemaVersion=${catalog.schemaVersion}"
        }
        if (catalog.contentVersion.isBlank()) errors += "contentVersion is blank"
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
            edition.courses.forEach { course ->
                checkId(course.id, "course")
                course.sections.forEach { section ->
                    checkId(section.id, "section")
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

    private fun validateVideo(video: VideoItem, errors: MutableList<String>) {
        if (video.id.isBlank()) return
        if (video.revision < 1) errors += "video ${video.id} revision must be >= 1"
        when {
            video.isMp4 -> {
                val url = video.url.orEmpty()
                if (!url.startsWith("https://")) errors += "MP4 ${video.id} must use HTTPS URL"
            }
            video.isYouTube -> {
                if (video.videoId.isNullOrBlank()) errors += "YouTube ${video.id} has no videoId"
            }
            else -> errors += "video ${video.id} has unsupported sourceType=${video.sourceType}"
        }
    }
}
