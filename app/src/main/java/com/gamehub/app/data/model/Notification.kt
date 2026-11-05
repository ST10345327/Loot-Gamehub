package com.gamehub.app.data.model

data class GameNotification(
    val id: Int,
    val userId: Int,
    val type: String,
    val title: String,
    val body: String,
    val gameId: Int?,
    val isRead: Boolean,
    val createdAt: String
)
