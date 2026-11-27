package com.example.data

import com.example.data.CurriculumRepository.Companion.shortSnippet
import com.example.engine.AngularSimulator
import com.example.engine.PreviewHtmlBuilder
import com.example.engine.SmartHintEngine
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.TrackType
import com.example.model.asExpectedStrings

data class TrackLevelGroup(
    val levelTitle: String,
    val levelSubtitle: String,
    val tracks: List<TrackType>
)

data class LessonCuriosityMeta(
    val experimentHeadline: String,
    val problemStatement: String,
    val curiosityQuestion: String,
    val baselineSnippet: String,
    val targetPreviewHtml: String,
    val canDoNow: String,
    val conceptSummary: String,
    val deepExplanation: String,
    val nextSparkQuestion: String,
    val predictChallenge: PredictChallenge,
    val personalGoals: List<String>,
    val resumeReminder: String
)

object CuriosityFraming {

    val levelGroups: List<TrackLevelGroup> = listOf(
        TrackLevelGroup(
            levelTitle = "المستوى الأول · أساسيات الويب",
            levelSubtitle = "بناء الصفحات وتنسيقها بصريًا خطوة بخطوة",
            tracks = listOf(TrackType.HTML, TrackType.CSS)
        ),
        TrackLevelGroup(
            levelTitle = "المستوى الثاني · التفاعل والبيانات",
            levelSubtitle = "تحريك العناصر بالمنطق واستخراج النتائج من الجداول",
            tracks = listOf(TrackType.JS, TrackType.DATABASE)
        ),
        TrackLevelGroup(
            levelTitle = "المستوى الثالث · البرمجة المتقدمة والتطبيقات",
            levelSubtitle = "الخوارزميات وتتبّع الذاكرة وبناء تطبيقات الويب التفاعلية",
            tracks = listOf(TrackType.CSHARP, TrackType.ANGULAR)
        )
    )

    fun levelLabelFor(track: TrackType): String = when (track) {
        TrackType.HTML, TrackType.CSS -> "أساسيات الويب"
        TrackType.JS, TrackType.DATABASE -> "التفاعل والبيانات"
        TrackType.CSHARP, TrackType.ANGULAR -> "البرمجة المتقدمة والتطبيقات"
    }

    fun buildLessonMeta(
        repo: CurriculumRepository,
        track: TrackType,
        lessonIndex: Int,
        lesson: RawLesson,
        nextLesson: RawLesson?
    ): LessonCuriosityMeta {
        val practiceSteps = SmartHintEngine.buildPracticeStepsForLesson(track, lesson)
        val firstEdit = practiceSteps.firstOrNull()
        val predict = if (track == TrackType.ANGULAR) {
            AngularSimulator.buildPredictChallenge(lesson)
        } else {
            repo.buildPredictChallenge(track, lesson, practiceSteps)
        }
        val personalGoals = derivePersonalGoals(track, lesson)

        // Crafted flagship problem-first framing for high-impact discovery lessons
        if (track == TrackType.HTML && lesson.id == "html-12-colors") {
            val glassPreview = PreviewHtmlBuilder.buildHtmlPreview(
                """
                <div style="padding:18px;border-radius:16px;background:linear-gradient(135deg,#0f2b22,#1e4d3b);color:#eaf3ee;">
                  <div style="padding:14px;border-radius:12px;background:rgba(43,176,127,0.22);border:1px solid rgba(140,229,190,0.45);">
                    <h2 style="margin:0 0 6px;color:#8ce5be;font-size:18px;">بطاقة زجاجية واضحة</h2>
                    <p style="margin:0;color:#eaf3ee;font-size:14px;">خلفية شبه شفافة بـ rgba مع نص عالي التباين!</p>
                  </div>
                </div>
                """.trimIndent()
            )
            val nextQ = nextLesson?.let {
                "كيف تفصل ملف التنسيق تمامًا عن صفحة HTML ليُطبّق على عشرات الصفحات دفعة واحدة؟"
            } ?: "جرّب مزج الألوان والشفافية لصنع بطاقة خاصة بك!"
            return LessonCuriosityMeta(
                experimentHeadline = "كيف تصنع بطاقة زجاجية شفافة دون التأثير على وضوح النص؟",
                problemStatement = "أحيانًا تضع خلفية ملونة فوق صورة أو سطح داكن فيبهت النص أو يضيع تباينه. نحتاج للتحكم في لون الحروف، وخلفية الصندوق، والشفافية بدقة.",
                curiosityQuestion = "ماذا يحدث للنص عندما تستبدل لونًا صلبًا بقيمة HEX دقيقة أو قناة شفافية rgba؟",
                baselineSnippet = firstEdit?.starterCode?.lines()?.take(6)?.joinToString("\n")
                    ?: "<h1 style=\"color: tomato;\">لون الحروف</h1>\n<p style=\"background-color: lightyellow;\">لون مساحة الخلفية.</p>",
                targetPreviewHtml = glassPreview,
                canDoNow = "تلوين أي عنصر بدقة عبر أسماء الألوان وHEX، وفصل لون النص عن شفافية الخلفية لصناعة بطاقات زجاجية مريحة للعين.",
                conceptSummary = lesson.concept,
                deepExplanation = listOf(lesson.whyUse, lesson.browserMechanics).filter { it.isNotBlank() }.joinToString("\n\n"),
                nextSparkQuestion = nextQ,
                predictChallenge = predict,
                personalGoals = personalGoals,
                resumeReminder = "كنا نحاول جعل خلفية البطاقة شفافة. غيّر الرقم الأخير من اللون وشاهد النتيجة."
            )
        }

        val headline = deriveProblemHeadline(track, lesson, firstEdit)
        val problem = deriveProblemStatement(track, lesson, firstEdit)
        val curiosity = deriveCuriosityQuestion(track, lesson, firstEdit)
        val baseline = deriveBaselineSnippet(track, lesson, firstEdit)
        val targetPreview = buildTargetPreviewHtml(track, lesson, firstEdit)
        val canDo = deriveCanDoNow(track, lesson, firstEdit)
        val nextQuestion = nextLesson?.let { nxt ->
            val nxtEdits = SmartHintEngine.buildPracticeStepsForLesson(track, nxt)
            deriveCuriosityQuestion(track, nxt, nxtEdits.firstOrNull())
        } ?: "ماذا لو جرّبت كسر قاعدة واحدة من هذا الدرس وشاهدت النتيجة في المعمل الحر؟"

        val deep = listOf(lesson.concept, lesson.whyUse, lesson.browserMechanics)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n\n")

        val resumeReminder = deriveResumeReminder(track, lesson, firstEdit)

        return LessonCuriosityMeta(
            experimentHeadline = headline,
            problemStatement = problem,
            curiosityQuestion = curiosity,
            baselineSnippet = baseline,
            targetPreviewHtml = targetPreview,
            canDoNow = canDo,
            conceptSummary = lesson.concept.ifBlank { lesson.summary.ifBlank { lesson.title } },
            deepExplanation = deep,
            nextSparkQuestion = nextQuestion,
            predictChallenge = predict,
            personalGoals = personalGoals,
            resumeReminder = resumeReminder
        )
    }

