package com.example.familytreeplatform.models

data class ParentChildRequest(
    val spaceId: String,
    val parentId: String,
    val childId: String,
    val meta: String
)
