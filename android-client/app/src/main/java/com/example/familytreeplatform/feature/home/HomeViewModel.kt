package com.example.familytreeplatform.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val people: List<PersonListItem> = emptyList(),
    val recentActivity: List<ChangeLog> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePersons(spaceId).collect { people ->
                _uiState.update { it.copy(people = people) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val peopleResult = repository.listPersons(spaceId)
            val activityResult = repository.listChanges(spaceId, limit = 6)
            _uiState.update { state ->
                state.copy(
                    people = peopleResult.getOrElse { state.people },
                    recentActivity = activityResult.getOrElse { state.recentActivity },
                    loading = false,
                    error = when {
                        peopleResult.isFailure && state.people.isEmpty() -> peopleResult.exceptionOrNull()?.message
                        activityResult.isFailure && state.recentActivity.isEmpty() -> activityResult.exceptionOrNull()?.message
                        else -> null
                    }
                )
            }
        }
    }

    class Factory(
        private val spaceId: String,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(spaceId, repository) as T
    }
}
