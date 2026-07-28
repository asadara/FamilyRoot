package com.example.familytreeplatform.models

data class SpaceLifecycleImpact(
    val spaceId: String,
    val name: String,
    val status: String,
    val canArchive: Boolean,
    val canRestore: Boolean,
    val canDelete: Boolean,
    val personCount: Int,
    val relationshipCount: Int,
    val memberCount: Int,
    val claimCount: Int,
    val mediaCount: Int,
    val sourceCount: Int,
    val pendingProposalCount: Int,
    val activeInvitationCount: Int
)

data class DeleteSpaceRequest(
    val confirmation: String,
    val acknowledgeExport: Boolean
)

data class DeleteSpaceResponse(
    val spaceId: String,
    val deleted: Boolean,
    val deletedAt: String
)
