package com.synctrip.app.network

import android.content.Context
import com.synctrip.app.BuildConfig
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.TokenRefreshRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    /** 현재 액세스 토큰 — 로그인/복구 시 저장 */
    var accessToken: String? = null

    /** 현재 리프레시 토큰 — 401 자동 갱신에 사용 */
    var refreshToken: String? = null

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** SyncTripApplication.onCreate()에서 한 번 호출 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 모든 요청에 Authorization: Bearer {token} 자동 첨부
    private val authInterceptor = Interceptor { chain ->
        val request = accessToken?.let { token ->
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } ?: chain.request()
        chain.proceed(request)
    }

    // 401 응답 시 리프레시 토큰으로 갱신 후 원래 요청 재시도
    private val tokenAuthenticator = Authenticator { _, response ->
        // 이미 재시도한 요청이면 포기 (무한루프 방지)
        if (response.responseCount >= 2) return@Authenticator null
        val currentRefresh = refreshToken ?: return@Authenticator null

        val newTokens = runCatching {
            runBlocking { refreshApi.refreshToken(TokenRefreshRequest(currentRefresh)) }
        }.getOrNull() ?: return@Authenticator null

        accessToken = newTokens.accessToken
        refreshToken = newTokens.refreshToken

        // DataStore에도 비동기 저장 (fire-and-forget)
        appContext?.let { ctx ->
            scope.launch {
                TokenDataStore.save(ctx, newTokens.accessToken, newTokens.refreshToken, newTokens.userId)
            }
        }

        response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    // 응답 체인 깊이 — 재시도 횟수 계산용
    private val Response.responseCount: Int
        get() = generateSequence(this) { it.priorResponse }.count()

    // 릴리스 빌드에서는 NONE으로 자동 전환
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    // 토큰 갱신 전용 클라이언트 — 인터셉터 없음 (무한루프 방지)
    private val refreshOkHttpClient = OkHttpClient.Builder().build()

    private val refreshApi: SyncTripApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(refreshOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncTripApiService::class.java)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: SyncTripApiService = retrofit.create(SyncTripApiService::class.java)

    /** WebSocket 접속 URL — BASE_URL의 스킴을 ws(s)로 변환 후 /ws 경로 추가 */
    val wsUrl: String = BuildConfig.BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/') + "/ws"

    /** STOMP CONNECT 프레임의 host 헤더용 호스트명 */
    val wsHost: String = BuildConfig.BASE_URL
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}
