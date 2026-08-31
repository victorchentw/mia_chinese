package mia.chinese.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mia.chinese.model.Catalog
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class CatalogRepository(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    suspend fun loadBundledCatalog(): Catalog = withContext(Dispatchers.IO) {
        context.assets.open(CATALOG_ASSET).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, Catalog::class.java)
                    ?: error("catalog is empty")
            }
        }
    }

    companion object {
        const val CATALOG_ASSET = "catalog/lessons.json"
    }
}
