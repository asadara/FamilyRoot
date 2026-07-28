package com.example.familytreeplatform.models

data class UserNotificationItem(
    val notificationId: String,
    val spaceId: String? = null,
    val kind: String,
    val code: String,
    val title: String,
    val message: String,
    val readAt: String? = null,
    val createdAt: String
)

data class NotificationHistoryResponse(
    val items: List<UserNotificationItem> = emptyList(),
    val unreadCount: Int = 0
)

data class MarkAllNotificationsReadResponse(
    val updated: Int,
    val readAt: String
)
