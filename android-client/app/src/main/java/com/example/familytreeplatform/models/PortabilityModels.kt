package com.example.familytreeplatform.models

data class GedcomDocument(
    val fileName: String,
    val mimeType: String,
    val content: String
)

data class ImportGedcomRequest(
    val spaceId: String,
    val content: String
)

data class RestoreBackupRequest(
    val spaceId: String,
    val backup: Map<String, Any?>
)

data class ImportSummary(
    val personCount: Int,
    val relationshipCount: Int
)

data class PortableDocument(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val kind: String
)
