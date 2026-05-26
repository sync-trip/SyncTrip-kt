package com.synctrip.app.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 앱 컨텍스트에 DataStore 인스턴스를 싱글톤으로 연결
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

object TokenDataStore {

    private val KEY_ACCESS           = stringPreferencesKey("access_token")
    private val KEY_REFRESH          = stringPreferencesKey("refresh_token")
    private val KEY_USER_ID          = longPreferencesKey("user_id")
    // 마지막으로 여권 화면을 연 시각 (epoch millis). 기본 0 = 한 번도 방문한 적 없음.
    private val KEY_PASSPORT_VISITED = longPreferencesKey("passport_last_visited_ms")

    fun accessTokenFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[KEY_ACCESS] }

    fun refreshTokenFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[KEY_REFRESH] }

    fun userIdFlow(context: Context): Flow<Long?> =
        context.dataStore.data.map { it[KEY_USER_ID] }

    /** 로그인 성공 후 토큰 저장 */
    suspend fun save(context: Context, accessToken: String, refreshToken: String, userId: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS]  = accessToken
            prefs[KEY_REFRESH] = refreshToken
            prefs[KEY_USER_ID] = userId
        }
    }

    /** 로그아웃 시 토큰 삭제 */
    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    fun passportLastVisitedFlow(context: Context): Flow<Long> =
        context.dataStore.data.map { it[KEY_PASSPORT_VISITED] ?: 0L }

    /** 여권 화면 진입 시 현재 시각(epoch millis)으로 갱신 */
    suspend fun markPassportVisited(context: Context) {
        context.dataStore.edit { it[KEY_PASSPORT_VISITED] = System.currentTimeMillis() }
    }
}
