package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.LiveWebPreview
import com.example.ui.components.SqlResultTable
import com.example.ui.theme.CodeEditorBg
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
import com.example.ui.theme.ForestWarnInk
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonCompletionScreen(
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
    val lesson = viewModel.currentActiveLesson() ?: return
    val nextLesson = lessons.getOrNull(idx + 1)

    val meta = remember(track, lesson.id, nextLesson?.id) {
        CuriosityFraming.buildLessonMeta(repo, track, idx, lesson, nextLesson)
    }

    var pinDraft by remember { mutableStateOf("") }

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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ForestLime)
                        Text("اكتملت التجربة", style = MaterialTheme.typography.titleMedium, color = ForestLime)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.exitLessonFlow() },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("completion_close_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "العودة للرئيسية", tint = ForestInk)
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
                // Live Result Built by the Learner
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(shape = CircleShape, color = ForestGreenDark) {
                                Text(
                                    text = "${track.shortTitle} · ${lesson.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ForestMint,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "النتيجة التي بنيتها بيدك",
                                style = MaterialTheme.typography.titleMedium,
                                color = ForestLime
                            )

                            when (track) {
                                TrackType.DATABASE -> {
                                    uiState.sqlResult?.let { SqlResultTable(result = it) }
                                }
                                TrackType.CSHARP -> {
                                    val output = uiState.csharpRunResult?.output.orEmpty()
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Surface(
                                            color = CodeEditorBg,
                                            shape = MaterialTheme.shapes.small,
                                            border = BorderStroke(1.dp, ForestLine),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = output.ifBlank { "(تم تنفيذ البرنامج بنجاح)" },
                                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = ForestLime),
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                                TrackType.ANGULAR -> {
                                    LiveWebPreview(html = uiState.angularPreviewHtml, desktopMode = true, minHeightDp = 170)
                                }
                                else -> {
                                    LiveWebPreview(
                                        html = uiState.webPreviewHtml.ifBlank { meta.targetPreviewHtml },
                                        desktopMode = true,
                                        minHeightDp = 170
                                    )
                                }
                            }
                        }
                    }
                }

                // Concept & What You Can Do Now
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("المفهوم الذي اكتشفته", style = MaterialTheme.typography.labelLarge, color = ForestMint)
                                Text(meta.conceptSummary, style = MaterialTheme.typography.bodyMedium, color = ForestInk)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ما تستطيع فعله الآن", style = MaterialTheme.typography.labelLarge, color = ForestLime)
                                Text(meta.canDoNow, style = MaterialTheme.typography.bodyMedium, color = ForestInk)
                            }
                        }
                    }
                }

                // Next Curiosity Spark + Pin Idea for Later
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = ForestWarnInk)
                                Text("شرارة الفضول القادمة", style = MaterialTheme.typography.titleSmall, color = ForestWarnInk)
                            }
                            Text(
                                text = meta.nextSparkQuestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForestInk
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pinDraft,
                                    onValueChange = { pinDraft = it },
                                    placeholder = { Text("ثبّت فكرة للمرة الجاية...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("completion_pin_idea_input")
                                )
                                Button(
                                    onClick = {
                                        viewModel.pinCustomIdea(pinDraft)
                                        pinDraft = ""
                                    },
                                    enabled = pinDraft.isNotBlank(),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("completion_pin_idea_btn")
                                ) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "تثبيت الفكرة")
                                }
                            }
                        }
                    }
                }

                // CTAs: Next Experiment, Open Free Tinker, Back Home
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.openNextExperimentFromCompletion() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestGreen,
                                contentColor = ForestBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("completion_next_experiment_btn")
                        ) {
                            Text("اكتشف التجربة التالية", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = { viewModel.openActiveCompletionInTinker() },
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("completion_open_tinker_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ForestMint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("افتح التجربة في المعمل الحر", color = ForestMint)
                        }

                        uiState.learnerState.growth.livingProjects.firstOrNull()?.let { firstProject ->
                            OutlinedButton(
                                onClick = { viewModel.openLivingProjectInWorkbench(firstProject) },
                                border = BorderStroke(1.dp, ForestLine),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("completion_develop_project_btn")
                            ) {
                                Text("طوّر «${firstProject.title}» الآن", color = ForestLime)
                            }
                        }

                        TextButton(
                            onClick = { viewModel.exitLessonFlow() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("completion_home_btn")
                        ) {
                            Text("العودة للرئيسية", color = ForestMuted)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
