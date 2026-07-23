package com.example.familytreeplatform.models

data class CreateSpouseRequest(
    val spaceId: String,
    val personAId: String,
    val personBId: String,
    val meta: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val clientMutationId: String = java.util.UUID.randomUUID().toString()
)
