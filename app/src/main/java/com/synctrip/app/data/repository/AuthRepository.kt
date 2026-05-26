package com.synctrip.app.data.repository

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.FcmTokenRequest
import com.synctrip.app.data.models.LoginResponse
import com.synctrip.app.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AuthRepository {

    /** 카카오 액세스 토큰으로 서버 로그인 → JWT 저장 */
    suspend fun kakaoLogin(context: Context, kakaoAccessToken: String): LoginResponse {
        val response = ApiClient.api.kakaoLogin(
            com.synctrip.app.data.models.KakaoLoginRequest(kakaoAccessToken)
        )
        persistTokens(context, response)
        return response
    }

    /** Google ID 토큰으로 서버 로그인 → JWT 저장 */
    suspend fun googleLogin(context: Context, idToken: String): LoginResponse {
        val response = ApiClient.api.googleLogin(
            com.synctrip.app.data.models.GoogleLoginRequest(idToken)
        )
        persistTokens(context, response)
        return response
    }

    /** 로그아웃 — 서버 호출 후 로컬 토큰 삭제 */
    suspend fun logout(context: Context) {
        runCatching { ApiClient.api.logout() }
        ApiClient.accessToken  = null
        ApiClient.refreshToken = null
        TokenDataStore.clear(context)
    }

    /** 회원탈퇴 — 서버 Soft Delete 후 로컬 토큰 삭제 */
    suspend fun withdraw(context: Context) {
        ApiClient.api.withdraw()
        ApiClient.accessToken  = null
        ApiClient.refreshToken = null
        TokenDataStore.clear(context)
    }

    /** 앱 시작 시 DataStore에서 토큰 복구 */
    fun restoreToken(context: Context): kotlinx.coroutines.flow.Flow<String?> =
        TokenDataStore.accessTokenFlow(context)

    // JWT를 ApiClient(메모리)와 DataStore(영구) 양쪽에 저장, FCM 토큰 등록
    private suspend fun persistTokens(context: Context, response: LoginResponse) {
        ApiClient.accessToken  = response.accessToken
        ApiClient.refreshToken = response.refreshToken
        TokenDataStore.save(context, response.accessToken, response.refreshToken, response.userId)
        // 로그인 직후 FCM 토큰 등록 — onNewToken은 설치 시점에만 호출되므로 여기서 직접 등록
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ApiClient.api.registerFcmToken(FcmTokenRequest(fcmToken)) }
            }
        }
    }
}
