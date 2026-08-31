package mia.chinese

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mia.chinese.data.ChineseDatabase
import mia.chinese.data.ProgressRepository
import mia.chinese.data.ProgressStatus
import mia.chinese.model.Course
import mia.chinese.model.Edition
import mia.chinese.model.Section
import mia.chinese.model.VideoItem
import mia.chinese.model.VideoLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryInstrumentedTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ChineseDatabase::class.java
    ).allowMainThreadQueries().build()
    private val repository = ProgressRepository(database)
    private val location = VideoLocation(
        edition = Edition(id = "edition", name = "版"),
        course = Course(id = "course", title = "課"),
        section = Section(id = "section", order = 1, type = "video", title = "影片"),
        video = VideoItem(
            id = "video",
            revision = 1,
            sourceType = "mp4",
            url = "https://example.com/video.mp4"
        )
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun restartDoesNotEraseCheckpointBeforePlay() = runBlocking {
        repository.saveCheckpoint(
            location = location,
            positionMs = 12_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 100L
        )
        repository.saveCheckpoint(
            location = location,
            positionMs = 0L,
            durationMs = 60_000L,
            status = ProgressStatus.NOT_STARTED,
            nowMs = 200L
        )

        val progress = repository.getProgress("video", 1)
        assertEquals(12_000L, progress?.positionMs)
        assertEquals(ProgressStatus.IN_PROGRESS.name, progress?.status)
    }

    @Test
    fun olderCheckpointCannotOverwriteNewerPosition() = runBlocking {
        repository.saveCheckpoint(
            location = location,
            positionMs = 20_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 200L
        )
        repository.saveCheckpoint(
            location = location,
            positionMs = 5_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 100L
        )

        assertEquals(20_000L, repository.getProgress("video", 1)?.positionMs)
    }

    @Test
    fun olderCheckpointCannotMoveGlobalResumePointerBack() = runBlocking {
        val otherLocation = location.copy(video = location.video.copy(id = "other-video"))
        repository.saveCheckpoint(
            location = location,
            positionMs = 20_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 200L
        )
        repository.saveCheckpoint(
            location = otherLocation,
            positionMs = 5_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 300L
        )
        repository.saveCheckpoint(
            location = location,
            positionMs = 25_000L,
            durationMs = 60_000L,
            status = ProgressStatus.IN_PROGRESS,
            nowMs = 250L
        )

        assertEquals("other-video", repository.lastResumePointer.first()?.videoId)
        assertEquals(25_000L, repository.getProgress("video", 1)?.positionMs)
    }
}
