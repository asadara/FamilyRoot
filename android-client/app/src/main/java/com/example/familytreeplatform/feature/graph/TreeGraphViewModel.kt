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
            val relationshipsResult = repository.listRelationships(spaceId)
            val people = peopleResult.getOrNull().orEmpty()
            val relationships = relationshipsResult.getOrNull().orEmpty().map { relationship ->
                ExportRelationship(
                    relationshipId = relationship.relationshipId,
                    type = relationship.type,
                    fromPersonId = relationship.fromPersonId,
                    toPersonId = relationship.toPersonId,
                    meta = relationship.meta,
                    startDate = relationship.startDate,
                    endDate = relationship.endDate,
                    createdAt = relationship.createdAt
                )
            }

            if (peopleResult.isFailure) {
                _uiState.update {
                    it.copy(loading = false, error = peopleResult.exceptionOrNull()?.message)
                }
                return@launch
            }
            if (relationshipsResult.isFailure) {
                _uiState.update {
                    it.copy(loading = false, error = relationshipsResult.exceptionOrNull()?.message)
                }
                return@launch
            }

            val center = _uiState.value.centerPersonId
                ?: chooseInitialCenterPersonId(people, relationships)

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

/**
 * Opens the graph on the most connected family member so disconnected records
 * (for example a duplicate candidate) do not look like an empty family tree.
 */
internal fun chooseInitialCenterPersonId(
    persons: List<PersonListItem>,
    relationships: List<ExportRelationship>
): String? {
    if (persons.isEmpty()) return null

    val availableIds = persons.mapTo(mutableSetOf()) { it.personId }
    val connectionCount = mutableMapOf<String, Int>()
    val hasParent = mutableSetOf<String>()
    val hasChild = mutableSetOf<String>()
    relationships.forEach { relationship ->
        if (relationship.fromPersonId in availableIds && relationship.toPersonId in availableIds) {
            connectionCount[relationship.fromPersonId] =
                connectionCount.getOrDefault(relationship.fromPersonId, 0) + 1
            connectionCount[relationship.toPersonId] =
                connectionCount.getOrDefault(relationship.toPersonId, 0) + 1
            if (relationship.type == "PARENT_CHILD") {
                hasChild += relationship.fromPersonId
                hasParent += relationship.toPersonId
            }
        }
    }

    return persons.maxWithOrNull(
        compareBy<PersonListItem> {
            val generationBridgeBonus = if (it.personId in hasParent && it.personId in hasChild) 100 else 0
            generationBridgeBonus + connectionCount.getOrDefault(it.personId, 0)
        }
            .thenByDescending { it.createdAt }
    )?.personId
}
