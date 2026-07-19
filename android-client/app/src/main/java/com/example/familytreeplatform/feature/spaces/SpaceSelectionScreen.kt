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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.R
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.models.InvitationPreview
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.launch

@Composable
fun SpaceSelectionScreen(repository: PersonRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var spaces by remember { mutableStateOf<List<FamilySpace>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var inviteToken by remember { mutableStateOf("") }
    var invitationPreview by remember { mutableStateOf<InvitationPreview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() = scope.launch {
        loading = true
        repository.listSpaces().onSuccess { spaces = it }.onFailure { error = it.message }
        loading = false
    }

    fun previewInvitation() = scope.launch {
        val token = inviteToken.trim()
        if (token.isBlank()) return@launch
        loading = true
        error = null
        invitationPreview = null
        repository.previewInvitation(token)
            .onSuccess { invitationPreview = it }
            .onFailure { error = it.message }
        loading = false
    }

    fun acceptInvitation() = scope.launch {
        val token = inviteToken.trim()
        if (token.isBlank()) return@launch
        loading = true
        error = null
        repository.acceptInvitation(token)
            .onSuccess { SessionStore.selectSpace(it.spaceId, it.name) }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            stringResource(R.string.choose_family_space),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        spaces.forEach { space ->
            Button(
                onClick = { SessionStore.selectSpace(space.spaceId, space.name) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.space_item_format, space.name, space.role ?: "MEMBER")) }
        }

        Text(
            stringResource(R.string.join_with_invitation),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() }
        )
        OutlinedTextField(
            value = inviteToken,
            onValueChange = {
                inviteToken = it
                invitationPreview = null
            },
            label = { Text(stringResource(R.string.invitation_code)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )
        Button(
            enabled = !loading && inviteToken.isNotBlank(),
            onClick = { previewInvitation() },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(stringResource(R.string.preview_invitation)) }
        invitationPreview?.let { preview ->
            Text(
                stringResource(R.string.invitation_preview_format, preview.spaceName, preview.role),
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                enabled = !loading,
                onClick = { acceptInvitation() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.join_family_space)) }
        }

        Text(
            stringResource(R.string.create_new_family_space),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() }
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.new_family_space)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )
        Button(enabled = !loading && name.isNotBlank(), onClick = {
            scope.launch {
                loading = true
                repository.createSpace(name).onSuccess { SessionStore.selectSpace(it.spaceId, it.name) }
                    .onFailure { error = it.message }
                loading = false
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.create_space)) }
        Button(
            onClick = { scope.launch { repository.logout() } },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(stringResource(R.string.sign_out)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
