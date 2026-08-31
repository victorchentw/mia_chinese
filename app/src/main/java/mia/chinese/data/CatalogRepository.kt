package mia.chinese.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mia.chinese.BuildConfig
import mia.chinese.model.Catalog
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Loads the bundled catalog immediately and optionally maintains a validated,
 * last-known-good file cache for a future static HTTPS catalog endpoint.
 */
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

    /**
     * Returns the cache only when it is still a valid catalog; otherwise the
     * APK baseline is used. A bad cache is never allowed to blank the app.
     */
    suspend fun loadCatalog(): Catalog = withContext(Dispatchers.IO) {
        val cached = runCatching {
            readCachedCatalog()?.takeIf { CatalogValidator.errors(it).isEmpty() }
        }.getOrNull()
        if (cached != null) return@withContext cached

        val bundled = loadBundledCatalog()
        runCatching {
            writeCatalogAtomically(
                bytes = gson.toJson(bundled).toByteArray(StandardCharsets.UTF_8),
                checksum = sha256(gson.toJson(bundled).toByteArray(StandardCharsets.UTF_8)),
                etag = null
            )
        }
        bundled
    }

    fun cachedMetadata(): CatalogMetadata? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val contentVersion = preferences.getString(KEY_CONTENT_VERSION, null) ?: return null
        return CatalogMetadata(
            contentVersion = contentVersion,
            updatedAt = preferences.getString(KEY_UPDATED_AT, "").orEmpty(),
            downloadedAtMs = preferences.getLong(KEY_DOWNLOADED_AT, 0L),
            checksum = preferences.getString(KEY_CHECKSUM, "").orEmpty(),
            etag = preferences.getString(KEY_ETAG, null)
        )
    }

    /**
     * Downloads a complete catalog into a sibling temporary file, validates it,
     * and atomically replaces the last-known-good file. The checksum is
     * intentionally supplied by a trusted release manifest; an unverified
     * response is rejected instead of replacing local content.
     */
    suspend fun sync(
        endpoint: String,
        expectedSha256: String?,
        minimumAppVersion: String? = null,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 20_000
    ): CatalogSyncResult = withContext(Dispatchers.IO) {
        val uri = runCatching { URL(endpoint) }.getOrNull()
            ?: return@withContext CatalogSyncResult.Failed("同步網址格式錯誤")
        if (uri.protocol != "https") {
            return@withContext CatalogSyncResult.Failed("同步網址必須使用 HTTPS")
        }
        val expected = expectedSha256?.trim()?.lowercase()
        if (!CatalogSyncPolicy.isSha256(expected)) {
            return@withContext CatalogSyncResult.Rejected("缺少有效的 catalog SHA-256 checksum")
        }
        if (minimumAppVersion != null &&
            !CatalogSyncPolicy.isVersionAtLeast(BuildConfig.VERSION_NAME, minimumAppVersion)
        ) {
            return@withContext CatalogSyncResult.Rejected("需要更新 App 才能使用這份課程資料")
        }

        val connection = (uri.openConnection() as? HttpURLConnection)
            ?: return@withContext CatalogSyncResult.Failed("無法建立同步連線")
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            cachedMetadata()?.etag?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("If-None-Match", it)
            }
            connection.connect()
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    val cached = readCachedCatalog()
                    if (cached != null && CatalogValidator.errors(cached).isEmpty()) {
                        CatalogSyncResult.NotModified(cached)
                    } else {
                        CatalogSyncResult.Failed("伺服器回報未變更，但本機沒有有效快取")
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    val contentType = connection.contentType.orEmpty()
                    if (contentType.isNotBlank() &&
                        !contentType.lowercase().contains("json") &&
                        !contentType.lowercase().contains("text")
                    ) {
                        return@withContext CatalogSyncResult.Rejected("同步回應不是 JSON 課程資料")
                    }
                    val bytes = connection.inputStream.use { readUpToLimit(it, MAX_CATALOG_BYTES) }
                    val actual = sha256(bytes)
                    if (actual != expected) {
                        return@withContext CatalogSyncResult.Rejected("catalog checksum 不符，保留目前資料")
                    }
                    val catalog = runCatching {
                        gson.fromJson(bytes.toString(StandardCharsets.UTF_8), Catalog::class.java)
                            ?: error("catalog is empty")
                    }.getOrElse {
                        return@withContext CatalogSyncResult.Rejected("課程資料格式無法讀取，保留目前資料")
                    }
                    val validationErrors = CatalogValidator.errors(catalog)
                    if (validationErrors.isNotEmpty()) {
                        return@withContext CatalogSyncResult.Rejected(
                            "課程資料驗證失敗：${validationErrors.take(3).joinToString("；")}"
                        )
                    }
                    writeCatalogAtomically(
                        bytes = bytes,
                        checksum = actual,
                        etag = connection.getHeaderField("ETag")
                    )
                    CatalogSyncResult.Updated(catalog)
                }
                else -> CatalogSyncResult.Failed("同步失敗（HTTP ${connection.responseCode}）")
            }
        } catch (error: Exception) {
            CatalogSyncResult.Failed(error.message ?: "目前無法同步課程資料")
        } finally {
            connection.disconnect()
        }
    }

    private fun readCachedCatalog(): Catalog? {
        val file = catalogFile()
        if (!file.isFile || file.length() > MAX_CATALOG_BYTES) return null
        return runCatching {
            gson.fromJson(file.readText(StandardCharsets.UTF_8), Catalog::class.java)
        }.getOrNull()
    }

    private fun writeCatalogAtomically(bytes: ByteArray, checksum: String, etag: String?) {
        val directory = catalogFile().parentFile ?: error("catalog cache directory unavailable")
        if (!directory.exists() && !directory.mkdirs()) error("cannot create catalog cache directory")
        val temporary = File(directory, "$CATALOG_FILE.tmp")
        temporary.outputStream().use { it.write(bytes) }
        try {
            Files.move(
                temporary.toPath(),
                catalogFile().toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                catalogFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONTENT_VERSION, readCachedCatalog()?.contentVersion.orEmpty())
            .putString(KEY_UPDATED_AT, readCachedCatalog()?.updatedAt.orEmpty())
            .putLong(KEY_DOWNLOADED_AT, System.currentTimeMillis())
            .putString(KEY_CHECKSUM, checksum)
            .apply {
                if (etag.isNullOrBlank()) remove(KEY_ETAG) else putString(KEY_ETAG, etag)
            }
            .apply()
    }

    private fun catalogFile(): File = File(context.filesDir, "$CACHE_DIRECTORY/$CATALOG_FILE")

    companion object {
        const val CATALOG_ASSET = "catalog/lessons.json"
        private const val CACHE_DIRECTORY = "catalog"
        private const val CATALOG_FILE = "lessons.json"
        private const val PREFERENCES = "catalog_metadata"
        private const val KEY_CONTENT_VERSION = "contentVersion"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_DOWNLOADED_AT = "downloadedAtMs"
        private const val KEY_CHECKSUM = "checksum"
        private const val KEY_ETAG = "etag"
        private const val MAX_CATALOG_BYTES = 10L * 1024L * 1024L

        private fun readUpToLimit(input: java.io.InputStream, limit: Long): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) error("catalog is too large")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

data class CatalogMetadata(
    val contentVersion: String,
    val updatedAt: String,
    val downloadedAtMs: Long,
    val checksum: String,
    val etag: String?
)

sealed interface CatalogSyncResult {
    data class Updated(val catalog: Catalog) : CatalogSyncResult
    data class NotModified(val catalog: Catalog) : CatalogSyncResult
    data class Rejected(val reason: String) : CatalogSyncResult
    data class Failed(val reason: String) : CatalogSyncResult
}

object CatalogSyncPolicy {
    fun isSha256(value: String?): Boolean =
        value != null && value.length == 64 && value.all { it in "0123456789abcdef" }

    fun isVersionAtLeast(current: String, minimum: String): Boolean =
        compareVersions(current, minimum) >= 0

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val comparison = (leftParts.getOrElse(index) { 0 })
                .compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
