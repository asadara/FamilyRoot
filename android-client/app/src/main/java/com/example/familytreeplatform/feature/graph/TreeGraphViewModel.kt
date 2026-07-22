package com.example.familytreeplatform.feature.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.PersonRequest
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TreeGraphUiState(
    val centerPersonId: String? = null,
    val selectedPersonId: String? = null,
    val inspectedPersonId: String? = null,
    val persons: List<PersonListItem> = emptyList(),
    val relations: RelationsResponse? = null,
    val relationships: List<ExportRelationship> = emptyList(),
    val explorationHistory: List<String> = emptyList(),
    val explorationBreadcrumbVisible: Boolean = false,
    val relationshipPath: RelationshipPathResponse? = null,
    val showRelationshipPathInGraph: Boolean = false,
    val loading: Boolean = false,
    val quickAddSaving: Boolean = false,
    val quickAddError: String? = null,
    val quickAddCompletedPersonId: String? = null,
    val connectionSaving: Boolean = false,
    val connectionMessage: String? = null,
    val connectionError: String? = null,
    val integritySavingRelationshipId: String? = null,
    val integrityMessage: String? = null,
    val integrityError: String? = null,
    val error: String? = null
)

enum class ExistingRelationKind { TARGET_PARENT, TARGET_CHILD, PARTNER }

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
        viewModelScope.launch {
            repository.observeRelationships(spaceId).collectLatest { relationships ->
                val graphRelationships = relationships.map(RelationItem::toExportRelationship)
                _uiState.update { state -> updateGraphRelationships(state, graphRelationships) }
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
            val relationships = relationshipsResult.getOrNull().orEmpty()
                .map(RelationItem::toExportRelationship)

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

    fun inspectPerson(personId: String) {
        _uiState.update { inspectGraphPerson(it, personId) }
    }

    fun beginQuickAdd() {
        _uiState.update {
            it.copy(quickAddError = null, quickAddCompletedPersonId = null)
        }
    }

    fun clearQuickAddFeedback() {
        _uiState.update {
            it.copy(quickAddError = null, quickAddCompletedPersonId = null)
        }
    }

    fun connectExistingPeople(
        sourcePersonId: String,
        targetPersonId: String,
        kind: ExistingRelationKind,
        meta: String
    ) {
        if (sourcePersonId == targetPersonId) return
        validateProposedRelationship(
            sourcePersonId = sourcePersonId,
            targetPersonId = targetPersonId,
            kind = kind,
            meta = meta,
            relationships = _uiState.value.relationships
        )?.let { validationMessage ->
            _uiState.update {
                it.copy(
                    connectionSaving = false,
                    connectionMessage = null,
                    connectionError = validationMessage
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionSaving = true,
                    connectionMessage = null,
                    connectionError = null
                )
            }
            val result = when (kind) {
                ExistingRelationKind.TARGET_PARENT -> repository.queueParentChild(
                    ParentChildRequest(spaceId, targetPersonId, sourcePersonId, meta),
                    focusPersonId = sourcePersonId
                )
                ExistingRelationKind.TARGET_CHILD -> repository.queueParentChild(
                    ParentChildRequest(spaceId, sourcePersonId, targetPersonId, meta),
                    focusPersonId = sourcePersonId
                )
                ExistingRelationKind.PARTNER -> repository.queueSpouse(
                    CreateSpouseRequest(
                        spaceId = spaceId,
                        personAId = sourcePersonId,
                        personBId = targetPersonId,
                        meta = meta,
                        startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    ),
                    focusPersonId = sourcePersonId
                )
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        connectionSaving = false,
                        connectionMessage = "Hubungan berhasil dibuat dan menunggu sinkronisasi.",
                        connectionError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        connectionSaving = false,
                        connectionMessage = null,
                        connectionError = error.message ?: "Hubungan belum berhasil dibuat."
                    )
                }
            }
        }
    }

    fun deleteRecommendedRelationship(relationshipId: String) {
        if (_uiState.value.integritySavingRelationshipId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    integritySavingRelationshipId = relationshipId,
                    integrityMessage = null,
                    integrityError = null
                )
            }
            repository.deleteRelationship(spaceId, relationshipId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            integritySavingRelationshipId = null,
                            integrityMessage = "Hubungan rancu berhasil dihapus.",
                            integrityError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            integritySavingRelationshipId = null,
                            integrityMessage = null,
                            integrityError = error.message
                                ?: "Hubungan rancu belum berhasil dihapus."
                        )
                    }
                }
        }
    }

    fun quickAddRelative(
        request: GraphQuickAddRequest,
        firstName: String,
        nickName: String,
        gender: String,
        startDate: String?
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    quickAddSaving = true,
                    quickAddError = null,
                    quickAddCompletedPersonId = null
                )
            }
            val created = repository.createPerson(
                PersonRequest(
                    spaceId = spaceId,
                    firstName = firstName.trim(),
                    nickName = nickName.trim(),
                    gender = gender
                )
            ).getOrElse { error ->
                _uiState.update {
                    it.copy(quickAddSaving = false, quickAddError = error.message)
                }
                return@launch
            }

            val relationResult = runCatching {
                when (request.kind) {
                    QuickRelationKind.PARENT -> repository.queueParentChild(
                        ParentChildRequest(
                            spaceId = spaceId,
                            parentId = created.personId,
                            childId = request.anchorPersonId,
                            meta = "BIOLOGICAL"
                        ),
                        focusPersonId = request.anchorPersonId
                    ).getOrThrow()
                    QuickRelationKind.CHILD -> {
                        repository.queueParentChild(
                            ParentChildRequest(
                                spaceId = spaceId,
                                parentId = request.anchorPersonId,
                                childId = created.personId,
                                meta = "BIOLOGICAL"
                            ),
                            focusPersonId = request.anchorPersonId
                        ).getOrThrow()
                        request.coParentId?.let { coParentId ->
                            repository.queueParentChild(
                                ParentChildRequest(
                                    spaceId = spaceId,
                                    parentId = coParentId,
                                    childId = created.personId,
                                    meta = "BIOLOGICAL"
                                ),
                                focusPersonId = request.anchorPersonId
                            ).getOrThrow()
                        }
                    }
                    QuickRelationKind.PARTNER -> repository.queueSpouse(
                        CreateSpouseRequest(
                            spaceId = spaceId,
                            personAId = request.anchorPersonId,
                            personBId = created.personId,
                            meta = "MARRIED",
                            startDate = requireNotNull(startDate) {
                                "Tanggal mulai hubungan diperlukan"
                            }
                        ),
                        focusPersonId = request.anchorPersonId
                    ).getOrThrow()
                }
            }

            relationResult.onSuccess {
                _uiState.update {
                    it.copy(
                        quickAddSaving = false,
                        quickAddCompletedPersonId = created.personId,
                        quickAddError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        quickAddSaving = false,
                        quickAddError = "Person tersimpan, tetapi hubungan belum berhasil: ${error.message}"
                    )
                }
            }
        }
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
): TreeGraphUiState {
    val availableIds = people.mapTo(mutableSetOf()) { it.personId }
    val center = state.centerPersonId
        ?.takeIf { it in availableIds }
        ?: chooseInitialCenterPersonId(people, state.relationships)
    return state.copy(
        persons = people,
        centerPersonId = center,
        relations = center?.let { state.relationships.toRelationsResponse(it) },
        explorationHistory = state.explorationHistory.ifEmpty {
            center?.let(::listOf).orEmpty()
        }
    )
}

