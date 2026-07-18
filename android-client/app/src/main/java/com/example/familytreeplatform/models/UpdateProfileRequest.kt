package com.example.familytreeplatform.models

data class UpdateProfileRequest(
    val spaceId: String,
    val birthPlace: String,
    val notes: String,
    val expectedVersion: Int,
    val clientMutationId: String
)
