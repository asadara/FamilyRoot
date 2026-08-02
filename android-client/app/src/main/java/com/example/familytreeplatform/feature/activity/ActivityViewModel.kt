package com.example.familytreeplatform.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.HistoryAccessRequestItem
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUiState(
    val loading: Boolean = false,
    val logs: List<ChangeLog> = emptyList(),
    val historyAccessRequest: HistoryAccessRequestItem? = null,
    val requestingHistoryAccess: Boolean = false,
    val showingFullHistory: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val canOpenFullHistory: Boolean = false,
    val error: String? = null
)

class ActivityViewModel(
    private val spaceId: String,
    private val privilegedHistoryAccess: Boolean,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState(loading = true))
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refreshHistoryAccess() {
        if (privilegedHistoryAccess) {
            _uiState.update { it.copy(canOpenFullHistory = true) }
            return
        }
        viewModelScope.launch {
            repository.myHistoryAccessRequest(spaceId)
                .onSuccess { request ->
                    _uiState.update {
                        it.copy(
                            historyAccessRequest = request,
                            canOpenFullHistory = request?.status == "APPROVED"
                        )
                    }
                }
        }
    }

    fun requestFullHistoryAccess() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(requestingHistoryAccess = true, error = null)
            }
            repository.requestFullHistoryAccess(spaceId)
                .onSuccess { request ->
                    _uiState.update {
                        it.copy(
                            historyAccessRequest = request,
                            requestingHistoryAccess = false,
                            canOpenFullHistory = request.status == "APPROVED"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(requestingHistoryAccess = false, error = error.message)
                    }
                }
        }
    }

    fun openFullHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.listFullHistory(spaceId)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            logs = page.items,
                            showingFullHistory = true,
                            nextCursor = page.nextCursor
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    fun loadMoreHistory() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.loadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true, error = null) }
            repository.listFullHistory(spaceId, before = cursor)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            logs = it.logs + page.items,
                            nextCursor = page.nextCursor,
                            loadingMore = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loadingMore = false, error = error.message)
                    }
                }
        }
    }

    fun showRecentHistory() {
        _uiState.update {
            it.copy(showingFullHistory = false, nextCursor = null)
        }
        refresh()
    }

    fun refresh() {
        refreshHistoryAccess()
        if (_uiState.value.showingFullHistory) {
            openFullHistory()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.resumeOfflineSync()
            repository.listChanges(spaceId)
                .onSuccess { logs -> _uiState.update { it.copy(loading = false, logs = logs) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message) } }
        }
    }

    class Factory(
        private val spaceId: String,
        private val privilegedHistoryAccess: Boolean,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(spaceId, privilegedHistoryAccess, repository) as T
        }
    }
}
