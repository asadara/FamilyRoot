package com.example.familytreeplatform.feature.persondetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytreeplatform.data.local.OfflineMutationEntity
import com.example.familytreeplatform.data.local.OfflineMutationStatus
import com.example.familytreeplatform.data.local.OfflineMutationType
import com.example.familytreeplatform.models.MediaItem
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.SourceItem
import coil.compose.SubcomposeAsyncImage

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onBack: () -> Unit,
    canEditProfile: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val person = state.person

    if (person == null) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var birthPlace by rememberSaveable(person.personId, person.birthPlace) {
        mutableStateOf(person.birthPlace.orEmpty())
    }
    var profileNotes by rememberSaveable(person.personId, person.notes) {
        mutableStateOf(person.notes.orEmpty())
    }
    var sourceTitle by rememberSaveable(person.personId) { mutableStateOf("") }
    var sourceNote by rememberSaveable(person.personId) { mutableStateOf("") }
    var mediaLabel by rememberSaveable(person.personId) { mutableStateOf("") }
    var mediaUri by rememberSaveable(person.personId) { mutableStateOf("") }
    var proposalNotes by rememberSaveable(person.personId) { mutableStateOf("") }
    var proposalReason by rememberSaveable(person.personId) { mutableStateOf("") }
    var relationQuery by rememberSaveable(person.personId) { mutableStateOf("") }
    var pendingRelationshipDelete by rememberSaveable(person.personId) { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val profilePhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(viewModel::uploadProfilePhoto)
    }

    var overviewExpanded by rememberSaveable(person.personId) { mutableStateOf(true) }
    var syncExpanded by rememberSaveable(person.personId) { mutableStateOf(false) }
    var sourcesExpanded by rememberSaveable(person.personId) { mutableStateOf(false) }
    var mediaExpanded by rememberSaveable(person.personId) { mutableStateOf(false) }
    var proposalExpanded by rememberSaveable(person.personId) { mutableStateOf(false) }
    var relationsExpanded by rememberSaveable(person.personId) { mutableStateOf(false) }

    val relations = state.relations
    val relationCount = relations?.let { it.parents.size + it.children.size + it.spouses.size } ?: 0
    val relationTargets = remember(state.people, relationQuery, person.personId) {
        state.people
            .asSequence()
            .filter { it.personId != person.personId }
            .filter { relationQuery.isBlank() || it.fullName.contains(relationQuery, ignoreCase = true) }
            .take(if (relationQuery.isBlank()) 6 else 20)
            .toList()
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.refreshing,
        onRefresh = viewModel::refresh
    )

    LaunchedEffect(state.message, state.error) {
        val feedback = state.error?.let(::personErrorMessage)
            ?: state.message?.let(::personMessage)
        if (!feedback.isNullOrBlank()) snackbarHostState.showSnackbar(feedback)
    }

    pendingRelationshipDelete?.let { relationshipId ->
        AlertDialog(
            onDismissRequest = { pendingRelationshipDelete = null },
            title = { Text("Hapus hubungan keluarga?") },
            text = {
                Text("Garis hubungan akan dihapus dari pohon keluarga. Profil person tetap tersimpan.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingRelationshipDelete = null
                    viewModel.deleteRelationship(relationshipId)
                }) { Text("Hapus hubungan") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRelationshipDelete = null }) { Text("Batal") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pullRefresh(pullRefreshState),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().widthIn(max = 1040.dp)
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("‹  Kembali ke workspace")
                }
            }
            item {
                PersonProfileHero(
                    person = person,
                    profilePhotoUrl = state.profilePhotoUrl,
                    canEditProfile = canEditProfile,
                    updating = state.updating,
                    onPickPhoto = { profilePhotoPicker.launch("image/*") }
                )
            }
            state.message?.let { message ->
                item { ProfileFeedback(message = personMessage(message), error = false) }
            }
            state.error?.let { error ->
                item { ProfileFeedback(message = personErrorMessage(error), error = true) }
            }
            item {
                PersonProfileSection(
                    title = "Data utama",
                    subtitle = "Identitas dasar dan catatan person",
                    badge = "Utama",
                    expanded = overviewExpanded,
                    onToggle = { overviewExpanded = !overviewExpanded }
                ) {
                    OverviewSection(
                        person = person,
                        birthPlace = birthPlace,
                        onBirthPlaceChange = { birthPlace = it },
                        notes = profileNotes,
                        onNotesChange = { profileNotes = it },
                        updating = state.updating,
                        claiming = state.claiming,
                        claimStatus = state.claim?.status,
                        onSave = { viewModel.updateProfile(birthPlace, profileNotes) },
                        onLifeStatusChange = viewModel::updateLifeStatus,
                        onClaim = viewModel::claimAsMe
                    )
                }
            }
            item {
                PersonProfileSection(
                    title = "Sinkronisasi",
                    subtitle = "Perubahan lokal, antrean, dan konflik",
                    badge = state.offlineMutations.size.takeIf { it > 0 }?.toString(),
                    expanded = syncExpanded,
                    onToggle = { syncExpanded = !syncExpanded }
                ) {
                    SyncSection(
                        mutations = state.offlineMutations,
                        onKeepLocal = viewModel::keepLocalConflict,
                        onUseServer = viewModel::useServerConflict,
                        onRetry = viewModel::retryFailedSync
                    )
                }
            }
            item {
                PersonProfileSection(
                    title = "Sumber keluarga",
                    subtitle = "Catatan referensi tanpa unggahan dokumen formal",
                    badge = state.sources.size.toString(),
                    expanded = sourcesExpanded,
                    onToggle = { sourcesExpanded = !sourcesExpanded }
                ) {
                    SourcesSection(
                        sources = state.sources,
                        title = sourceTitle,
                        onTitleChange = { sourceTitle = it },
                        note = sourceNote,
                        onNoteChange = { sourceNote = it },
                        updating = state.updating,
                        onAdd = {
                            viewModel.addSource(sourceTitle, sourceNote)
                            sourceTitle = ""
                            sourceNote = ""
                        }
                    )
                }
            }
            item {
                PersonProfileSection(
                    title = "Tautan kenangan",
                    subtitle = "Arahkan ke penyimpanan atau media eksternal",
                    badge = state.media.size.toString(),
                    expanded = mediaExpanded,
                    onToggle = { mediaExpanded = !mediaExpanded }
                ) {
                    MediaSection(
                        media = state.media.filterNot { it.uri.startsWith("object://") },
                        label = mediaLabel,
                        onLabelChange = { mediaLabel = it },
                        uri = mediaUri,
                        onUriChange = { mediaUri = it },
                        updating = state.updating,
                        onAdd = {
                            viewModel.addMedia(mediaLabel, mediaUri)
                            mediaLabel = ""
                            mediaUri = ""
                        }
                    )
                }
            }
            item {
                PersonProfileSection(
                    title = "Usulan kolaboratif",
                    subtitle = "Ajukan koreksi tanpa menimpa data langsung",
                    expanded = proposalExpanded,
                    onToggle = { proposalExpanded = !proposalExpanded }
                ) {
                    ProposalSection(
                        notes = proposalNotes,
                        onNotesChange = { proposalNotes = it },
                        reason = proposalReason,
                        onReasonChange = { proposalReason = it },
                        updating = state.updating,
                        onSubmit = {
                            viewModel.proposeNotes(proposalNotes, proposalReason)
                            proposalNotes = ""
                            proposalReason = ""
                        }
                    )
                }
            }
            item {
                PersonProfileSection(
                    title = "Keluarga & hubungan",
                    subtitle = "Tinjau dan hubungkan profil anggota dalam silsilah",
                    badge = relationCount.toString(),
                    expanded = relationsExpanded,
                    onToggle = { relationsExpanded = !relationsExpanded }
                ) {
                    RelationsSection(
                        relations = relations,
                        loading = state.loadingRelations,
                        query = relationQuery,
                        onQueryChange = { relationQuery = it },
                        targets = relationTargets,
                        people = state.people,
                        updating = state.updating,
                        onAddParent = { targetId, meta -> viewModel.addParent(targetId, meta) },
                        onAddChild = { targetId, meta -> viewModel.addChild(targetId, meta) },
                        onAddSpouse = viewModel::addSpouse,
                        onDeleteRelationship = { pendingRelationshipDelete = it },
                        onFindPath = viewModel::findPathTo,
                        pathLabel = state.path?.let { path ->
                            if (path.found) {
                                path.people.joinToString("  ›  ") { it.fullName }
                            } else {
                                "Jalur hubungan belum ditemukan"
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        PullRefreshIndicator(
            refreshing = state.refreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun PersonProfileHero(
    person: PersonListItem,
    profilePhotoUrl: String?,
    canEditProfile: Boolean,
    updating: Boolean,
    onPickPhoto: () -> Unit
) {
    val deceased = person.lifeStatus == "DECEASED"
    val avatarColor = if (deceased) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val contentColor = if (deceased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            if (deceased) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            val compact = maxWidth < 480.dp
            if (compact) {
                Column {
                    PersonHeroIdentity(
                        person, avatarColor, contentColor, profilePhotoUrl,
                        canEditProfile, updating, onPickPhoto
                    )
                    PersonHeroFacts(person, Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonHeroIdentity(
                        person, avatarColor, contentColor, profilePhotoUrl,
                        canEditProfile, updating, onPickPhoto, Modifier.weight(1f)
                    )
                    PersonHeroFacts(person, Modifier.widthIn(min = 300.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonHeroIdentity(
    person: PersonListItem,
    avatarColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    profilePhotoUrl: String?,
    canEditProfile: Boolean,
    updating: Boolean,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        PersonDetailAvatar(person, profilePhotoUrl, avatarColor)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text("Profil person", style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.76f))
            Text(
                person.fullName,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    if (person.lifeStatus == "DECEASED") "Profil historis" else "Person keluarga",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
            if (canEditProfile) {
                TextButton(
                    enabled = !updating,
                    onClick = onPickPhoto,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(if (profilePhotoUrl == null) "Tambah foto" else "Ganti foto")
                }
            }
        }
    }
}

@Composable
private fun PersonDetailAvatar(
    person: PersonListItem,
    profilePhotoUrl: String?,
    avatarColor: androidx.compose.ui.graphics.Color
) {
    val fallback: @Composable () -> Unit = {
        Surface(shape = CircleShape, color = avatarColor, modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    personProfileInitials(person.fullName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (person.lifeStatus == "DECEASED") {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            }
        }
    }
    Surface(
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = Modifier.size(82.dp).clip(CircleShape)
    ) {
        if (profilePhotoUrl.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = profilePhotoUrl,
                contentDescription = "Foto profil ${person.fullName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { fallback() },
                error = { fallback() }
            )
        }
    }
}

@Composable
private fun PersonHeroFacts(person: PersonListItem, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.horizontalScroll(rememberScrollState())) {
        ProfileFact("Gender", genderLabel(person.gender))
        ProfileFact("Lahir", person.birthDate ?: "Belum diisi")
        if (person.lifeStatus == "DECEASED") ProfileFact("Meninggal", person.deceasedAt ?: "Belum diisi")
    }
}

@Composable
private fun ProfileFact(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun PersonProfileSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(18.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                badge?.let {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                }
                Text(
                    if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = if (expanded) "Tutup $title" else "Buka $title" }
                        .padding(8.dp)
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), content = content)
            }
        }
    }
}

@Composable
private fun OverviewSection(
    person: PersonListItem,
    birthPlace: String,
    onBirthPlaceChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    updating: Boolean,
    claiming: Boolean,
    claimStatus: String?,
    onSave: () -> Unit,
    onLifeStatusChange: (String) -> Unit,
    onClaim: () -> Unit
) {
    Text("Status kehidupan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState())
    ) {
        listOf("ALIVE" to "Aktif", "DECEASED" to "Historis", "UNKNOWN" to "Belum diketahui").forEach { (value, label) ->
            ProfileChoice(label, selected = person.lifeStatus == value, enabled = !updating) {
                if (person.lifeStatus != value) onLifeStatusChange(value)
            }
        }
    }
    OutlinedTextField(
        value = birthPlace,
        onValueChange = onBirthPlaceChange,
        label = { Text("Tempat lahir") },
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        singleLine = true
    )
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text("Catatan profil") },
        supportingText = { Text("Informasi keluarga, bukan dokumen administratif formal") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 3
    )
    Button(
        enabled = !updating && (birthPlace.trim() != person.birthPlace.orEmpty() || notes.trim() != person.notes.orEmpty()),
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Text(if (updating) "Menyimpan…" else "Simpan perubahan")
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Identitas akun", style = MaterialTheme.typography.titleMedium)
                Text(
                    claimStatus?.let { "Status klaim: ${claimStatusLabel(it)}" }
                        ?: "Hubungkan profil person ini dengan akun Anda melalui proses verifikasi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(onClick = onClaim, enabled = !claiming) {
                Text(if (claiming) "Mengirim…" else "Ini saya")
            }
        }
    }
}

@Composable
private fun ProfileChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SyncSection(
    mutations: List<OfflineMutationEntity>,
    onKeepLocal: (String) -> Unit,
    onUseServer: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    if (mutations.isEmpty()) {
        EmptySectionMessage("Tidak ada perubahan lokal yang menunggu penanganan.")
        return
    }
    mutations.forEachIndexed { index, mutation ->
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mutationTypeLabel(mutation.mutationType), style = MaterialTheme.typography.titleMedium)
                Text(
                    syncStatusLabel(mutation.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mutation.status == OfflineMutationStatus.CONFLICT || mutation.status == OfflineMutationStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text("Percobaan ${mutation.attemptCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        mutation.lastError?.let {
            Text(personErrorMessage(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        if (mutation.status == OfflineMutationStatus.CONFLICT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(onClick = { onKeepLocal(mutation.mutationId) }) { Text("Pakai perubahan saya") }
                OutlinedButton(onClick = { onUseServer(mutation.mutationId) }) { Text("Pakai versi server") }
            }
        } else if (mutation.status == OfflineMutationStatus.FAILED) {
            OutlinedButton(onClick = { onRetry(mutation.mutationId) }, modifier = Modifier.padding(top = 10.dp)) {
                Text("Coba sinkronkan lagi")
            }
        }
    }
}

@Composable
private fun SourcesSection(
    sources: List<SourceItem>,
    title: String,
    onTitleChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    updating: Boolean,
    onAdd: () -> Unit
) {
    Text(
        "TRêdhAH menyimpan catatan referensi, bukan berkas administrasi negara atau bukti formal.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    sources.forEach { source -> ArchiveRow(source.title, source.note ?: "Catatan sumber keluarga") }
    if (sources.isEmpty()) EmptySectionMessage("Belum ada catatan sumber.")
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Judul sumber") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        singleLine = true
    )
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text("Catatan referensi") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2
    )
    Button(enabled = title.isNotBlank() && !updating, onClick = onAdd, modifier = Modifier.padding(top = 10.dp)) {
        Text("Tambah catatan sumber")
    }
}

@Composable
private fun MediaSection(
    media: List<MediaItem>,
    label: String,
    onLabelChange: (String) -> Unit,
    uri: String,
    onUriChange: (String) -> Unit,
    updating: Boolean,
    onAdd: () -> Unit
) {
    Text(
        "Gunakan tautan Google Drive, album keluarga, atau media sosial. TRêdhAH tidak menjadi galeri penyimpanan foto.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    media.forEach { item -> ArchiveRow(item.label, item.uri) }
    if (media.isEmpty()) EmptySectionMessage("Belum ada tautan kenangan.")
    OutlinedTextField(
        value = label,
        onValueChange = onLabelChange,
        label = { Text("Label tautan") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        singleLine = true
    )
    OutlinedTextField(
        value = uri,
        onValueChange = onUriChange,
        label = { Text("URL eksternal") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true
    )
    Button(enabled = label.isNotBlank() && uri.isNotBlank() && !updating, onClick = onAdd, modifier = Modifier.padding(top = 10.dp)) {
        Text("Tambah tautan")
    }
}

@Composable
private fun ArchiveRow(title: String, detail: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProposalSection(
    notes: String,
    onNotesChange: (String) -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    updating: Boolean,
    onSubmit: () -> Unit
) {
    Text(
        "Usulan akan tercatat sebagai kontribusi dan dapat ditinjau keluarga sebelum menjadi data bersama.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text("Catatan yang diusulkan") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        minLines = 3
    )
    OutlinedTextField(
        value = reason,
        onValueChange = onReasonChange,
        label = { Text("Alasan atau konteks") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2
    )
    Button(enabled = notes.isNotBlank() && !updating, onClick = onSubmit, modifier = Modifier.padding(top = 10.dp)) {
        Text("Kirim usulan")
    }
}

@Composable
private fun RelationsSection(
    relations: RelationsResponse?,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    targets: List<PersonListItem>,
    people: List<PersonListItem>,
    updating: Boolean,
    onAddParent: (String, String) -> Unit,
    onAddChild: (String, String) -> Unit,
    onAddSpouse: (String) -> Unit,
    onDeleteRelationship: (String) -> Unit,
    onFindPath: (String) -> Unit,
    pathLabel: String?
) {
    if (loading && relations == null) CircularProgressIndicator()
    relations?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            RelationCount("Orang tua", it.parents.size)
            RelationCount("Anak", it.children.size)
            RelationCount("Pasangan", it.spouses.size)
        }
        val peopleById = people.associateBy(PersonListItem::personId)
        val existing = buildList {
            addAll(it.parents.map { relation -> ExistingRelation(relation, "Orang tua", relation.fromPersonId) })
            addAll(it.children.map { relation -> ExistingRelation(relation, "Anak", relation.toPersonId) })
            addAll(it.spouses.map { relation ->
                val otherId = if (relation.fromPersonId == it.personId) relation.toPersonId else relation.fromPersonId
                ExistingRelation(relation, "Pasangan", otherId)
            })
        }
        existing.forEach { item ->
            ExistingRelationshipRow(
                item = item,
                personName = peopleById[item.otherPersonId]?.fullName ?: "Person keluarga",
                enabled = !updating,
                onDelete = { onDeleteRelationship(item.relationship.relationshipId) }
            )
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Cari person untuk dihubungkan") },
        supportingText = { Text("Hasil awal dibatasi agar halaman tetap ringan") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        singleLine = true
    )
    if (targets.isEmpty()) {
        EmptySectionMessage("Person yang dicari tidak ditemukan.")
    } else {
        targets.forEach { target ->
            RelationTargetRow(
                target = target,
                enabled = !updating,
                onAddParent = { meta -> onAddParent(target.personId, meta) },
                onAddChild = { meta -> onAddChild(target.personId, meta) },
                onAddSpouse = { onAddSpouse(target.personId) },
                onFindPath = { onFindPath(target.personId) }
            )
        }
    }
    pathLabel?.let {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Jalur hubungan", style = MaterialTheme.typography.titleMedium)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun RelationCount(label: String, value: Int) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RelationTargetRow(
    target: PersonListItem,
    enabled: Boolean,
    onAddParent: (String) -> Unit,
    onAddChild: (String) -> Unit,
    onAddSpouse: () -> Unit,
    onFindPath: () -> Unit
) {
    var moreExpanded by rememberSaveable(target.personId) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(personProfileInitials(target.fullName), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(target.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(genderLabel(target.gender), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp).horizontalScroll(rememberScrollState())
            ) {
                OutlinedButton(enabled = enabled, onClick = { onAddParent("BIOLOGICAL") }) { Text("Orang tua") }
                OutlinedButton(enabled = enabled, onClick = { onAddChild("BIOLOGICAL") }) { Text("Anak") }
                OutlinedButton(enabled = enabled, onClick = onAddSpouse) { Text("Pasangan") }
                Box {
                    OutlinedButton(enabled = enabled, onClick = { moreExpanded = true }) { Text("Lainnya") }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Orang tua adopsi") },
                            onClick = { moreExpanded = false; onAddParent("ADOPTIVE") }
                        )
                        DropdownMenuItem(
                            text = { Text("Orang tua tiri") },
                            onClick = { moreExpanded = false; onAddParent("STEP") }
                        )
                        DropdownMenuItem(
                            text = { Text("Anak adopsi") },
                            onClick = { moreExpanded = false; onAddChild("ADOPTIVE") }
                        )
                        DropdownMenuItem(
                            text = { Text("Anak tiri") },
                            onClick = { moreExpanded = false; onAddChild("STEP") }
                        )
                    }
                }
                TextButton(enabled = enabled, onClick = onFindPath) { Text("Cari jalur") }
            }
        }
    }
}

private data class ExistingRelation(
    val relationship: RelationItem,
    val roleLabel: String,
    val otherPersonId: String
)

@Composable
private fun ExistingRelationshipRow(
    item: ExistingRelation,
    personName: String,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("${item.roleLabel} · $personName", style = MaterialTheme.typography.titleSmall)
            Text(
                relationshipMetaLabel(item.relationship.meta),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(enabled = enabled, onClick = onDelete) {
            Text("Hapus", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptySectionMessage(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(13.dp))
    }
}

@Composable
private fun ProfileFeedback(message: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(13.dp)
        )
    }
}

internal fun personProfileInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "?" }

internal fun genderLabel(gender: String?): String = when (gender?.uppercase()) {
    "MALE" -> "Pria"
    "FEMALE" -> "Wanita"
    else -> "Belum diketahui"
}

internal fun mutationTypeLabel(type: String): String = when (type) {
    OfflineMutationType.UPDATE_PROFILE -> "Perubahan profil"
    OfflineMutationType.UPDATE_LIFE_STATUS -> "Status kehidupan"
    OfflineMutationType.ADD_PARENT_CHILD -> "Hubungan orang tua–anak"
    OfflineMutationType.ADD_SPOUSE -> "Hubungan pasangan"
    else -> "Perubahan person"
}

internal fun syncStatusLabel(status: String): String = when (status) {
    OfflineMutationStatus.PENDING -> "Menunggu sinkronisasi"
    OfflineMutationStatus.SYNCING -> "Sedang disinkronkan"
    OfflineMutationStatus.CONFLICT -> "Perlu menyelesaikan konflik"
    OfflineMutationStatus.FAILED -> "Sinkronisasi gagal"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

internal fun claimStatusLabel(status: String): String = when (status.uppercase()) {
    "VERIFIED" -> "Terverifikasi"
    "PENDING" -> "Menunggu verifikasi"
    "REJECTED" -> "Ditolak"
    else -> status
}

internal fun personMessage(message: String): String = when {
    message.contains("Source added", true) -> "Catatan sumber berhasil ditambahkan."
    message.contains("Media added", true) -> "Tautan kenangan berhasil ditambahkan."
    message.contains("Proposal submitted", true) -> "Usulan berhasil dikirim untuk ditinjau keluarga."
    message.contains("Parent relationship saved", true) -> "Hubungan orang tua berhasil ditentukan dan menunggu sinkronisasi."
    message.contains("Child relationship saved", true) -> "Hubungan anak berhasil ditentukan dan menunggu sinkronisasi."
    message.contains("Spouse saved", true) -> "Hubungan pasangan berhasil ditentukan dan menunggu sinkronisasi."
    message.contains("Relationship deleted", true) -> "Hubungan keluarga berhasil dihapus."
    message.contains("sync queued", true) -> "Perubahan disimpan di perangkat dan masuk antrean sinkronisasi."
    else -> message
}

internal fun relationshipMetaLabel(meta: String?): String = when (meta) {
    "BIOLOGICAL" -> "Biologis"
    "ADOPTIVE" -> "Adopsi"
    "STEP" -> "Tiri"
    "MARRIED" -> "Menikah"
    "DIVORCED" -> "Bercerai"
    "WIDOWED" -> "Duda/janda"
    else -> "Hubungan keluarga"
}

internal fun personErrorMessage(error: String): String = when {
    error.contains("127.0.0.1", true) || error.contains("failed to connect", true) || error.contains("end of stream", true) ->
        "Data belum dapat diperbarui. Periksa koneksi lalu coba kembali."
    else -> error
}
