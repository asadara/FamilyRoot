package com.example.familytreeplatform.feature.spacesettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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
    }
}
