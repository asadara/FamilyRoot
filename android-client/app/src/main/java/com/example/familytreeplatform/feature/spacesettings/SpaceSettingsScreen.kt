package com.example.familytreeplatform.feature.spacesettings

import android.content.Intent

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.example.familytreeplatform.models.ProposalCommentItem
import com.example.familytreeplatform.models.SpaceMember
import com.example.familytreeplatform.models.SpaceInvitation

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
    var rejectingProposalId by rememberSaveable { mutableStateOf<String?>(null) }
    var proposalRejectionReason by rememberSaveable { mutableStateOf("") }
    var expandedProposalDiscussionId by rememberSaveable {
        mutableStateOf<String?>(null)
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
    LaunchedEffect(state.spaceDeleted) {
        if (state.spaceDeleted) onBack()
    }

    if (state.showArchiveConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelLifecycleConfirmation,
            title = { Text("Arsipkan silsilah?") },
            text = {
                Text(
                    "Silsilah menjadi read-only untuk semua anggota dan seluruh undangan aktif " +
                        "akan dicabut. Data tetap tersimpan dan Pemilik dapat mengaktifkannya kembali."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmArchiveSpace) {
                    Text("Arsipkan")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelLifecycleConfirmation) { Text("Batal") }
            }
        )
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelLifecycleConfirmation,
            title = { Text("Hapus silsilah?") },
            text = {
                Column {
                    Text(
                        "Data akan disembunyikan dari semua anggota. Riwayat audit tetap disimpan. " +
                            "Pastikan Anda telah membuat ekspor bila memerlukannya."
                    )
                    OutlinedTextField(
                        value = state.deleteConfirmation,
                        onValueChange = viewModel::setDeleteConfirmation,
                        label = { Text("Ketik persis: ${state.spaceName}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDeleteExportAcknowledged(
                                    !state.deleteExportAcknowledged
                                )
                            }
                            .padding(top = 8.dp)
                    ) {
                        Checkbox(
                            checked = state.deleteExportAcknowledged,
                            onCheckedChange = viewModel::setDeleteExportAcknowledged
                        )
                        Text("Saya sudah mengekspor data atau memilih untuk tidak mengekspor.")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = state.deleteConfirmation == state.spaceName &&
                        state.deleteExportAcknowledged &&
                        state.lifecycleAction == null,
                    onClick = viewModel::confirmDeleteSpace,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Hapus silsilah") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelLifecycleConfirmation) { Text("Batal") }
            }
        )
    }
    state.pendingRoleChange?.let { change ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMembershipConfirmation,
            title = { Text("Ubah peran anggota?") },
            text = {
                Text(
                    "${change.member.displayName} akan menjadi " +
                        "${memberRoleLabel(change.role)}. Kewenangan aksesnya langsung mengikuti peran baru."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRoleChange) { Text("Ubah peran") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMembershipConfirmation) { Text("Batal") }
            }
        )
    }
    state.pendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMembershipConfirmation,
            title = { Text("Keluarkan anggota?") },
            text = {
                Text(
                    "${member.displayName} akan kehilangan akses ke silsilah ini. " +
                        "Data person, hubungan keluarga, dan riwayat kontribusinya tidak ikut dihapus. " +
                        "Cache pada perangkat anggota dibersihkan saat akses diperiksa kembali."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRemoveMember,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Keluarkan") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMembershipConfirmation) { Text("Batal") }
            }
        )
    }
    state.pendingTransfer?.let { member ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMembershipConfirmation,
            title = { Text("Pindahkan kepemilikan?") },
            text = {
                Text(
                    "${member.displayName} akan memperoleh kendali penuh sebagai Pemilik. " +
                        "Peran Anda berubah menjadi Pengelola. Tindakan ini tidak menghapus data keluarga."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmTransferOwnership) {
                    Text("Pindahkan kepemilikan")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMembershipConfirmation) { Text("Batal") }
            }
        )
    }
    state.pendingInvitationRevoke?.let { invitation ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMembershipConfirmation,
            title = { Text("Cabut undangan?") },
            text = {
                Text(
                    "Undangan ${memberRoleLabel(invitation.role)} ini tidak dapat dipakai lagi. " +
                        "Anggota yang sudah menerima undangan tidak ikut dikeluarkan."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRevokeInvitation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Cabut undangan") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMembershipConfirmation) { Text("Batal") }
            }
        )
    }
    if (state.showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMembershipConfirmation,
            title = { Text("Keluar dari silsilah?") },
            text = {
                Text(
                    "Akses Anda dan cache keluarga pada perangkat ini akan dihapus. " +
                        "Data keluarga di server tidak ikut terhapus. Buat ekspor terlebih dahulu bila diperlukan."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmLeaveSpace,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Keluar dari silsilah") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMembershipConfirmation) { Text("Batal") }
            }
        )
    }

    rejectingProposalId?.let { proposalId ->
        val proposal = state.proposals.firstOrNull { it.proposalId == proposalId }
        AlertDialog(
            onDismissRequest = {
                rejectingProposalId = null
                proposalRejectionReason = ""
            },
            title = { Text("Tolak usulan perubahan?") },
            text = {
                Column {
                    Text(
                        "Jelaskan alasan agar kontributor memahami keputusan untuk " +
                            "${proposal?.personName ?: "person ini"}."
                    )
                    OutlinedTextField(
                        value = proposalRejectionReason,
                        onValueChange = { proposalRejectionReason = it.take(1000) },
                        label = { Text("Alasan penolakan") },
                        supportingText = {
                            Text("${proposalRejectionReason.length}/1000")
                        },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isProposalRejectionReasonValid(proposalRejectionReason) &&
                        state.reviewingProposalId != proposalId,
                    onClick = {
                        viewModel.rejectProposal(proposalId, proposalRejectionReason)
                        rejectingProposalId = null
                        proposalRejectionReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Tolak usulan") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rejectingProposalId = null
                        proposalRejectionReason = ""
                    }
                ) { Text("Batal") }
            }
        )
    }

    var portabilityOpen by rememberSaveable { mutableStateOf(true) }
    var lifecycleOpen by rememberSaveable { mutableStateOf(true) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var membersOpen by rememberSaveable { mutableStateOf(true) }
    var invitationOpen by rememberSaveable { mutableStateOf(false) }
    var claimsOpen by rememberSaveable { mutableStateOf(false) }
    var historyAccessOpen by rememberSaveable { mutableStateOf(false) }
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
                        state.proposals.count { it.status == "PENDING" } +
                        state.historyAccessRequests.count { it.status == "PENDING" },
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
            state.membershipMessage?.let { message ->
                item { SettingsNotice(message) }
            }
            state.lifecycleMessage?.let { message ->
                item { SettingsNotice(message) }
            }
            if (state.spaceStatus == "ARCHIVED") {
                item {
                    SettingsNotice(
                        "Silsilah ini sedang diarsipkan. Semua data dapat dibaca, tetapi perubahan " +
                            "baru ditolak sampai Pemilik mengaktifkannya kembali."
                    )
                }
            }
            if (state.memberRole == "OWNER") {
                item {
                    SettingsSection(
                        title = "Status silsilah",
                        subtitle = "Arsipkan, aktifkan kembali, atau hapus dengan aman",
                        badge = if (state.spaceStatus == "ARCHIVED") "Diarsipkan" else "Aktif",
                        expanded = lifecycleOpen,
                        onToggle = { lifecycleOpen = !lifecycleOpen }
                    ) {
                        val impact = state.lifecycleImpact
                        Text(
                            if (state.spaceStatus == "ARCHIVED") {
                                "Mode read-only aktif. Data dan riwayat tidak dihapus."
                            } else {
                                "Arsipkan lebih dahulu sebelum penghapusan tersedia."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (impact != null) {
                            Text(
                                "${impact.personCount} person · ${impact.relationshipCount} hubungan · " +
                                    "${impact.memberCount} anggota · ${impact.activeInvitationCount} undangan aktif",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                "${impact.mediaCount} media · ${impact.sourceCount} sumber · " +
                                    "${impact.pendingProposalCount} usulan menunggu",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ReviewHeader(
                            loading = state.loadingLifecycle,
                            onRefresh = viewModel::refreshLifecycle
                        )
                        if (state.spaceStatus == "ACTIVE") {
                            OutlinedButton(
                                enabled = state.lifecycleAction == null,
                                onClick = viewModel::requestArchiveSpace
                            ) {
                                Text("Arsipkan silsilah")
                            }
                        } else {
                            AdaptiveActionPair(
                                firstLabel = if (state.lifecycleAction == "RESTORE") {
                                    "Mengaktifkan..."
                                } else {
                                    "Aktifkan kembali"
                                },
                                secondLabel = "Hapus silsilah",
                                enabled = state.lifecycleAction == null,
                                onFirst = viewModel::restoreSpace,
                                onSecond = viewModel::requestDeleteSpace
                            )
                        }
                    }
                }
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
            if (state.memberRole in setOf("OWNER", "ADMIN")) {
                item {
                    SettingsSection(
                        title = "Akses riwayat lengkap",
                        subtitle = "Setujui permintaan melihat aktivitas lama secara bertahap",
                        badge = pendingBadge(
                            state.historyAccessRequests.count { it.status == "PENDING" }
                        ),
                        expanded = historyAccessOpen,
                        onToggle = { historyAccessOpen = !historyAccessOpen }
                    ) {
                        ReviewHeader(
                            loading = state.loadingHistoryAccessRequests,
                            onRefresh = viewModel::refreshHistoryAccessRequests
                        )
                        if (!state.loadingHistoryAccessRequests &&
                            state.historyAccessRequests.isEmpty()
                        ) {
                            EmptyReview("Belum ada permintaan akses riwayat lengkap.")
                        }
                        state.historyAccessRequests.forEach { request ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        request.userDisplayName ?: "Anggota keluarga",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        when (request.status) {
                                            "APPROVED" -> "Disetujui"
                                            "REJECTED" -> "Ditolak"
                                            else -> "Menunggu keputusan"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    if (request.status == "PENDING") {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 10.dp)
                                        ) {
                                            Button(
                                                enabled = state.reviewingHistoryAccessRequestId != request.requestId,
                                                onClick = {
                                                    viewModel.reviewHistoryAccessRequest(
                                                        request.requestId,
                                                        true
                                                    )
                                                }
                                            ) { Text("Setujui") }
                                            OutlinedButton(
                                                enabled = state.reviewingHistoryAccessRequestId != request.requestId,
                                                onClick = {
                                                    viewModel.reviewHistoryAccessRequest(
                                                        request.requestId,
                                                        false
                                                    )
                                                }
                                            ) { Text("Tolak") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSection(
                    title = "Anggota & akses",
                    subtitle = "Kelola peran, kepemilikan, dan akses silsilah",
                    badge = "${state.members.size} anggota",
                    expanded = membersOpen,
                    onToggle = { membersOpen = !membersOpen }
                ) {
                    Text(
                        "Pemilik mengatur seluruh anggota. Pengelola hanya dapat mengatur " +
                            "Kontributor dan Pembaca. Perubahan akses tercatat pada Aktivitas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReviewHeader(
                        loading = state.loadingMembers,
                        onRefresh = viewModel::refreshMembers
                    )
                    if (!state.loadingMembers && state.members.isEmpty()) {
                        EmptyReview("Daftar anggota belum tersedia.")
                    }
                    state.members.forEach { member ->
                        MembershipCard(
                            member = member,
                            actorRole = state.memberRole,
                            actionBusy = state.membershipActionMemberId == member.memberId,
                            onChangeRole = { role -> viewModel.requestRoleChange(member, role) },
                            onTransfer = { viewModel.requestTransferOwnership(member) },
                            onRemove = { viewModel.requestRemoveMember(member) }
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Text("Akses saya", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.memberRole == "OWNER") {
                            "Pemilik harus memindahkan kepemilikan sebelum keluar agar silsilah tidak kehilangan pengelola utama."
                        } else {
                            "Keluar hanya menghapus akses Anda. Person dan sejarah keluarga tetap tersimpan."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedButton(
                        enabled = state.memberRole != "OWNER" && !state.leavingSpace,
                        onClick = viewModel::requestLeaveSpace,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            if (state.leavingSpace) "Sedang keluar..." else "Keluar dari silsilah",
                            color = if (state.memberRole == "OWNER") {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
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
                    if (!state.loadingInvitePermission && state.memberRole !in setOf("OWNER", "ADMIN")) {
                        SettingsNotice(
                            "Hanya pemilik atau pengelola silsilah yang dapat membuat undangan.",
                            error = true
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        listOf("VIEWER", "EDITOR", "ADMIN").forEach { role ->
                            FilterChip(
                                selected = state.role == role,
                                onClick = { viewModel.setRole(role) },
                                enabled = state.memberRole in setOf("OWNER", "ADMIN"),
                                label = { Text(invitationRoleLabel(role)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.invitationTargetEmail,
                        onValueChange = viewModel::setInvitationTargetEmail,
                        label = { Text("Email penerima (disarankan)") },
                        supportingText = {
                            Text("Jika diisi, hanya akun dengan email ini yang dapat menerima.")
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.expiresInDays,
                        onValueChange = viewModel::setExpiresInDays,
                        label = { Text("Masa berlaku (hari)") },
                        supportingText = { Text("Antara 1–30 hari") },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        singleLine = true
                    )
                    Button(
                        enabled = !state.creating && !state.loadingInvitePermission &&
                            state.memberRole in setOf("OWNER", "ADMIN"),
                        onClick = viewModel::createInvitation,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(if (state.creating) "Membuat undangan…" else "Buat kode undangan")
                    }
                    state.invitationError?.let { error ->
                        Spacer(Modifier.height(10.dp))
                        SettingsNotice(settingsErrorMessage(error), error = true)
                    }
                    state.invitationMessage?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        SettingsNotice(message)
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
                                invitation.maskedTargetEmail?.let { target ->
                                    Text(
                                        "Khusus akun $target",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    invitation.token,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                                Text("Berlaku hingga ${invitation.expiresAt}", style = MaterialTheme.typography.bodySmall)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { clipboardManager.setText(AnnotatedString(invitation.token)) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Salin kode") }
                                    OutlinedButton(
                                        onClick = {
                                            val sharedText = "Undangan ${invitation.spaceName}\nKode: ${invitation.token}"
                                            context.startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, sharedText)
                                                    },
                                                    "Bagikan undangan"
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Bagikan") }
                                }
                            }
                        }
                    }
                    if (state.memberRole in setOf("OWNER", "ADMIN")) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        Text(
                            "Riwayat undangan",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Kode rahasia tidak ditampilkan kembali. Cabut undangan aktif yang tidak lagi diperlukan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        listOf(
                            listOf("ALL", "ACTIVE", "ACCEPTED"),
                            listOf("REVOKED", "EXPIRED")
                        ).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { status ->
                                    FilterChip(
                                        selected = state.invitationStatusFilter == status,
                                        onClick = { viewModel.setInvitationStatusFilter(status) },
                                        label = { Text(invitationStatusLabel(status)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        ReviewHeader(
                            loading = state.loadingInvitations,
                            onRefresh = viewModel::refreshInvitations
                        )
                        if (!state.loadingInvitations && state.invitations.isEmpty()) {
                            EmptyReview("Tidak ada undangan pada filter ini.")
                        }
                        state.invitations.forEach { invitation ->
                            InvitationHistoryCard(
                                invitation = invitation,
                                revoking = state.revokingInvitationId == invitation.inviteId,
                                onRevoke = { viewModel.requestRevokeInvitation(invitation) }
                            )
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
                            comments = state.proposalComments[proposal.proposalId].orEmpty(),
                            commentsExpanded =
                                expandedProposalDiscussionId == proposal.proposalId,
                            loadingComments =
                                proposal.proposalId in state.loadingProposalComments,
                            commentDraft =
                                state.proposalCommentDrafts[proposal.proposalId].orEmpty(),
                            postingComment =
                                state.postingProposalCommentId == proposal.proposalId,
                            onApprove = viewModel::approveProposal,
                            onReject = { proposalId ->
                                rejectingProposalId = proposalId
                                proposalRejectionReason = ""
                            },
                            onToggleComments = { proposalId ->
                                if (expandedProposalDiscussionId == proposalId) {
                                    expandedProposalDiscussionId = null
                                } else {
                                    expandedProposalDiscussionId = proposalId
                                    viewModel.refreshProposalComments(proposalId)
                                }
                            },
                            onCommentDraftChange =
                                viewModel::setProposalCommentDraft,
                            onAddComment = viewModel::addProposalComment
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
private fun InvitationHistoryCard(
    invitation: SpaceInvitation,
    revoking: Boolean,
    onRevoke: () -> Unit
) {
    ReviewCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    memberRoleLabel(invitation.role),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Dibuat ${invitation.createdAt.take(10)} oleh ${invitation.createdByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    invitationStatusLabel(invitation.status),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        invitation.acceptedByName?.let { name ->
            Text(
                "Diterima oleh $name",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        invitation.maskedTargetEmail?.let { target ->
            Text(
                "Ditujukan ke $target",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            "Berlaku hingga ${invitation.expiresAt.take(10)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (invitation.status == "ACTIVE") {
            OutlinedButton(
                enabled = !revoking,
                onClick = onRevoke,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    if (revoking) "Mencabut..." else "Cabut undangan",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MembershipCard(
    member: SpaceMember,
    actorRole: String?,
    actionBusy: Boolean,
    onChangeRole: (String) -> Unit,
    onTransfer: () -> Unit,
    onRemove: () -> Unit
) {
    val roles = manageableRoles(actorRole, member)
    ReviewCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (member.isCurrentUser) "${member.displayName} (Anda)" else member.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Bergabung ${member.joinedAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Text(
                    memberRoleLabel(member.role),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        if (roles.isNotEmpty()) {
            Text(
                "Peran akses",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                roles.forEach { role ->
                    FilterChip(
                        selected = member.role == role,
                        enabled = !actionBusy,
                        onClick = { onChangeRole(role) },
                        label = { Text(memberRoleLabel(role)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (actorRole == "OWNER") {
                Spacer(Modifier.height(8.dp))
                AdaptiveActionPair(
                    firstLabel = "Jadikan pemilik",
                    secondLabel = "Keluarkan",
                    enabled = !actionBusy,
                    outlined = true,
                    onFirst = onTransfer,
                    onSecond = onRemove
                )
            } else {
                OutlinedButton(
                    enabled = !actionBusy,
                    onClick = onRemove,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Keluarkan", color = MaterialTheme.colorScheme.error)
                }
            }
            if (actionBusy) {
                Text(
                    "Memperbarui akses...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ClaimCard(claim: ClaimReviewItem, verifyingId: String?, onVerify: (String) -> Unit) {
    ReviewCard {
        Text(claim.personName ?: "Person keluarga", style = MaterialTheme.typography.titleMedium)
        Text("Status: ${reviewStatusLabel(claim.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Peran anggota: ${memberRoleLabel(claim.memberRole)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Kontributor ···${claim.userId.takeLast(6)}", style = MaterialTheme.typography.bodySmall)
        Text(
            if (claim.verificationBasis == "LEGACY") {
                "Diverifikasi sebelum aturan konfirmasi kolektif."
            } else {
                "Konfirmasi keluarga: ${claim.confirmationCount}/${claim.required}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    comments: List<ProposalCommentItem>,
    commentsExpanded: Boolean,
    loadingComments: Boolean,
    commentDraft: String,
    postingComment: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onToggleComments: (String) -> Unit,
    onCommentDraftChange: (String, String) -> Unit,
    onAddComment: (String) -> Unit
) {
    ReviewCard {
        Text(proposalFieldLabel(proposal.field), style = MaterialTheme.typography.titleMedium)
        proposal.personName?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Saat ini",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            proposalCurrentValueLabel(proposal),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (
            proposal.beforeValue != null &&
            proposal.beforeValue != proposal.currentValue
        ) {
            Text(
                "Saat diajukan: ${proposal.beforeValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Usulan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            proposalValueLabel(proposal),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Status: ${reviewStatusLabel(proposal.status)}", style = MaterialTheme.typography.bodySmall)
        proposal.reason?.takeIf { it.isNotBlank() }?.let {
            Text("Alasan pengusul: $it", modifier = Modifier.padding(top = 4.dp))
        }
        proposal.reviewReason?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Catatan peninjau: $it",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (proposal.reviewedAt != null) {
            Text(
                "Keputusan review telah tercatat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        TextButton(
            onClick = { onToggleComments(proposal.proposalId) },
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Text(
                if (commentsExpanded) {
                    "Tutup diskusi"
                } else {
                    "Buka diskusi${if (comments.isEmpty()) "" else " (${comments.size})"}"
                }
            )
        }
        if (commentsExpanded) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text("Diskusi usulan", style = MaterialTheme.typography.titleSmall)
            when {
                loadingComments -> CircularProgressIndicator(
                    modifier = Modifier.padding(top = 10.dp)
                )
                comments.isEmpty() -> Text(
                    "Belum ada komentar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                else -> comments.forEach { comment ->
                    ProposalCommentRow(comment)
                }
            }
            OutlinedTextField(
                value = commentDraft,
                onValueChange = {
                    onCommentDraftChange(proposal.proposalId, it)
                },
                label = { Text("Tambahkan konteks") },
                supportingText = { Text("${commentDraft.length}/1000") },
                minLines = 2,
                enabled = !postingComment,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Button(
                enabled = isProposalCommentValid(commentDraft) && !postingComment,
                onClick = { onAddComment(proposal.proposalId) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (postingComment) "Mengirim…" else "Kirim komentar")
            }
        }
    }
}

@Composable
private fun ProposalCommentRow(comment: ProposalCommentItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            if (comment.isMine) {
                "${comment.authorDisplayName} · Anda"
            } else {
                comment.authorDisplayName
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(comment.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    "DELETE_PERSON" -> "Permintaan penghapusan person"
    else -> field.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

internal fun invitationStatusLabel(status: String?): String = when (status) {
    "ALL" -> "Semua"
    "ACTIVE" -> "Aktif"
    "ACCEPTED" -> "Diterima"
    "REVOKED" -> "Dicabut"
    "EXPIRED" -> "Kedaluwarsa"
    else -> "Tidak diketahui"
}

internal fun proposalValueLabel(proposal: ProposalItem): String = when (proposal.field) {
    "DELETE_PERSON" ->
        "Person hanya dapat dihapus setelah hubungan dan data terhubung diselesaikan."
    else -> proposal.proposedValue
}

internal fun proposalCurrentValueLabel(proposal: ProposalItem): String = when {
    proposal.field == "DELETE_PERSON" -> "Person masih tersimpan."
    proposal.currentValue.isNullOrBlank() -> "Belum diisi"
    else -> proposal.currentValue
}

internal fun isProposalRejectionReasonValid(reason: String): Boolean =
    reason.trim().length in 1..1000

internal fun isProposalCommentValid(comment: String): Boolean =
    comment.trim().length in 1..1000

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
        value.contains("targetEmail", ignoreCase = true) ||
            value.contains("must be an email", ignoreCase = true) ->
            "Masukkan alamat email penerima yang valid."
        value.contains("Only OWNER", ignoreCase = true) || value.contains("Only ADMIN", ignoreCase = true) ->
            "Hanya pemilik atau pengelola silsilah yang dapat membuat undangan."
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
