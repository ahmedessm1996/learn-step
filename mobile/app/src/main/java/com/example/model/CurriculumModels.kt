package com.example.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class TrackType(
    val key: String,
    val titleAr: String,
    val shortTitle: String,
    val fileName: String,
    val assetFile: String,
    val subtitle: String
) {
    HTML(
        key = "html",
        titleAr = "HTML القياسية",
        shortTitle = "HTML",
        fileName = "index.html",
        assetFile = "html-lessons.json",
        subtitle = "بناء هيكل الصفحة وعناصرها الدلالية"
    ),
    CSS(
        key = "css",
        titleAr = "تنسيق CSS",
        shortTitle = "CSS",
        fileName = "style.css",
        assetFile = "css-lessons.json",
        subtitle = "الألوان والبطاقات والتخطيط المتجاوب"
    ),
    JS(
        key = "js",
        titleAr = "برمجة JavaScript",
        shortTitle = "JavaScript",
        fileName = "script.js",
        assetFile = "js-lessons.json",
        subtitle = "التفاعل والمنطق والأحداث والـ DOM"
    ),
    DATABASE(
        key = "database",
        titleAr = "قواعد البيانات (SQL)",
        shortTitle = "SQL",
        fileName = "query.sql",
        assetFile = "database-lessons.json",
        subtitle = "تحليل البيانات وتصميم الجداول واستعلامات SQL"
    ),
    CSHARP(
        key = "csharp",
        titleAr = "تأسيس #C",
        shortTitle = "C#",
        fileName = "Program.cs",
        assetFile = "csharp-lessons.json",
        subtitle = "الخوارزميات، الحلقات، الكائنات، والتتبع الحي للتنفيذ"
    ),
    ANGULAR(
        key = "angular",
        titleAr = "Angular 22 و TypeScript",
        shortTitle = "Angular",
        fileName = "src/app/app.ts",
        assetFile = "angular-lessons.json",
        subtitle = "بناء تطبيق سينما متكامل بـ Signals والمعمارية الحديثة"
    );

    companion object {
        fun fromKey(key: String?): TrackType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: HTML
        }
    }
}

/**
 * Breakdown elements in the JSON files can be either plain Strings ("<code>: <meaning>")
 * or Objects ({ "code": "...", "meaning": "..." }).
 */
@Serializable(with = BreakdownItemSerializer::class)
data class BreakdownItem(
    val code: String = "",
    val meaning: String = "",
    val title: String = "",
    val benefit: String = ""
)

object BreakdownItemSerializer : KSerializer<BreakdownItem> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BreakdownItem")

    override fun deserialize(decoder: Decoder): BreakdownItem {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return BreakdownItem(meaning = decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val raw = element.content.trim()
                val lastColon = raw.lastIndexOf(": ")
                val colon = if (lastColon >= 0) lastColon else raw.indexOf(':')
                if (colon > 0) {
                    val codePart = raw.substring(0, colon).trim()
                    val meaningPart = raw.substring(colon + 1).trim()
                    BreakdownItem(
                        code = codePart,
                        meaning = meaningPart,
                        title = codePart,
                        benefit = "فائدة $codePart: $meaningPart"
                    )
                } else {
                    BreakdownItem(meaning = raw, benefit = raw)
                }
            }
            is JsonObject -> {
                val code = element["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val meaning = element["meaning"]?.jsonPrimitive?.contentOrNull
                    ?: element["note"]?.jsonPrimitive?.contentOrNull
                    ?: element["text"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val title = element["title"]?.jsonPrimitive?.contentOrNull ?: code
                val benefit = element["benefit"]?.jsonPrimitive?.contentOrNull ?: meaning
                BreakdownItem(code = code, meaning = meaning, title = title, benefit = benefit)
            }
            else -> BreakdownItem()
        }
    }

    override fun serialize(encoder: Encoder, value: BreakdownItem) {
        encoder.encodeString(if (value.code.isNotBlank()) "${value.code}: ${value.meaning}" else value.meaning)
    }
}

@Serializable
data class ErrorRule(
    val pattern: String = "",
    val flags: String = "i",
    val type: String = "regex",
    val invert: Boolean = false,
    val message: String = "",
    val tier1: String? = null,
    val tier2: String? = null,
    val tier3: String? = null,
    val tier4: String? = null,
    val solution: String? = null
)

@Serializable
data class BugRepair(
    val brokenCode: String = "",
    val fixedCode: String = "",
    val question: String = "",
    val explanation: String = "",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0
)

@Serializable
data class LessonQuiz(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correct: Int = 0
)

@Serializable
data class CSharpIndexAccess(
    val name: String = "",
    val mode: String = "read",
    val rank: Int = 1
)

