package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository
import com.example.model.AppTab
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.NativeOutcomePreviewCard
import com.example.ui.components.PinIdeaDialog
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
import com.example.widget.LearnStepWidgetProvider

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { CurriculumRepository(context) }

    val activeTrack = uiState.activeTrack
    val lessons = uiState.tracks[activeTrack]?.lessons.orEmpty()
    val activeIdx = viewModel.lessonIndexFor(uiState.learnerState, activeTrack)
        .coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
    val activeLesson = lessons.getOrNull(activeIdx)
    val nextLesson = lessons.getOrNull(activeIdx + 1)

    val meta = remember(activeTrack, activeLesson?.id, nextLesson?.id) {
        activeLesson?.let {
            CuriosityFraming.buildLessonMeta(repo, activeTrack, activeIdx, it, nextLesson)
        }
    }

    val pendingIdea = remember(uiState.learnerState.growth.ideaQueue) {
        uiState.learnerState.growth.ideaQueue.firstOrNull { !it.tried }
    }

    var showPinDialog by remember { mutableStateOf(false) }

    if (showPinDialog) {
        PinIdeaDialog(
            trackLabel = activeTrack.shortTitle,
            onPin = { text -> viewModel.pinCustomIdea(text) },
            onDismiss = { showPinDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Calm Greeting Row + Subtle Pin Idea Trigger
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "أهلًا، ${uiState.activeProfile.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ForestInk
                        )
                        Text(
                            text = uiState.greetingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = ForestMuted
                        )
                    }
                    TextButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_open_pin_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = "ثبّت فكرة للتجربة",
                            tint = ForestMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("سجّل فكرة", color = ForestMint)
                    }
                }
            }

            // Open-Loop Curiosity Idea Card (if user saved an idea for later)
            if (pendingIdea != null) {
                item {
                    val ideaTrack = TrackType.fromKey(pendingIdea.course)
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                        border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lightbulb,
                                        contentDescription = null,
                                        tint = ForestWarnInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "فكرتك المفتوحة بانتظارك",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ForestWarnInk
                                    )
                                }
                                Surface(shape = CircleShape, color = ForestSurface) {
                                    Text(
                                        text = ideaTrack.shortTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestMint,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = pendingIdea.text,
                                style = MaterialTheme.typography.titleMedium,
                                color = ForestInk
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.openIdeaInWorkbench(pendingIdea) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ForestGreen,
                                        contentColor = ForestBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("home_try_pinned_idea_btn")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("جرّبها في المعمل الحر")
                                }
                                TextButton(
                                    onClick = { viewModel.markIdeaTried(pendingIdea.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("home_dismiss_idea_btn")
                                ) {
                                    Text("تجاهل", color = ForestMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Primary Single-Focus Experiment Card
            if (activeLesson != null && meta != null) {
                val activeKey = viewModel.lessonKeyFor(activeTrack, activeIdx)
                val savedGoal = uiState.learnerState.growth.personalGoals[activeKey].orEmpty()
                val isResumable = viewModel.hasPriorProgressForLesson(uiState.learnerState, activeTrack, activeIdx)

                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ForestGreenDark,
                                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "${activeTrack.titleAr} · ${CuriosityFraming.levelLabelFor(activeTrack)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ForestMint,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // Compose-native miniature preview of the outcome
                            NativeOutcomePreviewCard(
                                track = activeTrack,
                                lesson = activeLesson,
                                meta = meta
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = meta.experimentHeadline,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = ForestInk
                                )
                                Text(
                                    text = meta.curiosityQuestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ForestMint
                                )
                            }

                            // Optional Personal Goal Ownership Pill (never mandatory)
                            if (savedGoal.isNotBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ForestGreenDark.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, ForestLime.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Flag, contentDescription = null, tint = ForestLime, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "هدفك الشخصي: $savedGoal",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ForestLime,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else if (meta.personalGoals.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "اختر نتيجة شخصية تناسب فكرتك (اختياري):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestMuted
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        meta.personalGoals.take(2).forEachIndexed { i, goalOption ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.setLessonPersonalGoal(activeKey, goalOption) },
                                                label = { Text(goalOption, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = ForestSurfaceRaised,
                                                    labelColor = ForestInk
                                                ),
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("home_goal_chip_$i")
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.openCurrentExperiment() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForestGreen,
                                    contentColor = ForestBg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_open_experiment_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isResumable) "أكمل من آخر خطوة" else "افتح وشوف النتيجة",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }

            // Subtle Links: Explore Tracks + Optional Home Screen Widget Pin
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.selectTab(AppTab.COURSES) },
                        border = BorderStroke(1.dp, ForestLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("home_explore_tracks_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = ForestMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("استكشف المسارات الستة", color = ForestInk)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ForestMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            val ok = LearnStepWidgetProvider.requestPinWidget(context)
                            if (!ok) {
                                viewModel.showToast("يمكنك تثبيت أداة steplearn من قائمة أدوات الشاشة الرئيسية.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("home_pin_widget_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Widgets,
                            contentDescription = null,
                            tint = ForestMint,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("أضف بطاقة التجربة للشاشة الرئيسية (اختياري)", color = ForestMint)
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
