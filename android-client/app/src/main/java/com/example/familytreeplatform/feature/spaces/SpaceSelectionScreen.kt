package com.example.familytreeplatform.feature.spaces

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo

@Composable
fun SpaceSelectionScreen(viewModel: SpaceSelectionViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var joinOpen by rememberSaveable { mutableStateOf(state.spaces.isEmpty()) }
    var createOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 720.dp
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 16.dp, vertical = 20.dp),
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            item {
                SpaceSelectionHeader(onSignOut = viewModel::signOut, Modifier.fillMaxWidth().widthIn(max = 980.dp))
            }
            item {
                SpaceSelectionHero(state.spaces.size, Modifier.fillMaxWidth().widthIn(max = 980.dp))
            }
            state.error?.let { error ->
                item { SpaceError(spaceSelectionErrorMessage(error), Modifier.fillMaxWidth().widthIn(max = 980.dp)) }
            }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Silsilah Anda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Text("Pilih silsilah untuk melanjutkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!state.loadingSpaces) TextButton(onClick = viewModel::refresh) { Text("Perbarui") }
                    }
                    if (state.loadingSpaces) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(18.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Memuat silsilah…")
                            }
                        }
                    } else if (state.spaces.isEmpty()) {
                        EmptySpaces()
                    }
                }
            }
            items(state.spaces, key = { it.spaceId }) { space ->
                FamilySpaceCard(space, { viewModel.selectSpace(space) }, Modifier.fillMaxWidth().widthIn(max = 980.dp))
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                    if (maxWidth >= 720.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                            JoinFamilyCard(state, joinOpen, { joinOpen = !joinOpen }, viewModel, Modifier.weight(1f))
                            CreateFamilyCard(state, createOpen, { createOpen = !createOpen }, viewModel, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            JoinFamilyCard(state, joinOpen, { joinOpen = !joinOpen }, viewModel)
                            CreateFamilyCard(state, createOpen, { createOpen = !createOpen }, viewModel)
                        }
                    }
                }
            }
            item {
                Text(
                    "Setiap silsilah bersifat privat. Akses hanya diberikan melalui keanggotaan atau undangan yang disetujui.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SpaceSelectionHeader(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        TredhahLogo(Modifier.size(50.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(TredhahBrand.NAME, style = MaterialTheme.typography.titleMedium)
            Text(TredhahBrand.TAGLINE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSignOut) { Text("Keluar") }
    }
}

@Composable
private fun SpaceSelectionHero(spaceCount: Int, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Silsilah mana yang ingin Anda buka?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                if (spaceCount > 0) "Anda memiliki akses ke $spaceCount silsilah. Setiap silsilah memiliki anggota dan pengaturan akses tersendiri."
                else "Bergabunglah melalui undangan keluarga atau buat silsilah baru untuk memulai.",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun FamilySpaceCard(space: FamilySpace, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                Text(familySpaceInitials(space.name), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.padding(13.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(space.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(familySpaceRoleLabel(space.role), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptySpaces() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
            Text("Belum ada silsilah", style = MaterialTheme.typography.titleMedium)
            Text("Gunakan undangan atau buat silsilah baru di bawah.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JoinFamilyCard(
    state: SpaceSelectionUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    viewModel: SpaceSelectionViewModel,
    modifier: Modifier = Modifier
) {
    SpaceActionCard(
        title = "Gabung dengan undangan",
        subtitle = "Masukkan kode yang diberikan pengelola keluarga.",
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = state.invitationCode,
            onValueChange = viewModel::setInvitationCode,
            label = { Text("Kode undangan") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            enabled = !state.processing && state.invitationCode.isNotBlank(),
            onClick = viewModel::previewInvitation,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text(if (state.processing) "Memeriksa…" else "Periksa undangan") }
        state.invitationPreview?.let { preview ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(preview.spaceName, style = MaterialTheme.typography.titleMedium)
                    Text("Akses sebagai ${familySpaceRoleLabel(preview.role)}")
                    Text("Berlaku hingga ${preview.expiresAt}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = !state.processing,
                        onClick = viewModel::acceptInvitation,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) { Text("Terima dan bergabung") }
                }
            }
        }
    }
}

@Composable
private fun CreateFamilyCard(
    state: SpaceSelectionUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    viewModel: SpaceSelectionViewModel,
    modifier: Modifier = Modifier
) {
    SpaceActionCard(
        title = "Buat silsilah baru",
        subtitle = "Mulai silsilah privat yang dapat dirawat bersama.",
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = state.newSpaceName,
            onValueChange = viewModel::setNewSpaceName,
            label = { Text("Nama silsilah") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            enabled = !state.processing && state.newSpaceName.isNotBlank(),
            onClick = viewModel::createSpace,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text(if (state.processing) "Membuat silsilah…" else "Buat silsilah") }
    }
}

@Composable
private fun SpaceActionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggle) { Text(if (expanded) "Tutup" else "Buka") }
            }
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun SpaceError(message: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
    }
}

internal fun familySpaceRoleLabel(role: String?): String = when (role) {
    "OWNER" -> "Pemilik silsilah"
    "ADMIN" -> "Pengelola"
    "EDITOR" -> "Kontributor"
    "VIEWER" -> "Pembaca"
    else -> "Anggota keluarga"
}

internal fun familySpaceInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "TR" }

internal fun spaceSelectionErrorMessage(message: String?): String {
    val value = message.orEmpty()
    return when {
        value.contains("invite", ignoreCase = true) || value.contains("token", ignoreCase = true) ->
            "Kode undangan tidak valid atau sudah tidak berlaku."
        value.contains("connect", ignoreCase = true) || value.contains("failed", ignoreCase = true) ||
            value.contains("127.0.0.1") || value.contains("localhost") ->
            "Silsilah belum dapat dimuat. Periksa koneksi lalu coba kembali."
        else -> "Tindakan belum dapat diselesaikan. Coba kembali."
    }
}
