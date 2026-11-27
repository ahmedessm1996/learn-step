package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.model.LessonStage
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.LearnerProfilesDialog
import com.example.ui.screens.BuildsScreen
import com.example.ui.screens.CoursesScreen
import com.example.ui.screens.GuidedStepScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LessonCompletionScreen
import com.example.ui.screens.LessonIntroScreen
import com.example.ui.theme.ForestBg
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised
import com.example.ui.theme.StepLearnTheme
import com.example.widget.LearnStepWidgetProvider

data class PendingWidgetLaunch(
    val trackKey: String?,
    val lessonIndex: Int?
)

class MainActivity : ComponentActivity() {
    private var pendingWidgetLaunch by mutableStateOf<PendingWidgetLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureWidgetLaunch(intent)
        setContent {
            StepLearnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    StepLearnApp(
                        pendingWidgetLaunch = pendingWidgetLaunch,
                        onConsumeWidgetLaunch = { pendingWidgetLaunch = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureWidgetLaunch(intent)
    }

    private fun captureWidgetLaunch(intent: Intent?) {
        if (intent?.getBooleanExtra(LearnStepWidgetProvider.EXTRA_LAUNCH_EXPERIMENT, false) == true) {
            val trackKey = intent.getStringExtra(LearnStepWidgetProvider.EXTRA_WIDGET_TRACK)
            val idx = if (intent.hasExtra(LearnStepWidgetProvider.EXTRA_WIDGET_INDEX)) {
                intent.getIntExtra(LearnStepWidgetProvider.EXTRA_WIDGET_INDEX, 0)
            } else null
            pendingWidgetLaunch = PendingWidgetLaunch(trackKey = trackKey, lessonIndex = idx)
            intent.removeExtra(LearnStepWidgetProvider.EXTRA_LAUNCH_EXPERIMENT)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepLearnApp(
    viewModel: StepLearnViewModel = viewModel(),
    pendingWidgetLaunch: PendingWidgetLaunch? = null,
    onConsumeWidgetLaunch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoading, pendingWidgetLaunch) {
        if (!uiState.isLoading && pendingWidgetLaunch != null) {
            viewModel.launchExperimentFromWidget(
                trackKey = pendingWidgetLaunch.trackKey,
                lessonIndex = pendingWidgetLaunch.lessonIndex
            )
            onConsumeWidgetLaunch()
        }
    }

    BackHandler(enabled = uiState.lessonStage != LessonStage.NONE || uiState.activeTab != AppTab.HOME) {
        if (uiState.lessonStage != LessonStage.NONE) {
            viewModel.handleLessonBack()
        } else {
            viewModel.selectTab(AppTab.HOME)
        }
    }

    if (uiState.showProfilesDialog) {
        LearnerProfilesDialog(
            profiles = uiState.profiles,
            activeProfile = uiState.activeProfile,
            errorText = uiState.profileError,
            onSelectProfile = { viewModel.switchProfile(it) },
            onCreateProfile = { viewModel.createProfile(it) },
            onDismiss = { viewModel.setShowProfilesDialog(false) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestBg)
    ) {
        if (uiState.lessonStage != LessonStage.NONE) {
            // Distraction-free full-screen learning flow (Intro -> Step -> Completion)
            AnimatedContent(
                targetState = uiState.lessonStage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "lesson_stage_transition"
            ) { stage ->
                when (stage) {
                    LessonStage.INTRO -> LessonIntroScreen(uiState = uiState, viewModel = viewModel)
                    LessonStage.STEP -> GuidedStepScreen(uiState = uiState, viewModel = viewModel)
                    LessonStage.COMPLETION -> LessonCompletionScreen(uiState = uiState, viewModel = viewModel)
                    LessonStage.NONE -> Unit
                }
            }
        } else {
            // 3-Tab Top-Level Shell (Home, Courses, Builds)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 720.dp

                Scaffold(
                    containerColor = ForestBg,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = ForestSurface,
                                titleContentColor = ForestInk
                            ),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(shape = CircleShape, color = ForestGreenDark) {
                                        Text(
                                            text = "خ",
                                            color = ForestLime,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "steplearn",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ForestInk,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "اكتشف الكود بالتجربة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ForestMint,
                                            maxLines = 1
                                        )
                                    }
                                }
                            },
                            actions = {
                                Surface(
                                    shape = CircleShape,
                                    color = ForestSurfaceRaised,
                                    border = BorderStroke(1.dp, ForestLine),
                                    onClick = { viewModel.setShowProfilesDialog(true) },
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("topbar_profiles_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AccountCircle,
                                            contentDescription = "ملفات التعلّم",
                                            tint = ForestMint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = uiState.activeProfile.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ForestInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = ForestSurface,
                                contentColor = ForestInk
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val selected = uiState.activeTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tab.labelAr
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.labelAr,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ForestBg,
                                            selectedTextColor = ForestLime,
                                            indicatorColor = ForestGreen,
                                            unselectedIconColor = ForestMuted,
                                            unselectedTextColor = ForestMuted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = ForestSurface,
                                contentColor = ForestInk,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(Modifier.size(12.dp))
                                AppTab.entries.forEach { tab ->
                                    val selected = uiState.activeTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tab.labelAr
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.labelAr,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = ForestBg,
                                            selectedTextColor = ForestLime,
                                            indicatorColor = ForestGreen,
                                            unselectedIconColor = ForestMuted,
                                            unselectedTextColor = ForestMuted
                                        ),
                                        modifier = Modifier.testTag("rail_tab_${tab.route}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (uiState.isLoading) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = ForestGreen)
                                    Spacer(Modifier.size(12.dp))
                                    Text("جاري تجهيز التجارب التفاعلية...", color = ForestMuted)
                                }
                            } else {
                                MainTabContent(uiState = uiState, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Floating subtle status toast banner
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = ForestGreenDark,
                shape = CircleShape,
                border = BorderStroke(1.dp, ForestLime),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = uiState.toastMessage.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ForestInk,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun MainTabContent(uiState: StepLearnUiState, viewModel: StepLearnViewModel) {
    when (uiState.activeTab) {
        AppTab.HOME -> HomeScreen(uiState = uiState, viewModel = viewModel)
        AppTab.COURSES -> CoursesScreen(uiState = uiState, viewModel = viewModel)
        AppTab.BUILDS -> BuildsScreen(uiState = uiState, viewModel = viewModel)
    }
}

private fun tabIcon(tab: AppTab, selected: Boolean): ImageVector = when (tab) {
    AppTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    AppTab.COURSES -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
    AppTab.BUILDS -> if (selected) Icons.Filled.FolderSpecial else Icons.Outlined.FolderSpecial
}
