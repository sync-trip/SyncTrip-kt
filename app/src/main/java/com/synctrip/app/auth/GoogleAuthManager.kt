package com.synctrip.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
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
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context = context, request = request)

        // Credential Manager는 GoogleIdTokenCredential 또는 CustomCredential로 반환됨
        return when (val credential = result.credential) {
            is GoogleIdTokenCredential ->
                credential.idToken

            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                        ?: throw IllegalStateException("Google ID Token이 null입니다")
                } else {
                    throw IllegalStateException("지원하지 않는 credential 타입: ${credential.type}")
                }
            }

            else -> throw IllegalStateException("알 수 없는 credential 타입")
        }
    }
}
