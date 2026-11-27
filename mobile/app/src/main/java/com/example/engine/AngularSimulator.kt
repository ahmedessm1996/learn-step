package com.example.engine

import com.example.data.CurriculumRepository.Companion.shortSnippet
import com.example.data.PredictChallenge
import com.example.engine.PreviewHtmlBuilder.escapeHtml
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.asExpectedStrings

object AngularSimulator {

    private val ALLOWED_EXTENSIONS = listOf(".ts", ".html", ".css", ".json", ".md")

    fun isAllowedProjectPath(path: String): Boolean {
        val normalized = path.trim().replace("\\", "/").removePrefix("/")
        if (normalized.isEmpty() || normalized.contains("..") || normalized.startsWith("node_modules/")) return false
        return ALLOWED_EXTENSIONS.any { normalized.endsWith(it) }
    }

    fun normalizeWhitespace(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*([;:,{}()\\[\\]=<>])\\s*"), "$1")
            .trim()
    }

    fun supportedTask(lesson: RawLesson, files: Map<String, String>): Boolean {
        val checks = if (lesson.edits.isNotEmpty()) {
            lesson.edits.map { it.file to it.expected.asExpectedStrings() }
        } else {
            listOf(lesson.file to lesson.expected.asExpectedStrings())
        }
        return checks.all { (file, expectedList) ->
            if (expectedList.isEmpty()) true
            else {
                val content = files[file].orEmpty()
                expectedList.all { exp ->
                    if (content.contains(exp)) true
                    else {
                        val normExp = normalizeWhitespace(exp)
                        normExp.isEmpty() || normalizeWhitespace(content).contains(normExp)
                    }
                }
            }
        }
    }

    fun isStepCompleted(step: RawLessonStep?, lesson: RawLesson, files: Map<String, String>, quizSelected: Int?): Boolean {
        if (step == null || step.type == "observe") return true
        if (step.type == "quiz") return quizSelected != null && quizSelected == step.correct
        val targetFile = step.file?.takeIf { it.isNotBlank() } ?: lesson.file
        val expectedList = step.expected.asExpectedStrings().ifEmpty {
            listOfNotNull(step.code?.takeIf { it.isNotBlank() }).ifEmpty { lesson.expected.asExpectedStrings() }
        }
        if (expectedList.isEmpty() || targetFile.isBlank()) return true
        val content = files[targetFile].orEmpty()
        return expectedList.all { exp ->
            if (content.contains(exp)) true
            else {
                val normExp = normalizeWhitespace(exp)
                normExp.isEmpty() || normalizeWhitespace(content).contains(normExp)
            }
        }
    }

    fun buildPredictChallenge(lesson: RawLesson): PredictChallenge {
        val steps = lesson.steps
        val quizStep = steps.firstOrNull { it.type == "quiz" && it.options.size >= 2 }
        if (quizStep != null) {
            return PredictChallenge(
                prompt = quizStep.question ?: "توقع قبل التعديل: ماذا يضيف هذا الدرس للمشروع؟",
                snippet = lesson.code.ifBlank { lesson.flow }.lines().take(6).joinToString("\n"),
                options = quizStep.options.take(4),
                correctIndex = quizStep.correct ?: 0,
                revealNote = quizStep.explanation.ifBlank { lesson.concept }
            )
        }
        val firstEdit = steps.firstOrNull { it.type != "observe" && (it.expected != null || !it.code.isNullOrBlank()) }
        val snippet = firstEdit?.expected.asExpectedStrings().firstOrNull()
            ?: firstEdit?.code
            ?: lesson.code
        return PredictChallenge(
            prompt = "توقع قبل التعديل: عند إضافة أو تحديث «${lesson.title}»، ماذا يتغيّر في تطبيق السينما؟",
            snippet = snippet.lines().take(6).joinToString("\n"),
            options = listOf(
                shortSnippet(lesson.summary.ifBlank { lesson.concept }, 110),
                "لن يتغيّر أي شيء في المعاينة الحية أو البيانات",
                "سيحذف باقي ملفات المشروع تلقائيًا"
            ),
            correctIndex = 0,
            revealNote = lesson.concept.ifBlank { lesson.summary }
        )
    }

