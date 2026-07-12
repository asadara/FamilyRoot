package com.example.familytreeplatform.feature.persondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.UpdateLifeStatusRequest
import com.example.familytreeplatform.repository.PersonRepository
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
    val loadingRelations: Boolean = false,
    val claiming: Boolean = false,
    val updating: Boolean = false,
    val claim: ClaimResponse? = null,
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
            repository.listPersons(spaceId)
        }
        refreshRelations()
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

    fun updateLifeStatus(lifeStatus: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.updateLifeStatus(
                personId,
                UpdateLifeStatusRequest(spaceId = spaceId, lifeStatus = lifeStatus)
            ).onSuccess {
                repository.listPersons(spaceId)
                _uiState.update {
                    it.copy(updating = false, message = "Life status updated")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
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
            repository.addParentChild(
                ParentChildRequest(
                    spaceId = spaceId,
                    parentId = parentId,
                    childId = childId,
                    meta = "BIOLOGICAL"
                )
            ).onSuccess {
                refreshRelations()
                _uiState.update { it.copy(updating = false, message = success) }
            }.onFailure { error ->
                _uiState.update { it.copy(updating = false, error = error.message) }
            }
        }
    }

    fun addSpouse(spouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null, message = null) }
            repository.createSpouse(
                CreateSpouseRequest(
                    spaceId = spaceId,
                    personAId = personId,
                    personBId = spouseId,
                    meta = "MARRIED",
                    startDate = "2020-01-01"
                )
            ).onSuccess {
                refreshRelations()
                _uiState.update { it.copy(updating = false, message = "Spouse added") }
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
