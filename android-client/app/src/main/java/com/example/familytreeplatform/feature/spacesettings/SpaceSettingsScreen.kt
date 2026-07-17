package com.example.familytreeplatform.feature.spacesettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(R.string.space_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() }
        )
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
