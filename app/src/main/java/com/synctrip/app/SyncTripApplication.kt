package com.synctrip.app

import android.app.Application
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
    }

    companion object {
        lateinit var instance: SyncTripApplication
    }
}
