package com.example.familytreeplatform.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.feature.activity.activityDateLabel
import com.example.familytreeplatform.feature.activity.activityPresentation
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.PersonListItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    displayName: String,
    spaceName: String,
    pendingSyncCount: Int,
    onOpenTree: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = homeSummary(state.people, state.recentActivity, pendingSyncCount)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 16.dp, vertical = 18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                HomeHero(
                    displayName = displayName,
                    spaceName = spaceName,
                    onOpenTree = onOpenTree,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            state.error?.let {
                item { HomeNotice(homeErrorMessage(it), Modifier.fillMaxWidth().widthIn(max = 980.dp)) }
            }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                        HomeMetric(summary.people, "person", Modifier.weight(1f))
                        HomeMetric(summary.living, "hidup", Modifier.weight(1f))
                        HomeMetric(summary.contributors, "kontributor", Modifier.weight(1f))
                        HomeMetric(summary.pendingSync, "menunggu sync", Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            HomeMetric(summary.people, "person", Modifier.weight(1f))
                            HomeMetric(summary.living, "hidup", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            HomeMetric(summary.contributors, "kontributor", Modifier.weight(1f))
                            HomeMetric(summary.pendingSync, "menunggu sync", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                        FamilyAttentionCard(summary, onOpenFamily, Modifier.weight(0.9f))
                        RecentActivityCard(state, viewModel::refresh, onOpenActivity, Modifier.weight(1.1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                        FamilyAttentionCard(summary, onOpenFamily)
                        RecentActivityCard(state, viewModel::refresh, onOpenActivity)
                    }
                }
            }
            item {
                QuickActions(onOpenTree, onOpenFamily, onOpenActivity, Modifier.fillMaxWidth().widthIn(max = 980.dp))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HomeHero(displayName: String, spaceName: String, onOpenTree: () -> Unit, modifier: Modifier = Modifier) {
    val branchColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f))
                    )
                )
                .padding(22.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val base = Offset(size.width * 0.86f, size.height * 0.95f)
                drawLine(branchColor, base, Offset(size.width * 0.9f, size.height * 0.18f), 4f, StrokeCap.Round)
                drawLine(branchColor, Offset(size.width * 0.88f, size.height * 0.55f), Offset(size.width * 0.76f, size.height * 0.34f), 3f, StrokeCap.Round)
                drawCircle(branchColor, 13f, Offset(size.width * 0.9f, size.height * 0.18f), style = Stroke(4f))
                drawCircle(branchColor, 12f, Offset(size.width * 0.76f, size.height * 0.34f), style = Stroke(4f))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 520.dp
                if (compact) {
                    Column {
                        HomeHeroCopy(displayName, spaceName)
                        Button(onClick = onOpenTree, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Lanjutkan pohon") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HomeHeroCopy(displayName, spaceName, Modifier.weight(1f))
                        Button(onClick = onOpenTree) { Text("Lanjutkan pohon") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCopy(displayName: String, spaceName: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Beranda keluarga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "Selamat datang, ${homeFirstName(displayName)}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.semantics { heading() }
        )
        Text(spaceName, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HomeMetric(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FamilyAttentionCard(summary: HomeSummary, onOpenFamily: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Yang dapat dilengkapi", style = MaterialTheme.typography.titleLarge)
            Text("Petunjuk ringan untuk menjaga data keluarga tetap berguna.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AttentionRow(summary.missingBirthDate, "person belum memiliki tanggal lahir")
            AttentionRow(summary.unknownLifeStatus, "person belum memiliki status kehidupan")
            AttentionRow(summary.pendingSync, "perubahan menunggu sinkronisasi")
            OutlinedButton(onClick = onOpenFamily, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Buka direktori keluarga") }
        }
    }
}

@Composable
private fun AttentionRow(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Text(value.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
        Text(label, modifier = Modifier.padding(start = 10.dp).weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentActivityCard(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Aktivitas terbaru", style = MaterialTheme.typography.titleLarge)
                    Text("Kontribusi terakhir dalam keluarga", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = !state.loading, onClick = onRefresh) { Text("Perbarui") }
            }
            if (state.loading && state.recentActivity.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                    CircularProgressIndicator()
                    Text("Memuat aktivitas…")
                }
            } else if (state.recentActivity.isEmpty()) {
                Text("Belum ada kontribusi yang tercatat.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
            } else {
                state.recentActivity.take(3).forEach { HomeActivityRow(it) }
            }
            OutlinedButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Lihat seluruh aktivitas") }
        }
    }
}

@Composable
private fun HomeActivityRow(log: ChangeLog) {
    val presentation = activityPresentation(log)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("•", color = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(presentation.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(presentation.entityLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(activityDateLabel(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActions(onOpenTree: () -> Unit, onOpenFamily: () -> Unit, onOpenActivity: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Jalan cepat", style = MaterialTheme.typography.titleLarge)
        Text("Lanjutkan pekerjaan tanpa kehilangan konteks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (maxWidth >= 620.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Pohon", "Jelajahi lineage", onOpenTree, Modifier.weight(1f))
                    QuickAction("Keluarga", "Cari dan buka person", onOpenFamily, Modifier.weight(1f))
                    QuickAction("Aktivitas", "Tinjau kontribusi", onOpenActivity, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Pohon", "Jelajahi lineage", onOpenTree)
                    QuickAction("Keluarga", "Cari dan buka person", onOpenFamily)
                    QuickAction("Aktivitas", "Tinjau kontribusi", onOpenActivity)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(15.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeNotice(message: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
    }
}

internal data class HomeSummary(
    val people: Int,
    val living: Int,
    val contributors: Int,
    val pendingSync: Int,
    val missingBirthDate: Int,
    val unknownLifeStatus: Int
)

internal fun homeSummary(people: List<PersonListItem>, logs: List<ChangeLog>, pendingSync: Int) = HomeSummary(
    people = people.size,
    living = people.count { it.lifeStatus == "ALIVE" },
    contributors = logs.map(ChangeLog::actorUserId).distinct().size,
    pendingSync = pendingSync,
    missingBirthDate = people.count { it.birthDate.isNullOrBlank() },
    unknownLifeStatus = people.count { it.lifeStatus == "UNKNOWN" }
)

internal fun homeFirstName(displayName: String): String = displayName.trim().substringBefore(' ').ifBlank { "keluarga" }

internal fun homeErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return if (
        value.contains("connect", ignoreCase = true) || value.contains("failed", ignoreCase = true) ||
        value.contains("127.0.0.1") || value.contains("localhost")
    ) "Ringkasan belum dapat diperbarui. Data tersimpan tetap dapat digunakan."
    else "Sebagian ringkasan belum dapat dimuat. Coba perbarui kembali."
}
