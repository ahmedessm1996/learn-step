package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CuriosityFraming
import com.example.data.CurriculumRepository.Companion.ar
import com.example.model.RawLesson
import com.example.model.TrackType
import com.example.ui.StepLearnUiState
import com.example.ui.StepLearnViewModel
import com.example.ui.components.RoadmapModalSheet
import com.example.ui.theme.ForestBg
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestInk
import com.example.ui.theme.ForestLime
import com.example.ui.theme.ForestLine
import com.example.ui.theme.ForestMint
import com.example.ui.theme.ForestMuted
import com.example.ui.theme.ForestSurface
import com.example.ui.theme.ForestSurfaceRaised

private data class LessonSearchHit(
    val track: TrackType,
    val index: Int,
    val lesson: RawLesson,
    val isDone: Boolean
)

@Composable
fun CoursesScreen(
    uiState: StepLearnUiState,
    viewModel: StepLearnViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterTrack by remember { mutableStateOf<TrackType?>(null) }
    var roadmapTrack by remember { mutableStateOf<TrackType?>(null) }

    val searchHits = remember(searchQuery, filterTrack, uiState.tracks, uiState.learnerState) {
        if (searchQuery.isBlank() && filterTrack == null) emptyList()
        else {
            val tracksToScan = filterTrack?.let { listOf(it) } ?: TrackType.entries
            buildList {
                tracksToScan.forEach { track ->
                    val lessons = uiState.tracks[track]?.lessons.orEmpty()
                    lessons.forEachIndexed { idx, lesson ->
                        val matches = searchQuery.isBlank() ||
                            lesson.title.contains(searchQuery, ignoreCase = true) ||
                            lesson.summary.contains(searchQuery, ignoreCase = true) ||
                            lesson.skill.contains(searchQuery, ignoreCase = true)
                        if (matches) {
                            val isDone = when (track) {
                                TrackType.CSHARP -> lesson.id in uiState.learnerState.csharp.completed
                                TrackType.ANGULAR -> lesson.id in uiState.learnerState.angular.completed
                                else -> "${track.key}:$idx" in uiState.learnerState.growth.completed
                            }
                            add(LessonSearchHit(track, idx, lesson, isDone))
                        }
                    }
                }
            }.take(50)
        }
    }

    roadmapTrack?.let { track ->
        val lessons = uiState.tracks[track]?.lessons.orEmpty()
        val currentIdx = viewModel.lessonIndexFor(uiState.learnerState, track)
        val completedIds = when (track) {
            TrackType.CSHARP -> uiState.learnerState.csharp.completed.toSet()
            TrackType.ANGULAR -> uiState.learnerState.angular.completed.toSet()
            else -> uiState.learnerState.growth.completed.filter { it.startsWith("${track.key}:") }.toSet()
        }
        RoadmapModalSheet(
            track = track,
            lessons = lessons,
            currentIndex = currentIdx,
            completedIds = completedIds,
            onSelectLesson = { idx ->
                roadmapTrack = null
                viewModel.openLesson(track, idx)
            },
            onDismiss = { roadmapTrack = null }
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
                    Text("المسارات التعليمية", style = MaterialTheme.typography.headlineMedium, color = ForestInk)
                    Text(
                        "مسارات مرتبة حسب المستوى؛ اختر المسار أو افتح خريطته لتصفح كل التجارب.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForestMuted
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    placeholder = { Text("ابحث عن درس أو مهارة...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("courses_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterTrack == null,
                        onClick = { filterTrack = null },
                        label = { Text("الكل") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = ForestBg
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    TrackType.entries.forEach { t ->
                        FilterChip(
                            selected = filterTrack == t,
                            onClick = { filterTrack = if (filterTrack == t) null else t },
                            label = { Text(t.shortTitle) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = ForestBg
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }

            if (searchQuery.isNotBlank() || filterTrack != null) {
                item {
                    Text(
                        text = "نتائج البحث (${ar(searchHits.size)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestLime
                    )
                }
                if (searchHits.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ForestSurface),
                            border = BorderStroke(1.dp, ForestLine)
                        ) {
                            Text(
                                text = "لا توجد نتائج مطابقة لبحثك.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForestMuted,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(searchHits, key = { "${it.track.key}-${it.index}" }) { hit ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ForestSurface),
                            border = BorderStroke(1.dp, ForestLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { viewModel.openLesson(hit.track, hit.index) }
                                .testTag("search_hit_${hit.track.key}_${hit.index}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, color = ForestSurfaceRaised) {
                                            Text(
                                                text = hit.track.shortTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ForestMint,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = hit.lesson.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ForestInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val sub = hit.lesson.summary.ifBlank { hit.lesson.concept }
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
                                if (hit.isDone) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "مكتمل",
                                        tint = ForestLime,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                CuriosityFraming.levelGroups.forEach { group ->
                    item {
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = group.levelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = ForestLime
                            )
                            Text(
                                text = group.levelSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestMuted
                            )
                        }
                    }
                    items(group.tracks, key = { it.key }) { track ->
                        val model = uiState.tracks[track]
                        val total = model?.lessons?.size ?: 0
                        val done = when (track) {
                            TrackType.CSHARP -> uiState.learnerState.csharp.completed.size
                            TrackType.ANGULAR -> uiState.learnerState.angular.completed.size
                            else -> uiState.learnerState.growth.completed.count { it.startsWith("${track.key}:") }
                        }
                        val currentIdx = viewModel.lessonIndexFor(uiState.learnerState, track)
                        val currentLessonTitle = model?.lessons?.getOrNull(
                            currentIdx.coerceIn(0, (total - 1).coerceAtLeast(0))
                        )?.title.orEmpty()
                        val fraction = if (total > 0) done.toFloat() / total else 0f

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = ForestSurface),
                            border = BorderStroke(
                                1.dp,
                                if (uiState.activeTrack == track) ForestGreen else ForestLine
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(track.titleAr, style = MaterialTheme.typography.titleLarge, color = ForestInk)
                                        Text(track.subtitle, style = MaterialTheme.typography.bodySmall, color = ForestMuted)
                                    }
                                    Surface(shape = CircleShape, color = ForestGreenDark) {
                                        Text(
                                            text = track.shortTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ForestMint,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = ForestGreen,
                                    trackColor = ForestSurfaceRaised
                                )

                                if (currentLessonTitle.isNotBlank()) {
                                    Text(
                                        text = "المحطة الحالية: $currentLessonTitle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ForestMint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.openLesson(track, currentIdx) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ForestGreen,
                                            contentColor = ForestBg
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("courses_open_${track.key}")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (done > 0) "واصل التجربة" else "ابدأ المسار")
                                    }
                                    OutlinedButton(
                                        onClick = { roadmapTrack = track },
                                        border = BorderStroke(1.dp, ForestLine),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("courses_roadmap_${track.key}")
                                    ) {
                                        Icon(Icons.Filled.Map, contentDescription = null, tint = ForestMint, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("الخريطة", color = ForestInk)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
