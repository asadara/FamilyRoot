package com.example.familytreeplatform

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SessionStore {
    private const val PREFS = "family_tree_session"
    private lateinit var preferences: SharedPreferences
    private lateinit var secureStorage: SecureSessionStorage
    private var storedRefreshToken: String? = null

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId
    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName: StateFlow<String?> = _userDisplayName
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail
    private val _activeSpaceId = MutableStateFlow<String?>(null)
    val activeSpaceId: StateFlow<String?> = _activeSpaceId
    private val _activeSpaceName = MutableStateFlow<String?>(null)
    val activeSpaceName: StateFlow<String?> = _activeSpaceName
    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring
    private val _hasPersistedSession = MutableStateFlow(false)
    val hasPersistedSession: StateFlow<Boolean> = _hasPersistedSession

    fun initialize(appContext: Context) {
        preferences = appContext.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        secureStorage = SecureSessionStorage(appContext)
        _accessToken.value = null
        val restored = runCatching {
            storedRefreshToken = secureStorage.get("refresh_token")
            _userId.value = secureStorage.get("user_id")
            _userDisplayName.value = secureStorage.get("user_display_name")
            _userEmail.value = secureStorage.get("user_email")
            _activeSpaceId.value = preferences.getString("active_space_id", null)
            _activeSpaceName.value = preferences.getString("active_space_name", null)
            !storedRefreshToken.isNullOrBlank() && !_userId.value.isNullOrBlank()
        }.getOrElse {
            secureStorage.clear()
            preferences.edit().clear().commit()
            storedRefreshToken = null
            _userId.value = null
            _userDisplayName.value = null
            _userEmail.value = null
            _activeSpaceId.value = null
            _activeSpaceName.value = null
            false
        }
        _restoring.value = restored
        _hasPersistedSession.value = restored
    }

    @Synchronized
    fun saveSession(
        token: String,
        refreshToken: String,
        userId: String,
        displayName: String?,
        email: String? = null
    ) {
        secureStorage.put("refresh_token", refreshToken)
        secureStorage.put("user_id", userId)
        displayName?.let { secureStorage.put("user_display_name", it) }
        email?.let { secureStorage.put("user_email", it) }
        storedRefreshToken = refreshToken
        _accessToken.value = token
        _userId.value = userId
        _userDisplayName.value = displayName
        _userEmail.value = email
        _activeSpaceId.value = null
        _activeSpaceName.value = null
        _restoring.value = false
        _hasPersistedSession.value = true
        preferences.edit().remove("active_space_id").apply()
    }

    @Synchronized
    fun updateSession(
        token: String,
        refreshToken: String,
        userId: String,
        displayName: String?,
        email: String? = null
    ) {
        secureStorage.put("refresh_token", refreshToken)
        secureStorage.put("user_id", userId)
        displayName?.let { secureStorage.put("user_display_name", it) }
        email?.let { secureStorage.put("user_email", it) }
        storedRefreshToken = refreshToken
        _accessToken.value = token
        _userId.value = userId
        _userDisplayName.value = displayName
        _userEmail.value = email ?: _userEmail.value
        _restoring.value = false
        _hasPersistedSession.value = true
    }

    @Synchronized
    fun refreshToken(): String? = storedRefreshToken

    fun finishRestore() {
        _restoring.value = false
    }

    fun selectSpace(spaceId: String, spaceName: String? = null) {
        _activeSpaceId.value = spaceId
        _activeSpaceName.value = spaceName
        preferences.edit()
            .putString("active_space_id", spaceId)
            .putString("active_space_name", spaceName)
            .apply()
    }

    fun updateActiveSpaceName(spaceName: String) {
        _activeSpaceName.value = spaceName
        preferences.edit().putString("active_space_name", spaceName).apply()
    }

    @Synchronized
    fun clear() {
        _accessToken.value = null
        _userId.value = null
        _userDisplayName.value = null
        _userEmail.value = null
        _activeSpaceId.value = null
        _activeSpaceName.value = null
        storedRefreshToken = null
        _restoring.value = false
        _hasPersistedSession.value = false
        secureStorage.clear()
        preferences.edit().clear().commit()
    }
}
