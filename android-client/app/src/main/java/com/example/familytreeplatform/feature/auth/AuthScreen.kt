package com.example.familytreeplatform.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.familytreeplatform.R

@Composable
fun AuthScreen(repository: PersonRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var registerMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (registerMode) stringResource(R.string.create_account) else stringResource(R.string.sign_in), style = MaterialTheme.typography.headlineSmall)
        if (registerMode) OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.display_name)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )
        Button(
            enabled = !loading && email.isNotBlank() && password.isNotBlank() && (!registerMode || displayName.isNotBlank()),
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    val result = if (registerMode) repository.register(email, displayName, password)
                    else repository.login(email, password)
                    result.onSuccess { SessionStore.saveSession(it.accessToken, it.user.userId) }
                        .onFailure { error = it.message }
                    loading = false
                }
            },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text(if (loading) stringResource(R.string.please_wait) else if (registerMode) stringResource(R.string.register) else stringResource(R.string.sign_in)) }
        Button(onClick = { registerMode = !registerMode }, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (registerMode) stringResource(R.string.already_have_account) else stringResource(R.string.create_account))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
