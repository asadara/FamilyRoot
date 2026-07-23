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
                    "menyusun hubungan antargenerasi, merawat profil person, dan menyimpan " +
                    "cerita keluarga dalam satu workspace. Huruf kapitalnya membentuk TRAH, " +
                    "sedangkan kata tr\u00eadh merujuk pada pohon silsilah dalam bahasa Jawa."
            )
        }
        item {
            SupportSection(
                title = "Fitur utama",
                body = "Workspace pohon interaktif dengan block keluarga, card berfoto, filter " +
                    "generasi, pencarian jalur hubungan, penambahan person langsung, profil " +
                    "lengkap, hubungan biologis/adopsi/tiri, pemeriksaan hubungan rancu, " +
                    "aktivitas sinkronisasi, undangan berbasis peran, serta ekspor PDF dan PNG."
            )
        }
        item {
            SupportSection(
                title = "Keunggulan",
                body = "Penataan pohon menghitung ruang antarkeluarga agar garis lineage lebih " +
                    "mudah dibaca. Perubahan disimpan lebih dahulu di perangkat, kemudian " +
                    "disinkronkan saat koneksi tersedia. Sistem juga menjaga aturan hubungan " +
                    "penting tanpa menghapus keputusan keluarga secara otomatis."
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
            "Masuk dengan akun Google atau akun yang tersedia, pilih atau buat silsilah, lalu " +
            "buka Pohon. Person pertama langsung menjadi titik awal workspace.",
        "Menjelajahi pohon" to
            "Geser area kosong untuk berpindah, gunakan cubit dua jari untuk memperbesar atau " +
            "memperkecil. Filter Semua, Satu generasi, Leluhur, atau Keturunan membantu " +
            "memusatkan bacaan. Atur ulang graph mengembalikan viewport.",
        "Membuka person" to
            "Ketuk kartu sekali untuk memilih. Panah membuka hubungan yang sudah tercatat. " +
            "Ketuk kartu terpilih sekali lagi untuk membuka inspector. Ketuk area kosong untuk " +
            "melepas pilihan.",
        "Menambah person" to
            "Tekan tombol Tambah person atau tekan lama area kosong. Nama lengkap tersimpan di " +
            "profil, sedangkan nama panggilan tampil pada card. Person tanpa hubungan tetap " +
            "muncul di kelompok Belum terhubung.",
        "Menambah hubungan" to
            "Pilih card lalu gunakan tanda +, tarik titik hubung ke card lain, atau buka bagian " +
            "Keluarga & hubungan di profil. Pilihan mencakup orang tua/anak biologis, adopsi, " +
            "tiri, dan pasangan. Tanggal pernikahan boleh dikosongkan jika belum diketahui.",
        "Mengubah profil" to
            "Buka Inspector lalu pilih Edit profil lengkap. Kontributor dan Pengelola dapat " +
            "mengubah nama lengkap, nama panggilan card, gender, tanggal/tempat lahir, status " +
            "kehidupan, data meninggal, foto, dan catatan. Viewer hanya dapat membaca.",
        "Pusat pohon" to
            "Jadikan [nama] pusat pohon berarti menata ulang silsilah dengan person tersebut " +
            "sebagai person utama; bukan memindahkan card ke kiri. Pan dan zoom tetap aktif.",
        "Memperbaiki hubungan" to
            "Hubungan yang salah dapat dihapus dari Profil lengkap. Jika sistem menemukan " +
            "hubungan ganda atau rancu, banner pemeriksaan menampilkan rekomendasi tanpa " +
            "menghapus data secara otomatis.",
        "Bekerja offline" to
            "Perubahan dapat masuk ke antrean ketika perangkat offline. Aktifkan koneksi kembali " +
            "dan pantau label Tersinkron, Menunggu, Gagal, atau Konflik di header. Detail dan " +
            "tindakan ulang tersedia melalui Aktivitas atau bagian Sinkronisasi profil.",
        "Undangan dan peran" to
            "Viewer hanya melihat data, Kontributor dapat menambah atau mengubah, sedangkan " +
            "Pengelola/Pemilik dapat mengatur anggota dan undangan. Kode undangan dibedakan " +
            "menurut peran akses.",
        "Export pohon" to
            "Buka Alat lalu pilih Ekspor PDF atau Ekspor PNG. Export mengikuti cabang, kartu, " +
            "pasangan, dan connector yang sedang terlihat di workspace.",
        "Kembali ke Pohon" to
            "Dari Profil lengkap, gunakan tombol Kembali ke Pohon yang selalu terlihat atau " +
            "pilih menu Pohon. Anda tidak perlu menggulir kembali ke bagian atas."
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
