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
        it.copy(invitationCode = value, invitationPreview = null, error = null)
    }

    fun selectSpace(space: FamilySpace) = SessionStore.selectSpace(space.spaceId, space.name)

    fun previewInvitation() {
        val token = _uiState.value.invitationCode.trim()
        if (token.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, invitationPreview = null, error = null) }
            repository.previewInvitation(token)
                .onSuccess { preview -> _uiState.update { it.copy(processing = false, invitationPreview = preview) } }
                .onFailure { error -> _uiState.update { it.copy(processing = false, error = error.message) } }
        }
    }

    fun acceptInvitation() {
        val token = _uiState.value.invitationCode.trim()
        if (token.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, error = null) }
            repository.acceptInvitation(token)
                .onSuccess { SessionStore.selectSpace(it.spaceId, it.name) }
                .onFailure { error -> _uiState.update { it.copy(processing = false, error = error.message) } }
        }
    }

    fun createSpace() {
        val name = _uiState.value.newSpaceName.trim()
        if (name.isBlank() || _uiState.value.processing) return
        viewModelScope.launch {
            _uiState.update { it.copy(processing = true, error = null) }
            repository.createSpace(name)
                .onSuccess { SessionStore.selectSpace(it.spaceId, it.name) }
                .onFailure { error -> _uiState.update { it.copy(processing = false, error = error.message) } }
        }
    }

    fun signOut() = viewModelScope.launch { repository.logout() }

    class Factory(private val repository: PersonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SpaceSelectionViewModel(repository) as T
    }
}
