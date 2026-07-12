package com.example.familytreeplatform.models

data class ClaimResponse(
    val claimId: String,
    val status: String,
    val spaceId: String,
    val userId: String,
    val personId: String
)
