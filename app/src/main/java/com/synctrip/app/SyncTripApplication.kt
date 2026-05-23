package com.synctrip.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class SyncTripApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 카카오 SDK 초기화 — secrets 플러그인이 local.properties의 KAKAO_NATIVE_KEY를
        // BuildConfig.KAKAO_NATIVE_KEY로 주입
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_KEY)
    }
}
