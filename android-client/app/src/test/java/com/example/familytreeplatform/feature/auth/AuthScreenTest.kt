package com.example.familytreeplatform.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthScreenTest {
    @Test
    fun registrationRequiresDisplayName() {
        assertFalse(AuthUiState(mode = AuthMode.REGISTER, email = "a@b.test", password = "secret").canSubmit)
        assertTrue(
            AuthUiState(
                mode = AuthMode.REGISTER,
                displayName = "Budi",
                email = "a@b.test",
                password = "secret"
            ).canSubmit
        )
    }

    @Test
    fun technicalAuthenticationErrorsAreHidden() {
        assertTrue(authErrorMessage("Failed to connect to 127.0.0.1").contains("Server belum dapat dijangkau"))
        assertTrue(authErrorMessage("HTTP 401 invalid credentials").contains("tidak sesuai"))
        assertTrue(authErrorMessage("HTTP 400 VALIDATION_ERROR: password is too short").contains("Periksa"))
        assertTrue(authErrorMessage("HTTP 500 INTERNAL_ERROR: database unavailable").contains("Server sedang"))
    }
}
