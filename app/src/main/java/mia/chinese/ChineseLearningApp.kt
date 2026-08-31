package mia.chinese

import android.app.Application
import androidx.room.Room
import mia.chinese.data.CatalogRepository
import mia.chinese.data.ChineseDatabase
import mia.chinese.data.ProgressRepository

class ChineseLearningApp : Application() {
    lateinit var catalogRepository: CatalogRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set

    override fun onCreate() {
        super.onCreate()
        catalogRepository = CatalogRepository(this)
        val database = Room.databaseBuilder(
            this,
            ChineseDatabase::class.java,
            "mia_chinese.db"
        ).build()
        progressRepository = ProgressRepository(database)
    }
}
