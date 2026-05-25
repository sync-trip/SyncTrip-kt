package com.synctrip.app.auth

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object KakaoAuthManager {

    /**
     * 카카오 로그인 수행 후 액세스 토큰 반환.
     * 카카오톡 앱 로그인 → 실패 시 카카오 계정 웹 로그인으로 폴백.
     */
    suspend fun login(context: Context): String = suspendCoroutine { cont ->
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                cont.resumeWithException(error)
            } else if (token != null) {
                cont.resume(token.accessToken)
            } else {
                cont.resumeWithException(IllegalStateException("카카오 로그인 실패: 토큰 없음"))
            }
        }

        // 카카오톡 설치 여부 확인 후 분기
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    // 사용자가 카카오톡 로그인을 취소한 경우 웹 로그인 시도 안 함
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        cont.resumeWithException(error)
                        return@loginWithKakaoTalk
                    }
                    // 카카오톡 로그인 실패 → 카카오 계정 웹 로그인 폴백
                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                } else if (token != null) {
                    cont.resume(token.accessToken)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
        }
    }

    /** 카카오 로그아웃 (로컬 토큰 삭제) */
    suspend fun logout(): Unit = suspendCoroutine { cont ->
        UserApiClient.instance.logout { error ->
            // 서버 에러가 있어도 로컬 토큰은 삭제됨 — 무조건 성공 처리
            cont.resume(Unit)
        }
    }
}
