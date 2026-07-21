package com.example.familytreeplatform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiExceptionTest {
    @Test
    fun messageRetainsStatusAndCodeWithoutSecrets() {
        val error = ApiException(403, "FORBIDDEN", "Role VIEWER is not allowed")
        assertEquals(403, error.statusCode)
        assertEquals("FORBIDDEN", error.errorCode)
        assertTrue(error.message!!.contains("HTTP 403 FORBIDDEN"))
        assertTrue(error.message!!.contains("Role VIEWER"))
    }
}
