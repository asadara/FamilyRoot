package com.example.familytreeplatform.models

data class UpdateProfileRequest(
    val spaceId: String,
    val birthPlace: String? = null,
    val notes: String? = null,
    val fullName: String? = null,
    val nickName: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathPlace: String? = null,
    val expectedVersion: Int,
    val clientMutationId: String
)
