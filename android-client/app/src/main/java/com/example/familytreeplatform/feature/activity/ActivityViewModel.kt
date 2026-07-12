package com.example.familytreeplatform.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUiState(
    val loading: Boolean = false,
    val logs: List<ChangeLog> = emptyList(),
    val error: String? = null
)

class ActivityViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState(loading = true))
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.listChanges(spaceId)
                .onSuccess { logs -> _uiState.update { it.copy(loading = false, logs = logs) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message) } }
        }
    }

    class Factory(
        private val spaceId: String,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(spaceId, repository) as T
        }
    }
}
