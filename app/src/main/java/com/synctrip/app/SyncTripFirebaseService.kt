package com.synctrip.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synctrip.app.core.FcmEventBus
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
        // notification 필드 또는 data 필드에서 title/body 추출
        val title  = message.notification?.title ?: message.data["title"] ?: return
        val body   = message.notification?.body  ?: message.data["body"]  ?: return
        val type   = message.data["type"]
        val bandId = message.data["bandId"]

        // 인앱 Toast — 앱 프로세스가 살아 있을 때 항상 표시
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "$title\n$body", Toast.LENGTH_LONG).show()
        }
        FcmEventBus.send(title, body)

        // 시스템 알림 — 화면 꺼짐/백그라운드 대비
        showNotification(title, body, type, bandId)
    }

    private fun showNotification(title: String, body: String, type: String?, bandId: String?) {
        val channelId      = "synctrip_notifications"
        val notificationId = System.currentTimeMillis().toInt()
        val manager        = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(channelId, "SyncTrip 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )

        // bandId, type을 Intent extras에 담아 탭 시 해당 화면으로 이동 가능하게
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            bandId?.let { putExtra("bandId", it) }
            type?.let   { putExtra("fcmType", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }
}
