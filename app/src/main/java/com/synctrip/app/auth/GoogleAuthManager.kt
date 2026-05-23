package com.synctrip.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.synctrip.app.BuildConfig

object GoogleAuthManager {

    /**
     * Google 로그인 수행 후 ID 토큰 반환.
     * Credential Manager API 사용 (Android 14+ 네이티브 지원).
     *
     * @throws GetCredentialException 로그인 취소 또는 실패 시
     */
    suspend fun login(context: Context): String {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)   // 기존 계정 + 새 계정 모두 표시
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)             // 자동 선택 비활성화
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return googleIdTokenCredential.idToken
    }
}
