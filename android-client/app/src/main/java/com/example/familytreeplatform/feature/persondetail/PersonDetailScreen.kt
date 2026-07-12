package com.example.familytreeplatform.feature.persondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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
        }
    }
}
