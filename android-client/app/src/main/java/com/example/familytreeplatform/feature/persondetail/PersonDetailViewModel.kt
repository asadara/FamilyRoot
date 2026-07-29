package com.example.familytreeplatform.feature.persondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.MediaItem
import com.example.familytreeplatform.models.MediaRequest
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.ProposalRequest
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.SourceItem
import com.example.familytreeplatform.models.SourceRequest
import com.example.familytreeplatform.models.PersonDeletionImpact
import com.example.familytreeplatform.repository.PersonRepository
import android.net.Uri
import com.example.familytreeplatform.data.local.OfflineMutationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonDetailUiState(
    val person: PersonListItem? = null,
    val relations: RelationsResponse? = null,
    val people: List<PersonListItem> = emptyList(),
    val sources: List<SourceItem> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val profilePhotoUrl: String? = null,
    val path: RelationshipPathResponse? = null,
    val loadingRelations: Boolean = false,
    val refreshing: Boolean = false,
    val claiming: Boolean = false,
    val updating: Boolean = false,
    val claim: ClaimResponse? = null,
    val offlineMutations: List<OfflineMutationEntity> = emptyList(),
    val deletionImpact: PersonDeletionImpact? = null,
    val loadingDeletionImpact: Boolean = false,
    val deletingPerson: Boolean = false,
    val deletionOutcome: PersonDeletionOutcome? = null,
    val message: String? = null,
    val error: String? = null
)

enum class PersonDeletionOutcome {
    DELETED,
    REQUESTED
}

data class PersonProfileEditInput(
    val fullName: String,
    val nickName: String,
    val gender: String,
    val birthDate: String,
    val birthPlace: String,
    val lifeStatus: String,
    val deceasedAt: String,
    val deathPlace: String,
    val notes: String
)

