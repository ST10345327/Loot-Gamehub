package com.gamehub.app.data.remote.dto

data class NotificationDto(
    val id: Int?,
    val userId: Int?,
    val type: String?,
    val title: String?,
    val body: String?,
    val gameId: Int?,
    val isRead: Boolean?,
    val createdAt: String?
)

data class NotificationListDto(
    val items: List<NotificationDto>?
)

data class UnreadCountDto(
    val count: Int?
)

data class FcmTokenRequest(
    val token: String
)
