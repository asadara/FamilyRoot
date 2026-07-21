package com.example.familytreeplatform.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.repository.PersonRepository
import com.example.familytreeplatform.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, REGISTER }
enum class AuthMethod { PASSWORD, GOOGLE }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val loadingMethod: AuthMethod? = null,
    val error: String? = null
) {
    val loading: Boolean
        get() = loadingMethod != null
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

    fun beginGoogleSignIn(): Boolean {
        if (_uiState.value.loading) return false
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _uiState.update { it.copy(error = "GOOGLE_NOT_CONFIGURED") }
            return false
        }
        _uiState.update { it.copy(loadingMethod = AuthMethod.GOOGLE, error = null) }
        return true
    }

    fun completeGoogleSignIn(credentialResult: Result<String>) {
        val idToken = credentialResult.getOrElse { error ->
            _uiState.update {
                it.copy(
                    loadingMethod = null,
                    error = if (error is GoogleSignInCancelledException) null else error.message
                )
            }
            return
        }
        viewModelScope.launch {
            val result = repository.loginWithGoogle(idToken)
            result.onSuccess {
                repository.acceptSession(it)
                repository.resumeOfflineSync()
                _uiState.update { state -> state.copy(loadingMethod = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(loadingMethod = null, error = error.message) }
            }
        }
    }

    fun cancelPendingGoogleSignIn() {
        _uiState.update {
            if (it.loadingMethod == AuthMethod.GOOGLE) it.copy(loadingMethod = null) else it
        }
    }

    private fun authenticate(email: String, password: String, displayName: String?) {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMethod = AuthMethod.PASSWORD, error = null) }
            val result = if (displayName != null) repository.register(email, displayName, password)
            else repository.login(email, password)
            result.onSuccess {
                repository.acceptSession(it)
                repository.resumeOfflineSync()
                _uiState.update { state -> state.copy(loadingMethod = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(loadingMethod = null, error = error.message) }
            }
        }
    }

    class Factory(private val repository: PersonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }
}
