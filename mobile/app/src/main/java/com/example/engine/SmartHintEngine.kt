package com.example.engine

import com.example.data.CurriculumRepository.Companion.ar
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.TrackType
import com.example.model.asExpectedStrings
import com.example.model.hintTextAt
import kotlin.math.min

data class HintEvaluationResult(
    val valid: Boolean,
    val passed: Boolean,
    val message: String,
    val hintText: String,
    val tier: Int,
    val ruleMatched: Boolean,
    val solutionRevealable: Boolean
)

data class HtmlTagIssue(
    val type: String,
    val tag: String,
    val message: String
)

data class HtmlTagCheckResult(
    val valid: Boolean,
    val errors: List<HtmlTagIssue>
)

data class BuildStageSpec(
    val index: Int,
    val code: String,
    val added: String,
    val title: String,
    val objective: String,
    val benefit: String
)

data class GentleTreeAlert(
    val icon: String,
    val heading: String,
    val diagram: String,
    val targetLine: String,
    val message: String
)

data class SlotBuilderModel(
    val stage1Location: String,
    val stage2Tool: String,
    val tagOpen: String,
    val tagClose: String,
    val initialValue: String,
    val isEdit: Boolean
)

object SmartHintEngine {

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr", "!doctype"
    )

    fun normalizeArabic(text: String): String {
        return text
            .replace("\u0623", "\u0627")
            .replace("\u0625", "\u0627")
            .replace("\u0622", "\u0627")
            .replace("\u0629", "\u0647")
            .replace("\u0649", "\u064a")
            .replace(Regex("[\\u064b-\\u065f\\u0670]"), "")
    }

    fun normalizeCodeLine(value: String): String {
        return normalizeArabic(value)
            .replace("'", "\"")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*=\\s*"), "=")
            .replace(Regex("\\s*:\\s*"), ":")
            .replace(Regex("\\s*;\\s*"), ";")
            .trim()
    }

    fun codeLines(value: String): List<String> {
        return value.replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { normalizeCodeLine(it) }
            .filter { it.isNotEmpty() }
    }

    fun normalizeDoc(value: String): String {
        return normalizeArabic(value)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("'", "\"")
            .replace(Regex("\\s+"), " ")
            .replace(Regex(">\\s+<"), "><")
            .replace(Regex("\\s*=\\s*"), "=")
            .replace(Regex("\\s*:\\s*"), ":")
            .replace(Regex("\\s*;\\s*"), ";")
            .trim()
    }

    fun checkHtmlTags(code: String): HtmlTagCheckResult {
        val errors = mutableListOf<HtmlTagIssue>()
        val clean = code.replace(Regex("<!--([\\s\\S]*?)-->"), "")
        val tagRegex = Regex("<(/?)([a-zA-Z!][a-zA-Z0-9-]*)([^>]*?)(/?)>")
        val stack = mutableListOf<String>()

        for (match in tagRegex.findAll(clean)) {
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val selfClosing = match.groupValues[4] == "/" || tagName in VOID_TAGS
            if (tagName.startsWith("!")) continue

            if (!isClosing) {
                if (!selfClosing) {
                    stack += tagName
                }
            } else {
                if (stack.isEmpty()) {
                    errors += HtmlTagIssue(
                        type = "orphan_close",
                        tag = tagName,
                        message = "وسم الإغلاق </$tagName> ظهر دون وسم فتح <$tagName> قبله."
                    )
                } else {
                    val last = stack.last()
                    if (last == tagName) {
                        stack.removeAt(stack.lastIndex)
                    } else {
                        errors += HtmlTagIssue(
                            type = "mismatch",
                            tag = tagName,
                            message = "اسم وسم الإغلاق </$tagName> لا يطابق وسم البداية <$last>. اجعل وسم الإغلاق </$last> ليطابق وسم البداية."
                        )
                        val idx = stack.lastIndexOf(tagName)
                        if (idx >= 0) {
                            stack.subList(idx, stack.size).clear()
                        }
                    }
                }
            }
        }

        if (stack.isNotEmpty()) {
            val unclosed = stack.last()
            errors += HtmlTagIssue(
                type = "unclosed",
                tag = unclosed,
                message = "وسم <$unclosed> تم فتحه ولم يُغلق. أضف </$unclosed> بعد المحتوى."
            )
        }

        return HtmlTagCheckResult(valid = errors.isEmpty(), errors = errors)
    }

    fun missingBuildLine(userCode: String, stage: BuildStageSpec): String {
        val actual = codeLines(userCode)
        val required = codeLines(stage.added)
        return required.firstOrNull { line -> !actual.contains(line) }.orEmpty()
    }

    fun buildPracticeStepsForLesson(track: TrackType, lesson: RawLesson): List<RawLessonStep> {
        val explicitEdits = lesson.steps.filter { it.type == "edit" && (it.to.isNotBlank() || it.expected != null) }
        if (explicitEdits.isNotEmpty()) {
            return explicitEdits.mapIndexed { idx, step ->
                val expectedList = step.expected.asExpectedStrings()
                val beforeCode = step.starterCode ?: if (idx == 0) lesson.code else explicitEdits[idx - 1].solution ?: lesson.code
                val targetTo = step.to.ifBlank { expectedList.firstOrNull().orEmpty() }
                val afterCode = step.solution ?: when {
                    step.from.isNotBlank() && targetTo.isNotBlank() -> beforeCode.replace(step.from, targetTo)
                    expectedList.isNotEmpty() -> expectedList.first()
                    else -> lesson.expected.asExpectedStrings().firstOrNull() ?: lesson.code
                }
                step.copy(
                    id = step.id.ifBlank { "edit-$idx" },
                    starterCode = beforeCode,
                    solution = afterCode,
                    to = targetTo
                )
            }
        }
        val expectedStr = lesson.expected.asExpectedStrings().firstOrNull()
        if (!expectedStr.isNullOrBlank() && expectedStr != lesson.code) {
            return listOf(
                RawLessonStep(
                    id = "edit-0",
                    type = "edit",
                    title = "عدّل المثال",
                    explanation = lesson.task.ifBlank { "نفّذ التعديل المطلوب في المثال وشغّل لتشاهد النتيجة." },
                    task = lesson.task,
                    benefit = "أضفت تعديلك بنجاح ولاحظت أثره مباشرة.",
                    starterCode = lesson.code,
                    solution = expectedStr,
                    to = expectedStr
                )
            )
        }
        return emptyList()
    }

    fun buildAllSteps(track: TrackType, lesson: RawLesson): List<RawLessonStep> {
        val steps = mutableListOf<RawLessonStep>()

        // 1. Idea / observe step
        val firstObserve = lesson.steps.firstOrNull { it.type == "observe" }
        steps += RawLessonStep(
            id = "idea",
            type = "observe",
            title = firstObserve?.title?.ifBlank { "الفكرة" } ?: "الفكرة",
            objective = lesson.summary.ifBlank { lesson.concept },
            explanation = lesson.concept.ifBlank { lesson.summary },
            flow = lesson.flow,
            task = firstObserve?.task?.ifBlank { "تأمل الفكرة ولاحظ كيف يتكوّن المثال قبل التعديل." }
                ?: "تأمل الفكرة ولاحظ كيف يتكوّن المثال قبل التعديل.",
            benefit = firstObserve?.benefit?.ifBlank { "اتضحت الصورة العامة لفكرة الدرس." }
                ?: "اتضحت الصورة العامة لفكرة الدرس."
        )

        // 2. Detail / breakdown steps
        if (lesson.breakdown.isNotEmpty()) {
            lesson.breakdown.forEachIndexed { idx, item ->
                steps += RawLessonStep(
                    id = "detail-$idx",
                    type = "detail",
                    title = item.title.ifBlank { "تفصيلة ${ar(idx + 1)}" },
                    code = item.code.takeIf { it.isNotBlank() },
                    explanation = item.meaning.ifBlank { item.benefit },
                    flow = item.code,
                    task = "لاحظ دور «${item.code.ifBlank { item.title }}» في المثال أمامك، ثم شغّل.",
                    benefit = item.benefit.ifBlank { item.meaning }
                )
            }
        }

        // 3. Quiz step (if lesson has quiz)
        lesson.quiz?.let { quiz ->
            if (quiz.options.isNotEmpty()) {
                steps += RawLessonStep(
                    id = "quiz",
                    type = "quiz",
                    title = "تحقق سريع",
                    question = quiz.question,
                    options = quiz.options,
                    correct = quiz.correct,
                    benefit = "ربطت القاعدة النظرية بالسطر الفعلي في الكود."
                )
            }
        }

        // 4. Guided Edit steps
        val edits = buildPracticeStepsForLesson(track, lesson)
        edits.forEachIndexed { idx, step ->
            steps += step.copy(id = "edit-$idx", type = "edit")
        }

        // 5. Personal experiment step
        steps += RawLessonStep(
            id = "experiment",
            type = "experiment",
            title = "لمستك الخاصة",
            explanation = "التعديل نجح! الآن جرّب قيمة أو نصًا مختلفًا من اختيارك وشغّل لتشاهد أثرك الشخصي.",
            task = "غيّر قيمة أو نصًا آخر من اختيارك داخل المحرر ثم شغّل.",
            benefit = "أصبحت الفكرة ملكًا لك عبر تجربتك الشخصية."
        )

        // 6. Bug discovery & repair steps (if bugRepair is present)
        val bug = lesson.bugRepair
        if (bug != null && bug.brokenCode.isNotBlank()) {
            if (bug.options.isNotEmpty()) {
                steps += RawLessonStep(
                    id = "diagnose",
                    type = "diagnose",
                    title = "اكتشف الخطأ",
                    question = bug.question,
                    options = bug.options,
                    correctOptionIndex = bug.correctOptionIndex,
                    code = bug.brokenCode,
                    explanation = bug.explanation
                )
            }
            if (bug.fixedCode.isNotBlank()) {
                steps += RawLessonStep(
                    id = "repair",
                    type = "repair",
                    title = "أصلح الخطأ",
                    task = when (track) {
                        TrackType.HTML -> "طابق اسم وسم الإغلاق أو البنية في السطر المعطوب، ثم شغّل."
                        TrackType.CSS -> "صحّح اسم الخاصية أو القيمة في السطر المعطوب، ثم شغّل."
                        TrackType.DATABASE -> "صحّح صياغة الاستعلام أو الشرط في السطر المعطوب، ثم شغّل."
                        else -> "أصلح السطر المعطوب ليعمل الكود بنجاح، ثم شغّل."
                    },
                    starterCode = bug.brokenCode,
                    solution = bug.fixedCode,
                    benefit = "أصبح الخطأ تجربة مفيدة تثبّت القاعدة."
                )
            }
        }

        // 7. Project contribution step
        steps += RawLessonStep(
            id = "project",
            type = "project",
            title = "ربطه بمشروعي",
            explanation = "أضف ما أنجزته في هذا الدرس كلبنة دائمة في مشروعك الشخصي.",
            task = "اضغط زر إضافة اللبنة لإغلاق الدرس وحفظه في مشروعك.",
            benefit = "أضيفت لبنة جديدة إلى مشروعك الشخصي."
        )

        return steps
    }

    fun evaluate(
        userCode: String,
        step: RawLessonStep?,
        track: TrackType,
        lesson: RawLesson,
        practiceSteps: List<RawLessonStep>,
        experimentBaseCode: String?,
        attemptCount: Int
    ): HintEvaluationResult {
        val rawCode = userCode.replace("\r\n", "\n").replace("\r", "\n")
        if (step == null) {
            return HintEvaluationResult(true, true, "", "", 1, false, false)
        }

        val attempts = attemptCount.coerceAtLeast(1)
        val tier = min(4, attempts)
        val solutionRevealable = attempts >= 3

        // Detail step check
        if (step.id.startsWith("detail-") && !step.code.isNullOrBlank()) {
            val reqToken = step.code.trim()
            val tagMatch = Regex("^<([a-zA-Z][a-zA-Z0-9-]*)").find(reqToken)
            val attrMatch = Regex("^([a-zA-Z_:][a-zA-Z0-9_.:-]*)\\s*=").find(reqToken)
            val missingDetail = when {
                tagMatch != null -> {
                    val tagName = tagMatch.groupValues[1].lowercase()
                    !Regex("<$tagName(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(rawCode)
                }
                attrMatch != null -> {
                    val attrName = Regex.escape(attrMatch.groupValues[1].lowercase())
                    !Regex("\\b$attrName\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(rawCode)
                }
                reqToken.length > 1 && !reqToken.contains("...") -> {
                    !rawCode.contains(reqToken, ignoreCase = true)
                }
                else -> false
            }

            if (missingDetail) {
                val msg = when {
                    tier == 2 -> "تلميح: تأكد من وجود «$reqToken» داخل الكود في المحرر ثم شغّل."
                    tier >= 3 -> "مساعدة: المطلوب كتابة أو تضمين «$reqToken» في المحرر قبل الانتقال."
                    else -> "لم تكتمل الخطوة بعد. ينقصك إضافة: $reqToken"
                }
                return HintEvaluationResult(false, false, msg, msg, tier, true, solutionRevealable)
            }
        }

        // Edit step check
        if (step.type == "edit") {
            val rawAfter = (step.solution ?: "").replace("\r\n", "\n").replace("\r", "\n")
            val rawBefore = (step.starterCode ?: "").replace("\r\n", "\n").replace("\r", "\n")

            if (rawBefore.isNotBlank() &&
                (rawCode.trim() == rawBefore.trim() || normalizeDoc(rawCode) == normalizeDoc(rawBefore))
            ) {
                val msg = when (tier) {
                    1 -> "راجع التغيير المطلوب في بطاقة الخطوة. غيّره وحده ثم شغّل من جديد."
                    2 -> "قاعدة: الكود لم يتغير عن الحالة السابقة. هل استبدلت القيمة المطلوبة؟"
                    3 -> "مساعدة: التعديل المطلوب: غيّر «${step.from.ifBlank { rawBefore }}» إلى «${step.to.ifBlank { rawAfter }}»."
                    else -> "كشف الحل: اكتب القيمة المطلوبة:\n${rawAfter.ifBlank { step.to }}"
                }
                return HintEvaluationResult(false, false, msg, msg, tier, false, solutionRevealable)
            }

            if (rawAfter.isNotBlank() &&
                (rawCode.trim() == rawAfter.trim() || normalizeDoc(rawCode) == normalizeDoc(rawAfter))
            ) {
                return HintEvaluationResult(true, true, "", "", tier, false, false)
            }

            val expectedTargets = (listOf(step.to) + step.expected.asExpectedStrings() + listOf(step.expectedLine))
                .filter { it.isNotBlank() }

            for (target in expectedTargets) {
                val normTo = normalizeCodeLine(target)
                val normFrom = if (step.from.isNotBlank()) normalizeCodeLine(step.from) else ""
                val normCode = normalizeCodeLine(rawCode)
                val normBefore = normalizeCodeLine(rawBefore)

                val toInCode = countOccurrences(normCode, normTo)
                val toInBefore = countOccurrences(normBefore, normTo)
                val fromInCode = if (normFrom.isNotEmpty()) countOccurrences(normCode, normFrom) else 0
                val fromInBefore = if (normFrom.isNotEmpty()) countOccurrences(normBefore, normFrom) else 0

                val toIncreased = toInCode > toInBefore
                val fromReduced = normFrom.isEmpty() || fromInBefore == 0 || fromInCode < fromInBefore

                if (toIncreased && fromReduced) {
                    return HintEvaluationResult(true, true, "", "", tier, false, false)
                }
            }

            if (track == TrackType.HTML) {
                val tagCheck = checkHtmlTags(rawCode)
                if (!tagCheck.valid) {
                    val err = tagCheck.errors.first().message
                    val tiered = when (tier) {
                        1 -> err
                        2 -> "قاعدة: وسوم HTML يجب أن تتطابق أسماؤها في الفتح والإغلاق. $err"
                        3 -> "مساعدة: $err - تفحص السطر بعناية وصحح اسم الوسم."
                        else -> "كشف الحل للوسم: $err"
                    }
                    return HintEvaluationResult(false, false, tiered, tiered, tier, true, solutionRevealable)
                }
            }

            for (rule in step.errorRules) {
                if (rule.pattern.isBlank()) continue
                val regex = runCatching {
                    val options = mutableSetOf<RegexOption>()
                    if ('i' in rule.flags) options += RegexOption.IGNORE_CASE
                    if ('m' in rule.flags) options += RegexOption.MULTILINE
                    Regex(rule.pattern, options)
                }.getOrNull() ?: continue

                val isMissing = rule.type == "missing" || rule.invert
                val matched = if (isMissing) !regex.containsMatchIn(rawCode) else regex.containsMatchIn(rawCode)
                if (matched) {
                    val rMsg = when (tier) {
                        1 -> rule.tier1 ?: "تنبيه: ${rule.message}"
                        2 -> rule.tier2 ?: "قاعدة الخطوة: ${rule.message}"
                        3 -> rule.tier3 ?: "مساعدة محددة: ${rule.message}"
                        else -> rule.tier4 ?: rule.solution?.let { "كشف الحل: $it" } ?: "كشف الحل: ${rule.message}"
                    }
                    return HintEvaluationResult(false, false, rMsg, rMsg, tier, true, solutionRevealable)
                }
            }

            val customHint = step.hintTextAt(tier)
            if (customHint.isNotBlank()) {
                return HintEvaluationResult(false, false, customHint, customHint, tier, false, solutionRevealable)
            }

            val fallback = when (tier) {
                1 -> "راجع التغيير المطلوب في بطاقة الخطوة. غيّره وحده ثم شغّل من جديد."
                2 -> "قاعدة: المطلوب هو ${step.task.ifBlank { step.objective.ifBlank { "تعديل الكود المعروض" } }}. تحقق من خلو السطر من الأخطاء الإملائية."
                3 -> "مساعدة: الجزء المطلوب وجوده: «${step.to.ifBlank { rawAfter }}»."
                else -> "كشف الحل: اكتب:\n${rawAfter.ifBlank { step.to }}"
            }
            return HintEvaluationResult(false, false, fallback, fallback, tier, false, solutionRevealable)
        }

        // Experiment step check
        if (step.type == "experiment") {
            val basePractice = (practiceSteps.lastOrNull()?.solution ?: lesson.code)
                .replace("\r\n", "\n").replace("\r", "\n")
            val modelCode = lesson.code.replace("\r\n", "\n").replace("\r", "\n")
            val expCode = (experimentBaseCode ?: basePractice).replace("\r\n", "\n").replace("\r", "\n")

            if (rawCode == expCode || normalizeDoc(rawCode) == normalizeDoc(expCode) ||
                normalizeDoc(rawCode) == normalizeDoc(modelCode)
            ) {
                val expMsg = when (tier) {
                    1 -> "غيّر جزءًا آخر من اختيارك ثم شغّل لتشاهد أثر فكرتك."
                    2 -> "تلميح: جرب تعديل قيمة مثل النص أو اللون أو الأبعاد وشاهد ما يتغير في المعاينة."
                    3 -> "مساعدة: بدّل خاصية واحدة أو قيمة داخل الكود لتثبت استيعابك للمفهوم."
                    else -> "كشف خيار مقترح: جرّب تغيير النص التوضيحي أو القيمة الرقمية في المثال."
                }
                return HintEvaluationResult(false, false, expMsg, expMsg, tier, false, solutionRevealable)
            }

            if (track == TrackType.HTML) {
                val tagCheck = checkHtmlTags(rawCode)
                if (!tagCheck.valid) {
                    val err = tagCheck.errors.first().message
                    return HintEvaluationResult(false, false, err, err, tier, true, solutionRevealable)
                }
            }
            return HintEvaluationResult(true, true, "", "", tier, false, false)
        }

        // Repair step check
        if (step.type == "repair") {
            val fixedCode = (lesson.bugRepair?.fixedCode ?: step.solution ?: "")
                .replace("\r\n", "\n").replace("\r", "\n")
            val brokenCode = (lesson.bugRepair?.brokenCode ?: step.starterCode ?: "")
                .replace("\r\n", "\n").replace("\r", "\n")

            if (fixedCode.isNotBlank() &&
                (rawCode.trim() == fixedCode.trim() || normalizeDoc(rawCode) == normalizeDoc(fixedCode))
            ) {
                return HintEvaluationResult(true, true, "", "", tier, false, false)
            }

            if (track == TrackType.HTML) {
                if (Regex("<p\\b[^>]*>[^<]*</span>", RegexOption.IGNORE_CASE).containsMatchIn(rawCode)) {
                    val spanMsg = when (tier) {
                        1 -> "اسم وسم الإغلاق </span> لا يطابق وسم البداية <p>. اجعل وسم الإغلاق </p> ليطابق وسم البداية."
                        2 -> "قاعدة: لا يمكن إغلاق عنصر <p> بوسم </span>. بدّل كلمة span بـ p."
                        3 -> "مساعدة: غيّر </span> إلى </p> في نهاية السطر."
                        else -> "كشف الحل: <p>جزء جديد</p>"
                    }
                    return HintEvaluationResult(false, false, spanMsg, spanMsg, tier, true, solutionRevealable)
                }
                val tagCheck = checkHtmlTags(rawCode)
                if (!tagCheck.valid) {
                    val err = tagCheck.errors.first().message
                    return HintEvaluationResult(false, false, err, err, tier, true, solutionRevealable)
                }
                val brokenPresent = brokenCode.isNotBlank() &&
                    (normalizeCodeLine(rawCode).contains(normalizeCodeLine(brokenCode)) ||
                        normalizeDoc(rawCode).contains(normalizeDoc(brokenCode)))
                val fixedPresent = fixedCode.isNotBlank() &&
                    (normalizeCodeLine(rawCode).contains(normalizeCodeLine(fixedCode)) ||
                        normalizeDoc(rawCode).contains(normalizeDoc(fixedCode)))
                if (fixedPresent && !brokenPresent) {
                    return HintEvaluationResult(true, true, "", "", tier, false, false)
                }
            }

            if (track == TrackType.CSS &&
                Regex("color\\s*:\\s*#a65d32", RegexOption.IGNORE_CASE).containsMatchIn(rawCode) &&
                !Regex("colr\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(rawCode)
            ) {
                return HintEvaluationResult(true, true, "", "", tier, false, false)
            }

            if (track == TrackType.JS &&
                normalizeCodeLine(rawCode).contains("console.log(\"وصلت هنا\")")
            ) {
                return HintEvaluationResult(true, true, "", "", tier, false, false)
            }

            val repMsg = when (tier) {
                1 -> "راجع السطر الذي اكتشفت خطأه، ثم أصلحه وشغّل مرة أخرى."
                2 -> "قاعدة الخطأ: ${lesson.bugRepair?.question ?: "دقق في السطر غير الصحيح وأصلح الوسم أو الخاصية."}"
                3 -> "مساعدة: الخطأ يقع في: ${brokenCode.lines().lastOrNull().orEmpty()}"
                else -> "كشف الحل للتصليح: الكود الصحيح هو:\n${fixedCode.lines().lastOrNull().orEmpty()}"
            }
            return HintEvaluationResult(false, false, repMsg, repMsg, tier, false, solutionRevealable)
        }

        return HintEvaluationResult(true, true, "", "", tier, false, false)
    }

    fun inspectGentleDomTree(code: String, track: TrackType): GentleTreeAlert? {
        when (track) {
            TrackType.HTML -> {
                val bodyOpen = code.indexOf("<body", ignoreCase = true)
                val bodyClose = code.indexOf("</body", ignoreCase = true)
                if (bodyOpen >= 0 && bodyClose > bodyOpen) {
                    val beforeBody = code.substring(0, bodyOpen)
                    val afterBody = code.substring((bodyClose + 7).coerceAtMost(code.length))
                    val visibleTagRegex = Regex("<(h[1-6]|p|button|a|img|ul|ol|div|span)\\b[^>]*>", RegexOption.IGNORE_CASE)
                    val m = visibleTagRegex.find(beforeBody) ?: visibleTagRegex.find(afterBody)
                    if (m != null) {
                        val tagName = m.groupValues[1].lowercase()
                        return GentleTreeAlert(
                            icon = "🧭",
                            heading = "خريطة بنية الصفحة (DOM Tree)",
                            diagram = "html\n ├── head (الإعدادات والعنوان غير المرئي)\n └── body (المساحة المرئية لكل ما يشاهده الزائر)\n      └──",
                            targetLine = "<$tagName> مكان العنصر الدافئ هنا </$tagName>",
                            message = "العنصر <$tagName> يبدو أنه يبحث عن مكانه داخل الصفحة.. ما رأيك أن ندعوه ليعيش داخل <body> ليراه زوارك بكل وضوح؟"
                        )
                    }
                }
                val tagsToCheck = listOf("h1", "h2", "h3", "p", "button", "a")
                for (tag in tagsToCheck) {
                    val opens = Regex("<$tag\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(code).count()
                    val closes = Regex("</$tag>", RegexOption.IGNORE_CASE).findAll(code).count()
                    if (opens > closes) {
                        return GentleTreeAlert(
                            icon = "🌱",
                            heading = "اكتمال بنية الوسم",
                            diagram = "<$tag> محتوى العنصر </$tag>\n  └──",
                            targetLine = "لا تنسَ وسم الإغلاق </$tag> ليطمئن المتصفح لنهاية الوسم",
                            message = "الوسم <$tag> يحتاج إلى رفيقه </$tag> ليغلق الباب بعد انتهاء المحتوى بنجاح."
                        )
                    }
                }
            }
            TrackType.CSS -> {
                val stripped = code.replace(Regex("\\{[^{}]*\\}"), "")
                if (Regex("[a-zA-Z-]+\\s*:\\s*[^;]+;?").containsMatchIn(stripped)) {
                    return GentleTreeAlert(
                        icon = "🎨",
                        heading = "خريطة بنية التنسيق (CSS Rules)",
                        diagram = "المحدد (Selector) {\n  └──",
                        targetLine = "الخاصية: القيمة;  ← مكانها هنا بين قوسي المحدد",
                        message = "الخاصية تبحث عن بيتها داخل قوسي المحدد { } لتترك لمستك الأنيقة على العنصر المطلوب."
                    )
                }
            }
            TrackType.JS -> {
                val logMatch = Regex("console\\.log\\(\\s*([a-zA-Z\\u0600-\\u06FF]+)\\s*\\)").find(code)
                if (logMatch != null) {
                    val id = logMatch.groupValues[1]
                    if (!Regex("^(true|false|null|undefined|\\d+)$").matches(id) &&
                        !Regex("\\b(let|const|var|function)\\s+$id\\b").containsMatchIn(code)
                    ) {
                        return GentleTreeAlert(
                            icon = "💡",
                            heading = "خريطة بنية النص (JavaScript Syntax)",
                            diagram = "console.log(\"$id\");\n            └──",
                            targetLine = "علامتا التنصيص تحميان الكلمات لتظهر كنص ودّي",
                            message = "الكلمة «$id» تبحث عن علامتي تنصيص \" \" ليعرف JavaScript أنها رسالة واضحة وليست متغيرًا غير معرّف."
                        )
                    }
                }
            }
            else -> Unit
        }
        return null
    }

    fun buildSlotModel(step: RawLessonStep?, track: TrackType, isDone: Boolean): SlotBuilderModel? {
        if (step == null || isDone || step.type in setOf("quiz", "diagnose", "project", "observe")) return null
        val isEdit = step.type == "edit"
        val snippet = when {
            isEdit -> step.to.ifBlank { step.expectedLine }
            !step.code.isNullOrBlank() -> step.code.lines().first().trim()
            else -> ""
        }
        if (snippet.isBlank()) return null

        val stage1 = when (track) {
            TrackType.HTML -> "داخل <body> ليظهر للزوار"
            TrackType.CSS -> "داخل قوسي المحدد { }"
            TrackType.DATABASE -> "داخل استعلام SQL نشط"
            else -> "في السطر المناسب من البرنامج"
        }

        var stage2 = ""
        var tagOpen = ""
        var tagClose = ""
        var initialValue = ""

        if (isEdit) {
            stage2 = "تعديل موجه لضبط القيمة"
            initialValue = snippet
        } else if (track == TrackType.HTML) {
            val m = Regex("^(<([a-zA-Z0-9]+)[^>]*>)([\\s\\S]*?)(</\\2>)$").find(snippet)
            if (m != null) {
                tagOpen = m.groupValues[1]
                tagClose = m.groupValues[4]
                initialValue = m.groupValues[3].ifBlank { "اكتب النص هنا" }
                stage2 = "وسم <${m.groupValues[2]}>"
            } else {
                val mSingle = Regex("^<([a-zA-Z0-9]+)[^>]*>$").find(snippet)
                if (mSingle != null) {
                    tagOpen = snippet
                    stage2 = "وسم <${mSingle.groupValues[1]}>"
                } else {
                    stage2 = "لبنة HTML"
                    initialValue = snippet
                }
            }
        } else if (track == TrackType.CSS) {
            val mCss = Regex("^([a-zA-Z0-9-]+)\\s*:\\s*([^;]+);?$").find(snippet)
            if (mCss != null) {
                stage2 = "خاصية ${mCss.groupValues[1]}"
                tagOpen = "${mCss.groupValues[1]}: "
                tagClose = ";"
                initialValue = mCss.groupValues[2].trim()
            } else {
                stage2 = "قاعدة CSS"
                initialValue = snippet
            }
        } else {
            val mJs = Regex("^(console\\.log\\()([\"']?)([\\s\\S]*?)\\2(\\);?)$").find(snippet)
            if (mJs != null) {
                stage2 = "دالة console.log"
                tagOpen = "console.log(\""
                tagClose = "\");"
                initialValue = mJs.groupValues[3].ifBlank { "أهلاً بك" }
            } else {
                stage2 = "تعليمة برمجية"
                initialValue = snippet
            }
        }

        return SlotBuilderModel(
            stage1Location = stage1,
            stage2Tool = stage2,
            tagOpen = tagOpen,
            tagClose = tagClose,
            initialValue = initialValue,
            isEdit = isEdit
        )
    }

    fun applySlotToCode(
        currentCode: String,
        model: SlotBuilderModel,
        step: RawLessonStep,
        track: TrackType,
        customVal: String
    ): String {
        val assembled = if (model.tagOpen.isNotEmpty() || model.tagClose.isNotEmpty()) {
            model.tagOpen + customVal + model.tagClose
        } else {
            customVal
        }
        return if (model.isEdit) {
            if (step.from.isNotBlank() && currentCode.contains(step.from)) {
                currentCode.replaceFirst(step.from, assembled)
            } else {
                step.solution?.takeIf { it.isNotBlank() } ?: currentCode
            }
        } else {
            if (track == TrackType.HTML && currentCode.contains("</body>", ignoreCase = true)) {
                currentCode.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "  $assembled\n</body>")
            } else {
                if (currentCode.isNotBlank()) "$currentCode\n$assembled" else assembled
            }
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty() || haystack.isEmpty()) return 0
        var count = 0
        var pos = 0
        while (true) {
            val idx = haystack.indexOf(needle, pos)
            if (idx < 0) break
            count++
            pos = idx + needle.length
        }
        return count
    }
}
