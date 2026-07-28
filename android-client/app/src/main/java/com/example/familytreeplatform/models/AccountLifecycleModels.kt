package com.example.familytreeplatform.models

data class AccountSpaceImpact(
    val spaceId: String,
    val name: String,
    val role: String? = null
)

data class AccountDeletionImpact(
    val canDeleteAccount: Boolean,
    val blockers: List<String>,
    val membershipCount: Int,
    val claimCount: Int,
    val activeSessionCount: Int,
    val activeInvitationCount: Int,
    val ownedSpaces: List<AccountSpaceImpact>,
    val exportableSpaces: List<AccountSpaceImpact>
)

data class DeleteAccountRequest(val confirmation: String)

data class DeleteAccountResponse(
    val userId: String,
    val deleted: Boolean
)
