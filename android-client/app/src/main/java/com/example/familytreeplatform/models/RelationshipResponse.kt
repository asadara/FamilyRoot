package com.example.familytreeplatform.models

data class RelationshipResponse(
    val relationshipId: String,
    val type: String,
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String
)
