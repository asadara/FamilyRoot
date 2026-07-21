package com.example.familytreeplatform.feature.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo

private const val COPYRIGHT = "\u00a9 sadar@studio 2026"

internal fun applicationVersionLabel(versionName: String): String {
    val normalized = versionName.trim().ifBlank { "0.1.0-beta" }
    return if (normalized.contains("beta", ignoreCase = true)) {
        val number = normalized
            .replace(Regex("(?i)[._-]?beta"), "")
            .trim('-', '.', '_', ' ')
            .ifBlank { "0.1.0" }
        "Beta $number"
    } else {
        normalized
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SupportPage(
        title = "Tentang aplikasi",
        onBack = onBack,
        modifier = modifier
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                TredhahLogo(Modifier.height(104.dp))
                Text(
                    TredhahBrand.NAME,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Merangkai jejak, menyatukan trah",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            SupportSection(
                title = "Tentang TR\u00eadhAH",
                body = "TR\u00eadhAH adalah aplikasi silsilah keluarga kolaboratif untuk " +
                    "mencatat hubungan, menjaga cerita keluarga, dan menjelajahi hubungan " +
                    "antargenerasi dalam satu workspace. Huruf kapitalnya membentuk TRAH, " +
                    "sedangkan kata tr\u00eadh merujuk pada pohon silsilah dalam bahasa Jawa."
            )
        }
        item {
            SupportSection(
                title = "Privasi dan kolaborasi",
                body = "Setiap silsilah merupakan ruang privat. Akses pembaca, kontributor, " +
                    "dan pengelola mengikuti undangan serta peran yang diberikan. Hubungan " +
                    "keluarga tidak otomatis memberikan akses ke silsilah lain."
            )
        }
        item {
            SupportSection(
                title = "Versi aplikasi",
                body = applicationVersionLabel(BuildConfig.VERSION_NAME) +
                    " \u2022 Tahap pengujian pengguna. Fitur dan tampilan masih dapat disempurnakan."
            )
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        "Memulai" to
            "Masuk dengan akun Anda, pilih silsilah, lalu buka Pohon. Status Sinkron berarti " +
            "data perangkat telah terhubung dengan server.",
        "Menjelajahi pohon" to
            "Geser area kosong untuk berpindah, gunakan cubit dua jari untuk memperbesar atau " +
            "memperkecil, dan gunakan Atur ulang graph bila ingin kembali ke tampilan awal.",
        "Membuka person" to
            "Ketuk kartu sekali untuk memilih. Panah membuka hubungan yang sudah tercatat. " +
            "Ketuk kartu terpilih sekali lagi untuk membuka inspector. Ketuk area kosong untuk " +
            "melepas pilihan.",
        "Menambah keluarga" to
            "Tanda + menunjukkan arah hubungan yang belum mempunyai data: atas untuk orang tua, " +
            "bawah untuk anak, dan samping untuk pasangan. Jalur lengkap tetap tersedia melalui " +
            "Inspector, Profil, dan menu Keluarga.",
        "Mengisi data" to
            "Gunakan nama akrab agar kartu mudah dibaca. Tanggal mulai hubungan adalah tanggal " +
            "dimulainya partnership atau pernikahan, bukan tanggal lahir. Tanggal lahir dan data " +
            "rinci dapat dilengkapi melalui profil person.",
        "Bekerja offline" to
            "Perubahan dapat masuk ke antrean ketika perangkat offline. Aktifkan koneksi kembali " +
            "dan tunggu status Sinkron. Gunakan tarik-untuk-memperbarui pada halaman yang " +
            "menyediakannya bila data perangkat lain belum terlihat.",
        "Undangan dan peran" to
            "Pembaca hanya melihat data, kontributor dapat menambah atau mengubah, sedangkan " +
            "pengelola dapat mengatur anggota serta undangan. Kontrol terkunci menandakan akses " +
            "hanya baca.",
        "Export pohon" to
            "Buka Alat lalu pilih Ekspor PDF atau Ekspor PNG. Export mengikuti cabang, kartu, " +
            "pasangan, dan connector yang sedang terlihat di workspace."
    )
    SupportPage(
        title = "Petunjuk penggunaan",
        onBack = onBack,
        modifier = modifier
    ) {
        item {
            Text(
                "Panduan ringkas TR\u00eadhAH",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(sections) { (title, body) ->
            SupportSection(title, body)
        }
    }
}

@Composable
private fun SupportPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
            ) {
                TextButton(onClick = onBack) { Text("Kembali") }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            content()
            item { Spacer(Modifier.height(8.dp)) }
        }
        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Text(
                COPYRIGHT,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SupportSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
