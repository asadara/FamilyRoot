package com.example.familytreeplatform.models

data class SpouseResponse(
    val relationshipId: String,
    val type: String = "SPOUSE",
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String,
    val startDate: String,
    val endDate: String?,
    val createdAt: String? = null
)
