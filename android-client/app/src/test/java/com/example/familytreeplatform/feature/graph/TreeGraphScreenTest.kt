package com.example.familytreeplatform.feature.graph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeGraphScreenTest {
    @Test
    fun serverAndExportErrorsAreActionableWithoutTechnicalDetails() {
        val server = graphErrorMessage("HTTP 503 INTERNAL_ERROR: database unavailable")
        val export = exportErrorMessage("Workspace tree is not ready")

        assertTrue(server.contains("Server sedang"))
        assertFalse(server.contains("database"))
        assertTrue(export.contains("Tunggu"))
    }
}
