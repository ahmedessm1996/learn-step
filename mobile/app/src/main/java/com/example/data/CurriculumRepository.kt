package com.example.data

import android.content.Context
import com.example.model.AbilityDefinition
import com.example.model.CurriculumTrack
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.SkillDefinition
import com.example.model.TrackType
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs

data class CuriositySparkOption(
    val label: String,
    val text: String,
    val course: String,
    val topicIndex: Int,
    val starterCode: String
)

data class CuriosityTeaser(
    val question: String,
    val sparks: List<CuriositySparkOption>
)

data class PredictChallenge(
    val prompt: String,
    val snippet: String,
    val options: List<String>,
    val correctIndex: Int,
    val revealNote: String
)

data class DailyChallengeSpec(
    val title: String,
    val kind: String, // "text" | "color"
    val course: String,
    val label: String,
    val prompt: String,
    val placeholder: String,
    val artifactLabel: String,
    val objective: String,
    val action: String = ""
)

class CurriculumRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val trackCache = mutableMapOf<TrackType, CurriculumTrack>()

    fun getTrack(trackType: TrackType): CurriculumTrack {
        return trackCache.getOrPut(trackType) {
            val raw = context.assets.open(trackType.assetFile).bufferedReader().use { it.readText() }
            parseTrack(raw)
        }
    }

    fun parseTrack(rawJson: String): CurriculumTrack {
        return json.decodeFromString<CurriculumTrack>(rawJson)
    }

    fun preloadAll() {
        TrackType.entries.forEach { getTrack(it) }
    }

    fun lessonKey(track: TrackType, index: Int, lesson: RawLesson? = null): String {
        return when (track) {
            TrackType.CSHARP, TrackType.ANGULAR -> lesson?.id?.takeIf { it.isNotBlank() } ?: "${track.key}:$index"
            else -> "${track.key}:$index"
        }
    }

    fun lessonObjective(lesson: RawLesson): String {
        return lesson.summary.ifBlank {
            lesson.concept.ifBlank { lesson.task.ifBlank { lesson.title } }
        }
    }

    fun getCuriosityTeaser(
        track: TrackType,
        index: Int,
        lesson: RawLesson,
        practiceSteps: List<RawLessonStep>
    ): CuriosityTeaser {
        val firstStep = practiceSteps.firstOrNull()
        val codeSample = lesson.code
        val sparks = mutableListOf<CuriositySparkOption>()

        val question = when {
            firstStep != null && firstStep.from.isNotBlank() && firstStep.to.isNotBlank() -> {
                "ماذا يتغير في النتيجة لو بدّلت «${shortSnippet(firstStep.from, 34)}» إلى «${shortSnippet(firstStep.to, 34)}»؟"
            }
            lesson.bugRepair != null && lesson.bugRepair.question.isNotBlank() -> {
                "لغز سريع في ${lesson.title}: ${shortSnippet(lesson.bugRepair.question, 90)}"
            }
            else -> {
                "ماذا يحدث لو جرّبت تغيير سطر واحد في «${lesson.title}» وشاهدت النتيجة فورًا؟"
            }
        }

        if (firstStep != null && firstStep.from.isNotBlank() && firstStep.to.isNotBlank()) {
            sparks += CuriositySparkOption(
                label = "جرّب تبديل القيمة الأولى",
                text = "أجرب تبديل «${shortSnippet(firstStep.from, 28)}» بـ «${shortSnippet(firstStep.to, 28)}» في ${lesson.title}",
                course = track.key,
                topicIndex = index,
                starterCode = firstStep.starterCode ?: codeSample
            )
        }

        val brokenSnippet = lesson.bugRepair?.brokenCode?.takeIf { it.isNotBlank() }
        if (brokenSnippet != null) {
            sparks += CuriositySparkOption(
                label = "اختبر الكود المعطوب",
                text = "أشوف ماذا يحدث لو شغّلت الكود المعطوب في ${lesson.title} ثم أصلحه بيدي",
                course = track.key,
                topicIndex = index,
                starterCode = brokenSnippet
            )
        }

        val freeLabel = when (track) {
            TrackType.CSS -> "اكسر التنسيق وشوف"
            TrackType.JS -> "غيّر المدخلات"
            TrackType.DATABASE -> "بدّل الاستعلام"
            TrackType.CSHARP -> "جرّب قيمة أخرى"
            TrackType.ANGULAR -> "عدّل الإشارة"
            TrackType.HTML -> "أضف لمسة حرة"
        }
        val freeIdea = when (track) {
            TrackType.CSS -> "أكسر تنسيق «${lesson.title}» بلون أو مسافة جريئة وأراقب الصفحة"
            TrackType.JS -> "أبدّل مدخلات «${lesson.title}» وأراقب المخرجات في المعاينة"
            TrackType.DATABASE -> "أغيّر شروط الاستعلام في «${lesson.title}» وأشوف الجدول الناتج"
            TrackType.CSHARP -> "أعدّل قيمة متغير في «${lesson.title}» وأتتبع تغيّر الذاكرة"
            TrackType.ANGULAR -> "أبدّل قيمة Signal أو القالب في «${lesson.title}» وأراقب السينما"
            TrackType.HTML -> "أضيف لمستي الخاصة داخل «${lesson.title}» وأرى شكلها فورًا"
        }
        sparks += CuriositySparkOption(
            label = freeLabel,
            text = freeIdea,
            course = track.key,
            topicIndex = index,
            starterCode = codeSample
        )

        return CuriosityTeaser(question = question, sparks = sparks.take(3))
    }

    fun buildPredictChallenge(
        track: TrackType,
        lesson: RawLesson,
        practiceSteps: List<RawLessonStep>
    ): PredictChallenge {
        val quiz = lesson.quiz
        if (quiz != null && quiz.options.size >= 2) {
            return PredictChallenge(
                prompt = quiz.question.ifBlank { "قبل التشغيل: ماذا تتوقع أن يحدث هنا؟" },
                snippet = lesson.code.lines().take(6).joinToString("\n"),
                options = quiz.options.take(4),
                correctIndex = quiz.correct.coerceIn(0, quiz.options.size - 1),
                revealNote = lesson.concept.ifBlank { lessonObjective(lesson) }
            )
        }
        val bug = lesson.bugRepair
        if (bug != null && bug.question.isNotBlank() && bug.options.size >= 2 && bug.brokenCode.isNotBlank()) {
            return PredictChallenge(
                prompt = "توقّع قبل التشغيل: لو شغلنا هذا السطر كما هو، ما الذي سيحدث؟",
                snippet = bug.brokenCode.lines().take(6).joinToString("\n"),
                options = bug.options.take(4),
                correctIndex = bug.correctOptionIndex.coerceIn(0, bug.options.size - 1),
                revealNote = bug.explanation.ifBlank { lesson.concept }
            )
        }
        val first = practiceSteps.firstOrNull()
        if (first != null && first.from.isNotBlank() && first.to.isNotBlank()) {
            val cleanBenefit = first.benefit.replace(Regex("^فائدة[^:]*:\\s*"), "").trim()
            val correctText = if (cleanBenefit.isNotBlank()) {
                shortSnippet(cleanBenefit, 110)
            } else {
                "تتغير النتيجة الحية مباشرة لتعكس «${shortSnippet(first.to, 30)}»"
            }
            val distractor1 = when (track) {
                TrackType.CSS -> "لن يتأثر الشكل الظاهر إلا إذا أعدنا كتابة ملف HTML بالكامل"
                TrackType.JS -> "سيظهر الكود نفسه كنص عادي ولن يتنفذ أي تغيير"
                TrackType.DATABASE -> "سيتوقف محرك قواعد البيانات عن قراءة بقية الجداول"
                else -> "سيختفي محتوى الصفحة بالكامل ولن يظهر شيء"
            }
            val distractor2 = "يتوقف المحرك عن قراءة باقي الأسطر تمامًا"
            return PredictChallenge(
                prompt = "توقّع قبل التشغيل: لو استبدلنا «${shortSnippet(first.from, 36)}» بـ «${shortSnippet(first.to, 36)}» ماذا سيحدث؟",
                snippet = "${first.from}  ⟶  ${first.to}",
                options = listOf(correctText, distractor1, distractor2),
                correctIndex = 0,
                revealNote = first.explanation.ifBlank { lesson.concept.ifBlank { "جرّب التعديل الآن في المحرر وشاهد الأثر بعينك." } }
            )
        }
        return PredictChallenge(
            prompt = "توقّع قبل التشغيل: ما الدور الأساسي الذي يظهر عند تشغيل مثال «${lesson.title}»؟",
            snippet = lesson.code.lines().take(5).joinToString("\n"),
            options = listOf(
                shortSnippet(lessonObjective(lesson).ifBlank { "تنفيذ الفكرة وظهور أثرها في المعاينة" }, 110),
                "لا يحدث أي أثر مرئي أو منطقي عند التشغيل"
            ),
            correctIndex = 0,
            revealNote = lesson.concept.ifBlank { lessonObjective(lesson) }
        )
    }

    fun dailyChallengeModel(track: TrackType, lesson: RawLesson): DailyChallengeSpec {
        val dayIndex = abs(LocalDate.now().toEpochDay().toInt()) % 3
        val topic = lesson.title
        return when (track) {
            TrackType.HTML -> {
                val lower = topic.lowercase()
                val chosen = when {
                    lower.contains("نموذج") || lower.contains("إدخال") || lower.contains("form") || lower.contains("input") -> listOf(
                        Triple("اكتب تسمية توضّح البيانات التي سيدخلها الزائر.", "تسمية الحقل", "حقل في نموذجي"),
                        Triple("اكتب مساعدة قصيرة تخبر الزائر بصيغة الإدخال المطلوبة.", "مساعدة الحقل", "توجيه للحقل"),
                        Triple("اكتب نص زر واضحًا يخبر الزائر بما سيحدث بعد الضغط.", "نص زر النموذج", "زر في النموذج")
                    )[dayIndex]
                    lower.contains("صور") || lower.contains("image") -> listOf(
                        Triple("اكتب وصفًا قصيرًا لصورة ستظهر في صفحتك.", "وصف الصورة", "وصف صورة"),
                        Triple("تخيّل صورة لمشروعك واكتب نصًا بديلًا يشرحها لمن لا يراها.", "النص البديل", "وصف بديل للصورة"),
                        Triple("اختر صورة لمشروعك واكتب تعليقًا صغيرًا تحتها.", "تعليق الصورة", "تعليق صورة")
                    )[dayIndex]
                    lower.contains("رابط") || lower.contains("روابط") || lower.contains("link") -> listOf(
                        Triple("اكتب نص رابط يوضّح للزائر ما الذي سيفتحه.", "نص الرابط", "رابط في صفحتي"),
                        Triple("اكتب اسم وجهة تريد أن يصل إليها زائر صفحتك.", "وجهة الرابط", "وجهة في صفحتي"),
                        Triple("اكتب دعوة قصيرة تشجّع الزائر على فتح أحد أقسامك.", "دعوة للرابط", "دعوة للرابط")
                    )[dayIndex]
                    else -> listOf(
                        Triple("اكتب عنوانًا قصيرًا لقسم واحد تريد إضافته إلى صفحتك.", "عنوان القسم", "قسم جديد"),
                        Triple("اكتب جملة واحدة تعرّف الزائر بما ستجده في صفحتك.", "نبذة قصيرة", "نبذة للصفحة"),
                        Triple("اكتب نص زر صغيرًا يدعو الزائر لاستكشاف صفحة مشروعك.", "نص زر", "زر في صفحتي")
                    )[dayIndex]
                }
                DailyChallengeSpec(
                    title = topic,
                    kind = "text",
                    course = track.key,
                    label = chosen.second,
                    prompt = chosen.first,
                    placeholder = "اكتب جملة قصيرة من اختيارك",
                    artifactLabel = chosen.third,
                    objective = "أضفت ${chosen.second} إلى صفحة مشروعك."
                )
            }
            TrackType.CSS -> {
                val prompts = listOf(
                    "غيّر اللون الأساسي للبطاقة إلى لون يعبّر عن مشروعك.",
                    "جرّب لونًا هادئًا للبطاقة وشاهد أثره على هويتها.",
                    "اختار لونًا جديدًا، ولاحظ كيف يغيّر إحساس البطاقة."
                )
                DailyChallengeSpec(
                    title = topic,
                    kind = "color",
                    course = track.key,
                    label = "لون البطاقة",
                    prompt = prompts[dayIndex],
                    placeholder = "",
                    artifactLabel = "لمسة لونية",
                    objective = "لون البطاقة اتغير واتحفظ في مشروعك."
                )
            }
            TrackType.JS -> {
                val lower = topic.lowercase()
                if (lower.contains("مصفوف") || lower.contains("حلق") || lower.contains("array") || lower.contains("loop")) {
                    val chosen = listOf(
                        Pair("أضف خطوة واحدة تحب تنفيذها في مشروعك.", "خطوة في القائمة"),
                        Pair("اكتب مهمة صغيرة ستقرّبك من إنهاء صفحتك.", "مهمة للمشروع"),
                        Pair("اختار تحسينًا واحدًا تريد تذكّره في قائمة مشروعك.", "تحسين قادم")
                    )[dayIndex]
                    DailyChallengeSpec(
                        title = topic,
                        kind = "text",
                        course = track.key,
                        label = chosen.second,
                        prompt = chosen.first,
                        placeholder = "مثال: أضيف قسم الصور",
                        artifactLabel = "خطوة في قائمة المهام",
                        objective = "أضف مهمة؛ ستظهر في قائمة مشروعك وتحفظ مع تقدمك.",
                        action = "todo"
                    )
                } else {
                    val chosen = listOf(
                        Pair("اكتب رسالة ترحيب تظهر للزائر عند تفاعله مع صفحتك.", "رسالة الترحيب"),
                        Pair("اكتب تأكيدًا قصيرًا يراه الزائر بعد حفظ تعديل.", "رسالة التأكيد"),
                        Pair("اكتب ردًا لطيفًا يظهر بعد ضغط الزائر على زر.", "رد الزر")
                    )[dayIndex]
                    DailyChallengeSpec(
                        title = topic,
                        kind = "text",
                        course = track.key,
                        label = chosen.second,
                        prompt = chosen.first,
                        placeholder = "مثال: أهلًا بك في صفحتي",
                        artifactLabel = "رسالة تفاعلية",
                        objective = "أضفت رسالة تفاعلية إلى مشروعك."
                    )
                }
            }
            else -> DailyChallengeSpec(
                title = topic,
                kind = "text",
                course = track.key,
                label = "ملخص تجربتك اليوم",
                prompt = "اكتب قاعدة أو ملاحظة صغيرة اكتشفتها اليوم في «$topic».",
                placeholder = "مثال: فهمت اليوم كيف نربط الخطوة بالنتيجة",
                artifactLabel = "تجربة يومية",
                objective = "أضف نتيجة صغيرة من الدرس إلى مشروعك."
            )
        }
    }

    companion object {
        val greetings = listOf(
            "لنر ما الخطوة التالية اليوم.",
            "بضع دقائق ممكن تضيف فكرة جديدة.",
            "أهلًا بعودتك. نكمل من حيث وقفنا؟",
            "في تجربة صغيرة مستنياك النهارده.",
            "اختار سؤالًا وسيب الباقي يتكشف.",
            "يمكننا استكشاف فكرة بسيطة اليوم؛ الخطوة تجربة ممتعة وليست واجبًا."
        )

        val phrases = listOf(
            "خطوة إضافية اتضافت لفهمك.",
            "تقدم صغير، لكنه حقيقي.",
            "كل تجربة بتوضح جزءًا جديدًا.",
            "أنت دلوقتي تعرف أكتر من قبل شوية.",
            "لبنة جديدة اتضافت للصورة.",
            "جرّب تغيّر حاجة وشوف اللي هيحصل.",
            "الكود بقى يتحرك خطوة لقدام.",
            "ممتاز، عندنا حاجة شغالة دلوقتي.",
            "تجربتك خلت المثال أوضح.",
            "كل خطوة بتقرب الصورة."
        )

        val skillCatalog = listOf(
            SkillDefinition("html", "الصفحة الأولى والوسوم الأساسية", 0, 4),
            SkillDefinition("html", "النصوص والروابط والقوائم", 5, 15),
            SkillDefinition("html", "الصور والجداول والعناصر الدلالية", 16, 27),
            SkillDefinition("html", "بنية الصفحة ووسائط التضمين", 28, 40),
            SkillDefinition("html", "النماذج وإدخال المستخدم", 41, 50),
            SkillDefinition("html", "الرسوم والوسائط الحديثة", 51, 54),

            SkillDefinition("css", "المحددات والألوان والخلفيات", 0, 10),
            SkillDefinition("css", "نموذج الصندوق والحدود والمسافات", 11, 18),
            SkillDefinition("css", "النصوص والخطوط والروابط والجداول", 19, 24),
            SkillDefinition("css", "العرض والتموضع والتخطيط المرن", 25, 34),
            SkillDefinition("css", "التدرجات والظلال والحركة والأزرار", 35, 63),
            SkillDefinition("css", "التخطيط المتجاوب والمتغيرات والشبكات", 64, 94),

            SkillDefinition("js", "أول سطر ومتغيرات وقواعد الأساس", 0, 11),
            SkillDefinition("js", "الشروط والحلقات والنصوص والأرقام", 12, 30),
            SkillDefinition("js", "الدوال والكائنات والمصفوفات", 31, 69),
            SkillDefinition("js", "المجموعات والمنطق وإدارة الأخطاء", 70, 98),
            SkillDefinition("js", "الـ DOM والأحداث والتخزين وواجهات الويب", 99, 119)
        )

        val abilityCatalog = listOf(
            AbilityDefinition(
                id = "output-panel",
                title = "لوحة عرض نتيجة",
                icon = "🖥️",
                project = "بطاقة نتيجة",
                tool = "أداة رسالة نتيجة",
                challenge = "أضف بطاقة تعرض رسالة من اختيارك",
                requires = listOf("html:2" to "أساسيات HTML", "js:2" to "المخرجات في JavaScript")
            ),
            AbilityDefinition(
                id = "personal-page",
                title = "صفحة شخصية كاملة",
                icon = "📄",
                project = "صفحتي الشخصية",
                tool = "منشئ قسم في صفحتي",
                challenge = "اكتب عنوانًا وفقرة لصفحتك في المحرر",
                requires = listOf("html:3" to "عناصر HTML", "css:6" to "ألوان CSS", "css:12" to "نموذج الصندوق")
            ),
            AbilityDefinition(
                id = "signup-form",
                title = "نموذج تسجيل بسيط",
                icon = "📝",
                project = "صفحة تسجيل",
                tool = "نموذج بيانات محلي",
                challenge = "اختبر نموذجًا يتحقق من إدخال الاسم محليًا",
                requires = listOf("html:41" to "النماذج في HTML", "html:44" to "أنواع الإدخال", "html:45" to "سمات الإدخال")
            ),
            AbilityDefinition(
                id = "photo-gallery",
                title = "معرض صور منظم",
                icon = "🖼️",
                project = "معرض أعمالي",
                tool = "منظم بطاقات الصور",
                challenge = "أنشئ شبكة من ٣ بطاقات صور",
                requires = listOf("html:16" to "الصور في HTML", "css:35" to "معرض الصور بـ CSS")
            ),
            AbilityDefinition(
                id = "calculator",
                title = "آلة حاسبة صغيرة",
                icon = "🧮",
                project = "أداة حسابية",
                tool = "حاسبة تفاعلية",
                challenge = "احسب مجموع رقمين من إدخالك",
                requires = listOf("js:3" to "قواعد JavaScript", "js:11" to "العمليات الحسابية")
            ),
            AbilityDefinition(
                id = "todo-list",
                title = "قائمة مهام محفوظة",
                icon = "✅",
                project = "قائمتي",
                tool = "قائمة مهام متصلة بالمشروع",
                challenge = "أضف ٣ مهام تتحدث في القائمة",
                requires = listOf(
                    "js:60" to "المصفوفات في JS",
                    "js:62" to "دوال المصفوفات",
                    "js:99" to "التعامل مع DOM",
                    "js:100" to "الأحداث في JS"
                )
            ),
            AbilityDefinition(
                id = "notes-app",
                title = "تطبيق ملاحظات محلي",
                icon = "🗒️",
                project = "مفكرتي",
                tool = "دفتر أفكار محفوظ",
                challenge = "احفظ فكرة صغيرة تبقى بعد التحديث",
                requires = listOf(
                    "js:44" to "الكائنات في JS",
                    "js:104" to "بيانات JSON",
                    "js:105" to "واجهات الويب"
                )
            ),
            AbilityDefinition(
                id = "visual-style",
                title = "هوية بصرية لبطاقتك",
                icon = "🎨",
                project = "تصميم المشروع",
                tool = "لوحة تلوين حية",
                challenge = "غيّر لون البطاقة ليتطابق مع مشروعك",
                requires = listOf("css:6" to "ألوان CSS", "css:12" to "نموذج الصندوق")
            )
        )

        private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

        fun ar(value: Any?): String {
            val s = value?.toString().orEmpty()
            val out = StringBuilder(s.length)
            for (ch in s) {
                if (ch in '0'..'9') {
                    out.append(ARABIC_DIGITS[ch - '0'])
                } else {
                    out.append(ch)
                }
            }
            return out.toString()
        }

        fun shortSnippet(text: String, max: Int = 42): String {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            return if (clean.length <= max) clean else clean.take((max - 1).coerceAtLeast(1)) + "…"
        }
    }
}
