package com.example.familytreeplatform.feature.graph

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.GraphScreen
import com.example.familytreeplatform.GraphExportSnapshot
import com.example.familytreeplatform.R
import com.example.familytreeplatform.export.FamilyGraphExporter
import com.example.familytreeplatform.navigation.GraphShellAction

@Composable
fun TreeGraphScreen(
    viewModel: TreeGraphViewModel,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    canEditRelationships: Boolean = true,
    shellAction: GraphShellAction? = null,
    onShellActionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val centerPersonId = state.centerPersonId
    val context = LocalContext.current
    var resetViewRequest by rememberSaveable { mutableIntStateOf(0) }
    var quickAddRequest by remember { mutableStateOf<GraphQuickAddRequest?>(null) }
    var connectionRequest by remember { mutableStateOf<GraphConnectionRequest?>(null) }
    var integrityDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingIntegrityDeletion by remember {
        mutableStateOf<RelationshipIntegrityConflict?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val integrityConflicts = remember(state.persons, state.relationships) {
        detectRelationshipIntegrityConflicts(state.persons, state.relationships)
    }
    var exportSnapshot by remember(centerPersonId) { mutableStateOf<GraphExportSnapshot?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val pdfWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) runCatching {
            val snapshot = requireNotNull(exportSnapshot) { "Workspace tree is not ready" }
            val bytes = FamilyGraphExporter.renderPdf(snapshot)
            context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }
        }.onSuccess { exportError = null }
            .onFailure { exportError = exportErrorMessage(it.message) }
    }
    val pngWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) runCatching {
            val snapshot = requireNotNull(exportSnapshot) { "Workspace tree is not ready" }
            val bytes = FamilyGraphExporter.renderPng(snapshot)
            context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }
        }.onSuccess { exportError = null }
            .onFailure { exportError = exportErrorMessage(it.message) }
    }

    LaunchedEffect(shellAction) {
        when (shellAction) {
            GraphShellAction.EXPORT_PDF -> pdfWriter.launch("familyroot-tree.pdf")
            GraphShellAction.EXPORT_PNG -> pngWriter.launch("familyroot-tree.png")
            GraphShellAction.RESET_VIEW -> resetViewRequest++
            null -> return@LaunchedEffect
        }
        onShellActionConsumed()
    }

    LaunchedEffect(state.quickAddCompletedPersonId) {
        if (state.quickAddCompletedPersonId != null) {
            quickAddRequest = null
            viewModel.clearQuickAddFeedback()
        }
    }

    LaunchedEffect(
        state.connectionMessage,
        state.connectionError,
        state.integrityMessage,
        state.integrityError
    ) {
        val feedback = state.integrityError ?: state.integrityMessage
            ?: state.connectionError ?: state.connectionMessage
        if (!feedback.isNullOrBlank()) snackbarHostState.showSnackbar(feedback)
    }

    LaunchedEffect(integrityConflicts) {
        if (integrityConflicts.isEmpty()) {
            integrityDialogVisible = false
            pendingIntegrityDeletion = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (centerPersonId == null) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.no_family_members),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (integrityConflicts.isNotEmpty()) {
                    RelationshipIntegrityBanner(
                        conflictCount = integrityConflicts.size,
                        onReview = { integrityDialogVisible = true }
                    )
                }
                GraphScreen(
                    centerPersonId = centerPersonId,
                    selectedPersonId = state.selectedPersonId,
                    inspectedPersonId = state.inspectedPersonId,
                    persons = state.persons,
                    relations = state.relations,
                    allRelationships = state.relationships,
                    explorationHistory = state.explorationHistory,
                    explorationBreadcrumbVisible = state.explorationBreadcrumbVisible,
                    relationshipPath = state.relationshipPath,
                    showRelationshipPathInGraph = state.showRelationshipPathInGraph,
                    resetViewRequest = resetViewRequest,
                    onSelectPerson = viewModel::selectPerson,
                    onInspectPerson = viewModel::inspectPerson,
                    canEditRelationships = canEditRelationships,
                    onQuickAddRequest = { request ->
                        viewModel.beginQuickAdd()
                        quickAddRequest = request
                    },
                    onConnectPersons = { sourceId, targetId ->
                        if (!state.connectionSaving) {
                            connectionRequest = GraphConnectionRequest(sourceId, targetId)
                        }
                    },
                    onClearSelection = viewModel::clearSelection,
                    onOpenPerson = onOpenPerson,
                    onShowRelationshipPath = viewModel::showRelationshipPathInGraph,
                    onHideExplorationBreadcrumb = viewModel::hideExplorationBreadcrumb,
                    onExportSnapshotChanged = { exportSnapshot = it },
                    onBack = onBack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        quickAddRequest?.let { request ->
            GraphQuickAddDialog(
                request = request,
                saving = state.quickAddSaving,
                error = state.quickAddError,
                onDismiss = {
                    quickAddRequest = null
                    viewModel.clearQuickAddFeedback()
                },
                onOpenProfile = {
                    quickAddRequest = null
                    viewModel.clearQuickAddFeedback()
                    onOpenPerson(request.anchorPersonId)
                },
                onSave = { firstName, nickName, gender, startDate ->
                    viewModel.quickAddRelative(
                        request,
                        firstName,
                        nickName,
                        gender,
                        startDate
                    )
                }
            )
        }
        connectionRequest?.let { request ->
            val sourceName = state.persons.firstOrNull { it.personId == request.sourcePersonId }
                ?.fullName ?: "Person asal"
            val targetName = state.persons.firstOrNull { it.personId == request.targetPersonId }
                ?.fullName ?: "Person tujuan"
            AlertDialog(
                onDismissRequest = { connectionRequest = null },
                title = { Text("Tentukan hubungan keluarga") },
                text = {
                    Column {
                        Text("Pilih posisi $targetName terhadap $sourceName.")
                        TextButton(
                            onClick = {
                                connectionRequest = null
                                viewModel.connectExistingPeople(
                                    request.sourcePersonId,
                                    request.targetPersonId,
                                    ExistingRelationKind.TARGET_PARENT,
                                    "BIOLOGICAL"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$targetName adalah orang tua biologis") }
                        TextButton(
                            onClick = {
                                connectionRequest = null
                                viewModel.connectExistingPeople(
                                    request.sourcePersonId,
                                    request.targetPersonId,
                                    ExistingRelationKind.TARGET_CHILD,
                                    "BIOLOGICAL"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$targetName adalah anak biologis") }
                        TextButton(
                            onClick = {
                                connectionRequest = null
                                viewModel.connectExistingPeople(
                                    request.sourcePersonId,
                                    request.targetPersonId,
                                    ExistingRelationKind.PARTNER,
                                    "MARRIED"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$targetName adalah pasangan") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { connectionRequest = null }) { Text("Batal") }
                }
            )
        }
        if (integrityDialogVisible && integrityConflicts.isNotEmpty()) {
            RelationshipIntegrityDialog(
                conflicts = integrityConflicts,
                canEditRelationships = canEditRelationships,
                savingRelationshipId = state.integritySavingRelationshipId,
                onDismiss = { integrityDialogVisible = false },
                onDeleteRecommended = { pendingIntegrityDeletion = it }
            )
        }
        pendingIntegrityDeletion?.let { conflict ->
            AlertDialog(
                onDismissRequest = {
                    if (state.integritySavingRelationshipId == null) {
                        pendingIntegrityDeletion = null
                    }
                },
                title = { Text("Hapus hubungan yang direkomendasikan?") },
                text = {
                    Text(
                        "${conflict.recommendation} Person tetap tersimpan; hanya hubungan ini yang dihapus."
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = state.integritySavingRelationshipId == null,
                        onClick = {
                            viewModel.deleteRecommendedRelationship(
                                conflict.recommendedRelationshipId
                            )
                            pendingIntegrityDeletion = null
                        }
                    ) { Text("Hapus hubungan") }
                },
                dismissButton = {
                    TextButton(
                        enabled = state.integritySavingRelationshipId == null,
                        onClick = { pendingIntegrityDeletion = null }
                    ) { Text("Batal") }
                }
            )
        }
        state.error?.let { error ->
            Text(
                graphErrorMessage(error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
        exportError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun RelationshipIntegrityBanner(
    conflictCount: Int,
    onReview: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$conflictCount hubungan perlu diperiksa",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Sistem menemukan hubungan ganda yang membuat pohon rancu.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onReview) { Text("Tinjau") }
        }
    }
}

@Composable
private fun RelationshipIntegrityDialog(
    conflicts: List<RelationshipIntegrityConflict>,
    canEditRelationships: Boolean,
    savingRelationshipId: String?,
    onDismiss: () -> Unit,
    onDeleteRecommended: (RelationshipIntegrityConflict) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Periksa hubungan keluarga") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tidak ada hubungan yang dihapus otomatis. Periksa rekomendasi sebelum melanjutkan.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                conflicts.forEachIndexed { index, conflict ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Text(conflict.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(conflict.detail, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        conflict.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (canEditRelationships) {
                        TextButton(
                            enabled = savingRelationshipId == null,
                            onClick = { onDeleteRecommended(conflict) }
                        ) {
                            Text(
                                if (savingRelationshipId == conflict.recommendedRelationshipId) {
                                    "Menghapus…"
                                } else {
                                    "Hapus yang direkomendasikan"
                                }
                            )
                        }
                    } else {
                        Text(
                            "Minta Kontributor atau Pengelola untuk memperbaiki hubungan ini.",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

private data class GraphConnectionRequest(
    val sourcePersonId: String,
    val targetPersonId: String
)

internal fun exportErrorMessage(message: String?): String = when {
    message.orEmpty().contains("not ready", ignoreCase = true) ->
        "Pohon masih disiapkan. Tunggu sebentar lalu ulangi ekspor."
    message.orEmpty().contains("permission", ignoreCase = true) ->
        "Lokasi penyimpanan tidak dapat diakses. Pilih lokasi lain."
    else -> "Ekspor belum berhasil. Pilih lokasi penyimpanan lalu coba kembali."
}

internal fun graphErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return when {
        value.contains("401") || value.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Sesi masuk sudah berakhir. Silakan masuk kembali."
        value.contains("403") || value.contains("FORBIDDEN", ignoreCase = true) ->
            "Akun Anda tidak memiliki izin untuk membuka data ini."
        value.contains("500") || value.contains("INTERNAL_ERROR", ignoreCase = true) ||
            value.contains("502") || value.contains("503") || value.contains("504") ->
            "Server sedang bermasalah. Coba lagi beberapa saat."
        value.contains("connect", ignoreCase = true) || value.contains("timeout", ignoreCase = true) ||
            value.contains("failed", ignoreCase = true) ->
            "Server belum dapat dijangkau. Periksa koneksi lalu coba kembali."
        else -> "Pohon belum dapat dimuat. Coba perbarui kembali."
    }
}
