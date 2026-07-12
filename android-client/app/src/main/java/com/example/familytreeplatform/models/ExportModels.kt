package com.example.familytreeplatform.models

data class ExportPerson(
    val personId: String,
    val fullName: String,
    val lifeStatus: String,
    val deceasedAt: String? = null,
    val createdAt: String
)

data class ExportRelationship(
    val relationshipId: String,
    val type: String,
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String?,
    val startDate: String? = null,
    val endDate: String? = null,
    val createdAt: String
)

data class ExportClaim(
    val claimId: String,
    val status: String,
    val userId: String,
    val personId: String,
    val createdAt: String
)

data class ExportSpaceResponse(
    val spaceId: String,
    val persons: List<ExportPerson>,
    val relationships: List<ExportRelationship>,
    val claims: List<ExportClaim>
)
