package com.example.familytreeplatform.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.models.UserNotificationItem
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.ProfilePhotoItem
import com.example.familytreeplatform.ui.ProfilePhotoAvatar

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    displayName: String,
    email: String?,
    spaceName: String,
    pendingSyncCount: Int,
    onOpenSelfProfile: (String) -> Unit,
    onOpenSpaceSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profilePhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::uploadProfilePhoto)
    }
    if (state.showDeleteConfirmation) {
        val impact = state.deletionImpact
        AlertDialog(
            onDismissRequest = viewModel::cancelAccountDeletion,
            title = { Text("Hapus akun TRÃªdhAH?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Akun, sesi, akses silsilah, undangan aktif, dan klaim identitas akan diputus. " +
                            "Profil Person serta sejarah keluarga tidak ikut dihapus."
                    )
                    Text(
                        "Dampak: ${impact?.membershipCount ?: 0} membership, " +
                            "${impact?.claimCount ?: 0} klaim, " +
                            "${impact?.activeSessionCount ?: 0} sesi aktif."
                    )
                    if (!impact?.ownedSpaces.isNullOrEmpty()) {
                        Text(
                            "Anda masih menjadi Pemilik. Pindahkan kepemilikan setiap silsilah sebelum menghapus akun.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.pendingMutationCount > 0) {
                        Text(
                            "${state.pendingMutationCount} perubahan lokal belum tersinkron dan harus diselesaikan terlebih dahulu.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedTextField(
                        value = state.deleteConfirmation,
                        onValueChange = viewModel::setDeleteConfirmation,
                        label = { Text("Ketik HAPUS AKUN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = impact?.canDeleteAccount == true &&
                        state.pendingMutationCount == 0 &&
                        state.deleteConfirmation == "HAPUS AKUN",
                    onClick = viewModel::confirmAccountDeletion
                ) {
                    Text("Hapus akun permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelAccountDeletion) { Text("Batal") }
            }
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 28.dp else 16.dp)
        ) {
            item { Spacer(Modifier.height(if (wide) 12.dp else 2.dp)) }
            item {
                ProfileHero(
                    displayName = displayName,
                    email = email,
                    claim = state.myClaim,
                    photo = state.profilePhoto,
                    uploadingPhoto = state.uploadingPhoto,
                    onChangePhoto = {
                        profilePhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenSelfProfile = onOpenSelfProfile,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            item {
                FamilySpaceCard(
                    spaceName,
                    pendingSyncCount,
                    onOpenSpaceSettings,
                    Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            item {
                NotificationHistoryCard(
                    notifications = state.notifications,
                    unreadCount = state.unreadNotificationCount,
                    loading = state.loadingNotifications,
                    markingAllRead = state.markingNotificationsRead,
                    onRefresh = viewModel::refreshNotifications,
                    onMarkRead = viewModel::markNotificationRead,
                    onMarkAllRead = viewModel::markAllNotificationsRead,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            state.error?.let { error ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                    ) {
                        Text(
                            profileErrorMessage(error),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            state.message?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                    ) {
                        Text(message, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item {
                AccountLifecycleCard(
                    loading = state.loadingDeletionImpact || state.deletingAccount,
                    onOpenSpaceSettings = onOpenSpaceSettings,
                    onDeleteAccount = viewModel::requestAccountDeletion,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            item {
                OutlinedButton(onClick = onSignOut) {
                    Text("Keluar dari akun")
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun NotificationHistoryCard(
    notifications: List<UserNotificationItem>,
    unreadCount: Int,
    loading: Boolean,
    markingAllRead: Boolean,
    onRefresh: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileSectionCard(
        title = "Riwayat notifikasi pribadi",
        subtitle = if (unreadCount > 0) {
            "$unreadCount pemberitahuan belum dibaca"
        } else {
            "Bukti tindakan dan status penyimpanan akun Anda"
        },
        modifier = modifier
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !loading, onClick = onRefresh) {
                Text(if (loading) "Memuat..." else "Muat ulang")
            }
            OutlinedButton(
                enabled = unreadCount > 0 && !markingAllRead,
                onClick = onMarkAllRead
            ) {
                Text(if (markingAllRead) "Menandai..." else "Tandai semua dibaca")
            }
        }
        when {
            loading && notifications.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 14.dp))
            }
            notifications.isEmpty() -> {
                Text(
                    "Belum ada riwayat tindakan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            else -> recentProfileNotifications(notifications).forEach { notification ->
                NotificationHistoryRow(notification, onMarkRead)
            }
        }
    }
}

@Composable
private fun NotificationHistoryRow(
    notification: UserNotificationItem,
    onMarkRead: (String) -> Unit
) {
    val unread = notification.readAt == null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (unread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable(enabled = unread) {
                onMarkRead(notification.notificationId)
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notificationKindLabel(notification.kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (notification.kind) {
                        "ERROR" -> MaterialTheme.colorScheme.error
                        "WARNING" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    notificationTimeLabel(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                notification.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                notification.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (unread) {
                Text(
                    "Ketuk untuk menandai dibaca",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountLifecycleCard(
    loading: Boolean,
    onOpenSpaceSettings: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileSectionCard(
        title = "Area berisiko",
        subtitle = "Tinjau dampak sebelum mengakhiri akses atau akun",
        modifier = modifier
    ) {
        Text(
            "Sebelum menghapus akun, ekspor data yang memang boleh Anda bawa dan pindahkan " +
                "kepemilikan silsilah. Penghapusan akun tidak menghapus Person dari pohon keluarga.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onOpenSpaceSettings,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Tinjau kepemilikan & ekspor")
        }
        OutlinedButton(
            enabled = !loading,
            onClick = onDeleteAccount,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                if (loading) "Memeriksa dampak..." else "Hapus akun",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ProfileHero(
    displayName: String,
    email: String?,
    claim: ClaimReviewItem?,
    photo: ProfilePhotoItem?,
    uploadingPhoto: Boolean,
    onChangePhoto: () -> Unit,
    onOpenSelfProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val branchColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            val avatarSize = if (maxWidth < 430.dp) 76.dp else 88.dp
            Canvas(modifier = Modifier.fillMaxSize()) {
                val start = Offset(size.width * 0.72f, size.height * 0.86f)
                drawLine(branchColor, start, Offset(size.width * 0.86f, size.height * 0.2f), 4f, StrokeCap.Round)
                drawLine(branchColor, Offset(size.width * 0.8f, size.height * 0.5f), Offset(size.width * 0.69f, size.height * 0.3f), 3f, StrokeCap.Round)
                drawLine(branchColor, Offset(size.width * 0.82f, size.height * 0.4f), Offset(size.width * 0.93f, size.height * 0.25f), 3f, StrokeCap.Round)
                listOf(
                    Offset(size.width * 0.86f, size.height * 0.2f),
                    Offset(size.width * 0.69f, size.height * 0.3f),
                    Offset(size.width * 0.93f, size.height * 0.25f)
                ).forEach { drawCircle(branchColor, 13f, it, style = Stroke(4f)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProfilePhotoAvatar(
                    photo = photo,
                    fallbackText = profileInitials(displayName),
                    modifier = Modifier.size(avatarSize)
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(
                        "Profil akun",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    email?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        when (claim?.status) {
                            "VERIFIED" -> "Terhubung ke ${claim.personName ?: "profil diri"}"
                            "PENDING" -> "Klaim profil diri menunggu verifikasi"
                            else -> "Belum terhubung ke profil diri pada silsilah ini"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    claim?.let { currentClaim ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            OutlinedButton(
                                enabled = currentClaim.status == "VERIFIED" && !uploadingPhoto,
                                onClick = onChangePhoto,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (uploadingPhoto) "Mengunggah..." else "Ganti foto profil")
                            }
                            TextButton(
                                onClick = { onOpenSelfProfile(currentClaim.personId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Buka profil keluarga")
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            "●  Sesi aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilySpaceCard(
    spaceName: String,
    pendingSyncCount: Int,
    onOpenSpaceSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileSectionCard("Silsilah aktif", "Silsilah kolaboratif yang sedang dibuka", modifier) {
        Text(spaceName, style = MaterialTheme.typography.titleLarge)
        Text(
            if (pendingSyncCount == 0) "Semua perubahan telah tersinkron" else "$pendingSyncCount perubahan menunggu sinkronisasi",
            style = MaterialTheme.typography.bodyMedium,
            color = if (pendingSyncCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clickable(onClick = onOpenSpaceSettings)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pengaturan silsilah", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Anggota, undangan, klaim, dan ekspor data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            content()
        }
    }
}

internal fun profileInitials(displayName: String): String = displayName
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "FR" }

internal fun notificationKindLabel(kind: String): String = when (kind) {
    "SUCCESS" -> "Berhasil"
    "WARNING" -> "Perlu perhatian"
    "ERROR" -> "Gagal"
    else -> "Informasi"
}

internal fun recentProfileNotifications(
    notifications: List<UserNotificationItem>
): List<UserNotificationItem> = notifications.take(10)

internal fun notificationTimeLabel(createdAt: String): String =
    createdAt
        .replace('T', ' ')
        .take(16)
        .ifBlank { "Baru saja" }

internal fun profileErrorMessage(message: String?): String = when {
    message.orEmpty().contains("ownership", ignoreCase = true) ->
        "Pindahkan kepemilikan seluruh silsilah sebelum menghapus akun."
    message.orEmpty().contains("sinkron", ignoreCase = true) ||
        message.orEmpty().contains("sync", ignoreCase = true) ->
        "Selesaikan perubahan yang belum tersinkron sebelum melanjutkan."
    message.orEmpty().contains("connect", ignoreCase = true) ->
        "Dampak akun belum dapat diperiksa. Periksa koneksi lalu coba kembali."
    else -> "Tindakan akun belum dapat diselesaikan. Coba kembali."
}