class PersonDetailViewModel(
    private val spaceId: String,
    private val personId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePerson(personId).collectLatest { person ->
                _uiState.update { it.copy(person = person) }
            }
        }
        viewModelScope.launch {
            repository.observePersons(spaceId).collectLatest { people ->
                _uiState.update { it.copy(people = people) }
            }
        }
        viewModelScope.launch {
            repository.observeOfflineMutations(personId).collectLatest { mutations ->
                _uiState.update { it.copy(offlineMutations = mutations) }
            }
        }
        viewModelScope.launch {
            repository.observeProfilePhotoUrls(spaceId).collectLatest { photoUrls ->
                _uiState.update { it.copy(profilePhotoUrl = photoUrls[personId]) }
            }
        }
        viewModelScope.launch {
            repository.observeSources(personId).collectLatest { sources ->
                _uiState.update { it.copy(sources = sources) }
            }
        }
        viewModelScope.launch {
            repository.listPersons(spaceId)
        }
        refreshRelations()
        refreshArchive()
    }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(refreshing = true, loadingRelations = true, error = null, message = null)
            }
            val peopleResult = repository.listPersons(spaceId)
            val relationsResult = repository.getRelations(spaceId, personId)
            val sourcesResult = repository.listSources(spaceId, personId)
            val mediaResult = repository.listMedia(spaceId, personId)
            val profilePhotosResult = repository.listProfilePhotos(spaceId)
            _uiState.update { current ->
                current.copy(
                    refreshing = false,
                    loadingRelations = false,
                    people = peopleResult.getOrNull() ?: current.people,
                    relations = relationsResult.getOrNull() ?: current.relations,
                    sources = sourcesResult.getOrNull() ?: current.sources,
                    media = mediaResult.getOrNull() ?: current.media,
                    profilePhotoUrl = profilePhotosResult.getOrNull()
                        ?.firstOrNull { it.personId == personId }
                        ?.url
                        ?: current.profilePhotoUrl,
                    error = peopleResult.exceptionOrNull()?.message
                        ?: relationsResult.exceptionOrNull()?.message
                        ?: sourcesResult.exceptionOrNull()?.message
                        ?: mediaResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun refreshRelations() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingRelations = true, error = null) }
            repository.getRelations(spaceId, personId)
                .onSuccess { relations ->
                    _uiState.update { it.copy(loadingRelations = false, relations = relations) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loadingRelations = false, error = error.message) }
                }
        }
    }

    fun claimAsMe() {
        viewModelScope.launch {
            _uiState.update { it.copy(claiming = true, error = null, claim = null) }
            repository.createClaim(ClaimRequest(spaceId = spaceId, personId = personId))
                .onSuccess { claim ->
                    _uiState.update { it.copy(claiming = false, claim = claim) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(claiming = false, error = error.message) }
                }
        }
    }

    fun refreshArchive() {
        viewModelScope.launch {
            val sourcesResult = repository.listSources(spaceId, personId)
            val mediaResult = repository.listMedia(spaceId, personId)
            val profilePhotosResult = repository.listProfilePhotos(spaceId)
            _uiState.update {
                it.copy(
                    sources = sourcesResult.getOrNull().orEmpty(),
                    media = mediaResult.getOrNull().orEmpty(),
                    profilePhotoUrl = profilePhotosResult.getOrNull()
                        ?.firstOrNull { photo -> photo.personId == personId }
                        ?.url
                        ?: it.profilePhotoUrl,
                    error = sourcesResult.exceptionOrNull()?.message
                        ?: mediaResult.exceptionOrNull()?.message
                        ?: it.error
                )
            }
        }
    }

    fun addSource(title: String, note: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.createSource(
                personId,
                SourceRequest(
                    spaceId = spaceId,
                    title = title.trim(),
                    type = "DOCUMENT",
                    note = note.trim().ifBlank { null }
                )
            ).onSuccess {
                refreshArchive()
                _uiState.update { it.copy(updating = false, message = "Source sync queued") }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun addMedia(label: String, uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.createMedia(
                personId,
                MediaRequest(
                    spaceId = spaceId,
                    label = label.trim(),
                    kind = "PHOTO",
                    uri = uri.trim()
                )
            ).onSuccess {
                refreshArchive()
                _uiState.update { it.copy(updating = false, message = "Media added") }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun proposeNotes(value: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.createProposal(
                ProposalRequest(
                    spaceId = spaceId,
                    personId = personId,
                    field = "notes",
                    proposedValue = value.trim(),
                    reason = reason.trim().ifBlank { null }
                )
            ).onSuccess {
                _uiState.update { it.copy(updating = false, message = "Proposal submitted") }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun findPathTo(targetPersonId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, path = null) }
            repository.relationshipPath(spaceId, personId, targetPersonId)
                .onSuccess { path ->
                    _uiState.update { it.copy(updating = false, path = path) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(updating = false, error = error.message) }
                }
        }
    }

    fun updateProfile(input: PersonProfileEditInput) {
        val current = _uiState.value.person ?: return
        profileEditValidationError(input)?.let { message ->
            _uiState.update {
                it.copy(error = message)
            }
            return
        }
        val birthDate = input.birthDate.trim()
        val deceasedAt = input.deceasedAt.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            val profileChanged =
                input.fullName.trim() != current.fullName ||
                    input.nickName.trim() != current.nickName.orEmpty() ||
                    input.gender != current.gender.orEmpty() ||
                    birthDate != current.birthDate.orEmpty() ||
                    input.birthPlace.trim() != current.birthPlace.orEmpty() ||
                    input.deathPlace.trim() != current.deathPlace.orEmpty() ||
                    input.notes.trim() != current.notes.orEmpty()
            val normalizedDeceasedAt = deceasedAt.takeIf {
                input.lifeStatus == "DECEASED"
            }
            val lifeChanged =
                input.lifeStatus != current.lifeStatus ||
                    normalizedDeceasedAt.orEmpty() != current.deceasedAt.orEmpty()

            if (profileChanged) {
                repository.queueProfileUpdate(
                    spaceId = spaceId,
                    personId = personId,
                    fullName = input.fullName,
                    nickName = input.nickName,
                    gender = input.gender,
                    birthDate = birthDate,
                    birthPlace = input.birthPlace,
                    deathPlace = input.deathPlace,
                    notes = input.notes
                ).getOrElse { error ->
                    _uiState.update { it.copy(updating = false, error = error.message) }
                    return@launch
                }
            }
            if (lifeChanged) {
                repository.queueLifeStatusUpdate(
                    spaceId,
                    personId,
                    input.lifeStatus,
                    normalizedDeceasedAt
                ).getOrElse { error ->
                    _uiState.update { it.copy(updating = false, error = error.message) }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    updating = false,
                    message = if (profileChanged || lifeChanged) {
                        "Profil tersimpan di tablet dan masuk antrean sinkronisasi."
                    } else {
                        "Tidak ada perubahan profil."
                    }
                )
            }
        }
    }

    fun updateVisibility(visibility: String) {
        val person = _uiState.value.person ?: return
        if (!person.canManageVisibility || _uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.updatePersonVisibility(
                spaceId = spaceId,
                personId = personId,
                visibility = visibility,
                expectedVersion = person.version
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        updating = false,
                        message = "Privasi profil berhasil diperbarui."
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun keepLocalConflict(mutationId: String) {
        val mutation = _uiState.value.offlineMutations.firstOrNull { it.mutationId == mutationId } ?: return
        val serverVersion = mutation.conflictVersion ?: return
        viewModelScope.launch {
            repository.keepLocalConflict(mutation.mutationId, serverVersion)
        }
    }

    fun useServerConflict(mutationId: String) {
        val mutation = _uiState.value.offlineMutations.firstOrNull { it.mutationId == mutationId } ?: return
        viewModelScope.launch {
            repository.useServerConflict(mutation)
        }
    }

    fun retryFailedSync(mutationId: String) {
        val mutation = _uiState.value.offlineMutations.firstOrNull { it.mutationId == mutationId } ?: return
        viewModelScope.launch {
            repository.retryFailedMutation(mutation.mutationId, mutation.baseVersion)
        }
    }

    fun addParent(
        parentId: String,
        meta: String = "BIOLOGICAL",
        startDate: String? = null,
        endDate: String? = null,
        careContext: String? = null
    ) {
        addParentChild(
            parentId = parentId,
            childId = personId,
            meta = meta,
            startDate = startDate,
            endDate = endDate,
            careContext = careContext,
            success = "Parent relationship saved (${relationshipMetaMessage(meta)})"
        )
    }

    fun uploadProfilePhoto(uri: Uri) {
        val person = _uiState.value.person ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.uploadProfilePhoto(spaceId, personId, uri, person.fullName)
                .onSuccess {
                    refreshArchive()
                    _uiState.update {
                        it.copy(updating = false, message = "Foto profil berhasil diperbarui")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(updating = false, error = error.message) }
                }
        }
    }

    fun addChild(
        childId: String,
        meta: String = "BIOLOGICAL",
        startDate: String? = null,
        endDate: String? = null,
        careContext: String? = null
    ) {
        addParentChild(
            parentId = personId,
            childId = childId,
            meta = meta,
            startDate = startDate,
            endDate = endDate,
            careContext = careContext,
            success = "Child relationship saved (${relationshipMetaMessage(meta)})"
        )
    }

    private fun addParentChild(
        parentId: String,
        childId: String,
        meta: String,
        startDate: String?,
        endDate: String?,
        careContext: String?,
        success: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.queueParentChild(
                ParentChildRequest(
                    spaceId = spaceId,
                    parentId = parentId,
                    childId = childId,
                    meta = meta,
                    startDate = startDate,
                    endDate = endDate,
                    careContext = careContext
                ),
                focusPersonId = personId
            ).onSuccess {
                refreshRelations()
                _uiState.update { it.copy(updating = false, message = "$success locally; sync queued") }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun addSpouse(spouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.queueSpouse(
                CreateSpouseRequest(
                    spaceId = spaceId,
                    personAId = personId,
                    personBId = spouseId,
                    meta = "MARRIED",
                    startDate = null
                ),
                focusPersonId = personId
            ).onSuccess {
                refreshRelations()
                _uiState.update { it.copy(updating = false, message = "Spouse saved locally; sync queued") }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun deleteRelationship(relationshipId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.deleteRelationship(spaceId, relationshipId)
                .onSuccess {
                    refreshRelations()
                    _uiState.update {
                        it.copy(updating = false, message = "Relationship deleted")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(updating = false, error = error.message) }
                }
        }
    }

    fun loadDeletionImpact() {
        if (_uiState.value.loadingDeletionImpact) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingDeletionImpact = true,
                    deletionImpact = null,
                    error = null
                )
            }
            repository.personDeletionImpact(spaceId, personId)
                .onSuccess { impact ->
                    _uiState.update {
                        it.copy(
                            loadingDeletionImpact = false,
                            deletionImpact = impact
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingDeletionImpact = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun deletePerson() {
        val impact = _uiState.value.deletionImpact ?: return
        if (!impact.canDelete || _uiState.value.deletingPerson) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(deletingPerson = true, error = null, message = null)
            }
            repository.deletePerson(spaceId, personId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            deletingPerson = false,
                            deletionOutcome = PersonDeletionOutcome.DELETED
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(deletingPerson = false, error = error.message)
                    }
                    loadDeletionImpact()
                }
        }
    }

    fun requestPersonDeletion(reason: String) {
        if (_uiState.value.deletingPerson) return
        if (reason.isBlank()) {
            _uiState.update {
                it.copy(error = "Jelaskan alasan penghapusan agar dapat ditinjau pengelola.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(deletingPerson = true, error = null, message = null)
            }
            repository.requestPersonDeletion(spaceId, personId, reason)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            deletingPerson = false,
                            deletionOutcome = PersonDeletionOutcome.REQUESTED,
                            message = "Permintaan penghapusan dikirim untuk ditinjau."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(deletingPerson = false, error = error.message)
                    }
                }
        }
    }

    fun clearDeletionReview() {
        _uiState.update {
            it.copy(
                deletionImpact = null,
                loadingDeletionImpact = false,
                deletionOutcome = null
            )
        }
    }

    class Factory(
        private val spaceId: String,
        private val personId: String,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonDetailViewModel(spaceId, personId, repository) as T
        }
    }
}

internal fun profileEditValidationError(input: PersonProfileEditInput): String? = when {
    input.fullName.isBlank() || input.nickName.isBlank() ->
        "Nama lengkap dan nama panggilan wajib diisi."
    input.birthDate.isNotBlank() &&
        input.deceasedAt.isNotBlank() &&
        input.deceasedAt < input.birthDate ->
        "Tanggal meninggal tidak boleh sebelum tanggal lahir."
    else -> null
}

private fun relationshipMetaMessage(meta: String): String = when (meta) {
    "ADOPTIVE" -> "adoptive"
    "STEP" -> "step"
    "FOSTER" -> "foster care"
    "GUARDIAN" -> "family guardian"
    else -> "biological"
}
