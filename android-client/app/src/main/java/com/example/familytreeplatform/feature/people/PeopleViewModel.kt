package com.example.familytreeplatform.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.PersonRequest
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeopleUiState(
    val people: List<PersonListItem> = emptyList(),
    val query: String = "",
    val lifeStatusFilter: String = "ALL",
    val refreshing: Boolean = false,
    val creating: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null
) {
    val filteredPeople: List<PersonListItem>
        get() = people.filter { person ->
            val matchesQuery = query.isBlank() || person.fullName.contains(query, true)
            val matchesStatus = lifeStatusFilter == "ALL" || person.lifeStatus == lifeStatusFilter
            matchesQuery && matchesStatus
        }
}

class PeopleViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val lifeStatusFilter = MutableStateFlow("ALL")
    private val operation = MutableStateFlow(PeopleUiState())

    val uiState: StateFlow<PeopleUiState> = combine(
        repository.observePersons(spaceId), query, lifeStatusFilter, operation
    ) { people, currentQuery, currentStatusFilter, status ->
        status.copy(
            people = people,
            query = currentQuery,
            lifeStatusFilter = currentStatusFilter
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    init { refresh() }

    fun setQuery(value: String) { query.value = value }

    fun setLifeStatusFilter(value: String) { lifeStatusFilter.value = value }

    fun refresh() = viewModelScope.launch {
        operation.value = operation.value.copy(refreshing = true, error = null)
        repository.listPersons(spaceId)
            .onSuccess { operation.value = operation.value.copy(refreshing = false, offline = false) }
            .onFailure { error ->
                operation.value = operation.value.copy(refreshing = false, offline = true, error = error.message)
            }
    }

    fun create(firstName: String, nickName: String, gender: String) = viewModelScope.launch {
        operation.value = operation.value.copy(creating = true, error = null)
        repository.createPerson(PersonRequest(
            spaceId = spaceId,
            firstName = firstName.trim(),
            nickName = nickName.trim(),
            gender = gender
        )).onSuccess { refresh() }
            .onFailure { operation.value = operation.value.copy(creating = false, error = it.message) }
        operation.value = operation.value.copy(creating = false)
    }

    class Factory(private val spaceId: String, private val repository: PersonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PeopleViewModel(spaceId, repository) as T
    }
}
