package com.example.familytreeplatform.models

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val displayName: String, val password: String)
data class AuthUser(val userId: String, val email: String?, val displayName: String)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshExpiresIn: Int,
    val user: AuthUser
)
data class RefreshTokenRequest(val refreshToken: String)
data class FamilySpace(
    val spaceId: String,
    val name: String,
    val createdBy: String,
    val role: String? = null
)
data class CreateSpaceRequest(val name: String)
data class CreateInvitationRequest(
    val spaceId: String,
    val role: String,
    val expiresInDays: Int
)
data class AcceptInvitationRequest(val token: String)
data class InvitationPreview(
    val spaceId: String,
    val spaceName: String,
    val role: String,
    val expiresAt: String
)
data class CreatedInvitation(
    val inviteId: String,
    val token: String,
    val role: String,
    val spaceId: String,
    val spaceName: String,
    val expiresAt: String
)
