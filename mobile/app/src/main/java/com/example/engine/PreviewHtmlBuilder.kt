package com.example.engine

import com.example.data.CurriculumRepository
import com.example.data.CurriculumRepository.Companion.ar
import com.example.model.AbilityDefinition
import com.example.model.GrowthState
import com.example.model.ProfileCardColor
import com.example.model.ProfileCardState
import com.example.model.ProjectPart
import com.example.model.TrackType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PreviewHtmlBuilder {

    fun buildTrackPreview(track: TrackType, code: String): String {
        return when (track) {
            TrackType.HTML -> buildHtmlPreview(code)
            TrackType.CSS -> buildCssPreview(code)
            TrackType.JS -> buildJavascriptPreview(code)
            TrackType.DATABASE -> buildDatabaseFallbackHtml(code)
            TrackType.CSHARP -> buildHtmlPreview("<pre>${escapeHtml(code)}</pre>")
            TrackType.ANGULAR -> code
        }
    }

    fun buildHtmlPreview(code: String): String {
        val trimmed = code.trim()
        if (Regex("<html[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return trimmed
        }
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <style>
                body { font: 16px/1.6 Arial, sans-serif; padding: 14px; color: #19372e; margin: 0; background: #ffffff; }
                h1, h2, h3 { color: #1b5e43; margin-top: 0; }
                img { max-width: 100%; height: auto; border-radius: 8px; }
                table { border-collapse: collapse; width: 100%; margin: 8px 0; }
                th, td { border: 1px solid #c9dac6; padding: 6px 10px; text-align: right; }
                th { background: #eef5e9; color: #256b57; }
                form { display: grid; gap: 8px; max-width: 340px; }
                input, select, textarea, button { font: inherit; padding: 7px 10px; border-radius: 7px; border: 1px solid #bccbb8; }
                button { background: #256b57; color: #fff; border: 0; font-weight: bold; }
              </style>
            </head>
            <body>
            $code
            </body>
            </html>
        """.trimIndent()
    }

    fun buildCssPreview(cssCode: String): String {
        val sampleSvg = "data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22180%22 height=%22120%22%3E%3Crect width=%22180%22 height=%22120%22 fill=%22%23d7ebd0%22/%3E%3Ccircle cx=%2290%22 cy=%2260%22 r=%2240%22 fill=%22%238cb275%22/%3E%3C/svg%3E"
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <style>
                body{font:16px Arial,sans-serif;padding:12px;margin:0;color:#234b3e;background:#fff}
                .card{background:#eef5e9;border:1px solid #c9dac6;border-radius:14px;padding:14px}
                .card h1{color:#256b57;font-size:18px;margin:0 0 6px}
                .card p{margin:0 0 8px}
                .card a{color:#256b57}
                .tiles,.gallery,.nav{display:flex;gap:6px;margin-top:8px;flex-wrap:wrap}
                .tiles span{display:grid;place-items:center;width:30px;height:30px;border-radius:6px;background:#dfeeda}
                .art{display:block;width:90px;height:60px;object-fit:cover;margin-top:9px;border-radius:8px}
                .card ul{padding-inline-start:22px}
                .card table{border-collapse:collapse;margin:8px 0}
                .card td{border:1px solid #bccbb8;padding:4px}
                .card input,.card button,.card select{font:inherit;padding:5px;border:1px solid #bccbb8;border-radius:6px}
                $cssCode
              </style>
            </head>
            <body>
              <div class="card">
                <h1>بطاقتي الصغيرة</h1>
                <p id="result">كل قاعدة بتغيّر إحساس الصفحة.</p>
                <nav class="nav"><a href="#result">الرئيسية</a><a href="#result">عن الصفحة</a></nav>
                <a href="#result" title="رابط للتجربة">رابط تجريبي</a>
                <ul><li>قراءة</li><li>تصوير</li></ul>
                <table><tr><td>اليوم</td><td>الاثنين</td></tr><tr><td>الوقت</td><td>صباحًا</td></tr></table>
                <form>
                  <input aria-label="اسمك" placeholder="اسمك">
                  <select aria-label="اختر نشاطًا"><option>قراءة</option><option>تصوير</option></select>
                  <button type="button">حفظ</button>
                </form>
                <div class="tiles"><span>١</span><span>٢</span><span>٣</span></div>
                <div class="gallery">
                  <img class="art" alt="شكل تجريبي" src="$sampleSvg">
                  <img class="art" alt="نسخة ثانية من الشكل" src="$sampleSvg">
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildJavascriptPreview(jsCode: String): String {
        val safeCode = Json.encodeToString(jsCode).replace("<", "\\u003c")
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <style>
                body{font:16px Arial,sans-serif;padding:12px;color:#234b3e;margin:0;background:#fff}
                h1{font-size:16px;margin:0 0 8px}
                button{padding:7px 10px;border:0;border-radius:7px;background:#e8f2ec;color:#256b57;cursor:pointer;font-weight:bold}
                #output{margin-top:9px;padding:8px;border-radius:7px;background:#f2f5ee;white-space:pre-wrap;font:14px/1.6 monospace;direction:ltr;text-align:left}
                #output.error{background:#fff0f0;color:#8d2020}
              </style>
            </head>
            <body>
              <h1 id="result">نتيجة تشغيل السطر</h1>
              <button id="change" type="button">جرّب الزر</button>
              <canvas width="180" height="70" style="display:block;margin-top:8px;border:1px solid #dfe8dc;border-radius:7px"></canvas>
              <pre id="output" aria-live="polite"></pre>
              <script>
                const out = document.querySelector('#output');
                const originalLog = console.log.bind(console);
                const originalError = console.error.bind(console);
                console.log = (...args) => {
                  out.textContent += args.map(v => typeof v === 'object' ? JSON.stringify(v) : String(v)).join(' ') + '\n';
                  originalLog(...args);
                };
                function reportError(message) {
                  const text = String(message || 'خطأ غير معروف');
                  out.classList.add('error');
                  out.textContent += 'خطأ: ' + text + '\n';
                }
                console.error = (...args) => { originalError(...args); reportError(args.map(String).join(' ')); };
                window.addEventListener('error', e => reportError(e.message || 'خطأ في صياغة JavaScript'));
                window.addEventListener('unhandledrejection', e => { e.preventDefault(); reportError(e.reason && e.reason.message || e.reason || 'رفض طلب غير معالج'); });
                const userCode = $safeCode;
                try { new Function(userCode)(); } catch (error) { reportError(error && error.message || error); }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildDatabaseFallbackHtml(query: String): String {
        return buildHtmlPreview("<div style='padding:12px;background:#f6f9f4;border-radius:8px'><b>استعلام SQL</b><pre dir='ltr'>${escapeHtml(query)}</pre></div>")
    }

    fun buildTrackSnapshotDoc(track: TrackType, code: String): String {
        return buildTrackPreview(track, code)
    }

    fun mutateCodeSpark(code: String, track: TrackType, stepIndex: Int): String {
        val mod = ((stepIndex % 3) + 3) % 3
        return when (track) {
            TrackType.CSS -> {
                val palettes = listOf(
                    "\n/* لمسة تجربة حرة */\n.card { background: linear-gradient(135deg, #1b4332, #2d6a4f); color: #f1faee; border-radius: 20px; box-shadow: 0 10px 24px rgba(0,0,0,0.22); }\n.card h1 { color: #bbf268; letter-spacing: 0.5px; }",
                    "\n/* لمسة تجربة حرة */\n.card { background: #fff7ed; border: 2px dashed #ea580c; border-radius: 18px; transform: rotate(-1deg); }\n.card h1 { color: #9a3412; font-size: 22px; }",
                    "\n/* لمسة تجربة حرة */\n.card { background: #eff6ff; border-right: 6px solid #2563eb; padding: 18px; }\n.card h1 { color: #1e3a8a; }"
                )
                code.replace(Regex("\\n/\\* لمسة تجربة حرة \\*/[\\s\\S]*$"), "") + palettes[mod]
            }
            TrackType.JS -> {
                val jsSparks = listOf(
                    "\n// تجربة حرة سريعة\ndocument.querySelector('h1').textContent = '✨ عدّلت النتيجة بنفسي!';\nconsole.log('نتيجة التجربة الحرة: كل خطوة تضيف فهمًا جديدًا');",
                    "\n// تجربة حرة سريعة\ndocument.querySelector('h1').style.color = '#1b6b4a';\nconsole.log('جرّبت تغيير لون العنوان من JavaScript!');",
                    "\n// تجربة حرة سريعة\nconst items = ['فكرة', 'تجربة', 'نتيجة'];\nconsole.log('خطواتي:', items.join(' ← '));"
                )
                code.replace(Regex("\\n// تجربة حرة سريعة[\\s\\S]*$"), "") + jsSparks[mod]
            }
            TrackType.DATABASE -> {
                listOf(
                    "SELECT name, price FROM products ORDER BY price DESC;",
                    "SELECT customer_name, total_orders FROM orders;",
                    "SELECT id, name, salary FROM employees;"
                )[mod]
            }
            TrackType.CSHARP -> {
                val csSparks = listOf(
                    "\n// تجربة حرة\nConsole.WriteLine(\"تجربة ذكية: أضفت طباعة جديدة!\");",
                    "\n// تجربة حرة\nint bonus = 25;\nConsole.WriteLine(\"المكافأة: \" + bonus);",
                    "\n// تجربة حرة\nConsole.WriteLine(\"التتبع يعمل لحظة بلحظة ✓\");"
                )
                code.replace(Regex("\\n// تجربة حرة[\\s\\S]*$"), "") + csSparks[mod]
            }
            else -> {
                val htmlBadges = listOf(
                    "<div style=\"margin-top:10px;padding:10px;border-radius:10px;background:#e8f5e9;color:#1b5e20;font-weight:bold\">✨ أضفت هذا الصندوق في المعمل الحر!</div>",
                    "<button type=\"button\" onclick=\"this.textContent='تم التفاعل ✓'\" style=\"margin-top:10px;padding:8px 14px;border:0;border-radius:8px;background:#1f6f50;color:#fff\">اضغط وجرّبني</button>",
                    "<blockquote style=\"margin-top:10px;padding:8px 12px;border-right:4px solid #2bb07f;background:#f4faf6\">فكرة صغيرة ← نتيجة مرئية فورًا</blockquote>"
                )
                val cleaned = code.replace(Regex("\\s*<!-- tinker-spark -->[\\s\\S]*?<!-- /tinker-spark -->"), "")
                val injection = "  <!-- tinker-spark -->${htmlBadges[mod]}<!-- /tinker-spark -->"
                if (Regex("</body>", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) {
                    cleaned.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$injection\n</body>")
                } else {
                    if (cleaned.isNotBlank()) "$cleaned\n$injection" else injection
                }
            }
        }
    }

    fun isAbilityUnlocked(ability: AbilityDefinition, completedIds: Collection<String>): Boolean {
        return ability.requires.all { (reqId, _) -> completedIds.contains(reqId) }
    }

    fun buildProfileHtmlWithParts(
        card: ProfileCardState,
        growth: GrowthState,
        completedIds: Collection<String>
    ): String {
        val theme = ProfileCardColor.fromKey(card.color)
        val avatarHtml = if (card.imageDataUri.isNotBlank()) {
            "<img class=\"avatar\" src=\"${escapeHtml(card.imageDataUri)}\" alt=\"صورة ${escapeHtml(card.name.ifBlank { "صاحب البطاقة" })}\">"
        } else {
            "<span class=\"avatar initials\">${escapeHtml(card.initials())}</span>"
        }

        val latest = growth.projectParts.lastOrNull()
        val latestHtml = if (latest != null) {
            """
            <section class="latest-build">
              <small>أحدث ما أضفته لمشروعي</small>
              <b>${escapeHtml(latest.label)}</b>
              ${if (latest.note.isNotBlank()) "<p>${escapeHtml(latest.note)}</p>" else ""}
            </section>
            """.trimIndent()
        } else ""

        val partsListHtml = if (growth.projectParts.isNotEmpty()) {
            val items = growth.projectParts.takeLast(12).reversed().joinToString("") { part ->
                val codeBlock = if (part.code.isNotBlank()) {
                    "<details><summary>عرض الكود</summary><pre dir=\"ltr\">${escapeHtml(part.code)}</pre></details>"
                } else ""
                val noteBlock = if (part.note.isNotBlank()) {
                    "<p class=\"project-artifact-note\">${escapeHtml(part.note)}</p>"
                } else ""
                "<li class=\"project-artifact\"><b>${escapeHtml(part.label)}</b>${if (part.objective.isNotBlank()) "<p>${escapeHtml(part.objective)}</p>" else ""}$noteBlock$codeBlock</li>"
            }
            "<details class=\"learning\"><summary>لبنات مشروعي · ${ar(growth.projectParts.size)} محفوظة</summary><ul>$items</ul></details>"
        } else ""

        val unlockedAbilities = CurriculumRepository.abilityCatalog.filter { isAbilityUnlocked(it, completedIds) }
        val modulesHtml = unlockedAbilities.joinToString("") { ability ->
            when (ability.id) {
                "output-panel" -> """<section class="feature-module"><h4>بطاقة نتيجة</h4><p>رسالتي المحفوظة:</p><p class="module-status">${escapeHtml(growth.projectData.outputMessage)}</p></section>"""
                "personal-page" -> """<section class="feature-module"><h4>${escapeHtml(growth.projectData.pageSection.title)}</h4><p>${escapeHtml(growth.projectData.pageSection.description)}</p></section>"""
                "photo-gallery" -> {
                    val figs = growth.projectData.gallery.joinToString("") { p ->
                        "<figure><img src=\"${escapeHtml(p.src)}\" alt=\"${escapeHtml(p.alt)}\"><figcaption>${escapeHtml(p.caption)}</figcaption></figure>"
                    }
                    """<section class="feature-module"><h4>معرض صوري</h4><div class="project-gallery">${figs.ifEmpty { "<p>لا توجد صور مضافة بعد.</p>" }}</div></section>"""
                }
                "todo-list" -> {
                    val lis = growth.projectData.todos.joinToString("") { "<li>${escapeHtml(it)}</li>" }
                    """<section class="feature-module"><h4>قائمة مهامي</h4><ul class="todo-items">${lis.ifEmpty { "<li>أضف مهمة من شاشة مشاريعي</li>" }}</ul></section>"""
                }
                "notes-app" -> """<section class="feature-module"><h4>ملاحظتي</h4><p>${escapeHtml(growth.projectData.note.ifBlank { "لا توجد ملاحظة بعد." })}</p></section>"""
                else -> ""
            }
        }

        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>${escapeHtml(card.name.ifBlank { "بطاقتي الشخصية" })}</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;background:#f5f6f1;font:16px/1.7 Arial,sans-serif;color:${theme.inkHex}}
                .card{width:min(100%,460px);overflow:hidden;border:1px solid #e4e9e2;border-radius:22px;background:#fff;box-shadow:0 18px 48px #203d2d18}
                .top{display:flex;align-items:center;justify-content:space-between;padding:20px;background:${theme.softHex}}
                .avatar{width:76px;height:76px;display:grid;place-items:center;border-radius:50%;object-fit:cover;background:${theme.accentHex};color:#fff;font-size:24px;font-weight:bold}
                .mark{color:${theme.accentHex};font-weight:bold}
                .copy{padding:22px}
                .role{color:${theme.accentHex};font-size:14px;font-weight:bold}
                .copy h1{margin:5px 0 8px;font-size:26px}
                .copy p{margin:0;color:#53645a}
                .foot{display:flex;justify-content:space-between;padding:12px 22px;background:#fafbf8;color:#5b6c61}
                .latest-build{display:grid;gap:3px;padding:12px 20px;border-top:1px solid #e8ede5;background:${theme.softHex}}
                .latest-build small{color:#65766a;font-size:12px;font-weight:bold}
                .latest-build b{color:${theme.accentHex};font-size:14px}
                .learning{padding:12px 22px;border-top:1px solid #e4e9e2}
                .learning summary{cursor:pointer;font-weight:bold;color:${theme.accentHex}}
                .learning ul{display:grid;gap:8px;margin:10px 0 0;padding:0;list-style:none}
                .project-artifact{padding:9px 10px;border:1px solid #e4e9e2;border-radius:10px;background:#f7faf5}
                .project-artifact pre{max-height:160px;overflow:auto;white-space:pre-wrap;direction:ltr;text-align:left;padding:8px;border-radius:8px;background:#1b2a20;color:#e2eee4;font:12px/1.5 monospace}
                .feature-module{margin:12px 20px;padding:14px;border:1px solid #e3eadd;border-radius:14px;background:#fff}
                .feature-module h4{margin:0 0 4px}
                .project-gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:8px}
                .project-gallery figure{margin:0;padding:6px;border:1px solid #e5ebe2;border-radius:10px}
                .project-gallery img{width:100%;height:100px;object-fit:cover;border-radius:7px}
                .module-status{color:${theme.accentHex};font-weight:bold}
              </style>
            </head>
            <body>
              <main class="card">
                <div class="top">$avatarHtml<span class="mark">بطاقتي</span></div>
                <div class="copy">
                  <span class="role">${escapeHtml(card.role.ifBlank { "مجال تتعلمه" })}</span>
                  <h1>${escapeHtml(card.name.ifBlank { "اسمك" })}</h1>
                  <p>${escapeHtml(card.about.ifBlank { "نبذة قصيرة عني" })}</p>
                </div>
                <div class="foot"><span>خطوة صغيرة كل يوم</span><span>✦</span></div>
                $latestHtml
                $partsListHtml
                $modulesHtml
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
