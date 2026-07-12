package com.example.familytreeplatform.feature.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.R

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onPersonClick: (String) -> Unit,
    onActivityClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var firstName by remember { mutableStateOf("") }
    var nickName by remember { mutableStateOf("") }

    val form: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.add_person),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text(stringResource(R.string.first_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = nickName,
                onValueChange = { nickName = it },
                label = { Text(stringResource(R.string.nickname)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            Button(
                enabled = firstName.isNotBlank() && nickName.isNotBlank() && !state.creating,
                onClick = {
                    viewModel.create(firstName, nickName, "UNKNOWN")
                    firstName = ""
                    nickName = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (state.creating) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        }
    }

    val list: @Composable (Modifier) -> Unit = { listModifier ->
        Column(modifier = listModifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onActivityClick) { Text(stringResource(R.string.activity)) }
                Button(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.refresh)) }
                Button(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            }
            Text(
                text = stringResource(R.string.family_members),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() }
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            if (state.refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            if (state.offline && state.people.isNotEmpty()) {
                Text(stringResource(R.string.offline_saved_data), color = MaterialTheme.colorScheme.tertiary)
            }
            state.error?.takeIf { state.people.isEmpty() }?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (!state.refreshing && state.filteredPeople.isEmpty()) {
                Text(stringResource(R.string.no_family_members), modifier = Modifier.padding(top = 16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(state.filteredPeople, key = { it.personId }) { person ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .semantics { contentDescription = person.fullName }
                            .clickable { onPersonClick(person.personId) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(person.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.person_summary_format, person.lifeStatus, person.gender ?: "UNKNOWN"))
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(0.38f)) { form() }
                list(Modifier.weight(0.62f))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                form()
                list(Modifier.weight(1f))
            }
        }
    }
}