    private fun derivePersonalGoals(track: TrackType, lesson: RawLesson): List<String> {
        val idLower = lesson.id.lowercase()
        val titleLower = lesson.title.lowercase()
        return when {
            track == TrackType.HTML && (idLower == "html-12-colors" || titleLower.contains("لون") || titleLower.contains("الألوان")) ->
                listOf("تصميم بطاقة نجاح لتطبيقي", "تنسيق تنبيه شفاف فوق صورة", "تلوين أزرار صفحتي الشخصية")
            (track == TrackType.CSS || track == TrackType.HTML) && (idLower.contains("flex") || titleLower.contains("flex") || titleLower.contains("مرن")) ->
                listOf("تنظيم كروت لوحة التحكم", "ترتيب شريط أزرار متناسق", "توسيط بطاقة الترحيب في الشاشة")
            track == TrackType.JS && (idLower.contains("cond") || idLower.contains("if") || titleLower.contains("شرط") || titleLower.contains("قرار")) ->
                listOf("التحكم في فتح باب داخل لعبتي", "إظهار رسالة نجاح عند الفوز", "التحقق من شرط الدخول السريع")
            track == TrackType.DATABASE ->
                listOf("استخراج بيانات طلاب الكورس", "عرض أكثر العناصر تميّزًا", "تصفية النتائج حسب الحالة")
            track == TrackType.CSHARP ->
                listOf("حساب نقاط اللاعب في لعبتي", "فحص مدخلات المستخدم خطوة بخطوة", "تتبع تغيّر القيم داخل الذاكرة")
            track == TrackType.ANGULAR ->
                listOf("تطوير بطاقة فيلم في تطبيق السينما", "تفعيل تفاعل المستخدم مع العرض", "تحديث حالة الواجهة لحظيًا")
            track == TrackType.CSS ->
                listOf("تحسين بطاقة مشروعي الشخصي", "جعل العناصر أوضح وأكثر توازنًا", "تجربة ألوان وزوايا عصرية")
            track == TrackType.JS ->
                listOf("إضافة زر تفاعلي لبطاقتي", "تحديث النص في الصفحة لحظيًا", "اختبار سلوك دالة جديدة")
            else ->
                listOf("بناء قسم جديد في بطاقتي", "تجربة عرض المحتوى بوضوح", "دمج الفكرة داخل مشروعي")
        }
    }

