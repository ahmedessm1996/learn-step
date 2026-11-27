package com.example.engine

import com.example.model.CSharpChallenge
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.math.BigInteger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.truncate

class CSharpError(
    message: String,
    val line: Int = 1,
    val column: Int = 1,
    val code: String = "syntax"
) : RuntimeException(message)

data class CSharpDecimal(val coefficient: BigInteger, val scale: Int)

data class CSharpArray2D(val rows: MutableList<MutableList<Any?>>, val columns: Int)

data class CSharpSlot(val type: String, var value: Any?)

class CSharpObject(
    val id: Int,
    val type: String,
    val fields: MutableMap<String, CSharpSlot> = linkedMapOf(),
    var collection: CollectionState? = null
)

data class TracedVariable(val name: String, val type: String, val valueText: String, val rawValue: Any?)

data class TracedCollectionSnapshot(val kind: String, val count: Int, val items: List<String>)

data class TracedObject(
    val id: Int,
    val type: String,
    val fields: List<TracedVariable>,
    val collection: TracedCollectionSnapshot? = null
)

data class ElementAccessRecord(
    val name: String?,
    val indices: List<Int>,
    val mode: String,
    val valueText: String
)

data class FieldAccessRecord(
    val id: Int,
    val field: String,
    val mode: String,
    val valueText: String
)

data class CollectionOpRecord(
    val id: Int,
    val method: String,
    val previousCount: Int,
    val count: Int,
    val valueText: String
)

data class TraceSnapshot(
    val stepIndex: Int,
    val line: Int,
    val source: String,
    val kind: String,
    val name: String?,
    val action: String,
    val output: String,
    val nextLine: Int?,
    val reads: Set<String>,
    val variables: List<TracedVariable>,
    val objects: List<TracedObject>,
    val skippedLines: Set<Int>,
    val loopKind: String? = null,
    val loopIteration: Int? = null,
    val loopDepth: Int? = null,
    val loopPhase: String? = null,
    val callName: String? = null,
    val callDepth: Int? = null,
    val callPhase: String? = null,
    val clause: String? = null,
    val comparisonSummary: String? = null,
    val logicSummary: String? = null,
    val calculationSummary: String? = null,
    val accesses: List<ElementAccessRecord> = emptyList(),
    val fields: List<FieldAccessRecord> = emptyList(),
    val collectionOps: List<CollectionOpRecord> = emptyList()
)

data class CSharpRunResult(
    val output: String,
    val trace: List<TraceSnapshot>,
    val variables: Map<String, TracedVariable>,
    val error: CSharpError? = null
)

data class CSharpChallengeResult(
    val passed: Boolean,
    val result: CSharpRunResult?,
    val message: String
)

object CSharpRuntime {

    private const val LIMIT_SOURCE = 16000
    private const val LIMIT_TOKENS = 2000
    private const val LIMIT_STATEMENTS = 160
    private const val LIMIT_OUTPUT = 16000
    private const val LIMIT_DEPTH = 40
    private const val LIMIT_TRACE = 1000
    private const val LIMIT_ARRAY = 64
    private const val LIMIT_CALLS = 24
    private const val LIMIT_OBJECTS = 64
    private const val LIMIT_FIELDS = 16

    private val DECIMAL_MAX = BigInteger("79228162514264337593543950335")
    private val NUMERIC_TYPES = setOf("int", "double", "decimal")
    private val PRIMITIVE_TYPES = NUMERIC_TYPES + setOf("string", "bool", "char")
    private val TEXT_TYPES = setOf("string", "string?", "null")
    private val LOOP_KINDS = setOf("while", "for", "foreach")
    private val INCREMENTS = setOf("++", "--")
    private val ASSIGNMENTS = setOf("=", "+=", "-=", "*=", "/=", "%=")
    private val COMPARISONS = setOf(">", ">=", "<", "<=", "==", "!=")
    private val LOGICAL = setOf("&&", "||")
    private val BINARY_PRECEDENCE = mapOf(
        "??" to 1, "||" to 2, "&&" to 3,
        "==" to 4, "!=" to 4,
        ">" to 5, ">=" to 5, "<" to 5, "<=" to 5,
        "+" to 10, "-" to 10,
        "*" to 20, "/" to 20, "%" to 20
    )
    private val RESERVED = (
        "abstract as base bool break byte case catch char checked class const continue decimal " +
            "default delegate do double else enum event explicit extern false finally fixed float " +
            "for foreach goto if implicit in int interface internal is lock long namespace new null " +
            "object operator out override params private protected public readonly ref return sbyte " +
            "sealed short sizeof stackalloc static string struct switch this throw true try typeof " +
            "uint ulong unchecked unsafe ushort using virtual void volatile while var"
        ).split(" ").toSet()

    private val ARRAY_TYPES = mapOf(
        "int" to "Int32", "double" to "Double", "decimal" to "Decimal",
        "bool" to "Boolean", "char" to "Char", "string" to "String", "string?" to "String"
    )

    private data class Token(
        val kind: String,
        val value: String,
        val line: Int,
        val column: Int,
        val start: Int,
        val end: Int
    )

    private fun fail(msg: String, t: Token? = null, code: String = "syntax"): Nothing {
        throw CSharpError(msg, t?.line ?: 1, t?.column ?: 1, code)
    }

    private fun arrayRank(type: String?): Int = when {
        type == null -> 0
        type.endsWith("[,]") -> 2
        type.endsWith("[]") -> 1
        else -> 0
    }

    private fun elementType(type: String): String = type.dropLast(arrayRank(type) + 1)
    private fun nonNullable(type: String): String = if (type.endsWith("?")) type.dropLast(1) else type
    private fun className(type: String): String = nonNullable(type).substringBefore('<')

    private fun makeDecimal(coeff: BigInteger, scaleInit: Int, token: Token?): CSharpDecimal {
        var c = coeff
        var s = scaleInit
        while (s > 0 && c.remainder(BigInteger.TEN) == BigInteger.ZERO && (s > 28 || c.abs() > DECIMAL_MAX)) {
            c = c.divide(BigInteger.TEN)
            s--
        }
        if (s > 28 || c.abs() > DECIMAL_MAX) {
            fail("هذا الناتج يحتاج تقريبًا أو نطاقًا خارج حساب decimal المدعوم.", token, "unsupported")
        }
        return CSharpDecimal(c, s)
    }

    private fun parseDecimal(text: String, token: Token?): CSharpDecimal {
        val m = Regex("^(-?)(\\d*)(?:\\.(\\d+))?$").matchEntire(text)
            ?: fail("اكتب قيمة decimal عشرية بسيطة متبوعة بـ m، مثل 0.25m.", token, "unsupported")
        val sign = m.groupValues[1]
        val whole = m.groupValues[2]
        val frac = m.groupValues[3]
        if (whole.isEmpty() && frac.isEmpty()) fail("اكتب قيمة decimal عشرية بسيطة متبوعة بـ m.", token, "unsupported")
        return makeDecimal(BigInteger(sign + whole.ifEmpty { "0" } + frac), frac.length, token)
    }

    private fun decimalText(d: CSharpDecimal): String {
        val absStr = d.coefficient.abs().toString().padStart(d.scale + 1, '0')
        val sign = if (d.coefficient < BigInteger.ZERO) "-" else ""
        return if (d.scale == 0) "$sign$absStr"
        else "$sign${absStr.dropLast(d.scale)}.${absStr.takeLast(d.scale)}"
    }

    private fun doubleText(v: Double): String {
        if (v == 0.0) return if (1.0 / v < 0) "-0" else "0"
        val a = abs(v)
        return if (a < 1e-4 || a >= 1e17) {
            String.format(Locale.US, "%.15E", v).replace(Regex("0+E"), "E").replace(".E", "E")
        } else {
            val l = v.toLong()
            if (v == l.toDouble()) l.toString() else v.toString()
        }
    }

    private fun clrName(type: String): String {
        if (arrayRank(type) > 0) {
            val elem = elementType(type)
            return clrName(elem) + type.substring(elem.length)
        }
        val name = className(type)
        ARRAY_TYPES[name]?.let { return "System.$it" }
        val coll = CSharpCollections.describeCollection(type)
        if (coll != null) {
            return "System.Collections.Generic.$name`${coll.args.size}[${coll.args.joinToString(",") { clrName(it) }}]"
        }
        val nn = nonNullable(type)
        val arg = if (nn.length > name.length + 2) nn.substring(name.length + 1, nn.length - 1) else ""
        return if (arg.isNotEmpty()) "$name`1[${clrName(arg)}]" else name
    }

    private fun displayValue(value: Any?, type: String?): String = when {
        value == null -> ""
        type == "decimal" && value is CSharpDecimal -> decimalText(value)
        type == "double" && value is Number -> doubleText(value.toDouble())
        value is CSharpObject -> clrName(value.type)
        type != null && arrayRank(type) > 0 -> clrName(type)
        value is Boolean -> if (value) "True" else "False"
        else -> value.toString()
    }

    private fun traceValueText(value: Any?, type: String?): String {
        if (value == null) return "null"
        if (value is CSharpObject) return "#${value.id}"
        val rank = arrayRank(type)
        val elem = if (rank > 0 && type != null) elementType(type) else null
        if (rank == 1 && value is List<*>) {
            return "[" + value.joinToString(", ") { traceValueText(it, elem) } + "]"
        }
        if (rank == 2 && value is CSharpArray2D) {
            return "[" + value.rows.joinToString(", ") { row ->
                "[" + row.joinToString(", ") { traceValueText(it, elem) } + "]"
            } + "]"
        }
        if (type == "decimal" && value is CSharpDecimal) return decimalText(value)
        if (type == "string" || type == "string?") return "\"$value\""
        if (type == "char") return "'$value'"
        return value.toString()
    }

