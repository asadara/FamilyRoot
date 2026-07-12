package com.example.familytreeplatform.feature.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.familytreeplatform.R

@Composable
fun SpaceSelectionScreen(repository: PersonRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var spaces by remember { mutableStateOf<List<FamilySpace>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() = scope.launch {
        loading = true
        repository.listSpaces().onSuccess { spaces = it }.onFailure { error = it.message }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.choose_family_space), style = MaterialTheme.typography.headlineSmall)
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        spaces.forEach { space ->
            Button(
                onClick = { SessionStore.selectSpace(space.spaceId) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("${space.name} · ${space.role ?: "MEMBER"}") }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.new_family_space)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true
        )
        Button(enabled = !loading && name.isNotBlank(), onClick = {
            scope.launch {
                loading = true
                repository.createSpace(name).onSuccess { SessionStore.selectSpace(it.spaceId) }
                    .onFailure { error = it.message }
                loading = false
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.create_space)) }
        Button(onClick = { SessionStore.clear() }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.sign_out)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
