package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeEditorBg
import com.example.ui.theme.CodeEditorGutter
import com.example.ui.theme.CodeEditorMuted
import com.example.ui.theme.CodeEditorText
import com.example.ui.theme.CodeTargetLineBg
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised
import com.example.ui.theme.ForestWarnInk
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SyntaxAttr
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxOperator
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxTag

private val QUICK_SYMBOLS = listOf("<", ">", "/", "=", "\"", ":", ";", "#", "{", "}", "(", ")")

private val KEYWORD_REGEX = Regex(
    "\\b(const|let|var|function|return|if|else|for|while|switch|case|break|class|public|private|static|void|int|double|string|bool|true|false|null|new|import|export|from|SELECT|FROM|WHERE|ORDER BY|GROUP BY|JOIN|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|LIMIT|AND|OR|AS|COUNT|SUM|AVG|Console|WriteLine|ReadLine)\\b"
)
private val COMMENT_REGEX = Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/|<!--([\\s\\S]*?)-->|--[^\\n]*)")
private val STRING_REGEX = Regex("(\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')")
private val TAG_REGEX = Regex("</?[a-zA-Z][a-zA-Z0-9-]*|/?>")
private val CSS_ATTR_REGEX = Regex("\\b[a-zA-Z-]+(?=\\s*:)")
private val NUMBER_HEX_REGEX = Regex("(#[0-9a-fA-F]{3,8}\\b|\\b\\d+(\\.\\d+)?\\b)")

private class CodeSyntaxVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = buildAnnotatedString {
            append(raw)
            // Base color already applied via textStyle; overlay syntax spans
            NUMBER_HEX_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxNumber), m.range.first, m.range.last + 1)
            }
            CSS_ATTR_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxAttr), m.range.first, m.range.last + 1)
            }
            KEYWORD_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            TAG_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
            }
            STRING_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxString), m.range.first, m.range.last + 1)
            }
            COMMENT_REGEX.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxComment), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

fun findTargetLineNumbers(code: String, targetSnippet: String?): Set<Int> {
    if (code.isBlank() || targetSnippet.isNullOrBlank()) return emptySet()
    val normalizedTarget = targetSnippet.trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (normalizedTarget.isBlank()) return emptySet()
    val lines = code.replace("\r\n", "\n").split("\n")
    val matched = mutableSetOf<Int>()
    lines.forEachIndexed { idx, line ->
        if (line.contains(normalizedTarget, ignoreCase = true)) {
            matched += (idx + 1)
        }
    }
    return matched
}

