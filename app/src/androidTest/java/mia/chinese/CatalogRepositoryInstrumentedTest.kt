package mia.chinese

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import mia.chinese.data.CatalogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogRepositoryInstrumentedTest {
    @Test
    fun corruptCacheFallsBackToBundledCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cache = File(context.filesDir, "catalog/lessons.json")
        cache.parentFile?.mkdirs()
        cache.writeText("{not valid json")

        val catalog = CatalogRepository(context).loadCatalog()

        assertEquals(2, catalog.schemaVersion)
        assertEquals(3, catalog.editions.size)
        assertTrue(catalog.editions.sumOf { it.courses.size } >= 50)
    }
}
