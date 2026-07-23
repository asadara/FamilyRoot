package com.example.familytreeplatform.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.models.PersonListItem

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var firstName by rememberSaveable { mutableStateOf("") }
    var nickName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("UNKNOWN") }
    var compactFormExpanded by rememberSaveable { mutableStateOf(false) }
    val stats = remember(state.people) { familyDirectoryStats(state.people) }

    val savePerson = {
        viewModel.create(firstName, nickName, gender)
        firstName = ""
        nickName = ""
        gender = "UNKNOWN"
        compactFormExpanded = false
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= 720.dp) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                FamilyDirectoryHeader(
                    stats = stats,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    AddPersonPanel(
                        firstName = firstName,
                        onFirstNameChange = { firstName = it },
                        nickName = nickName,
                        onNickNameChange = { nickName = it },
                        gender = gender,
                        onGenderChange = { gender = it },
                        saving = state.creating,
                        onSave = savePerson,
                        modifier = Modifier.fillMaxHeight().weight(0.34f).widthIn(max = 360.dp)
                    )
                    FamilyDirectoryPanel(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onFilterChange = viewModel::setLifeStatusFilter,
                        onRefresh = viewModel::refresh,
                        onPersonClick = onPersonClick,
                        modifier = Modifier.weight(0.66f).fillMaxHeight()
                    )
                }
            }
        } else {
            CompactPeopleContent(
                state = state,
                stats = stats,
                firstName = firstName,
                onFirstNameChange = { firstName = it },
                nickName = nickName,
                onNickNameChange = { nickName = it },
                gender = gender,
                onGenderChange = { gender = it },
                formExpanded = compactFormExpanded,
                onToggleForm = { compactFormExpanded = !compactFormExpanded },
                onSave = savePerson,
                onQueryChange = viewModel::setQuery,
                onFilterChange = viewModel::setLifeStatusFilter,
                onRefresh = viewModel::refresh,
                onPersonClick = onPersonClick,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)
            )
        }
    }
}

@Composable
private fun CompactPeopleContent(
    state: PeopleUiState,
    stats: FamilyDirectoryStats,
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    nickName: String,
    onNickNameChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    formExpanded: Boolean,
    onToggleForm: () -> Unit,
    onSave: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        item { FamilyDirectoryHeader(stats = stats, modifier = Modifier.fillMaxWidth()) }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleForm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tambah person", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tambahkan profil anggota ke silsilah",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                        )
                    }
                    Text(if (formExpanded) "⌃" else "+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (formExpanded) {
            item {
                AddPersonPanel(
                    firstName = firstName,
                    onFirstNameChange = onFirstNameChange,
                    nickName = nickName,
                    onNickNameChange = onNickNameChange,
                    gender = gender,
                    onGenderChange = onGenderChange,
                    saving = state.creating,
                    onSave = onSave,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            CompactDirectoryControls(
                state = state,
                onQueryChange = onQueryChange,
                onFilterChange = onFilterChange,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.offline && state.people.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Offline · menampilkan data yang tersimpan di perangkat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        state.error?.takeIf { state.people.isEmpty() }?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp)) }
        }
        when {
            state.refreshing && state.people.isEmpty() -> item {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.filteredPeople.isEmpty() -> item {
                EmptyFamilyDirectory(
                    hasQuery = state.query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            else -> items(state.filteredPeople, key = { it.personId }) { person ->
                FamilyPersonRow(person = person, onClick = { onPersonClick(person.personId) })
            }
        }
    }
}

@Composable
private fun CompactDirectoryControls(
    state: PeopleUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direktori person", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.filteredPeople.size} dari ${state.people.size} person ditampilkan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Text(if (state.refreshing) "Memuat…" else "Segarkan")
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Cari nama person") },
                leadingIcon = { Text("⌕", color = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (state.query.isNotBlank()) {
                    { Text("×", modifier = Modifier.clickable { onQueryChange("") }.padding(8.dp)) }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "ALL" to "Semua",
                    "ALIVE" to "Aktif",
                    "DECEASED" to "Historis",
                    "UNKNOWN" to "Belum lengkap"
                ).forEach { (value, label) ->
                    DirectoryFilter(label, state.lifeStatusFilter == value) { onFilterChange(value) }
                }
            }
        }
    }
}

