package com.synctrip.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.synctrip.app.network.ApiClient

class SyncTripApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // ApiClient에 앱 컨텍스트 주입 — 토큰 갱신 시 DataStore 저장에 필요
        ApiClient.init(this)
        // 카카오 SDK 초기화 — local.properties에 KAKAO_NATIVE_KEY 없으면 스킵 (UI 개발용)
        if (BuildConfig.KAKAO_NATIVE_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_KEY)
        }
    }
}
