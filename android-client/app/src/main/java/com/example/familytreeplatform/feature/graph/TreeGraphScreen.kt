package com.example.familytreeplatform.feature.graph

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.GraphScreen
import com.example.familytreeplatform.R

@Composable
fun TreeGraphScreen(
    viewModel: TreeGraphViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val centerPersonId = state.centerPersonId

    Column(modifier = modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        if (centerPersonId == null) {
            Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.back))
            }
            Text(stringResource(R.string.no_family_members), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            GraphScreen(
                centerPersonId = centerPersonId,
                persons = state.persons,
                relations = state.relations,
                allRelationships = state.relationships,
                onSelectPerson = viewModel::selectPerson,
                onBack = onBack
            )
        }
    }
}
