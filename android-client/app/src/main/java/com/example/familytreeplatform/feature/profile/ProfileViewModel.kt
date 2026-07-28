package com.example.familytreeplatform.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.AccountDeletionImpact
import com.example.familytreeplatform.models.UserNotificationItem
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val deletionImpact: AccountDeletionImpact? = null,
    val loadingDeletionImpact: Boolean = false,
    val pendingMutationCount: Int = 0,
    val showDeleteConfirmation: Boolean = false,
    val deleteConfirmation: String = "",
    val deletingAccount: Boolean = false,
    val notifications: List<UserNotificationItem> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val loadingNotifications: Boolean = false,
    val markingNotificationsRead: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refreshNotifications()
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingNotifications = true, error = null) }
            repository.listNotifications()
                .onSuccess { history ->
                    _uiState.update {
                        it.copy(
                            notifications = history.items,
                            unreadNotificationCount = history.unreadCount,
                            loadingNotifications = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loadingNotifications = false, error = error.message)
                    }
                }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
                .onSuccess { updated ->
                    _uiState.update {
                        val hadUnread = it.notifications.any { item ->
                            item.notificationId == notificationId && item.readAt == null
                        }
                        it.copy(
                            notifications = it.notifications.map { item ->
                                if (item.notificationId == notificationId) updated else item
                            },
                            unreadNotificationCount =
                                (it.unreadNotificationCount - if (hadUnread) 1 else 0)
                                    .coerceAtLeast(0)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun markAllNotificationsRead() {
        if (_uiState.value.unreadNotificationCount == 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(markingNotificationsRead = true, error = null) }
            repository.markAllNotificationsRead()
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            notifications = it.notifications.map { item ->
                                if (item.readAt == null) item.copy(readAt = result.readAt) else item
                            },
                            unreadNotificationCount = 0,
                            markingNotificationsRead = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            markingNotificationsRead = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun requestAccountDeletion() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingDeletionImpact = true,
                    deleteConfirmation = "",
                    error = null
                )
            }
            val pendingCount = repository.pendingMutationCountAcrossSpaces()
            repository.accountDeletionImpact()
                .onSuccess { impact ->
                    _uiState.update {
                        it.copy(
                            deletionImpact = impact,
                            loadingDeletionImpact = false,
                            pendingMutationCount = pendingCount,
                            showDeleteConfirmation = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingDeletionImpact = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun setDeleteConfirmation(value: String) {
        _uiState.update { it.copy(deleteConfirmation = value, error = null) }
    }

    fun cancelAccountDeletion() {
        _uiState.update {
            it.copy(showDeleteConfirmation = false, deleteConfirmation = "")
        }
    }

    fun confirmAccountDeletion() {
        val state = _uiState.value
        if (!state.deletionImpact.orEmptyCanDelete()) return
        if (state.pendingMutationCount > 0) {
            _uiState.update {
                it.copy(
                    showDeleteConfirmation = false,
                    error = "Selesaikan seluruh perubahan yang belum tersinkron sebelum menghapus akun."
                )
            }
            return
        }
        if (state.deleteConfirmation != "HAPUS AKUN") {
            _uiState.update {
                it.copy(error = "Ketik HAPUS AKUN secara lengkap untuk melanjutkan.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showDeleteConfirmation = false,
                    deletingAccount = true,
                    error = null
                )
            }
            repository.deleteAccount()
                .onFailure { error ->
                    _uiState.update {
                        it.copy(deletingAccount = false, error = error.message)
                    }
                }
        }
    }

    class Factory(
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(repository) as T
        }
    }
}

private fun AccountDeletionImpact?.orEmptyCanDelete(): Boolean =
    this?.canDeleteAccount == true
