package com.example.familytreeplatform.feature.graph

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    shellAction: GraphShellAction? = null,
    onShellActionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val centerPersonId = state.centerPersonId
    val context = LocalContext.current
    var resetViewRequest by rememberSaveable { mutableIntStateOf(0) }
    var quickAddRequest by remember { mutableStateOf<GraphQuickAddRequest?>(null) }
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
                onQuickAddRequest = { request ->
                    viewModel.beginQuickAdd()
                    quickAddRequest = request
                },
                onClearSelection = viewModel::clearSelection,
                onOpenPerson = onOpenPerson,
                onShowRelationshipPath = viewModel::showRelationshipPathInGraph,
                onHideExplorationBreadcrumb = viewModel::hideExplorationBreadcrumb,
                onExportSnapshotChanged = { exportSnapshot = it },
                onBack = onBack,
            )
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
    }
}

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
