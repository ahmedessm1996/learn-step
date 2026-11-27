package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurriculumRepository.Companion.ar
import com.example.engine.CSharpRunResult
import com.example.ui.theme.CodeEditorBg
import com.example.ui.theme.CodeEditorText
import com.example.ui.theme.ForestErrorBg
import com.example.ui.theme.ForestErrorInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestInfoBg
import com.example.ui.theme.ForestInfoInk
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceVariant
import com.example.ui.theme.ForestWarnInk
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CSharpTraceSection(
    runResult: CSharpRunResult?,
    traceIndex: Int,
    isAutoPlaying: Boolean,
    onSetTraceIndex: (Int) -> Unit,
    onToggleAutoPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val traceList = runResult?.trace.orEmpty()
    val activeTrace = traceList.getOrNull(traceIndex.coerceIn(0, (traceList.size - 1).coerceAtLeast(0)))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ForestSurfaceVariant),
        border = BorderStroke(1.dp, ForestLine)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("فيلم التنفيذ الحي", style = MaterialTheme.typography.titleMedium, color = ForestLime)
                Text(
                    text = if (traceList.isEmpty()) "٠ لقطات" else "لقطة ${ar(traceIndex + 1)} / ${ar(traceList.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ForestMint
                )
            }

            if (traceList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSetTraceIndex(0) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("trace_reset_btn")
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "إعادة التتبع", tint = ForestMint)
                    }
                    IconButton(
                        onClick = { onSetTraceIndex(traceIndex - 1) },
                        enabled = traceIndex > 0,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("trace_prev_btn")
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "اللقطة السابقة", tint = ForestInk)
                    }
                    Button(
                        onClick = onToggleAutoPlay,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("trace_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isAutoPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isAutoPlaying) "إيقاف التتبع" else "تشغيل التتبع"
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isAutoPlaying) "إيقاف" else "تشغيل")
                    }
                    IconButton(
                        onClick = { onSetTraceIndex(traceIndex + 1) },
                        enabled = traceIndex + 1 < traceList.size,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("trace_next_btn")
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "اللقطة التالية", tint = ForestInk)
                    }
                }

                if (traceList.size > 1) {
                    Slider(
                        value = traceIndex.toFloat(),
                        onValueChange = { onSetTraceIndex(it.toInt()) },
                        valueRange = 0f..(traceList.size - 1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ForestLime, activeTrackColor = ForestGreen),
                        modifier = Modifier.fillMaxWidth().testTag("trace_slider")
                    )
                }

                activeTrace?.let { snap ->
                    Surface(
                        color = ForestInfoBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, ForestInfoInk.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "السطر ${ar(snap.line)}: ${snap.action}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForestInk
                            )
                            snap.comparisonSummary?.let {
                                Text("مقارنة: $it", style = MaterialTheme.typography.bodySmall, color = ForestLime)
                            }
                            snap.logicSummary?.let {
                                Text("منطق: $it", style = MaterialTheme.typography.bodySmall, color = ForestInfoInk)
                            }
                            snap.calculationSummary?.let {
                                Text("حساب: $it", style = MaterialTheme.typography.bodySmall, color = ForestWarnInk)
                            }
                            if (snap.loopIteration != null) {
                                Text(
                                    text = "دورة ${snap.loopKind ?: "loop"} رقم ${ar(snap.loopIteration)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestMint
                                )
                            }
                        }
                    }

                    if (snap.variables.isNotEmpty()) {
                        Text("المتغيرات في الذاكرة", style = MaterialTheme.typography.titleSmall, color = ForestMint)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            snap.variables.forEach { v ->
                                Surface(
                                    color = ForestSurface,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, ForestLine)
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(
                                                text = "${v.type} ${v.name}",
                                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = ForestMuted)
                                            )
                                            Text(
                                                text = v.valueText,
                                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestLime)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (snap.objects.isNotEmpty()) {
                        Text("الكائنات والمجموعات", style = MaterialTheme.typography.titleSmall, color = ForestMint)
                        snap.objects.forEach { obj ->
                            Surface(
                                color = ForestSurface,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "#${obj.id} : ${obj.type}",
                                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestMint)
                                        )
                                        obj.collection?.let { coll ->
                                            Text(
                                                "${coll.kind} (${coll.count}) = [${coll.items.joinToString(", ")}]",
                                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = ForestLime)
                                            )
                                        }
                                        obj.fields.forEach { f ->
                                            Text(
                                                "${f.name} = ${f.valueText}",
                                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = CodeEditorText)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text("مخرجات Console", style = MaterialTheme.typography.titleSmall, color = ForestMint)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Surface(
                            color = CodeEditorBg,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = snap.output.ifBlank { "(لا توجد مخرجات حتى هذه الخطوة)" },
                                style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = ForestLime),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            } else {
                val err = runResult?.error
                Surface(
                    color = if (err != null) ForestErrorBg else CodeEditorBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err?.message ?: "اضغط «تشغيل» لعرض خطوات التنفيذ والذاكرة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (err != null) ForestErrorInk else ForestMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
