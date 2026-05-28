package com.synctrip.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.kakao.sdk.common.KakaoSdk
import com.synctrip.app.network.ApiClient
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SyncTripApplication : Application() {

    // FCM 수신 시 앱 전체에 밴드 갱신 신호를 전파 — tripLobby에서 collect
    private val _bandRefreshFlow = MutableSharedFlow<Long>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val bandRefreshFlow: SharedFlow<Long> = _bandRefreshFlow.asSharedFlow()

    fun emitBandRefresh(bandId: Long) {
        _bandRefreshFlow.tryEmit(bandId)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // ApiClient에 앱 컨텍스트 주입 — 토큰 갱신 시 DataStore 저장에 필요
        ApiClient.init(this)
        // 카카오 SDK 초기화 — local.properties에 KAKAO_NATIVE_KEY 없으면 스킵 (UI 개발용)
        if (BuildConfig.KAKAO_NATIVE_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_KEY)
        }
        // 앱 시작 시 알림 채널 생성 — 백그라운드에서 Firebase가 채널을 찾지 못하는 문제 방지
        val manager = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_HIGH: 헤드업 알림(화면 상단 팝업)을 표시하기 위해 필요
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SyncTrip 알림",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        lateinit var instance: SyncTripApplication
        const val NOTIFICATION_CHANNEL_ID = "synctrip_notifications"
    }
}
