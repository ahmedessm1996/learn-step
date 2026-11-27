package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import com.example.data.CurriculumRepository
import com.example.data.CurriculumRepository.Companion.ar
import com.example.engine.PreviewHtmlBuilder
import com.example.model.AmbientSoundType
import com.example.model.LivingProject
import com.example.model.ProfileCardColor
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.PinIdeaDialog
import com.example.widget.LearnStepWidgetProvider
import com.example.ui.theme.CardForestAccent
import com.example.ui.theme.CardForestSoft
import com.example.ui.theme.CardOceanAccent
import com.example.ui.theme.CardOceanSoft
import com.example.ui.theme.CardPlumAccent
import com.example.ui.theme.CardPlumSoft
import com.example.ui.theme.CardSunsetAccent
import com.example.ui.theme.CardSunsetSoft
import com.example.ui.theme.ForestBg
import com.example.ui.theme.ForestErrorInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised
import com.example.ui.theme.ForestSurfaceVariant
import com.example.ui.theme.ForestWarnInk

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildsScreen(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state = uiState.learnerState
    val prefs = uiState.uiPreferences
    val card = state.profileCard
    val cardColor = ProfileCardColor.fromKey(card.color)
    val accent = when (cardColor) {
        ProfileCardColor.FOREST -> CardForestAccent
        ProfileCardColor.OCEAN -> CardOceanAccent
        ProfileCardColor.PLUM -> CardPlumAccent
        ProfileCardColor.SUNSET -> CardSunsetAccent
    }
    val soft = when (cardColor) {
        ProfileCardColor.FOREST -> CardForestSoft
        ProfileCardColor.OCEAN -> CardOceanSoft
        ProfileCardColor.PLUM -> CardPlumSoft
        ProfileCardColor.SUNSET -> CardSunsetSoft
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadProfileAvatar(context, uri)
    }

    val unlockedAbilities = remember(state.growth.completed) {
        CurriculumRepository.abilityCatalog.filter {
            PreviewHtmlBuilder.isAbilityUnlocked(it, state.growth.completed)
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }

    if (showPinDialog) {
        PinIdeaDialog(
            trackLabel = uiState.activeTrack.shortTitle,
            onPin = { viewModel.pinCustomIdea(it) },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showNewProjectDialog) {
        NewLivingProjectDialog(
            onCreate = { title, goal -> viewModel.createLivingProject(title, goal) },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("تجاربي ومشاريعي", style = MaterialTheme.typography.headlineMedium, color = ForestInk)
                    Text(
                        "مشاريعك القابلة للتطوير، أفكارك المفتوحة، وبطاقتك الشخصية في مكان واحد هادئ.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForestMuted
                    )
                }
            }

            // 0. Evolving Living Projects ("مشاريعي القابلة للتطوير")
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ForestSurface),
                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("مشاريعي القابلة للتطوير", style = MaterialTheme.typography.titleMedium, color = ForestLime)
                                Text(
                                    "ابدأ ببطاقة بسيطة وطوّرها بالتنسيق والتفاعل والبيانات كلما تعلمت مفهومًا جديدًا.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForestMuted
                                )
                            }
                            TextButton(
                                onClick = { showNewProjectDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("builds_new_living_project_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("مشروع جديد", color = ForestMint)
                            }
                        }

                        state.growth.livingProjects.forEach { project ->
                            LivingProjectCard(
                                project = project,
                                onDevelop = { viewModel.openLivingProjectInWorkbench(project) },
                                onDelete = if (state.growth.livingProjects.size > 1) {
                                    { viewModel.deleteLivingProject(project.id) }
                                } else null
                            )
                        }
                    }
                }
            }

            // 1. Open-Loop Curiosity Ideas Queue
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ForestSurface),
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = ForestWarnInk)
                                Text("أفكار مفتوحة للعودة", style = MaterialTheme.typography.titleMedium, color = ForestInk)
                            }
                            TextButton(
                                onClick = { showPinDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("builds_add_idea_btn")
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("فكرة جديدة", color = ForestMint)
                            }
                        }

                        if (state.growth.ideaQueue.isEmpty()) {
                            Text(
                                text = "كل سؤال يخطر لك أثناء التعلّم ثبّته هنا لتجربته في المعمل الحر وقتما تشاء.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMuted
                            )
                        } else {
                            state.growth.ideaQueue.forEach { idea ->
                                val ideaTrack = TrackType.fromKey(idea.course)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                                    border = BorderStroke(1.dp, if (!idea.tried) ForestWarnInk.copy(alpha = 0.4f) else ForestLine)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(idea.text, style = MaterialTheme.typography.bodyMedium, color = ForestInk)
                                            Text(
                                                text = "${ideaTrack.shortTitle} · ${if (idea.tried) "جرّبتها ✓" else "بانتظار التجربة"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (idea.tried) ForestLime else ForestWarnInk
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { viewModel.openIdeaInWorkbench(idea) },
                                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = ForestBg),
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("builds_try_idea_${idea.id}")
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("جرّب")
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeIdea(idea.id) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "حذف الفكرة", tint = ForestErrorInk)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Saved Free Tinker Snapshots
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ForestSurface),
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "تجاربي الحرة المحفوظة (${ar(state.growth.tinkerSnapshots.size)})",
                            style = MaterialTheme.typography.titleMedium,
                            color = ForestInk
                        )
                        if (state.growth.tinkerSnapshots.isEmpty()) {
                            Text(
                                text = "عندما تحفظ أي تجربة من المعمل الحر، ستظهر هنا لتفتحها وتكمّل تعديلها لاحقًا.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMuted
                            )
                        } else {
                            state.growth.tinkerSnapshots.forEach { snap ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
                                    border = BorderStroke(1.dp, ForestLine)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(snap.title, style = MaterialTheme.typography.titleSmall, color = ForestMint)
                                            if (snap.note.isNotBlank()) {
                                                Text(snap.note, style = MaterialTheme.typography.bodySmall, color = ForestInk)
                                            }
                                            Text("${snap.course.uppercase()} · ${snap.createdAt}", style = MaterialTheme.typography.labelSmall, color = ForestMuted)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                onClick = { viewModel.openSnapshotInWorkbench(snap) },
                                                border = BorderStroke(1.dp, ForestGreen),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("افتح وعدّل", color = ForestMint)
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteSnapshot(snap.id) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "حذف اللقطة", tint = ForestErrorInk)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Personal Build Card Preview (.profile-card) + Share Intent Button
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, ForestLine),
                    modifier = Modifier.testTag("profile_card")
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(soft)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarPreviewBox(
                                imageDataUri = card.imageDataUri,
                                initials = card.initials(),
                                accent = accent
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("بطاقتي", color = accent, fontWeight = FontWeight.Bold)
                                OutlinedButton(
                                    onClick = {
                                        val summary = viewModel.buildProfileShareSummary()
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "ملخص مشروعي من steplearn")
                                            putExtra(Intent.EXTRA_TEXT, summary)
                                        }
                                        context.startActivity(Intent.createChooser(send, "مشاركة بطاقتك ومشروعك"))
                                    },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("profile_card_share_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "مشاركة البطاقة والمشروع",
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("مشاركة", color = accent, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = card.role.ifBlank { "مجال تتعلمه" },
                                color = accent,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = card.name.ifBlank { uiState.activeProfile.name },
                                color = Color(0xFF19372E),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = card.about.ifBlank { "نبذة قصيرة عن رحلتك وتجاربك." },
                                color = Color(0xFF53645A),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ForestSurface),
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("خصّص بطاقتك وشارك مشروعك", style = MaterialTheme.typography.titleMedium, color = ForestLime)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { s ->
                                FilterChip(
                                    selected = card.step == s,
                                    onClick = { viewModel.updateProfileCard(step = s) },
                                    label = { Text("خطوة ${ar(s)}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestGreen,
                                        selectedLabelColor = ForestBg
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }

                        when (card.step) {
                            1 -> {
                                OutlinedTextField(
                                    value = card.name,
                                    onValueChange = { viewModel.updateProfileCard(name = it) },
                                    label = { Text("الاسم على البطاقة") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("card_name_input")
                                )
                                OutlinedTextField(
                                    value = card.role,
                                    onValueChange = { viewModel.updateProfileCard(role = it) },
                                    label = { Text("التخصص أو الاهتمام") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("card_role_input")
                                )
                            }
                            2 -> {
                                OutlinedTextField(
                                    value = card.about,
                                    onValueChange = { viewModel.updateProfileCard(about = it) },
                                    label = { Text("نبذة عنك") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("card_about_input")
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ProfileCardColor.entries.forEach { color ->
                                        val swatch = when (color) {
                                            ProfileCardColor.FOREST -> CardForestAccent
                                            ProfileCardColor.OCEAN -> CardOceanAccent
                                            ProfileCardColor.PLUM -> CardPlumAccent
                                            ProfileCardColor.SUNSET -> CardSunsetAccent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .clickable { viewModel.updateProfileCard(color = color) }
                                                .testTag("card_color_${color.key}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (card.color == color.key) {
                                                Icon(Icons.Filled.Check, contentDescription = color.labelAr, tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("card_pick_avatar_btn")
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("اختر صورة")
                                    }
                                    if (card.imageDataUri.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { viewModel.clearProfileAvatar() },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("إزالة الصورة")
                                        }
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val html = viewModel.buildExportProjectHtml()
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/html"
                                        putExtra(Intent.EXTRA_SUBJECT, "صفحتي الشخصية من steplearn")
                                        putExtra(Intent.EXTRA_TEXT, html)
                                    }
                                    context.startActivity(Intent.createChooser(send, "مشاركة صفحتك"))
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("project_share_html_btn")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة HTML")
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(viewModel.buildExportProjectHtml()))
                                    viewModel.showToast("تم نسخ كود صفحتك الكامل")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("project_copy_html_btn")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("نسخ الكود")
                            }
                        }
                    }
                }
            }

            // 4. Unlocked Capabilities
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForestSurfaceVariant),
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ما فتحته بالتجربة (${ar(unlockedAbilities.size)} / ${ar(CurriculumRepository.abilityCatalog.size)})",
                            style = MaterialTheme.typography.titleMedium,
                            color = ForestLime
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurriculumRepository.abilityCatalog.forEach { ab ->
                                val unlocked = ab in unlockedAbilities
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (unlocked) ForestGreenDark.copy(alpha = 0.45f) else ForestSurface,
                                    border = BorderStroke(1.dp, if (unlocked) ForestLime else ForestLine)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (unlocked) Icons.Filled.Verified else Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = if (unlocked) ForestLime else ForestMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${ab.icon} ${ab.title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (unlocked) ForestInk else ForestMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Offline Profiles & Calm Ambient Audio
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ForestSurface),
                    border = BorderStroke(1.dp, ForestLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الملف المحلي الحالي", style = MaterialTheme.typography.titleSmall, color = ForestInk)
                                Text(uiState.activeProfile.name, style = MaterialTheme.typography.bodySmall, color = ForestMint)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setShowProfilesDialog(true) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("builds_profiles_btn")
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تبديل الملف")
                            }
                        }

                        Surface(
                            color = ForestSurfaceRaised,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = ForestMint)
                                        Text("أجواء هادئة أثناء التجربة", style = MaterialTheme.typography.titleSmall, color = ForestInk)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.toggleAmbientSound() },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("sound_toggle_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (prefs.soundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (prefs.soundEnabled) "إيقاف" else "تشغيل")
                                    }
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AmbientSoundType.entries.forEach { sound ->
                                        FilterChip(
                                            selected = prefs.soundType == sound,
                                            onClick = { viewModel.setAmbientSoundType(sound) },
                                            label = { Text(sound.labelAr) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ForestGreen,
                                                selectedLabelColor = ForestBg
                                            ),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("الصوت", style = MaterialTheme.typography.labelSmall, color = ForestMuted)
                                    Slider(
                                        value = prefs.soundVolume.toFloat(),
                                        onValueChange = { viewModel.setAmbientSoundVolume(it.toInt()) },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(thumbColor = ForestLime, activeTrackColor = ForestGreen),
                                        modifier = Modifier.weight(1f).testTag("sound_volume_slider")
                                    )
                                    Text("${ar(prefs.soundVolume)}%", style = MaterialTheme.typography.labelSmall, color = ForestLime)
                                }
                            }
                        }

                        HorizontalDivider(color = ForestLine)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("أداة الشاشة الرئيسية (اختياري)", style = MaterialTheme.typography.titleSmall, color = ForestInk)
                                Text(
                                    "تعرض نتيجة التجربة والسؤال الحالي لتفتحها بلمسة واحدة دون إشعارات.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForestMuted
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val requested = LearnStepWidgetProvider.requestPinWidget(context)
                                    if (!requested) {
                                        viewModel.showToast("يمكنك إضافة أداة steplearn من قائمة أدوات الشاشة الرئيسية.")
                                    }
                                },
                                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("builds_pin_widget_btn")
                            ) {
                                Icon(Icons.Filled.Widgets, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تثبيت الأداة", color = ForestMint)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LivingProjectCard(
    project: LivingProject,
    onDevelop: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val stages = listOf(
        "structure" to "١. بطاقة",
        "style" to "٢. تنسيق",
        "action" to "٣. زر وإجراء",
        "data" to "٤. تفاعل ومنطق"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = ForestSurfaceRaised),
        border = BorderStroke(1.dp, ForestLine)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleSmall, color = ForestInk)
                    Text(project.goal, style = MaterialTheme.typography.bodySmall, color = ForestMuted)
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف المشروع", tint = ForestErrorInk)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stages.forEach { (key, label) ->
                    val active = key in project.appliedStages
                    Surface(
                        shape = CircleShape,
                        color = if (active) ForestGreenDark else ForestSurface,
                        border = BorderStroke(1.dp, if (active) ForestLime else ForestLine)
                    ) {
                        Text(
                            text = if (active) "✓ $label" else label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) ForestLime else ForestMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (project.updatedAt.isNotBlank()) "آخر تطوير: ${project.updatedAt}" else "جاهز للتطوير",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForestMuted
                )
                Button(
                    onClick = onDevelop,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = ForestBg),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("living_project_open_${project.id}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طوّر المشروع الآن")
                }
            }
        }
    }
}

@Composable
private fun NewLivingProjectDialog(
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForestSurface,
        titleContentColor = ForestInk,
        textContentColor = ForestInk,
        title = { Text("مشروع جديد قابل للتطوير") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم المشروع (مثلاً: لوحة مهامي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_living_project_title_input")
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("ماذا تريد أن يبني لك؟ (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("new_living_project_goal_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(title, goal)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = ForestBg),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("new_living_project_confirm_btn")
            ) {
                Text("ابدأ المشروع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("إلغاء", color = ForestMuted)
            }
        }
    )
}

@Composable
private fun AvatarPreviewBox(imageDataUri: String, initials: String, accent: Color) {
    val bmp = remember(imageDataUri) {
        if (imageDataUri.contains("base64,")) {
            runCatching {
                val b64 = imageDataUri.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "صورة البطاقة الشخصية",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}
