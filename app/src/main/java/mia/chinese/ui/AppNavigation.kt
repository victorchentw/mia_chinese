package mia.chinese.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import mia.chinese.MainViewModel
import mia.chinese.data.LastResumePointerEntity
import mia.chinese.data.ProgressStatus
import mia.chinese.data.ProgressRepository
import mia.chinese.data.VideoProgressEntity
import mia.chinese.model.Catalog
import mia.chinese.model.Course
import mia.chinese.model.Edition
import mia.chinese.model.Section
import mia.chinese.model.VideoLocation
import mia.chinese.model.findCourse
import mia.chinese.model.findVideo
import mia.chinese.model.videoSections
import mia.chinese.ui.theme.MiaChineseTheme

private const val HOME_ROUTE = "home"
private const val EDITION_ROUTE = "edition/{editionId}"
private const val COURSE_ROUTE = "course/{courseId}"
private const val PLAYER_ROUTE = "player/{videoId}?startPositionMs={startPositionMs}"

private fun editionPath(id: String) = "edition/${Uri.encode(id)}"
private fun coursePath(id: String) = "course/${Uri.encode(id)}"
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
                progressRepository = application.progressRepository
            )
        }
    }
}

@Composable
private fun CatalogNavigation(
    catalog: Catalog,
    progress: List<VideoProgressEntity>,
    pointer: LastResumePointerEntity?,
    progressRepository: ProgressRepository
) {
    val navController = rememberNavController()

    fun openPlayer(location: VideoLocation, startPositionMs: Long, addCourseToBackStack: Boolean = false) {
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
                onOpenEdition = { navController.navigate(editionPath(it.id)) },
                onResume = { location, start -> openPlayer(location, start, addCourseToBackStack = true) },
                onRestart = { location -> openPlayer(location, 0L, addCourseToBackStack = true) }
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
                    onBack = { navController.popBackStack() },
                    onOpenCourse = { navController.navigate(coursePath(it.id)) }
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
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { location, start -> openPlayer(location, start) },
                    onRestartVideo = { location -> openPlayer(location, 0L) }
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
                val start = if (requestedStart >= 0L) requestedStart else persisted?.positionMs ?: 0L
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
        Text("正在載入課程資料…", style = MaterialTheme.typography.h5)
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
    ScreenFrame {
        ScreenHeader(title = "找不到教材", onBack = onBack)
        TvPanel(modifier = Modifier.padding(top = 32.dp)) {
            Text("教材內容可能已更新，請返回上一頁重新選擇。")
        }
    }
}

@Composable
private fun HomeScreen(
    catalog: Catalog,
    progress: List<VideoProgressEntity>,
    pointer: LastResumePointerEntity?,
    onOpenEdition: (Edition) -> Unit,
    onResume: (VideoLocation, Long) -> Unit,
    onRestart: (VideoLocation) -> Unit
) {
    val resumeLocation = remember(catalog, pointer) {
        pointer?.let { candidate ->
            catalog.findVideo(candidate.videoId)?.takeIf { location ->
                location.edition.id == candidate.editionId &&
                    location.course.id == candidate.courseId &&
                    location.section.id == candidate.sectionId &&
                    location.video.revision == candidate.revision
            }
        }
    }
    val resumeProgress = resumeLocation?.video?.progressFrom(progress)
    val continueRequester = remember { FocusRequester() }
    val firstEditionRequester = remember { FocusRequester() }

    LaunchedEffect(resumeLocation?.video?.id, catalog.editions.firstOrNull()?.id) {
        runCatching {
            if (resumeLocation != null) continueRequester.requestFocus()
            else firstEditionRequester.requestFocus()
        }
    }

    ScreenFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mia 國文影片課", style = MaterialTheme.typography.h4)
            Spacer(Modifier.weight(1f))
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
                    focusRequester = if (resumeLocation == null && index == 0) firstEditionRequester else null,
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
    onBack: () -> Unit,
    onOpenCourse: (Course) -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(edition.id) { runCatching { firstRequester.requestFocus() } }

    ScreenFrame {
        ScreenHeader(
            title = edition.name,
            subtitle = "${edition.grade}・${edition.semester}",
            onBack = onBack
        )
        Text(
            "課程清單",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(top = 30.dp, bottom = 12.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(edition.courses, key = { it.id }) { course ->
                val videos = course.videoSections().mapNotNull { it.video }
                val completedCount = videos.count {
                    it.progressFrom(progress)?.status == ProgressStatus.COMPLETED.name
                }
                val inProgress = videos.firstNotNullOfOrNull { it.progressFrom(progress) }
                    ?.takeIf { it.status == ProgressStatus.IN_PROGRESS.name }
                TvAction(
                    onClick = { onOpenCourse(course) },
                    focusRequester = if (course == edition.courses.firstOrNull()) firstRequester else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.title, style = MaterialTheme.typography.h6)
                            Text(
                                "${videos.size} 部影片",
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
    onBack: () -> Unit,
    onOpenVideo: (VideoLocation, Long) -> Unit,
    onRestartVideo: (VideoLocation) -> Unit
) {
    val locations = course.sections
        .sortedBy { it.order }
        .mapNotNull { section ->
            section.video?.let { VideoLocation(edition, course, section, it) }
        }
    val firstPlayable = locations.firstOrNull { it.video.isMp4 }
    val firstRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(course.id) {
        runCatching {
            if (firstPlayable == null) backRequester.requestFocus() else firstRequester.requestFocus()
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
            "影片與教材",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(top = 26.dp, bottom = 12.dp)
        )
        if (locations.isEmpty()) {
            TvPanel(modifier = Modifier.fillMaxWidth()) {
                Text("目前沒有可播放的影片。")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(locations, key = { it.section.id }) { location ->
                    val itemProgress = location.video.progressFrom(progress)
                    val isYouTube = location.video.isYouTube
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
                                    if (isYouTube) "YouTube・待 Go/No-Go" else progressLabel(itemProgress),
                                    style = MaterialTheme.typography.body2,
                                    color = if (isYouTube) MaterialTheme.colors.secondary else MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                                    modifier = Modifier.padding(top = 7.dp)
                                )
                            }
                            if (isYouTube) {
                                Text(
                                    "v${BuildConfig.VERSION_NAME} 尚未啟用",
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                                )
                            } else {
                                val start = if (itemProgress?.status == ProgressStatus.COMPLETED.name) {
                                    0L
                                } else {
                                    itemProgress?.positionMs ?: 0L
                                }
                                TvAction(
                                    onClick = { onOpenVideo(location, start) },
                                    focusRequester = if (location == firstPlayable) firstRequester else null,
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

@Composable
private fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 72.dp, vertical = 48.dp),
        content = content
    )
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvAction(
            onClick = onBack,
            focusRequester = backFocusRequester,
            modifier = Modifier.width(180.dp)
        ) {
            Text("← 返回")
        }
        Spacer(Modifier.size(24.dp))
        ScreenTitle(title = title, subtitle = subtitle)
    }
}

private fun resumeProgressText(progress: VideoProgressEntity?): String = when {
    progress == null -> "上次開啟，尚未開始播放"
    progress.status == ProgressStatus.COMPLETED.name -> "已完成・可重新觀看"
    progress.positionMs > 0L -> "上次看到 ${formatDuration(progress.positionMs)}"
    else -> "上次開啟，尚未開始播放"
}
