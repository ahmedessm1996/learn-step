package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CurriculumRepository.Companion.ar
import com.example.data.LessonCuriosityMeta
import com.example.data.PredictChallenge
import com.example.engine.GentleTreeAlert
import com.example.engine.SlotBuilderModel
import com.example.engine.SqlExecutionResult
import com.example.model.RawLesson
import com.example.model.RawLessonStep
import com.example.model.TrackType
import com.example.ui.theme.CodeEditorBg
import com.example.ui.theme.CodeEditorText
import com.example.ui.theme.ForestBg
import com.example.ui.theme.ForestErrorBg
import com.example.ui.theme.ForestErrorInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestInfoBg
import com.example.ui.theme.ForestInfoInk
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised
import com.example.ui.theme.ForestSurfaceVariant
import com.example.ui.theme.ForestWarnBg
import com.example.ui.theme.ForestWarnInk
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * Pure Compose miniature visual preview for HomeScreen (zero WebView jank).
 */
@Composable
fun NativeOutcomePreviewCard(
    track: TrackType,
    lesson: RawLesson?,
    meta: LessonCuriosityMeta,
    modifier: Modifier = Modifier
) {
    val isGlassColors = lesson?.id == "html-12-colors" ||
        lesson?.title?.contains("لون") == true ||
        lesson?.title?.contains("الألوان") == true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0E261E), Color(0xFF15382B), Color(0xFF0F2019))
                )
            )
            .border(1.dp, ForestGreen.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .padding(14.dp)
    ) {
        when {
            isGlassColors -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2BB07F).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, ForestMint.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "بطاقة زجاجية واضحة",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ForestMint
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Surface(shape = CircleShape, color = ForestBg.copy(alpha = 0.7f)) {
                                        Text(
                                            text = "rgba(43, 176, 127, 0.22)",
                                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = ForestLime),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "خلفية شفافة فوق سطح داكن مع نص عالي التباين",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestInk
                            )
                        }
                    }
                }
            }

            track == TrackType.DATABASE -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نتيجة الاستعلام الحية", style = MaterialTheme.typography.labelMedium, color = ForestLime)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("SELECT *", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMint))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ForestBg.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(ForestSurfaceRaised).padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("id", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMint))
                                    Text("title", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMint))
                                    Text("score", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMint))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestInk))
                                    Text("LearnStep", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestInk))
                                    Text("98", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestLime))
                                }
                            }
                        }
                    }
                }
            }

            track == TrackType.CSHARP -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فيلم التنفيذ والذاكرة", style = MaterialTheme.typography.labelMedium, color = ForestLime)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(shape = CircleShape, color = ForestGreenDark) {
                                Text(
                                    text = "line 4 → OK",
                                    style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = ForestMint),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ForestBg.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier.weight(1f)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = meta.baselineSnippet.lines().take(2).joinToString("\n"),
                                    style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = CodeEditorText),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ForestGreenDark.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, ForestGreen)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("state", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = ForestMuted))
                                    Text("Active", style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestLime))
                                }
                            }
                        }
                    }
                }
            }

            track == TrackType.ANGULAR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForestGreenDark,
                        border = BorderStroke(1.dp, ForestGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = ForestMint,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "تطبيق سينما خطوة التفاعلي",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForestInk
                        )
                        Text(
                            text = lesson?.title ?: "مكوّن Angular حي",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForestMint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(shape = CircleShape, color = ForestBg.copy(alpha = 0.75f)) {
                        Text(
                            text = "Signal ●",
                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = ForestLime),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            else -> {
                // HTML / CSS / JS crisp Compose-native visual result card
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lesson?.title ?: track.titleAr,
                            style = MaterialTheme.typography.titleSmall,
                            color = ForestMint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(shape = CircleShape, color = ForestBg.copy(alpha = 0.7f)) {
                            Text(
                                text = track.shortTitle,
                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = ForestLime),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForestBg.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, ForestLine)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = meta.baselineSnippet.lines().take(3).joinToString("\n"),
                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = CodeEditorText),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveWebPreview(
    html: String,
    desktopMode: Boolean,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 220
) {
    var consoleError by remember(html) { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val frameModifier = if (desktopMode) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.widthIn(max = 340.dp).fillMaxWidth()
            }

            Card(
                modifier = frameModifier,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ForestLine),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeightDp.dp, max = 360.dp)
                        .testTag("live_webview"),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webViewClient = WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                    if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                        consoleError = msg.message()
                                    }
                                    return true
                                }
                            }
                            tag = html
                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        if (webView.tag != html) {
                            webView.tag = html
                            consoleError = null
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }

        if (!consoleError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = ForestErrorBg,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, ForestErrorInk.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "تنبيه المتصفح: $consoleError",
                    color = ForestErrorInk,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TieredHintDrawer(
    revealedTier: Int,
    hintText: String,
    onRevealNextTier: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = revealedTier > 0) {
        val tierNames = listOf("فكرة", "قاعدة", "مثال", "الحل")
        val currentLabel = tierNames.getOrElse((revealedTier - 1).coerceIn(0, 3)) { "فكرة" }
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = ForestWarnBg),
            border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = ForestWarnInk, modifier = Modifier.size(18.dp))
                        Text(
                            text = "تلميح متدرج · $currentLabel",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForestWarnInk
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tierNames.forEachIndexed { idx, label ->
                            val active = idx + 1 <= revealedTier
                            Surface(
                                shape = CircleShape,
                                color = if (active) ForestWarnInk.copy(alpha = 0.22f) else ForestSurface
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) ForestWarnInk else ForestMuted,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = hintText.ifBlank { "ركّز على السطر المميّز بالأخضر داخل المحرر وجرّب التعديل." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForestInk
                )

                if (revealedTier < 4) {
                    val nextLabel = tierNames[revealedTier]
                    OutlinedButton(
                        onClick = onRevealNextTier,
                        border = BorderStroke(1.dp, ForestWarnInk.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hint_next_tier_btn")
                    ) {
                        Text("أظهر التلميح التالي ($nextLabel)", color = ForestWarnInk)
                    }
                }
            }
        }
    }
}

