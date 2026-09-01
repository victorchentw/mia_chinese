package mia.chinese.data

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mia.chinese.model.Attachment
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/** Result of resolving an attachment URL for use by the TV UI and QR code. */
internal data class AttachmentUrlResult(
    val url: String?,
    val errorMessage: String? = null
)

/**
 * Notion's public catalog exposes private S3 source references. Resolve those
 * references through Notion only when needed, so a QR code contains a usable
 * short-lived download URL instead of an S3 URL that returns HTTP 403.
 */
internal suspend fun resolveAttachmentUrl(attachment: Attachment): AttachmentUrlResult {
    val source = attachment.url?.trim().orEmpty()
    if (source.isBlank()) {
        return AttachmentUrlResult(
            url = null,
            errorMessage = "目前沒有附件下載連結。"
        )
    }

    val sourceUri = Uri.parse(source)
    if (sourceUri.scheme != "https" || sourceUri.host.isNullOrBlank()) {
        return AttachmentUrlResult(
            url = null,
            errorMessage = "附件連結必須使用有效的 HTTPS 網址。"
        )
    }

    // External stable HTTPS files can be placed directly in the QR code.
    if (!isNotionHostedFile(sourceUri)) {
        return AttachmentUrlResult(url = source)
    }

    // A previously resolved file.notion.com URL is already signed.
    if (isSignedNotionUrl(sourceUri)) {
        return AttachmentUrlResult(url = source)
    }

    val blockId = attachment.notionBlockId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: inferBlockId(attachment.id)
    val spaceId = attachment.notionSpaceId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: inferSpaceId(sourceUri)

    if (blockId == null) {
        return AttachmentUrlResult(
            url = null,
            errorMessage = "Notion 附件缺少權限資訊，請更新課程資料後重試。"
        )
    }

    return withContext(Dispatchers.IO) {
        try {
            requestSignedUrl(source, blockId, spaceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AttachmentUrlResult(
                url = null,
                errorMessage = "無法取得 PDF 暫時下載連結，請確認網路後重試。"
            )
        }
    }
}

private const val SIGNED_URL_ENDPOINT =
    "https://www.notion.so/api/v3/getSignedFileUrls"
private const val FILE_NOTION_COM_HOST = "file.notion.com"
private const val FILE_NOTION_SO_HOST = "file.notion.so"
private val gson = Gson()

private data class PermissionRecord(
    val id: String,
    val table: String = "block",
    @SerializedName("spaceId") val spaceId: String? = null
)

private data class SignedUrlRequest(
    val url: String,
    val permissionRecord: PermissionRecord
)

private data class SignedUrlPayload(
    val urls: List<SignedUrlRequest>
)

private data class SignedUrlResponse(
    @SerializedName("signedUrls") val signedUrls: List<String>? = null
)

private fun requestSignedUrl(
    source: String,
    blockId: String,
    spaceId: String?
): AttachmentUrlResult {
    val connection = (URL(SIGNED_URL_ENDPOINT).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        doOutput = true
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("User-Agent", "mia-chinese-android-tv/1.0")
        setRequestProperty("Referer", "https://www.notion.so/")
    }

    return try {
        val payload = SignedUrlPayload(
            urls = listOf(
                SignedUrlRequest(
                    url = source,
                    permissionRecord = PermissionRecord(
                        id = blockId,
                        spaceId = spaceId
                    )
                )
            )
        )
        connection.outputStream.use { output ->
            output.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        }

        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            return AttachmentUrlResult(
                url = null,
                errorMessage = "Notion 附件連結取得失敗（HTTP $statusCode），請重試。"
            )
        }

        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, SignedUrlResponse::class.java)
        }
        val signedUrl = response.signedUrls.orEmpty().firstOrNull { candidate ->
            isSignedNotionUrl(Uri.parse(candidate))
        }
        if (signedUrl == null) {
            AttachmentUrlResult(
                url = null,
                errorMessage = "Notion 沒有回傳可下載的附件連結，請重試。"
            )
        } else {
            AttachmentUrlResult(url = signedUrl)
        }
    } finally {
        connection.disconnect()
    }
}

private fun isSignedNotionUrl(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" &&
        (host == FILE_NOTION_COM_HOST || host == FILE_NOTION_SO_HOST)
}

private fun isNotionHostedFile(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    if (isSignedNotionUrl(uri)) return false

    val isCurrentNotionFile =
        host.startsWith("prod-files-secure.s3.") && host.endsWith(".amazonaws.com")
    val isLegacyNotionFile =
        host.endsWith(".amazonaws.com") &&
            uri.path?.contains("/secure.notion-static.com/") == true
    return isCurrentNotionFile || isLegacyNotionFile
}

private fun inferBlockId(attachmentId: String): String? {
    val compact = attachmentId.substringAfterLast("-attachment-", "")
    if (!compact.matches(Regex("[0-9a-fA-F]{32}"))) return null
    return compact.substring(0, 8) + "-" +
        compact.substring(8, 12) + "-" +
        compact.substring(12, 16) + "-" +
        compact.substring(16, 20) + "-" +
        compact.substring(20)
}

private fun inferSpaceId(uri: Uri): String? {
    val host = uri.host?.lowercase() ?: return null
    if (!host.startsWith("prod-files-secure.s3.") || !host.endsWith(".amazonaws.com")) {
        return null
    }
    val firstPathSegment = uri.path
        ?.trim('/')
        ?.substringBefore('/')
        ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
    return firstPathSegment
}
