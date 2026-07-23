package com.example.familytreeplatform.feature.graph

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Calendar

enum class QuickRelationKind { PARENT, CHILD, PARTNER }

data class GraphQuickAddRequest(
    val anchorPersonId: String,
    val anchorName: String,
    val kind: QuickRelationKind,
    val coParentId: String? = null,
    val coParentName: String? = null
)

@Composable
fun GraphAddPersonDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (firstName: String, nickName: String, gender: String) -> Unit
) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var nickName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("UNKNOWN") }
    val canSave = firstName.isNotBlank() && nickName.isNotBlank() && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Tambah person baru") },
        text = {
            Column {
                Text(
                    "Card akan langsung muncul di workspace pada bagian Belum terhubung. " +
                        "Hubungan keluarga dapat ditentukan setelahnya."
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Nama lengkap") },
                    supportingText = {
                        Text("Tuliskan sesuai sebutan keluarga; tidak perlu dipisah depan/belakang.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = nickName,
                    onValueChange = { nickName = it },
                    label = { Text("Nama panggilan di card") },
                    supportingText = { Text("Contoh: Aji, Bude Ani, atau Pak Budi.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text("Gender", modifier = Modifier.padding(top = 10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    GenderButton("MALE", "Pria", gender) { gender = it }
                    GenderButton("FEMALE", "Wanita", gender) { gender = it }
                    GenderButton("UNKNOWN", "Belum tahu", gender) { gender = it }
                }
                error?.let {
                    Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(firstName.trim(), nickName.trim(), gender)
                }
            ) {
                Text(if (saving) "Menyimpan…" else "Simpan person")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun GraphQuickAddDialog(
    request: GraphQuickAddRequest,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onSave: (firstName: String, nickName: String, gender: String, startDate: String?) -> Unit
) {
    var firstName by rememberSaveable(request) { mutableStateOf("") }
    var nickName by rememberSaveable(request) { mutableStateOf("") }
    var gender by rememberSaveable(request) { mutableStateOf("UNKNOWN") }
    var startDate by rememberSaveable(request) { mutableStateOf("") }
    val context = LocalContext.current
    val relationLabel = when (request.kind) {
        QuickRelationKind.PARENT -> "orang tua"
        QuickRelationKind.CHILD -> "anak"
        QuickRelationKind.PARTNER -> "pasangan"
    }
    val canSave = firstName.isNotBlank() && nickName.isNotBlank() && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Tambah $relationLabel") },
        text = {
            Column {
                Text(
                    request.coParentName?.let {
                        "Person baru akan dihubungkan sebagai anak ${request.anchorName} dan $it."
                    } ?: "Person baru akan dihubungkan sebagai $relationLabel ${request.anchorName}."
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Nama lengkap") },
                    supportingText = {
                        Text("Tuliskan sesuai sebutan keluarga; tidak perlu dipisah depan/belakang.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = nickName,
                    onValueChange = { nickName = it },
                    label = { Text("Nama panggilan di card") },
                    supportingText = { Text("Nama singkat yang akan terlihat di workspace.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text("Gender", modifier = Modifier.padding(top = 10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    GenderButton("MALE", "Pria", gender) { gender = it }
                    GenderButton("FEMALE", "Wanita", gender) { gender = it }
                    GenderButton("UNKNOWN", "Belum tahu", gender) { gender = it }
                }
                if (request.kind == QuickRelationKind.PARTNER) {
                    val openRelationshipDatePicker = {
                        val initial = parseIsoDate(startDate) ?: Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                startDate = "%04d-%02d-%02d".format(
                                    year,
                                    month + 1,
                                    dayOfMonth
                                )
                            },
                            initial.get(Calendar.YEAR),
                            initial.get(Calendar.MONTH),
                            initial.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    OutlinedTextField(
                        value = formatIndonesianDate(startDate).orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tanggal pernikahan (opsional)") },
                        supportingText = {
                            Text("Biarkan kosong jika keluarga belum mengetahui tanggalnya.")
                        },
                        trailingIcon = {
                            Row {
                                if (startDate.isNotBlank()) {
                                    TextButton(
                                        enabled = !saving,
                                        onClick = { startDate = "" }
                                    ) {
                                        Text("Hapus")
                                    }
                                }
                                TextButton(
                                    enabled = !saving,
                                    onClick = openRelationshipDatePicker
                                ) {
                                    Text("Pilih")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("relationship-start-date")
                    )
                }
                error?.let {
                    Text(it, modifier = Modifier.padding(top = 10.dp))
                }
                TextButton(
                    enabled = !saving,
                    onClick = onOpenProfile,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Kelola lewat profil lengkap")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        firstName.trim(),
                        nickName.trim(),
                        gender,
                        startDate.takeIf {
                            request.kind == QuickRelationKind.PARTNER && it.isNotBlank()
                        }
                    )
                }
            ) {
                Text(if (saving) "Menyimpan…" else "Simpan & hubungkan")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun GenderButton(
    value: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

private val INDONESIAN_MONTHS = listOf(
    "Januari",
    "Februari",
    "Maret",
    "April",
    "Mei",
    "Juni",
    "Juli",
    "Agustus",
    "September",
    "Oktober",
    "November",
    "Desember"
)

internal fun formatIndonesianDate(isoDate: String): String? {
    val calendar = parseIsoDate(isoDate) ?: return null
    return "%02d %s %04d".format(
        calendar.get(Calendar.DAY_OF_MONTH),
        INDONESIAN_MONTHS[calendar.get(Calendar.MONTH)],
        calendar.get(Calendar.YEAR)
    )
}

private fun parseIsoDate(isoDate: String): Calendar? {
    if (!DATE_PATTERN.matches(isoDate)) return null
    val parts = isoDate.split('-').mapNotNull(String::toIntOrNull)
    if (parts.size != 3 || parts[1] !in 1..12) return null
    return runCatching {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(parts[0], parts[1] - 1, parts[2])
            timeInMillis
        }
    }.getOrNull()
}