internal fun updateGraphRelationships(
    state: TreeGraphUiState,
    relationships: List<ExportRelationship>
): TreeGraphUiState {
    val center = state.centerPersonId ?: chooseInitialCenterPersonId(state.persons, relationships)
    return state.copy(
        centerPersonId = center,
        relationships = relationships,
        relations = center?.let { centerId -> relationships.toRelationsResponse(centerId) },
        explorationHistory = state.explorationHistory.ifEmpty {
            center?.let(::listOf).orEmpty()
        }
    )
}

internal fun selectGraphPerson(state: TreeGraphUiState, personId: String): TreeGraphUiState =
    state.copy(
        selectedPersonId = personId,
        inspectedPersonId = null,
        relationshipPath = null,
        showRelationshipPathInGraph = false
    )

internal fun inspectGraphPerson(state: TreeGraphUiState, personId: String): TreeGraphUiState =
    state.copy(
        selectedPersonId = personId,
        inspectedPersonId = personId,
        relationshipPath = null,
        showRelationshipPathInGraph = false
    )

internal fun clearGraphSelection(state: TreeGraphUiState): TreeGraphUiState =
    state.copy(selectedPersonId = null, inspectedPersonId = null)

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
        inspectedPersonId = personId,
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

private fun RelationItem.toExportRelationship() = ExportRelationship(
    relationshipId = relationshipId,
    type = type,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    meta = meta,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

private fun List<ExportRelationship>.toRelationsResponse(personId: String) = RelationsResponse(
    personId = personId,
    parents = filter { it.type == "PARENT_CHILD" && it.toPersonId == personId }
        .map(ExportRelationship::toRelationItem),
    children = filter { it.type == "PARENT_CHILD" && it.fromPersonId == personId }
        .map(ExportRelationship::toRelationItem),
    spouses = filter {
        it.type == "SPOUSE" && (it.fromPersonId == personId || it.toPersonId == personId)
    }.map(ExportRelationship::toRelationItem)
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
