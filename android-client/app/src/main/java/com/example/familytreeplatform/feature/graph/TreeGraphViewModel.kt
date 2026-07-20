package com.example.familytreeplatform.feature.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationshipPathEdge
import com.example.familytreeplatform.models.RelationshipPathPerson
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreeGraphUiState(
    val centerPersonId: String? = null,
    val selectedPersonId: String? = null,
    val persons: List<PersonListItem> = emptyList(),
    val relations: RelationsResponse? = null,
    val relationships: List<ExportRelationship> = emptyList(),
    val explorationHistory: List<String> = emptyList(),
    val explorationBreadcrumbVisible: Boolean = false,
    val relationshipPath: RelationshipPathResponse? = null,
    val showRelationshipPathInGraph: Boolean = false,
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
        viewModelScope.launch {
            repository.observePersons(spaceId).collectLatest { people ->
                _uiState.update { state -> updateGraphPersons(state, people) }
            }
        }
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

            val centerRelations = center?.let { centerId ->
                RelationsResponse(
                    personId = centerId,
                    parents = relationships
                        .filter { it.type == "PARENT_CHILD" && it.toPersonId == centerId }
                        .map(ExportRelationship::toRelationItem),
                    children = relationships
                        .filter { it.type == "PARENT_CHILD" && it.fromPersonId == centerId }
                        .map(ExportRelationship::toRelationItem),
                    spouses = relationships
                        .filter {
                            it.type == "SPOUSE" &&
                                (it.fromPersonId == centerId || it.toPersonId == centerId)
                        }
                        .map(ExportRelationship::toRelationItem)
                )
            }

            _uiState.update {
                it.copy(
                    centerPersonId = center,
                    selectedPersonId = it.selectedPersonId,
                    explorationHistory = it.explorationHistory.ifEmpty {
                        center?.let(::listOf).orEmpty()
                    },
                    persons = people,
                    relationships = relationships,
                    relations = centerRelations,
                    loading = false
                )
            }
        }
    }

    fun selectPerson(personId: String) {
        _uiState.update { selectGraphPerson(it, personId) }
    }

    fun clearSelection() {
        _uiState.update(::clearGraphSelection)
    }

    fun selectSearchResult(personId: String) {
        _uiState.update { selectGraphSearchResult(it, personId) }
    }

    fun showRelationshipPathInGraph() {
        _uiState.update { state ->
            if (state.relationshipPath?.found == true) {
                state.copy(showRelationshipPathInGraph = true)
            } else {
                state
            }
        }
    }

    fun hideExplorationBreadcrumb() {
        _uiState.update { it.copy(explorationBreadcrumbVisible = false) }
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

internal fun updateGraphPersons(
    state: TreeGraphUiState,
    people: List<PersonListItem>
): TreeGraphUiState = state.copy(persons = people)

internal fun selectGraphPerson(state: TreeGraphUiState, personId: String): TreeGraphUiState =
    state.copy(
        selectedPersonId = personId,
        relationshipPath = null,
        showRelationshipPathInGraph = false
    )

internal fun clearGraphSelection(state: TreeGraphUiState): TreeGraphUiState =
    state.copy(selectedPersonId = null)

internal fun selectGraphSearchResult(
    state: TreeGraphUiState,
    personId: String
): TreeGraphUiState {
    val originId = state.explorationHistory.lastOrNull() ?: state.centerPersonId ?: personId
    val path = findShortestRelationshipPath(
        fromPersonId = originId,
        toPersonId = personId,
        persons = state.persons,
        relationships = state.relationships
    )
    val history = if (state.explorationHistory.lastOrNull() == personId) {
        state.explorationHistory
    } else {
        state.explorationHistory + personId
    }
    return state.copy(
        selectedPersonId = personId,
        explorationHistory = history,
        explorationBreadcrumbVisible = true,
        relationshipPath = path,
        showRelationshipPathInGraph = false
    )
}

internal fun findShortestRelationshipPath(
    fromPersonId: String,
    toPersonId: String,
    persons: List<PersonListItem>,
    relationships: List<ExportRelationship>
): RelationshipPathResponse {
    val personById = persons.associateBy { it.personId }
    if (fromPersonId !in personById || toPersonId !in personById) {
        return RelationshipPathResponse(found = false)
    }
    if (fromPersonId == toPersonId) {
        return RelationshipPathResponse(
            found = true,
            people = listOf(RelationshipPathPerson(fromPersonId, personById.getValue(fromPersonId).fullName))
        )
    }

    data class TraversalEdge(
        val nextPersonId: String,
        val relationship: ExportRelationship,
        val direction: String
    )

    val adjacency = mutableMapOf<String, MutableList<TraversalEdge>>()
    relationships.forEach { relationship ->
        adjacency.getOrPut(relationship.fromPersonId) { mutableListOf() }.add(
            TraversalEdge(relationship.toPersonId, relationship, "FORWARD")
        )
        adjacency.getOrPut(relationship.toPersonId) { mutableListOf() }.add(
            TraversalEdge(relationship.fromPersonId, relationship, "REVERSE")
        )
    }

    val queue = ArrayDeque<String>()
    val visited = mutableSetOf(fromPersonId)
    val previous = mutableMapOf<String, Pair<String, TraversalEdge>>()
    queue.add(fromPersonId)
    while (queue.isNotEmpty() && toPersonId !in visited) {
        val current = queue.removeFirst()
        adjacency[current].orEmpty().forEach { edge ->
            if (visited.add(edge.nextPersonId)) {
                previous[edge.nextPersonId] = current to edge
                queue.add(edge.nextPersonId)
            }
        }
    }
    if (toPersonId !in visited) return RelationshipPathResponse(found = false)

    val pathPersonIds = mutableListOf(toPersonId)
    val pathEdges = mutableListOf<RelationshipPathEdge>()
    var cursor = toPersonId
    while (cursor != fromPersonId) {
        val (priorPersonId, traversal) = previous.getValue(cursor)
        val relationship = traversal.relationship
        pathEdges.add(
            0,
            RelationshipPathEdge(
                relationshipId = relationship.relationshipId,
                type = relationship.type,
                fromPersonId = relationship.fromPersonId,
                toPersonId = relationship.toPersonId,
                meta = relationship.meta,
                direction = traversal.direction
            )
        )
        pathPersonIds.add(0, priorPersonId)
        cursor = priorPersonId
    }
    return RelationshipPathResponse(
        found = true,
        people = pathPersonIds.map { id ->
            RelationshipPathPerson(id, personById[id]?.fullName ?: id)
        },
        edges = pathEdges
    )
}

private fun ExportRelationship.toRelationItem() = RelationItem(
    relationshipId = relationshipId,
    type = type,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    meta = meta,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

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
