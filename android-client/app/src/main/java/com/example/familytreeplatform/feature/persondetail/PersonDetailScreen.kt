package com.example.familytreeplatform.feature.persondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.R

@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sourceTitle by remember { mutableStateOf("") }
    var sourceNote by remember { mutableStateOf("") }
    var mediaLabel by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf("") }
    var proposalNotes by remember { mutableStateOf("") }
    var proposalReason by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        val item = state.person
        if (item == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else {
            Text(
                text = item.fullName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
            Text(stringResource(R.string.status_format, item.lifeStatus), modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.gender_format, item.gender ?: "UNKNOWN"))
            item.birthDate?.let { Text(stringResource(R.string.born_format, it)) }
            item.deceasedAt?.let { Text(stringResource(R.string.died_format, it)) }
            Text(
                stringResource(R.string.life_status),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("ALIVE", "DECEASED", "UNKNOWN").forEach { status ->
                    Button(
                        enabled = !state.updating && item.lifeStatus != status,
                        onClick = { viewModel.updateLifeStatus(status) }
                    ) { Text(status) }
                }
            }
            Button(
                enabled = !state.claiming,
                onClick = { viewModel.claimAsMe() },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(if (state.claiming) stringResource(R.string.claiming_profile) else stringResource(R.string.claim_as_me))
            }
            state.claim?.let { claim ->
                Text(
                    stringResource(R.string.claim_created_format, claim.status),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                stringResource(R.string.sources),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
            OutlinedTextField(
                value = sourceTitle,
                onValueChange = { sourceTitle = it },
                label = { Text(stringResource(R.string.source_title)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = sourceNote,
                onValueChange = { sourceNote = it },
                label = { Text(stringResource(R.string.source_note)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                enabled = sourceTitle.isNotBlank() && !state.updating,
                onClick = {
                    viewModel.addSource(sourceTitle, sourceNote)
                    sourceTitle = ""
                    sourceNote = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.add_source)) }
            state.sources.forEach { source ->
                Text("${source.title} · ${source.type}", modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                stringResource(R.string.media),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
            OutlinedTextField(
                value = mediaLabel,
                onValueChange = { mediaLabel = it },
                label = { Text(stringResource(R.string.media_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = mediaUri,
                onValueChange = { mediaUri = it },
                label = { Text(stringResource(R.string.media_uri)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            Button(
                enabled = mediaLabel.isNotBlank() && mediaUri.isNotBlank() && !state.updating,
                onClick = {
                    viewModel.addMedia(mediaLabel, mediaUri)
                    mediaLabel = ""
                    mediaUri = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.add_media)) }
            state.media.forEach { media ->
                Text("${media.label} · ${media.kind}", modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                stringResource(R.string.propose_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
            OutlinedTextField(
                value = proposalNotes,
                onValueChange = { proposalNotes = it },
                label = { Text(stringResource(R.string.proposed_notes)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = proposalReason,
                onValueChange = { proposalReason = it },
                label = { Text(stringResource(R.string.reason)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                enabled = proposalNotes.isNotBlank() && !state.updating,
                onClick = {
                    viewModel.proposeNotes(proposalNotes, proposalReason)
                    proposalNotes = ""
                    proposalReason = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.submit_proposal)) }
            if (state.loadingRelations) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            state.relations?.let { relations ->
                Text(
                    stringResource(R.string.relations),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(stringResource(R.string.parents_count_format, relations.parents.size))
                Text(stringResource(R.string.children_count_format, relations.children.size))
                Text(stringResource(R.string.spouses_count_format, relations.spouses.size))
            }
            Text(
                stringResource(R.string.connect_relation),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
            state.people.filter { it.personId != item.personId }.forEach { target ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(target.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.person_summary_format, target.lifeStatus, target.gender ?: "UNKNOWN"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(enabled = !state.updating, onClick = { viewModel.addParent(target.personId) }) {
                                Text(stringResource(R.string.add_as_parent))
                            }
                            Button(enabled = !state.updating, onClick = { viewModel.addChild(target.personId) }) {
                                Text(stringResource(R.string.add_as_child))
                            }
                        }
                        Button(
                            enabled = !state.updating,
                            onClick = { viewModel.addSpouse(target.personId) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.add_as_spouse))
                        }
                        Button(
                            enabled = !state.updating,
                            onClick = { viewModel.findPathTo(target.personId) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.find_path))
                        }
                    }
                }
            }
            state.path?.let { path ->
                Text(
                    if (path.found) {
                        stringResource(
                            R.string.path_result_format,
                            path.people.joinToString(" -> ") { it.fullName }
                        )
                    } else {
                        stringResource(R.string.no_path_found)
                    },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
