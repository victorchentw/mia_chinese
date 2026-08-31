package mia.chinese.ui

import android.net.Uri
import android.webkit.WebView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavHostController
import mia.chinese.BuildConfig
import mia.chinese.ChineseLearningApp
import mia.chinese.CatalogLoadState
import mia.chinese.CatalogSyncState
import mia.chinese.MainViewModel
import kotlinx.coroutines.launch
import mia.chinese.data.CatalogMetadata
import mia.chinese.data.CatalogRepository
import mia.chinese.data.LastCatalogLocationEntity
import mia.chinese.data.LastResumePointerEntity
import mia.chinese.data.ProgressStatus
import mia.chinese.data.ProgressRepository
import mia.chinese.data.ResumeTargetResolver
import mia.chinese.data.VideoProgressEntity
import mia.chinese.model.AttachmentLocation
import mia.chinese.model.Catalog
import mia.chinese.model.Course
import mia.chinese.model.Edition
import mia.chinese.model.Section
import mia.chinese.model.VideoLocation
import mia.chinese.playback.PlaybackPolicy
import mia.chinese.playback.clampPosition
import mia.chinese.playback.launchExternalYouTube
import mia.chinese.model.attachmentSections
import mia.chinese.model.findAttachment
import mia.chinese.model.findCourse
import mia.chinese.model.findVideo
import mia.chinese.model.isPdf
import mia.chinese.model.noteSections
import mia.chinese.model.orderedSections
import mia.chinese.model.videoSections
import mia.chinese.ui.theme.MiaChineseTheme

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val EDITION_ROUTE = "edition/{editionId}"
private const val COURSE_ROUTE = "course/{courseId}"
private const val ATTACHMENT_ROUTE = "attachment/{attachmentId}"
private const val PLAYER_ROUTE = "player/{videoId}?startPositionMs={startPositionMs}"

private fun editionPath(id: String) = "edition/${Uri.encode(id)}"
private fun coursePath(id: String) = "course/${Uri.encode(id)}"
private fun attachmentPath(id: String) = "attachment/${Uri.encode(id)}"
private fun playerPath(id: String, startPositionMs: Long) =
    "player/${Uri.encode(id)}?startPositionMs=$startPositionMs"

@Composable
fun MiaChineseApp(application: ChineseLearningApp) {
    val viewModel: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(application.catalogRepository, application.progressRepository) as T
        }
    )
    val catalogState by viewModel.catalogState.collectAsState()
    val progress by viewModel.allProgress.collectAsState()
    val pointer by viewModel.lastResumePointer.collectAsState()
    val catalogLocation by viewModel.lastCatalogLocation.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    MiaChineseTheme {
        when (val state = catalogState) {
            CatalogLoadState.Loading -> LoadingScreen()
            is CatalogLoadState.Error -> ErrorScreen(
                message = state.message,
                onRetry = viewModel::retry
            )
            is CatalogLoadState.Ready -> CatalogNavigation(
                catalog = state.catalog,
                progress = progress,
                pointer = pointer,
                catalogLocation = catalogLocation,
                progressRepository = application.progressRepository,
                catalogRepository = application.catalogRepository,
                syncState = syncState,
                onReloadCatalog = viewModel::retry,
                onSyncCatalog = viewModel::syncCatalog
            )
        }
    }
}

