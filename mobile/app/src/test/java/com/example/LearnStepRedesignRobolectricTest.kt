package com.example

import android.app.Application
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository
import com.example.engine.AngularSimulator
import com.example.engine.CSharpRuntime
import com.example.engine.SmartHintEngine
import com.example.engine.SqlPlaygroundEngine
import com.example.model.AppTab
import com.example.model.LessonStage
import com.example.model.StepExecutionPhase
import com.example.model.TrackType
import com.example.ui.StepLearnViewModel
import com.example.ui.components.findTargetLineNumbers
import com.example.widget.LearnStepWidgetProvider
import com.example.widget.WidgetExperimentSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearnStepRedesignRobolectricTest {

    private fun appContext(): Application = RuntimeEnvironment.getApplication()

    private fun waitForViewModelReady(vm: StepLearnViewModel) {
        val deadline = System.currentTimeMillis() + 5000
        while (vm.uiState.value.isLoading && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun curiosityFramingBuildsFlagshipHtmlColorsAndAllSixTracks() {
        val repo = CurriculumRepository(appContext())
        repo.preloadAll()

        val htmlTrack = repo.getTrack(TrackType.HTML)
        val colorsIndex = htmlTrack.lessons.indexOfFirst { it.id == "html-12-colors" }
        assertTrue("Expected html-12-colors lesson in HTML curriculum", colorsIndex >= 0)

        val colorsLesson = htmlTrack.lessons[colorsIndex]
        val colorsMeta = CuriosityFraming.buildLessonMeta(
            repo = repo,
            track = TrackType.HTML,
            lessonIndex = colorsIndex,
            lesson = colorsLesson,
            nextLesson = htmlTrack.lessons.getOrNull(colorsIndex + 1)
        )
        assertTrue(colorsMeta.experimentHeadline.contains("بطاقة زجاجية شفافة"))
        assertTrue(colorsMeta.targetPreviewHtml.contains("rgba("))
        assertTrue(colorsMeta.predictChallenge.options.size >= 2)
        assertTrue(colorsMeta.personalGoals.contains("تصميم بطاقة نجاح لتطبيقي"))
        assertTrue(colorsMeta.resumeReminder.contains("شفافة"))

        assertEquals(3, CuriosityFraming.levelGroups.size)
        TrackType.entries.forEach { trackType ->
            val lessons = repo.getTrack(trackType).lessons
            assertTrue("Expected lessons in ${trackType.key}", lessons.isNotEmpty())
            val meta = CuriosityFraming.buildLessonMeta(repo, trackType, 0, lessons.first(), lessons.getOrNull(1))
            assertTrue(meta.experimentHeadline.isNotBlank())
            assertTrue(meta.curiosityQuestion.isNotBlank())
            assertTrue(meta.canDoNow.isNotBlank())
            assertTrue(meta.personalGoals.isNotEmpty())
            assertTrue(meta.resumeReminder.isNotBlank())
        }
    }

    @Test
    fun targetLineHighlightingMatchesExactSnippetLines() {
        val code = """
            <div>
              <h1 style="color: tomato;">مرحبا</h1>
              <p>نص تجريبي</p>
            </div>
        """.trimIndent()
        val matched = findTargetLineNumbers(code, "<h1 style=\"color: tomato;\">")
        assertEquals(setOf(2), matched)
        assertTrue(findTargetLineNumbers(code, null).isEmpty())
    }

    @Test
    fun enginesExecuteRealCodeAcrossSqlCSharpAndAngular() {
        val sqlRes = SqlPlaygroundEngine().execute("SELECT name FROM products;")
        assertFalse(sqlRes.isError)
        assertTrue(sqlRes.columns.isNotEmpty())
        assertTrue(sqlRes.rows.isNotEmpty())

        val csRes = CSharpRuntime.runProgram(
            """
            int x = 4;
            int y = x * 3;
            Console.WriteLine(y);
            """.trimIndent()
        )
        assertEquals(null, csRes.error)
        assertTrue(csRes.output.contains("12"))
        assertTrue(csRes.trace.isNotEmpty())

        val repo = CurriculumRepository(appContext())
        val ngTrack = repo.getTrack(TrackType.ANGULAR)
        val firstNgLesson = ngTrack.lessons.first()
        val doc = AngularSimulator.renderPreviewDoc(firstNgLesson, "src/app/app.ts", ngTrack.starterFiles)
        assertTrue(doc.contains("<!doctype html>") || doc.contains("<html"))
    }

    @Test
    fun viewModelSupportsIntroPredictStepTinkerPinAndCompletionFlow() {
        val vm = StepLearnViewModel(appContext())
        waitForViewModelReady(vm)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(AppTab.HOME, vm.uiState.value.activeTab)
        assertEquals(LessonStage.NONE, vm.uiState.value.lessonStage)

        // 1. Open brand-new lesson -> shows INTRO
        vm.openLessonFlow(TrackType.HTML, 0, startInTinker = false, forceIntro = true)
        assertEquals(LessonStage.INTRO, vm.uiState.value.lessonStage)
        assertFalse(vm.uiState.value.introPredictRevealed)

        // 2. Skip or select predict reveals curtain
        vm.skipIntroPrediction()
        assertTrue(vm.uiState.value.introPredictRevealed)

        // 3. Start guided step -> StepExecutionPhase starts at BEFORE_RUN
        vm.startLessonFromIntro(inTinkerMode = false)
        assertEquals(LessonStage.STEP, vm.uiState.value.lessonStage)
        assertEquals(StepExecutionPhase.BEFORE_RUN, vm.uiState.value.stepPhase)
        assertFalse(vm.uiState.value.isCurrentTrackTinkerMode)

        // 4. Reveal progressive hints on demand
        assertEquals(0, vm.uiState.value.hintTierRevealed)
        vm.revealNextHintTier()
        assertEquals(1, vm.uiState.value.hintTierRevealed)

        // 5. Non-destructive Free Tinker mode preserves guided draft and updates stepPhase on run
        val guidedDraftBefore = vm.uiState.value.webCodeDraft
        vm.toggleActiveTinkerMode()
        assertTrue(vm.uiState.value.isCurrentTrackTinkerMode)
        vm.updateActiveCode("<h1>تجربة حرة مؤقتة</h1>")
        vm.runActiveCode()
        assertEquals(StepExecutionPhase.AFTER_RUN, vm.uiState.value.stepPhase)
        assertNotNull(vm.uiState.value.lastRunInterpretation)
        vm.returnToCodeEditPhase()
        assertEquals(StepExecutionPhase.BEFORE_RUN, vm.uiState.value.stepPhase)

        // Toggle back to guided -> restores guidedDraftBefore
        vm.toggleActiveTinkerMode()
        assertFalse(vm.uiState.value.isCurrentTrackTinkerMode)
        assertEquals(guidedDraftBefore, vm.uiState.value.webCodeDraft)

        // 6. Pin open-loop curiosity idea and open it in Tinker Mode
        vm.pinCustomIdea("تجربة لون زمردي شفاف")
        val pinned = vm.uiState.value.learnerState.growth.ideaQueue.firstOrNull()
        assertNotNull(pinned)
        vm.openIdeaInWorkbench(pinned!!)
        assertEquals(LessonStage.STEP, vm.uiState.value.lessonStage)
        assertTrue(vm.uiState.value.isCurrentTrackTinkerMode)
    }

    @Test
    fun smartResumePersonalGoalsLivingProjectsShareAndWidgetWorkCorrectly() {
        val vm = StepLearnViewModel(appContext())
        waitForViewModelReady(vm)

        // 1. Brand-new CSS lesson opens INTRO; after editing draft, reopening jumps straight to STEP with isResumingLesson = true
        vm.openLessonFlow(TrackType.CSS, 1, startInTinker = false)
        assertEquals(LessonStage.INTRO, vm.uiState.value.lessonStage)
        vm.startLessonFromIntro(inTinkerMode = false)
        vm.updateActiveCode("<style>.card { color: #8ce5be; }</style>")
        vm.exitLessonFlow()

        vm.openLessonFlow(TrackType.CSS, 1, startInTinker = false)
        assertEquals(LessonStage.STEP, vm.uiState.value.lessonStage)
        assertTrue(vm.uiState.value.isResumingLesson)

        // 2. Personal Goal ownership persists per lesson
        val key = vm.lessonKeyFor(TrackType.CSS, 1)
        vm.setLessonPersonalGoal(key, "تنظيم كروت لوحة التحكم")
        assertEquals("تنظيم كروت لوحة التحكم", vm.personalGoalFor(TrackType.CSS, 1))

        // 3. LivingProject develops across stages (structure -> style -> action)
        vm.createLivingProject("بطاقة نجاح", "تجربة تفاعلية")
        val project = vm.uiState.value.learnerState.growth.livingProjects.first()
        vm.openLivingProjectInWorkbench(project)
        assertEquals(project.id, vm.uiState.value.activeLivingProjectId)
        vm.updateActiveCode("<div style=\"background:rgba(43,176,127,0.2);\"><button onclick=\"alert('ok')\">ابدأ</button></div>")
        vm.saveActiveLivingProjectProgress()
        val updatedProject = vm.uiState.value.learnerState.growth.livingProjects.first { it.id == project.id }
        assertTrue("style" in updatedProject.appliedStages)
        assertTrue("action" in updatedProject.appliedStages)

        // 4. Profile card share summary includes card name + living project + code snippet
        vm.updateProfileCard(name = "أحمد", role = "مطور واجهات")
        val shareText = vm.buildProfileShareSummary()
        assertTrue(shareText.contains("أحمد"))
        assertTrue(shareText.contains("بطاقة نجاح"))
        assertTrue(shareText.contains("<button"))

        // 5. Widget snapshot + preview bitmap + deep-link intent
        val snap = WidgetExperimentSnapshot(
            trackKey = TrackType.HTML.key,
            trackTitle = "HTML",
            lessonIndex = 0,
            headline = "بطاقة زجاجية",
            question = "ماذا يحدث مع rgba؟",
            personalGoal = "تصميم بطاقة نجاح لتطبيقي",
            isGlassColors = true
        )
        LearnStepWidgetProvider.saveAndRefreshWidget(appContext(), snap)
        val restored = LearnStepWidgetProvider.readSnapshot(appContext())
        assertEquals("تصميم بطاقة نجاح لتطبيقي", restored.personalGoal)
        val bmp = LearnStepWidgetProvider.renderPreviewBitmap(restored)
        assertTrue(bmp.width == 260 && bmp.height == 104)
        val launchIntent = LearnStepWidgetProvider.buildLaunchIntent(appContext(), restored)
        assertTrue(launchIntent.getBooleanExtra(LearnStepWidgetProvider.EXTRA_LAUNCH_EXPERIMENT, false))
        assertEquals("html", launchIntent.getStringExtra(LearnStepWidgetProvider.EXTRA_WIDGET_TRACK))
    }
}
