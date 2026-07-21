package com.example.familytreeplatform.feature.support

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportScreensTest {
    @Test
    fun `beta version has a user friendly label`() {
        assertEquals("Beta 0.1.0", applicationVersionLabel("0.1.0-beta"))
        assertEquals("Beta 2.0", applicationVersionLabel("2.0_BETA"))
    }

    @Test
    fun `official version is not mislabeled beta`() {
        assertEquals("1.0.0", applicationVersionLabel("1.0.0"))
    }
}