    fun inspectGentleTree(code: String, activePath: String): GentleTreeAlert? {
        if (activePath.endsWith(".html")) {
            val openSig = Regex("\\{\\{\\s*([a-zA-Z0-9_$]+)\\s*\\}\\}").find(code)
            if (openSig != null && code.contains("signal") && !code.contains("()")) {
                return GentleTreeAlert(
                    icon = "📡",
                    heading = "تذكير Signals في القالب",
                    diagram = "{{ ${openSig.groupValues[1]}() }}  ← استدعِ الإشارة كدالة",
                    targetLine = "{{ ${openSig.groupValues[1]}() }}",
                    message = "في Angular Signals نستدعي الإشارة بأقواس () داخل القالب لقراءة قيمتها التفاعلية."
                )
            }
            if (code.contains("@for") && !code.contains("track ")) {
                return GentleTreeAlert(
                    icon = "⚡",
                    heading = "شجرة التحكم الحديثة (@for)",
                    diagram = "@for (item of items; track item.id) { ... }",
                    targetLine = "track item.id",
                    message = "تتطلب حلقة @for في Angular 22 تعبير track لتحديد هوية كل عنصر بكفاءة."
                )
            }
        }
        if (activePath.endsWith(".ts")) {
            if (Regex("signal\\(").containsMatchIn(code) && !Regex("@Component|@Injectable").containsMatchIn(code) && !code.contains("import")) {
                return GentleTreeAlert(
                    icon = "📘",
                    heading = "بنية وحدة TypeScript / Angular",
                    diagram = "import { signal } from '@angular/core';",
                    targetLine = "import { signal } from '@angular/core';",
                    message = "لا تنسَ استيراد signal من @angular/core عند الحاجة لبناء حالة تفاعلية."
                )
            }
        }
        return null
    }

    fun buildSlotModel(step: RawLessonStep?, lesson: RawLesson, isDone: Boolean): SlotBuilderModel? {
        if (step == null || isDone || step.type == "observe" || step.type == "quiz") return null
        val snippet = step.expected.asExpectedStrings().firstOrNull()
            ?: step.code?.takeIf { it.isNotBlank() }
            ?: lesson.expected.asExpectedStrings().firstOrNull()
            ?: lesson.code
        if (snippet.isBlank()) return null
        val targetFile = step.file ?: lesson.file
        return SlotBuilderModel(
            stage1Location = "داخل $targetFile",
            stage2Tool = "بنية Angular / TS",
            tagOpen = "",
            tagClose = "",
            initialValue = snippet.lines().first().trim(),
            isEdit = false
        )
    }

    fun mutateAngularSpark(code: String, path: String, stepIndex: Int): String {
        val mod = ((stepIndex % 3) + 3) % 3
        return when {
            path.endsWith(".html") -> {
                val badges = listOf(
                    "\n<div class=\"final-ticket-card\"><h3>✨ تجربة حرة</h3><p>عدّلت قالب السينما بنفسي!</p></div>",
                    "\n<p class=\"badge discount\">خصم تجربة حرة ٢٠٪ على العرض المسائي</p>",
                    "\n<div class=\"ticket-summary\"><span>تذاكر العرض الخاص</span><span>٢ × ٨٥ ر.س</span></div>"
                )
                code.replace(Regex("\\n<div class=\"(final-ticket-card|ticket-summary)\">[\\s\\S]*$"), "") + badges[mod]
            }
            path.endsWith(".css") -> {
                val styles = listOf(
                    "\n/* tinker */\n.cinema-shell { border-color: #bbf268; }",
                    "\n/* tinker */\n.cinema-heading h1 { color: #fde047; }",
                    "\n/* tinker */\n.btn-book { background: #1d4ed8; }"
                )
                code.replace(Regex("\\n/\\* tinker \\*/[\\s\\S]*$"), "") + styles[mod]
            }
            else -> {
                val logs = listOf(
                    "\n// تجربة حرة\nconsole.log('سينما خطوة جاهزة للتفاعل ✓');",
                    "\n// تجربة حرة\nexport const demoSeatCount: number = 4;",
                    "\n// تجربة حرة\nconsole.log('إشارة حية جديدة تعمل بنجاح!');"
                )
                code.replace(Regex("\\n// تجربة حرة[\\s\\S]*$"), "") + logs[mod]
            }
        }
    }

