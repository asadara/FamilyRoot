package com.example.familytreeplatform.feature.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreeGraphUiState(
    val centerPersonId: String? = null,
    val persons: List<PersonListItem> = emptyList(),
    val relations: RelationsResponse? = null,
    val relationships: List<ExportRelationship> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class TreeGraphViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TreeGraphUiState())
    val uiState: StateFlow<TreeGraphUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val peopleResult = repository.listPersons(spaceId)
            val exportResult = repository.exportSpace(spaceId)
            val people = peopleResult.getOrNull().orEmpty()
            val relationships = exportResult.getOrNull()?.relationships.orEmpty()
            val center = _uiState.value.centerPersonId ?: people.firstOrNull()?.personId

            if (peopleResult.isFailure) {
                _uiState.update {
                    it.copy(loading = false, error = peopleResult.exceptionOrNull()?.message)
                }
                return@launch
            }
            if (exportResult.isFailure) {
                _uiState.update {
                    it.copy(loading = false, error = exportResult.exceptionOrNull()?.message)
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    centerPersonId = center,
                    persons = people,
                    relationships = relationships,
                    loading = false
                )
            }
            center?.let { selectPerson(it) }
        }
    }

    fun selectPerson(personId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(centerPersonId = personId, loading = true, error = null) }
            repository.getRelations(spaceId, personId)
                .onSuccess { relations ->
                    _uiState.update { it.copy(relations = relations, loading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    class Factory(
        private val spaceId: String,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TreeGraphViewModel(spaceId, repository) as T
        }
    }
}
