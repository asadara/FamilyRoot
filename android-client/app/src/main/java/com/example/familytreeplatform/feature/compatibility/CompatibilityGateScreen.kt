package com.example.familytreeplatform.feature.compatibility

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.models.AppCompatibilityState
import com.example.familytreeplatform.models.CompatibilityGateStatus
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo

@Composable
fun CompatibilityGateScreen(
    state: AppCompatibilityState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 })
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TredhahLogo(Modifier.size(112.dp))
                Spacer(Modifier.height(18.dp))
                Text(
                    TredhahBrand.NAME,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    compatibilityTitle(state),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 22.dp)
                )
                Text(
                    compatibilityMessage(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )

                if (state.status == CompatibilityGateStatus.CHECKING) {
                    CircularProgressIndicator(Modifier.padding(top = 28.dp).size(32.dp))
                } else {
                    HorizontalDivider(Modifier.padding(vertical = 24.dp))
                    Text(
                        "Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                            BuildConfig.RELEASE_CHANNEL.lowercase().replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.response?.let { response ->
                        Text(
                            "Didukung mulai build ${response.minimumSupportedVersionCode}; " +
                                "build terbaru ${response.latestVersionCode}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (state.usingCachedPolicy) {
                        Text(
                            "Status sementara memakai pemeriksaan tersimpan.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    CompatibilityActions(
                        state = state,
                        onRetry = onRetry,
                        onContinue = onContinue,
                        onOpenUpdate = onOpenUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun CompatibilityActions(
    state: AppCompatibilityState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onOpenUpdate: (String) -> Unit
) {
    val updateUrl = state.response?.updateUrl
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
    ) {
        if (!updateUrl.isNullOrBlank()) {
            Button(
                onClick = { onOpenUpdate(updateUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Perbarui aplikasi")
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Periksa lagi")
            }
        }
        if (
            state.status == CompatibilityGateStatus.UPDATE_AVAILABLE ||
            state.status == CompatibilityGateStatus.UNAVAILABLE
        ) {
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Lanjutkan sementara")
            }
        } else if (!updateUrl.isNullOrBlank()) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Periksa lagi")
            }
        }
    }
}

internal fun compatibilityTitle(state: AppCompatibilityState): String = when (
    state.status
) {
    CompatibilityGateStatus.CHECKING -> "Memeriksa kompatibilitas"
    CompatibilityGateStatus.UPDATE_AVAILABLE -> when (state.response?.status) {
        "APP_TOO_NEW" -> "Build aplikasi belum terdaftar"
        "APP_TOO_OLD" -> "Peringatan versi aplikasi"
        "API_CONTRACT_MISMATCH" -> "Peringatan kompatibilitas layanan"
        else -> "Pembaruan tersedia"
    }
    CompatibilityGateStatus.UNAVAILABLE -> "Versi belum dapat diverifikasi"
    CompatibilityGateStatus.BLOCKED -> when (state.response?.status) {
        "APP_TOO_OLD" -> "Aplikasi perlu diperbarui"
        "APP_TOO_NEW" -> "Layanan belum siap untuk build ini"
        else -> "Versi tidak kompatibel"
    }
    CompatibilityGateStatus.COMPATIBLE -> "Versi kompatibel"
}

internal fun compatibilityMessage(state: AppCompatibilityState): String = when {
    state.status == CompatibilityGateStatus.CHECKING ->
        "Menyesuaikan versi aplikasi dengan layanan FamilyRoot."
    state.status == CompatibilityGateStatus.UNAVAILABLE ->
        "Hubungkan perangkat ke internet lalu periksa kembali. Aplikasi belum dibuka " +
            "agar perubahan keluarga tidak dikirim ke layanan yang belum terverifikasi."
    !state.response?.message.isNullOrBlank() -> state.response?.message.orEmpty()
    else -> "Versi aplikasi ini belum dapat digunakan."
}

internal fun compatibilityRequiresGate(
    state: AppCompatibilityState,
    releaseChannel: String = BuildConfig.RELEASE_CHANNEL
): Boolean {
    val betaChannel = releaseChannel == "DEBUG" || releaseChannel == "PILOT"
    if (betaChannel && state.response?.enforcementEnabled != true) return false
    return when (state.status) {
        CompatibilityGateStatus.COMPATIBLE -> false
        CompatibilityGateStatus.UPDATE_AVAILABLE -> !state.updateWarningAcknowledged
        CompatibilityGateStatus.UNAVAILABLE -> !state.updateWarningAcknowledged
        else -> true
    }
}