    fun renderPreviewDoc(
        lesson: RawLesson?,
        activePath: String,
        files: Map<String, String>
    ): String {
        val isTsPhase = lesson != null && (
            lesson.phase.contains("TypeScript", ignoreCase = true) ||
                lesson.id.startsWith("ng-ts-") ||
                lesson.module.contains("typescript", ignoreCase = true)
            )
        val isPureTsFile = activePath.endsWith(".ts") && (
            activePath.contains("/models/") ||
                activePath.contains("/services/") ||
                activePath.contains("/validators/") ||
                activePath.contains("/guards/") ||
                activePath.contains("/interceptors/") ||
                activePath.contains("/store/") ||
                activePath.endsWith("types.ts") ||
                activePath.endsWith("pricing.utils.ts") ||
                (!files.containsKey(activePath.replace(Regex("\\.ts$"), ".html")) &&
                    activePath != "src/app/app.ts" && activePath != "src/main.ts")
            )
        val isEditingHtml = files.containsKey("src/app/app.html") && (!files.containsKey(activePath) || !activePath.endsWith(".ts") || activePath == "src/app/app.ts")
        if ((isTsPhase && !isEditingHtml) || isPureTsFile) {
            val tsCode = files[activePath] ?: lesson?.file?.let { files[it] } ?: files["src/app/app.ts"].orEmpty()
            return renderTypeScriptPreviewDoc(tsCode, activePath)
        }
        return renderAngularTemplateDoc(activePath, files)
    }

