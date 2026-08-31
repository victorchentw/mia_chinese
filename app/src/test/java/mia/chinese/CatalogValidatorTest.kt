package mia.chinese

import mia.chinese.data.CatalogValidator
import mia.chinese.model.Catalog
import mia.chinese.model.Edition
import mia.chinese.model.Course
import mia.chinese.model.Section
import mia.chinese.model.VideoItem
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
