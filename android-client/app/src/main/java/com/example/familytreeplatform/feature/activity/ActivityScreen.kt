package com.example.familytreeplatform.feature.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            text = stringResource(R.string.activity),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() }
        )
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.logs, key = { it.changeId }) { log ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.activity_item_format, log.operation, log.entityType))
                    log.note?.let { Text(it) }
                }
            }
        }
    }
}
