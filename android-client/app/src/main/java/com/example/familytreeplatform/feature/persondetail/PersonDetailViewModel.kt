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
import com.example.familytreeplatform.repository.PersonRepository
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
    val path: RelationshipPathResponse? = null,
    val loadingRelations: Boolean = false,
    val claiming: Boolean = false,
    val updating: Boolean = false,
    val claim: ClaimResponse? = null,
    val offlineMutations: List<OfflineMutationEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null
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
            repository.listPersons(spaceId)
        }
        refreshRelations()
        refreshArchive()
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
            _uiState.update {
                it.copy(
                    sources = sourcesResult.getOrNull().orEmpty(),
                    media = mediaResult.getOrNull().orEmpty(),
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
                _uiState.update { it.copy(updating = false, message = "Source added") }
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

    fun updateLifeStatus(lifeStatus: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.queueLifeStatusUpdate(spaceId, personId, lifeStatus).onSuccess {
                _uiState.update {
                    it.copy(updating = false, message = "Saved locally; sync queued")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun updateProfile(birthPlace: String, notes: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.queueProfileUpdate(spaceId, personId, birthPlace, notes).onSuccess {
                _uiState.update {
                    it.copy(updating = false, message = "Profile saved locally; sync queued")
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

    fun addParent(parentId: String) {
        addParentChild(parentId = parentId, childId = personId, success = "Parent added")
    }

    fun addChild(childId: String) {
        addParentChild(parentId = personId, childId = childId, success = "Child added")
    }

    private fun addParentChild(parentId: String, childId: String, success: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.queueParentChild(
                ParentChildRequest(
                    spaceId = spaceId,
                    parentId = parentId,
                    childId = childId,
                    meta = "BIOLOGICAL"
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
                    startDate = "2020-01-01"
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
