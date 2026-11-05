package com.gamehub.app.data.repository

import com.gamehub.app.data.model.GameNotification
import com.gamehub.app.data.remote.ApiCaller
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.remote.LootApi
import com.gamehub.app.data.remote.dto.FcmTokenRequest
import com.gamehub.app.data.remote.dto.NotificationDto

interface NotificationRepository {
    suspend fun getNotifications(): ApiResult<List<GameNotification>>
    suspend fun getUnreadCount(): ApiResult<Int>
    suspend fun markAsRead(id: Int): ApiResult<Unit>
    suspend fun markAllAsRead(): ApiResult<Unit>
    suspend fun registerFcmToken(token: String): ApiResult<Unit>
    suspend fun unregisterFcmToken(token: String?): ApiResult<Unit>
}

class NotificationRepositoryImpl(
    private val api: LootApi,
    private val apiCaller: ApiCaller
) : NotificationRepository {

    override suspend fun getNotifications(): ApiResult<List<GameNotification>> =
        apiCaller.call {
            api.getNotifications().items.orEmpty().mapNotNull { it.toGameNotification() }
        }

    override suspend fun getUnreadCount(): ApiResult<Int> =
        apiCaller.call {
            api.getUnreadCount().count ?: 0
        }

    override suspend fun markAsRead(id: Int): ApiResult<Unit> =
        apiCaller.call {
            api.markNotificationRead(id)
            Unit
        }

    override suspend fun markAllAsRead(): ApiResult<Unit> =
        apiCaller.call {
            api.markAllNotificationsRead()
            Unit
        }

    override suspend fun registerFcmToken(token: String): ApiResult<Unit> =
        apiCaller.call {
            api.registerFcmToken(FcmTokenRequest(token))
            Unit
        }

    override suspend fun unregisterFcmToken(token: String?): ApiResult<Unit> =
        apiCaller.call {
            api.unregisterFcmToken(token?.let { FcmTokenRequest(it) })
            Unit
        }

    private fun NotificationDto.toGameNotification(): GameNotification? {
        val safeId = id ?: return null
        val safeUser = userId ?: 0
        val safeType = type ?: "general"
        val safeTitle = title ?: ""
        val safeBody = body ?: ""
        return GameNotification(
            id = safeId,
            userId = safeUser,
            type = safeType,
            title = safeTitle,
            body = safeBody,
            gameId = gameId,
            isRead = isRead ?: false,
            createdAt = createdAt.orEmpty()
        )
    }
}
