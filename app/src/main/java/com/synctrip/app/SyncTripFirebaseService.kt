package com.synctrip.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synctrip.app.data.models.FcmTokenRequest
import com.synctrip.app.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging 서비스.
 * 구 앱(SyncTripFirebaseService)을 패키지만 바꿔 이식.
 *
 * - onNewToken: 로그인 상태면 서버에 토큰 등록 (POST /api/users/fcm-token)
 * - onMessageReceived: notification 필드에서 title/body 추출 후 시스템 알림 표시
 */
class SyncTripFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 로그인 상태(accessToken 있음)일 때만 서버 등록
        if (ApiClient.accessToken.isNullOrBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ApiClient.api.registerFcmToken(FcmTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body  = message.notification?.body  ?: return
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "synctrip_notifications"
        val manager   = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(channelId, "SyncTrip 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
