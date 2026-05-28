package com.synctrip.app

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
        android.util.Log.d("FCM_TOKEN", "토큰: $token")
        // 로그인 상태(accessToken 있음)일 때만 서버 등록
        if (ApiClient.accessToken.isNullOrBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ApiClient.api.registerFcmToken(FcmTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title  = message.notification?.title ?: "SyncTrip"
        val body   = message.notification?.body  ?: return
        val bandId = message.data["bandId"]
        val type   = message.data["type"]
        showNotification(title, body, bandId, type)

        // data 페이로드의 bandId가 있으면 해당 밴드 화면을 즉시 갱신
        val bandIdLong = bandId?.toLongOrNull()
        if (bandIdLong != null) {
            SyncTripApplication.instance.emitBandRefresh(bandIdLong)
        }
    }

    private fun showNotification(title: String, body: String, bandId: String?, type: String?) {
        val channelId = SyncTripApplication.NOTIFICATION_CHANNEL_ID
        val manager   = getSystemService(NotificationManager::class.java)

        // 알림 탭 시 이동할 화면: VOTE_STARTED → 투표창, 나머지 → 밴드 로비
        // key를 FCM SDK가 백그라운드 자동 표시 시 넣어주는 data 페이로드 key와 동일하게 유지해야
        // 포그라운드/백그라운드 양쪽에서 extractNotificationRoute()가 동일하게 읽을 수 있음
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            bandId?.let { putExtra("bandId", it) }
            type?.let   { putExtra("type", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
