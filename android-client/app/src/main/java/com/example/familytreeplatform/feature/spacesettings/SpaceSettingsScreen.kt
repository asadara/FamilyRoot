package com.example.familytreeplatform.feature.spacesettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.DuplicateGroup
import com.example.familytreeplatform.models.ProposalItem

@Composable
fun SpaceSettingsScreen(
    viewModel: SpaceSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    fun writePending(uri: android.net.Uri?) {
        val document = state.pendingDocument
        if (uri == null || document == null) {
            viewModel.documentHandled(false)
            return
        }
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                requireNotNull(writer).write(document.content)
            }
        }.isSuccess
        viewModel.documentHandled(saved)
    }

    val gedcomWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vnd.familysearch.gedcom"),
        ::writePending
    )
    val backupWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        ::writePending
    )
    val gedcomReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader().use { requireNotNull(it).readText() }
        }.onSuccess(viewModel::importGedcom)
    }
    val backupReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader().use { requireNotNull(it).readText() }
        }.onSuccess(viewModel::restoreBackup)
    }

    LaunchedEffect(state.pendingDocument) {
        state.pendingDocument?.let { document ->
            if (document.kind == "GEDCOM") gedcomWriter.launch(document.fileName)
            else backupWriter.launch(document.fileName)
        }
    }

    if (state.showClearOfflineConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClearOfflineData,
            title = { Text("Hapus data offline?") },
            text = {
                Text(
                    "Cache person dan hubungan untuk keluarga ini akan dihapus dari perangkat. " +
                        "Data server dan berkas cadangan Anda tidak ikut dihapus."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::clearOfflineData) { Text("Hapus dari perangkat") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelClearOfflineData) { Text("Batal") }
            }
        )
    }

    var portabilityOpen by rememberSaveable { mutableStateOf(true) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var invitationOpen by rememberSaveable { mutableStateOf(false) }
    var claimsOpen by rememberSaveable { mutableStateOf(false) }
    var proposalsOpen by rememberSaveable { mutableStateOf(false) }
    var duplicatesOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().widthIn(max = 1040.dp).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 28.dp)
        ) {
            item {
                SettingsHero(
                    pendingReviews = state.claims.count { it.status == "PENDING" } +
                        state.proposals.count { it.status == "PENDING" },
                    duplicateGroups = state.duplicates.size,
                    onBack = onBack
                )
            }
            state.error?.let { error ->
                item { SettingsNotice(settingsErrorMessage(error), error = true) }
            }
            state.transferMessage?.let { message ->
                item { SettingsNotice(settingsStatusMessage(message)) }
            }
            state.privacyMessage?.let { message ->
                item { SettingsNotice(settingsStatusMessage(message)) }
            }
            item {
                SettingsSection(
                    title = "Portabilitas data",
                    subtitle = "Pindahkan salinan silsilah dan cadangan keluarga",
                    badge = "Data",
                    expanded = portabilityOpen,
                    onToggle = { portabilityOpen = !portabilityOpen }
                ) {
                    Text(
                        "Impor hanya dapat dilakukan ketika silsilah belum memiliki profil anggota. " +
                            "Berkas ini adalah salinan data keluarga, bukan dokumen administratif formal.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    AdaptiveActionPair(
                        firstLabel = "Ekspor GEDCOM",
                        secondLabel = "Buat cadangan",
                        enabled = !state.transferringData,
                        onFirst = viewModel::prepareGedcomExport,
                        onSecond = viewModel::prepareBackupExport
                    )
                    Spacer(Modifier.height(10.dp))
                    AdaptiveActionPair(
                        firstLabel = "Impor GEDCOM",
                        secondLabel = "Pulihkan cadangan",
                        enabled = !state.transferringData,
                        outlined = true,
                        onFirst = { gedcomReader.launch(arrayOf("text/*", "application/octet-stream")) },
                        onSecond = { backupReader.launch(arrayOf("application/json", "text/json")) }
                    )
                    if (state.transferringData) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 14.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Memproses data keluarga…")
                        }
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Privasi perangkat",
                    subtitle = "Kelola salinan offline pada perangkat ini",
                    badge = "Privat",
                    expanded = privacyOpen,
                    onToggle = { privacyOpen = !privacyOpen }
                ) {
                    Text(
                        "Data keluarga tidak disertakan dalam backup cloud Android. Berkas ekspor " +
                            "disimpan di lokasi yang Anda pilih dan tidak dienkripsi otomatis oleh TRêdhAH.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        enabled = !state.clearingOfflineData,
                        onClick = viewModel::requestClearOfflineData,
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        Text(if (state.clearingOfflineData) "Sedang menghapus…" else "Hapus data offline")
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Undangan keluarga",
                    subtitle = "Beri akses dengan peran dan masa berlaku terbatas",
                    badge = "Kolaborasi",
                    expanded = invitationOpen,
                    onToggle = { invitationOpen = !invitationOpen }
                ) {
                    Text("Tentukan kewenangan penerima undangan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        listOf("VIEWER", "EDITOR", "ADMIN").forEach { role ->
                            FilterChip(
                                selected = state.role == role,
                                onClick = { viewModel.setRole(role) },
                                label = { Text(invitationRoleLabel(role)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.expiresInDays,
                        onValueChange = viewModel::setExpiresInDays,
                        label = { Text("Masa berlaku (hari)") },
                        supportingText = { Text("Antara 1–30 hari") },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        singleLine = true
                    )
                    Button(
                        enabled = !state.creating,
                        onClick = viewModel::createInvitation,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(if (state.creating) "Membuat undangan…" else "Buat kode undangan")
                    }
                    state.invitation?.let { invitation ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Kode siap dibagikan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${invitation.spaceName} · ${invitationRoleLabel(invitation.role)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    invitation.token,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                                Text("Berlaku hingga ${invitation.expiresAt}", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(invitation.token)) },
                                    modifier = Modifier.padding(top = 10.dp)
                                ) { Text("Salin kode") }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Validasi identitas",
                    subtitle = "Tinjau permintaan person yang mengaku sebagai dirinya",
                    badge = pendingBadge(state.claims.count { it.status == "PENDING" }),
                    expanded = claimsOpen,
                    onToggle = { claimsOpen = !claimsOpen }
                ) {
                    ReviewHeader(loading = state.loadingClaims, onRefresh = viewModel::refreshClaims)
                    if (!state.loadingClaims && state.claims.isEmpty()) {
                        EmptyReview("Belum ada permintaan validasi.")
                    }
                    state.claims.forEach { claim ->
                        ClaimCard(claim, state.verifyingClaimId, viewModel::verifyClaim)
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Usulan perubahan",
                    subtitle = "Putuskan kontribusi yang menunggu persetujuan",
                    badge = pendingBadge(state.proposals.count { it.status == "PENDING" }),
                    expanded = proposalsOpen,
                    onToggle = { proposalsOpen = !proposalsOpen }
                ) {
                    ReviewHeader(loading = state.loadingProposals, onRefresh = viewModel::refreshProposals)
                    if (!state.loadingProposals && state.proposals.isEmpty()) {
                        EmptyReview("Belum ada usulan perubahan.")
                    }
                    state.proposals.forEach { proposal ->
                        ProposalCard(
                            proposal = proposal,
                            reviewingId = state.reviewingProposalId,
                            onApprove = viewModel::approveProposal,
                            onReject = viewModel::rejectProposal
                        )
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Kemungkinan data ganda",
                    subtitle = "Periksa sebelum menggabungkan dua person",
                    badge = pendingBadge(state.duplicates.size),
                    expanded = duplicatesOpen,
                    onToggle = { duplicatesOpen = !duplicatesOpen }
                ) {
                    ReviewHeader(loading = state.loadingDuplicates, onRefresh = viewModel::refreshDuplicates)
                    if (!state.loadingDuplicates && state.duplicates.isEmpty()) {
                        EmptyReview("Tidak ditemukan kandidat data ganda.")
                    }
                    state.duplicates.forEach { group ->
                        DuplicateCard(group, state.merging, viewModel::mergeDuplicate)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHero(pendingReviews: Int, duplicateGroups: Int, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹  Kembali") }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
                val compact = maxWidth < 520.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsHeroCopy()
                        SettingsHeroStats(pendingReviews, duplicateGroups)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsHeroCopy(Modifier.weight(1f))
                        SettingsHeroStats(pendingReviews, duplicateGroups)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeroCopy(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "Pengaturan keluarga",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Kelola akses, validasi, privasi perangkat, dan portabilitas data.",
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingsHeroStats(pendingReviews: Int, duplicateGroups: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricPill(pendingReviews.toString(), "menunggu")
        MetricPill(duplicateGroups.toString(), "duplikat")
    }
}

@Composable
private fun MetricPill(value: String, label: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), shape = RoundedCornerShape(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    badge: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics { contentDescription = if (expanded) "Tutup $title" else "Buka $title" }
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Text(badge, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
        }
    }
}

@Composable
private fun AdaptiveActionPair(
    firstLabel: String,
    secondLabel: String,
    enabled: Boolean,
    outlined: Boolean = false,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsAction(firstLabel, enabled, outlined, onFirst, Modifier.fillMaxWidth())
                SettingsAction(secondLabel, enabled, outlined, onSecond, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsAction(firstLabel, enabled, outlined, onFirst, Modifier.weight(1f))
                SettingsAction(secondLabel, enabled, outlined, onSecond, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsAction(label: String, enabled: Boolean, outlined: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (outlined) OutlinedButton(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
    else Button(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun ReviewHeader(loading: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(enabled = !loading, onClick = onRefresh) { Text("Perbarui") }
        if (loading) CircularProgressIndicator()
    }
}

@Composable
private fun EmptyReview(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp)) }
}

@Composable
private fun ClaimCard(claim: ClaimReviewItem, verifyingId: String?, onVerify: (String) -> Unit) {
    ReviewCard {
        Text(claim.personName ?: "Person keluarga", style = MaterialTheme.typography.titleMedium)
        Text("Status: ${reviewStatusLabel(claim.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Peran anggota: ${memberRoleLabel(claim.memberRole)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Kontributor ···${claim.userId.takeLast(6)}", style = MaterialTheme.typography.bodySmall)
        if (claim.status == "PENDING") {
            Button(
                enabled = verifyingId != claim.claimId,
                onClick = { onVerify(claim.claimId) },
                modifier = Modifier.padding(top = 10.dp)
            ) { Text(if (verifyingId == claim.claimId) "Memvalidasi…" else "Validasi identitas") }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: ProposalItem,
    reviewingId: String?,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    ReviewCard {
        Text(proposalFieldLabel(proposal.field), style = MaterialTheme.typography.titleMedium)
        Text(proposal.proposedValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Status: ${reviewStatusLabel(proposal.status)}", style = MaterialTheme.typography.bodySmall)
        proposal.reason?.takeIf { it.isNotBlank() }?.let { Text("Alasan: $it", modifier = Modifier.padding(top = 4.dp)) }
        if (proposal.status == "PENDING") {
            AdaptiveActionPair(
                firstLabel = "Setujui",
                secondLabel = "Tolak",
                enabled = reviewingId != proposal.proposalId,
                outlined = true,
                onFirst = { onApprove(proposal.proposalId) },
                onSecond = { onReject(proposal.proposalId) }
            )
        }
    }
}

@Composable
private fun DuplicateCard(group: DuplicateGroup, merging: Boolean, onMerge: (String, String) -> Unit) {
    ReviewCard {
        Text("Perlu diperiksa", style = MaterialTheme.typography.titleMedium)
        Text(duplicateReasonLabel(group.reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
        group.people.forEachIndexed { index, person -> Text("${index + 1}. ${person.fullName}", modifier = Modifier.padding(top = 4.dp)) }
        if (group.people.size >= 2) {
            OutlinedButton(
                enabled = !merging,
                onClick = { onMerge(group.people[1].personId, group.people[0].personId) },
                modifier = Modifier.padding(top = 10.dp)
            ) { Text(if (merging) "Menggabungkan…" else "Gabungkan ke data pertama") }
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun SettingsNotice(message: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(14.dp)
        )
    }
}

internal fun invitationRoleLabel(role: String?): String = when (role) {
    "VIEWER" -> "Pembaca"
    "EDITOR" -> "Kontributor"
    "ADMIN" -> "Pengelola"
    "OWNER" -> "Pemilik"
    else -> "Anggota"
}

internal fun memberRoleLabel(role: String?): String = invitationRoleLabel(role)

internal fun reviewStatusLabel(status: String?): String = when (status) {
    "PENDING" -> "Menunggu"
    "APPROVED", "VERIFIED" -> "Disetujui"
    "REJECTED" -> "Ditolak"
    else -> "Belum diketahui"
}

internal fun proposalFieldLabel(field: String): String = when (field) {
    "notes" -> "Catatan person"
    "birthPlace" -> "Tempat lahir"
    "lifeStatus" -> "Status kehidupan"
    else -> field.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

internal fun duplicateReasonLabel(reason: String): String = when {
    reason.contains("name", ignoreCase = true) -> "Nama atau identitas person terlihat serupa."
    reason.contains("birth", ignoreCase = true) -> "Data kelahiran person terlihat serupa."
    else -> "Beberapa informasi person terlihat serupa."
}

internal fun pendingBadge(count: Int): String = if (count > 0) "$count menunggu" else "Bersih"

internal fun settingsErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return when {
        value.contains("Expiry", ignoreCase = true) -> "Masa berlaku harus antara 1–30 hari."
        value.contains("already", ignoreCase = true) && value.contains("member", ignoreCase = true) ->
            "Pengguna tersebut sudah menjadi anggota silsilah."
        value.contains("403") || value.contains("FORBIDDEN", ignoreCase = true) ->
            "Akun Anda tidak memiliki izin untuk tindakan ini."
        value.contains("401") || value.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Sesi masuk sudah berakhir. Silakan masuk kembali."
        value.contains("500") || value.contains("INTERNAL_ERROR", ignoreCase = true) ||
            value.contains("502") || value.contains("503") || value.contains("504") ->
            "Server sedang bermasalah. Coba lagi beberapa saat."
        value.contains("connect", ignoreCase = true) || value.contains("failed", ignoreCase = true) ||
            value.contains("127.0.0.1") || value.contains("localhost") ->
            "Data belum dapat diperbarui. Periksa koneksi lalu coba kembali."
        value.isBlank() -> "Tindakan belum dapat diselesaikan. Coba kembali."
        else -> "Tindakan belum dapat diselesaikan. Coba kembali."
    }
}

internal fun settingsStatusMessage(message: String): String = when {
    message == "File saved" -> "Berkas berhasil disimpan."
    message.startsWith("Imported ") -> message
        .replace("Imported", "Berhasil mengimpor")
        .replace("people and", "person dan")
        .replace("relationships", "hubungan")
    message.startsWith("Restored ") -> message
        .replace("Restored", "Berhasil memulihkan")
        .replace("people and", "person dan")
        .replace("relationships", "hubungan")
    message.contains("Offline family data removed") -> "Data offline keluarga telah dihapus dari perangkat."
    else -> message
}
