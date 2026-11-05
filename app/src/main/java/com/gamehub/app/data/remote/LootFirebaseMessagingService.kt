package com.gamehub.app.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gamehub.app.MainActivity
import com.gamehub.app.R
import com.gamehub.app.LootApplication
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LootFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val masked = if (token.length > 6) token.take(6) + "..." else "***"
        AppLogger.debug("FCM token refreshed (prefix: $masked)")
        serviceScope.launch {
            try {
                val appContainer = (application as LootApplication).container
                appContainer.notificationRepository.registerFcmToken(token)
                AppLogger.debug("FCM token synced successfully on refresh")
            } catch (e: Exception) {
                AppLogger.warn("Failed to sync FCM token on refresh: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val gameId = message.data["gameId"]?.toIntOrNull()

        showNotification(title, body, gameId)
    }

    private fun showNotification(title: String, body: String, gameId: Int?) {
        val channelId = "gamehub_notifications"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GameHub Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Game releases, wishlist updates, and recommendations"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (gameId != null) {
                putExtra("nav_route", "game/$gameId")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.loot_appicon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
