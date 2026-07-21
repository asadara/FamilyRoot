package com.example.familytreeplatform.feature.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInCancelledException : Exception()

class GoogleSignInUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class GoogleCredentialClient(context: Context) {
    private val context = context
    private val credentialManager = CredentialManager.create(context)

    suspend fun getIdToken(serverClientId: String): String {
        if (serverClientId.isBlank()) {
            throw GoogleSignInUnavailableException("GOOGLE_NOT_CONFIGURED")
        }
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = try {
            credentialManager.getCredential(context, request).credential
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInCancelledException()
        } catch (error: GetCredentialException) {
            throw GoogleSignInUnavailableException("GOOGLE_CREDENTIAL_UNAVAILABLE", error)
        }

        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleSignInUnavailableException("GOOGLE_CREDENTIAL_INVALID")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: Exception) {
            throw GoogleSignInUnavailableException("GOOGLE_CREDENTIAL_INVALID", error)
        }
    }

    suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
