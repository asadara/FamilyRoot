package com.example.familytreeplatform.feature.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.models.ChangeLog

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    currentUserId: String? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var summaryExpanded by rememberSaveable { mutableStateOf(true) }
    val filteredLogs = remember(state.logs, selectedFilter) {
        state.logs.filter(selectedFilter::matches)
    }
    val summary = remember(state.logs) { activitySummary(state.logs) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 760.dp
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 14.dp)) {
            ActivityHeader(
                summary = summary,
                loading = state.loading,
                onRefresh = viewModel::refresh,
                expanded = summaryExpanded,
                onToggleExpanded = { summaryExpanded = !summaryExpanded },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 20.dp else 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Riwayat kolaborasi", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Setiap kontribusi tetap memiliki jejak pembuat dan waktunya.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).horizontalScroll(rememberScrollState())
                    ) {
                        ActivityFilter.entries.forEach { filter ->
                            ActivityFilterPill(
                                label = filter.label,
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                    state.error?.let { error ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                activityErrorMessage(error),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    when {
                        state.loading && state.logs.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        filteredLogs.isEmpty() -> {
                            EmptyActivity(selectedFilter, Modifier.fillMaxSize())
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(filteredLogs, key = { it.changeId }) { log ->
                                    ActivityTimelineItem(
                                        log = log,
                                        isCurrentUser = currentUserId != null && currentUserId == log.actorUserId
                                    )
                                }
                                item { Spacer(Modifier.height(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHeader(
    summary: ActivitySummary,
    loading: Boolean,
    onRefresh: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (!expanded) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Aktivitas",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    Text(
                        "Buka ringkasan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    ActivityHeaderToggle(expanded = false, onClick = onToggleExpanded)
                }
            } else if (maxWidth < 480.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Aktivitas",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        TextButton(onClick = onRefresh, enabled = !loading) {
                            Text(if (loading) "Memuat…" else "Segarkan")
                        }
                        ActivityHeaderToggle(expanded = true, onClick = onToggleExpanded)
                    }
                    Text(
                        "Pantau kontribusi yang membentuk riwayat keluarga.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            "${summary.total} perubahan  ·  ${summary.contributors} kontributor",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aktivitas",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Pantau kontribusi yang membentuk riwayat keluarga.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                        )
                    }
                    ActivitySummaryChip(summary.total.toString(), "Perubahan")
                    Spacer(Modifier.width(8.dp))
                    ActivitySummaryChip(summary.contributors.toString(), "Kontributor")
                    TextButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.padding(start = 4.dp)) {
                        Text(if (loading) "Memuat…" else "Segarkan")
                    }
                    ActivityHeaderToggle(expanded = true, onClick = onToggleExpanded)
                }
            }
        }
    }
}

@Composable
private fun ActivityHeaderToggle(expanded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = Modifier
            .size(36.dp)
            .semantics {
                contentDescription = if (expanded) {
                    "Tutup ringkasan Aktivitas"
                } else {
                    "Buka ringkasan Aktivitas"
                }
            }
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ActivitySummaryChip(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ActivityTimelineItem(log: ChangeLog, isCurrentUser: Boolean) {
    val presentation = remember(log.entityType, log.operation) { activityPresentation(log) }
    val accent = when (presentation.group) {
        ActivityGroup.PERSON -> MaterialTheme.colorScheme.primary
        ActivityGroup.RELATIONSHIP -> MaterialTheme.colorScheme.secondary
        ActivityGroup.COLLABORATION -> MaterialTheme.colorScheme.tertiary
        ActivityGroup.OTHER -> MaterialTheme.colorScheme.outline
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(34.dp).height(112.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(
                    color = accent.copy(alpha = 0.28f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 3f
                )
                drawCircle(color = accent.copy(alpha = 0.2f), radius = 16f, center = Offset(x, 28f))
                drawCircle(color = accent, radius = 7f, center = Offset(x, 28f))
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f).padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(presentation.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            presentation.entityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        activityDateLabel(log.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                log.note?.takeIf(String::isNotBlank)?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
                Text(
                    if (isCurrentUser) "Oleh Anda" else "Kontributor · ••••${log.actorUserId.takeLast(6)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyActivity(filter: ActivityFilter, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(60.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(
                if (filter == ActivityFilter.ALL) "Belum ada aktivitas" else "Belum ada aktivitas pada kategori ini",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "Kontribusi berikutnya akan tercatat di sini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

internal enum class ActivityGroup { PERSON, RELATIONSHIP, COLLABORATION, OTHER }

internal enum class ActivityFilter(val label: String) {
    ALL("Semua"),
    PERSON("Person"),
    RELATIONSHIP("Hubungan"),
    COLLABORATION("Kolaborasi");

    fun matches(log: ChangeLog): Boolean = this == ALL || activityGroup(log.entityType) == when (this) {
        PERSON -> ActivityGroup.PERSON
        RELATIONSHIP -> ActivityGroup.RELATIONSHIP
        COLLABORATION -> ActivityGroup.COLLABORATION
        ALL -> ActivityGroup.OTHER
    }
}

internal data class ActivitySummary(val total: Int, val contributors: Int)

internal data class ActivityPresentation(
    val title: String,
    val entityLabel: String,
    val group: ActivityGroup
)

internal fun activitySummary(logs: List<ChangeLog>) = ActivitySummary(
    total = logs.size,
    contributors = logs.map(ChangeLog::actorUserId).distinct().size
)

internal fun activityGroup(entityType: String): ActivityGroup = when (entityType.uppercase()) {
    "PERSON", "PROFILE", "LIFE_STATUS" -> ActivityGroup.PERSON
    "RELATIONSHIP", "PARENT_CHILD", "SPOUSE" -> ActivityGroup.RELATIONSHIP
    "CLAIM", "PROPOSAL", "INVITATION", "MEMBER", "SPACE_MEMBER" -> ActivityGroup.COLLABORATION
    else -> ActivityGroup.OTHER
}

internal fun activityPresentation(log: ChangeLog): ActivityPresentation {
    val group = activityGroup(log.entityType)
    val entityLabel = when (group) {
        ActivityGroup.PERSON -> "Data person"
        ActivityGroup.RELATIONSHIP -> "Hubungan keluarga"
        ActivityGroup.COLLABORATION -> "Kolaborasi"
        ActivityGroup.OTHER -> log.entityType.lowercase().replaceFirstChar { it.uppercase() }
    }
    val action = when (log.operation.uppercase()) {
        "CREATE" -> "ditambahkan"
        "UPDATE" -> "diperbarui"
        "DELETE" -> "dihapus"
        "VERIFY" -> "diverifikasi"
        "APPROVE" -> "disetujui"
        "REJECT" -> "ditolak"
        "MERGE" -> "digabungkan"
        else -> "diubah"
    }
    return ActivityPresentation("$entityLabel $action", entityLabel, group)
}

internal fun activityDateLabel(createdAt: String): String {
    val cleaned = createdAt.trim().replace('T', ' ').removeSuffix("Z")
    if (cleaned.length < 10) return createdAt
    val date = cleaned.take(10)
    val time = cleaned.drop(11).take(5).takeIf { it.length == 5 }
    return if (time == null) date else "$date · $time"
}

internal fun activityErrorMessage(error: String): String = when {
    error.contains("127.0.0.1", ignoreCase = true) ||
        error.contains("failed to connect", ignoreCase = true) ||
        error.contains("end of stream", ignoreCase = true) ->
        "Riwayat belum dapat dimuat. Periksa koneksi lalu coba segarkan kembali."
    else -> "Riwayat belum dapat dimuat untuk sementara. Coba segarkan kembali."
}