@Composable
private fun CatalogNavigation(
    catalog: Catalog,
    progress: List<VideoProgressEntity>,
    pointer: LastResumePointerEntity?,
    catalogLocation: LastCatalogLocationEntity?,
    progressRepository: ProgressRepository,
    catalogRepository: CatalogRepository,
    syncState: CatalogSyncState,
    onReloadCatalog: () -> Unit,
    onSyncCatalog: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    fun saveCatalogLocation(
        editionId: String? = null,
        courseId: String? = null,
        sectionId: String? = null,
        focusedItemId: String? = null
    ) {
        scope.launch {
            progressRepository.saveCatalogLocation(
                editionId = editionId,
                courseId = courseId,
                sectionId = sectionId,
                focusedItemId = focusedItemId
            )
        }
    }

    fun openPlayer(location: VideoLocation, startPositionMs: Long, addCourseToBackStack: Boolean = false) {
        saveCatalogLocation(
            editionId = location.edition.id,
            courseId = location.course.id,
            sectionId = location.section.id,
            focusedItemId = location.video.id
        )
        if (addCourseToBackStack) navController.navigate(coursePath(location.course.id))
        navController.navigate(playerPath(location.video.id, startPositionMs.coerceAtLeast(0L)))
    }

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(HOME_ROUTE) {
            HomeScreen(
                catalog = catalog,
                progress = progress,
                pointer = pointer,
                catalogLocation = catalogLocation,
                onOpenEdition = {
                    saveCatalogLocation(editionId = it.id)
                    navController.navigate(editionPath(it.id))
                },
                onEditionFocused = { edition ->
                    saveCatalogLocation(editionId = edition.id)
                },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onResume = { location, start -> openPlayer(location, start, addCourseToBackStack = true) },
                onRestart = { location -> openPlayer(location, 0L, addCourseToBackStack = true) }
            )
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(
                metadata = catalogRepository.cachedMetadata(),
                syncState = syncState,
                onBack = { navController.popBackStack() },
                onReloadCatalog = onReloadCatalog,
                onSyncCatalog = onSyncCatalog
            )
        }
        composable(
            route = EDITION_ROUTE,
            arguments = listOf(navArgument("editionId") { type = NavType.StringType })
        ) { entry ->
            val edition = catalog.editions.firstOrNull {
                it.id == entry.arguments?.getString("editionId")
            }
            if (edition == null) {
                MissingContentScreen(onBack = { navController.popBackStack() })
            } else {
                EditionScreen(
                    edition = edition,
                    progress = progress,
                    catalogLocation = catalogLocation,
                    onBack = { navController.popBackStack() },
                    onOpenCourse = {
                        saveCatalogLocation(editionId = edition.id, courseId = it.id)
                        navController.navigate(coursePath(it.id))
                    },
                    onCourseFocused = { course ->
                        saveCatalogLocation(editionId = edition.id, courseId = course.id)
                    }
                )
            }
        }
        composable(
            route = COURSE_ROUTE,
            arguments = listOf(navArgument("courseId") { type = NavType.StringType })
        ) { entry ->
            val courseId = entry.arguments?.getString("courseId")
            val found = catalog.editions.firstNotNullOfOrNull { edition ->
                edition.findCourse(courseId.orEmpty())?.let { course -> edition to course }
            }
            if (found == null) {
                MissingContentScreen(onBack = { navController.popBackStack() })
            } else {
                val (edition, course) = found
                CourseScreen(
                    edition = edition,
                    course = course,
                    progress = progress,
                    catalogLocation = catalogLocation,
                    progressRepository = progressRepository,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { location, start -> openPlayer(location, start) },
                    onRestartVideo = { location -> openPlayer(location, 0L) },
                    onOpenAttachment = { location ->
                        saveCatalogLocation(
                            editionId = location.edition.id,
                            courseId = location.course.id,
                            sectionId = location.section.id,
                            focusedItemId = location.attachment.id
                        )
                        navController.navigate(attachmentPath(location.attachment.id))
                    },
                    onSectionFocused = { section ->
                        saveCatalogLocation(
                            editionId = edition.id,
                            courseId = course.id,
                            sectionId = section.id,
                            focusedItemId = section.video?.id ?: section.attachment?.id
                        )
                    }
                )
            }
        }
        composable(
            route = ATTACHMENT_ROUTE,
            arguments = listOf(navArgument("attachmentId") { type = NavType.StringType })
        ) { entry ->
            val attachmentId = entry.arguments?.getString("attachmentId")
            val location = attachmentId?.let(catalog::findAttachment)
            if (location == null) {
                MissingContentScreen(onBack = { navController.popBackStack() })
            } else {
                PdfAttachmentScreen(
                    location = location,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = PLAYER_ROUTE,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            val videoId = entry.arguments?.getString("videoId")
            val location = videoId?.let(catalog::findVideo)
            if (location == null) {
                MissingContentScreen(onBack = { navController.popBackStack() })
            } else {
                val persisted = location.video.progressFrom(progress)
                val requestedStart = entry.arguments?.getLong("startPositionMs") ?: -1L
                val rawStart = if (requestedStart >= 0L) requestedStart else persisted?.positionMs ?: 0L
                val knownDuration = location.video.durationMs ?: persisted?.durationMs
                val start = clampPosition(rawStart, knownDuration)
                PlayerScreen(
                    location = location,
                    startPositionMs = start,
                    progressRepository = progressRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onBackground) {
            Text("正在載入課程資料…", style = MaterialTheme.typography.h5)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { requester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(72.dp),
        contentAlignment = Alignment.Center
    ) {
        TvPanel(modifier = Modifier.width(720.dp)) {
            Text("課程資料載入失敗", style = MaterialTheme.typography.h5)
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 12.dp)
            )
            TvAction(
                onClick = onRetry,
                focusRequester = requester,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            ) {
                Text("重新嘗試")
            }
        }
    }
}

@Composable
private fun MissingContentScreen(onBack: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    ScreenFrame {
        ScreenHeader(title = "找不到教材", onBack = onBack, backFocusRequester = requester)
        TvPanel(modifier = Modifier.padding(top = 32.dp)) {
            Text("教材內容可能已更新，請返回上一頁重新選擇。")
        }
    }
}

@Composable
private fun SettingsScreen(
    metadata: CatalogMetadata?,
    syncState: CatalogSyncState,
    onBack: () -> Unit,
    onReloadCatalog: () -> Unit,
    onSyncCatalog: () -> Unit
) {
    val requester = remember { FocusRequester() }
    val webViewVersion = remember {
        WebView.getCurrentWebViewPackage()?.let { packageInfo ->
            "${packageInfo.packageName} ${packageInfo.versionName ?: "unknown"}"
        } ?: "未回報"
    }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    ScreenFrame {
        ScreenHeader(title = "設定", onBack = onBack)
        TvPanel(modifier = Modifier.padding(top = 30.dp).fillMaxWidth()) {
            Text("課程資料", style = MaterialTheme.typography.h6)
            Text(
                "目前使用已驗證的 APK／本機課程資料，不會因網路中斷而清空課程或播放進度。",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "內容版本：${metadata?.contentVersion ?: "內建 baseline"}",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "最後成功寫入本機：${formatCatalogDate(metadata?.downloadedAtMs)}",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 4.dp)
            )
            TvAction(
                onClick = onReloadCatalog,
                focusRequester = requester,
                modifier = Modifier.padding(top = 20.dp).width(270.dp)
            ) {
                Text("重新載入課程資料")
            }
            TvAction(
                onClick = onSyncCatalog,
                modifier = Modifier.padding(top = 12.dp).width(270.dp)
            ) {
                Text("手動同步遠端資料")
            }
            Text(
                text = when (syncState) {
                    CatalogSyncState.Idle -> "遠端同步尚未執行"
                    CatalogSyncState.Unconfigured -> "尚未設定遠端 catalog endpoint；目前安全使用 APK baseline"
                    CatalogSyncState.Running -> "同步中…目前資料仍可使用"
                    is CatalogSyncState.Success -> syncState.message
                    is CatalogSyncState.Error -> "同步失敗：${syncState.message}（已保留目前資料）"
                },
                style = MaterialTheme.typography.body2,
                color = when (syncState) {
                    is CatalogSyncState.Error -> MaterialTheme.colors.error
                    else -> MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        TvPanel(modifier = Modifier.padding(top = 18.dp).fillMaxWidth()) {
            Text("播放提示", style = MaterialTheme.typography.h6)
            Text(
                "MP4 使用原生播放器；YouTube 預設先走系統 WebView，失敗時可改用 SmartTube／其他外部播放器。可用 build property 關閉 WebView。",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "目前 WebView：$webViewVersion。能否更新及實際使用哪個 provider 由 Android TV 系統／Play Store 決定。",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun formatCatalogDate(timestampMs: Long?): String {
    if (timestampMs == null || timestampMs <= 0L) return "尚無紀錄"
    return DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        Locale.TAIWAN
    ).format(Date(timestampMs))
}

@Composable
private fun HomeScreen(
    catalog: Catalog,
    progress: List<VideoProgressEntity>,
    pointer: LastResumePointerEntity?,
    catalogLocation: LastCatalogLocationEntity?,
    onOpenEdition: (Edition) -> Unit,
    onEditionFocused: (Edition) -> Unit,
    onOpenSettings: () -> Unit,
    onResume: (VideoLocation, Long) -> Unit,
    onRestart: (VideoLocation) -> Unit
) {
    val resumeResolution = remember(catalog, pointer, progress) {
        ResumeTargetResolver.resolve(catalog, pointer, progress)
    }
    val resumeLocation = resumeResolution.location
    val resumeProgress = resumeLocation?.video?.progressFrom(progress)
    val continueRequester = remember { FocusRequester() }
    val fallbackRequester = remember { FocusRequester() }
    val firstEditionRequester = remember { FocusRequester() }
    val rememberedEditionRequester = remember { FocusRequester() }
    val rememberedEditionId = catalogLocation?.editionId?.takeIf { id -> catalog.editions.any { it.id == id } }
    var initialFocusRequested by remember(catalog.contentVersion) { mutableStateOf(false) }

    LaunchedEffect(resumeLocation?.video?.id, resumeResolution.fallback?.video?.id, rememberedEditionId, catalog.editions.firstOrNull()?.id) {
        if (initialFocusRequested) return@LaunchedEffect
        runCatching {
            when {
                resumeLocation != null -> continueRequester.requestFocus()
                resumeResolution.fallback != null -> fallbackRequester.requestFocus()
                rememberedEditionId != null -> rememberedEditionRequester.requestFocus()
                else -> firstEditionRequester.requestFocus()
            }
            // Catalog-location writes happen when an edition receives focus. Do not
            // let that persistence update re-run this initial focus decision.
            initialFocusRequested = true
        }
    }

    ScreenFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mia 國文影片課", style = MaterialTheme.typography.h4)
            Spacer(Modifier.weight(1f))
            if (resumeLocation != null) {
                TvAction(
                    onClick = {
                        val start = if (resumeProgress?.status == ProgressStatus.COMPLETED.name) {
                            0L
                        } else {
                            resumeProgress?.positionMs ?: 0L
                        }
                        onResume(resumeLocation, start)
                    },
                    modifier = Modifier.width(250.dp)
                ) {
                    Text("回到上次進度")
                }
            }
            TvAction(onClick = onOpenSettings, modifier = Modifier.width(140.dp)) {
                Text("設定")
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.65f)
            )
        }

        Text(
            text = "選擇教材版本，依課次觀看老師的課程影片。",
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(top = 8.dp)
        )

        resumeResolution.staleReason?.let { reason ->
            TvPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Text("無法直接恢復上次位置", style = MaterialTheme.typography.h6)
                Text(
                    reason,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 8.dp)
                )
                resumeResolution.fallback?.let { fallback ->
                    TvAction(
                        onClick = { onResume(fallback, 0L) },
                        focusRequester = fallbackRequester,
                        modifier = Modifier.padding(top = 14.dp).width(250.dp)
                    ) {
                        Text("開啟本課程第一部影片")
                    }
                }
            }
        }

        if (resumeLocation != null) {
            Text(
                text = "繼續上次學習",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
            )
            TvPanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${resumeLocation.edition.name}｜${resumeLocation.course.title}",
                            style = MaterialTheme.typography.h6,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            resumeLocation.section.title,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            resumeProgressText(resumeProgress),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    TvAction(
                        onClick = {
                            val start = if (resumeProgress?.status == ProgressStatus.COMPLETED.name) {
                                0L
                            } else {
                                resumeProgress?.positionMs ?: 0L
                            }
                            onResume(resumeLocation, start)
                        },
                        focusRequester = continueRequester,
                        modifier = Modifier.width(230.dp)
                    ) {
                        Text(
                            if (resumeProgress?.status == ProgressStatus.COMPLETED.name) "重新觀看" else "繼續播放"
                        )
                    }
                    if (resumeProgress != null && resumeProgress.positionMs > 0L) {
                        TvAction(
                            onClick = { onRestart(resumeLocation) },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(180.dp)
                        ) {
                            Text("重新開始")
                        }
                    }
                }
            }
        }

        Text(
            text = "選擇教材版本",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(top = if (resumeLocation == null) 36.dp else 28.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            catalog.editions.forEachIndexed { index, edition ->
                TvAction(
                    onClick = { onOpenEdition(edition) },
                    onFocused = { onEditionFocused(edition) },
                    focusRequester = when {
                        resumeLocation == null && edition.id == rememberedEditionId -> rememberedEditionRequester
                        resumeLocation == null && rememberedEditionId == null && index == 0 -> firstEditionRequester
                        else -> null
                    },
                    modifier = Modifier
                        .width(300.dp)
                        .heightIn(min = 116.dp)
                ) {
                    Text(edition.name, style = MaterialTheme.typography.h6)
                    Text(
                        "${edition.grade}・${edition.semester}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "${edition.courses.size} 個課程",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditionScreen(
    edition: Edition,
    progress: List<VideoProgressEntity>,
    catalogLocation: LastCatalogLocationEntity?,
    onBack: () -> Unit,
    onOpenCourse: (Course) -> Unit,
    onCourseFocused: (Course) -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    val rememberedRequester = remember { FocusRequester() }
    val courseListState = rememberLazyListState()
    val rememberedCourseId = catalogLocation?.courseId?.takeIf { id -> edition.courses.any { it.id == id } }
    LaunchedEffect(edition.id, rememberedCourseId) {
        runCatching {
            if (rememberedCourseId != null) {
                val index = edition.courses.indexOfFirst { it.id == rememberedCourseId }
                courseListState.scrollToItem((index + 1).coerceAtLeast(0))
                rememberedRequester.requestFocus()
            } else {
                firstRequester.requestFocus()
            }
        }
    }

    ScreenFrame {
        ScreenHeader(
            title = edition.name,
            subtitle = "${edition.grade}・${edition.semester}",
            onBack = onBack
        )
        LazyColumn(
            state = courseListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "course-heading") {
                Text(
                    "課程清單（${edition.courses.size} 課）",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(top = 30.dp, bottom = 12.dp)
                )
            }
            items(edition.courses, key = { it.id }) { course ->
                val videos = course.videoSections().mapNotNull { it.video }
                val noteCount = course.noteSections().size
                val attachmentCount = course.attachmentSections().size
                val completedCount = videos.count {
                    it.progressFrom(progress)?.status == ProgressStatus.COMPLETED.name
                }
                val inProgress = videos.asSequence()
                    .mapNotNull { it.progressFrom(progress) }
                    .firstOrNull { it.status == ProgressStatus.IN_PROGRESS.name }
                TvAction(
                    onClick = { onOpenCourse(course) },
                    onFocused = { onCourseFocused(course) },
                    focusRequester = when {
                        course.id == rememberedCourseId -> rememberedRequester
                        rememberedCourseId == null && course == edition.courses.firstOrNull() -> firstRequester
                        else -> null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.title, style = MaterialTheme.typography.h6)
                            Text(
                                "${videos.size} 部影片・${noteCount} 則說明・${attachmentCount} 份附件",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                        Text(
                            when {
                                videos.isNotEmpty() && completedCount == videos.size -> "已完成"
                                inProgress != null -> "進行中 ${formatDuration(inProgress.positionMs)}"
                                completedCount > 0 -> "已看 $completedCount/${videos.size}"
                                else -> "尚未觀看"
                            },
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseScreen(
    edition: Edition,
    course: Course,
    progress: List<VideoProgressEntity>,
    catalogLocation: LastCatalogLocationEntity?,
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
    onOpenVideo: (VideoLocation, Long) -> Unit,
    onRestartVideo: (VideoLocation) -> Unit,
    onOpenAttachment: (AttachmentLocation) -> Unit,
    onSectionFocused: (Section) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var externalMessage by remember(course.id) { mutableStateOf<String?>(null) }
    val sections = course.orderedSections()
    val locations = sections.mapNotNull { section ->
        if (section.type.equals("video", ignoreCase = true)) {
            section.video?.let { VideoLocation(edition, course, section, it) }
        } else {
            null
        }
    }
    val firstPlayable = locations.firstOrNull { PlaybackPolicy.isPlayable(it.video) }
    val rememberedPlayable = catalogLocation?.sectionId?.let { sectionId ->
        locations.firstOrNull { it.section.id == sectionId && PlaybackPolicy.isPlayable(it.video) }
    }
    val initialPlayable = rememberedPlayable ?: firstPlayable
    val firstRequester = remember { FocusRequester() }
    val rememberedRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val sectionListState = rememberLazyListState()
    LaunchedEffect(course.id, initialPlayable?.section?.id) {
        runCatching {
            if (initialPlayable == null) {
                backRequester.requestFocus()
            } else {
                val initialIndex = sections.indexOfFirst { it.id == initialPlayable.section.id }
                sectionListState.scrollToItem(initialIndex.coerceAtLeast(0))
                if (rememberedPlayable != null) rememberedRequester.requestFocus()
                else firstRequester.requestFocus()
            }
        }
    }

    ScreenFrame {
        ScreenHeader(
            title = course.title,
            subtitle = "${edition.name}・${edition.semester}",
            onBack = onBack,
            backFocusRequester = if (firstPlayable == null) backRequester else null
        )
        if (course.instructions.isNotEmpty()) {
            TvPanel(modifier = Modifier.padding(top = 22.dp).fillMaxWidth()) {
                Text("學習提醒", style = MaterialTheme.typography.h6)
                course.instructions.forEach { instruction ->
                    Text(
                        "・$instruction",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        Text(
            "${course.videoSections().size} 部影片・${course.noteSections().size} 則說明・${course.attachmentSections().size} 份附件",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            "影片與教材",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        externalMessage?.let { message ->
            TvPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(message, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.secondary)
            }
        }
        if (sections.isEmpty()) {
            TvPanel(modifier = Modifier.fillMaxWidth()) {
                Text("目前沒有課程內容。")
            }
        } else {
            LazyColumn(
                state = sectionListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sections, key = { it.id }) { section ->
                    val location = section.video?.let { VideoLocation(edition, course, section, it) }
                    when {
                        section.type.equals("heading", ignoreCase = true) -> {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.secondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        section.type.equals("note", ignoreCase = true) -> {
                            TvPanel(modifier = Modifier.fillMaxWidth()) {
                                Text(section.title, style = MaterialTheme.typography.h6)
                                section.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.body1,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                        section.type.equals("attachment", ignoreCase = true) && section.attachment != null -> {
                            val attachmentLocation = AttachmentLocation(
                                edition = edition,
                                course = course,
                                section = section,
                                attachment = section.attachment
                            )
                            TvAction(
                                onClick = { onOpenAttachment(attachmentLocation) },
                                onFocused = { onSectionFocused(section) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(section.title, style = MaterialTheme.typography.h6)
                                        Text(
                                            if (section.attachment.isPdf()) {
                                                "PDF 講義・可在 TV 嘗試或用 QR code 在手機開啟"
                                            } else {
                                                "課後附件"
                                            },
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                    Text("查看附件", style = MaterialTheme.typography.body1)
                                }
                            }
                        }
                        location != null -> {
                            val itemProgress = location.video.progressFrom(progress)
                            val isYouTube = location.video.isYouTube
                            val isYouTubeBlocked = isYouTube && !PlaybackPolicy.youtubeEnabled
                            TvPanel(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(location.section.title, style = MaterialTheme.typography.h6)
                                        location.section.description?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.body2,
                                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                                                modifier = Modifier.padding(top = 5.dp)
                                            )
                                        }
                                        Text(
                                            if (isYouTubeBlocked) "YouTube・待 Go/No-Go" else progressLabel(itemProgress),
                                            style = MaterialTheme.typography.body2,
                                            color = if (isYouTubeBlocked) MaterialTheme.colors.secondary else MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                                            modifier = Modifier.padding(top = 7.dp)
                                        )
                                    }
                                    if (isYouTubeBlocked) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "WebView 尚未通過 Go/No-Go",
                                                style = MaterialTheme.typography.body2,
                                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                                            )
                                            TvAction(
                                                onClick = {
                                                    val result = launchExternalYouTube(
                                                        context,
                                                        location.video.videoId.orEmpty()
                                                    )
                                                    externalMessage = result.message
                                                    if (result.player != mia.chinese.playback.ExternalYouTubePlayer.NONE) {
                                                        val existing = location.video.progressFrom(progress)
                                                        scope.launch {
                                                            progressRepository.saveCheckpoint(
                                                                location = location,
                                                                positionMs = existing?.positionMs ?: 0L,
                                                                durationMs = existing?.durationMs ?: location.video.durationMs,
                                                                status = when (existing?.status) {
                                                                    ProgressStatus.COMPLETED.name -> ProgressStatus.COMPLETED
                                                                    ProgressStatus.IN_PROGRESS.name -> ProgressStatus.IN_PROGRESS
                                                                    else -> ProgressStatus.NOT_STARTED
                                                                }
                                                            )
                                                        }
                                                    }
                                                },
                                                onFocused = { onSectionFocused(section) },
                                                modifier = Modifier
                                                    .padding(top = 8.dp)
                                                    .width(260.dp)
                                            ) {
                                                Text("開啟外部播放器")
                                            }
                                        }
                                    } else {
                                        val start = if (itemProgress?.status == ProgressStatus.COMPLETED.name) {
                                            0L
                                        } else {
                                            itemProgress?.positionMs ?: 0L
                                        }
                                        TvAction(
                                            onClick = { onOpenVideo(location, start) },
                                            onFocused = { onSectionFocused(section) },
                                            focusRequester = when {
                                                location == rememberedPlayable -> rememberedRequester
                                                rememberedPlayable == null && location == firstPlayable -> firstRequester
                                                else -> null
                                            },
                                            modifier = Modifier.width(190.dp)
                                        ) {
                                            Text(
                                                when {
                                                    itemProgress?.status == ProgressStatus.COMPLETED.name -> "重新觀看"
                                                    itemProgress != null && itemProgress.positionMs > 0L -> "繼續播放"
                                                    else -> "開始觀看"
                                                }
                                            )
                                        }
                                        if (itemProgress != null && itemProgress.positionMs > 0L) {
                                            TvAction(
                                                onClick = { onRestartVideo(location) },
                                                modifier = Modifier
                                                    .padding(start = 10.dp)
                                                    .width(150.dp)
                                            ) {
                                                Text("重新開始")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(
                    horizontal = if (LocalConfiguration.current.screenHeightDp <= 600) 36.dp else 72.dp,
                    vertical = if (LocalConfiguration.current.screenHeightDp <= 600) 24.dp else 48.dp
                ),
            content = content
        )
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvAction(
            onClick = onBack,
            focusRequester = backFocusRequester,
            modifier = Modifier.width(
                if (LocalConfiguration.current.screenHeightDp <= 600) 150.dp else 180.dp
            )
        ) {
            Text("← 返回")
        }
        Spacer(Modifier.size(if (LocalConfiguration.current.screenHeightDp <= 600) 16.dp else 24.dp))
        ScreenTitle(title = title, subtitle = subtitle)
    }
}

private fun resumeProgressText(progress: VideoProgressEntity?): String = when {
    progress == null -> "上次開啟，尚未開始播放"
    progress.status == ProgressStatus.COMPLETED.name -> "已完成・可重新觀看"
    progress.positionMs > 0L -> "上次看到 ${formatDuration(progress.positionMs)}"
    else -> "上次開啟，尚未開始播放"
}
