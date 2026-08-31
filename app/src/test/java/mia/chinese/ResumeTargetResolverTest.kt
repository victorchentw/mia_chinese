package mia.chinese

import mia.chinese.data.CatalogSyncPolicy
import mia.chinese.data.LastResumePointerEntity
import mia.chinese.data.ResumeTargetResolver
import mia.chinese.model.Catalog
import mia.chinese.model.Course
import mia.chinese.model.Edition
import mia.chinese.model.Section
import mia.chinese.model.VideoItem
import mia.chinese.model.VideoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTargetResolverTest {
    @Test
    fun matchingPointerResolvesStablePath() {
        val catalog = sampleCatalog()
        val pointer = pointer(revision = 1)

        val result = ResumeTargetResolver.resolve(catalog, pointer)

        assertEquals("video-1", result.location?.video?.id)
        assertNull(result.staleReason)
        assertNull(result.fallback)
    }

    @Test
    fun changedRevisionReturnsSameCourseFallback() {
        val catalog = sampleCatalog()
        val pointer = pointer(revision = 2)

        val result = ResumeTargetResolver.resolve(catalog, pointer)

        assertNull(result.location)
        assertNotNull(result.fallback)
        assertEquals("video-2", result.fallback?.video?.id)
        assertTrue(result.staleReason.orEmpty().contains("更新"))
    }

    @Test
    fun malformedProgressDoesNotBecomeAResumeCard() {
        val catalog = sampleCatalog()
        val pointer = pointer(revision = 1)
        val badProgress = mia.chinese.data.VideoProgressEntity(
            videoId = "video-1",
            revision = 1,
            editionId = "edition-1",
            courseId = "course-1",
            sectionId = "section-1",
            positionMs = -1L,
            durationMs = 1000L,
            status = "IN_PROGRESS",
            lastStartedAtMs = 1L,
            lastCheckpointAtMs = 2L,
            completedAtMs = null
        )

        val result = ResumeTargetResolver.resolve(catalog, pointer, listOf(badProgress))

        assertNull(result.location)
        assertNotNull(result.fallback)
    }

    @Test
    fun syncPolicyRequiresSha256AndComparesVersionsNumerically() {
        assertTrue(CatalogSyncPolicy.isSha256("a".repeat(64)))
        assertTrue(!CatalogSyncPolicy.isSha256("not-a-checksum"))
        assertTrue(CatalogSyncPolicy.isVersionAtLeast("0.1.10", "0.1.4"))
        assertTrue(!CatalogSyncPolicy.isVersionAtLeast("0.1.3", "0.1.4"))
    }

    private fun pointer(revision: Int): LastResumePointerEntity = LastResumePointerEntity(
        videoId = "video-1",
        editionId = "edition-1",
        courseId = "course-1",
        sectionId = "section-1",
        revision = revision,
        lastFocusedItemId = "video-1",
        updatedAtMs = 10L
    )

    private fun sampleCatalog(): Catalog {
        val course = Course(
            id = "course-1",
            sections = listOf(
                Section(
                    id = "section-1",
                    order = 1,
                    type = "video",
                    video = VideoItem(
                        id = "video-1",
                        revision = 1,
                        sourceType = "mp4",
                        url = "https://example.com/one.mp4"
                    )
                ),
                Section(
                    id = "section-2",
                    order = 2,
                    type = "video",
                    video = VideoItem(
                        id = "video-2",
                        revision = 1,
                        sourceType = "mp4",
                        url = "https://example.com/two.mp4"
                    )
                )
            )
        )
        return Catalog(
            schemaVersion = 2,
            contentVersion = "test",
            editions = listOf(Edition(id = "edition-1", courses = listOf(course)))
        )
    }
}