    fun renderTypeScriptPreviewDoc(code: String, filePath: String): String {
        val fileName = filePath.substringAfterLast('/')
        val interfaces = Regex("(?:export\\s+)?interface\\s+([a-zA-Z0-9_]+(?:\\s*<[^>]+>)?)(?:\\s+extends\\s+[^{]+)?\\s*\\{([\\s\\S]*?)\\}")
            .findAll(code).map { m ->
                val fields = m.groupValues[2].lines()
                    .map { it.trim().removeSuffix(";") }
                    .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") }
                    .map { line ->
                        val colon = line.indexOf(':')
                        if (colon > 0) line.substring(0, colon).trim() to line.substring(colon + 1).trim()
                        else line to "any"
                    }
                m.groupValues[1] to fields
            }.toList()

        val types = Regex("(?:export\\s+)?type\\s+([a-zA-Z0-9_]+)\\s*=\\s*([^;]+);")
            .findAll(code).map { it.groupValues[1] to it.groupValues[2].trim() }.toList()

        val variables = Regex("(?:export\\s+)?(?:const|let|var|readonly)\\s+([a-zA-Z0-9_\$]+)(?:\\s*:\\s*([^=;]+))?\\s*=\\s*([^;]+);")
            .findAll(code).map { m ->
                Triple(
                    m.groupValues[1],
                    m.groupValues[2].trim().ifBlank { "تلقائي (Inferred)" },
                    m.groupValues[3].trim()
                )
            }.toList()

        val classes = Regex("(?:export\\s+)?class\\s+([a-zA-Z0-9_]+)").findAll(code).map { it.groupValues[1] }.toList()
        val functions = Regex("(?:export\\s+)?function\\s+([a-zA-Z0-9_]+)\\s*((?:<[^>]+>\\s*)?\\([^)]*\\)(?:\\s*:\\s*[^{]+)?)")
            .findAll(code).map { it.groupValues[1] to it.groupValues[2].trim() }.toList()
        val enums = Regex("(?:export\\s+)?enum\\s+([a-zA-Z0-9_]+)\\s*\\{([\\s\\S]*?)\\}")
            .findAll(code).map { m ->
                val vals = m.groupValues[2].split(",").map { it.trim().removeSuffix(";") }.filter { it.isNotEmpty() && !it.startsWith("//") }
                m.groupValues[1] to vals
            }.toList()

        val varMap = variables.associate { it.first to it.third }
        val logs = Regex("console\\.log\\s*\\(([\\s\\S]*?)\\);?").findAll(code).map { m ->
            val arg = m.groupValues[1].trim()
            varMap[arg] ?: arg
        }.toList()

        val body = StringBuilder()
        body.append("<div class=\"ts-container\"><div class=\"ts-header\"><div class=\"ts-header-title\"><span>🟦</span><span>محاكي وفاحص TypeScript</span></div><span class=\"ts-badge-file\">${escapeHtml(fileName)}</span></div>")

        if (interfaces.isEmpty() && types.isEmpty() && variables.isEmpty() && classes.isEmpty() && functions.isEmpty() && enums.isEmpty() && logs.isEmpty()) {
            body.append("<div class=\"ts-card ts-empty-state\"><p>اكتب واجهات أو أنواع أو متغيرات أو دوال في المحرر، وستظهر مخرجاتها وهيكلتها هنا لحظيًا!</p></div>")
        }
        if (interfaces.isNotEmpty()) {
            body.append("<div class=\"ts-card\"><h3>📋 الواجهات البرمجية (Interfaces)</h3>")
            interfaces.forEach { (name, fields) ->
                body.append("<b>interface ${escapeHtml(name)}</b><table class=\"ts-table\"><thead><tr><th>الحقل</th><th>النوع</th></tr></thead><tbody>")
                fields.forEach { (k, v) ->
                    body.append("<tr><td>${escapeHtml(k)}</td><td><span class=\"ts-pill-type\">${escapeHtml(v)}</span></td></tr>")
                }
                body.append("</tbody></table>")
            }
            body.append("</div>")
        }
        if (types.isNotEmpty()) {
            body.append("<div class=\"ts-card\"><h3>🏷️ الأنواع المستعارة (Type Aliases)</h3><table class=\"ts-table\">")
            types.forEach { (n, d) ->
                body.append("<tr><td>${escapeHtml(n)}</td><td><span class=\"ts-pill-type\">${escapeHtml(d)}</span></td></tr>")
            }
            body.append("</table></div>")
        }
        if (variables.isNotEmpty()) {
            body.append("<div class=\"ts-card\"><h3>📦 المتغيرات والقيم المصرّح بها</h3><table class=\"ts-table\"><thead><tr><th>المتغير</th><th>النوع</th><th>القيمة</th></tr></thead><tbody>")
            variables.forEach { (n, t, v) ->
                body.append("<tr><td><span class=\"ts-pill-var\">${escapeHtml(n)}</span></td><td><span class=\"ts-pill-type\">${escapeHtml(t)}</span></td><td><span class=\"ts-pill-val\">${escapeHtml(v)}</span></td></tr>")
            }
            body.append("</tbody></table></div>")
        }
        if (classes.isNotEmpty() || functions.isNotEmpty() || enums.isNotEmpty()) {
            body.append("<div class=\"ts-card\"><h3>⚙️ الأصناف والدوال والتعدادات</h3><div style=\"display:flex;flex-wrap:wrap;gap:8px\">")
            classes.forEach { body.append("<span class=\"ts-pill-type\">class ${escapeHtml(it)}</span>") }
            functions.forEach { (n, sig) -> body.append("<span class=\"ts-pill-type\">function ${escapeHtml(n)}${escapeHtml(sig)}</span>") }
            enums.forEach { (n, vals) -> body.append("<span class=\"ts-pill-type\">enum ${escapeHtml(n)} { ${escapeHtml(vals.joinToString(", "))} }</span>") }
            body.append("</div></div>")
        }
        body.append("<div class=\"ts-card\"><h3>💻 شاشة الكونسول (Console)</h3><div class=\"ts-console-box\"><div class=\"ts-console-content\">")
        if (logs.isEmpty()) {
            body.append("<div class=\"ts-empty-hint\">لم يُستدعَ console.log() بعد..</div>")
        } else {
            logs.forEach { body.append("<div class=\"ts-log-line\"><span>&gt;</span><span>${escapeHtml(it)}</span></div>") }
        }
        body.append("</div></div></div></div>")

        return """
            <!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{margin:0;padding:14px;font-family:sans-serif;background:#0c1813;color:#e2ede6;direction:rtl}
              .ts-header{display:flex;align-items:center;justify-content:space-between;padding:10px 14px;background:#13271f;border-radius:10px;border:1px solid #234435;margin-bottom:12px}
              .ts-header-title{display:flex;align-items:center;gap:8px;font-weight:bold;color:#6ee7b7}
              .ts-badge-file{background:#1f3b2f;color:#a7f3d0;padding:3px 8px;border-radius:6px;font-size:12px;font-family:monospace;direction:ltr}
              .ts-card{background:#12231b;border:1px solid #1f3a2d;border-radius:10px;padding:12px;margin-bottom:12px}
              .ts-card h3{margin:0 0 8px;font-size:14px;color:#93c5fd}
              .ts-table{width:100%;border-collapse:collapse;font-size:13px}
              .ts-table th{text-align:right;color:#9ca3af;padding:5px;border-bottom:1px solid #234435}
              .ts-table td{padding:6px 5px;border-bottom:1px solid #1a3327}
              .ts-pill-type{display:inline-block;padding:2px 7px;border-radius:5px;font-size:11px;font-family:monospace;background:#1e3a5f;color:#93c5fd;direction:ltr}
              .ts-pill-var{display:inline-block;padding:2px 7px;border-radius:5px;font-size:11px;font-family:monospace;background:#1b4332;color:#a7f3d0;direction:ltr}
              .ts-pill-val{color:#fde047;font-family:monospace;direction:ltr}
              .ts-console-box{background:#070e0b;border:1px solid #1f3b2d;border-radius:8px;padding:10px;font-family:monospace;font-size:13px;direction:ltr;text-align:left}
              .ts-log-line{color:#a7f3d0;display:flex;gap:8px;margin-bottom:3px}
              .ts-empty-hint{color:#8ea89a;font-size:12px;direction:rtl;text-align:right}
            </style></head><body>$body</body></html>
        """.trimIndent()
    }

