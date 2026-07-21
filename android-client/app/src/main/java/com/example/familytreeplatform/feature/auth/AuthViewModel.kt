package com.example.familytreeplatform.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val emailLooksValid: Boolean
        get() = email.trim().let { value ->
            value.contains('@') && value.substringAfter('@').contains('.')
        }
    val passwordLooksValid: Boolean
        get() = password.length >= 10
    val canSubmit: Boolean
        get() = emailLooksValid && passwordLooksValid &&
            (mode == AuthMode.SIGN_IN || displayName.isNotBlank()) && !loading
}

class AuthViewModel(private val repository: PersonRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) = _uiState.update { it.copy(mode = mode, error = null) }
    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value, error = null) }
    fun setEmail(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        authenticate(
            email = state.email.trim(),
            password = state.password,
            displayName = state.displayName.trim().takeIf { state.mode == AuthMode.REGISTER }
        )
    }

    fun signInDemo(email: String) {
        _uiState.update {
            it.copy(mode = AuthMode.SIGN_IN, email = email, password = "Test123456!", error = null)
        }
        authenticate(email, "Test123456!", displayName = null)
    }

    private fun authenticate(email: String, password: String, displayName: String?) {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = if (displayName != null) repository.register(email, displayName, password)
            else repository.login(email, password)
            result.onSuccess {
                repository.acceptSession(it)
                repository.resumeOfflineSync()
                _uiState.update { state -> state.copy(loading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    class Factory(private val repository: PersonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }
}
