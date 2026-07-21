package com.example.familytreeplatform.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.models.InvitationPreview
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpaceSelectionUiState(
    val spaces: List<FamilySpace> = emptyList(),
    val newSpaceName: String = "",
    val invitationCode: String = "",
    val invitationPreview: InvitationPreview? = null,
    val loadingSpaces: Boolean = true,
    val processing: Boolean = false,
    val invitationError: String? = null,
    val error: String? = null
)

class SpaceSelectionViewModel(private val repository: PersonRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceSelectionUiState())
    val uiState: StateFlow<SpaceSelectionUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingSpaces = true, error = null) }
            repository.listSpaces()
                .onSuccess { spaces -> _uiState.update { it.copy(spaces = spaces, loadingSpaces = false) } }
                .onFailure { error -> _uiState.update { it.copy(loadingSpaces = false, error = error.message) } }
        }
    }

    fun setNewSpaceName(value: String) = _uiState.update { it.copy(newSpaceName = value, error = null) }

    fun setInvitationCode(value: String) = _uiState.update {
        it.copy(invitationCode = value, invitationPreview = null, invitationError = null)
    }

    fun selectSpace(space: FamilySpace) =
        SessionStore.selectSpace(space.spaceId, space.name, space.role)

    fun previewInvitation() {
        val token = normalizeInvitationToken(_uiState.value.invitationCode)
        if (token.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, invitationPreview = null, invitationError = null) }
            repository.previewInvitation(token)
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(processing = false, invitationCode = token, invitationPreview = preview)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(processing = false, invitationError = error.message) }
                }
        }
    }

    fun acceptInvitation() {
        val token = normalizeInvitationToken(_uiState.value.invitationCode)
        if (token.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, invitationError = null) }
            repository.acceptInvitation(token)
                .onSuccess { SessionStore.selectSpace(it.spaceId, it.name, it.role) }
                .onFailure { error ->
                    _uiState.update { it.copy(processing = false, invitationError = error.message) }
                }
        }
    }

    fun createSpace() {
        val name = _uiState.value.newSpaceName.trim()
        if (name.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, error = null) }
            repository.createSpace(name)
                .onSuccess { SessionStore.selectSpace(it.spaceId, it.name, it.role) }
                .onFailure { error -> _uiState.update { it.copy(processing = false, error = error.message) } }
        }
    }

    fun signOut() = viewModelScope.launch { repository.logout() }

    class Factory(private val repository: PersonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SpaceSelectionViewModel(repository) as T
    }
}

internal fun normalizeInvitationToken(raw: String): String {
    val trimmed = raw.trim()
    return Regex("[A-Za-z0-9_-]{12,128}")
        .findAll(trimmed)
        .lastOrNull()
        ?.value
        ?: trimmed
}
