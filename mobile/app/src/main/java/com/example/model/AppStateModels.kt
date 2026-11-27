package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class LearnerProfile(
    val id: String = "default",
    val name: String = "المتعلم الحالي"
)

enum class ProfileCardColor(
    val key: String,
    val labelAr: String,
    val accentHex: String,
    val softHex: String,
    val inkHex: String
) {
    FOREST("forest", "أخضر", "#286b56", "#eaf3e9", "#19372e"),
    OCEAN("ocean", "أزرق", "#35658f", "#eaf1f8", "#20364a"),
    PLUM("plum", "بنفسجي", "#79527e", "#f3edf4", "#403043"),
    SUNSET("sunset", "برتقالي دافئ", "#a65d32", "#fbefe6", "#4c3529");

    companion object {
        fun fromKey(key: String?): ProfileCardColor =
            entries.firstOrNull { it.key == key } ?: FOREST
    }
}

@Serializable
data class ProfileCardState(
    val name: String = "",
    val role: String = "",
    val about: String = "",
    val color: String = "forest",
    val imageDataUri: String = "",
    val step: Int = 1,
    val completed: Boolean = false
) {
    fun initials(): String = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1) }
        .ifEmpty { "خ" }

    fun isReady(): Boolean = name.isNotBlank() && role.isNotBlank() && about.isNotBlank()
}

@Serializable
data class LessonFlowState(
    val current: Int = 0,
    val unlocked: Int = 0
)

@Serializable
data class ProjectPart(
    val id: String = "",
    val label: String = "",
    val course: String = "",
    val objective: String = "",
    val code: String = "",
    val note: String = ""
)

@Serializable
data class GalleryPhoto(
    val id: String = "",
    val src: String = "",
    val alt: String = "",
    val caption: String = ""
)

@Serializable
data class PageSectionData(
    val title: String = "ما أتعلمه الآن",
    val description: String = "أبني صفحة شخصية خطوة خطوة."
)

@Serializable
data class ProjectData(
    val todos: List<String> = emptyList(),
    val note: String = "",
    val gallery: List<GalleryPhoto> = emptyList(),
    val pageSection: PageSectionData = PageSectionData(),
    val outputMessage: String = "كل تجربة تترك أثرًا."
)

@Serializable
data class DailyRecord(
    val day: String = "",
    val completed: Boolean = false,
    val topic: String = "",
    val result: String = ""
)

@Serializable
data class CuriosityIdea(
    val id: String = "",
    val text: String = "",
    val course: String = "html",
    val topicIndex: Int = 0,
    val starterCode: String = "",
    val createdAt: String = "",
    val tried: Boolean = false
)

@Serializable
data class PredictionRecord(
    val guessedIndex: Int = 0,
    val revealed: Boolean = false
)

@Serializable
data class TinkerSnapshot(
    val id: String = "",
    val title: String = "تجربة حرة",
    val course: String = "html",
    val code: String = "",
    val note: String = "",
    val createdAt: String = ""
)

@Serializable
data class LivingProject(
    val id: String = "living-card-1",
    val title: String = "بطاقة تفاعلية لتطبيقي",
    val goal: String = "ابدأ بهيكل بطاقة نظيف، ثم أضف التنسيق، فالأزرار التفاعلية، فالمنطق والبيانات.",
    val code: String = """
        <div class="card" style="padding:18px;border-radius:16px;background:rgba(43,176,127,0.18);border:1px solid rgba(140,229,190,0.45);color:#eaf3ee;">
          <h2 style="margin:0 0 8px;color:#8ce5be;">مشروعي الشخصي</h2>
          <p style="margin:0;color:#eaf3ee;">بطاقة تتطور مع كل مفهوم تتعلمه.</p>
        </div>
    """.trimIndent(),
    val appliedStages: List<String> = listOf("structure"),
    val updatedAt: String = ""
)