@Composable
fun MobileCodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    fileName: String,
    modifier: Modifier = Modifier,
    targetSnippet: String? = null,
    activeTraceLine: Int? = null,
    skippedLines: Set<Int> = emptySet(),
    showPinAction: Boolean = true,
    onHintClick: (() -> Unit)? = null,
    onPinIdeaClick: (() -> Unit)? = null,
    onResetClick: (() -> Unit)? = null,
    testTag: String = "code_editor"
) {
    val clipboard = LocalClipboardManager.current
    val normalizedCode = remember(code) { code.replace("\r\n", "\n") }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = normalizedCode, selection = TextRange(normalizedCode.length)))
    }
    if (textFieldValue.text != normalizedCode) {
        val clampedSel = TextRange(
            textFieldValue.selection.start.coerceIn(0, normalizedCode.length),
            textFieldValue.selection.end.coerceIn(0, normalizedCode.length)
        )
        textFieldValue = textFieldValue.copy(text = normalizedCode, selection = clampedSel)
    }

    val lines = remember(normalizedCode) {
        val split = normalizedCode.split("\n")
        if (split.isEmpty()) listOf("") else split
    }
    val targetLines = remember(normalizedCode, targetSnippet) {
        findTargetLineNumbers(normalizedCode, targetSnippet)
    }
    val syntaxTransformation = remember { CodeSyntaxVisualTransformation() }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val editorScroll = rememberScrollState()

    // Precompute char ranges for logical lines (1-indexed)
    val logicalLineRanges = remember(normalizedCode) {
        val map = mutableMapOf<Int, IntRange>()
        var start = 0
        val split = normalizedCode.split("\n")
        split.forEachIndexed { idx, line ->
            val end = start + line.length
            map[idx + 1] = start..end
            start = end + 1
        }
        map
    }

    fun insertSymbol(symbol: String) {
        val cur = textFieldValue
        val selStart = cur.selection.min.coerceIn(0, cur.text.length)
        val selEnd = cur.selection.max.coerceIn(0, cur.text.length)
        val updated = buildString {
            append(cur.text.substring(0, selStart))
            append(symbol)
            append(cur.text.substring(selEnd))
        }
        val nextCursor = selStart + symbol.length
        textFieldValue = TextFieldValue(text = updated, selection = TextRange(nextCursor))
        onCodeChange(updated)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CodeEditorBg),
        border = BorderStroke(1.dp, ForestLine)
    ) {
        Column {
            // Top bar (LTR file badge + Copy/Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeEditorGutter)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = "ملف الكود",
                            tint = ForestGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = fileName,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestMint
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(normalizedCode)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTag}_copy")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "نسخ الكود",
                            tint = ForestMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (onResetClick != null) {
                        IconButton(
                            onClick = onResetClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("${testTag}_reset")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "إعادة الكود الأصلي",
                                tint = ForestMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = ForestLine)

            // LTR Editor Canvas + Synchronized Gutter + Exact Wrapped Line Highlights
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 330.dp)
                        .verticalScroll(editorScroll)
                        .background(CodeEditorBg)
                        .padding(vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Line numbers gutter aligned via TextLayoutResult when available
                        Column(
                            modifier = Modifier
                                .width(38.dp)
                                .background(CodeEditorGutter)
                                .padding(top = 2.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            lines.forEachIndexed { idx, _ ->
                                val lineNum = idx + 1
                                val isTrace = activeTraceLine == lineNum
                                val isTarget = lineNum in targetLines
                                val isSkipped = lineNum in skippedLines
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when {
                                                isTrace -> ForestGreen.copy(alpha = 0.35f)
                                                isTarget -> CodeTargetLineBg
                                                isSkipped -> ForestLine.copy(alpha = 0.25f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(end = 6.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = lineNum.toString(),
                                        style = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 12.sp,
                                            lineHeight = 20.sp,
                                            color = when {
                                                isTrace || isTarget -> ForestLime
                                                isSkipped -> CodeEditorMuted.copy(alpha = 0.45f)
                                                else -> CodeEditorMuted
                                            }
                                        )
                                    )
                                }
                            }
                        }

                        // Editable text area with full-width wrapped target-line canvas backdrop
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            val layout = textLayoutResult
                            if (layout != null && (targetLines.isNotEmpty() || activeTraceLine != null)) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    fun highlightLogicalLine(lineNumber: Int, highlightColor: Color) {
                                        val range = logicalLineRanges[lineNumber] ?: return
                                        if (layout.layoutInput.text.isEmpty()) return
                                        val maxOffset = layout.layoutInput.text.length
                                        val startOffset = range.first.coerceIn(0, maxOffset)
                                        val endOffset = range.last.coerceIn(startOffset, maxOffset)
                                        val firstVisual = layout.getLineForOffset(startOffset)
                                        val lastVisual = layout.getLineForOffset(endOffset)
                                        for (vLine in firstVisual..lastVisual) {
                                            val top = layout.getLineTop(vLine)
                                            val bottom = layout.getLineBottom(vLine)
                                            drawRect(
                                                color = highlightColor,
                                                topLeft = Offset(-10.dp.toPx(), top),
                                                size = Size(size.width + 20.dp.toPx(), (bottom - top).coerceAtLeast(1f))
                                            )
                                        }
                                    }

                                    targetLines.forEach { lineNum ->
                                        highlightLogicalLine(lineNum, CodeTargetLineBg)
                                    }
                                    activeTraceLine?.let { traceLine ->
                                        highlightLogicalLine(traceLine, ForestGreen.copy(alpha = 0.30f))
                                    }
                                }
                            }

                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { next ->
                                    textFieldValue = next
                                    if (next.text != normalizedCode) {
                                        onCodeChange(next.text)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = CodeEditorText
                                ),
                                cursorBrush = SolidColor(ForestLime),
                                visualTransformation = syntaxTransformation,
                                onTextLayout = { textLayoutResult = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                                    .testTag(testTag)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = ForestLine)

            // Docked Quick Programming Symbol Bar + On-Demand Hint/Pin Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onHintClick != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForestSurfaceRaised,
                        border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onHintClick() }
                            .testTag("${testTag}_hint_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = "اطلب تلميحًا",
                                tint = ForestWarnInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("تلميح", style = MaterialTheme.typography.labelMedium, color = ForestWarnInk)
                        }
                    }
                }

                if (showPinAction && onPinIdeaClick != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForestSurfaceRaised,
                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPinIdeaClick() }
                            .testTag("${testTag}_pin_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = "ثبّت فكرة",
                                tint = ForestMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("فكرة", style = MaterialTheme.typography.labelMedium, color = ForestMint)
                        }
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QUICK_SYMBOLS.forEach { sym ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ForestSurfaceRaised,
                                border = BorderStroke(1.dp, ForestLine),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { insertSymbol(sym) }
                                    .testTag("${testTag}_sym_$sym")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sym,
                                        style = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SyntaxOperator
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
