package mia.chinese.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "video_progress",
    primaryKeys = ["videoId", "revision"]
)
data class VideoProgressEntity(
    val videoId: String,
    val revision: Int,
    val editionId: String,
    val courseId: String,
    val sectionId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val status: String,
    val lastStartedAtMs: Long?,
    val lastCheckpointAtMs: Long,
    val completedAtMs: Long?
)

@Entity(tableName = "last_resume_pointer")
data class LastResumePointerEntity(
    @PrimaryKey val id: Int = 1,
    val videoId: String,
    val editionId: String,
    val courseId: String,
    val sectionId: String,
    val revision: Int,
    val lastFocusedItemId: String?,
    val updatedAtMs: Long
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM video_progress ORDER BY lastCheckpointAtMs DESC")
    fun observeAllProgress(): Flow<List<VideoProgressEntity>>

    @Query("SELECT * FROM last_resume_pointer WHERE id = 1 LIMIT 1")
    fun observeLastResumePointer(): Flow<LastResumePointerEntity?>

    @Query("SELECT * FROM video_progress WHERE videoId = :videoId AND revision = :revision LIMIT 1")
    suspend fun getProgress(videoId: String, revision: Int): VideoProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: VideoProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPointer(pointer: LastResumePointerEntity)
}

@Database(
    entities = [VideoProgressEntity::class, LastResumePointerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChineseDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
