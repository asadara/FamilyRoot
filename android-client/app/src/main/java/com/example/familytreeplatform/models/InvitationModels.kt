package com.example.familytreeplatform.models

data class SpaceInvitation(
    val inviteId: String,
    val role: String,
    val status: String,
    val createdBy: String,
    val createdByName: String,
    val acceptedBy: String? = null,
    val acceptedByName: String? = null,
    val createdAt: String,
    val expiresAt: String,
    val acceptedAt: String? = null,
    val revokedAt: String? = null,
    val maskedTargetEmail: String? = null
)

data class RevokeInvitationResponse(
    val spaceId: String,
    val inviteId: String,
    val status: String,
    val revokedAt: String
)