@Composable
private fun FamilyDirectoryHeader(stats: FamilyDirectoryStats, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (maxWidth < 480.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Keluarga",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        "Temukan, tinjau, dan tambahkan profil anggota dalam silsilah.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            "${stats.total} person  ·  ${stats.alive} aktif  ·  ${stats.deceased} historis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Keluarga",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Temukan, tinjau, dan tambahkan profil anggota dalam silsilah.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FamilyStat("Total", stats.total.toString())
                        FamilyStat("Aktif", stats.alive.toString())
                        FamilyStat("Historis", stats.deceased.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddPersonPanel(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    nickName: String,
    onNickNameChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text("Tambah person", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Nama lengkap disimpan di profil; nama panggilan dipakai agar card tetap ringkas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = { Text("Nama lengkap") },
                supportingText = {
                    Text("Tidak perlu dipisah menjadi nama depan dan nama belakang.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = nickName,
                onValueChange = onNickNameChange,
                label = { Text("Nama panggilan di card") },
                supportingText = { Text("Contoh: Aji, Bude Ani, atau Pak Budi.") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            Text("Avatar fallback", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                GenderChoice("MALE", "Pria", gender, onGenderChange)
                GenderChoice("FEMALE", "Wanita", gender, onGenderChange)
                GenderChoice("UNKNOWN", "Belum tahu", gender, onGenderChange)
            }
            Button(
                enabled = firstName.isNotBlank() && nickName.isNotBlank() && !saving,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(if (saving) "Menyimpan…" else "Simpan person")
            }
        }
    }
}

@Composable
private fun GenderChoice(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    val active = value == selected
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onSelect(value) }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FamilyDirectoryPanel(
    state: PeopleUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direktori person", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.filteredPeople.size} dari ${state.people.size} person ditampilkan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Text(if (state.refreshing) "Memuat…" else "Segarkan")
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Cari nama person") },
                leadingIcon = { Text("⌕", color = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (state.query.isNotBlank()) {
                    { Text("×", modifier = Modifier.clickable { onQueryChange("") }.padding(8.dp)) }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "ALL" to "Semua",
                    "ALIVE" to "Aktif",
                    "DECEASED" to "Historis",
                    "UNKNOWN" to "Belum lengkap"
                ).forEach { (value, label) ->
                    DirectoryFilter(label, state.lifeStatusFilter == value) { onFilterChange(value) }
                }
            }
            if (state.offline && state.people.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        "Offline · menampilkan data yang tersimpan di perangkat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            state.error?.takeIf { state.people.isEmpty() }?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            }
            if (state.refreshing && state.people.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredPeople.isEmpty()) {
                EmptyFamilyDirectory(hasQuery = state.query.isNotBlank(), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp)
                ) {
                    items(state.filteredPeople, key = { it.personId }) { person ->
                        FamilyPersonRow(person = person, onClick = { onPersonClick(person.personId) })
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DirectoryFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FamilyPersonRow(person: PersonListItem, onClick: () -> Unit) {
    val deceased = person.lifeStatus == "DECEASED"
    val container = if (deceased) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    }
    val avatarColor = when {
        deceased -> MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        person.gender == "FEMALE" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = person.fullName }
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Surface(shape = CircleShape, color = avatarColor, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        personInitials(person.fullName),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (deceased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    person.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (deceased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        deceased -> "Profil historis"
                        person.birthPlace.isNullOrBlank() -> "Detail dasar belum lengkap"
                        else -> person.birthPlace
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyFamilyDirectory(hasQuery: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (hasQuery) "⌕" else "+", style = MaterialTheme.typography.titleLarge) }
            }
            Text(
                if (hasQuery) "Person tidak ditemukan" else "Belum ada person",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                if (hasQuery) "Coba nama atau filter lain." else "Tambahkan person pertama untuk memulai keluarga.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

internal data class FamilyDirectoryStats(val total: Int, val alive: Int, val deceased: Int)

internal fun familyDirectoryStats(people: List<PersonListItem>) = FamilyDirectoryStats(
    total = people.size,
    alive = people.count { it.lifeStatus == "ALIVE" },
    deceased = people.count { it.lifeStatus == "DECEASED" }
)

internal fun personInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "?" }
