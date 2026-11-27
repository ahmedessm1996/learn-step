package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientSoundEngine
import com.example.data.CuriosityFraming
import com.example.data.CuriositySparkOption
import com.example.data.CurriculumRepository
import com.example.data.CurriculumRepository.Companion.ar
import com.example.data.DailyChallengeSpec
import com.example.data.LearnerDatabase
import com.example.data.PreferencesRepository
import com.example.data.UiPreferences
import com.example.engine.AngularSimulator
import com.example.engine.CSharpRunResult
import com.example.engine.CSharpRuntime
import com.example.engine.GentleTreeAlert
import com.example.engine.HintEvaluationResult
import com.example.engine.PreviewHtmlBuilder
import com.example.engine.SmartHintEngine
import com.example.engine.SqlExecutionResult
import com.example.engine.SqlPlaygroundEngine
import com.example.model.AmbientSoundType
import com.example.model.AppTab
import com.example.model.CuriosityIdea
import com.example.model.CurriculumTrack
import com.example.model.GalleryPhoto
import com.example.model.LearnerProfile
import com.example.model.LearnerState
import com.example.model.LessonFlowState
import com.example.model.LessonStage
import com.example.model.LivingProject
import com.example.model.MiniCalculatorItem
import com.example.model.MiniNoteItem
import com.example.model.MiniTodoItem
import com.example.model.PredictionRecord
import com.example.model.ProfileCardColor
import com.example.model.ProjectPart
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.StepExecutionPhase
import com.example.model.TinkerSnapshot
import com.example.model.TrackType
import com.example.model.hintTextAt
import com.example.widget.LearnStepWidgetProvider
import com.example.widget.WidgetExperimentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.min

data class StepLearnUiState(
    val isLoading: Boolean = true,
    val activeTab: AppTab = AppTab.HOME,
    val lessonStage: LessonStage = LessonStage.NONE,
    val activeTrack: TrackType = TrackType.HTML,
    val tracks: Map<TrackType, CurriculumTrack> = emptyMap(),
    val activeProfile: LearnerProfile = LearnerProfile(),
    val profiles: List<LearnerProfile> = emptyList(),
    val profileError: String? = null,
    val learnerState: LearnerState = LearnerState(),
    val uiPreferences: UiPreferences = UiPreferences(),
    val greetingText: String = CurriculumRepository.greetings.first(),
    val toastMessage: String? = null,

    // Redesigned Curiosity & Lesson Overlay State
    val activeIdeaId: String? = null,
    val activeLivingProjectId: String? = null,
    val isResumingLesson: Boolean = false,
    val stepPhase: StepExecutionPhase = StepExecutionPhase.BEFORE_RUN,
    val hintTierRevealed: Int = 0,
    val introPredictSelected: Int? = null,
    val introPredictRevealed: Boolean = false,
    val stepCompletedForCurrentStep: Boolean = false,
    val lastRunInterpretation: String? = null,
    val showStepPlanSheet: Boolean = false,
    val showDeepTheorySheet: Boolean = false,

    // Guided draft backups for non-destructive Free Tinker Mode
    val guidedWebDraftBackup: String? = null,
    val guidedCSharpDraftBackup: String? = null,
    val guidedAngularFilesBackup: Map<String, String>? = null,

    // Web / SQL workbench state
    val webSteps: List<RawLessonStep> = emptyList(),
    val webCodeDraft: String = "",
    val webPreviewHtml: String = "",
    val sqlResult: SqlExecutionResult? = null,
    val webHintResult: HintEvaluationResult? = null,
    val webGentleAlert: GentleTreeAlert? = null,
    val webTinkerMode: Boolean = false,
    val webTinkerMutationIndex: Int = 0,
    val webDesktopPreview: Boolean = true,
    val webQuizSelected: Int? = null,
    val webHandCraftScore: Int = 20,

    // C# workbench state
    val csharpCodeDraft: String = "",
    val csharpStdin: String = "",
    val csharpRunResult: CSharpRunResult? = null,
    val csharpTraceIndex: Int = 0,
    val isAutoPlayingTrace: Boolean = false,
    val csharpTinkerMode: Boolean = false,
    val csharpTinkerMutationIndex: Int = 0,
    val csharpFeedback: String? = null,
    val csharpFeedbackPassed: Boolean? = null,
    val csharpBaselineOutput: String? = null,

    // Angular workbench state
    val angularFiles: Map<String, String> = emptyMap(),
    val angularActiveFilePath: String = "src/app/app.ts",
    val angularPreviewHtml: String = "",
    val angularGentleAlert: GentleTreeAlert? = null,
    val angularTinkerMode: Boolean = false,
    val angularTinkerMutationIndex: Int = 0,
    val angularFeedback: String? = null,
    val angularFeedbackPassed: Boolean? = null,

    // Modals
    val showProfilesDialog: Boolean = false
) {
    val isCurrentTrackTinkerMode: Boolean
        get() = when (activeTrack) {
            TrackType.CSHARP -> csharpTinkerMode
            TrackType.ANGULAR -> angularTinkerMode
            else -> webTinkerMode
        }
}

class StepLearnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CurriculumRepository(application)
    private val database = LearnerDatabase(application)
    private val prefsRepo = PreferencesRepository(application)
    private val sqlEngine = SqlPlaygroundEngine()
    private val soundEngine = AmbientSoundEngine()

    private val _uiState = MutableStateFlow(StepLearnUiState())
    val uiState: StateFlow<StepLearnUiState> = _uiState.asStateFlow()

    private var traceAutoPlayJob: Job? = null
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(uiPreferences = prefs) }
                soundEngine.setVolume(prefs.soundVolume)
                if (prefs.soundEnabled) {
                    soundEngine.start(prefs.soundType, prefs.soundVolume)
                } else {
                    soundEngine.stop()
                }
            }
        }
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val loadedTracks = withContext(Dispatchers.IO) {
                repository.preloadAll()
                TrackType.entries.associateWith { repository.getTrack(it) }
            }
            val profiles = withContext(Dispatchers.IO) { database.listProfiles() }
            val activeProfile = withContext(Dispatchers.IO) { database.getActiveProfile() }
            val rawLoadedState = withContext(Dispatchers.IO) { database.loadState(activeProfile.id) }
            val loadedState = if (rawLoadedState.growth.livingProjects.isEmpty()) {
                val seeded = rawLoadedState.copy(
                    growth = rawLoadedState.growth.copy(
                        livingProjects = listOf(LivingProject(updatedAt = nowLabel()))
                    )
                )
                withContext(Dispatchers.IO) { database.saveState(activeProfile.id, seeded) }
                seeded
            } else {
                rawLoadedState
            }

            val initialTrack = TrackType.fromKey(loadedState.activeCourse)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    tracks = loadedTracks,
                    profiles = profiles,
                    activeProfile = activeProfile,
                    learnerState = loadedState,
                    activeTrack = initialTrack
                )
            }
            syncWebWorkbenchForLesson(initialTrack, lessonIndexFor(loadedState, initialTrack))
            syncCSharpWorkbench(loadedState.csharp.currentLessonIndex)
            syncAngularWorkbench(loadedState.angular.currentLessonIndex)
            syncWidgetSnapshot()
        }
    }

    // ---- Top-Level Navigation & Lesson Overlay Flow ----

    fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                lessonStage = LessonStage.NONE,
                activeLivingProjectId = null,
                isResumingLesson = false,
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                showStepPlanSheet = false,
                showDeepTheorySheet = false
            )
        }
    }

    fun openCurrentExperiment() {
        val track = _uiState.value.activeTrack
        val idx = lessonIndexFor(_uiState.value.learnerState, track)
        openLessonFlow(track, idx, startInTinker = false)
    }

    fun launchExperimentFromWidget(trackKey: String?, lessonIndex: Int?) {
        val state = _uiState.value
        if (state.isLoading || state.tracks.isEmpty()) return
        val targetTrack = if (!trackKey.isNullOrBlank()) TrackType.fromKey(trackKey) else state.activeTrack
        val targetIndex = lessonIndex ?: lessonIndexFor(state.learnerState, targetTrack)
        openLessonFlow(targetTrack, targetIndex, startInTinker = false)
    }

    fun openLesson(track: TrackType, index: Int) {
        openLessonFlow(track, index, startInTinker = false)
    }

    fun hasPriorProgressForLesson(state: LearnerState, track: TrackType, index: Int): Boolean {
        val lessons = _uiState.value.tracks[track]?.lessons.orEmpty()
        val clamped = index.coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
        val lesson = lessons.getOrNull(clamped) ?: return false
        return when (track) {
            TrackType.CSHARP -> {
                state.csharp.drafts.containsKey(lesson.id) ||
                    (state.csharp.stepIndexMap[lesson.id] ?: 0) > 0 ||
                    lesson.id in state.csharp.completed
            }
            TrackType.ANGULAR -> {
                state.angular.playgroundFiles.containsKey(lesson.id) ||
                    (state.angular.stepIndexMap[lesson.id] ?: 0) > 0 ||
                    lesson.id in state.angular.completed
            }
            else -> {
                val key = repository.lessonKey(track, clamped, lesson)
                val flow = state.growth.lessonFlow[key]
                state.growth.drafts.containsKey(key) ||
                    (flow?.current ?: 0) > 0 ||
                    (flow?.unlocked ?: 0) > 0 ||
                    key in state.growth.completed
            }
        }
    }

    fun openLessonFlow(
        track: TrackType,
        index: Int,
        startInTinker: Boolean = false,
        ideaId: String? = null,
        customStarterCode: String? = null,
        forceIntro: Boolean = false,
        livingProjectId: String? = null
    ) {
        val trackObj = _uiState.value.tracks[track] ?: return
        val clamped = index.coerceIn(0, (trackObj.lessons.size - 1).coerceAtLeast(0))
        val priorProgress = hasPriorProgressForLesson(_uiState.value.learnerState, track, clamped)
        val shouldSkipIntro = startInTinker || (priorProgress && !forceIntro)

        updateLearnerState { st ->
            when (track) {
                TrackType.HTML -> st.copy(activeCourse = track.key, htmlLesson = clamped)
                TrackType.CSS -> st.copy(activeCourse = track.key, cssLesson = clamped)
                TrackType.JS -> st.copy(activeCourse = track.key, jsLesson = clamped)
                TrackType.DATABASE -> st.copy(activeCourse = track.key, databaseLesson = clamped)
                TrackType.CSHARP -> st.copy(
                    activeCourse = track.key,
                    csharp = st.csharp.copy(currentLessonIndex = clamped)
                )
                TrackType.ANGULAR -> st.copy(
                    activeCourse = track.key,
                    angular = st.angular.copy(currentLessonIndex = clamped)
                )
            }
        }

        when (track) {
            TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE ->
                syncWebWorkbenchForLesson(track, clamped)
            TrackType.CSHARP -> syncCSharpWorkbench(clamped)
            TrackType.ANGULAR -> syncAngularWorkbench(clamped)
        }

        _uiState.update {
            it.copy(
                activeTrack = track,
                lessonStage = if (shouldSkipIntro) LessonStage.STEP else LessonStage.INTRO,
                activeIdeaId = ideaId,
                activeLivingProjectId = livingProjectId,
                isResumingLesson = !startInTinker && priorProgress && !forceIntro,
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                introPredictSelected = null,
                introPredictRevealed = false,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null,
                showStepPlanSheet = false,
                showDeepTheorySheet = false
            )
        }

        if (startInTinker) {
            enterTinkerWithOptionalCode(track, customStarterCode)
        }
        syncWidgetSnapshot()
    }

    private fun enterTinkerWithOptionalCode(track: TrackType, customCode: String?) {
        val st = _uiState.value
        when (track) {
            TrackType.CSHARP -> {
                val backup = st.guidedCSharpDraftBackup ?: st.csharpCodeDraft
                val code = customCode?.takeIf { it.isNotBlank() } ?: st.csharpCodeDraft
                val runRes = CSharpRuntime.runProgram(code, emptyMap(), st.csharpStdin)
                _uiState.update {
                    it.copy(
                        guidedCSharpDraftBackup = backup,
                        csharpTinkerMode = true,
                        csharpCodeDraft = code,
                        csharpRunResult = runRes,
                        csharpTraceIndex = (runRes.trace.size - 1).coerceAtLeast(0),
                        csharpFeedback = null
                    )
                }
            }
            TrackType.ANGULAR -> {
                val backup = st.guidedAngularFilesBackup ?: st.angularFiles
                val path = st.angularActiveFilePath
                val files = if (!customCode.isNullOrBlank()) st.angularFiles + (path to customCode) else st.angularFiles
                val lesson = currentAngularLesson()
                _uiState.update {
                    it.copy(
                        guidedAngularFilesBackup = backup,
                        angularTinkerMode = true,
                        angularFiles = files,
                        angularPreviewHtml = AngularSimulator.renderPreviewDoc(lesson, path, files),
                        angularFeedback = null
                    )
                }
            }
            else -> {
                val backup = st.guidedWebDraftBackup ?: st.webCodeDraft
                val code = customCode?.takeIf { it.isNotBlank() } ?: st.webCodeDraft
                _uiState.update {
                    it.copy(
                        guidedWebDraftBackup = backup,
                        webTinkerMode = true,
                        webCodeDraft = code,
                        webPreviewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, code) else "",
                        sqlResult = if (track == TrackType.DATABASE) sqlEngine.execute(code) else null,
                        webHintResult = null
                    )
                }
            }
        }
    }

    fun exitLessonFlow() {
        // Safely exit tinker mode before closing so guided draft remains clean
        if (_uiState.value.isCurrentTrackTinkerMode) {
            toggleActiveTinkerMode()
        }
        traceAutoPlayJob?.cancel()
        _uiState.update {
            it.copy(
                lessonStage = LessonStage.NONE,
                activeIdeaId = null,
                activeLivingProjectId = null,
                isResumingLesson = false,
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null,
                showStepPlanSheet = false,
                showDeepTheorySheet = false
            )
        }
    }

    fun revisitIntroFromStep() {
        if (_uiState.value.isCurrentTrackTinkerMode) toggleActiveTinkerMode()
        _uiState.update {
            it.copy(
                lessonStage = LessonStage.INTRO,
                isResumingLesson = false,
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null
            )
        }
    }

    fun handleLessonBack() {
        when (_uiState.value.lessonStage) {
            LessonStage.COMPLETION -> exitLessonFlow()
            LessonStage.STEP -> {
                if (_uiState.value.activeIdeaId != null || _uiState.value.activeLivingProjectId != null || _uiState.value.isResumingLesson) {
                    exitLessonFlow()
                } else {
                    if (_uiState.value.isCurrentTrackTinkerMode) toggleActiveTinkerMode()
                    _uiState.update {
                        it.copy(
                            lessonStage = LessonStage.INTRO,
                            stepPhase = StepExecutionPhase.BEFORE_RUN,
                            hintTierRevealed = 0,
                            stepCompletedForCurrentStep = false,
                            lastRunInterpretation = null
                        )
                    }
                }
            }
            LessonStage.INTRO -> exitLessonFlow()
            LessonStage.NONE -> Unit
        }
    }

    fun selectIntroPrediction(optionIndex: Int) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                introPredictSelected = optionIndex,
                introPredictRevealed = true
            )
        }
        if (state.activeTrack in listOf(TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE)) {
            val key = currentWebLessonKey()
            updateLearnerState { st ->
                st.copy(
                    growth = st.growth.copy(
                        predictions = st.growth.predictions + (key to PredictionRecord(optionIndex, true))
                    )
                )
            }
        }
    }

    fun skipIntroPrediction() {
        _uiState.update { it.copy(introPredictRevealed = true) }
    }

    fun startLessonFromIntro(inTinkerMode: Boolean = false) {
        _uiState.update {
            it.copy(
                lessonStage = LessonStage.STEP,
                isResumingLesson = false,
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null
            )
        }
        if (inTinkerMode) {
            enterTinkerWithOptionalCode(_uiState.value.activeTrack, null)
        }
    }

    fun returnToCodeEditPhase() {
        _uiState.update { it.copy(stepPhase = StepExecutionPhase.BEFORE_RUN) }
    }

    fun revealNextHintTier() {
        _uiState.update {
            it.copy(hintTierRevealed = (it.hintTierRevealed + 1).coerceAtMost(4))
        }
    }

    fun setShowStepPlanSheet(show: Boolean) {
        _uiState.update { it.copy(showStepPlanSheet = show) }
    }

    fun setShowDeepTheorySheet(show: Boolean) {
        _uiState.update { it.copy(showDeepTheorySheet = show) }
    }

    fun toggleActiveTinkerMode() {
        when (_uiState.value.activeTrack) {
            TrackType.CSHARP -> toggleCSharpTinker()
            TrackType.ANGULAR -> toggleAngularTinker()
            else -> toggleWebTinkerMode()
        }
    }

    fun mutateActiveTinkerSpark() {
        when (_uiState.value.activeTrack) {
            TrackType.CSHARP -> mutateCSharpTinker()
            TrackType.ANGULAR -> mutateAngularTinker()
            else -> mutateWebTinkerSpark()
        }
    }

    fun openActiveCompletionInTinker() {
        val track = _uiState.value.activeTrack
        _uiState.update {
            it.copy(
                lessonStage = LessonStage.STEP,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null
            )
        }
        enterTinkerWithOptionalCode(track, null)
    }

    fun openNextExperimentFromCompletion() {
        val state = _uiState.value
        val track = state.activeTrack
        val lessons = state.tracks[track]?.lessons.orEmpty()
        val currentIdx = lessonIndexFor(state.learnerState, track)
        if (currentIdx + 1 < lessons.size) {
            openLessonFlow(track, currentIdx + 1, startInTinker = false)
        } else {
            val tracksList = TrackType.entries
            val nextTrack = tracksList[(tracksList.indexOf(track) + 1) % tracksList.size]
            val nextIdx = lessonIndexFor(state.learnerState, nextTrack)
            openLessonFlow(nextTrack, nextIdx, startInTinker = false)
        }
    }

    // ---- Unified Step Actions Across All 6 Tracks ----

    fun currentActiveLesson(): RawLesson? = when (_uiState.value.activeTrack) {
        TrackType.CSHARP -> currentCSharpLesson()
        TrackType.ANGULAR -> currentAngularLesson()
        else -> currentWebLesson()
    }

    fun currentActiveSteps(): List<RawLessonStep> = when (_uiState.value.activeTrack) {
        TrackType.CSHARP -> currentCSharpLesson()?.steps.orEmpty()
        TrackType.ANGULAR -> currentAngularLesson()?.steps.orEmpty()
        else -> _uiState.value.webSteps
    }

    fun currentActiveStepIndex(): Int {
        val state = _uiState.value
        val steps = currentActiveSteps()
        val rawIdx = when (state.activeTrack) {
            TrackType.CSHARP -> currentCSharpLesson()?.let { state.learnerState.csharp.stepIndexMap[it.id] } ?: 0
            TrackType.ANGULAR -> currentAngularLesson()?.let { state.learnerState.angular.stepIndexMap[it.id] } ?: 0
            else -> {
                val key = currentWebLessonKey()
                state.learnerState.growth.lessonFlow[key]?.current ?: 0
            }
        }
        return rawIdx.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    }

    fun currentActiveStep(): RawLessonStep? =
        currentActiveSteps().getOrNull(currentActiveStepIndex())

    fun currentActiveCode(): String = when (_uiState.value.activeTrack) {
        TrackType.CSHARP -> _uiState.value.csharpCodeDraft
        TrackType.ANGULAR -> _uiState.value.angularFiles[_uiState.value.angularActiveFilePath].orEmpty()
        else -> _uiState.value.webCodeDraft
    }

    fun updateActiveCode(newCode: String) {
        when (_uiState.value.activeTrack) {
            TrackType.CSHARP -> updateCSharpCode(newCode)
            TrackType.ANGULAR -> updateAngularActiveCode(newCode)
            else -> updateWebCode(newCode)
        }
    }

    fun resetActiveCode() {
        when (_uiState.value.activeTrack) {
            TrackType.CSHARP -> resetCSharpCode()
            TrackType.ANGULAR -> resetAngularCode()
            else -> resetWebLessonCode()
        }
        _uiState.update {
            it.copy(
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null
            )
        }
    }

    fun selectActiveStep(stepIdx: Int) {
        when (_uiState.value.activeTrack) {
            TrackType.CSHARP -> selectCSharpStep(stepIdx)
            TrackType.ANGULAR -> selectAngularStep(stepIdx)
            else -> selectWebStep(stepIdx)
        }
        _uiState.update {
            it.copy(
                stepPhase = StepExecutionPhase.BEFORE_RUN,
                hintTierRevealed = 0,
                stepCompletedForCurrentStep = false,
                lastRunInterpretation = null,
                showStepPlanSheet = false
            )
        }
    }

    fun selectActiveQuizOption(optIdx: Int) {
        val state = _uiState.value
        val step = currentActiveStep() ?: return
        val lesson = currentActiveLesson() ?: return
        when (state.activeTrack) {
            TrackType.CSHARP -> selectCSharpQuiz(optIdx)
            TrackType.ANGULAR -> selectAngularQuiz(optIdx)
            else -> selectWebQuizOption(optIdx)
        }
        val correctIdx = step.correct ?: step.correctOptionIndex ?: 0
        if (optIdx == correctIdx) {
            _uiState.update {
                it.copy(
                    stepPhase = StepExecutionPhase.AFTER_RUN,
                    stepCompletedForCurrentStep = true,
                    lastRunInterpretation = CuriosityFraming.formatStepInterpretation(state.activeTrack, step, lesson),
                    webHintResult = null,
                    csharpFeedback = null,
                    angularFeedback = null
                )
            }
        } else {
            val msg = "جرّب خيارًا آخر؛ تأمل السطر الموضح أعلاه ثم اختر من جديد."
            _uiState.update {
                it.copy(
                    stepPhase = StepExecutionPhase.AFTER_RUN,
                    stepCompletedForCurrentStep = false,
                    lastRunInterpretation = null,
                    webHintResult = HintEvaluationResult(false, false, msg, msg, 1, false, false),
                    csharpFeedback = msg,
                    csharpFeedbackPassed = false,
                    angularFeedback = msg,
                    angularFeedbackPassed = false
                )
            }
        }
    }

    fun runActiveCode() {
        val state = _uiState.value
        val track = state.activeTrack
        val lesson = currentActiveLesson() ?: return
        val step = currentActiveStep()

        when (track) {
            TrackType.CSHARP -> {
                runCSharpCode(autoPlayTrace = true)
                val latest = _uiState.value
                if (latest.csharpTinkerMode) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            lastRunInterpretation = "تم تشغيل تجربتك الحرة وتحديث فيلم التنفيذ وذاكرة المتغيرات."
                        )
                    }
                    state.activeIdeaId?.let { markIdeaTried(it) }
                    return
                }
                val err = latest.csharpRunResult?.error
                if (err != null) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = false,
                            csharpFeedback = err.message,
                            csharpFeedbackPassed = false
                        )
                    }
                    return
                }
                if (step == null || step.type in listOf("observe", "detail")) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = true,
                            lastRunInterpretation = CuriosityFraming.formatStepInterpretation(track, step, lesson),
                            csharpFeedback = null,
                            csharpFeedbackPassed = true
                        )
                    }
                    return
                }
                val res = CSharpRuntime.checkChallenge(
                    source = latest.csharpCodeDraft,
                    challenge = step.challenge,
                    baselineOutput = latest.csharpBaselineOutput,
                    stdin = latest.csharpStdin
                )
                if (res.passed) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = true,
                            lastRunInterpretation = CuriosityFraming.formatStepInterpretation(track, step, lesson),
                            csharpFeedback = null,
                            csharpFeedbackPassed = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = false,
                            csharpFeedback = res.message,
                            csharpFeedbackPassed = false
                        )
                    }
                }
            }
            TrackType.ANGULAR -> {
                runAngularCode()
                val latest = _uiState.value
                if (latest.angularTinkerMode) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            lastRunInterpretation = "تم تحديث معاينة تطبيق السينما بالتعديل الحر."
                        )
                    }
                    state.activeIdeaId?.let { markIdeaTried(it) }
                    return
                }
                val ngState = latest.learnerState.angular
                val stepIdx = currentActiveStepIndex()
                val stepOk = AngularSimulator.isStepCompleted(step, lesson, latest.angularFiles, ngState.quizSelectedMap[lesson.id])
                val taskOk = if (stepIdx >= lesson.steps.lastIndex) AngularSimulator.supportedTask(lesson, latest.angularFiles) else true
                if (stepOk && taskOk) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = true,
                            lastRunInterpretation = CuriosityFraming.formatStepInterpretation(track, step, lesson),
                            angularFeedback = null,
                            angularFeedbackPassed = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = false,
                            angularFeedback = "شُغّلت المعاينة؛ أكمل التعديل المطلوب في الخطوة أو اطلب تلميحًا عند الحاجة.",
                            angularFeedbackPassed = false
                        )
                    }
                }
            }
            else -> {
                // HTML, CSS, JS, DATABASE
                val code = state.webCodeDraft
                val previewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, code) else ""
                val sqlRes = if (track == TrackType.DATABASE) sqlEngine.execute(code) else null
                val gentle = SmartHintEngine.inspectGentleDomTree(code, track)
                _uiState.update {
                    it.copy(
                        webPreviewHtml = previewHtml,
                        sqlResult = sqlRes,
                        webGentleAlert = gentle
                    )
                }
                updateLearnerState { st -> st.copy(examples = st.examples + 1) }

                if (state.webTinkerMode) {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            lastRunInterpretation = "شُغّلت تجربتك الحرة مباشرة في المعاينة دون قيود."
                        )
                    }
                    state.activeIdeaId?.let { markIdeaTried(it) }
                    return
                }

                if (step == null || step.type == "observe" || step.type == "project") {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = true,
                            lastRunInterpretation = CuriosityFraming.formatStepInterpretation(track, step, lesson),
                            webHintResult = null
                        )
                    }
                    return
                }

                val key = currentWebLessonKey()
                val attempts = (state.learnerState.growth.stepAttempts["$key:${step.id}"] ?: 0) + 1
                val practice = SmartHintEngine.buildPracticeStepsForLesson(track, lesson)
                val eval = SmartHintEngine.evaluate(
                    userCode = code,
                    step = step,
                    track = track,
                    lesson = lesson,
                    practiceSteps = practice,
                    experimentBaseCode = state.learnerState.growth.experimentCodes[key],
                    attemptCount = attempts
                )
                if (eval.passed) {
                    if (step.type == "repair") {
                        updateLearnerState { st -> st.copy(fixes = st.fixes + 1) }
                    }
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = true,
                            lastRunInterpretation = CuriosityFraming.formatStepInterpretation(track, step, lesson),
                            webHintResult = null
                        )
                    }
                } else {
                    updateLearnerState { st ->
                        st.copy(
                            growth = st.growth.copy(
                                stepAttempts = st.growth.stepAttempts + ("$key:${step.id}" to attempts)
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.AFTER_RUN,
                            stepCompletedForCurrentStep = false,
                            webHintResult = eval
                        )
                    }
                }
            }
        }
    }

    fun advanceOrCompleteStep() {
        val state = _uiState.value
        val track = state.activeTrack
        val lesson = currentActiveLesson() ?: return
        val steps = currentActiveSteps()
        val currentIdx = currentActiveStepIndex()
        val isFinalStep = steps.isEmpty() || currentIdx >= steps.lastIndex

        when (track) {
            TrackType.CSHARP -> {
                val csState = state.learnerState.csharp
                val step = steps.getOrNull(currentIdx)
                val nextIdx = (currentIdx + 1).coerceAtMost((steps.size - 1).coerceAtLeast(0))
                val nextStep = steps.getOrNull(nextIdx)
                var nextDraft = state.csharpCodeDraft
                if (!isFinalStep && nextStep != null) {
                    if (nextStep.starterCode != null) nextDraft = nextStep.starterCode
                    else if (nextStep.code != null && nextDraft.isBlank()) nextDraft = nextStep.code
                }
                updateLearnerState { st ->
                    val newCompleted = if (isFinalStep && lesson.id !in st.csharp.completed) {
                        st.csharp.completed + lesson.id
                    } else st.csharp.completed
                    val totalLessonsCount = _uiState.value.tracks.values.sumOf { it.lessons.size }.coerceAtLeast(1)
                    val allDoneCount = st.growth.completed.size + newCompleted.size + st.angular.completed.size
                    val newProgress = ((allDoneCount * 100) / totalLessonsCount).coerceIn(0, 100)
                    val curMax = st.csharp.maxUnlockedStepMap[lesson.id] ?: 0
                    st.copy(
                        lessons = allDoneCount,
                        progress = maxOf(st.progress, newProgress),
                        csharp = st.csharp.copy(
                            completed = newCompleted,
                            stepIndexMap = st.csharp.stepIndexMap + (lesson.id to if (isFinalStep) currentIdx else nextIdx),
                            maxUnlockedStepMap = st.csharp.maxUnlockedStepMap + (lesson.id to maxOf(curMax, nextIdx)),
                            drafts = st.csharp.drafts + (lesson.id to nextDraft),
                            handCraftScore = (st.csharp.handCraftScore + 4).coerceAtMost(100)
                        )
                    )
                }
                if (isFinalStep) {
                    _uiState.update {
                        it.copy(
                            lessonStage = LessonStage.COMPLETION,
                            stepPhase = StepExecutionPhase.BEFORE_RUN
                        )
                    }
                } else {
                    val nextStdin = nextStep?.challenge?.stdin ?: state.csharpStdin
                    val runRes = CSharpRuntime.runProgram(nextDraft, emptyMap(), nextStdin)
                    _uiState.update {
                        it.copy(
                            csharpCodeDraft = nextDraft,
                            csharpStdin = nextStdin,
                            csharpRunResult = runRes,
                            csharpTraceIndex = (runRes.trace.size - 1).coerceAtLeast(0),
                            csharpBaselineOutput = if (nextStep?.type == "experiment") runRes.output else it.csharpBaselineOutput,
                            csharpFeedback = null,
                            csharpFeedbackPassed = null,
                            stepPhase = StepExecutionPhase.BEFORE_RUN,
                            hintTierRevealed = 0,
                            stepCompletedForCurrentStep = false,
                            lastRunInterpretation = null
                        )
                    }
                }
            }
            TrackType.ANGULAR -> {
                val nextIdx = (currentIdx + 1).coerceAtMost((steps.size - 1).coerceAtLeast(0))
                val nextStep = steps.getOrNull(nextIdx)
                updateLearnerState { st ->
                    val newCompleted = if (isFinalStep && lesson.id !in st.angular.completed) {
                        st.angular.completed + lesson.id
                    } else st.angular.completed
                    val totalLessonsCount = _uiState.value.tracks.values.sumOf { it.lessons.size }.coerceAtLeast(1)
                    val allDoneCount = st.growth.completed.size + st.csharp.completed.size + newCompleted.size
                    val newProgress = ((allDoneCount * 100) / totalLessonsCount).coerceIn(0, 100)
                    st.copy(
                        lessons = allDoneCount,
                        progress = maxOf(st.progress, newProgress),
                        angular = st.angular.copy(
                            completed = newCompleted,
                            stepIndexMap = st.angular.stepIndexMap + (lesson.id to if (isFinalStep) currentIdx else nextIdx),
                            handCraftScore = (st.angular.handCraftScore + 4).coerceAtMost(100)
                        )
                    )
                }
                if (isFinalStep) {
                    _uiState.update {
                        it.copy(
                            lessonStage = LessonStage.COMPLETION,
                            stepPhase = StepExecutionPhase.BEFORE_RUN
                        )
                    }
                } else {
                    val nextFile = nextStep?.file?.takeIf { it.isNotBlank() && state.angularFiles.containsKey(it) }
                    if (nextFile != null) selectAngularFile(nextFile)
                    _uiState.update {
                        it.copy(
                            angularFeedback = null,
                            angularFeedbackPassed = null,
                            stepPhase = StepExecutionPhase.BEFORE_RUN,
                            hintTierRevealed = 0,
                            stepCompletedForCurrentStep = false,
                            lastRunInterpretation = null
                        )
                    }
                }
            }
            else -> {
                val key = currentWebLessonKey()
                val flow = state.learnerState.growth.lessonFlow[key] ?: LessonFlowState(0, 0)
                val step = steps.getOrNull(currentIdx) ?: return
                if (step.type == "project") {
                    saveLessonPartToProject(key, track, lesson, state.webCodeDraft)
                }
                advanceWebStep(key, flow, steps, state.webCodeDraft, step, lesson)
                if (isFinalStep) {
                    saveLessonPartToProject(key, track, lesson, state.webCodeDraft)
                    _uiState.update {
                        it.copy(
                            lessonStage = LessonStage.COMPLETION,
                            stepPhase = StepExecutionPhase.BEFORE_RUN,
                            hintTierRevealed = 0,
                            stepCompletedForCurrentStep = false,
                            lastRunInterpretation = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            stepPhase = StepExecutionPhase.BEFORE_RUN,
                            hintTierRevealed = 0,
                            stepCompletedForCurrentStep = false,
                            lastRunInterpretation = null,
                            webHintResult = null
                        )
                    }
                }
            }
        }
    }

    fun stepHintText(step: RawLessonStep?, lesson: RawLesson?, tier: Int): String {
        if (step == null || tier <= 0) return ""
        val track = _uiState.value.activeTrack
        if (track in listOf(TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE) && lesson != null) {
            val fromJson = step.hintTextAt(tier)
            if (fromJson.isNotBlank()) return fromJson
            val practice = SmartHintEngine.buildPracticeStepsForLesson(track, lesson)
            val eval = SmartHintEngine.evaluate(
                userCode = _uiState.value.webCodeDraft,
                step = step,
                track = track,
                lesson = lesson,
                practiceSteps = practice,
                experimentBaseCode = null,
                attemptCount = tier
            )
            if (eval.hintText.isNotBlank()) return eval.hintText
        }
        val fromJson = step.hintTextAt(tier)
        if (fromJson.isNotBlank()) return fromJson
        return when (tier) {
            1 -> "تلميح: ركّز على السطر المميّز بالأخضر في المحرر؛ هذا هو موضع التعديل."
            2 -> "قاعدة: ${step.explanation.ifBlank { step.objective.ifBlank { lesson?.concept.orEmpty() } }}"
            3 -> "مثال: الهدف المطلوب يتضمن «${step.to.ifBlank { step.expectedLine.ifBlank { step.task } }}»."
            else -> "الحل المقترح:\n${step.solution?.takeIf { it.isNotBlank() } ?: step.to.ifBlank { lesson?.code?.lines()?.take(4)?.joinToString("\n").orEmpty() }}"
        }
    }

    // ---- Existing Public Actions Retained & Upgraded ----

    fun selectTrack(track: TrackType, openWorkbench: Boolean = true) {
        updateLearnerState { it.copy(activeCourse = track.key) }
        _uiState.update { it.copy(activeTrack = track) }
        val idx = lessonIndexFor(_uiState.value.learnerState, track)
        when (track) {
            TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE ->
                syncWebWorkbenchForLesson(track, idx)
            TrackType.CSHARP -> syncCSharpWorkbench(idx)
            TrackType.ANGULAR -> syncAngularWorkbench(idx)
        }
        if (openWorkbench) {
            openLessonFlow(track, idx, startInTinker = false)
        }
    }

    fun refreshGreeting() {
        val current = _uiState.value.greetingText
        val next = CurriculumRepository.greetings.filter { it != current }.randomOrNull()
            ?: CurriculumRepository.greetings.first()
        _uiState.update { it.copy(greetingText = next) }
    }

    fun setShowProfilesDialog(show: Boolean) {
        _uiState.update { it.copy(showProfilesDialog = show, profileError = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(3200)
            _uiState.update { if (it.toastMessage == message) it.copy(toastMessage = null) else it }
        }
    }

    fun setSideBySidePreference(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSideBySide(enabled) }
    }

    // ---- Profiles ----

    fun createProfile(name: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { database.createProfile(name) }
            result.onSuccess { profile ->
                val profiles = withContext(Dispatchers.IO) { database.listProfiles() }
                val newState = withContext(Dispatchers.IO) { database.loadState(profile.id) }
                val track = TrackType.fromKey(newState.activeCourse)
                _uiState.update {
                    it.copy(
                        profiles = profiles,
                        activeProfile = profile,
                        learnerState = newState,
                        activeTrack = track,
                        profileError = null,
                        showProfilesDialog = false
                    )
                }
                syncWebWorkbenchForLesson(track, lessonIndexFor(newState, track))
                syncCSharpWorkbench(newState.csharp.currentLessonIndex)
                syncAngularWorkbench(newState.angular.currentLessonIndex)
                showToast("تم فتح ملف «${profile.name}»")
            }.onFailure { err ->
                _uiState.update { it.copy(profileError = err.message) }
            }
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { database.activateProfile(profileId) }
            if (!ok) return@launch
            val active = withContext(Dispatchers.IO) { database.getActiveProfile() }
            val loaded = withContext(Dispatchers.IO) { database.loadState(active.id) }
            val track = TrackType.fromKey(loaded.activeCourse)
            _uiState.update {
                it.copy(
                    activeProfile = active,
                    learnerState = loaded,
                    activeTrack = track,
                    profileError = null,
                    showProfilesDialog = false
                )
            }
            syncWebWorkbenchForLesson(track, lessonIndexFor(loaded, track))
            syncCSharpWorkbench(loaded.csharp.currentLessonIndex)
            syncAngularWorkbench(loaded.angular.currentLessonIndex)
            showToast("مرحبًا بعودتك، ${active.name}")
        }
    }

    // ---- Web & SQL Workbench ----

    fun currentWebLesson(): RawLesson? {
        val state = _uiState.value
        val track = state.activeTrack
        if (track !in listOf(TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE)) return null
        val lessons = state.tracks[track]?.lessons.orEmpty()
        val idx = lessonIndexFor(state.learnerState, track).coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
        return lessons.getOrNull(idx)
    }

    fun currentWebLessonKey(): String {
        val state = _uiState.value
        val idx = lessonIndexFor(state.learnerState, state.activeTrack)
        return repository.lessonKey(state.activeTrack, idx, currentWebLesson())
    }

    fun lessonIndexFor(learner: LearnerState, track: TrackType): Int = when (track) {
        TrackType.HTML -> learner.htmlLesson
        TrackType.CSS -> learner.cssLesson
        TrackType.JS -> learner.jsLesson
        TrackType.DATABASE -> learner.databaseLesson
        TrackType.CSHARP -> learner.csharp.currentLessonIndex
        TrackType.ANGULAR -> learner.angular.currentLessonIndex
    }

    private fun syncWebWorkbenchForLesson(track: TrackType, index: Int) {
        if (track !in listOf(TrackType.HTML, TrackType.CSS, TrackType.JS, TrackType.DATABASE)) return
        val lessons = _uiState.value.tracks[track]?.lessons.orEmpty()
        val lesson = lessons.getOrNull(index) ?: return
        val steps = SmartHintEngine.buildAllSteps(track, lesson)
        val key = repository.lessonKey(track, index, lesson)
        val state = _uiState.value.learnerState
        val flow = state.growth.lessonFlow[key] ?: LessonFlowState(0, 0)
        val activeStep = steps.getOrNull(flow.current.coerceIn(0, (steps.size - 1).coerceAtLeast(0)))

        val draft = state.growth.drafts[key]
            ?: activeStep?.starterCode
            ?: lesson.code

        val previewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, draft) else ""
        val sqlRes = if (track == TrackType.DATABASE) sqlEngine.execute(draft) else null

        _uiState.update {
            it.copy(
                webSteps = steps,
                webCodeDraft = draft,
                guidedWebDraftBackup = null,
                webPreviewHtml = previewHtml,
                sqlResult = sqlRes,
                webHintResult = null,
                webGentleAlert = SmartHintEngine.inspectGentleDomTree(draft, track),
                webTinkerMode = false,
                webQuizSelected = null
            )
        }
    }

    fun updateWebCode(newCode: String) {
        val state = _uiState.value
        val track = state.activeTrack
        val key = currentWebLessonKey()
        val prevCode = state.webCodeDraft
        var newScore = state.webHandCraftScore
        if (newCode != prevCode && !state.webTinkerMode) {
            newScore = (newScore + 1).coerceAtMost(100)
        }
        val gentle = SmartHintEngine.inspectGentleDomTree(newCode, track)
        _uiState.update {
            it.copy(
                webCodeDraft = newCode,
                webGentleAlert = gentle,
                webHandCraftScore = newScore,
                webHintResult = null
            )
        }
        if (!state.webTinkerMode) {
            updateLearnerState { st ->
                st.copy(growth = st.growth.copy(drafts = st.growth.drafts + (key to newCode)))
            }
        }
    }

    fun selectWebStep(stepIndex: Int) {
        val state = _uiState.value
        val steps = state.webSteps
        if (stepIndex !in steps.indices) return
        val key = currentWebLessonKey()
        val flow = state.learnerState.growth.lessonFlow[key] ?: LessonFlowState(0, 0)
        if (stepIndex > flow.unlocked && !state.webTinkerMode) return

        val step = steps[stepIndex]
        val lesson = currentWebLesson()
        var nextCode = state.webCodeDraft
        if (step.type == "repair" && !step.starterCode.isNullOrBlank()) {
            nextCode = step.starterCode
        } else if (step.type == "edit" && !step.starterCode.isNullOrBlank() && state.learnerState.growth.drafts[key].isNullOrBlank()) {
            nextCode = step.starterCode
        } else if (step.type == "observe" && lesson != null && nextCode.isBlank()) {
            nextCode = lesson.code
        }

        updateLearnerState { st ->
            st.copy(
                growth = st.growth.copy(
                    lessonFlow = st.growth.lessonFlow + (key to flow.copy(current = stepIndex)),
                    drafts = st.growth.drafts + (key to nextCode)
                )
            )
        }
        val previewHtml = if (state.activeTrack != TrackType.DATABASE) {
            PreviewHtmlBuilder.buildTrackPreview(state.activeTrack, nextCode)
        } else ""
        val sqlRes = if (state.activeTrack == TrackType.DATABASE) sqlEngine.execute(nextCode) else null
        _uiState.update {
            it.copy(
                webCodeDraft = nextCode,
                webPreviewHtml = previewHtml,
                sqlResult = sqlRes,
                webHintResult = null,
                webQuizSelected = null
            )
        }
    }

    private fun advanceWebStep(
        key: String,
        flow: LessonFlowState,
        steps: List<RawLessonStep>,
        code: String,
        currentStep: RawLessonStep,
        lesson: RawLesson
    ) {
        val nextIndex = (flow.current + 1).coerceAtMost(steps.lastIndex)
        val newUnlocked = maxOf(flow.unlocked, nextIndex)
        val isFinalStep = flow.current >= steps.lastIndex
        val newScore = (_uiState.value.webHandCraftScore + 3).coerceAtMost(100)

        updateLearnerState { st ->
            val updatedCompleted = if (isFinalStep && key !in st.growth.completed) {
                st.growth.completed + key
            } else st.growth.completed

            val updatedExperimentCodes = if (currentStep.type == "edit") {
                st.growth.experimentCodes + (key to code)
            } else st.growth.experimentCodes

            val newUnlocks = CurriculumRepository.abilityCatalog
                .filter { PreviewHtmlBuilder.isAbilityUnlocked(it, updatedCompleted) }
                .map { it.id }

            val totalLessonsCount = _uiState.value.tracks.values.sumOf { it.lessons.size }.coerceAtLeast(1)
            val allDoneCount = updatedCompleted.size + st.csharp.completed.size + st.angular.completed.size
            val newProgress = ((allDoneCount * 100) / totalLessonsCount).coerceIn(0, 100)

            st.copy(
                lessons = allDoneCount,
                progress = maxOf(st.progress, newProgress),
                growth = st.growth.copy(
                    completed = updatedCompleted,
                    unlocks = newUnlocks,
                    experimentCodes = updatedExperimentCodes,
                    lessonFlow = st.growth.lessonFlow + (key to LessonFlowState(
                        current = if (isFinalStep) flow.current else nextIndex,
                        unlocked = newUnlocked
                    ))
                )
            )
        }

        val nextStep = steps.getOrNull(nextIndex)
        var nextCode = code
        if (!isFinalStep && nextStep != null && nextStep.type == "repair" && !nextStep.starterCode.isNullOrBlank()) {
            nextCode = nextStep.starterCode
        }
        val track = _uiState.value.activeTrack
        val previewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, nextCode) else ""
        val sqlRes = if (track == TrackType.DATABASE) sqlEngine.execute(nextCode) else null

        _uiState.update {
            it.copy(
                webCodeDraft = nextCode,
                webPreviewHtml = previewHtml,
                sqlResult = sqlRes,
                webQuizSelected = null,
                webHandCraftScore = newScore
            )
        }
    }

    private fun saveLessonPartToProject(key: String, track: TrackType, lesson: RawLesson, code: String) {
        updateLearnerState { st ->
            val part = ProjectPart(
                id = key,
                label = "${track.shortTitle}: ${lesson.title}",
                course = track.key,
                objective = repository.lessonObjective(lesson),
                code = code,
                note = "لبنة مضافة من التجربة"
            )
            val filtered = st.growth.projectParts.filterNot { it.id == key } + part
            st.copy(
                project = (filtered.size * 12).coerceAtMost(100),
                growth = st.growth.copy(projectParts = filtered)
            )
        }
    }

    fun selectWebQuizOption(index: Int) {
        _uiState.update { it.copy(webQuizSelected = index, webHintResult = null) }
    }

    fun toggleWebDesktopPreview() {
        _uiState.update { it.copy(webDesktopPreview = !it.webDesktopPreview) }
    }

    fun toggleWebTinkerMode() {
        val state = _uiState.value
        val entering = !state.webTinkerMode
        val key = currentWebLessonKey()
        val lesson = currentWebLesson()
        val track = state.activeTrack
        if (entering) {
            _uiState.update {
                it.copy(
                    guidedWebDraftBackup = state.webCodeDraft,
                    webTinkerMode = true,
                    stepPhase = StepExecutionPhase.BEFORE_RUN,
                    webHintResult = null,
                    lastRunInterpretation = null
                )
            }
        } else {
            val restored = state.guidedWebDraftBackup
                ?: state.learnerState.growth.drafts[key]
                ?: lesson?.code
                ?: state.webCodeDraft
            _uiState.update {
                it.copy(
                    webTinkerMode = false,
                    guidedWebDraftBackup = null,
                    stepPhase = StepExecutionPhase.BEFORE_RUN,
                    webCodeDraft = restored,
                    webPreviewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, restored) else "",
                    sqlResult = if (track == TrackType.DATABASE) sqlEngine.execute(restored) else null,
                    webHintResult = null,
                    lastRunInterpretation = null
                )
            }
        }
    }

    fun mutateWebTinkerSpark() {
        val state = _uiState.value
        val nextIdx = state.webTinkerMutationIndex + 1
        val mutated = PreviewHtmlBuilder.mutateCodeSpark(state.webCodeDraft, state.activeTrack, nextIdx)
        val previewHtml = if (state.activeTrack != TrackType.DATABASE) {
            PreviewHtmlBuilder.buildTrackPreview(state.activeTrack, mutated)
        } else ""
        val sqlRes = if (state.activeTrack == TrackType.DATABASE) sqlEngine.execute(mutated) else null
        _uiState.update {
            it.copy(
                webTinkerMode = true,
                stepPhase = StepExecutionPhase.AFTER_RUN,
                webTinkerMutationIndex = nextIdx,
                webCodeDraft = mutated,
                webPreviewHtml = previewHtml,
                sqlResult = sqlRes,
                webGentleAlert = SmartHintEngine.inspectGentleDomTree(mutated, state.activeTrack),
                lastRunInterpretation = "تم تطبيق تعديل مفاجئ! شاهد أثره في المعاينة وعدّل عليه كما تشاء."
            )
        }
    }

    fun resetWebLessonCode() {
        val lesson = currentWebLesson() ?: return
        val key = currentWebLessonKey()
        val track = _uiState.value.activeTrack
        val flow = _uiState.value.learnerState.growth.lessonFlow[key] ?: LessonFlowState(0, 0)
        val step = _uiState.value.webSteps.getOrNull(flow.current)
        val base = step?.starterCode ?: lesson.code
        if (!_uiState.value.webTinkerMode) {
            updateLearnerState { st ->
                st.copy(growth = st.growth.copy(drafts = st.growth.drafts + (key to base)))
            }
        }
        _uiState.update {
            it.copy(
                webCodeDraft = base,
                webPreviewHtml = if (track != TrackType.DATABASE) PreviewHtmlBuilder.buildTrackPreview(track, base) else "",
                sqlResult = if (track == TrackType.DATABASE) sqlEngine.execute(base) else null,
                webHintResult = null,
                webGentleAlert = SmartHintEngine.inspectGentleDomTree(base, track)
            )
        }
    }

    fun applyWebSlot(customValue: String) {
        val state = _uiState.value
        val key = currentWebLessonKey()
        val flow = state.learnerState.growth.lessonFlow[key] ?: LessonFlowState(0, 0)
        val step = state.webSteps.getOrNull(flow.current) ?: return
        val model = SmartHintEngine.buildSlotModel(step, state.activeTrack, isDone = false) ?: return
        val applied = SmartHintEngine.applySlotToCode(state.webCodeDraft, model, step, state.activeTrack, customValue)
        updateWebCode(applied)
        runActiveCode()
    }

    // ---- C# Workbench ----

    fun currentCSharpLesson(): RawLesson? {
        val track = _uiState.value.tracks[TrackType.CSHARP] ?: return null
        val idx = _uiState.value.learnerState.csharp.currentLessonIndex
            .coerceIn(0, (track.lessons.size - 1).coerceAtLeast(0))
        return track.lessons.getOrNull(idx)
    }

    private fun syncCSharpWorkbench(index: Int) {
        traceAutoPlayJob?.cancel()
        val track = _uiState.value.tracks[TrackType.CSHARP] ?: return
        val lesson = track.lessons.getOrNull(index) ?: return
        val csState = _uiState.value.learnerState.csharp
        val stepIdx = (csState.stepIndexMap[lesson.id] ?: 0).coerceIn(0, (lesson.steps.size - 1).coerceAtLeast(0))
        val step = lesson.steps.getOrNull(stepIdx)
        val code = csState.drafts[lesson.id]
            ?: step?.starterCode
            ?: step?.code
            ?: lesson.code
        val stdin = csState.stdinMap[lesson.id] ?: step?.challenge?.stdin.orEmpty()
        val runRes = CSharpRuntime.runProgram(code, emptyMap(), stdin)
        _uiState.update {
            it.copy(
                csharpCodeDraft = code,
                guidedCSharpDraftBackup = null,
                csharpStdin = stdin,
                csharpRunResult = runRes,
                csharpTraceIndex = (runRes.trace.size - 1).coerceAtLeast(0),
                isAutoPlayingTrace = false,
                csharpTinkerMode = false,
                csharpFeedback = null,
                csharpFeedbackPassed = null,
                csharpBaselineOutput = runRes.output
            )
        }
    }

    fun selectCSharpStep(stepIdx: Int) {
        val lesson = currentCSharpLesson() ?: return
        if (stepIdx !in lesson.steps.indices) return
        val csState = _uiState.value.learnerState.csharp
        val maxUnlocked = csState.maxUnlockedStepMap[lesson.id] ?: 0
        if (stepIdx > maxUnlocked && !_uiState.value.csharpTinkerMode) return

        val step = lesson.steps[stepIdx]
        var nextCode = _uiState.value.csharpCodeDraft
        if (step.starterCode != null) {
            nextCode = step.starterCode
        } else if (step.code != null && nextCode.isBlank()) {
            nextCode = step.code
        }
        val nextStdin = step.challenge?.stdin ?: _uiState.value.csharpStdin
        val runRes = CSharpRuntime.runProgram(nextCode, emptyMap(), nextStdin)
        val baseline = if (step.type == "experiment") runRes.output else _uiState.value.csharpBaselineOutput

        updateLearnerState { st ->
            st.copy(
                csharp = st.csharp.copy(
                    stepIndexMap = st.csharp.stepIndexMap + (lesson.id to stepIdx),
                    drafts = st.csharp.drafts + (lesson.id to nextCode)
                )
            )
        }
        _uiState.update {
            it.copy(
                csharpCodeDraft = nextCode,
                csharpStdin = nextStdin,
                csharpRunResult = runRes,
                csharpTraceIndex = 0,
                isAutoPlayingTrace = false,
                csharpFeedback = null,
                csharpFeedbackPassed = null,
                csharpBaselineOutput = baseline
            )
        }
    }

    fun updateCSharpCode(newCode: String) {
        val lesson = currentCSharpLesson()
        _uiState.update {
            it.copy(
                csharpCodeDraft = newCode,
                csharpFeedback = null,
                csharpFeedbackPassed = null
            )
        }
        if (lesson != null && !_uiState.value.csharpTinkerMode) {
            updateLearnerState { st ->
                st.copy(
                    csharp = st.csharp.copy(
                        drafts = st.csharp.drafts + (lesson.id to newCode),
                        handCraftScore = (st.csharp.handCraftScore + 1).coerceAtMost(100)
                    )
                )
            }
        }
    }

    fun updateCSharpStdin(stdin: String) {
        val lesson = currentCSharpLesson()
        _uiState.update { it.copy(csharpStdin = stdin) }
        if (lesson != null) {
            updateLearnerState { st ->
                st.copy(csharp = st.csharp.copy(stdinMap = st.csharp.stdinMap + (lesson.id to stdin)))
            }
        }
    }

    fun runCSharpCode(autoPlayTrace: Boolean = false) {
        traceAutoPlayJob?.cancel()
        val state = _uiState.value
        val result = CSharpRuntime.runProgram(state.csharpCodeDraft, emptyMap(), state.csharpStdin)
        _uiState.update {
            it.copy(
                csharpRunResult = result,
                csharpTraceIndex = if (autoPlayTrace) 0 else (result.trace.size - 1).coerceAtLeast(0),
                isAutoPlayingTrace = false,
                csharpFeedback = result.error?.message,
                csharpFeedbackPassed = if (result.error != null) false else null
            )
        }
        updateLearnerState { st -> st.copy(examples = st.examples + 1) }
        if (autoPlayTrace && result.trace.size > 1) {
            toggleAutoPlayTrace()
        }
    }

    fun selectCSharpQuiz(optIdx: Int) {
        val lesson = currentCSharpLesson() ?: return
        updateLearnerState { st ->
            st.copy(csharp = st.csharp.copy(quizSelectedMap = st.csharp.quizSelectedMap + (lesson.id to optIdx)))
        }
        _uiState.update { it.copy(csharpFeedback = null, csharpFeedbackPassed = null) }
    }

    fun setTraceIndex(index: Int) {
        val traceSize = _uiState.value.csharpRunResult?.trace?.size ?: 0
        if (traceSize == 0) return
        _uiState.update { it.copy(csharpTraceIndex = index.coerceIn(0, traceSize - 1)) }
    }

    fun toggleAutoPlayTrace() {
        val state = _uiState.value
        val traceSize = state.csharpRunResult?.trace?.size ?: 0
        if (traceSize <= 1) return

        if (state.isAutoPlayingTrace) {
            traceAutoPlayJob?.cancel()
            _uiState.update { it.copy(isAutoPlayingTrace = false) }
        } else {
            val startIdx = if (state.csharpTraceIndex >= traceSize - 1) 0 else state.csharpTraceIndex
            _uiState.update { it.copy(isAutoPlayingTrace = true, csharpTraceIndex = startIdx) }
            traceAutoPlayJob?.cancel()
            traceAutoPlayJob = viewModelScope.launch {
                while (isActive) {
                    delay(520)
                    val cur = _uiState.value
                    val total = cur.csharpRunResult?.trace?.size ?: 0
                    if (cur.csharpTraceIndex + 1 >= total) {
                        _uiState.update { it.copy(isAutoPlayingTrace = false) }
                        break
                    }
                    _uiState.update { it.copy(csharpTraceIndex = it.csharpTraceIndex + 1) }
                }
            }
        }
    }

    fun toggleCSharpTinker() {
        val state = _uiState.value
        val entering = !state.csharpTinkerMode
        val lesson = currentCSharpLesson()
        if (entering) {
            _uiState.update {
                it.copy(
                    guidedCSharpDraftBackup = state.csharpCodeDraft,
                    csharpTinkerMode = true,
                    stepPhase = StepExecutionPhase.BEFORE_RUN,
                    csharpFeedback = null,
                    lastRunInterpretation = null
                )
            }
        } else {
            val restored = state.guidedCSharpDraftBackup
                ?: lesson?.let { state.learnerState.csharp.drafts[it.id] }
                ?: lesson?.code.orEmpty()
            val runRes = CSharpRuntime.runProgram(restored, emptyMap(), state.csharpStdin)
            _uiState.update {
                it.copy(
                    csharpTinkerMode = false,
                    guidedCSharpDraftBackup = null,
                    stepPhase = StepExecutionPhase.BEFORE_RUN,
                    csharpCodeDraft = restored,
                    csharpRunResult = runRes,
                    csharpTraceIndex = (runRes.trace.size - 1).coerceAtLeast(0),
                    csharpFeedback = null,
                    lastRunInterpretation = null
                )
            }
        }
    }

    fun mutateCSharpTinker() {
        val state = _uiState.value
        val nextIdx = state.csharpTinkerMutationIndex + 1
        val mutated = PreviewHtmlBuilder.mutateCodeSpark(state.csharpCodeDraft, TrackType.CSHARP, nextIdx)
        val runRes = CSharpRuntime.runProgram(mutated, emptyMap(), state.csharpStdin)
        _uiState.update {
            it.copy(
                csharpTinkerMode = true,
                stepPhase = StepExecutionPhase.AFTER_RUN,
                csharpTinkerMutationIndex = nextIdx,
                csharpCodeDraft = mutated,
                csharpRunResult = runRes,
                csharpTraceIndex = (runRes.trace.size - 1).coerceAtLeast(0),
                lastRunInterpretation = "تمت إضافة تجربة جديدة في البرنامج وتحديث فيلم التنفيذ."
            )
        }
    }

    fun resetCSharpCode() {
        val lesson = currentCSharpLesson() ?: return
        val stepIdx = _uiState.value.learnerState.csharp.stepIndexMap[lesson.id] ?: 0
        val step = lesson.steps.getOrNull(stepIdx)
        val base = step?.starterCode ?: step?.code ?: lesson.code
        val runRes = CSharpRuntime.runProgram(base, emptyMap(), _uiState.value.csharpStdin)
        if (!_uiState.value.csharpTinkerMode) {
            updateLearnerState { st ->
                st.copy(csharp = st.csharp.copy(drafts = st.csharp.drafts + (lesson.id to base)))
            }
        }
        _uiState.update {
            it.copy(
                csharpCodeDraft = base,
                csharpRunResult = runRes,
                csharpTraceIndex = 0,
                csharpFeedback = null
            )
        }
    }

    // ---- Angular Workbench ----

    fun currentAngularLesson(): RawLesson? {
        val track = _uiState.value.tracks[TrackType.ANGULAR] ?: return null
        val idx = _uiState.value.learnerState.angular.currentLessonIndex
            .coerceIn(0, (track.lessons.size - 1).coerceAtLeast(0))
        return track.lessons.getOrNull(idx)
    }

    private fun syncAngularWorkbench(index: Int) {
        val track = _uiState.value.tracks[TrackType.ANGULAR] ?: return
        val lesson = track.lessons.getOrNull(index) ?: return
        val ngState = _uiState.value.learnerState.angular
        val baseStarter = track.starterFiles
        val files = if (ngState.mode == "project") {
            baseStarter + ngState.projectFiles
        } else {
            val saved = ngState.playgroundFiles[lesson.id]
            if (saved.isNullOrEmpty()) {
                val targetFile = lesson.file.ifBlank { "src/app/app.ts" }
                if (lesson.code.isNotBlank()) baseStarter + (targetFile to lesson.code) else baseStarter
            } else {
                baseStarter + saved
            }
        }
        val activeFile = lesson.file.takeIf { it.isNotBlank() && files.containsKey(it) }
            ?: "src/app/app.ts"
        val previewDoc = AngularSimulator.renderPreviewDoc(lesson, activeFile, files)
        val alert = AngularSimulator.inspectGentleTree(files[activeFile].orEmpty(), activeFile)

        _uiState.update {
            it.copy(
                angularFiles = files,
                guidedAngularFilesBackup = null,
                angularActiveFilePath = activeFile,
                angularPreviewHtml = previewDoc,
                angularGentleAlert = alert,
                angularTinkerMode = false,
                angularFeedback = null,
                angularFeedbackPassed = null
            )
        }
    }

    fun selectAngularFile(path: String) {
        val files = _uiState.value.angularFiles
        if (!files.containsKey(path)) return
        val lesson = currentAngularLesson()
        val previewDoc = AngularSimulator.renderPreviewDoc(lesson, path, files)
        val alert = AngularSimulator.inspectGentleTree(files[path].orEmpty(), path)
        _uiState.update {
            it.copy(
                angularActiveFilePath = path,
                angularPreviewHtml = previewDoc,
                angularGentleAlert = alert
            )
        }
    }

    fun createAngularFile(rawPath: String): Boolean {
        val clean = rawPath.trim().replace("\\", "/").removePrefix("/")
        if (!AngularSimulator.isAllowedProjectPath(clean)) {
            _uiState.update {
                it.copy(
                    angularFeedback = "يُسمح بإنشاء ملفات .ts و .html و .css و .json و .md فقط.",
                    angularFeedbackPassed = false
                )
            }
            return false
        }
        val updatedFiles = _uiState.value.angularFiles + (clean to "// $clean\n")
        val lesson = currentAngularLesson()
        if (!_uiState.value.angularTinkerMode) saveAngularFiles(updatedFiles)
        _uiState.update {
            it.copy(
                angularFiles = updatedFiles,
                angularActiveFilePath = clean,
                angularPreviewHtml = AngularSimulator.renderPreviewDoc(lesson, clean, updatedFiles),
                angularFeedback = "تم إنشاء الملف $clean",
                angularFeedbackPassed = true
            )
        }
        return true
    }

    fun updateAngularActiveCode(newCode: String) {
        val path = _uiState.value.angularActiveFilePath
        val updatedFiles = _uiState.value.angularFiles + (path to newCode)
        val alert = AngularSimulator.inspectGentleTree(newCode, path)
        _uiState.update {
            it.copy(
                angularFiles = updatedFiles,
                angularGentleAlert = alert,
                angularFeedback = null
            )
        }
        if (!_uiState.value.angularTinkerMode) {
            saveAngularFiles(updatedFiles)
            updateLearnerState { st ->
                st.copy(angular = st.angular.copy(handCraftScore = (st.angular.handCraftScore + 1).coerceAtMost(100)))
            }
        }
    }

    private fun saveAngularFiles(files: Map<String, String>) {
        val lesson = currentAngularLesson() ?: return
        updateLearnerState { st ->
            if (st.angular.mode == "project") {
                st.copy(angular = st.angular.copy(projectFiles = files))
            } else {
                st.copy(angular = st.angular.copy(playgroundFiles = st.angular.playgroundFiles + (lesson.id to files)))
            }
        }
    }

    fun runAngularCode() {
        val state = _uiState.value
        val lesson = currentAngularLesson()
        val previewDoc = AngularSimulator.renderPreviewDoc(lesson, state.angularActiveFilePath, state.angularFiles)
        val alert = AngularSimulator.inspectGentleTree(
            state.angularFiles[state.angularActiveFilePath].orEmpty(),
            state.angularActiveFilePath
        )
        _uiState.update {
            it.copy(
                angularPreviewHtml = previewDoc,
                angularGentleAlert = alert
            )
        }
        updateLearnerState { st -> st.copy(examples = st.examples + 1) }
    }

    fun selectAngularStep(stepIdx: Int) {
        val lesson = currentAngularLesson() ?: return
        if (stepIdx !in lesson.steps.indices) return
        val stepFile = lesson.steps[stepIdx].file?.takeIf { it.isNotBlank() && _uiState.value.angularFiles.containsKey(it) }
        updateLearnerState { st ->
            st.copy(angular = st.angular.copy(stepIndexMap = st.angular.stepIndexMap + (lesson.id to stepIdx)))
        }
        if (stepFile != null) selectAngularFile(stepFile)
    }

    fun selectAngularQuiz(optIdx: Int) {
        val lesson = currentAngularLesson() ?: return
        updateLearnerState { st ->
            st.copy(angular = st.angular.copy(quizSelectedMap = st.angular.quizSelectedMap + (lesson.id to optIdx)))
        }
        _uiState.update { it.copy(angularFeedback = null, angularFeedbackPassed = null) }
    }

    fun toggleAngularTinker() {
        val state = _uiState.value
        val entering = !state.angularTinkerMode
        if (entering) {
            _uiState.update {
                it.copy(
                    guidedAngularFilesBackup = state.angularFiles,
                    angularTinkerMode = true,
                    stepPhase = StepExecutionPhase.BEFORE_RUN,
                    angularFeedback = null,
                    lastRunInterpretation = null
                )
            }
        } else {
            val restored = state.guidedAngularFilesBackup
            if (restored != null) {
                val lesson = currentAngularLesson()
                val path = state.angularActiveFilePath
                _uiState.update {
                    it.copy(
                        angularTinkerMode = false,
                        guidedAngularFilesBackup = null,
                        stepPhase = StepExecutionPhase.BEFORE_RUN,
                        angularFiles = restored,
                        angularPreviewHtml = AngularSimulator.renderPreviewDoc(lesson, path, restored),
                        angularFeedback = null,
                        lastRunInterpretation = null
                    )
                }
            } else {
                syncAngularWorkbench(state.learnerState.angular.currentLessonIndex)
            }
        }
    }

    fun mutateAngularTinker() {
        val state = _uiState.value
        val path = state.angularActiveFilePath
        val nextIdx = state.angularTinkerMutationIndex + 1
        val current = state.angularFiles[path].orEmpty()
        val mutated = AngularSimulator.mutateAngularSpark(current, path, nextIdx)
        val files = state.angularFiles + (path to mutated)
        _uiState.update {
            it.copy(
                angularTinkerMode = true,
                stepPhase = StepExecutionPhase.AFTER_RUN,
                angularTinkerMutationIndex = nextIdx,
                angularFiles = files,
                angularPreviewHtml = AngularSimulator.renderPreviewDoc(currentAngularLesson(), path, files),
                lastRunInterpretation = "تم تحديث شيفرة Angular ومعاينتها مباشرة في السينما."
            )
        }
    }

    fun resetAngularCode() {
        val track = _uiState.value.tracks[TrackType.ANGULAR] ?: return
        val lesson = currentAngularLesson() ?: return
        val targetFile = lesson.file.ifBlank { "src/app/app.ts" }
        val resetFiles = if (lesson.code.isNotBlank()) track.starterFiles + (targetFile to lesson.code) else track.starterFiles
        if (!_uiState.value.angularTinkerMode) saveAngularFiles(resetFiles)
        _uiState.update {
            it.copy(
                angularFiles = resetFiles,
                angularActiveFilePath = targetFile,
                angularPreviewHtml = AngularSimulator.renderPreviewDoc(lesson, targetFile, resetFiles),
                angularFeedback = null
            )
        }
    }

    // ---- Open-Loop Curiosity Ideas & Tinker Snapshots ----

    fun pinSparkIdea(spark: CuriositySparkOption) {
        val idea = CuriosityIdea(
            id = "idea-" + UUID.randomUUID().toString().take(8),
            text = spark.text,
            course = spark.course,
            topicIndex = spark.topicIndex,
            starterCode = spark.starterCode,
            createdAt = nowLabel(),
            tried = false
        )
        updateLearnerState { st ->
            val updated = (listOf(idea) + st.growth.ideaQueue.filterNot { it.text == spark.text }).take(16)
            st.copy(growth = st.growth.copy(ideaQueue = updated))
        }
        showToast("ثُبّتت الفكرة للعودة إليها لاحقًا")
    }

    fun pinCustomIdea(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val track = _uiState.value.activeTrack
        val idx = lessonIndexFor(_uiState.value.learnerState, track)
        val idea = CuriosityIdea(
            id = "idea-" + UUID.randomUUID().toString().take(8),
            text = clean,
            course = track.key,
            topicIndex = idx,
            starterCode = currentActiveCode(),
            createdAt = nowLabel(),
            tried = false
        )
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(ideaQueue = (listOf(idea) + st.growth.ideaQueue).take(16)))
        }
        showToast("تم تثبيت فكرتك المفتوحة للمرة القادمة")
    }

    fun openIdeaInWorkbench(idea: CuriosityIdea) {
        val track = TrackType.fromKey(idea.course)
        openLessonFlow(
            track = track,
            index = idea.topicIndex,
            startInTinker = true,
            ideaId = idea.id,
            customStarterCode = idea.starterCode
        )
        markIdeaTried(idea.id)
    }

    fun markIdeaTried(ideaId: String) {
        updateLearnerState { st ->
            st.copy(
                growth = st.growth.copy(
                    ideaQueue = st.growth.ideaQueue.map { if (it.id == ideaId) it.copy(tried = true) else it }
                )
            )
        }
    }

    fun removeIdea(ideaId: String) {
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(ideaQueue = st.growth.ideaQueue.filterNot { it.id == ideaId }))
        }
    }

    fun saveTinkerSnapshot(title: String, note: String) {
        val state = _uiState.value
        val track = state.activeTrack
        val code = currentActiveCode()
        if (code.isBlank()) return
        val snap = TinkerSnapshot(
            id = "snap-" + UUID.randomUUID().toString().take(8),
            title = title.trim().ifBlank { "تجربة حرة (${track.shortTitle})" },
            course = track.key,
            code = code,
            note = note.trim(),
            createdAt = nowLabel()
        )
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(tinkerSnapshots = (listOf(snap) + st.growth.tinkerSnapshots).take(20)))
        }
        showToast("تم حفظ التجربة في تجاربي ومشاريعي")
    }

    fun openSnapshotInWorkbench(snap: TinkerSnapshot) {
        val track = TrackType.fromKey(snap.course)
        val idx = lessonIndexFor(_uiState.value.learnerState, track)
        openLessonFlow(
            track = track,
            index = idx,
            startInTinker = true,
            ideaId = null,
            customStarterCode = snap.code
        )
    }

    fun deleteSnapshot(snapId: String) {
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(tinkerSnapshots = st.growth.tinkerSnapshots.filterNot { it.id == snapId }))
        }
    }

    // ---- Profile Card, Project Data & Mini Projects ----

    fun updateProfileCard(
        name: String? = null,
        role: String? = null,
        about: String? = null,
        color: ProfileCardColor? = null,
        step: Int? = null
    ) {
        updateLearnerState { st ->
            val updated = st.profileCard.copy(
                name = name ?: st.profileCard.name,
                role = role ?: st.profileCard.role,
                about = about ?: st.profileCard.about,
                color = color?.key ?: st.profileCard.color,
                step = step ?: st.profileCard.step
            )
            val ready = updated.isReady()
            st.copy(
                profileCard = updated.copy(completed = updated.completed || ready),
                project = maxOf(st.project, if (ready) 30 else st.project)
            )
        }
    }

    fun uploadProfileAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) { compressImageToDataUri(context, uri, 480, 340 * 1024) }
            if (encoded != null) {
                updateLearnerState { st ->
                    st.copy(profileCard = st.profileCard.copy(imageDataUri = encoded))
                }
                showToast("تم حفظ صورة البطاقة")
            } else {
                showToast("تعذر قراءة الصورة المختارة.")
            }
        }
    }

    fun clearProfileAvatar() {
        updateLearnerState { st -> st.copy(profileCard = st.profileCard.copy(imageDataUri = "")) }
    }

    fun buildExportProjectHtml(): String {
        val st = _uiState.value.learnerState
        return PreviewHtmlBuilder.buildProfileHtmlWithParts(
            card = st.profileCard,
            growth = st.growth,
            completedIds = st.growth.completed
        )
    }

    fun buildProfileShareSummary(): String {
        val state = _uiState.value
        val st = state.learnerState
        val card = st.profileCard
        val displayName = card.name.ifBlank { state.activeProfile.name }
        val displayRole = card.role.ifBlank { "متعلّم ومطوّر" }
        val displayAbout = card.about.ifBlank { "أطوّر مشاريعي بالتجربة المباشرة عبر steplearn." }
        val activeKey = lessonKeyFor(state.activeTrack, lessonIndexFor(st, state.activeTrack))
        val personalGoal = st.growth.personalGoals[activeKey]
        val mainProject = st.growth.livingProjects.firstOrNull()
        val snippet = (mainProject?.code?.takeIf { it.isNotBlank() } ?: buildExportProjectHtml())
            .lines()
            .take(12)
            .joinToString("\n")
        return buildString {
            appendLine("بطاقة مشروعي من steplearn")
            appendLine("الاسم: $displayName — $displayRole")
            appendLine("نبذة: $displayAbout")
            if (!personalGoal.isNullOrBlank()) {
                appendLine("هدفي الحالي: $personalGoal")
            }
            if (mainProject != null) {
                appendLine("المشروع المتطور: ${mainProject.title} (${mainProject.appliedStages.size}/4 مراحل)")
            }
            appendLine()
            appendLine("مقتطف الكود:")
            appendLine(snippet)
        }.trim()
    }

    // ---- Personal Goals, Living Projects & Home Screen Widget ----

    fun lessonKeyFor(track: TrackType, index: Int): String {
        val lessons = _uiState.value.tracks[track]?.lessons.orEmpty()
        val clamped = index.coerceIn(0, (lessons.size - 1).coerceAtLeast(0))
        val lesson = lessons.getOrNull(clamped)
        return when (track) {
            TrackType.CSHARP, TrackType.ANGULAR -> lesson?.id ?: "${track.key}-$clamped"
            else -> repository.lessonKey(track, clamped, lesson)
        }
    }

    fun currentActiveLessonKey(): String {
        val st = _uiState.value
        return lessonKeyFor(st.activeTrack, lessonIndexFor(st.learnerState, st.activeTrack))
    }

    fun personalGoalFor(track: TrackType, index: Int): String? {
        val key = lessonKeyFor(track, index)
        return _uiState.value.learnerState.growth.personalGoals[key]
    }

    fun setLessonPersonalGoal(lessonKey: String, goal: String) {
        val clean = goal.trim()
        updateLearnerState { st ->
            val updated = if (clean.isBlank()) {
                st.growth.personalGoals - lessonKey
            } else {
                st.growth.personalGoals + (lessonKey to clean)
            }
            st.copy(growth = st.growth.copy(personalGoals = updated))
        }
        syncWidgetSnapshot()
        if (clean.isNotBlank()) {
            showToast("تم تثبيت هدفك الشخصي: $clean")
        }
    }

    fun clearLessonPersonalGoal(lessonKey: String) {
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(personalGoals = st.growth.personalGoals - lessonKey))
        }
        syncWidgetSnapshot()
    }

    fun openLivingProjectInWorkbench(project: LivingProject) {
        openLessonFlow(
            track = TrackType.HTML,
            index = 0,
            startInTinker = true,
            ideaId = null,
            customStarterCode = project.code,
            forceIntro = true,
            livingProjectId = project.id
        )
    }

    fun saveActiveLivingProjectProgress(appliedStage: String? = null) {
        val state = _uiState.value
        val projectId = state.activeLivingProjectId ?: return
        val currentCode = currentActiveCode()
        if (currentCode.isBlank()) return

        val inferredStages = buildSet {
            add("structure")
            val lower = currentCode.lowercase()
            if (lower.contains("style=") || lower.contains("<style") || lower.contains("background") || lower.contains("rgba")) {
                add("style")
            }
            if (lower.contains("<button") || lower.contains("onclick") || lower.contains("<script") || lower.contains("addeventlistener")) {
                add("action")
            }
            if (lower.contains("fetch(") || lower.contains("json") || lower.contains("select ") || lower.contains("<table") || lower.contains("array")) {
                add("data")
            }
            if (!appliedStage.isNullOrBlank()) add(appliedStage)
        }.toList()

        updateLearnerState { st ->
            val updated = st.growth.livingProjects.map { proj ->
                if (proj.id == projectId) {
                    proj.copy(
                        code = currentCode,
                        appliedStages = (proj.appliedStages + inferredStages).distinct(),
                        updatedAt = nowLabel()
                    )
                } else proj
            }
            st.copy(growth = st.growth.copy(livingProjects = updated))
        }
        showToast("تم حفظ التطوير في مشروعك!")
    }

    fun createLivingProject(title: String, goal: String) {
        val cleanTitle = title.trim().ifBlank { "مشروع جديد" }
        val cleanGoal = goal.trim().ifBlank { "أطوّره خطوة بخطوة بما أتعلمه." }
        val starter = """
            <div class="card" style="padding:18px;border-radius:16px;background:rgba(43,176,127,0.18);border:1px solid rgba(140,229,190,0.45);color:#eaf3ee;">
              <h2 style="margin:0 0 8px;color:#8ce5be;">$cleanTitle</h2>
              <p style="margin:0;color:#eaf3ee;">$cleanGoal</p>
            </div>
        """.trimIndent()
        val project = LivingProject(
            id = "proj-" + UUID.randomUUID().toString().take(8),
            title = cleanTitle,
            goal = cleanGoal,
            code = starter,
            appliedStages = listOf("structure"),
            updatedAt = nowLabel()
        )
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(livingProjects = (listOf(project) + st.growth.livingProjects).take(12)))
        }
        showToast("أُنشئ مشروع «$cleanTitle»")
    }

    fun deleteLivingProject(projectId: String) {
        updateLearnerState { st ->
            st.copy(growth = st.growth.copy(livingProjects = st.growth.livingProjects.filterNot { it.id == projectId }))
        }
    }

    private fun syncWidgetSnapshot() {
        val state = _uiState.value
        val track = state.activeTrack
        val lessons = state.tracks[track]?.lessons.orEmpty()
        if (lessons.isEmpty()) return
        val idx = lessonIndexFor(state.learnerState, track).coerceIn(0, lessons.lastIndex)
        val lesson = lessons[idx]
        val nextLesson = lessons.getOrNull(idx + 1)
        val meta = CuriosityFraming.buildLessonMeta(repository, track, idx, lesson, nextLesson)
        val key = lessonKeyFor(track, idx)
        val personalGoal = state.learnerState.growth.personalGoals[key].orEmpty()

        runCatching {
            LearnStepWidgetProvider.saveAndRefreshWidget(
                context = getApplication(),
                snapshot = WidgetExperimentSnapshot(
                    trackKey = track.key,
                    trackTitle = track.shortTitle,
                    lessonIndex = idx,
                    headline = meta.experimentHeadline,
                    question = meta.curiosityQuestion,
                    personalGoal = personalGoal,
                    isGlassColors = lesson.id == "html-12-colors"
                )
            )
        }
    }

    // ---- Ambient Audio (Optional Calm Environment) ----

    fun toggleAmbientSound() {
        val next = !_uiState.value.uiPreferences.soundEnabled
        viewModelScope.launch { prefsRepo.setSoundEnabled(next) }
    }

    fun setAmbientSoundType(type: AmbientSoundType) {
        viewModelScope.launch { prefsRepo.setSoundType(type) }
    }

    fun setAmbientSoundVolume(volume: Int) {
        viewModelScope.launch { prefsRepo.setSoundVolume(volume) }
    }

    // ---- Internal Helpers ----

    private fun updateLearnerState(transform: (LearnerState) -> LearnerState) {
        val current = _uiState.value.learnerState
        val updated = transform(current)
        _uiState.update { it.copy(learnerState = updated) }
        val profileId = _uiState.value.activeProfile.id
        viewModelScope.launch(Dispatchers.IO) {
            database.saveState(profileId, updated)
        }
    }

    private fun nowLabel(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

    private fun compressImageToDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        maxBytes: Int
    ): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDimension * 2 || opts.outHeight / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val ratio = minOf(
                maxDimension.toFloat() / rawBmp.width.coerceAtLeast(1),
                maxDimension.toFloat() / rawBmp.height.coerceAtLeast(1),
                1f
            )
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    rawBmp,
                    (rawBmp.width * ratio).toInt().coerceAtLeast(1),
                    (rawBmp.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also { rawBmp.recycle() }
            } else rawBmp

            var quality = 88
            var outBytes: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                outBytes = baos.toByteArray()
                quality -= 10
            } while (outBytes.size > maxBytes && quality >= 30)
            scaled.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(outBytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        traceAutoPlayJob?.cancel()
        toastJob?.cancel()
        soundEngine.release()
    }
}
