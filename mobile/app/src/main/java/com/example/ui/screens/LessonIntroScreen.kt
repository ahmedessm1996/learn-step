package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.LiveWebPreview
import com.example.ui.components.PredictHookCard
import com.example.ui.components.StepPlanBottomSheet
import com.example.ui.theme.CodeEditorBg
import com.example.ui.theme.CodeEditorText
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
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonIntroScreen(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { CurriculumRepository(context) }
    val track = uiState.activeTrack
    val lessons = uiState.tracks[track]?.lessons.orEmpty()
    val idx = viewModel.lessonIndexFor(uiState.learnerState, track)
        .coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
    val lesson = lessons.getOrNull(idx) ?: return
    val nextLesson = lessons.getOrNull(idx + 1)

    val meta = remember(track, lesson.id, nextLesson?.id) {
        CuriosityFraming.buildLessonMeta(repo, track, idx, lesson, nextLesson)
    }
    val steps = viewModel.currentActiveSteps()
    val lessonKey = viewModel.lessonKeyFor(track, idx)
    val activePersonalGoal = uiState.learnerState.growth.personalGoals[lessonKey].orEmpty()
    var customGoalDraft by remember(lessonKey) { mutableStateOf("") }

    if (uiState.showStepPlanSheet) {
        StepPlanBottomSheet(
            lessonTitle = lesson.title,
            steps = steps,
            currentIndex = viewModel.currentActiveStepIndex(),
            unlockedIndex = steps.lastIndex,
            tinkerMode = true,
            onSelectStep = { stepIdx ->
                viewModel.selectActiveStep(stepIdx)
                viewModel.startLessonFromIntro(false)
            },
            onDismiss = { viewModel.setShowStepPlanSheet(false) }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ForestBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestSurface,
                    titleContentColor = ForestInk
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleLessonBack() },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("intro_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = ForestInk
                        )
                    }
                },
                title = {
                    Surface(shape = CircleShape, color = ForestGreenDark) {
                        Text(
                            text = "${track.shortTitle} · ${lesson.title}",
                            style = MaterialTheme.typography.labelLarge,
                            color = ForestMint,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    if (steps.size > 1) {
                        TextButton(
                            onClick = { viewModel.setShowStepPlanSheet(true) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("intro_plan_btn")
                        ) {
                            Icon(Icons.Filled.ListAlt, contentDescription = null, tint = ForestMint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("خطة الدرس", color = ForestMint)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Problem Headline & Incomplete Baseline State
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = meta.experimentHeadline,
                                style = MaterialTheme.typography.headlineSmall,
                                color = ForestInk
                            )
                            Text(
                                text = meta.problemStatement,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForestMuted
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "نقطة البداية الحالية",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ForestMint
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Surface(
                                        color = CodeEditorBg,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, ForestLine),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = meta.baselineSnippet,
                                            style = TextStyle(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 12.sp,
                                                lineHeight = 19.sp,
                                                color = CodeEditorText
                                            ),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5-Second Predict-Before-Reveal Challenge
                item {
                    PredictHookCard(
                        challenge = meta.predictChallenge,
                        selectedOption = uiState.introPredictSelected,
                        revealed = uiState.introPredictRevealed,
                        onSelectOption = { viewModel.selectIntroPrediction(it) },
                        onSkipReveal = { viewModel.skipIntroPrediction() }
                    )
                }

                // Animated Curtain Reveal of Real Outcome Preview
                item {
                    AnimatedVisibility(
                        visible = uiState.introPredictRevealed,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "النتيجة التي ستصل إليها في هذه التجربة",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ForestLime
                                )
                                LiveWebPreview(
                                    html = meta.targetPreviewHtml,
                                    desktopMode = true,
                                    minHeightDp = 150
                                )
                            }
                        }
                    }
                }

                // Optional Personal Goal Ownership Selector (2-3 quick pills + short optional custom input)
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = null, tint = ForestMint, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "هدفك الشخصي (اختياري)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ForestInk
                                )
                            }
                            Text(
                                text = "اربط التجربة بفكرة تهمك، أو تجاوز وابدأ مباشرة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMuted
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                meta.personalGoals.take(3).forEachIndexed { i, goalOption ->
                                    val selected = activePersonalGoal == goalOption
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) viewModel.clearLessonPersonalGoal(lessonKey)
                                            else viewModel.setLessonPersonalGoal(lessonKey, goalOption)
                                        },
                                        label = { Text(goalOption) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForestGreen,
                                            selectedLabelColor = ForestBg,
                                            containerColor = ForestSurfaceRaised,
                                            labelColor = ForestInk
                                        ),
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("intro_goal_chip_$i")
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customGoalDraft,
                                    onValueChange = { customGoalDraft = it },
                                    placeholder = { Text("أو اكتب هدفك الخاص باختصار...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("intro_custom_goal_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setLessonPersonalGoal(lessonKey, customGoalDraft)
                                        customGoalDraft = ""
                                    },
                                    enabled = customGoalDraft.isNotBlank(),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("intro_custom_goal_save_btn")
                                ) {
                                    Text("حفظ", color = ForestMint)
                                }
                            }
                        }
                    }
                }

                // Primary Action Buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.startLessonFromIntro(inTinkerMode = false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestGreen,
                                contentColor = ForestBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("intro_start_experiment_btn")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ابدأ التجربة", style = MaterialTheme.typography.titleSmall)
                        }

                        OutlinedButton(
                            onClick = { viewModel.startLessonFromIntro(inTinkerMode = true) },
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("intro_start_tinker_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ForestMint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("افتح مباشرة في المعمل الحر", color = ForestInk)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
