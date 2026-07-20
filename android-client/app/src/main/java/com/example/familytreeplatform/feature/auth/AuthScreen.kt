package com.example.familytreeplatform.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo

@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 720.dp
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            item {
                if (wide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                    ) {
                        WelcomePanel(Modifier.weight(0.9f))
                        AuthForm(state, viewModel, Modifier.weight(1.1f))
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
                    ) {
                        CompactWordmark()
                        AuthForm(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(28.dp)) {
            TredhahLogo(Modifier.size(116.dp))
            Text(TredhahBrand.NAME, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                TredhahBrand.TAGLINE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                "Satu ruang privat untuk menyusun silsilah, menjaga cerita, dan merawat informasi bersama keluarga.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
            )
            WelcomePoint("Pohon keluarga yang mudah dijelajahi")
            WelcomePoint("Kontribusi tercatat dan dapat ditinjau")
            WelcomePoint("Informasi keluarga tetap dalam ruang privat")
        }
    }
}

@Composable
private fun WelcomePoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.secondary, shape = CircleShape) {
            Text("✓", color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun CompactWordmark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TredhahLogo(Modifier.size(96.dp))
        Text(TredhahBrand.NAME, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text(TredhahBrand.TAGLINE, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuthForm(state: AuthUiState, viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                if (state.mode == AuthMode.SIGN_IN) "Selamat datang kembali" else "Mulai silsilah keluarga Anda",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (state.mode == AuthMode.SIGN_IN) "Masuk untuk melanjutkan penjelajahan keluarga."
                else "Buat akun pribadi sebelum bergabung atau membuat silsilah.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            AuthModeSelector(state.mode, viewModel::setMode)
            if (state.mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::setDisplayName,
                    label = { Text("Nama akun") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Kata sandi") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            )
            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(authErrorMessage(it), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
            Button(
                enabled = state.canSubmit,
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("authSubmit")
            ) {
                if (state.loading) CircularProgressIndicator()
                else Text(if (state.mode == AuthMode.SIGN_IN) "Masuk" else "Buat akun")
            }
            Text(
                "Dengan melanjutkan, Anda bertanggung jawab atas kontribusi dan validasi informasi yang dibagikan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (BuildConfig.DEBUG) DemoAccounts(state.loading, viewModel::signInDemo)
        }
    }
}

@Composable
private fun AuthModeSelector(mode: AuthMode, onMode: (AuthMode) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
    ) {
        Row(Modifier.padding(4.dp)) {
            AuthModeButton("Masuk", mode == AuthMode.SIGN_IN, { onMode(AuthMode.SIGN_IN) }, Modifier.weight(1f))
            AuthModeButton("Buat akun", mode == AuthMode.REGISTER, { onMode(AuthMode.REGISTER) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AuthModeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else TextButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun DemoAccounts(loading: Boolean, onDemo: (String) -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text("Akun demo pengembangan", style = MaterialTheme.typography.titleMedium)
    Text("Pilih peran untuk masuk tanpa mengetik.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DemoButton("Ayah", "ayah@example.test", loading, onDemo, Modifier.weight(1f))
            DemoButton("Ibu", "ibu@example.test", loading, onDemo, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DemoButton("Anak", "anak@example.test", loading, onDemo, Modifier.weight(1f))
            DemoButton("Kakek", "kakek@example.test", loading, onDemo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DemoButton(label: String, email: String, loading: Boolean, onDemo: (String) -> Unit, modifier: Modifier) {
    OutlinedButton(enabled = !loading, onClick = { onDemo(email) }, modifier = modifier) { Text(label) }
}

internal fun authErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return when {
        value.contains("401") || value.contains("credential", ignoreCase = true) || value.contains("password", ignoreCase = true) ->
            "Email atau kata sandi tidak sesuai."
        value.contains("409") || value.contains("already", ignoreCase = true) ->
            "Email tersebut sudah digunakan. Silakan masuk."
        value.contains("connect", ignoreCase = true) || value.contains("failed", ignoreCase = true) ||
            value.contains("127.0.0.1") || value.contains("localhost") ->
            "Server belum dapat dijangkau. Periksa koneksi lalu coba kembali."
        else -> "Proses autentikasi belum berhasil. Coba kembali."
    }
}
