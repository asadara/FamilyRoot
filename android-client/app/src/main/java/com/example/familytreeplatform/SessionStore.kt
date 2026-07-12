package com.example.familytreeplatform

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SessionStore {
    private const val PREFS = "family_tree_session"
    private lateinit var preferences: SharedPreferences

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId
    private val _activeSpaceId = MutableStateFlow<String?>(null)
    val activeSpaceId: StateFlow<String?> = _activeSpaceId

    fun initialize(appContext: Context) {
        preferences = appContext.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Access tokens intentionally remain memory-only until encrypted storage
        // and refresh-token rotation are introduced.
        _accessToken.value = null
        _userId.value = null
        _activeSpaceId.value = null
    }

    fun saveSession(token: String, userId: String) {
        _accessToken.value = token
        _userId.value = userId
        _activeSpaceId.value = null
        preferences.edit().clear().apply()
    }

    fun selectSpace(spaceId: String) {
        _activeSpaceId.value = spaceId
        preferences.edit()
            .putString("active_space_id", spaceId).apply()
    }

    fun clear() {
        _accessToken.value = null
        _userId.value = null
        _activeSpaceId.value = null
        preferences.edit().clear().apply()
    }
}
