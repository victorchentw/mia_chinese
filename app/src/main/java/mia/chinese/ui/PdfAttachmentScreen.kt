package mia.chinese.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import mia.chinese.data.resolveAttachmentUrl
import mia.chinese.model.AttachmentLocation
import mia.chinese.model.isPdf

@Composable
internal fun PdfAttachmentScreen(
    location: AttachmentLocation,
    onBack: () -> Unit
) {
    val sourceUrl = location.attachment.url
        ?.trim()
        ?.takeIf { it.startsWith("https://") }
    var resolvedUrl by remember(location.attachment.id, sourceUrl) {
        mutableStateOf<String?>(null)
    }
    var isResolving by remember(location.attachment.id, sourceUrl) {
        mutableStateOf(sourceUrl != null)
    }
    var resolveError by remember(location.attachment.id, sourceUrl) {
        mutableStateOf<String?>(null)
    }
    var resolveAttempt by remember(location.attachment.id, sourceUrl) {
        mutableStateOf(0)
    }
    var showWebView by remember(location.attachment.id) { mutableStateOf(false) }

    LaunchedEffect(location.attachment.id, sourceUrl, resolveAttempt) {
        resolvedUrl = null
        resolveError = null
        if (sourceUrl == null) {
            isResolving = false
            return@LaunchedEffect
        }

        isResolving = true
        val result = resolveAttachmentUrl(location.attachment)
        resolvedUrl = result.url
        resolveError = result.errorMessage
        isResolving = false
    }

    val webViewUrl = resolvedUrl
    if (showWebView && webViewUrl != null) {
        BackHandler(enabled = true) { showWebView = false }
        PdfWebView(
            url = webViewUrl,
            onBackToQr = { showWebView = false }
        )
    } else {
        PdfQrCodeScreen(
            location = location,
            url = resolvedUrl,
            isResolving = isResolving,
            errorMessage = resolveError,
            onBack = onBack,
            onRetry = { resolveAttempt += 1 },
            onOpenInWebView = { showWebView = true }
        )
    }
}

@Composable
private fun PdfQrCodeScreen(
    location: AttachmentLocation,
    url: String?,
    isResolving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenInWebView: () -> Unit
) {
    val backRequester = remember { FocusRequester() }
    val qrBitmap = remember(url) { url?.let(::createQrBitmap) }

    LaunchedEffect(Unit) {
        runCatching { backRequester.requestFocus() }
    }

    ScreenFrame {
        ScreenHeader(
            title = if (location.attachment.isPdf()) "PDF 講義" else "附件",
            subtitle = "${location.edition.name}・${location.course.title}",
            onBack = onBack,
            backFocusRequester = backRequester
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvPanel(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 420.dp)
            ) {
                Text(location.attachment.title, style = MaterialTheme.typography.h5)
                when {
                    isResolving -> {
                        Text(
                            "正在取得可供手機掃描的 PDF 下載連結…",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.secondary,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    url == null -> {
                        Text(
                            errorMessage ?: "目前沒有有效的 HTTPS PDF 連結。",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        if (errorMessage != null) {
                            TvAction(
                                onClick = onRetry,
                                modifier = Modifier
                                    .padding(top = 14.dp)
                                    .width(300.dp)
                            ) {
                                Text("重新取得 PDF 連結")
                            }
                        }
                    }
                    else -> {
                        Text(
                            "電視內建 WebView 閱讀器（實驗功能）",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.secondary,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        TvAction(
                            onClick = onOpenInWebView,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .width(300.dp)
                        ) {
                            Text("在 TV 嘗試開啟 PDF")
                        }
                        Text(
                            "WebView 不保證能直接渲染 PDF；若畫面空白或開始下載，請改用右側 QR code。",
                            style = MaterialTheme.typography.body2,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(390.dp)
            ) {
                when {
                    isResolving -> {
                        Text(
                            "正在產生可下載 QR code…",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                    qrBitmap != null -> {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "用手機掃描開啟 PDF",
                            modifier = Modifier.size(350.dp)
                        )
                        Text(
                            "用手機相機掃描 QR code 開啟 PDF",
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    else -> {
                        Text(
                            "目前沒有可用的 PDF QR code。",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
                Spacer(Modifier.size(18.dp))
                Text(
                    "手機需能連線到 PDF 的公開 HTTPS 網址。",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                )
                Text(
                    "Notion 附件會先取得暫時下載連結，請在畫面顯示時掃描；過期後可重新取得。",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfWebView(
    url: String,
    onBackToQr: () -> Unit
) {
    val initialHost = remember(url) { Uri.parse(url).host?.lowercase() }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { backRequester.requestFocus() }
    }

    DisposableEffect(url) {
        onDispose {
            webViewState.value?.stopLoading()
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = !isAllowedPdfUrl(request.url, initialHost)

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                errorMessage = "PDF 無法載入（${error.description}）。"
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse
                        ) {
                            if (request.isForMainFrame) {
                                errorMessage = "PDF 伺服器回傳 HTTP ${errorResponse.statusCode}，請返回使用 QR code。"
                            }
                        }
                    }
                    setDownloadListener(DownloadListener { _, _, _, _, _ ->
                        errorMessage = "此 Android WebView 嘗試下載 PDF，沒有內建 PDF 閱讀器；請返回掃描 QR code。"
                    })
                    webViewState.value = this
                    loadUrl(url)
                }
            },
            update = { webViewState.value = it },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp)
        ) {
            TvAction(
                onClick = onBackToQr,
                focusRequester = backRequester,
                modifier = Modifier.width(230.dp)
            ) {
                Text("返回 PDF／QR")
            }
            Text(
                "PDF WebView 實驗",
                style = MaterialTheme.typography.body2,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        errorMessage?.let { message ->
            TvPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                Text(message, color = MaterialTheme.colors.error)
                Text(
                    "請返回後使用 QR code 在手機開啟。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun isAllowedPdfUrl(uri: Uri, initialHost: String?): Boolean {
    if (uri.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return initialHost != null && (host == initialHost || host.endsWith(".$initialHost"))
}

private fun createQrBitmap(value: String, size: Int = 350): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
    )
    val pixels = IntArray(size * size) { index ->
        val x = index % size
        val y = index / size
        if (matrix.get(x, y)) Color.BLACK else Color.WHITE
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()