    private fun renderAngularTemplateDoc(activePath: String, files: Map<String, String>): String {
        val appHtml = files["src/app/app.html"] ?: "<main class='cinema-shell'><h1>{{ title }}</h1></main>"
        val appCss = files["src/app/app.css"].orEmpty()
        val stylesCss = files["src/styles.css"].orEmpty()
        val combinedTs = (files["src/app/app.ts"].orEmpty() + "\n" +
            (if (activePath.endsWith(".ts")) files[activePath].orEmpty() else ""))

        val scope = linkedMapOf<String, Any?>(
            "title" to "سينما النجوم",
            "cinemaName" to "سينما النجوم",
            "description" to "بوابتك لأروع عروض السينما الحصرية",
            "ticketCount" to 1,
            "totalPrice" to 85,
            "customerName" to "ضيف السينما",
            "isVip" to false,
            "isAvailable" to true,
            "isSoldOut" to false,
            "hasDiscount" to true,
            "bookingCount" to 0,
            "bookingStatus" to "CONFIRMED",
            "movies" to listOf(
                mapOf("id" to 1, "title" to "رحلة النجوم", "time" to "18:00", "genre" to "خيال علمي", "rating" to 4.8, "price" to 85),
                mapOf("id" to 2, "title" to "واحة الأمل", "time" to "21:00", "genre" to "دراما", "rating" to 4.6, "price" to 75)
            ),
            "seats" to listOf(
                mapOf("id" to "A1", "tier" to "standard", "booked" to false),
                mapOf("id" to "A2", "tier" to "vip", "booked" to true),
                mapOf("id" to "A3", "tier" to "standard", "booked" to false)
            )
        )

        Regex("(?:readonly\\s+|public\\s+|private\\s+)?([a-zA-Z0-9_\$]+)\\s*(?::\\s*[^=;]+)?\\s*=\\s*(['\"][^'\"]*['\"]|\\d+(?:\\.\\d+)?|true|false);")
            .findAll(combinedTs).forEach { m ->
                val k = m.groupValues[1]
                val raw = m.groupValues[2].trim()
                scope[k] = parseScalar(raw)
            }

        Regex("(?:readonly\\s+|public\\s+|private\\s+)?([a-zA-Z0-9_\$]+)\\s*=\\s*(?:signal|model|input)(?:\\.required)?(?:<[^>]+>)?\\(([^)]*)\\)")
            .findAll(combinedTs).forEach { m ->
                val k = m.groupValues[1]
                val raw = m.groupValues[2].trim()
                if (raw.isNotEmpty() && !raw.startsWith("[")) scope[k] = parseScalar(raw)
            }

        var html = appHtml
        html = evalClassicDirectives(html, scope)
        html = evalIfBlocks(html, scope)
        html = evalForBlocks(html, scope)
        html = evalSwitchBlocks(html, scope)
        html = html.replace(Regex("@defer\\s*\\{([\\s\\S]*?)\\}(?:\\s*@placeholder(?:\\([^)]*\\))?\\s*\\{[\\s\\S]*?\\})?(?:\\s*@loading(?:\\([^)]*\\))?\\s*\\{[\\s\\S]*?\\})?"), "$1")

        html = html.replace(Regex("\\{\\{\\s*([a-zA-Z0-9_\$]+)\\.([a-zA-Z0-9_\$]+)\\s*\\}\\}")) { m ->
            val obj = scope[m.groupValues[1]] as? Map<*, *>
            escapeHtml(obj?.get(m.groupValues[2])?.toString().orEmpty())
        }
        html = html.replace(Regex("\\{\\{\\s*([a-zA-Z0-9_\$]+)(?:\\(\\))?\\s*\\}\\}")) { m ->
            escapeHtml(scope[m.groupValues[1]]?.toString().orEmpty())
        }

        if (combinedTs.contains("seats = signal") && !html.contains("seat-picker-grid")) {
            @Suppress("UNCHECKED_CAST")
            val seats = scope["seats"] as? List<Map<String, Any?>> ?: emptyList()
            val seatBtns = seats.joinToString("") { s ->
                val booked = s["booked"] == true
                "<button type=\"button\" class=\"cinema-seat ${if (booked) "booked" else "available"}\">${escapeHtml(s["id"].toString())}</button>"
            }
            html += "<div class=\"seat-picker-section\"><b>قاعة العرض · اختيار المقاعد:</b><div class=\"seat-picker-grid\">$seatBtns</div></div>"
        }

        return """
            <!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{margin:0;padding:14px;font-family:Arial,sans-serif;background:#0f1c16;color:#e4ece7;direction:rtl}
              .cinema-shell{max-width:580px;margin:0 auto;background:#162720;border-radius:12px;padding:16px;border:1px solid #233e31}
              .cinema-heading{display:flex;align-items:center;gap:12px;margin-bottom:14px}
              .cinema-mark{width:38px;height:38px;border-radius:9px;background:#357a53;color:#fff;font-weight:bold;font-size:20px;display:grid;place-items:center}
              .cinema-heading h1{margin:0;font-size:22px;color:#8ee2a8}
              .welcome-card{margin-top:12px;padding:14px;border:1px solid #2d503f;border-radius:12px;background:#1a3026}
              .badge{display:inline-block;padding:4px 10px;border-radius:6px;font-size:12px;font-weight:bold;margin-top:6px}
              .badge.discount{background:#ffe082;color:#5d4037}
              .badge.normal{background:#264639;color:#b7d5c5}
              .movie-pill{display:inline-flex;flex-direction:column;padding:8px 12px;background:#1e352b;border:1px solid #2f5443;border-radius:8px;margin:4px}
              .seat-picker-grid{display:flex;gap:8px;margin-top:8px}
              .cinema-seat{padding:8px 12px;border-radius:7px;border:1px solid #2f5443;background:#234735;color:#fff;font-weight:bold}
              .cinema-seat.booked{background:#5a2525;opacity:.7}
              .final-ticket-card{margin-top:14px;padding:12px;border:1px dashed #d4af37;background:#212c20;border-radius:10px}
              .ticket-summary{display:flex;justify-content:space-between;padding:10px;background:#1d332a;border-radius:8px;margin:10px 0}
              $stylesCss
              $appCss
            </style></head><body>$html</body></html>
        """.trimIndent()
    }

