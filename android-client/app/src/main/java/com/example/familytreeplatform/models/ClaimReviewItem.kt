package com.example.familytreeplatform.models

data class ClaimReviewItem(
    val claimId: String,
    val spaceId: String,
    val userId: String,
    val personId: String,
    val status: String,
    val requestedAt: String,
    val personName: String? = null,
    val memberRole: String? = null,
    val confirmationCount: Int = 0,
    val required: Int = 2,
    val verificationBasis: String = "COLLECTIVE"
)

data class MyClaimResponse(
    val claim: ClaimReviewItem? = null
)
