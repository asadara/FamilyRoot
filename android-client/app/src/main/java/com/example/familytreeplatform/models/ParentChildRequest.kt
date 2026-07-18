package com.example.familytreeplatform.models

data class ParentChildRequest(
    val spaceId: String,
    val parentId: String,
    val childId: String,
    val meta: String,
    val clientMutationId: String = java.util.UUID.randomUUID().toString()
)
