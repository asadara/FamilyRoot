package com.example.familytreeplatform.models

data class HistoryAccessRequestItem(
    val requestId: String,
    val spaceId: String,
    val userId: String,
    val status: String,
    val reviewedByUserId: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val userDisplayName: String? = null
)

data class MyHistoryAccessResponse(
    val request: HistoryAccessRequestItem? = null
)

data class RequestHistoryAccessBody(val spaceId: String)

data class ReviewHistoryAccessBody(
    val spaceId: String,
    val approved: Boolean
)

data class PagedChangeLog(
    val items: List<ChangeLog> = emptyList(),
    val nextCursor: String? = null
)
