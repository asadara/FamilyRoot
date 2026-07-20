package com.example.familytreeplatform.feature.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    displayName: String,
    email: String?,
    userId: String?,
    spaceName: String,
    pendingSyncCount: Int,
    onOpenSpaceSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                )
            }
            item {
                if (wide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                    ) {
                        IdentityCard(displayName, email, userId, Modifier.weight(1f))
                        FamilySpaceCard(
                            spaceName,
                            pendingSyncCount,
                            onOpenSpaceSettings,
                            Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)
                    ) {
                        IdentityCard(displayName, email, userId, Modifier.fillMaxWidth())
                        FamilySpaceCard(
                            spaceName,
                            pendingSyncCount,
                            onOpenSpaceSettings,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                PrivacyCard(Modifier.fillMaxWidth().widthIn(max = 980.dp))
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
private fun ProfileHero(displayName: String, email: String?, modifier: Modifier = Modifier) {
    val branchColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        Box(
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = profileInitials(displayName),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 18.dp).weight(1f)) {
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
private fun IdentityCard(
    displayName: String,
    email: String?,
    userId: String?,
    modifier: Modifier = Modifier
) {
    ProfileSectionCard("Identitas akun", "Digunakan saat masuk dan berkolaborasi", modifier) {
        ProfileValue("Nama tampilan", displayName)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        ProfileValue("Email", email?.takeIf(String::isNotBlank) ?: "Belum tersedia")
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        ProfileValue("ID akun", userId?.takeLast(12)?.let { "•••• $it" } ?: "Belum tersedia")
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
private fun PrivacyCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("i", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text("Akun bukan profil person", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Halaman ini hanya menampilkan identitas akun TRêdhAH. Nama, umur, cerita, dan hubungan keluarga tetap dikelola pada profil anggota di pohon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

@Composable
private fun ProfileValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

internal fun profileInitials(displayName: String): String = displayName
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "FR" }
