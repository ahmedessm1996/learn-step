package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurriculumRepository.Companion.ar
import com.example.model.LearnerProfile
import com.example.ui.theme.ForestErrorInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun LearnerProfilesDialog(
    profiles: List<LearnerProfile>,
    activeProfile: LearnerProfile,
    errorText: String?,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        titleContentColor = ForestInk,
        textContentColor = ForestInk,
        title = {
            Column {
                Text("ملفات التعلّم المحلية", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${ar(profiles.size)} من ٣٠ ملفًا محفوظًا على جهازك بدون كلمة مرور",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForestMuted
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isActive = profile.id == activeProfile.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) ForestGreen.copy(alpha = 0.2f) else ForestSurfaceRaised
                            ),
                            border = BorderStroke(1.dp, if (isActive) ForestLime else ForestLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectProfile(profile.id) }
                                .testTag("profile_item_${profile.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isActive) ForestLime else ForestInk
                                )
                                if (isActive) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "الملف النشط",
                                        tint = ForestLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("اسم متعلّم جديد") },
                    placeholder = { Text("مثال: سارة أو عمر") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_profile_name_input")
                )

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = ForestErrorInk
                    )
                }

                Button(
                    onClick = { onCreateProfile(newName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("create_profile_btn")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إنشاء وفتح الملف")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun NewAngularFileDialog(
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var path by remember { mutableStateOf("src/app/components/promo.component.ts") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        title = { Text("إنشاء ملف جديد في المشروع", color = ForestInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "الامتدادات المسموحة: .ts, .html, .css, .json, .md",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForestMuted
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = path,
                        onValueChange = {
                            path = it
                            invalid = false
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = ForestInk),
                        isError = invalid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_angular_file_input")
                    )
                }
                if (invalid) {
                    Text(
                        "المسار غير مدعوم. تأكد من الامتداد وعدم استخدام ..",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForestErrorInk
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onConfirm(path)) onDismiss() else invalid = true
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_new_angular_file")
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun SaveSnapshotDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        title = { Text("احفظ التجربة في تجاربي", color = ForestInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم التجربة") },
                    placeholder = { Text("مثال: بطاقة زجاجية بخلفية داكنة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("snapshot_title_input")
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة سريعة (اختياري)") },
                    modifier = Modifier.fillMaxWidth().testTag("snapshot_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, note)
                    onDismiss()
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_save_snapshot")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun PinIdeaDialog(
    trackLabel: String,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ideaText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        title = { Text("ثبّت فكرة للمرة الجاية", color = ForestInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "سجّل سؤالًا أو تعديلًا تود تجربته في المعمل الحر ($trackLabel)، وسيظهر لك فور عودتك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForestMuted
                )
                OutlinedTextField(
                    value = ideaText,
                    onValueChange = { ideaText = it },
                    placeholder = { Text("مثال: ماذا لو غيّرت التدرج والشفافية معًا؟") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_idea_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPin(ideaText)
                    onDismiss()
                },
                enabled = ideaText.isNotBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pin_idea_dialog_confirm")
            ) {
                Text("تثبيت الفكرة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
