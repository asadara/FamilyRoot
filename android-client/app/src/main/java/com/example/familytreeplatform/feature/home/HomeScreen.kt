package com.example.familytreeplatform.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenTree: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        Text("Beranda", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ringkasan ruang keluarga dan jalan cepat untuk melanjutkan penjelajahan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeAction("Lanjutkan pohon", "Buka kembali workspace graph", onOpenTree, Modifier.weight(1f))
            HomeAction("Keluarga", "Kelola anggota dan data dasar", onOpenFamily, Modifier.weight(1f))
        }
        HomeAction("Aktivitas", "Lihat riwayat kontribusi keluarga", onOpenActivity, Modifier.fillMaxWidth())
    }
}

@Composable
private fun HomeAction(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
