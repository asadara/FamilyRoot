package com.example.familytreeplatform.models

data class ChangeLog(
    val changeId: String,
    val createdAt: String,
    val actorUserId: String,
    val entityType: String,
    val operation: String,
    val note: String?
)