    private fun deriveResumeReminder(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        if (firstEdit != null && firstEdit.from.isNotBlank() && firstEdit.to.isNotBlank()) {
            return "كنا نعدّل «${shortSnippet(firstEdit.from, 28)}» لنصل إلى النتيجة المطلوبة. أكمل التعديل وشغّل الكود."
        }
        return when (track) {
            TrackType.DATABASE -> "كنا نصوغ استعلام «${lesson.title}». شغّل الاستعلام وشاهد الصفوف الناتجة."
            TrackType.CSHARP -> "كنا نتتبع تنفيذ «${lesson.title}». عدّل السطر المميّز وشغّل فيلم التنفيذ."
            else -> "كنا نجرّب «${lesson.title}». أضف تعديلك الصغير وشغّل لتشاهد الأثر فورًا."
        }
    }

    private fun deriveProblemHeadline(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        if (firstEdit != null && firstEdit.objective.isNotBlank()) {
            return firstEdit.objective.trimEnd('.') + " — وشاهد الأثر مباشرة"
        }
        val cleanSummary = lesson.summary.trim()
        if (cleanSummary.isNotBlank()) {
            return shortSnippet(cleanSummary, 88)
        }
        return when (track) {
            TrackType.HTML -> "كيف تبني «${lesson.title}» وتراه فورًا في المتصفح؟"
            TrackType.CSS -> "كيف تغيّر مظهر «${lesson.title}» بتعديل قاعدة واحدة؟"
            TrackType.JS -> "كيف تجعل الصفحة تتفاعل مع «${lesson.title}» لحظيًا؟"
            TrackType.DATABASE -> "كيف تستخرج النتيجة الدقيقة لـ «${lesson.title}» من الجدول؟"
            TrackType.CSHARP -> "ماذا يحدث داخل الذاكرة خطوة بخطوة في «${lesson.title}»؟"
            TrackType.ANGULAR -> "كيف ينعكس تعديل «${lesson.title}» مباشرة على تطبيق السينما؟"
        }
    }

    private fun deriveProblemStatement(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        val why = lesson.whyUse.trim()
        if (why.isNotBlank()) return shortSnippet(why, 180)
        if (firstEdit != null && firstEdit.from.isNotBlank() && firstEdit.to.isNotBlank()) {
            return "في الكود الحالي لدينا «${shortSnippet(firstEdit.from, 44)}». نريد تغييره لنصل إلى النتيجة المطلوبة دون كسر بقية البنية."
        }
        if (lesson.task.isNotBlank()) return shortSnippet(lesson.task, 170)
        return when (track) {
            TrackType.DATABASE -> "البيانات مخزنة في الجدول، لكننا نحتاج إلى صياغة استعلام يُظهر النتيجة المطلوبة فقط."
            TrackType.CSHARP -> "البرنامج ينفذ التعليمات سطرًا بسطر؛ تتبع تغيّر المتغيرات يكشف سبب كل مخرج."
            else -> "لدينا نواة أولية تحتاج تعديلًا محددًا لتظهر النتيجة البصرية كاملة."
        }
    }

    private fun deriveCuriosityQuestion(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        if (firstEdit != null && firstEdit.from.isNotBlank() && firstEdit.to.isNotBlank()) {
            return "ماذا يتغيّر في النتيجة إذا استبدلت «${shortSnippet(firstEdit.from, 34)}» بـ «${shortSnippet(firstEdit.to, 34)}»؟"
        }
        val bug = lesson.bugRepair
        if (bug != null && bug.question.isNotBlank()) {
            return shortSnippet(bug.question, 105)
        }
        val quizQ = lesson.quiz?.question?.takeIf { it.isNotBlank() }
        if (quizQ != null) return shortSnippet(quizQ, 105)
        val firstStepQ = lesson.steps.firstOrNull { !it.question.isNullOrBlank() }?.question
        if (!firstStepQ.isNullOrBlank()) return shortSnippet(firstStepQ, 105)

        return when (track) {
            TrackType.CSS -> "كيف يتحول شكل البطاقة فورًا عندما تضيف قاعدة «${lesson.title}»؟"
            TrackType.JS -> "ماذا يظهر في المخرجات عند تشغيل منطق «${lesson.title}»؟"
            TrackType.DATABASE -> "أي الصفوف ستظهر في الجدول عند تطبيق «${lesson.title}»؟"
            TrackType.CSHARP -> "كيف تتبدل قيم المتغيرات في الذاكرة أثناء تشغيل «${lesson.title}»؟"
            TrackType.ANGULAR -> "كيف تتحدث واجهة السينما لحظيًا عند تعديل «${lesson.title}»؟"
            TrackType.HTML -> "كيف يقرأ المتصفح سطور «${lesson.title}» ويحولها إلى شكل ظاهر؟"
        }
    }