    private fun lex(source: String): List<Token> {
        if (source.length > LIMIT_SOURCE) {
            fail("المثال أطول من حدود المحاكي. استخدم برنامجًا صغيرًا.", code = "limit")
        }
        val tokens = mutableListOf<Token>()
        var pos = 0
        var line = 1
        var column = 1

        fun advance(): Char {
            val c = source[pos++]
            if (c == '\n') { line++; column = 1 } else column++
            return c
        }

        fun add(kind: String, value: String, l: Int, c: Int, start: Int) {
            tokens += Token(kind, value, l, c, start, pos)
            if (tokens.size > LIMIT_TOKENS) fail("قلّل عدد أجزاء المثال.", tokens.last(), "limit")
        }

        while (pos < source.length) {
            val l = line
            val col = column
            val start = pos
            val c = source[pos]
            if (c.isWhitespace()) { advance(); continue }
            if (source.startsWith("//", pos)) {
                while (pos < source.length && source[pos] != '\n') advance()
                continue
            }
            if (source.startsWith("/*", pos)) {
                advance(); advance()
                while (pos < source.length && !source.startsWith("*/", pos)) advance()
                if (pos == source.length) fail("أغلق التعليق بعلامة */.", Token("", "", l, col, start, pos))
                advance(); advance()
                continue
            }
            if (pos + 1 < source.length) {
                val pair = source.substring(pos, pos + 2)
                if (pair in setOf("==", "!=", ">=", "<=", "&&", "||", "??") || pair in INCREMENTS || pair in ASSIGNMENTS) {
                    advance(); advance()
                    add(pair, pair, l, col, start)
                    continue
                }
            }
            if (c == '"' || c == '\'') {
                advance()
                val sb = StringBuilder()
                var closed = false
                while (pos < source.length) {
                    val ch = advance()
                    if (ch == c) { closed = true; break }
                    if (ch == '\n' || ch == '\r') {
                        fail("أغلق علامات الاقتباس في السطر نفسه. للسطر الجديد استخدم \\n.", Token("", "", l, col, start, pos))
                    }
                    if (ch != '\\') { sb.append(ch); continue }
                    if (pos >= source.length) break
                    val esc = advance()
                    val mapped = when (esc) {
                        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; '"' -> '"'; '\'' -> '\''; '\\' -> '\\'; '0' -> '\u0000'
                        else -> fail("صيغة الهروب هذه غير مدعومة في المحاكي.", Token("", "", l, col, start, pos), "unsupported")
                    }
                    sb.append(mapped)
                }
                if (!closed) fail("علامة إغلاق القيمة $c ناقصة.", Token("", "", l, col, start, pos))
                val str = sb.toString()
                if (c == '\'' && str.length != 1) {
                    fail("ضع حرفًا واحدًا بين علامتي الاقتباس المفردتين لقيمة char.", Token("", "", l, col, start, pos), "type")
                }
                add(if (c == '"') "string" else "char", str, l, col, start)
                continue
            }
            if (c.isDigit() || (c == '.' && pos + 1 < source.length && source[pos + 1].isDigit())) {
                val m = Regex("^(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?[dDmM]?").find(source.substring(pos))!!
                val lit = m.value
                repeat(lit.length) { advance() }
                add("number", lit, l, col, start)
                continue
            }
            if (c.isLetter() || c == '_') {
                val sb = StringBuilder()
                while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) {
                    sb.append(advance())
                }
                add("identifier", sb.toString(), l, col, start)
                continue
            }
            if (c in ";=().,+-*/%{}[]><!?") {
                advance()
                add(c.toString(), c.toString(), l, col, start)
                continue
            }
            fail("الرمز «$c» خارج نطاق المحاكي التعليمي.", Token("", "", l, col, start, pos), "unsupported")
        }
        tokens += Token("eof", "", line, column, pos, pos)
        return tokens
    }

    // ---- AST Nodes ----
    private sealed class Expr(open val token: Token, open val start: Int, open val end: Int) {
        var valueType: String = ""
    }

    private class LitExpr(val type: String, val value: Any?, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class VarExpr(val name: String, token: Token, start: Int, end: Int, var implicitMember: Expr? = null) : Expr(token, start, end)
    private class UnaryExpr(val op: String, val inner: Expr, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class BinaryExpr(val op: String, val left: Expr, val right: Expr, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class CastExpr(val targetType: String, val inner: Expr, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class ReadLineExpr(token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class TryParseExpr(val inner: Expr, val outName: String, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class CallExpr(val name: String, val args: List<Expr>, token: Token, start: Int, end: Int, val receiver: Expr? = null, var callKind: String = "call") : Expr(token, start, end)
    private class MemberExpr(val name: String, val receiver: Expr, token: Token, start: Int, end: Int, var memberKind: String = "member") : Expr(token, start, end)
    private class IndexExpr(val receiver: Expr, val indices: List<Expr>, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class NewArrayExpr(val arrayType: String, val indices: List<Expr>, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class ArrayLitExpr(val arrayType: String, val rows: List<List<Expr>>, val is2D: Boolean, token: Token, start: Int, end: Int) : Expr(token, start, end)
    private class NewObjectExpr(val type: String, var classNameResolved: String, val args: List<Expr>, token: Token, start: Int, end: Int) : Expr(token, start, end)

    private data class ParamSpec(val type: String, val name: String, val token: Token)
    private data class FieldSpec(val name: String, val type: String, val init: Expr?, val token: Token)

    private sealed class Stmt(open val token: Token, open val endToken: Token, open val sourceText: String)
    private class EmptyStmt(token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class BlockStmt(val body: List<Stmt>, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class DeclareStmt(val name: String, val type: String, val init: Expr, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class AssignStmt(val name: String, val updateOp: String, val expr: Expr, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class AssignMemberStmt(val target: MemberExpr, val expr: Expr, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class AssignIndexStmt(val name: String, val target: IndexExpr, val expr: Expr, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class PrintStmt(val method: String, val expr: Expr?, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class InvokeStmt(val call: CallExpr, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class IfStmt(val cond: Expr, val consequent: Stmt, val alternate: Stmt?, val elseToken: Token?, val header: String, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class LoopStmt(
        val kind: String,
        val init: Stmt?,
        val cond: Expr?,
        val update: Stmt?,
        val iterType: String?,
        val iterName: String?,
        val iterToken: Token?,
        val collectionExpr: Expr?,
        val header: String,
        val body: Stmt,
        token: Token,
        endToken: Token,
        sourceText: String
    ) : Stmt(token, endToken, sourceText)
    private class BreakContinueStmt(val kind: String, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class ReturnStmt(val expr: Expr?, token: Token, endToken: Token, sourceText: String) : Stmt(token, endToken, sourceText)
    private class CallableStmt(
        val callableKind: String,
        val name: String,
        val returnType: String,
        val params: List<ParamSpec>,
        val header: String,
        val body: BlockStmt,
        token: Token,
        endToken: Token,
        sourceText: String
    ) : Stmt(token, endToken, sourceText)
    private class ClassStmt(
        val name: String,
        val genericParam: String?,
        val fields: LinkedHashMap<String, FieldSpec>,
        val methods: LinkedHashMap<String, CallableStmt>,
        val creator: CallableStmt?,
        token: Token,
        endToken: Token,
        sourceText: String
    ) : Stmt(token, endToken, sourceText)

    private fun parseProgram(source: String): List<Stmt> {
        val text = source.replace("\r\n", "\n").replace("\r", "\n")
        val tokens = lex(text)
        val classNames = linkedMapOf<String, Boolean>()
        for (i in tokens.indices) {
            if (tokens[i].kind == "identifier" && tokens[i].value == "class" && i + 1 < tokens.size && tokens[i + 1].kind == "identifier") {
                classNames[tokens[i + 1].value] = (i + 2 < tokens.size && tokens[i + 2].kind == "<")
            }
        }
        var cursor = 0
        var depth = 0
        var nesting = 0
        var stmtCount = 0
        var genericParam: String? = null

        fun peek(offset: Int = 0): Token = tokens[(cursor + offset).coerceAtMost(tokens.lastIndex)]
        fun take(): Token {
            val t = peek()
            if (t.kind != "eof") cursor++
            return t
        }
        fun expect(kind: String, value: String? = null): Token {
            val t = peek()
            if (t.kind != kind || (value != null && t.value != value)) {
                val msg = if (kind == ";") "الفاصلة المنقوطة ; ناقصة في نهاية الأمر السابق."
                else "كنت أتوقع «${value ?: kind}» هنا."
                fail(msg, t)
            }
            return take()
        }
        fun nameToken(): Token {
            val name = expect("identifier")
            if (name.value in RESERVED || name.value == "Console" || name.value == "System") {
                fail("اختَر اسم متغير غير محجوز.", name, "name")
            }
            return name
        }
        fun isType(name: String): Boolean =
            name in PRIMITIVE_TYPES || classNames.containsKey(name) || CSharpCollections.collectionArity(name) > 0 || name == genericParam

        fun typeName(allowVoid: Boolean = false, allowArray: Boolean = true, typeDepth: Int = 0): String {
            val token = expect("identifier")
            if (!isType(token.value) && !(allowVoid && token.value == "void")) {
                fail("اختَر نوعًا مدعومًا مثل int أو string.", token, "type")
            }
            if (typeDepth >= LIMIT_DEPTH) fail("قلّل الأنواع العامة المتداخلة.", token, "limit")
            var type = token.value
            val arity = when {
                classNames.containsKey(type) -> if (classNames[type] == true) 1 else 0
                else -> CSharpCollections.collectionArity(type)
            }
            if (arity > 0) {
                expect("<")
                val argsList = mutableListOf(typeName(allowVoid = false, allowArray = true, typeDepth = typeDepth + 1))
                for (i in 1 until arity) {
                    expect(",")
                    argsList += typeName(allowVoid = false, allowArray = true, typeDepth = typeDepth + 1)
                }
                expect(">")
                type += "<${argsList.joinToString(",")}>"
                if (!classNames.containsKey(token.value) && CSharpCollections.describeCollection(type) == null) {
                    fail("جرّب المجموعة مع int أو string أو bool أو char في المحاكي.", token, "unsupported")
                }
            }
            if ((type == "string" || classNames.containsKey(token.value) || CSharpCollections.collectionArity(token.value) > 0) && peek().kind == "?") {
                type += take().value
            }
            if (allowArray && token.value in PRIMITIVE_TYPES && peek().kind == "[") {
                take()
                val rank = if (peek().kind == ",") take().value else ""
                expect("]")
                type += "[$rank]"
            }
            return type
        }

        lateinit var parseExpr: (Int) -> Expr
        lateinit var parseStmt: (Boolean) -> Stmt

        fun bracketExpressions(): List<Expr> {
            expect("[")
            val items = mutableListOf(parseExpr(0))
            if (peek().kind == ",") { take(); items += parseExpr(0) }
            expect("]")
            return items
        }

        fun parseCall(nameTok: Token, receiver: Expr? = null): CallExpr {
            expect("(")
            val args = mutableListOf<Expr>()
            if (peek().kind != ")") {
                while (true) {
                    args += parseExpr(0)
                    if (peek().kind != ",") break
                    take()
                }
            }
            val close = expect(")")
            return CallExpr(nameTok.value, args, nameTok, receiver?.start ?: nameTok.start, close.end, receiver)
        }

        fun postfix(initial: Expr): Expr {
            var node = initial
            while (peek().kind == "." || peek().kind == "[") {
                if (peek().kind == "[") {
                    val tok = peek()
                    val indices = bracketExpressions()
                    node = IndexExpr(node, indices, tok, node.start, tokens[cursor - 1].end)
                } else {
                    take()
                    val member = nameToken()
                    node = if (peek().kind == "(") {
                        parseCall(member, node)
                    } else {
                        MemberExpr(member.value, node, member, node.start, member.end)
                    }
                }
            }
            return node
        }

        fun braceItems(read: () -> List<Expr>): List<List<Expr>> {
            expect("{")
            val items = mutableListOf<List<Expr>>()
            while (peek().kind != "}") {
                if (items.size >= LIMIT_ARRAY) fail("اختر مصفوفة تعليمية من 64 عنصرًا أو أقل.", peek(), "limit")
                items += read()
                if (peek().kind != ",") break
                take()
            }
            expect("}")
            return items
        }

        fun brace1D(): List<Expr> {
            expect("{")
            val items = mutableListOf<Expr>()
            while (peek().kind != "}") {
                if (items.size >= LIMIT_ARRAY) fail("اختر مصفوفة تعليمية من 64 عنصرًا أو أقل.", peek(), "limit")
                items += parseExpr(0)
                if (peek().kind != ",") break
                take()
            }
            expect("}")
            return items
        }

        parseExpr = { minPrec: Int ->
            if (++depth > LIMIT_DEPTH) fail("قلّل الأقواس المتداخلة في المثال.", peek(), "limit")
            val t = take()
            var node: Expr = when {
                t.kind == "number" -> {
                    val suffix = t.value.last().lowercaseChar()
                    val type = when {
                        suffix == 'm' -> "decimal"
                        t.value.any { it == '.' || it == 'e' || it == 'E' || it == 'd' || it == 'D' } -> "double"
                        else -> "int"
                    }
                    val value: Any = when (type) {
                        "decimal" -> parseDecimal(t.value.dropLast(1), t)
                        "double" -> t.value.let { if (suffix == 'd') it.dropLast(1) else it }.toDoubleOrNull()
                            ?: fail("عدد غير صالح.", t, "type")
                        else -> t.value.toLongOrNull() ?: fail("عدد خارج النطاق.", t, "unsupported")
                    }
                    LitExpr(type, value, t, t.start, t.end)
                }
                t.kind == "string" || t.kind == "char" -> LitExpr(t.kind, t.value, t, t.start, t.end)
                t.value == "true" || t.value == "false" -> LitExpr("bool", t.value == "true", t, t.start, t.end)
                t.value == "null" -> LitExpr("null", null, t, t.start, t.end)
                t.value == "new" -> {
                    if (peek().value == "int") {
                        take()
                        val indices = bracketExpressions()
                        NewArrayExpr(if (indices.size == 1) "int[]" else "int[,]", indices, t, t.start, tokens[cursor - 1].end)
                    } else {
                        val objType = typeName(allowVoid = false, allowArray = false)
                        val c = parseCall(t)
                        NewObjectExpr(objType, objType, c.args, t, t.start, c.end)
                    }
                }
                (t.value == "Console" || t.value == "System") && peek().kind == "." -> {
                    expect(".")
                    if (t.value == "System") { expect("identifier", "Console"); expect(".") }
                    val m = expect("identifier", "ReadLine")
                    expect("("); val close = expect(")")
                    ReadLineExpr(m, t.start, close.end)
                }
                t.value == "int" && peek().kind == "." -> {
                    expect(".")
                    val m = expect("identifier", "TryParse")
                    expect("(")
                    val input = parseExpr(0)
                    expect(","); expect("identifier", "out"); expect("identifier", "int")
                    val outTok = nameToken()
                    val close = expect(")")
                    TryParseExpr(input, outTok.value, m, t.start, close.end)
                }
                t.value == "Array" && peek().kind == "." -> {
                    expect(".")
                    val m = expect("identifier", "IndexOf")
                    val c = parseCall(m)
                    CallExpr("Array.IndexOf", c.args, m, t.start, c.end, null, "array-search")
                }
                t.kind == "identifier" && (t.value !in RESERVED || t.value == "this") -> {
                    if (peek().kind == "(") parseCall(t) else VarExpr(t.value, t, t.start, t.end)
                }
                t.kind in INCREMENTS -> fail("اكتب تحديث ++ أو -- كأمر مستقل أو في خانة التحديث داخل for.", t, "unsupported")
                t.kind == "-" || t.kind == "+" || t.kind == "!" -> {
                    val inner = parseExpr(30)
                    UnaryExpr(t.kind, inner, t, t.start, inner.end)
                }
                t.kind == "(" -> {
                    if (peek().kind == "identifier" && peek().value in NUMERIC_TYPES && peek(1).kind == ")") {
                        val targetTok = take(); take()
                        val inner = parseExpr(30)
                        CastExpr(targetTok.value, inner, targetTok, t.start, inner.end)
                    } else {
                        val inner = parseExpr(0)
                        expect(")")
                        inner
                    }
                }
                else -> fail("اكتب قيمة أو اسم متغير في هذا الموضع.", t)
            }
            node = postfix(node)
            while ((BINARY_PRECEDENCE[peek().kind] ?: 0) > minPrec) {
                val opTok = take()
                val prec = BINARY_PRECEDENCE[opTok.kind]!! - if (opTok.kind == "??") 1 else 0
                val right = parseExpr(prec)
                node = BinaryExpr(opTok.kind, node, right, opTok, node.start, right.end)
            }
            depth--
            node
        }

        fun declaration(allowFunction: Boolean = false): Stmt {
            val token = peek()
            val type = typeName(allowVoid = allowFunction)
            val nameTok = nameToken()
            if (peek().kind == "(") {
                if (!allowFunction) fail("عرّف الدالة في مستوى البرنامج خارج جسم الحلقة أو الشرط.", token, "unsupported")
                expect("(")
                val params = mutableListOf<ParamSpec>()
                while (peek().kind != ")") {
                    val pType = typeName()
                    val pName = nameToken()
                    params += ParamSpec(pType, pName.value, pName)
                    if (peek().kind != ",") break
                    take()
                    if (peek().kind == ")") fail("أضف معاملًا بعد الفاصلة أو احذفها.", peek())
                }
                val close = expect(")")
                if (peek().kind != "{") fail("اكتب جسم الدالة بين { و }.", peek())
                val body = parseStmt(false) as BlockStmt
                return CallableStmt(
                    "function", nameTok.value, type, params,
                    text.substring(token.start, close.end), body, token, body.endToken,
                    text.substring(token.start, body.endToken.end)
                )
            }
            if (type == "void") fail("استخدم void في تعريف دالة لا تعيد قيمة.", token, "type")
            expect("=")
            val rank = arrayRank(type)
            val init: Expr = if (rank > 0 && peek().kind == "{") {
                val startTok = peek()
                if (rank == 1) {
                    val items = brace1D()
                    ArrayLitExpr(type, listOf(items), false, startTok, startTok.start, tokens[cursor - 1].end)
                } else {
                    val rows = braceItems(::brace1D)
                    val cols = rows.firstOrNull()?.size ?: 0
                    if (rows.any { it.size != cols }) fail("اكتب نفس عدد الأعمدة في كل صف من المصفوفة الثنائية.", startTok, "type")
                    ArrayLitExpr(type, rows, true, startTok, startTok.start, tokens[cursor - 1].end)
                }
            } else {
                parseExpr(0)
            }
            return DeclareStmt(nameTok.value, type, init, token, tokens[cursor - 1], "")
        }

        fun parseAssignment(): Stmt {
            val start = peek()
            val prefix = start.kind in INCREMENTS
            val prefixOp = if (prefix) take() else null
            val nameTok = if (peek().value == "this") take() else nameToken()
            val variable = VarExpr(nameTok.value, nameTok, nameTok.start, nameTok.end)
            val target = postfix(variable)
            if (target is CallExpr && target.receiver != null && !prefix) {
                return InvokeStmt(target, start, tokens[cursor - 1], "")
            }
            if (target is IndexExpr || target is MemberExpr) {
                if (prefix || peek().kind != "=") fail("استخدم = لإسناد قيمة إلى خانة أو حقل.", peek(), "unsupported")
                take()
                val rhs = parseExpr(0)
                return if (target is IndexExpr) AssignIndexStmt(nameTok.value, target, rhs, start, tokens[cursor - 1], "")
                else AssignMemberStmt(target as MemberExpr, rhs, start, tokens[cursor - 1], "")
            }
            val op = prefixOp ?: take()
            if (op.kind !in ASSIGNMENTS && op.kind !in INCREMENTS) {
                fail("استخدم = أو معامل تحديث مدعوم بعد اسم المتغير.", op)
            }
            val rhs = if (op.kind in INCREMENTS) {
                LitExpr("int", 1L, op, op.start, op.end)
            } else {
                parseExpr(0)
            }
            val finalExpr = if (op.kind == "=") rhs else BinaryExpr(op.kind.first().toString(), variable, rhs, op, start.start, tokens[cursor - 1].end)
            return AssignStmt(nameTok.value, op.kind, finalExpr, start, tokens[cursor - 1], "")
        }

        fun parseCallable(kind: String, name: String, returnType: String, token: Token): CallableStmt {
            expect("(")
            val params = mutableListOf<ParamSpec>()
            while (peek().kind != ")") {
                val pType = typeName()
                val pName = nameToken()
                params += ParamSpec(pType, pName.value, pName)
                if (peek().kind != ",") break
                take()
            }
            val close = expect(")")
            if (peek().kind != "{") fail("اكتب جسم الدالة بين { و }.", peek())
            val body = parseStmt(false) as BlockStmt
            return CallableStmt(
                kind, name, returnType, params,
                text.substring(token.start, close.end), body, token, body.endToken,
                text.substring(token.start, body.endToken.end)
            )
        }

        fun parseClassDef(): ClassStmt {
            if (peek().value == "public") take()
            val token = expect("identifier", "class")
            val name = nameToken().value
            if (peek().kind == "<") {
                take(); genericParam = nameToken().value; expect(">")
            }
            expect("{")
            val fields = linkedMapOf<String, FieldSpec>()
            val methods = linkedMapOf<String, CallableStmt>()
            var creator: CallableStmt? = null
            while (peek().kind != "}") {
                val member = expect("identifier", "public")
                if (peek().value == name && peek(1).kind == "(") {
                    take()
                    if (creator != null) fail("استخدم منشئًا واحدًا في هذا المثال.", member, "unsupported")
                    creator = parseCallable("constructor", name, "void", member)
                    continue
                }
                val mType = typeName(allowVoid = true)
                val fieldTok = nameToken()
                if (fields.containsKey(fieldTok.value) || methods.containsKey(fieldTok.value) || fieldTok.value == name) {
                    fail("اختَر اسمًا مختلفًا لكل عضو في الصنف.", fieldTok, "name")
                }
                if (peek().kind == "(") {
                    methods[fieldTok.value] = parseCallable("method", fieldTok.value, mType, member)
                } else {
                    if (mType == "void") fail("ضع نوع قيمة للحقل.", fieldTok, "type")
                    if (fields.size >= LIMIT_FIELDS) fail("اختصر الحقول في الصنف التعليمي.", fieldTok, "limit")
                    val init = if (peek().kind == "=") { take(); parseExpr(0) } else null
                    expect(";")
                    fields[fieldTok.value] = FieldSpec(fieldTok.value, mType, init, fieldTok)
                }
            }
            val endTok = expect("}")
            val gp = genericParam
            genericParam = null
            return ClassStmt(name, gp, fields, methods, creator, token, endTok, text.substring(token.start, endTok.end))
        }

        parseStmt = { embedded: Boolean ->
            if (++nesting > LIMIT_DEPTH) fail("قلّل الكتل المتداخلة.", peek(), "limit")
            if (++stmtCount > LIMIT_STATEMENTS) fail("المثال يتجاوز عدد أوامر المحاكي.", peek(), "limit")
            val start = peek()
            val stmt: Stmt = when {
                start.kind == "{" -> {
                    take()
                    val body = mutableListOf<Stmt>()
                    while (peek().kind != "}" && peek().kind != "eof") body += parseStmt(false)
                    val end = expect("}")
                    BlockStmt(body, start, end, text.substring(start.start, end.end))
                }
                start.value == "if" -> {
                    take(); expect("(")
                    val cond = parseExpr(0)
                    val close = expect(")")
                    val cons = parseStmt(true)
                    var alt: Stmt? = null
                    var elseTok: Token? = null
                    if (peek().value == "else") {
                        elseTok = take()
                        alt = parseStmt(true)
                    }
                    val end = alt?.endToken ?: cons.endToken
                    IfStmt(cond, cons, alt, elseTok, text.substring(start.start, close.end), start, end, text.substring(start.start, end.end))
                }
                start.value in LOOP_KINDS -> {
                    val kind = take().value
                    expect("(")
                    var init: Stmt? = null
                    var cond: Expr? = null
                    var update: Stmt? = null
                    var iterType: String? = null
                    var iterName: String? = null
                    var iterTok: Token? = null
                    var collExpr: Expr? = null
                    when (kind) {
                        "for" -> {
                            init = if (peek().kind == ";") null else {
                                val s = if (isType(peek().value)) declaration() else parseAssignment()
                                val e = tokens[cursor - 1]
                                when (s) {
                                    is DeclareStmt -> DeclareStmt(s.name, s.type, s.init, s.token, e, text.substring(s.token.start, e.end))
                                    is AssignStmt -> AssignStmt(s.name, s.updateOp, s.expr, s.token, e, text.substring(s.token.start, e.end))
                                    else -> s
                                }
                            }
                            expect(";")
                            cond = if (peek().kind == ";") null else parseExpr(0)
                            expect(";")
                            update = if (peek().kind == ")") null else {
                                val s = parseAssignment()
                                val e = tokens[cursor - 1]
                                if (s is AssignStmt) AssignStmt(s.name, s.updateOp, s.expr, s.token, e, text.substring(s.token.start, e.end)) else s
                            }
                        }
                        "foreach" -> {
                            val typeTok = expect("identifier")
                            if (typeTok.value !in PRIMITIVE_TYPES) fail("اكتب نوع العنصر في foreach مثل int.", typeTok, "type")
                            iterType = typeTok.value
                            val nTok = nameToken()
                            iterName = nTok.value
                            iterTok = nTok
                            expect("identifier", "in")
                            collExpr = parseExpr(0)
                        }
                        else -> cond = parseExpr(0)
                    }
                    val close = expect(")")
                    val header = text.substring(start.start, close.end)
                    val body = parseStmt(true)
                    LoopStmt(kind, init, cond, update, iterType, iterName, iterTok, collExpr, header, body, start, body.endToken, text.substring(start.start, body.endToken.end))
                }
                start.value == "return" -> {
                    take()
                    val expr = if (peek().kind != ";") parseExpr(0) else null
                    val end = expect(";")
                    ReturnStmt(expr, start, end, text.substring(start.start, end.end))
                }
                start.value == "break" || start.value == "continue" -> {
                    take()
                    val end = expect(";")
                    BreakContinueStmt(start.value, start, end, text.substring(start.start, end.end))
                }
                start.kind == ";" -> {
                    val end = take()
                    EmptyStmt(start, end, ";")
                }
                else -> {
                    val innerStmt: Stmt = when {
                        isType(start.value) || start.value == "void" || start.value == "static" -> {
                            if (embedded) fail("ضع تصريح المتغير داخل كتلة { }.", start, "syntax")
                            if (start.value == "static") take()
                            val d = declaration(allowFunction = nesting == 1)
                            if (start.value == "static" && d !is CallableStmt) fail("استخدم static هنا قبل تعريف دالة.", start, "unsupported")
                            d
                        }
                        start.value == "Console" || start.value == "System" -> {
                            take()
                            if (start.value == "System") { expect("."); expect("identifier", "Console") }
                            expect(".")
                            val methodTok = expect("identifier")
                            if (methodTok.value != "Write" && methodTok.value != "WriteLine") {
                                fail("اسم أمر الطباعة حساس لحالة الحروف: Console.Write أو Console.WriteLine.", methodTok, "name")
                            }
                            expect("(")
                            val arg = if (peek().kind == ")") null else parseExpr(0)
                            expect(")")
                            if (arg == null && methodTok.value == "Write") fail("Console.Write يحتاج قيمة لطباعتها.", methodTok, "type")
                            PrintStmt(methodTok.value, arg, start, tokens[cursor - 1], "")
                        }
                        start.kind == "identifier" && start.value !in RESERVED && peek(1).kind == "(" -> {
                            val c = parseCall(take())
                            InvokeStmt(c, start, c.token, "")
                        }
                        (start.kind == "identifier" && (start.value !in RESERVED || start.value == "this")) || start.kind in INCREMENTS -> {
                            parseAssignment()
                        }
                        else -> fail("هذا البناء خارج نطاق المحاكي. راجع الصيغ المتاحة في الدرس.", start, "unsupported")
                    }
                    if (innerStmt is CallableStmt) innerStmt else {
                        val end = expect(";")
                        val src = text.substring(start.start, end.end)
                        when (innerStmt) {
                            is DeclareStmt -> DeclareStmt(innerStmt.name, innerStmt.type, innerStmt.init, start, end, src)
                            is AssignStmt -> AssignStmt(innerStmt.name, innerStmt.updateOp, innerStmt.expr, start, end, src)
                            is AssignMemberStmt -> AssignMemberStmt(innerStmt.target, innerStmt.expr, start, end, src)
                            is AssignIndexStmt -> AssignIndexStmt(innerStmt.name, innerStmt.target, innerStmt.expr, start, end, src)
                            is PrintStmt -> PrintStmt(innerStmt.method, innerStmt.expr, start, end, src)
                            is InvokeStmt -> InvokeStmt(innerStmt.call, start, end, src)
                            else -> innerStmt
                        }
                    }
                }
            }
            nesting--
            stmt
        }

        while (peek().value == "using") {
            take()
            expect("identifier", "System")
            if (peek().kind == ".") {
                take(); expect("identifier", "Collections"); expect("."); expect("identifier", "Generic")
            }
            expect(";")
        }

        val statements = mutableListOf<Stmt>()
        while (peek().kind != "eof" && peek().value != "class" && peek().value != "public") {
            statements += parseStmt(false)
        }
        while (peek().kind != "eof") {
            statements += parseClassDef()
        }
        if (statements.isEmpty()) fail("اكتب أمرًا صغيرًا أولًا، ثم شغّله.")
        return statements
    }

    private fun closedType(type: String, def: ClassStmt?, owner: String?): String {
        if (def?.genericParam == null || owner == null) return type
        val nn = nonNullable(owner)
        val arg = if (nn.length > def.name.length + 2) nn.substring(def.name.length + 1, nn.length - 1) else return type
        return type.replace(Regex("[^<>,\\[\\]?]+")) { m ->
            if (m.value == def.genericParam) arg else m.value
        }
    }

    private fun promoteNumbers(left: String, right: String): String? {
        if (left !in NUMERIC_TYPES || right !in NUMERIC_TYPES) return null
        if (left == right || right == "int") return left
        return if (left == "int") right else null
    }

    private fun validateTypes(statements: List<Stmt>): Pair<Map<String, CallableStmt>, Map<String, ClassStmt>> {
        val functions = linkedMapOf<String, CallableStmt>()
        val classes = linkedMapOf<String, ClassStmt>()
        for (s in statements) {
            if (s is CallableStmt && s.callableKind == "function") functions[s.name] = s
            if (s is ClassStmt) {
                if (classes.containsKey(s.name)) fail("الصنف «${s.name}» معرّف بالفعل.", s.token, "name")
                classes[s.name] = s
            }
        }

        class ScopeFrame(val allNames: MutableSet<String>, val available: MutableMap<String, String>, var readOnly: String? = null)
        val scopes = mutableListOf<ScopeFrame>()
        var loopDepth = 0
        var currentFunction: CallableStmt? = null
        var insideClass = false

        fun lookup(name: String): String? {
            for (i in scopes.indices.reversed()) {
                scopes[i].available[name]?.let { return it }
            }
            return null
        }

        fun classFor(type: String?): ClassStmt? = type?.let { classes[className(it)] }
        fun collectionFor(type: String?): CollectionDescription? =
            if (classFor(type) != null) null else CSharpCollections.describeCollection(type)
        fun isRef(type: String?): Boolean = classFor(type) != null || collectionFor(type) != null

        fun canAssign(type: String, target: String): Boolean =
            type == target ||
                (type == "int" && target in NUMERIC_TYPES) ||
                (type == "string" && target == "string?") ||
                (type == "null" && (target == "string?" || isRef(target))) ||
                (isRef(type) && nonNullable(type) == nonNullable(target))

        fun implicitField(name: String, token: Token): MemberExpr? {
            val receiverType = lookup("this") ?: return null
            val def = classFor(receiverType) ?: return null
            if (!def.fields.containsKey(name)) return null
            return MemberExpr(name, VarExpr("this", token, token.start, token.end), token, token.start, token.end)
        }

        lateinit var infer: (Expr, Boolean, Boolean) -> String

        fun verifyArgs(callToken: Token, callName: String, args: List<Expr>, params: List<ParamSpec>, def: ClassStmt?, owner: String?) {
            if (params.size != args.size) fail("مرّر العدد المطلوب من القيم إلى $callName.", callToken, "type")
            for ((i, arg) in args.withIndex()) {
                val p = params[i]
                if (!canAssign(infer(arg, false, false), closedType(p.type, def, owner))) {
                    fail("راجع نوع القيمة الممررة للمعامل «${p.name}».", arg.token, "type")
                }
            }
        }

        fun inferCall(node: CallExpr, allowVoid: Boolean = false): String {
            if (node.callKind == "array-search") {
                if (node.args.size != 2 || infer(node.args[0], false, false) != "int[]" || infer(node.args[1], false, false) != "int") {
                    fail("مرّر مصفوفة int[] وقيمة int إلى Array.IndexOf في هذا المسار.", node.token, "type")
                }
                return "int"
            }
            if (node.receiver is VarExpr && lookup(node.receiver.name) == null && implicitField(node.receiver.name, node.receiver.token) == null) {
                fail("عرّف الكائن قبل استدعاء طريقة منه.", node.receiver.token)
            }
            val ownerType = node.receiver?.let { infer(it, false, false) }
            if (arrayRank(ownerType) > 0 && node.name == "GetLength" && node.args.size == 1) {
                if (infer(node.args[0], false, false) != "int") fail("استخدم رقم بُعد من النوع int.", node.token, "type")
                node.callKind = "dimension"
                return "int"
            }
            val def = classFor(ownerType)
            val coll = collectionFor(ownerType)
            if (coll != null) {
                val sig = CSharpCollections.collectionSignature(coll, node.name)
                    ?: fail("طريقة ${node.name} غير مدعومة لهذه المجموعة.", node.token, "name")
                val paramSpecs = sig.argumentTypes.mapIndexed { idx, t -> ParamSpec(t, (idx + 1).toString(), node.token) }
                verifyArgs(node.token, node.name, node.args, paramSpecs, null, null)
                if (sig.resultType == "void" && !allowVoid) fail("استدعِ هذه الطريقة في أمر مستقل.", node.token, "type")
                node.callKind = "collection-method"
                return sig.resultType
            }
            val method = when {
                ownerType != null -> def?.methods?.get(node.name)
                !insideClass && lookup(node.name) == null -> functions[node.name]
                else -> null
            } ?: fail("الدالة «${node.name}» غير معرّفة هنا.", node.token, "name")
            verifyArgs(node.token, node.name, node.args, method.params, def, ownerType)
            val res = closedType(method.returnType, def, ownerType)
            if (res == "void" && !allowVoid) fail("استدعِ دالة void في سطر مستقل.", node.token, "type")
            return res
        }

        infer = { node: Expr, allowMin: Boolean, allowTryParse: Boolean ->
            val t: String = when (node) {
                is LitExpr -> {
                    if (node.type == "int") {
                        val v = node.value as Long
                        val maxAllowed = if (allowMin) 2147483648L else 2147483647L
                        if (v > maxAllowed || v < -2147483648L) fail("هذا العدد خارج نطاق int المدعوم هنا.", node.token, "unsupported")
                    }
                    node.type
                }
                is ReadLineExpr -> "string?"
                is CallExpr -> inferCall(node, false)
                is NewObjectExpr -> {
                    val def = classFor(node.type)
                    val coll = collectionFor(node.type)
                    if ((def == null && coll == null) || node.type.endsWith("?")) {
                        fail("استخدم new متبوعة باسم صنف صالح للإنشاء.", node.token, "type")
                    }
                    node.classNameResolved = def?.name ?: coll!!.name
                    verifyArgs(node.token, node.classNameResolved, node.args, def?.creator?.params ?: emptyList(), def, node.type)
                    node.type
                }
                is ArrayLitExpr -> {
                    val elem = elementType(node.arrayType)
                    for (row in node.rows) {
                        for (item in row) {
                            if (!canAssign(infer(item, false, false), elem)) fail("استخدم عناصر من نوع $elem في المصفوفة.", item.token, "type")
                        }
                    }
                    node.arrayType
                }
                is NewArrayExpr -> {
                    for (idx in node.indices) {
                        if (infer(idx, false, false) != "int") fail("استخدم فهرسًا أو طولًا من النوع int.", idx.token, "type")
                    }
                    node.arrayType
                }
                is TryParseExpr -> {
                    if (!allowTryParse) fail("في هذا الدرس خزّن نجاح TryParse في متغير bool مستقل، ثم افحصه.", node.token, "unsupported")
                    if (infer(node.inner, false, false) !in TEXT_TYPES) fail("اعطِ TryParse قيمة نصية.", node.token, "type")
                    "bool"
                }
                is VarExpr -> {
                    lookup(node.name) ?: run {
                        val imp = implicitField(node.name, node.token)
                        if (imp != null) {
                            node.implicitMember = imp
                            infer(imp, false, false)
                        } else {
                            fail("المتغير «${node.name}» غير معرّف قبل استخدامه.", node.token, "name")
                        }
                    }
                }
                is MemberExpr -> {
                    val owner = infer(node.receiver, false, false)
                    if (node.name == "Length" && ((owner in TEXT_TYPES && owner != "null") || arrayRank(owner) > 0)) {
                        node.memberKind = "length"
                        "int"
                    } else if (node.name == "Count" && collectionFor(owner) != null) {
                        node.memberKind = "collection-count"
                        "int"
                    } else {
                        val def = classFor(owner)
                        val field = def?.fields?.get(node.name)
                        if (def == null && node.name == "Length") fail("استخدم Length مع نص أو مصفوفة.", node.token, "type")
                        if (field == null) fail("الحقل «${node.name}» غير معرّف للنوع $owner.", node.token, "name")
                        closedType(field.type, def, owner)
                    }
                }
                is IndexExpr -> {
                    val owner = infer(node.receiver, false, false)
                    val coll = collectionFor(owner)
                    if (coll != null) {
                        if (coll.indexKeyType == null || node.indices.size != 1 || infer(node.indices[0], false, false) != coll.indexKeyType) {
                            fail("راجع نوع الفهرس أو المفتاح المسموح لهذه المجموعة.", node.token, "type")
                        }
                        coll.indexValueType!!
                    } else {
                        val rank = arrayRank(owner)
                        val expectedCount = if (rank > 0) rank else if (owner in TEXT_TYPES && owner != "null") 1 else 0
                        if (node.indices.size != expectedCount) {
                            fail("استخدم فهرسًا واحدًا للنص أو المصفوفة الخطية، وفهرسي الصف والعمود للمصفوفة الثنائية.", node.token, "type")
                        }
                        for (idx in node.indices) {
                            if (infer(idx, false, false) != "int") fail("استخدم فهرسًا من النوع int.", idx.token, "type")
                        }
                        if (rank > 0) elementType(owner) else "char"
                    }
                }
                is CastExpr -> {
                    val from = infer(node.inner, false, false)
                    if (from !in NUMERIC_TYPES) fail("هذا التحويل يحتاج قيمة عددية.", node.token, "type")
                    if (promoteNumbers(from, node.targetType) == null) fail("التحويل بين double وdecimal خارج نطاق المحاكي.", node.token, "unsupported")
                    node.targetType
                }
                is UnaryExpr -> {
                    val innerT = infer(node.inner, node.op == "-", false)
                    if (node.op == "!") {
                        if (innerT != "bool") fail("المعامل ! يحتاج قيمة bool ليعكسها.", node.token, "type")
                        "bool"
                    } else {
                        if (innerT !in NUMERIC_TYPES) fail("الإشارة العددية تحتاج قيمة عددية.", node.token, "type")
                        innerT
                    }
                }
                is BinaryExpr -> {
                    val left = infer(node.left, false, false)
                    val right = infer(node.right, false, false)
                    when {
                        node.op == "??" -> {
                            if (left !in TEXT_TYPES || right !in TEXT_TYPES) fail("في هذا الدرس استخدم ?? لاختيار نص بديل.", node.token, "type")
                            if (left == "string") "string" else right
                        }
                        node.op in LOGICAL -> {
                            if (left != "bool" || right != "bool") fail("المعامل ${node.op} يحتاج قيمة bool على كل طرف.", node.token, "type")
                            "bool"
                        }
                        node.op in COMPARISONS -> {
                            val num = promoteNumbers(left, right)
                            val compat = left == right || (left in TEXT_TYPES && right in TEXT_TYPES) ||
                                (isRef(left) && canAssign(right, left)) || (isRef(right) && canAssign(left, right))
                            val eq = (node.op == "==" || node.op == "!=") && compat
                            if (num == null && !eq) fail("قارن قيمتين متوافقتين فقط.", node.token, "type")
                            "bool"
                        }
                        node.op == "+" && (left in TEXT_TYPES || right in TEXT_TYPES) -> "string"
                        else -> {
                            val num = promoteNumbers(left, right)
                                ?: fail("هذه العملية تحتاج عددين من نوعين متوافقين أو + لضم نص.", node.token, "type")
                            if (num == "decimal" && node.op == "/") fail("قسمة decimal خارج نطاق المحاكي. استخدم double للقسمة.", node.token, "unsupported")
                            num
                        }
                    }
                }
            }
            node.valueType = t
            t
        }

        fun enterScope(decls: List<Pair<String, Token>>) {
            val all = mutableSetOf<String>()
            for ((n, tok) in decls) {
                if (n in all || scopes.any { n in it.allNames }) {
                    fail("المتغير «$n» موجود بالفعل في هذا النطاق.", tok, "name")
                }
                all += n
            }
            scopes += ScopeFrame(all, linkedMapOf())
        }

        fun namesIn(stmts: List<Stmt>): List<Pair<String, Token>> = stmts.flatMap { s ->
            when {
                s is DeclareStmt && s.init is TryParseExpr -> listOf(s.name to s.token, s.init.outName to s.init.token)
                s is DeclareStmt -> listOf(s.name to s.token)
                s is CallableStmt -> listOf(s.name to s.token)
                else -> emptyList()
            }
        }

        lateinit var visitStmt: (Stmt) -> Unit

        visitStmt = { s ->
            when (s) {
                is EmptyStmt, is CallableStmt, is ClassStmt -> Unit
                is BlockStmt -> {
                    enterScope(namesIn(s.body))
                    s.body.forEach(visitStmt)
                    scopes.removeAt(scopes.lastIndex)
                }
                is InvokeStmt -> { s.call.valueType = inferCall(s.call, allowVoid = true) }
                is ReturnStmt -> {
                    val fn = currentFunction ?: fail("اكتب return داخل جسم الدالة.", s.token)
                    val retType = s.expr?.let { infer(it, false, false) } ?: "void"
                    if (!canAssign(retType, fn.returnType)) fail("أعد قيمة من نوع ${fn.returnType} في هذه الدالة.", s.token, "type")
                }
                is BreakContinueStmt -> if (loopDepth == 0) fail("استخدم ${s.kind} داخل حلقة.", s.token)
                is IfStmt -> {
                    if (infer(s.cond, false, false) != "bool") fail("شرط if يحتاج قيمة bool.", s.token, "type")
                    visitStmt(s.consequent)
                    s.alternate?.let(visitStmt)
                }
                is LoopStmt -> {
                    if (s.kind == "foreach") {
                        val collType = infer(s.collectionExpr!!, false, false)
                        if (collType != "${s.iterType}[]" && collectionFor(collType)?.iteratorType != s.iterType) {
                            fail("اختر عناصر من النوع ${s.iterType} في المصفوفة أو المجموعة.", s.collectionExpr.token, "type")
                        }
                        enterScope(listOf(s.iterName!! to s.iterToken!!))
                        scopes.last().available[s.iterName] = s.iterType!!
                        scopes.last().readOnly = s.iterName
                    } else {
                        enterScope(namesIn(listOfNotNull(s.init)))
                        s.init?.let(visitStmt)
                        s.cond?.let { c -> if (infer(c, false, false) != "bool") fail("شرط الحلقة يحتاج قيمة bool.", c.token, "type") }
                        s.update?.let(visitStmt)
                    }
                    loopDepth++
                    visitStmt(s.body)
                    loopDepth--
                    scopes.removeAt(scopes.lastIndex)
                }
                is AssignMemberStmt -> {
                    val targetT = infer(s.target, false, false)
                    if (!canAssign(infer(s.expr, false, false), targetT)) fail("قيمة الحقل يجب أن توافق النوع $targetT.", s.token, "type")
                }
                is AssignIndexStmt -> {
                    val targetT = infer(s.target, false, false)
                    if (s.target.receiver.valueType in TEXT_TYPES) fail("خانات النص للقراءة فقط.", s.token, "type")
                    if (!canAssign(infer(s.expr, false, false), targetT)) fail("ضع قيمة من النوع $targetT في خانة المصفوفة.", s.token, "type")
                }
                is AssignStmt -> {
                    val existing = lookup(s.name)
                    if (existing == null) {
                        val imp = implicitField(s.name, s.token)
                        if (imp == null) fail("عرّف المتغير «${s.name}» قبل إسناد قيمة له.", s.token, "name")
                        if (s.updateOp != "=") fail("استخدم إسنادًا بسيطًا لتحديث الحقل.", s.token, "unsupported")
                        val fType = infer(imp, false, false)
                        if (!canAssign(infer(s.expr, false, false), fType)) fail("قيمة الحقل يجب أن توافق النوع $fType.", s.token, "type")
                    } else {
                        val ownerFrame = scopes.lastOrNull { it.available.containsKey(s.name) }
                        if (ownerFrame?.readOnly == s.name) fail("متغير foreach للقراءة فقط داخل الحلقة.", s.token, "type")
                        val exprType = infer(s.expr, false, false)
                        if (!canAssign(exprType, existing)) fail("القيمة من النوع $exprType لا تصلح للمتغير $existing.", s.token, "type")
                    }
                }
                is DeclareStmt -> {
                    val allowTryParse = s.type == "bool"
                    val exprType = infer(s.init, false, allowTryParse)
                    if (!canAssign(exprType, s.type)) fail("القيمة من النوع $exprType لا تصلح لمتغير من النوع ${s.type}.", s.token, "type")
                    scopes.last().available[s.name] = s.type
                    if (s.init is TryParseExpr) scopes.last().available[s.init.outName] = "int"
                }
                is PrintStmt -> {
                    s.expr?.let { infer(it, false, false) }
                }
            }
        }

        enterScope(namesIn(statements))
        statements.forEach(visitStmt)
        scopes.removeAt(scopes.lastIndex)

        fun guaranteesReturn(s: Stmt): Boolean = when (s) {
            is ReturnStmt -> true
            is BlockStmt -> s.body.any(::guaranteesReturn)
            is IfStmt -> s.alternate != null && guaranteesReturn(s.consequent) && guaranteesReturn(s.alternate)
            else -> false
        }

        fun visitCallable(fn: CallableStmt, def: ClassStmt?) {
            currentFunction = fn
            val names = fn.params.map { it.name to it.token }.toMutableList()
            if (def != null) names += "this" to fn.token
            enterScope(names)
            val avail = scopes.last().available
            fn.params.forEach { avail[it.name] = it.type }
            if (def != null) avail["this"] = def.name + (def.genericParam?.let { "<$it>" } ?: "")
            visitStmt(fn.body)
            scopes.removeAt(scopes.lastIndex)
            if (fn.returnType != "void" && !guaranteesReturn(fn.body)) {
                fail("أضف return لقيمة في كل مسار من الدالة ${fn.name}.", fn.token, "type")
            }
        }

        functions.values.forEach { visitCallable(it, null) }
        insideClass = true
        classes.values.forEach { def ->
            def.fields.values.forEach { f ->
                if (f.init != null && !canAssign(infer(f.init, false, false), f.type)) {
                    fail("راجع نوع القيمة الأولية للحقل «${f.name}».", f.token, "type")
                }
            }
            def.creator?.let { visitCallable(it, def) }
            def.methods.values.forEach { visitCallable(it, def) }
        }

        return functions to classes
    }

    fun runProgram(
        source: String,
        inputs: Map<String, JsonElement> = emptyMap(),
        stdin: String = ""
    ): CSharpRunResult {
        return try {
            runProgramInternal(source, inputs, stdin)
        } catch (err: CSharpError) {
            CSharpRunResult(output = "", trace = emptyList(), variables = emptyMap(), error = err)
        }
    }

    private fun runProgramInternal(
        source: String,
        inputs: Map<String, JsonElement>,
        stdin: String
    ): CSharpRunResult {
        val text = source.replace("\r\n", "\n").replace("\r", "\n")
        val statements = parseProgram(text)
        val (functions, classes) = validateTypes(statements)
        val sourceLines = text.split("\n")

        if (stdin.length > LIMIT_SOURCE) fail("اختصر مدخلات التشغيل إلى 16 ألف حرف أو أقل.", code = "limit")
        val rawInputLines = stdin.split(Regex("\r\n?|\n")).toMutableList()
        if (rawInputLines.isNotEmpty() && rawInputLines.last().isEmpty()) rawInputLines.removeAt(rawInputLines.lastIndex)
        var nextInput = 0
        fun readInputLine(): String? = if (nextInput < rawInputLines.size) rawInputLines[nextInput++] else null

        var scopes = mutableListOf<LinkedHashMap<String, CSharpSlot>>(linkedMapOf())
        data class LoopFrame(val kind: String, var iteration: Int, var phase: String, var control: Set<String>, val continueLine: Int, val exitLine: Int?)
        data class CallFrame(val fn: CallableStmt, val callerLine: Int, val receiver: CSharpObject?, val returnType: String)
        var loops = mutableListOf<LoopFrame>()
        val calls = mutableListOf<CallFrame>()
        var skipped = linkedSetOf<Int>()
        var allocatedObjects = 0
        var output = ""
        val trace = mutableListOf<TraceSnapshot>()

        fun visibleVars(): LinkedHashMap<String, CSharpSlot> {
            val map = linkedMapOf<String, CSharpSlot>()
            for (scope in scopes) map.putAll(scope)
            return map
        }

        fun ownerScope(name: String): LinkedHashMap<String, CSharpSlot>? =
            scopes.lastOrNull { it.containsKey(name) }

        fun firstLine(s: Stmt): Int = when (s) {
            is BlockStmt -> s.body.firstOrNull()?.token?.line ?: s.endToken.line
            else -> s.token.line
        }

        fun concreteType(type: String): String {
            val receiver = calls.lastOrNull()?.receiver ?: return type
            return closedType(type, classes[className(receiver.type)], receiver.type)
        }

        fun snapshotObjects(vars: Map<String, CSharpSlot>): List<TracedObject> {
            val visited = linkedMapOf<Int, TracedObject>()
            fun visit(v: Any?) {
                if (v !is CSharpObject || visited.containsKey(v.id)) return
                val fields = v.fields.entries.map { (k, slot) ->
                    TracedVariable(k, slot.type, traceValueText(slot.value, slot.type), slot.value)
                }
                val collSnap = v.collection?.let { c ->
                    TracedCollectionSnapshot(
                        kind = c.name,
                        count = CSharpCollections.collectionCount(c),
                        items = CSharpCollections.collectionSnapshot(c).map { it.toString() }
                    )
                }
                visited[v.id] = TracedObject(v.id, v.type, fields, collSnap)
                v.fields.values.forEach { visit(it.value) }
            }
            vars.values.forEach { visit(it.value) }
            return visited.values.sortedBy { it.id }
        }

        fun addSnapshot(
            line: Int,
            src: String,
            kind: String,
            name: String? = null,
            action: String,
            nextLine: Int? = null,
            reads: Set<String> = emptySet(),
            loopPhaseOverride: String? = null,
            clause: String? = null,
            comparisonSummary: String? = null,
            logicSummary: String? = null,
            calculationSummary: String? = null,
            accesses: List<ElementAccessRecord> = emptyList(),
            fields: List<FieldAccessRecord> = emptyList(),
            collectionOps: List<CollectionOpRecord> = emptyList()
        ) {
            if (trace.size >= LIMIT_TRACE) {
                fail("توقف المحاكي عند حد الخطوات. افحص شرط الحلقة وتأكد إن العداد بيتغير وبيوصل للنهاية.", Token("", "", line, 1, 0, 0), "limit")
            }
            val frame = loops.lastOrNull()
            val cFrame = calls.lastOrNull()
            val vars = visibleVars()
            val tracedVars = vars.entries.map { (k, slot) ->
                TracedVariable(k, slot.type, traceValueText(slot.value, slot.type), slot.value)
            }
            trace += TraceSnapshot(
                stepIndex = trace.size,
                line = line,
                source = src,
                kind = kind,
                name = name,
                action = action,
                output = output,
                nextLine = nextLine,
                reads = reads,
                variables = tracedVars,
                objects = snapshotObjects(vars),
                skippedLines = skipped.toSet(),
                loopKind = frame?.kind,
                loopIteration = frame?.iteration,
                loopDepth = if (frame != null) loops.size else null,
                loopPhase = loopPhaseOverride ?: if (frame?.phase == "body" && kind == "assign" && name != null && name in frame.control) "update" else frame?.phase,
                callName = cFrame?.fn?.name,
                callDepth = if (cFrame != null) calls.size else null,
                callPhase = if (cFrame != null) when (kind) {
                    "call" -> "enter"
                    "return" -> "return"
                    else -> "body"
                } else null,
                clause = clause,
                comparisonSummary = comparisonSummary,
                logicSummary = logicSummary,
                calculationSummary = calculationSummary,
                accesses = accesses,
                fields = fields,
                collectionOps = collectionOps
            )
        }

        fun boundedInt(v: Long, tok: Token?): Int {
            if (v < -2147483648L || v > 2147483647L) fail("الناتج خارج نطاق الحساب المدعوم بالمحاكي.", tok, "unsupported")
            return v.toInt()
        }

        fun convertNumber(value: Any?, from: String, to: String, tok: Token?): Any? {
            if (from == to) return value
            if (to == "decimal") {
                val big = when (value) {
                    is Int -> BigInteger.valueOf(value.toLong())
                    is Long -> BigInteger.valueOf(value)
                    is CSharpDecimal -> return value
                    else -> BigInteger.ZERO
                }
                return makeDecimal(big, 0, tok)
            }
            if (to == "int") {
                return when (value) {
                    is CSharpDecimal -> boundedInt((value.coefficient / BigInteger.TEN.pow(value.scale)).toLong(), tok)
                    is Number -> boundedInt(truncate(value.toDouble()).toLong(), tok)
                    else -> value
                }
            }
            if (to == "double" && value is Int) return value.toDouble()
            if (to == "double" && value is Long) return value.toDouble()
            return value
        }

        fun defaultVal(type: String, tok: Token?): Any? = when {
            type == "bool" -> false
            type == "char" -> "\u0000"
            type == "decimal" -> makeDecimal(BigInteger.ZERO, 0, tok)
            type == "double" -> 0.0
            type in NUMERIC_TYPES -> 0
            else -> null
        }

        fun fromJsonElement(el: JsonElement, type: String, tok: Token?): Any? {
            if (el is JsonNull) return null
            val rank = arrayRank(type)
            if (rank > 0 && el is JsonArray) {
                val elem = elementType(type)
                return if (rank == 1) {
                    el.map { fromJsonElement(it, elem, tok) }.toMutableList()
                } else {
                    val rows = el.map { r -> (r as JsonArray).map { fromJsonElement(it, elem, tok) }.toMutableList() }.toMutableList()
                    CSharpArray2D(rows, rows.firstOrNull()?.size ?: 0)
                }
            }
            if (el is JsonPrimitive) {
                return when (type) {
                    "int" -> el.intOrNull ?: fail("قيمة الاختبار لا تطابق نوع المدخل.", tok, "type")
                    "double" -> el.doubleOrNull ?: fail("قيمة الاختبار لا تطابق نوع المدخل.", tok, "type")
                    "decimal" -> parseDecimal(el.content, tok)
                    "bool" -> el.booleanOrNull ?: fail("قيمة الاختبار لا تطابق نوع المدخل.", tok, "type")
                    "char" -> el.content.takeIf { it.length == 1 } ?: fail("قيمة الاختبار لا تطابق نوع المدخل.", tok, "type")
                    "string", "string?" -> el.content
                    else -> el.content
                }
            }
            fail("قيمة الاختبار لا تطابق نوع المدخل.", tok, "type")
        }

        data class EvalResult(
            val value: Any?,
            val reads: MutableSet<String> = linkedSetOf(),
            var comparisonSummary: String? = null,
            val logicList: MutableList<String> = mutableListOf(),
            var calculationSummary: String? = null,
            var binding: Pair<String, Int>? = null,
            val accesses: MutableList<ElementAccessRecord> = mutableListOf(),
            val fields: MutableList<FieldAccessRecord> = mutableListOf(),
            val collectionOps: MutableList<CollectionOpRecord> = mutableListOf()
        )

        lateinit var executeStmt: (Stmt, Int?, String) -> Any?
        lateinit var invokeCall: (Token, String, List<Expr>, List<Any?>, CSharpObject?, Boolean) -> Any?
        lateinit var evaluate: (Expr, Boolean) -> EvalResult

        fun createObject(node: NewObjectExpr, args: List<Any?>): CSharpObject {
            if (allocatedObjects >= LIMIT_OBJECTS) fail("وصل المحاكي إلى حد الكائنات في المثال.", node.token, "limit")
            val type = concreteType(node.type)
            val def = classes[className(type)]
            val obj = CSharpObject(id = ++allocatedObjects, type = type)
            if (def == null) {
                obj.collection = CSharpCollections.makeCollection(CSharpCollections.describeCollection(type)!!)
                return obj
            }
            for (field in def.fields.values) {
                val fType = closedType(field.type, def, type)
                val fVal = if (field.init != null) {
                    val ev = evaluate(field.init, false)
                    convertNumber(ev.value, concreteType(field.init.valueType), fType, field.token)
                } else {
                    defaultVal(fType, field.token)
                }
                obj.fields[field.name] = CSharpSlot(fType, fVal)
            }
            if (def.creator != null) {
                invokeCall(node.token, def.name, node.args, args, obj, true)
            }
            return obj
        }

        fun executeBody(body: List<Stmt>, nextLine: Int?): Any? {
            val cmds = body.filter { it !is CallableStmt && it !is ClassStmt }
            for ((idx, cmd) in cmds.withIndex()) {
                val next = cmds.getOrNull(idx + 1)?.token?.line ?: nextLine
                val jump = executeStmt(cmd, next, "if")
                if (jump != null) return jump
            }
            return null
        }

        fun executeBlock(s: BlockStmt, nextLine: Int?, skipExitSnapshot: Boolean = false): Any? {
            scopes.add(linkedMapOf())
            val jump = try {
                executeBody(s.body, if (skipExitSnapshot) nextLine else s.endToken.line)
            } finally {
                scopes.removeAt(scopes.lastIndex)
            }
            if (jump == null && !skipExitSnapshot) {
                addSnapshot(
                    line = s.endToken.line,
                    src = "}",
                    kind = "scope-exit",
                    action = "يا كمبيوتر، انتهت الكتلة. أكمل من الأمر التالي، واحتفظ بتحديثات المتغيرات الخارجية.",
                    nextLine = nextLine
                )
            }
            return jump
        }

        invokeCall = { token, name, argExprs, argValues, receiver, isConstructor ->
            val def = receiver?.let { classes[className(it.type)] }
            val fn = when {
                isConstructor -> def!!.creator!!
                receiver != null -> def!!.methods[name]!!
                else -> functions[name]!!
            }
            if (calls.size >= LIMIT_CALLS) fail("توقف المحاكي عند حد الاستدعاءات المتداخلة.", token, "limit")
            val savedScopes = scopes
            val savedLoops = loops
            val savedSkipped = skipped
            val callScope = linkedMapOf<String, CSharpSlot>()
            fn.params.forEachIndexed { i, p ->
                val pType = closedType(p.type, def, receiver?.type)
                callScope[p.name] = CSharpSlot(pType, convertNumber(argValues[i], concreteType(argExprs[i].valueType), pType, argExprs[i].token))
            }
            if (receiver != null) callScope["this"] = CSharpSlot(receiver.type, receiver)
            val callerLine = token.line
            calls += CallFrame(fn, callerLine, receiver, closedType(fn.returnType, def, receiver?.type))
            scopes = mutableListOf(callScope)
            loops = mutableListOf()
            skipped = savedSkipped.filterTo(linkedSetOf()) { it !in fn.token.line..fn.endToken.line }
            try {
                val target = firstLine(fn.body)
                addSnapshot(
                    line = fn.token.line,
                    src = fn.header,
                    kind = "call",
                    action = "يا كمبيوتر، ادخل ${if (isConstructor) "المنشئ " else if (receiver != null) "الطريقة " else "الدالة "}${fn.name} وابدأ من السطر $target.",
                    nextLine = target
                )
                val res = executeBlock(fn.body, callerLine, skipExitSnapshot = true)
                if (res is Pair<*, *> && res.first == "return") {
                    res.second
                } else {
                    addSnapshot(
                        line = fn.endToken.line,
                        src = "}",
                        kind = "return",
                        action = "يا كمبيوتر، انتهت الدالة. ارجع للسطر $callerLine.",
                        nextLine = callerLine
                    )
                    Unit
                }
            } finally {
                scopes = savedScopes
                loops = savedLoops
                skipped = savedSkipped
                calls.removeAt(calls.lastIndex)
            }
        }

        evaluate = { root: Expr, addressOnly: Boolean ->
            val vars = visibleVars()
            val res = EvalResult(null)

            fun eval(node: Expr): Any? = when (node) {
                is LitExpr -> if (node.type == "int") (node.value as Long).toInt() else node.value
                is ReadLineExpr -> readInputLine()
                is TryParseExpr -> {
                    val raw = eval(node.inner) as String?
                    val trimmed = raw?.trim()
                    val parsed = trimmed?.toLongOrNull()
                    val valid = parsed != null && parsed in -2147483648L..2147483647L
                    val outVal = if (valid) parsed!!.toInt() else 0
                    res.binding = node.outName to outVal
                    res.calculationSummary = "${node.inner.valueType} TryParse(${raw ?: "null"}) -> ${displayValue(valid, "bool")} (${node.outName} = $outVal)"
                    valid
                }
                is VarExpr -> {
                    if (node.implicitMember != null) {
                        eval(node.implicitMember!!)
                    } else {
                        res.reads += node.name
                        vars[node.name]!!.value
                    }
                }
                is MemberExpr -> {
                    val receiverObj = eval(node.receiver)
                    if (node.memberKind == "length") {
                        when (receiverObj) {
                            null -> fail("القيمة null. افحصها قبل قراءة الطول.", node.token, "runtime")
                            is String -> receiverObj.length
                            is List<*> -> receiverObj.size
                            is CSharpArray2D -> receiverObj.rows.size * receiverObj.columns
                            else -> 0
                        }
                    } else if (node.memberKind == "collection-count") {
                        val obj = receiverObj as? CSharpObject ?: fail("المرجع null.", node.token, "runtime")
                        CSharpCollections.collectionCount(obj.collection!!)
                    } else {
                        val obj = receiverObj as? CSharpObject ?: fail("المرجع null. افحصه قبل الوصول إلى الحقل «${node.name}».", node.token, "runtime")
                        if (addressOnly && node === root) Pair(obj, node.name)
                        else {
                            val slot = obj.fields[node.name]!!
                            res.fields += FieldAccessRecord(obj.id, node.name, "read", traceValueText(slot.value, slot.type))
                            slot.value
                        }
                    }
                }
                is CallExpr -> {
                    when (node.callKind) {
                        "array-search" -> {
                            val items = eval(node.args[0]) as? List<*> ?: fail("مرّر مصفوفة موجودة للبحث.", node.token, "runtime")
                            val wanted = eval(node.args[1])
                            items.indexOf(wanted)
                        }
                        "dimension" -> {
                            val arr = eval(node.receiver!!) ?: fail("القيمة null.", node.token, "runtime")
                            val axis = (eval(node.args[0]) as Number).toInt()
                            val dims = if (arr is CSharpArray2D) listOf(arr.rows.size, arr.columns) else listOf((arr as List<*>).size)
                            if (axis < 0 || axis >= dims.size) fail("البعد $axis غير موجود في هذه المصفوفة.", node.token, "runtime")
                            dims[axis]
                        }
                        "collection-method" -> {
                            val obj = eval(node.receiver!!) as? CSharpObject ?: fail("المرجع null. افحصه قبل استعمال المجموعة.", node.token, "runtime")
                            val args = node.args.map(::eval)
                            val r = CSharpCollections.applyMethod(obj.collection!!, node.name, args, LIMIT_ARRAY) { m, c -> fail(m, node.token, c) }
                            res.collectionOps += CollectionOpRecord(obj.id, node.name, r.previousCount, r.count, r.value.toString())
                            r.value
                        }
                        else -> {
                            val recv = node.receiver?.let { eval(it) as? CSharpObject ?: fail("المرجع null.", node.token, "runtime") }
                            val args = node.args.map(::eval)
                            invokeCall(node.token, node.name, node.args, args, recv, false)
                        }
                    }
                }
                is NewObjectExpr -> createObject(node, node.args.map(::eval))
                is ArrayLitExpr -> {
                    val elem = elementType(node.arrayType)
                    if (!node.is2D) {
                        node.rows.first().map { convertNumber(eval(it), it.valueType, elem, it.token) }.toMutableList()
                    } else {
                        val rows = node.rows.map { r -> r.map { convertNumber(eval(it), it.valueType, elem, it.token) }.toMutableList() }.toMutableList()
                        CSharpArray2D(rows, rows.firstOrNull()?.size ?: 0)
                    }
                }
                is NewArrayExpr -> {
                    val dims = node.indices.map { (eval(it) as Number).toInt() }
                    if (dims.any { it < 0 }) fail("اكتب طولًا يساوي صفرًا أو أكبر.", node.token, "runtime")
                    if (dims.any { it > LIMIT_ARRAY } || dims.fold(1) { acc, d -> acc * d } > LIMIT_ARRAY) {
                        fail("اختر مصفوفة تعليمية من 64 عنصرًا أو أقل.", node.token, "limit")
                    }
                    if (dims.size == 1) MutableList<Any?>(dims[0]) { 0 }
                    else CSharpArray2D(MutableList(dims[0]) { MutableList<Any?>(dims[1]) { 0 } }, dims[1])
                }
                is IndexExpr -> {
                    val recv = eval(node.receiver) ?: fail("القيمة null. افحصها قبل محاولة القراءة.", node.token, "runtime")
                    val idxs = node.indices.map { eval(it) }
                    if (addressOnly && node === root) {
                        Triple(recv, idxs, node)
                    } else {
                        val v = when (recv) {
                            is CSharpObject -> CSharpCollections.readIndex(recv.collection!!, idxs[0]) { m, c -> fail(m, node.token, c) }
                            is String -> {
                                val i = idxs[0] as Int
                                if (i < 0 || i >= recv.length) fail("الفهرس $i خارج حدود النص.", node.token, "runtime")
                                recv[i].toString()
                            }
                            is List<*> -> {
                                val i = idxs[0] as Int
                                if (i < 0 || i >= recv.size) fail("الفهرس $i خارج حدود المصفوفة.", node.token, "runtime")
                                recv[i]
                            }
                            is CSharpArray2D -> {
                                val r = idxs[0] as Int; val c = idxs[1] as Int
                                if (r < 0 || r >= recv.rows.size || c < 0 || c >= recv.columns) fail("الفهرس [$r, $c] خارج حدود المصفوفة.", node.token, "runtime")
                                recv.rows[r][c]
                            }
                            else -> fail("نوع غير قابل للفهرسة.", node.token, "type")
                        }
                        if (idxs.all { it is Int }) {
                            res.accesses += ElementAccessRecord((node.receiver as? VarExpr)?.name, idxs.map { it as Int }, "read", traceValueText(v, node.valueType))
                        }
                        v
                    }
                }
                is CastExpr -> convertNumber(eval(node.inner), node.inner.valueType, node.targetType, node.token)
                is UnaryExpr -> {
                    val v = eval(node.inner)
                    if (node.op == "!") {
                        val b = !(v as Boolean)
                        res.logicList += "!($v) -> ${displayValue(b, "bool")}"
                        b
                    } else if (node.valueType == "decimal") {
                        val d = v as CSharpDecimal
                        if (node.op == "-") CSharpDecimal(d.coefficient.negate(), d.scale) else d
                    } else if (node.valueType == "double") {
                        if (node.op == "-") -(v as Number).toDouble() else (v as Number).toDouble()
                    } else {
                        val l = (v as Number).toLong()
                        boundedInt(if (node.op == "-") -l else l, node.token)
                    }
                }
                is BinaryExpr -> {
                    val left = eval(node.left)
                    if (node.op == "??") {
                        val right = if (left == null) eval(node.right) else null
                        val out = left ?: right
                        res.calculationSummary = "${left ?: "null"} ?? ${right ?: "لم يُحسب"} -> $out"
                        out
                    } else if (node.op in LOGICAL) {
                        val lb = left as Boolean
                        val skipRight = if (node.op == "&&") !lb else lb
                        val right = if (skipRight) null else (eval(node.right) as Boolean)
                        val out = right ?: lb
                        res.logicList += "$lb ${node.op} ${right ?: "(توقف مختصر)"} -> ${displayValue(out, "bool")}"
                        out
                    } else {
                        val right = eval(node.right)
                        val lType = node.left.valueType
                        val rType = node.right.valueType
                        val result: Any? = when {
                            node.op == "+" && node.valueType == "string" -> {
                                val a = displayValue(left, lType)
                                val b = displayValue(right, rType)
                                if (a.length + b.length > LIMIT_OUTPUT) fail("قيمة النص أكبر من حدود المحاكي.", node.token, "limit")
                                a + b
                            }
                            promoteNumbers(lType, rType) == "decimal" -> {
                                val a = convertNumber(left, lType, "decimal", node.token) as CSharpDecimal
                                val b = convertNumber(right, rType, "decimal", node.token) as CSharpDecimal
                                if (node.op == "*") makeDecimal(a.coefficient.multiply(b.coefficient), a.scale + b.scale, node.token)
                                else {
                                    val s = maxOf(a.scale, b.scale)
                                    val x = a.coefficient.multiply(BigInteger.TEN.pow(s - a.scale))
                                    val y = b.coefficient.multiply(BigInteger.TEN.pow(s - b.scale))
                                    when (node.op) {
                                        "==" -> x == y; "!=" -> x != y; ">" -> x > y; ">=" -> x >= y; "<" -> x < y; "<=" -> x <= y
                                        "+" -> makeDecimal(x.add(y), s, node.token)
                                        "-" -> makeDecimal(x.subtract(y), s, node.token)
                                        else -> {
                                            if (y == BigInteger.ZERO) fail("لا يمكن حساب باقي القسمة على صفر.", node.token, "runtime")
                                            makeDecimal(x.remainder(y), s, node.token)
                                        }
                                    }
                                }
                            }
                            node.op in COMPARISONS -> {
                                if (lType in NUMERIC_TYPES && rType in NUMERIC_TYPES && left is Number && right is Number) {
                                    val ld = left.toDouble(); val rd = right.toDouble()
                                    when (node.op) {
                                        "==" -> ld == rd; "!=" -> ld != rd; ">" -> ld > rd; ">=" -> ld >= rd; "<" -> ld < rd; "<=" -> ld <= rd
                                        else -> false
                                    }
                                } else {
                                    when (node.op) {
                                        "==" -> left == right; "!=" -> left != right
                                        else -> false
                                    }
                                }
                            }
                            node.valueType == "int" -> {
                                val li = (left as Number).toLong(); val ri = (right as Number).toLong()
                                if ((node.op == "/" || node.op == "%") && ri == 0L) fail("لا يمكن قسمة int على صفر.", node.token, "runtime")
                                val rawRes = when (node.op) {
                                    "+" -> li + ri; "-" -> li - ri; "*" -> li * ri; "/" -> li / ri; "%" -> li % ri; else -> 0L
                                }
                                boundedInt(rawRes, node.token)
                            }
                            else -> {
                                val ld = (left as Number).toDouble(); val rd = (right as Number).toDouble()
                                when (node.op) {
                                    "+" -> ld + rd; "-" -> ld - rd; "*" -> ld * rd; "/" -> ld / rd; "%" -> ld % rd; else -> 0.0
                                }
                            }
                        }
                        if (node === root && node.op in COMPARISONS) {
                            res.comparisonSummary = "${displayValue(left, lType)} ${node.op} ${displayValue(right, rType)} -> ${displayValue(result, "bool")}"
                        }
                        if (node === root && (node.op == "/" || node.valueType == "decimal" || (node.op == "+" && node.valueType == "string"))) {
                            res.calculationSummary = "${displayValue(left, lType)} ${node.op} ${displayValue(right, rType)} = ${displayValue(result, node.valueType)}"
                        }
                        result
                    }
                }
            }

            EvalResult(eval(root)).also { it.reads.addAll(res.reads); it.comparisonSummary = res.comparisonSummary; it.logicList.addAll(res.logicList); it.calculationSummary = res.calculationSummary; it.binding = res.binding; it.accesses.addAll(res.accesses); it.fields.addAll(res.fields); it.collectionOps.addAll(res.collectionOps) }
        }

        fun skipLineRange(startTok: Token, endTok: Token) {
            val before = sourceLines.getOrNull(startTok.line - 1)?.take((startTok.column - 1).coerceAtLeast(0)) ?: ""
            val afterIdx = (endTok.column - 1 + (endTok.end - endTok.start)).coerceAtLeast(0)
            val after = sourceLines.getOrNull(endTok.line - 1)?.drop(afterIdx) ?: ""
            val from = startTok.line + if (before.isNotBlank()) 1 else 0
            val to = endTok.line - if (after.isNotBlank()) 1 else 0
            for (l in from..to) skipped += l
        }

        fun executeLoop(s: LoopStmt, nextLine: Int?): Any? {
            val continueLine = s.update?.token?.line ?: s.token.line
            val frame = LoopFrame(s.kind, 0, "initialize", emptySet(), continueLine, nextLine)
            loops += frame
            scopes += linkedMapOf()
            try {
                s.init?.let { executeStmt(it, s.token.line, "if") }
                val collEval = s.collectionExpr?.let { evaluate(it, false) }
                if (collEval != null && collEval.value == null) fail("افحص المرجع قبل المرور على عناصره.", s.token, "runtime")
                val obj = collEval?.value as? CSharpObject
                val versioned = obj?.collection
                val iterItems: List<Any?>? = when {
                    versioned != null -> CSharpCollections.collectionIteration(versioned)
                    collEval?.value is List<*> -> (collEval.value as List<*>)
                    else -> null
                }
                val startRev = versioned?.revision

                while (true) {
                    if (versioned != null && versioned.revision != startRev) {
                        fail("تغيّرت المجموعة أثناء foreach. أكمل المرور قبل تعديلها.", s.token, "runtime")
                    }
                    skipped.removeAll { it in s.body.token.line..s.body.endToken.line }
                    val condEval = when {
                        iterItems != null -> EvalResult(frame.iteration < iterItems.size, collEval?.reads ?: mutableSetOf())
                        s.cond != null -> evaluate(s.cond, false)
                        else -> EvalResult(true)
                    }
                    frame.control = condEval.reads
                    val passed = condEval.value as Boolean
                    if (passed) frame.iteration++ else skipLineRange(s.body.token, s.body.endToken)
                    val target = if (passed) (if (iterItems != null) s.token.line else firstLine(s.body)) else nextLine
                    val action = if (iterItems != null) {
                        if (passed) "يا كمبيوتر، باقي عنصر. اقرأ القيمة التالية." else "يا كمبيوتر، مفيش عناصر باقية. اخرج من الحلقة."
                    } else {
                        "يا كمبيوتر، شرط ${s.kind} أعطى ${displayValue(passed, "bool")}. ${if (passed) "نفّذ جسم الحلقة." else "اخرج من الحلقة."}"
                    }
                    frame.phase = "condition"
                    addSnapshot(
                        line = s.token.line, src = s.header, kind = "loop-condition",
                        action = action, nextLine = target, reads = condEval.reads,
                        comparisonSummary = condEval.comparisonSummary,
                        logicSummary = condEval.logicList.joinToString(" · ").takeIf { it.isNotEmpty() }
                    )
                    if (!passed) break

                    if (iterItems != null) {
                        val itemVal = iterItems[frame.iteration - 1]
                        scopes.last()[s.iterName!!] = CSharpSlot(s.iterType!!, itemVal)
                        addSnapshot(
                            line = s.token.line, src = s.header, kind = "loop-bind",
                            name = s.iterName, loopPhaseOverride = "bind",
                            action = "يا كمبيوتر، اقرأ قيمة العنصر رقم ${frame.iteration} وضعها في ${s.iterName}.",
                            nextLine = firstLine(s.body)
                        )
                    }
                    frame.phase = "body"
                    val jump = if (s.body is BlockStmt) executeBlock(s.body, frame.continueLine, skipExitSnapshot = true)
                    else executeStmt(s.body, frame.continueLine, "if")
                    if (iterItems != null) scopes.last().remove(s.iterName)
                    if (jump is Pair<*, *> && jump.first == "return") return jump
                    if (jump == "break") break
                    if (s.update != null) {
                        frame.phase = "update"
                        executeStmt(s.update, s.token.line, "if")
                    }
                    addSnapshot(
                        line = s.body.endToken.line,
                        src = if (s.body is BlockStmt) "}" else s.body.sourceText,
                        kind = "loop-jump",
                        loopPhaseOverride = "return",
                        nextLine = s.token.line,
                        action = "يا كمبيوتر، ارجع للسطر ${s.token.line} عشان تفحص الشرط من جديد."
                    )
                }
            } finally {
                scopes.removeAt(scopes.lastIndex)
                loops.removeAt(loops.lastIndex)
            }
            return null
        }

        executeStmt = { s: Stmt, nextLine: Int?, clause: String ->
            when (s) {
                is BlockStmt -> executeBlock(s, nextLine)
                is LoopStmt -> executeLoop(s, nextLine)
                is EmptyStmt -> {
                    addSnapshot(s.token.line, s.sourceText, "empty", action = "يا كمبيوتر، هذه جملة فارغة؛ انتقل إلى الأمر التالي.", nextLine = nextLine)
                    null
                }
                is InvokeStmt -> {
                    val ev = evaluate(s.call, false)
                    addSnapshot(s.token.line, s.sourceText, "invoke", action = "يا كمبيوتر، أكمل بعد استدعاء ${s.call.name}.", nextLine = nextLine, reads = ev.reads, collectionOps = ev.collectionOps)
                    null
                }
                is ReturnStmt -> {
                    val cFrame = calls.last()
                    val ev = s.expr?.let { evaluate(it, false) } ?: EvalResult(Unit)
                    val value = if (s.expr != null) convertNumber(ev.value, concreteType(s.expr.valueType), cFrame.returnType, s.token) else Unit
                    val what = if (s.expr != null) " بقيمة ${displayValue(value, cFrame.returnType)}" else ""
                    addSnapshot(s.token.line, s.sourceText, "return", action = "يا كمبيوتر، ارجع للسطر ${cFrame.callerLine}$what.", nextLine = cFrame.callerLine, reads = ev.reads)
                    "return" to value
                }
                is BreakContinueStmt -> {
                    val frame = loops.last()
                    val target = if (s.kind == "break") frame.exitLine else frame.continueLine
                    val act = if (s.kind == "break") "يا كمبيوتر، اخرج من أقرب حلقة الآن." else "يا كمبيوتر، تجاوز بقية أوامر الدورة الحالية."
                    addSnapshot(s.token.line, s.sourceText, s.kind, loopPhaseOverride = s.kind, action = act, nextLine = target)
                    s.kind
                }
                is IfStmt -> {
                    val ev = evaluate(s.cond, false)
                    val passed = ev.value as Boolean
                    val chosen = if (passed) s.consequent else s.alternate
                    val hasNextCond = s.alternate is IfStmt
                    if (passed && s.alternate != null) skipLineRange(s.elseToken!!, s.alternate.endToken)
                    if (!passed) skipLineRange(s.consequent.token, s.consequent.endToken)
                    val target: Int? = when {
                        passed -> firstLine(s.consequent)
                        hasNextCond -> s.alternate!!.token.line
                        s.alternate != null -> s.elseToken!!.line
                        else -> nextLine
                    }
                    val branchType = when {
                        passed -> "if"
                        hasNextCond -> "else-if"
                        s.alternate != null -> "else"
                        else -> "skip"
                    }
                    val inst = when (branchType) {
                        "if" -> "نفّذ جسم $clause."
                        "else-if" -> "افحص شرط else if التالي."
                        "else" -> "انتقل إلى else."
                        else -> "تجاوز الجسم."
                    }
                    addSnapshot(
                        line = s.token.line,
                        src = if (clause == "else if") "else ${s.header}" else s.header,
                        kind = "condition",
                        action = "يا كمبيوتر، نتيجة الشرط ${displayValue(passed, "bool")}. $inst",
                        nextLine = target,
                        reads = ev.reads,
                        clause = clause,
                        comparisonSummary = ev.comparisonSummary,
                        logicSummary = ev.logicList.joinToString(" · ").takeIf { it.isNotEmpty() }
                    )
                    if (branchType == "else") {
                        addSnapshot(s.elseToken!!.line, "else", "else", action = "يا كمبيوتر، نفّذ جسم else لأن الشروط السابقة لم تتحقق.")
                    }
                    if (chosen != null) executeStmt(chosen, nextLine, if (branchType == "else-if") "else if" else "if") else null
                }
                is AssignMemberStmt -> {
                    val targetAddr = evaluate(s.target, true)
                    val (obj, fName) = targetAddr.value as Pair<CSharpObject, String>
                    val ev = evaluate(s.expr, false)
                    val slot = obj.fields[fName]!!
                    slot.value = convertNumber(ev.value, concreteType(s.expr.valueType), slot.type, s.token)
                    val w = FieldAccessRecord(obj.id, fName, "write", traceValueText(slot.value, slot.type))
                    addSnapshot(s.token.line, s.sourceText, "assign-member", name = fName, action = "يا كمبيوتر، حدّث الحقل $fName في الكائن #${obj.id}.", nextLine = nextLine, reads = targetAddr.reads + ev.reads, fields = ev.fields + w)
                    null
                }
                is AssignIndexStmt -> {
                    val targetAddr = evaluate(s.target, true)
                    val (recv, idxs, _) = targetAddr.value as Triple<Any, List<Any?>, IndexExpr>
                    val ev = evaluate(s.expr, false)
                    val tType = concreteType(s.target.valueType)
                    val value = convertNumber(ev.value, concreteType(s.expr.valueType), tType, s.token)
                    if (recv is CSharpObject && recv.collection != null) {
                        CSharpCollections.writeIndex(recv.collection!!, idxs[0], value, LIMIT_ARRAY) { m, c -> fail(m, s.token, c) }
                    } else if (recv is MutableList<*>) {
                        val idx = idxs[0] as Int
                        if (idx < 0 || idx >= recv.size) fail("الفهرس $idx خارج الحدود.", s.token, "runtime")
                        @Suppress("UNCHECKED_CAST") (recv as MutableList<Any?>)[idx] = value
                    } else if (recv is CSharpArray2D) {
                        val r = idxs[0] as Int; val c = idxs[1] as Int
                        if (r < 0 || r >= recv.rows.size || c < 0 || c >= recv.columns) fail("الفهرس [$r, $c] خارج الحدود.", s.token, "runtime")
                        recv.rows[r][c] = value
                    }
                    val w = ElementAccessRecord(s.name, idxs.filterIsInstance<Int>(), "write", traceValueText(value, tType))
                    addSnapshot(s.token.line, s.sourceText, "assign-index", name = s.name, action = "يا كمبيوتر، حدّث ${s.name}[${idxs.joinToString(", ")}] واحفظ قيمة الخانة الجديدة.", nextLine = nextLine, reads = targetAddr.reads + ev.reads, accesses = ev.accesses + w)
                    null
                }
                is DeclareStmt -> {
                    val varsNow = visibleVars()
                    val exprType = concreteType(s.init.valueType)
                    val type = concreteType(s.type)
                    val overrideEl = if (s.init !is TryParseExpr) inputs[s.name] else null
                    val ev = if (overrideEl != null) {
                        EvalResult(fromJsonElement(overrideEl, type, s.token))
                    } else {
                        evaluate(s.init, false)
                    }
                    val value = if (overrideEl == null && type != exprType) convertNumber(ev.value, exprType, type, s.token) else ev.value
                    scopes.last()[s.name] = CSharpSlot(type, value)
                    ev.binding?.let { (outName, outVal) -> scopes.last()[outName] = CSharpSlot("int", outVal) }
                    addSnapshot(
                        line = s.token.line, src = s.sourceText, kind = "declare", name = s.name,
                        action = "يا كمبيوتر، أنشئ المتغير ${s.name} من النوع ${s.type} واحفظ فيه ${traceValueText(value, type)}.",
                        nextLine = nextLine, reads = ev.reads, calculationSummary = ev.calculationSummary,
                        accesses = ev.accesses, fields = ev.fields, collectionOps = ev.collectionOps
                    )
                    null
                }
                is AssignStmt -> {
                    val owner = ownerScope(s.name)
                    if (owner == null) {
                        // implicit field write on `this`
                        val thisObj = visibleVars()["this"]?.value as CSharpObject
                        val slot = thisObj.fields[s.name]!!
                        val ev = evaluate(s.expr, false)
                        slot.value = convertNumber(ev.value, concreteType(s.expr.valueType), slot.type, s.token)
                        addSnapshot(s.token.line, s.sourceText, "assign-member", name = s.name, action = "يا كمبيوتر، حدّث الحقل ${s.name}.", nextLine = nextLine, reads = ev.reads)
                    } else {
                        val slot = owner[s.name]!!
                        val previous = slot.value
                        val ev = evaluate(s.expr, false)
                        val value = convertNumber(ev.value, concreteType(s.expr.valueType), slot.type, s.token)
                        slot.value = value
                        addSnapshot(
                            line = s.token.line, src = s.sourceText, kind = "assign", name = s.name,
                            action = "يا كمبيوتر، حدّث ${s.name} من ${traceValueText(previous, slot.type)} إلى ${traceValueText(value, slot.type)}.",
                            nextLine = nextLine, reads = ev.reads, calculationSummary = ev.calculationSummary
                        )
                    }
                    null
                }
                is PrintStmt -> {
                    val ev = s.expr?.let { evaluate(it, false) } ?: EvalResult("")
                    val outStr = if (s.expr != null) displayValue(ev.value, concreteType(s.expr.valueType)) else ""
                    output += outStr + if (s.method == "WriteLine") "\n" else ""
                    if (output.length > LIMIT_OUTPUT) fail("المخرجات أكبر من حدود المحاكي.", s.token, "limit")
                    addSnapshot(
                        line = s.token.line, src = s.sourceText, kind = "print",
                        action = "يا كمبيوتر، اطبع القيمة الحالية" + if (s.method == "WriteLine") " ثم انتقل لسطر جديد." else " واترك موضع الكتابة في السطر نفسه.",
                        nextLine = nextLine, reads = ev.reads, calculationSummary = ev.calculationSummary,
                        accesses = ev.accesses, fields = ev.fields, collectionOps = ev.collectionOps
                    )
                    null
                }
                else -> null
            }
        }

        executeBody(statements, null)
        val topVars = scopes[0].entries.associate { (k, slot) ->
            k to TracedVariable(k, slot.type, traceValueText(slot.value, slot.type), slot.value)
        }
        return CSharpRunResult(output = output, trace = trace, variables = topVars)
    }

    fun checkChallenge(
        source: String,
        challenge: CSharpChallenge?,
        baselineOutput: String? = null,
        stdin: String = challenge?.stdin ?: ""
    ): CSharpChallengeResult {
        val result = runProgram(source, emptyMap(), stdin)
        if (result.error != null) {
            return CSharpChallengeResult(passed = false, result = result, message = result.error.message ?: "خطأ في البرنامج")
        }
        if (challenge == null) {
            return CSharpChallengeResult(passed = true, result = result, message = "تم التشغيل بنجاح.")
        }

        if (challenge.output != null && result.output != challenge.output) {
            return CSharpChallengeResult(false, result, "شُغّل الكود، لكن الناتج يحتاج مراجعة. قارن كل سطر بالناتج المطلوب.")
        }
        if (challenge.differentOutput && result.output == baselineOutput) {
            return CSharpChallengeResult(false, result, "غيّر قيمة تؤثر في الناتج، ثم شغّل. تغيير المسافات أو التعليقات وحده لا يغيّر التنفيذ.")
        }
        for (name in challenge.declared) {
            if (result.trace.none { it.kind == "declare" && it.name == name }) {
                return CSharpChallengeResult(false, result, "أنشئ المتغير «$name» واستخدمه في الحل.")
            }
        }
        for ((name, expectedType) in challenge.types) {
            if (result.variables[name]?.type != expectedType) {
                return CSharpChallengeResult(false, result, "استخدم النوع $expectedType للمتغير «$name» كما تطلب الخطوة.")
            }
        }
        for (name in challenge.read) {
            if (result.trace.none { name in it.reads }) {
                return CSharpChallengeResult(false, result, "استخدم قيمة المتغير «$name» في التنفيذ؛ كتابة الناتج مباشرة لا تثبت الفكرة.")
            }
        }
        for (name in challenge.printed) {
            if (result.trace.none { it.kind == "print" && name in it.reads }) {
                return CSharpChallengeResult(false, result, "اطبع قيمة المتغير «$name» باسمه؛ كتابة الناتج يدويًا لا تثبت قراءة المتغير.")
            }
        }
        if (challenge.minPrints != null && result.trace.count { it.kind == "print" } < challenge.minPrints) {
            return CSharpChallengeResult(false, result, "أضف أوامر الطباعة المطلوبة، ثم راقب ترتيبها.")
        }
        for (name in challenge.updated) {
            if (result.trace.none { it.kind == "assign" && it.name == name }) {
                return CSharpChallengeResult(false, result, "المطلوب تحديث المتغير «$name» بعد تعريفه.")
            }
        }
        for ((name, expectedEl) in challenge.values) {
            val actual = result.variables[name]?.rawValue
            val expectedStr = (expectedEl as? JsonPrimitive)?.content
            if (actual?.toString() != expectedStr) {
                return CSharpChallengeResult(false, result, "راجع القيمة النهائية للمتغير «$name».")
            }
        }
        for ((name, count) in challenge.calls) {
            val reached = result.trace.sumOf { step ->
                (if (step.kind == "call" && step.callName == name) 1 else 0) +
                    step.collectionOps.count { it.method == name }
            }
            if (reached < count) {
                return CSharpChallengeResult(false, result, "استدعِ الدالة «$name» في المهمة المطلوبة.")
            }
        }
        if (challenge.branching && result.trace.none { it.kind == "condition" }) {
            return CSharpChallengeResult(false, result, "استخدم if ليختار البرنامج متى ينفذ الأمر.")
        }
        if (challenge.elseRequired && result.trace.none { it.kind == "else" || it.clause == "else if" } && !source.contains("else")) {
            return CSharpChallengeResult(false, result, "أضف مسار else عندما لا يتحقق الشرط.")
        }

        val conditionReads = mutableSetOf<String>()
        val loopReads = mutableSetOf<String>()
        var hasLoopEffect = false
        var testedElseIf = false
        var reachedIndex = false
        var reachedField = false

        fun observe(traceList: List<TraceSnapshot>) {
            for (step in traceList) {
                challenge.indexAccess?.let { req ->
                    if (step.accesses.any { it.name == req.name && it.mode == req.mode && it.indices.size == req.rank }) reachedIndex = true
                }
                challenge.fieldAccess?.let { req ->
                    if (step.fields.any { it.field == req.name && it.mode == req.mode }) reachedField = true
                }
                challenge.loop?.let { reqLoop ->
                    if (step.loopKind == reqLoop.kind && (step.loopDepth ?: 0) >= (reqLoop.depth ?: 1)) {
                        if (step.kind == "loop-condition") loopReads += step.reads
                        if (reqLoop.effect == "print" && step.kind == "print") hasLoopEffect = true
                        if (step.kind == "assign" && step.name == reqLoop.effect && step.loopPhase != "initialize") hasLoopEffect = true
                    }
                }
                if (step.kind == "condition") {
                    conditionReads += step.reads
                    if (step.clause == "else if") testedElseIf = true
                }
            }
        }

        observe(result.trace)
        for (c in challenge.cases) {
            val alt = runProgram(source, c.inputs, c.stdin ?: stdin)
            if (alt.error != null) {
                return CSharpChallengeResult(false, result, "لم تنجح إحدى حالات الاختبار. راجع الأنواع واعتماد القرار على المتغيرات.")
            }
            if (alt.output != c.output) {
                return CSharpChallengeResult(false, result, "الناتج الحالي صحيح، لكن الحل لم ينجح بقيم أخرى. راجع المتغيرات والحد الفاصل.")
            }
            observe(alt.trace)
        }

        if (challenge.elseIfRequired && !testedElseIf) {
            return CSharpChallengeResult(false, result, "استخدم else if لفحص شرط بديل عند تجاوز الشرط الأول.")
        }
        challenge.indexAccess?.let { req ->
            if (!reachedIndex) {
                return CSharpChallengeResult(false, result, "نفّذ ${if (req.mode == "write") "تحديث خانة في" else "قراءة خانة من"} «${req.name}» باستخدام فهرسها.")
            }
        }
        challenge.fieldAccess?.let { req ->
            if (!reachedField) {
                return CSharpChallengeResult(false, result, "نفّذ ${if (req.mode == "write") "الإسناد للحقل" else "القراءة من الحقل"} «${req.name}» في الكائن.")
            }
        }
        for (name in challenge.conditionReads) {
            if (name !in conditionReads) {
                return CSharpChallengeResult(false, result, "اجعل قرار if يقرأ المتغير «$name» بدل شرط ثابت.")
            }
        }
        challenge.loop?.let { reqLoop ->
            val label = if ((reqLoop.depth ?: 1) > 1) "حلقات ${reqLoop.kind} متداخلة" else "حلقة ${reqLoop.kind}"
            if (!hasLoopEffect) return CSharpChallengeResult(false, result, "نفّذ التحديث أو الطباعة المطلوبة داخل $label.")
            for (name in reqLoop.reads) {
                if (name !in loopReads) return CSharpChallengeResult(false, result, "اقرأ قيمة «$name» عند فحص $label.")
            }
        }

        return CSharpChallengeResult(passed = true, result = result, message = "صحيح. نفّذت المطلوب وشاهدت أثره.")
    }
}
