package mia.chinese.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mia.chinese.model.VideoLocation

enum class ProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    STALE
}

class ProgressRepository(private val database: ChineseDatabase) {
    private val dao = database.progressDao()
    private val writeMutex = Mutex()

    val allProgress: Flow<List<VideoProgressEntity>> = dao.observeAllProgress()
    val lastResumePointer: Flow<LastResumePointerEntity?> = dao.observeLastResumePointer()
    val lastCatalogLocation: Flow<LastCatalogLocationEntity?> = dao.observeLastCatalogLocation()

    suspend fun saveCatalogLocation(
        editionId: String? = null,
        courseId: String? = null,
        sectionId: String? = null,
        focusedItemId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) = writeMutex.withLock {
        val existing = dao.getLastCatalogLocation()
        if (existing == null || nowMs >= existing.updatedAtMs) {
            dao.upsertCatalogLocation(
                LastCatalogLocationEntity(
                    editionId = editionId,
                    courseId = courseId,
                    sectionId = sectionId,
                    focusedItemId = focusedItemId,
                    updatedAtMs = nowMs
                )
            )
        }
    }

    suspend fun getProgress(videoId: String, revision: Int): VideoProgressEntity? =
        dao.getProgress(videoId, revision)

    suspend fun markStale(pointer: LastResumePointerEntity) = writeMutex.withLock {
        dao.markStale(pointer.videoId, pointer.revision)
    }

    suspend fun saveCheckpoint(
        location: VideoLocation,
        positionMs: Long,
        durationMs: Long?,
        status: ProgressStatus,
        lastFocusedItemId: String? = location.video.id,
        nowMs: Long = System.currentTimeMillis()
    ) = writeMutex.withLock {
        val safeDuration = durationMs?.takeIf { it > 0L }
        val safePosition = positionMs.coerceIn(0L, safeDuration ?: Long.MAX_VALUE)
        database.withTransaction {
            val existing = dao.getProgress(location.video.id, location.video.revision)
            // A delayed coroutine must never overwrite a newer checkpoint.
            if (existing != null && nowMs < existing.lastCheckpointAtMs) return@withTransaction
            val existingPointer = dao.getLastResumePointer()
            val shouldUpdatePointer = existingPointer == null || nowMs >= existingPointer.updatedAtMs
            val pointer = LastResumePointerEntity(
                videoId = location.video.id,
                editionId = location.edition.id,
                courseId = location.course.id,
                sectionId = location.section.id,
                revision = location.video.revision,
                lastFocusedItemId = lastFocusedItemId,
                updatedAtMs = nowMs
            )
            // Opening a video or choosing "restart" must not erase an older
            // checkpoint until the child actually presses Play.
            if (status == ProgressStatus.NOT_STARTED && existing != null) {
                if (shouldUpdatePointer) dao.upsertPointer(pointer)
                return@withTransaction
            }
            val startedAt = existing?.lastStartedAtMs
                ?: if (status != ProgressStatus.NOT_STARTED) nowMs else null
            val completedAt = if (status == ProgressStatus.COMPLETED) {
                existing?.completedAtMs ?: nowMs
            } else {
                null
            }
            val progress = VideoProgressEntity(
                videoId = location.video.id,
                revision = location.video.revision,
                editionId = location.edition.id,
                courseId = location.course.id,
                sectionId = location.section.id,
                positionMs = safePosition,
                durationMs = safeDuration,
                status = status.name,
                lastStartedAtMs = startedAt,
                lastCheckpointAtMs = nowMs,
                completedAtMs = completedAt
            )
            dao.upsertProgress(progress)
            if (shouldUpdatePointer) dao.upsertPointer(pointer)
        }
    }

    suspend fun markCompleted(
        location: VideoLocation,
        durationMs: Long?,
        lastFocusedItemId: String? = location.video.id,
        nowMs: Long = System.currentTimeMillis()
    ) {
        saveCheckpoint(
            location = location,
            positionMs = durationMs ?: 0L,
            durationMs = durationMs,
            status = ProgressStatus.COMPLETED,
            lastFocusedItemId = lastFocusedItemId,
            nowMs = nowMs
        )
    }
}
