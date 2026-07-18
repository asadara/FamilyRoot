package com.example.familytreeplatform.models

data class UpdateLifeStatusRequest(
    val spaceId: String,
    val lifeStatus: String,
    val deceasedAt: String? = null,
    val expectedVersion: Int,
    val clientMutationId: String
)