    private fun deriveBaselineSnippet(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        val raw = firstEdit?.starterCode?.takeIf { it.isNotBlank() }
            ?: lesson.bugRepair?.brokenCode?.takeIf { it.isNotBlank() }
            ?: lesson.code.takeIf { it.isNotBlank() }
            ?: lesson.steps.firstOrNull { !it.code.isNullOrBlank() }?.code.orEmpty()
        return raw.lines().take(7).joinToString("\n").ifBlank {
            "// ${track.fileName}"
        }
    }

    private fun buildTargetPreviewHtml(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        val targetCode = firstEdit?.solution?.takeIf { it.isNotBlank() }
            ?: lesson.expected.asExpectedStrings().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: lesson.code
        return when (track) {
            TrackType.HTML, TrackType.CSS, TrackType.JS -> PreviewHtmlBuilder.buildTrackPreview(track, targetCode)
            TrackType.ANGULAR -> AngularSimulator.renderPreviewDoc(
                lesson = lesson,
                activePath = lesson.file.ifBlank { "src/app/app.ts" },
                files = mapOf((lesson.file.ifBlank { "src/app/app.ts" }) to targetCode)
            )
            TrackType.DATABASE -> PreviewHtmlBuilder.buildHtmlPreview(
                """
                <div style="padding:12px;border-radius:10px;background:#0f1f18;color:#eaf3ee;border:1px solid #24382f;">
                  <div style="color:#8ce5be;font-weight:bold;margin-bottom:6px;">استعلام SQL جاهز للتنفيذ</div>
                  <pre dir="ltr" style="margin:0;color:#52d1a0;font-size:13px;white-space:pre-wrap;">${PreviewHtmlBuilder.escapeHtml(targetCode.lines().take(5).joinToString("\n"))}</pre>
                </div>
                """.trimIndent()
            )
            TrackType.CSHARP -> PreviewHtmlBuilder.buildHtmlPreview(
                """
                <div style="padding:12px;border-radius:10px;background:#0f1f18;color:#eaf3ee;border:1px solid #24382f;">
                  <div style="color:#8ce5be;font-weight:bold;margin-bottom:6px;">تتبع التنفيذ والذاكرة (C#)</div>
                  <pre dir="ltr" style="margin:0;color:#e1efe7;font-size:12px;white-space:pre-wrap;">${PreviewHtmlBuilder.escapeHtml(targetCode.lines().take(6).joinToString("\n"))}</pre>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun deriveCanDoNow(
        track: TrackType,
        lesson: RawLesson,
        firstEdit: RawLessonStep?
    ): String {
        val benefit = firstEdit?.benefit?.replace(Regex("^فائدة[^:]*:\\s*"), "")?.trim()
        if (!benefit.isNullOrBlank()) {
            return "أصبح بإمكانك الآن $benefit وتوظيف «${lesson.title}» في تجاربك الحرة ومشروعك."
        }
        return when (track) {
            TrackType.HTML -> "يمكنك الآن بناء عناصر «${lesson.title}» ودمجها داخل صفحاتك بثقة."
            TrackType.CSS -> "يمكنك الآن تعديل مظهر العناصر عبر «${lesson.title}» وملاحظة التباين والتخطيط فورًا."
            TrackType.JS -> "يمكنك الآن كتابة منطق «${lesson.title}» وتجربة مدخلات متعددة في المتصفح."
            TrackType.DATABASE -> "يمكنك الآن صياغة استعلامات «${lesson.title}» واستخراج النتائج التي تحتاجها من أي جدول."
            TrackType.CSHARP -> "يمكنك الآن تتبع «${lesson.title}» داخل الذاكرة وفهم سلوك كل متغير وشرط."
            TrackType.ANGULAR -> "يمكنك الآن تحديث مكونات تطبيق السينما باستخدام «${lesson.title}» ومشاهدة النتيجة الفورية."
        }
    }

    fun formatStepInterpretation(
        track: TrackType,
        step: RawLessonStep?,
        lesson: RawLesson
    ): String {
        if (step == null) return "شغّلت الكود ولاحظت أثره مباشرة في المعاينة."
        val expl = step.explanation.trim()
        val benefit = step.benefit.trim()
        return when {
            expl.isNotBlank() && benefit.isNotBlank() && expl != benefit -> "$expl\n$benefit"
            expl.isNotBlank() -> expl
            benefit.isNotBlank() -> benefit
            lesson.concept.isNotBlank() -> lesson.concept
            else -> when (track) {
                TrackType.DATABASE -> "نفّذ المحرك الاستعلام وعرض الصفوف المطابقة في الجدول."
                TrackType.CSHARP -> "تم تنفيذ السطور وتحديث قيم المتغيرات والمخرجات في فيلم التنفيذ."
                else -> "قرأ المحرك تعديلك وحدّث النتيجة المرئية فورًا."
            }
        }
    }
}
