package com.example.familytreeplatform.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonPrivacyCacheTest {
    @Test
    fun `sensitive offline payload is only reapplied to a full-access cache row`() {
        assertTrue(canReapplySensitiveMutation("FULL"))
        assertFalse(canReapplySensitiveMutation("STRUCTURE"))
        assertFalse(canReapplySensitiveMutation("MINIMUM"))
        assertFalse(canReapplySensitiveMutation(null))
    }
}
