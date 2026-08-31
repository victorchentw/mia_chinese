package mia.chinese

import mia.chinese.data.CatalogValidator
import mia.chinese.model.Attachment
import mia.chinese.model.Catalog
import mia.chinese.model.Edition
import mia.chinese.model.Course
import mia.chinese.model.Section
import mia.chinese.model.VideoItem
import mia.chinese.model.attachmentSections
import mia.chinese.model.noteSections
import mia.chinese.model.videoSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {
    @Test
    fun bundledShapeIsValid() {
        val catalog = Catalog(
            schemaVersion = 2,
            contentVersion = "test",
            editions = listOf(
                Edition(
                    id = "edition",
                    courses = listOf(
                        Course(
                            id = "course",
                            sections = listOf(
                                Section(
                                    id = "section",
                                    type = "video",
                                    video = VideoItem(
                                        id = "video",
                                        revision = 1,
                                        sourceType = "mp4",
                                        url = "https://example.com/video.mp4"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        assertTrue(CatalogValidator.errors(catalog).isEmpty())
    }

    @Test
    fun headingsNotesAndAttachmentsAreValidAndCounted() {
        val course = Course(
            id = "course",
            sections = listOf(
                Section(id = "heading", type = "heading", title = "第一部分"),
                Section(
                    id = "video-section",
                    type = "video",
                    video = VideoItem(
                        id = "video",
                        revision = 1,
                        sourceType = "mp4",
                        url = "https://example.com/video.mp4"
                    )
                ),
                Section(id = "note", type = "note", description = "更正說明"),
                Section(
                    id = "attachment-section",
                    type = "attachment",
                    attachment = Attachment(
                        id = "attachment",
                        kind = "pdf",
                        title = "課後講義.pdf"
                    )
                )
            )
        )
        val catalog = Catalog(
            schemaVersion = 2,
            contentVersion = "test",
            editions = listOf(Edition(id = "edition", courses = listOf(course)))
        )

        assertTrue(CatalogValidator.errors(catalog).isEmpty())
        assertEquals(1, course.videoSections().size)
        assertEquals(1, course.noteSections().size)
        assertEquals(1, course.attachmentSections().size)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val catalog = Catalog(
            schemaVersion = 2,
            contentVersion = "test",
            editions = listOf(
                Edition(id = "same", courses = listOf(Course(id = "same")))
            )
        )

        assertTrue(CatalogValidator.errors(catalog).any { it.contains("duplicate id=same") })
    }
}
