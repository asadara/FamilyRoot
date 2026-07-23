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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.R
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleCredentialClient = remember(context) { GoogleCredentialClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val onGoogleClick: () -> Unit = {
        if (viewModel.beginGoogleSignIn()) {
            coroutineScope.launch {
                viewModel.completeGoogleSignIn(
                    runCatching {
                        googleCredentialClient.getIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    }
                )
            }
        }
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::cancelPendingGoogleSignIn)
    }
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
                        AuthForm(
                            state,
                            viewModel,
                            onGoogleClick = onGoogleClick,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
                    ) {
                        CompactWordmark()
                        AuthForm(
                            state,
                            viewModel,
                            onGoogleClick = onGoogleClick
                        )
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
private fun AuthForm(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onGoogleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            GoogleAuthButton(
                mode = state.mode,
                loading = state.loadingMethod == AuthMethod.GOOGLE,
                enabled = !state.loading && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
                onClick = onGoogleClick
            )
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                Text(
                    "Masuk dengan Google belum dikonfigurasi untuk build ini.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            AuthDivider()
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
                supportingText = {
                    if (state.email.isNotBlank() && !state.emailLooksValid) {
                        Text("Masukkan alamat email yang valid.")
                    }
                },
                isError = state.email.isNotBlank() && !state.emailLooksValid,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Kata sandi") },
                supportingText = { Text("Minimal 10 karakter.") },
                isError = state.password.isNotBlank() && !state.passwordLooksValid,
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
                if (state.loadingMethod == AuthMethod.PASSWORD) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                else Text(if (state.mode == AuthMode.SIGN_IN) "Masuk" else "Buat akun")
            }
            Text(
                if (state.mode == AuthMode.REGISTER) {
                    "Google akan membuat akun baru secara otomatis bila email belum terdaftar."
                } else {
                    "Dengan melanjutkan, Anda bertanggung jawab atas kontribusi dan validasi informasi yang dibagikan."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun GoogleAuthButton(
    mode: AuthMode,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(top = 10.dp)
            .testTag("googleAuthSubmit")
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(R.drawable.google_g),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(if (mode == AuthMode.SIGN_IN) "Masuk dengan Google" else "Daftar dengan Google")
        }
    }
}

@Composable
private fun AuthDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("atau gunakan email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
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

internal fun authErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return when {
        value.contains("GOOGLE_NOT_CONFIGURED", ignoreCase = true) ->
            "Masuk dengan Google belum dikonfigurasi untuk aplikasi ini."
        value.contains("GOOGLE_CREDENTIAL", ignoreCase = true) ->
            "Akun Google belum dapat dibuka. Periksa Google Play Services lalu coba kembali."
        value.contains("Google", ignoreCase = true) &&
            (value.contains("401") || value.contains("UNAUTHENTICATED", ignoreCase = true)) ->
            "Akun Google tidak dapat diverifikasi. Pilih akun lalu coba kembali."
        value.contains("401") || value.contains("UNAUTHENTICATED", ignoreCase = true) ||
            (!value.contains("400") && (value.contains("credential", ignoreCase = true) ||
                value.contains("password", ignoreCase = true))) ->
            "Email atau kata sandi tidak sesuai."
        value.contains("409") || value.contains("CONFLICT", ignoreCase = true) ||
            value.contains("already", ignoreCase = true) ->
            "Email tersebut sudah digunakan. Silakan masuk."
        value.contains("400") || value.contains("VALIDATION_ERROR", ignoreCase = true) ->
            "Periksa nama, email, dan kata sandi lalu coba kembali."
        value.contains("500") || value.contains("INTERNAL_ERROR", ignoreCase = true) ||
            value.contains("502") || value.contains("503") || value.contains("504") ->
            "Server sedang bermasalah. Coba lagi beberapa saat."
        value.contains("connect", ignoreCase = true) || value.contains("failed", ignoreCase = true) ||
            value.contains("127.0.0.1") || value.contains("localhost") ->
            "Server belum dapat dijangkau. Periksa koneksi lalu coba kembali."
        else -> "Proses autentikasi belum berhasil. Coba kembali."
    }
}
