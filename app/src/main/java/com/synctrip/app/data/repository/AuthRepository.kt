package com.synctrip.app.data.repository

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.FcmTokenRequest
import com.synctrip.app.data.models.LoginResponse
import com.synctrip.app.network.ApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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

    // JWT를 ApiClient(메모리)와 DataStore(영구) 양쪽에 저장 + FCM 토큰 서버 등록
    private suspend fun persistTokens(context: Context, response: LoginResponse) {
        ApiClient.accessToken  = response.accessToken
        ApiClient.refreshToken = response.refreshToken   // 401 자동 갱신에 사용
        TokenDataStore.save(context, response.accessToken, response.refreshToken, response.userId)
        // onNewToken은 토큰이 변경될 때만 호출됨 — 로그인마다 현재 토큰을 직접 가져와 등록
        registerCurrentFcmToken()
    }

    /**
     * 현재 기기의 FCM 토큰을 가져와 서버에 등록.
     * 실패해도 로그인 자체를 막지 않도록 runCatching으로 감쌈.
     */
    private suspend fun registerCurrentFcmToken() {
        runCatching {
            val token = suspendCancellableCoroutine<String> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener  { cont.resume(it) }
                    .addOnFailureListener  { cont.resumeWithException(it) }
            }
            ApiClient.api.registerFcmToken(FcmTokenRequest(token))
        }
    }
}
