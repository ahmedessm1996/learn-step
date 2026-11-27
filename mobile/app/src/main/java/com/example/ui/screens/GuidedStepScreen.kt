package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Visibility
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository
import com.example.engine.SmartHintEngine
import com.example.model.RawLessonStep
import com.example.model.StepExecutionPhase
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.CSharpTraceSection
import com.example.ui.components.DeepTheoryBottomSheet
import com.example.ui.components.GentleTreeCard
import com.example.ui.components.LiveWebPreview
import com.example.ui.components.MobileCodeEditor
import com.example.ui.components.NewAngularFileDialog
import com.example.ui.components.PinIdeaDialog
import com.example.ui.components.SaveSnapshotDialog
import com.example.ui.components.SlotBuilderCard
import com.example.ui.components.SqlResultTable
import com.example.ui.components.StepPlanBottomSheet
import com.example.ui.components.TieredHintDrawer
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
import com.example.ui.theme.ForestWarnBg
import com.example.ui.theme.ForestWarnInk
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidedStepScreen(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { CurriculumRepository(context) }

    val track = uiState.activeTrack
    val lessons = uiState.tracks[track]?.lessons.orEmpty()
    val lessonIndex = viewModel.lessonIndexFor(uiState.learnerState, track)
        .coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
    val lesson = viewModel.currentActiveLesson() ?: return
    val nextLesson = lessons.getOrNull(lessonIndex + 1)
    val steps = viewModel.currentActiveSteps()
    val stepIdx = viewModel.currentActiveStepIndex()
    val step: RawLessonStep? = viewModel.currentActiveStep()
    val isTinker = uiState.isCurrentTrackTinkerMode
    val isFinalStep = steps.isEmpty() || stepIdx >= steps.lastIndex

    val meta = remember(track, lesson.id, nextLesson?.id) {
        CuriosityFraming.buildLessonMeta(repo, track, lessonIndex, lesson, nextLesson)
    }
    val lessonKey = viewModel.lessonKeyFor(track, lessonIndex)
    val personalGoal = uiState.learnerState.growth.personalGoals[lessonKey].orEmpty()
    val activeLivingProject = uiState.activeLivingProjectId?.let { id ->
        uiState.learnerState.growth.livingProjects.firstOrNull { it.id == id }
    }

    var showVisualExample by remember(lesson.id, stepIdx) { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var showNewAngularFileDialog by remember { mutableStateOf(false) }

    val unlockedIdx = when (track) {
        TrackType.CSHARP -> uiState.learnerState.csharp.maxUnlockedStepMap[lesson.id] ?: stepIdx
        TrackType.ANGULAR -> steps.lastIndex.coerceAtLeast(0)
        else -> {
            val key = viewModel.currentWebLessonKey()
            uiState.learnerState.growth.lessonFlow[key]?.unlocked ?: stepIdx
        }
    }

    if (uiState.showStepPlanSheet) {
        StepPlanBottomSheet(
            lessonTitle = lesson.title,
            steps = steps,
            currentIndex = stepIdx,
            unlockedIndex = unlockedIdx,
            tinkerMode = isTinker,
            onSelectStep = { viewModel.selectActiveStep(it) },
            onDismiss = { viewModel.setShowStepPlanSheet(false) }
        )
    }

    if (uiState.showDeepTheorySheet) {
        DeepTheoryBottomSheet(
            lesson = lesson,
            meta = meta,
            onDismiss = { viewModel.setShowDeepTheorySheet(false) }
        )
    }

    if (showPinDialog) {
        PinIdeaDialog(
            trackLabel = track.shortTitle,
            onPin = { viewModel.pinCustomIdea(it) },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showSnapshotDialog) {
        SaveSnapshotDialog(
            onSave = { title, note -> viewModel.saveTinkerSnapshot(title, note) },
            onDismiss = { showSnapshotDialog = false }
        )
    }

    if (showNewAngularFileDialog) {
        NewAngularFileDialog(
            onConfirm = { viewModel.createAngularFile(it) },
            onDismiss = { showNewAngularFileDialog = false }
        )
    }

    val activeFileName = when (track) {
        TrackType.ANGULAR -> uiState.angularActiveFilePath
        else -> track.fileName
    }
    val activeCode = viewModel.currentActiveCode()
    val targetSnippet = remember(step) {
        step?.from?.takeIf { it.isNotBlank() }
            ?: step?.expectedLine?.takeIf { it.isNotBlank() }
    }
    val activeTrace = if (track == TrackType.CSHARP) {
        val list = uiState.csharpRunResult?.trace.orEmpty()
        list.getOrNull(uiState.csharpTraceIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    } else null

    val gentleAlert = when (track) {
        TrackType.ANGULAR -> uiState.angularGentleAlert
        TrackType.CSHARP -> null
        else -> uiState.webGentleAlert
    }

    val slotModel = remember(track, lesson.id, stepIdx, uiState.stepCompletedForCurrentStep) {
        if (track in listOf(TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE)) {
            SmartHintEngine.buildSlotModel(step, track, isDone = uiState.stepCompletedForCurrentStep)
        } else null
    }

    val errorFeedback = when (track) {
        TrackType.CSHARP -> uiState.csharpFeedback?.takeIf { uiState.csharpFeedbackPassed != true }
        TrackType.ANGULAR -> uiState.angularFeedback?.takeIf { uiState.angularFeedbackPassed != true }
        else -> uiState.webHintResult?.let { res ->
            if (!res.passed) res.message else null
        }
    }

    val canAdvanceGuided = !isTinker && (
        uiState.stepCompletedForCurrentStep ||
            step == null ||
            step.type in listOf("observe", "project")
        )

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
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("step_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ForestInk)
                    }
                },
                title = {
                    // Guided | Free Tinker Segmented Toggle (no loud step counters)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !isTinker,
                            onClick = { if (isTinker) viewModel.toggleActiveTinkerMode() },
                            label = { Text("موجه") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = ForestBg
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("mode_guided_chip")
                        )
                        FilterChip(
                            selected = isTinker,
                            onClick = { if (!isTinker) viewModel.toggleActiveTinkerMode() },
                            label = { Text("تجربة حرة") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestWarnInk,
                                selectedLabelColor = ForestBg
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("mode_tinker_chip")
                        )
                    }
                },
                actions = {
                    if (steps.size > 1 && !isTinker) {
                        IconButton(
                            onClick = { viewModel.setShowStepPlanSheet(true) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("step_plan_btn")
                        ) {
                            Icon(Icons.Filled.ListAlt, contentDescription = "خطة الدرس", tint = ForestMint)
                        }
                    }
                    IconButton(
                        onClick = { viewModel.setShowDeepTheorySheet(true) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("step_deep_theory_btn")
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = "تعمّق أكثر", tint = ForestMint)
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 720.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0. Smart Resume 1-Line Reminder Banner (only when resuming a previously started lesson)
                if (uiState.isResumingLesson && !isTinker && activeLivingProject == null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = ForestSurfaceRaised,
                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().testTag("step_resume_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "تذكير سريع: ${meta.resumeReminder}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMint,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.revisitIntroFromStep() },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("step_revisit_intro_btn")
                            ) {
                                Text("المقدمة", color = ForestLime, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // 0b. Active Living Project Banner (when developing a LivingProject)
                if (activeLivingProject != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = ForestGreenDark.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, ForestLime.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth().testTag("step_living_project_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تطوير مشروع: ${activeLivingProject.title}", style = MaterialTheme.typography.titleSmall, color = ForestLime)
                                Text(activeLivingProject.goal, style = MaterialTheme.typography.bodySmall, color = ForestInk)
                            }
                            Button(
                                onClick = { viewModel.saveActiveLivingProjectProgress() },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = ForestBg),
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("step_save_living_project_btn")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("احفظ في المشروع")
                            }
                        }
                    }
                }

                // STEP 1: الهدف والمثال البصري (Goal + Personal Goal + Optional Visual Example)
                if (!isTinker) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurface),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = step?.title?.ifBlank { lesson.title } ?: lesson.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ForestInk,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { showVisualExample = !showVisualExample },
                                    border = BorderStroke(1.dp, ForestLine),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("step_toggle_example_btn")
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (showVisualExample) "إخفاء المثال" else "المثال البصري", color = ForestMint)
                                }
                            }

                            if (personalGoal.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = ForestGreenDark.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, ForestLime.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Flag, contentDescription = null, tint = ForestLime, modifier = Modifier.size(14.dp))
                                        Text("هدفك الشخصي: $personalGoal", style = MaterialTheme.typography.labelMedium, color = ForestLime)
                                    }
                                }
                            }

                            val goalText = step?.objective?.ifBlank {
                                step.instruction.ifBlank { step.task.ifBlank { lesson.task } }
                            } ?: lesson.task
                            if (goalText.isNotBlank()) {
                                Text(
                                    text = goalText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ForestMint
                                )
                            }

                            AnimatedVisibility(visible = showVisualExample) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("النتيجة البصرية المستهدفة:", style = MaterialTheme.typography.labelSmall, color = ForestLime)
                                    LiveWebPreview(
                                        html = meta.targetPreviewHtml,
                                        desktopMode = true,
                                        minHeightDp = 130
                                    )
                                }
                            }

                            // Quiz / Diagnose Interactive Options (if this step is quiz/diagnose)
                            if (step != null && (step.type == "quiz" || step.type == "diagnose") && step.options.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                if (!step.question.isNullOrBlank()) {
                                    Text(
                                        text = step.question,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ForestInk
                                    )
                                }
                                val chosenOpt = when (track) {
                                    TrackType.CSHARP -> uiState.learnerState.csharp.quizSelectedMap[lesson.id]
                                    TrackType.ANGULAR -> uiState.learnerState.angular.quizSelectedMap[lesson.id]
                                    else -> uiState.webQuizSelected
                                }
                                val correctIdx = step.correct ?: step.correctOptionIndex ?: 0
                                step.options.forEachIndexed { idx, opt ->
                                    val isChosen = chosenOpt == idx
                                    val isRight = isChosen && idx == correctIdx
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = when {
                                            isRight -> ForestGreenDark.copy(alpha = 0.7f)
                                            isChosen -> ForestWarnBg
                                            else -> ForestSurfaceRaised
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isRight -> ForestLime
                                                isChosen -> ForestWarnInk
                                                else -> ForestLine
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { viewModel.selectActiveQuizOption(idx) }
                                            .testTag("step_quiz_opt_$idx")
                                    ) {
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ForestInk,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (gentleAlert != null && uiState.stepPhase == StepExecutionPhase.BEFORE_RUN) {
                        GentleTreeCard(alert = gentleAlert)
                    }

                    if (slotModel != null && uiState.stepPhase == StepExecutionPhase.BEFORE_RUN) {
                        SlotBuilderCard(
                            model = slotModel,
                            onApply = { viewModel.applyWebSlot(it) }
                        )
                    }
                } else {
                    // Free Tinker Sandbox Controls
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                        border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (activeLivingProject != null) {
                                    "طوّر مشروعك بحرية، ثم اضغط «تشغيل» لمشاهدة النتيجة وحفظ التقدم."
                                } else {
                                    "المعمل الحر · عدّل وكسّر القواعد بحرية دون التأثير على الدرس الموجه"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestWarnInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.mutateActiveTinkerSpark() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ForestGreenDark,
                                        contentColor = ForestMint
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("tinker_spark_btn")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تعديل مفاجئ")
                                }
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    border = BorderStroke(1.dp, ForestLine),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("tinker_pin_idea_btn")
                                ) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ثبّت فكرة", color = ForestInk)
                                }
                                OutlinedButton(
                                    onClick = { showSnapshotDialog = true },
                                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("tinker_save_snapshot_btn")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, tint = ForestLime, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("احفظها في تجاربي", color = ForestLime)
                                }
                            }
                        }
                    }
                }

                // Angular File Switcher (when BEFORE_RUN or wide)
                if (track == TrackType.ANGULAR && (isWide || uiState.stepPhase == StepExecutionPhase.BEFORE_RUN)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showNewAngularFileDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("ng_new_file_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ملف")
                        }
                        val fileKeys = remember(uiState.angularFiles, lesson.file) {
                            val priority = listOfNotNull(
                                lesson.file.takeIf { it.isNotBlank() },
                                "src/app/app.ts",
                                "src/app/app.html",
                                "src/app/app.css",
                                "src/app/core/store/cinema.store.ts"
                            ).distinct()
                            (priority + uiState.angularFiles.keys).distinct().filter { uiState.angularFiles.containsKey(it) }
                        }
                        fileKeys.forEach { path ->
                            val shortName = path.substringAfterLast('/')
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                FilterChip(
                                    selected = path == uiState.angularActiveFilePath,
                                    onClick = { viewModel.selectAngularFile(path) },
                                    label = {
                                        Text(
                                            shortName,
                                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestGreen,
                                        selectedLabelColor = ForestBg
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }
                }

                // C# Optional Console.ReadLine() stdin input
                if (track == TrackType.CSHARP && (isWide || uiState.stepPhase == StepExecutionPhase.BEFORE_RUN) &&
                    (uiState.csharpCodeDraft.contains("ReadLine") || uiState.csharpStdin.isNotBlank())
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = uiState.csharpStdin,
                            onValueChange = { viewModel.updateCSharpStdin(it) },
                            label = { Text("Console.ReadLine() Input") },
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = ForestInk),
                            modifier = Modifier.fillMaxWidth().testTag("cs_stdin_input")
                        )
                    }
                }

                // Progressive Phase Indicator Tabs in Portrait (الكود -> النتيجة والتفسير)
                if (!isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.stepPhase == StepExecutionPhase.BEFORE_RUN,
                            onClick = { viewModel.returnToCodeEditPhase() },
                            label = { Text("١. تعديل الكود") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = ForestBg
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("tab_code_pane")
                        )
                        FilterChip(
                            selected = uiState.stepPhase == StepExecutionPhase.AFTER_RUN,
                            onClick = { viewModel.runActiveCode() },
                            label = { Text("٢. النتيجة والتفسير") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = ForestBg
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("tab_preview_pane")
                        )
                    }
                }

                // Adaptive Layout: Wide (Side-by-Side) vs Portrait (Progressive BEFORE_RUN -> AFTER_RUN)
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EditorAndRunBlock(
                                activeCode = activeCode,
                                activeFileName = activeFileName,
                                targetSnippet = targetSnippet,
                                activeTraceLine = activeTrace?.line,
                                skippedLines = activeTrace?.skippedLines ?: emptySet(),
                                onCodeChange = { viewModel.updateActiveCode(it) },
                                onHintClick = { viewModel.revealNextHintTier() },
                                onPinIdeaClick = { showPinDialog = true },
                                onResetClick = { viewModel.resetActiveCode() },
                                onRunClick = { viewModel.runActiveCode() }
                            )
                            TieredHintDrawer(
                                revealedTier = uiState.hintTierRevealed,
                                hintText = viewModel.stepHintText(step, lesson, uiState.hintTierRevealed),
                                onRevealNextTier = { viewModel.revealNextHintTier() }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiveOutputPane(uiState = uiState, viewModel = viewModel)
                            AfterRunFeedbackAndNextSection(
                                uiState = uiState,
                                errorFeedback = errorFeedback,
                                canAdvanceGuided = canAdvanceGuided,
                                isFinalStep = isFinalStep,
                                isLivingProject = activeLivingProject != null,
                                isPortrait = false,
                                viewModel = viewModel
                            )
                        }
                    }
                } else {
                    if (uiState.stepPhase == StepExecutionPhase.BEFORE_RUN) {
                        // STEPS 2 & 3: تعديل واحد في الكود -> تشغيل
                        EditorAndRunBlock(
                            activeCode = activeCode,
                            activeFileName = activeFileName,
                            targetSnippet = targetSnippet,
                            activeTraceLine = activeTrace?.line,
                            skippedLines = activeTrace?.skippedLines ?: emptySet(),
                            onCodeChange = { viewModel.updateActiveCode(it) },
                            onHintClick = { viewModel.revealNextHintTier() },
                            onPinIdeaClick = { showPinDialog = true },
                            onResetClick = { viewModel.resetActiveCode() },
                            onRunClick = { viewModel.runActiveCode() }
                        )

                        TieredHintDrawer(
                            revealedTier = uiState.hintTierRevealed,
                            hintText = viewModel.stepHintText(step, lesson, uiState.hintTierRevealed),
                            onRevealNextTier = { viewModel.revealNextHintTier() }
                        )
                    } else {
                        // STEPS 4 & 5: مشاهدة النتيجة وتفسيرها -> الخطوة التالية
                        LiveOutputPane(uiState = uiState, viewModel = viewModel)

                        OutlinedButton(
                            onClick = { viewModel.returnToCodeEditPhase() },
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("step_back_to_edit_btn")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("عدّل الكود مجددًا", color = ForestInk)
                        }

                        AfterRunFeedbackAndNextSection(
                            uiState = uiState,
                            errorFeedback = errorFeedback,
                            canAdvanceGuided = canAdvanceGuided,
                            isFinalStep = isFinalStep,
                            isLivingProject = activeLivingProject != null,
                            isPortrait = true,
                            viewModel = viewModel
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AfterRunFeedbackAndNextSection(
    uiState: StepLearnUiState,
    errorFeedback: String?,
    canAdvanceGuided: Boolean,
    isFinalStep: Boolean,
    isLivingProject: Boolean,
    isPortrait: Boolean,
    viewModel: StepLearnViewModel
) {
    AnimatedVisibility(visible = !errorFeedback.isNullOrBlank()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = ForestWarnBg,
            border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = errorFeedback.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForestWarnInk
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isPortrait) {
                        TextButton(
                            onClick = { viewModel.returnToCodeEditPhase() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("ارجع للتعديل", color = ForestInk)
                        }
                    }
                    if (uiState.hintTierRevealed == 0) {
                        TextButton(
                            onClick = { viewModel.revealNextHintTier() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("اطلب تلميحًا", color = ForestMint)
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = !uiState.lastRunInterpretation.isNullOrBlank() || canAdvanceGuided || isLivingProject) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = ForestGreenDark.copy(alpha = 0.55f)),
            border = BorderStroke(1.dp, ForestLime.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.lastRunInterpretation.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "تفسير ما حدث",
                            style = MaterialTheme.typography.labelLarge,
                            color = ForestLime
                        )
                        Text(
                            text = uiState.lastRunInterpretation.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ForestInk
                        )
                    }
                }
                if (isLivingProject) {
                    Button(
                        onClick = { viewModel.saveActiveLivingProjectProgress() },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = ForestBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("step_save_living_cta_btn")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("حفظ التطوير في مشروعي")
                    }
                } else if (canAdvanceGuided) {
                    Button(
                        onClick = { viewModel.advanceOrCompleteStep() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = ForestBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("step_next_cta_btn")
                    ) {
                        Icon(
                            imageVector = if (isFinalStep) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFinalStep) "إتمام التجربة" else "الخطوة التالية")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorAndRunBlock(
    activeCode: String,
    activeFileName: String,
    targetSnippet: String?,
    activeTraceLine: Int?,
    skippedLines: Set<Int>,
    onCodeChange: (String) -> Unit,
    onHintClick: () -> Unit,
    onPinIdeaClick: () -> Unit,
    onResetClick: () -> Unit,
    onRunClick: () -> Unit
) {
    MobileCodeEditor(
        code = activeCode,
        onCodeChange = onCodeChange,
        fileName = activeFileName,
        targetSnippet = targetSnippet,
        activeTraceLine = activeTraceLine,
        skippedLines = skippedLines,
        onHintClick = onHintClick,
        onPinIdeaClick = onPinIdeaClick,
        onResetClick = onResetClick,
        testTag = "step_code_editor"
    )
    Button(
        onClick = onRunClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = ForestGreen,
            contentColor = ForestBg
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .minimumInteractiveComponentSize()
            .testTag("step_run_btn")
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("تشغيل ومشاهدة النتيجة", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LiveOutputPane(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel
) {
    when (uiState.activeTrack) {
        TrackType.DATABASE -> {
            val res = uiState.sqlResult
            if (res != null) {
                SqlResultTable(result = res)
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ForestSurface,
                    border = BorderStroke(1.dp, ForestLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "اضغط «تشغيل» لتنفيذ الاستعلام وعرض الجدول.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForestMuted,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
        TrackType.CSHARP -> {
            CSharpTraceSection(
                runResult = uiState.csharpRunResult,
                traceIndex = uiState.csharpTraceIndex,
                isAutoPlaying = uiState.isAutoPlayingTrace,
                onSetTraceIndex = { viewModel.setTraceIndex(it) },
                onToggleAutoPlay = { viewModel.toggleAutoPlayTrace() }
            )
        }
        TrackType.ANGULAR -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("معاينة تطبيق السينما", style = MaterialTheme.typography.titleSmall, color = ForestMint)
                    IconButton(
                        onClick = { viewModel.toggleWebDesktopPreview() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (uiState.webDesktopPreview) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                            contentDescription = "تبديل حجم شاشة المعاينة",
                            tint = ForestLime
                        )
                    }
                }
                LiveWebPreview(
                    html = uiState.angularPreviewHtml,
                    desktopMode = uiState.webDesktopPreview
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("النتيجة المرئية الحية", style = MaterialTheme.typography.titleSmall, color = ForestMint)
                    IconButton(
                        onClick = { viewModel.toggleWebDesktopPreview() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (uiState.webDesktopPreview) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                            contentDescription = "تبديل عرض الهاتف أو سطح المكتب",
                            tint = ForestLime
                        )
                    }
                }
                LiveWebPreview(
                    html = uiState.webPreviewHtml,
                    desktopMode = uiState.webDesktopPreview
                )
            }
        }
    }
}
