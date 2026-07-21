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
    val pdfWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) runCatching {
            val bytes = FamilyGraphExporter.renderPdf(state.persons, state.relationships)
            context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }
        }
    }
    val pngWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) runCatching {
            val bytes = FamilyGraphExporter.renderPng(state.persons, state.relationships)
            context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }
        }
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
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}
