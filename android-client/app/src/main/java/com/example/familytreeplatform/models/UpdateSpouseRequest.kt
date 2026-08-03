package com.example.familytreeplatform.models

data class UpdateSpouseRequest(
    val spaceId: String,
    val meta: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val clientMutationId: String = java.util.UUID.randomUUID().toString()
)
