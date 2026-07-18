package com.example.familytreeplatform.models

data class RelationItem(
    val relationshipId: String,
    val type: String,
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String?,
    val createdAt: String,
    val startDate: String? = null,
    val endDate: String? = null
)