    private fun parseScalar(raw: String): Any? {
        return when {
            (raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\"")) -> raw.substring(1, raw.length - 1)
            raw == "true" -> true
            raw == "false" -> false
            else -> raw.toIntOrNull() ?: raw.toDoubleOrNull() ?: raw
        }
    }

    private fun evalCond(cond: String, scope: Map<String, Any?>): Boolean {
        val clean = cond.substringBefore(';').trim().replace("()", "")
        if (clean == "true") return true
        if (clean == "false") return false
        if (clean.startsWith("!")) return !evalCond(clean.drop(1).trim(), scope)
        val eqMatch = Regex("^([a-zA-Z0-9_.\$]+)\\s*(===?|!==?)\\s*(['\"][^'\"]*['\"]|\\d+|true|false)$").matchEntire(clean)
        if (eqMatch != null) {
            val lVal = scope[eqMatch.groupValues[1]]?.toString()
            val op = eqMatch.groupValues[2]
            val rVal = parseScalar(eqMatch.groupValues[3])?.toString()
            return if (op.startsWith("!")) lVal != rVal else lVal == rVal
        }
        val numMatch = Regex("^([a-zA-Z0-9_.\$]+)\\s*([><]=?)\\s*(\\d+(?:\\.\\d+)?)$").matchEntire(clean)
        if (numMatch != null) {
            val lNum = (scope[numMatch.groupValues[1]] as? Number)?.toDouble() ?: 0.0
            val rNum = numMatch.groupValues[3].toDoubleOrNull() ?: 0.0
            return when (numMatch.groupValues[2]) {
                ">" -> lNum > rNum; ">=" -> lNum >= rNum; "<" -> lNum < rNum; "<=" -> lNum <= rNum; else -> false
            }
        }
        val v = scope[clean] ?: return false
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotBlank()
            is Collection<*> -> v.isNotEmpty()
            else -> true
        }
    }

    private fun evalClassicDirectives(html: String, scope: Map<String, Any?>): String {
        var out = html
        out = out.replace(Regex("<([a-zA-Z0-9-]+)([^>]*?)\\*ngIf\\s*=\\s*[\"']([^\"']+)[\"']([^>]*?)>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { m ->
            if (evalCond(m.groupValues[3], scope)) "<${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[4]}>${m.groupValues[5]}</${m.groupValues[1]}>" else ""
        }
        out = out.replace(Regex("<([a-zA-Z0-9-]+)([^>]*?)\\*ngFor\\s*=\\s*[\"']let\\s+([a-zA-Z0-9_\$]+)\\s+of\\s+([a-zA-Z0-9_.\$]+)[^\"']*[\"']([^>]*?)>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { m ->
            val tag = m.groupValues[1]
            val itemVar = m.groupValues[3]
            val collName = m.groupValues[4]
            val inner = m.groupValues[6]
            val coll = (scope[collName] as? List<*>) ?: listOf("عنصر ١", "عنصر ٢")
            coll.joinToString("\n") { item ->
                var itemHtml = inner
                if (item is Map<*, *>) {
                    item.forEach { (k, v) ->
                        itemHtml = itemHtml.replace(Regex("\\{\\{\\s*$itemVar\\.$k\\s*\\}\\}"), escapeHtml(v?.toString().orEmpty()))
                    }
                }
                itemHtml = itemHtml.replace(Regex("\\{\\{\\s*$itemVar\\s*\\}\\}"), escapeHtml(item?.toString().orEmpty()))
                "<$tag${m.groupValues[2]}${m.groupValues[5]}>$itemHtml</$tag>"
            }
        }
        return out
    }

    private fun evalIfBlocks(html: String, scope: Map<String, Any?>): String {
        var res = html
        val regex = Regex("@if\\s*\\(([^)]+)\\)\\s*\\{([\\s\\S]*?)\\}(?:\\s*@else\\s*\\{([\\s\\S]*?)\\})?")
        while (true) {
            val m = regex.find(res) ?: break
            val replacement = if (evalCond(m.groupValues[1], scope)) m.groupValues[2] else m.groupValues[3]
            res = res.replaceRange(m.range, replacement)
        }
        return res
    }

    private fun evalForBlocks(html: String, scope: Map<String, Any?>): String {
        var res = html
        val regex = Regex("@for\\s*\\(([a-zA-Z0-9_\$]+)\\s+of\\s+([a-zA-Z0-9_.\$()]+)[^)]*\\)\\s*\\{([\\s\\S]*?)\\}(?:\\s*@empty\\s*\\{([\\s\\S]*?)\\})?")
        while (true) {
            val m = regex.find(res) ?: break
            val itemVar = m.groupValues[1]
            val collName = m.groupValues[2].replace("()", "").trim()
            val body = m.groupValues[3]
            val emptyBody = m.groupValues[4]
            val list = (scope[collName] as? List<*>)
                ?: if (collName.contains("seat")) (scope["seats"] as? List<*>)
                else if (collName.contains("movie")) (scope["movies"] as? List<*>)
                else listOf(mapOf("id" to 1, "name" to "عنصر ١", "title" to "عنصر ١"))
            val rendered = if (!list.isNullOrEmpty()) {
                list.joinToString("") { item ->
                    var itemHtml = body
                    if (item is Map<*, *>) {
                        item.forEach { (k, v) ->
                            itemHtml = itemHtml.replace(Regex("\\{\\{\\s*$itemVar\\.$k\\s*\\}\\}"), escapeHtml(v?.toString().orEmpty()))
                        }
                    }
                    itemHtml.replace(Regex("\\{\\{\\s*$itemVar\\s*\\}\\}"), escapeHtml(item?.toString().orEmpty()))
                }
            } else {
                emptyBody.ifBlank { "<p>لا توجد عناصر لعرضها حاليًا.</p>" }
            }
            res = res.replaceRange(m.range, rendered)
        }
        return res
    }

    private fun evalSwitchBlocks(html: String, scope: Map<String, Any?>): String {
        var res = html
        val regex = Regex("@switch\\s*\\(([^)]+)\\)\\s*\\{([\\s\\S]*?)\\}")
        while (true) {
            val m = regex.find(res) ?: break
            val key = m.groupValues[1].replace("()", "").trim()
            val actual = scope[key]?.toString() ?: key
            val body = m.groupValues[2]
            val cases = Regex("@case\\s*\\(([^)]+)\\)\\s*\\{([\\s\\S]*?)\\}").findAll(body)
            val matchedCase = cases.firstOrNull { c ->
                val cv = parseScalar(c.groupValues[1].trim())?.toString()
                cv == actual
            }?.groupValues?.get(2)
            val defaultCase = Regex("@default\\s*\\{([\\s\\S]*?)\\}").find(body)?.groupValues?.get(1).orEmpty()
            res = res.replaceRange(m.range, matchedCase ?: defaultCase)
        }
        return res
    }
}
