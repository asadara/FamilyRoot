package com.example.familytreeplatform.feature.spacesettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.R

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
            title = { Text("Clear offline data?") },
            text = {
                Text(
                    "This removes this Family Space's cached people and relationships from this device only. " +
                        "Server data and your portable backups are not deleted."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::clearOfflineData) { Text("Clear from device") }
            },
            dismissButton = {
                Button(onClick = viewModel::cancelClearOfflineData) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(R.string.space_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() }
        )
        Text(
            "Data portability",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() }
        )
        Text("Import and restore are accepted only when this Family Space has no people.")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Button(enabled = !state.transferringData, onClick = viewModel::prepareGedcomExport) {
                Text("Export GEDCOM")
            }
            Button(enabled = !state.transferringData, onClick = viewModel::prepareBackupExport) {
                Text("Create backup")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Button(
                enabled = !state.transferringData,
                onClick = { gedcomReader.launch(arrayOf("text/*", "application/octet-stream")) }
            ) { Text("Import GEDCOM") }
            Button(
                enabled = !state.transferringData,
                onClick = { backupReader.launch(arrayOf("application/json", "text/json")) }
            ) { Text("Restore backup") }
        }
        if (state.transferringData) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        state.transferMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        Text(
            "Privacy on this device",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() }
        )
        Text(
            "Family data is excluded from Android cloud backup. Choose a storage location you " +
                "control for exported files; FamilyRoot exports are not encrypted automatically."
        )
        Button(
            enabled = !state.clearingOfflineData,
            onClick = viewModel::requestClearOfflineData,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (state.clearingOfflineData) "Clearing..." else "Clear offline data")
        }
        state.privacyMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        Text(
            stringResource(R.string.create_invitation),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("VIEWER", "EDITOR", "ADMIN").forEach { role ->
                Button(
                    enabled = state.role != role,
                    onClick = { viewModel.setRole(role) }
                ) { Text(role) }
            }
        }
        OutlinedTextField(
            value = state.expiresInDays,
            onValueChange = viewModel::setExpiresInDays,
            label = { Text(stringResource(R.string.expires_in_days)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )
        Button(
            enabled = !state.creating,
            onClick = { viewModel.createInvitation() },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(stringResource(R.string.generate_invitation_code)) }
        if (state.creating) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        state.invitation?.let { invitation ->
            Text(
                stringResource(R.string.generated_invitation_format, invitation.spaceName, invitation.role),
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(invitation.token, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(invitation.token)) },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.copy_invitation_code)) }
            Text(stringResource(R.string.invitation_expiry_format, invitation.expiresAt), modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            stringResource(R.string.claim_reviews),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.refreshClaims() }) { Text(stringResource(R.string.refresh)) }
            if (state.loadingClaims) CircularProgressIndicator()
        }
        if (!state.loadingClaims && state.claims.isEmpty()) {
            Text(stringResource(R.string.no_claims), modifier = Modifier.padding(top = 8.dp))
        }
        state.claims.forEach { claim ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(claim.personName ?: claim.personId, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.claim_status_format, claim.status))
                    Text(stringResource(R.string.claim_member_role_format, claim.memberRole ?: "UNKNOWN"))
                    Text(stringResource(R.string.claim_user_format, claim.userId.takeLast(8)))
                    if (claim.status == "PENDING") {
                        Button(
                            enabled = state.verifyingClaimId != claim.claimId,
                            onClick = { viewModel.verifyClaim(claim.claimId) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                if (state.verifyingClaimId == claim.claimId) {
                                    stringResource(R.string.verifying)
                                } else {
                                    stringResource(R.string.verify_claim)
                                }
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.proposal_reviews),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.refreshProposals() }) { Text(stringResource(R.string.refresh)) }
            if (state.loadingProposals) CircularProgressIndicator()
        }
        if (!state.loadingProposals && state.proposals.isEmpty()) {
            Text(stringResource(R.string.no_proposals), modifier = Modifier.padding(top = 8.dp))
        }
        state.proposals.forEach { proposal ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${proposal.field}: ${proposal.proposedValue}", style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.claim_status_format, proposal.status))
                    proposal.reason?.let { Text(it) }
                    if (proposal.status == "PENDING") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                enabled = state.reviewingProposalId != proposal.proposalId,
                                onClick = { viewModel.approveProposal(proposal.proposalId) }
                            ) { Text(stringResource(R.string.approve)) }
                            Button(
                                enabled = state.reviewingProposalId != proposal.proposalId,
                                onClick = { viewModel.rejectProposal(proposal.proposalId) }
                            ) { Text(stringResource(R.string.reject)) }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.duplicate_candidates),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.refreshDuplicates() }) { Text(stringResource(R.string.refresh)) }
            if (state.loadingDuplicates) CircularProgressIndicator()
        }
        if (!state.loadingDuplicates && state.duplicates.isEmpty()) {
            Text(stringResource(R.string.no_duplicates), modifier = Modifier.padding(top = 8.dp))
        }
        state.duplicates.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(group.reason, style = MaterialTheme.typography.titleSmall)
                    group.people.forEach { person -> Text(person.fullName) }
                    if (group.people.size >= 2) {
                        Button(
                            enabled = !state.merging,
                            onClick = {
                                viewModel.mergeDuplicate(
                                    sourcePersonId = group.people[1].personId,
                                    targetPersonId = group.people[0].personId
                                )
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(stringResource(R.string.merge_into_first)) }
                    }
                }
            }
        }
    }
}
