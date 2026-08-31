package mia.chinese

import android.app.Application
import androidx.room.Room
import mia.chinese.data.CatalogRepository
import mia.chinese.data.ChineseDatabase
import mia.chinese.data.CHINESE_DB_MIGRATION_1_2
import mia.chinese.data.ProgressRepository
import mia.chinese.playback.PlaybackSettings
class ChineseLearningApp : Application() {
    lateinit var catalogRepository: CatalogRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set
    lateinit var playbackSettings: PlaybackSettings
        private set
    override fun onCreate() {
        super.onCreate()
        catalogRepository = CatalogRepository(this)
        val database = Room.databaseBuilder(
            this,
            ChineseDatabase::class.java,
            "mia_chinese.db"
        )
            .addMigrations(CHINESE_DB_MIGRATION_1_2)
            .build()
        progressRepository = ProgressRepository(database)
        playbackSettings = PlaybackSettings(this)
    }
}