@Composable
fun PredictHookCard(
    challenge: PredictChallenge,
    selectedOption: Int?,
    revealed: Boolean,
    onSelectOption: (Int) -> Unit,
    onSkipReveal: (() -> Unit)? = null
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
        border = BorderStroke(1.dp, ForestLine)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = ForestWarnInk)
                    Text(
                        text = "توقّع قبل الكشف (٥ ثوانٍ)",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestWarnInk
                    )
                }
                if (!revealed && onSkipReveal != null) {
                    TextButton(
                        onClick = onSkipReveal,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("intro_skip_predict_btn")
                    ) {
                        Text("اكشف المعاينة فورًا", color = ForestMint)
                    }
                }
            }
            Text(
                text = challenge.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = ForestInk
            )
            if (challenge.snippet.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        color = CodeEditorBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, ForestLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = challenge.snippet,
                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = CodeEditorText),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
            challenge.options.take(3).forEachIndexed { idx, opt ->
                val isSelected = selectedOption == idx
                val isCorrect = idx == challenge.correctIndex
                val bg = when {
                    revealed && isCorrect -> ForestGreenDark.copy(alpha = 0.65f)
                    revealed && isSelected && !isCorrect -> ForestErrorBg
                    isSelected -> ForestGreen.copy(alpha = 0.22f)
                    else -> ForestSurface
                }
                val border = when {
                    revealed && isCorrect -> ForestLime
                    revealed && isSelected && !isCorrect -> ForestErrorInk
                    isSelected -> ForestGreen
                    else -> ForestLine
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = bg,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelectOption(idx) }
                        .testTag("predict_option_$idx")
                ) {
                    Text(
                        text = opt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForestInk,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            AnimatedVisibility(visible = revealed && challenge.revealNote.isNotBlank()) {
                Surface(
                    color = ForestInfoBg,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, ForestInfoInk.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = challenge.revealNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = ForestInfoInk,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SlotBuilderCard(
    model: SlotBuilderModel,
    onApply: (String) -> Unit
) {
    var value by remember(model) { mutableStateOf(model.initialValue) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "منشئ سريع · ${model.stage2Tool}",
                style = MaterialTheme.typography.titleSmall,
                color = ForestMint
            )
            Text(
                text = "الموقع: ${model.stage1Location}",
                style = MaterialTheme.typography.bodySmall,
                color = ForestMuted
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    prefix = if (model.tagOpen.isNotEmpty()) ({ Text(model.tagOpen, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = ForestMint) }) else null,
                    suffix = if (model.tagClose.isNotEmpty()) ({ Text(model.tagClose, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = ForestMint) }) else null,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = ForestInk),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slot_builder_input")
                )
            }
            Button(
                onClick = { onApply(value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("slot_builder_apply")
            ) {
                Text("أدرج التعديل وشغّل")
            }
        }
    }
}

@Composable
fun GentleTreeCard(alert: GentleTreeAlert) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ForestInfoBg),
        border = BorderStroke(1.dp, ForestInfoInk.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${alert.icon} ${alert.heading}",
                style = MaterialTheme.typography.titleSmall,
                color = ForestInfoInk
            )
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                color = ForestInk
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    color = CodeEditorBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${alert.diagram}\n  ${alert.targetLine}",
                        style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMint),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SqlResultTable(result: SqlExecutionResult) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (result.isError) ForestErrorBg else ForestSurface),
        border = BorderStroke(1.dp, if (result.isError) ForestErrorInk else ForestLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = result.notice,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.isError) ForestErrorInk else ForestLime,
                fontWeight = FontWeight.Bold
            )

            if (result.columns.isNotEmpty()) {
                val hScroll = rememberScrollState()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .border(1.dp, ForestLine, MaterialTheme.shapes.small)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(ForestSurfaceRaised)
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            result.columns.forEach { col ->
                                Text(
                                    text = col,
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestMint
                                    ),
                                    modifier = Modifier
                                        .width(120.dp)
                                        .padding(horizontal = 6.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = ForestLine)
                        if (result.rows.isEmpty()) {
                            Text(
                                text = "(0 rows)",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMuted,
                                modifier = Modifier.padding(10.dp)
                            )
                        } else {
                            result.rows.forEachIndexed { rIdx, row ->
                                Row(
                                    modifier = Modifier
                                        .background(if (rIdx % 2 == 0) ForestBg else ForestSurface)
                                        .padding(vertical = 7.dp, horizontal = 6.dp)
                                ) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            style = TextStyle(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 12.sp,
                                                color = ForestInk
                                            ),
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .width(120.dp)
                                                .padding(horizontal = 6.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepPlanBottomSheet(
    lessonTitle: String,
    steps: List<RawLessonStep>,
    currentIndex: Int,
    unlockedIndex: Int,
    tinkerMode: Boolean,
    onSelectStep: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        contentColor = ForestInk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("خطة التجربة", style = MaterialTheme.typography.titleLarge, color = ForestInk)
                    Text(lessonTitle, style = MaterialTheme.typography.bodySmall, color = ForestMint)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق خطة التجربة")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(steps) { idx, step ->
                    val isCurrent = idx == currentIndex
                    val isDone = idx < unlockedIndex
                    val isLocked = idx > unlockedIndex && !tinkerMode
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) ForestGreenDark.copy(alpha = 0.55f) else ForestSurfaceRaised
                        ),
                        border = BorderStroke(1.dp, if (isCurrent) ForestLime else ForestLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !isLocked) { onSelectStep(idx) }
                            .testTag("step_plan_item_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ar(idx + 1)}. ${step.title.ifBlank { step.type }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) ForestLime else ForestInk
                                )
                                val sub = step.objective.ifBlank { step.explanation }
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ForestMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            when {
                                isDone -> Icon(Icons.Filled.Check, contentDescription = "مكتمل", tint = ForestLime)
                                isLocked -> Icon(Icons.Filled.Lock, contentDescription = "مقفل", tint = ForestMuted)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepTheoryBottomSheet(
    lesson: RawLesson,
    meta: LessonCuriosityMeta,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        contentColor = ForestInk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تعمّق أكثر · ${lesson.title}", style = MaterialTheme.typography.titleLarge, color = ForestLime)
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق الشرح")
                }
            }

            if (meta.conceptSummary.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ForestSurfaceRaised,
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("المفهوم الأساسي", style = MaterialTheme.typography.labelLarge, color = ForestMint)
                        Text(meta.conceptSummary, style = MaterialTheme.typography.bodyMedium, color = ForestInk)
                    }
                }
            }

            if (meta.deepExplanation.isNotBlank()) {
                Text(meta.deepExplanation, style = MaterialTheme.typography.bodyMedium, color = ForestInk)
            }

            if (lesson.whyUse.isNotBlank() && !meta.deepExplanation.contains(lesson.whyUse)) {
                Text("لماذا نستخدمه؟ ${lesson.whyUse}", style = MaterialTheme.typography.bodySmall, color = ForestMuted)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapModalSheet(
    track: TrackType,
    lessons: List<RawLesson>,
    currentIndex: Int,
    completedIds: Set<String>,
    onSelectLesson: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(lessons, query) {
        lessons.mapIndexed { idx, l -> idx to l }.filter { (_, l) ->
            query.isBlank() || l.title.contains(query, ignoreCase = true) ||
                l.summary.contains(query, ignoreCase = true) ||
                l.phase.contains(query, ignoreCase = true) ||
                l.skill.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        contentColor = ForestInk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "خريطة مسار ${track.titleAr}",
                        style = MaterialTheme.typography.titleLarge,
                        color = ForestInk
                    )
                    Text(
                        text = "${ar(completedIds.size)} مكتمل من أصل ${ar(lessons.size)} تجربة",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForestMuted
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق الخريطة")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("ابحث عن درس أو مهارة...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("roadmap_search_input")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { _, pair -> pair.first }) { _, (idx, lesson) ->
                    val lessonKey = if (track == TrackType.CSHARP || track == TrackType.ANGULAR) {
                        lesson.id
                    } else "${track.key}:$idx"
                    val isDone = lessonKey in completedIds || lesson.id in completedIds
                    val isCurrent = idx == currentIndex

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) ForestGreenDark.copy(alpha = 0.45f) else ForestSurfaceRaised
                        ),
                        border = BorderStroke(1.dp, if (isCurrent) ForestLime else ForestLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectLesson(idx) }
                            .testTag("roadmap_lesson_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ar(idx + 1)}. ${lesson.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) ForestLime else ForestInk
                                )
                                val sub = lesson.skill.ifBlank { lesson.phase.ifBlank { lesson.summary } }
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ForestMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isDone) {
                                Surface(shape = CircleShape, color = ForestGreen) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "مكتمل",
                                        tint = ForestBg,
                                        modifier = Modifier.padding(4.dp).size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