@Serializable
data class GrowthState(
    val completed: List<String> = emptyList(),
    val stages: Map<String, Int> = emptyMap(),
    val readingSteps: Map<String, Int> = emptyMap(),
    val lessonFlow: Map<String, LessonFlowState> = emptyMap(),
    val baseCodes: Map<String, String> = emptyMap(),
    val drafts: Map<String, String> = emptyMap(),
    val archivedDrafts: Map<String, String> = emptyMap(),
    val experimentCodes: Map<String, String> = emptyMap(),
    val repairStarted: Map<String, Boolean> = emptyMap(),
    val answers: Map<String, Boolean> = emptyMap(),
    val stepAttempts: Map<String, Int> = emptyMap(),
    val unlocks: List<String> = emptyList(),
    val projectParts: List<ProjectPart> = emptyList(),
    val projectData: ProjectData = ProjectData(),
    val daily: DailyRecord = DailyRecord(),
    val ideaQueue: List<CuriosityIdea> = emptyList(),
    val predictions: Map<String, PredictionRecord> = emptyMap(),
    val tinkerSnapshots: List<TinkerSnapshot> = emptyList(),
    val personalGoals: Map<String, String> = emptyMap(),
    val livingProjects: List<LivingProject> = emptyList(),
    val lastActiveDate: String = ""
)

@Serializable
data class MiniTodoItem(
    val text: String = "",
    val done: Boolean = false
)

@Serializable
data class MiniNoteItem(
    val title: String = "فكرة صغيرة",
    val body: String = ""
)

@Serializable
data class MiniCalculatorItem(
    val first: Double = 2.0,
    val second: Double = 3.0
)

@Serializable
data class MiniProjectsState(
    val active: String = "todo",
    val todo: List<MiniTodoItem> = emptyList(),
    val note: MiniNoteItem = MiniNoteItem(),
    val calculator: MiniCalculatorItem = MiniCalculatorItem()
)

@Serializable
data class CSharpProgressState(
    val currentLessonIndex: Int = 0,
    val completed: List<String> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val stdinMap: Map<String, String> = emptyMap(),
    val stepIndexMap: Map<String, Int> = emptyMap(),
    val maxUnlockedStepMap: Map<String, Int> = emptyMap(),
    val quizSelectedMap: Map<String, Int> = emptyMap(),
    val stepAttempts: Map<String, Int> = emptyMap(),
    val handCraftScore: Int = 20
)

@Serializable
data class AngularWorkspaceState(
    val currentLessonIndex: Int = 0,
    val completed: List<String> = emptyList(),
    val mode: String = "playground",
    val projectFiles: Map<String, String> = emptyMap(),
    val playgroundFiles: Map<String, Map<String, String>> = emptyMap(),
    val stepIndexMap: Map<String, Int> = emptyMap(),
    val quizSelectedMap: Map<String, Int> = emptyMap(),
    val stepAttempts: Map<String, Int> = emptyMap(),
    val handCraftScore: Int = 20
)

@Serializable
data class LearnerState(
    val lessons: Int = 0,
    val examples: Int = 0,
    val fixes: Int = 0,
    val minutes: Int = 0,
    val progress: Int = 0,
    val project: Int = 0,
    val activeCourse: String = "html",
    val htmlLesson: Int = 0,
    val cssLesson: Int = 0,
    val jsLesson: Int = 0,
    val databaseLesson: Int = 0,
    val focusDuration: Int = 300,
    val focusSeconds: Int = 300,
    val cheers: List<String> = emptyList(),
    val profileCard: ProfileCardState = ProfileCardState(),
    val growth: GrowthState = GrowthState(),
    val miniProjects: MiniProjectsState = MiniProjectsState(),
    val csharp: CSharpProgressState = CSharpProgressState(),
    val angular: AngularWorkspaceState = AngularWorkspaceState()
)

data class AbilityDefinition(
    val id: String,
    val title: String,
    val icon: String,
    val project: String,
    val tool: String,
    val challenge: String,
    val requires: List<Pair<String, String>>
)

data class SkillDefinition(
    val course: String,
    val title: String,
    val from: Int,
    val to: Int
)

enum class AmbientSoundType(val key: String, val labelAr: String) {
    WHITE("white", "ضوضاء بيضاء"),
    BROWN("brown", "ضوضاء بنية"),
    RAIN("rain", "مطر خفيف"),
    FOREST("forest", "نسيم الغابة"),
    CAFE("cafe", "مقهى هادئ");

    companion object {
        fun fromKey(key: String): AmbientSoundType = entries.firstOrNull { it.key == key } ?: WHITE
    }
}

enum class AppTab(val route: String, val labelAr: String) {
    HOME("home", "الرئيسية"),
    COURSES("courses", "المسارات"),
    BUILDS("builds", "تجاربي ومشاريعي")
}

enum class LessonStage {
    NONE,
    INTRO,
    STEP,
    COMPLETION
}

enum class StepExecutionPhase {
    BEFORE_RUN,
    AFTER_RUN
}
