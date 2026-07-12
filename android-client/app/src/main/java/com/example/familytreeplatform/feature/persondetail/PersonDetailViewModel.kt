package com.example.familytreeplatform.feature.persondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
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
    val loadingRelations: Boolean = false,
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