@Serializable
data class CSharpFieldAccess(
    val name: String = "",
    val mode: String = "read"
)

@Serializable
data class CSharpLoopSpec(
    val kind: String = "while",
    val depth: Int? = null,
    val reads: List<String> = emptyList(),
    val jump: String? = null,
    val effect: String? = null
)

@Serializable
data class CSharpTestCase(
    val inputs: Map<String, JsonElement> = emptyMap(),
    val stdin: String? = null,
    val output: String = ""
)

@Serializable
data class CSharpChallenge(
    val output: String? = null,
    val differentOutput: Boolean = false,
    val stdin: String? = null,
    val declared: List<String> = emptyList(),
    val types: Map<String, String> = emptyMap(),
    val read: List<String> = emptyList(),
    val printed: List<String> = emptyList(),
    val minPrints: Int? = null,
    val updated: List<String> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val calls: Map<String, Int> = emptyMap(),
    val operation: String? = null,
    val operationIn: String? = null,
    val methods: List<String> = emptyList(),
    val branching: Boolean = false,
    val elseRequired: Boolean = false,
    val elseIfRequired: Boolean = false,
    val indexAccess: CSharpIndexAccess? = null,
    val fieldAccess: CSharpFieldAccess? = null,
    val conditionReads: List<String> = emptyList(),
    val loop: CSharpLoopSpec? = null,
    val cases: List<CSharpTestCase> = emptyList()
)

/**
 * Angular expected can be a String or List<String> in some edits.
 */
@Serializable
data class AngularEditCheck(
    val file: String = "",
    val expected: JsonElement? = null
)

@Serializable
data class RawLessonStep(
    val id: String = "",
    val title: String = "",
    val objective: String = "",
    val type: String = "edit",
    val instruction: String = "",
    val explanation: String = "",
    val benefit: String = "",
    val expectedLine: String = "",
    val from: String = "",
    val to: String = "",
    val task: String = "",
    val flow: String = "",
    val file: String? = null,
    val code: String? = null,
    val starterCode: String? = null,
    val solution: String? = null,
    val expected: JsonElement? = null,
    val question: String? = null,
    val options: List<String> = emptyList(),
    val correct: Int? = null,
    val correctOptionIndex: Int? = null,
    val challenge: CSharpChallenge? = null,
    val errorRules: List<ErrorRule> = emptyList(),
    val hints: List<JsonElement> = emptyList()
)

@Serializable
data class RawLesson(
    val id: String = "",
    val legacyId: String = "",
    val title: String = "",
    val skill: String = "",
    val order: Int = 0,
    val unitId: String = "",
    val module: String = "",
    val phase: String = "",
    val duration: String = "",
    val level: String = "",
    val summary: String = "",
    val concept: String = "",
    val whyUse: String = "",
    val browserMechanics: String = "",
    val documentationUrl: String = "",
    val reference: String = "",
    val flow: String = "",
    val task: String = "",
    val file: String = "",
    val code: String = "",
    val expected: JsonElement? = null,
    val edits: List<AngularEditCheck> = emptyList(),
    val breakdown: List<BreakdownItem> = emptyList(),
    val steps: List<RawLessonStep> = emptyList(),
    val bugRepair: BugRepair? = null,
    val quiz: LessonQuiz? = null
)

@Serializable
data class CSharpUnit(
    val id: String = "",
    val title: String = "",
    val order: Int = 0,
    val lessonIds: List<String> = emptyList()
)

@Serializable
data class ModuleMeta(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val badge: String = "",
    val lessonCount: Int = 0
)

@Serializable
data class CurriculumTrack(
    val schemaVersion: String = "1.0.0",
    val track: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val version: String = "",
    val totalLessons: Int = 0,
    val modules: Map<String, ModuleMeta> = emptyMap(),
    val units: List<CSharpUnit> = emptyList(),
    val starterFiles: Map<String, String> = emptyMap(),
    val lessons: List<RawLesson> = emptyList()
)

fun JsonElement?.asExpectedStrings(): List<String> {
    return when (this) {
        is JsonPrimitive -> listOfNotNull(this.contentOrNull).filter { it.isNotBlank() }
        is JsonArray -> this.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }
        else -> emptyList()
    }
}

fun RawLessonStep.hintTextAt(tier: Int): String {
    val idx = (tier - 1).coerceIn(0, (hints.size - 1).coerceAtLeast(0))
    val elem = hints.getOrNull(idx) ?: return ""
    return when (elem) {
        is JsonPrimitive -> elem.contentOrNull.orEmpty()
        is JsonObject -> elem.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        else -> ""
    }
}
