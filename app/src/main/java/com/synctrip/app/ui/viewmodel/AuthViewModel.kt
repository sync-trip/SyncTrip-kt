package com.synctrip.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.auth.GoogleAuthManager
import com.synctrip.app.auth.KakaoAuthManager
import com.synctrip.app.data.models.LoginResponse
import com.synctrip.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle    : AuthUiState
    data object Loading : AuthUiState
    data class  Success(val response: LoginResponse) : AuthUiState
    data class  Error(val message: String)           : AuthUiState
}

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    /** 카카오 SDK 로그인 → 서버 JWT 발급 → DataStore 저장 */
    fun kakaoLogin(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = runCatching {
                val kakaoToken = KakaoAuthManager.login(context)
                AuthRepository.kakaoLogin(context, kakaoToken)
            }.fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "카카오 로그인 실패") },
            )
        }
    }

    /** Google Credential Manager 로그인 → 서버 JWT 발급 → DataStore 저장 */
    fun googleLogin(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = runCatching {
                val idToken = GoogleAuthManager.login(context)
                AuthRepository.googleLogin(context, idToken)
            }.fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "구글 로그인 실패") },
            )
        }
    }

    fun logout(context: Context) {
        viewModelScope.launch {
            runCatching {
                KakaoAuthManager.logout()
                AuthRepository.logout(context)
            }
            _uiState.value = AuthUiState.Idle
        }
    }

    /** 회원탈퇴 — 성공 시 onSuccess, 실패 시 onFailure("탈퇴 처리 실패...") 호출 */
    fun withdraw(context: Context, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { AuthRepository.withdraw(context) }
                .onSuccess {
                    _uiState.value = AuthUiState.Idle
                    onSuccess()
                }
                .onFailure { onFailure("탈퇴 처리 실패. 다시 시도해주세요.") }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
