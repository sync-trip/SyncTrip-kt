package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.VoteRepository
import com.synctrip.app.network.VoteEvent
import com.synctrip.app.network.VoteStompClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoteUiState(
    // 아직 투표 안 한 장소 스택 (0번이 현재 카드)
    val pendingPlaces: List<VotePlaceResponse>  = emptyList(),
    val votedPlaces:   List<VotePlaceResponse>  = emptyList(),
    val myStatus:      VoteStatusResponse?       = null,
    val groupStatus:   GroupVoteStatusResponse?  = null,
    val isLoading: Boolean                       = false,
    val error: String?                           = null,
)

class VoteViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VoteUiState())
    val uiState: StateFlow<VoteUiState> = _uiState

    private var currentBandId: Long = -1L
    private var stompClient: VoteStompClient? = null

    /**
     * WebSocket STOMP 연결 — 투표 화면 진입 시 호출.
     * 다른 멤버가 투표할 때마다 groupStatus를 갱신해 실시간 진행 현황을 표시.
     */
    fun connectWebSocket(token: String, bandId: Long) {
        stompClient?.disconnect()
        stompClient = VoteStompClient(
            token       = token,
            bandId      = bandId,
            onEvent     = { _: VoteEvent ->
                // 다른 멤버 투표 이벤트 수신 → 그룹 진행 현황 서버에서 재조회
                refreshStatus()
            },
            onConnected = { refreshStatus() },
        )
        stompClient?.connect()
    }

    override fun onCleared() {
        stompClient?.disconnect()
        super.onCleared()
    }

    fun loadVotePlaces(bandId: Long) {
        currentBandId = bandId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { VoteRepository.getVotePlaces(bandId) }
                .onSuccess { all ->
                    // 아직 투표 안 한 것만 pendingPlaces로
                    val pending = all.filter { it.myVoteResult == null }
                    _uiState.value = _uiState.value.copy(pendingPlaces = pending, isLoading = false)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /** 스와이프 오른쪽 = 좋아요(1), 스와이프 왼쪽 = 싫어요(-1) */
    fun swipe(result: Int) {
        val place = _uiState.value.pendingPlaces.firstOrNull() ?: return
        viewModelScope.launch {
            runCatching { VoteRepository.submitVote(currentBandId, place.placeId, result) }
                .onSuccess {
                    val remaining = _uiState.value.pendingPlaces.drop(1)
                    _uiState.value = _uiState.value.copy(
                        pendingPlaces = remaining,
                        votedPlaces   = _uiState.value.votedPlaces + place,
                    )
                    // 모두 투표 완료 시 상태 조회
                    if (remaining.isEmpty()) refreshStatus()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    /**
     * BlindVotingScreen에서 특정 장소를 선택해 투표.
     * candidateId(placeId 문자열)로 pendingPlaces 중 해당 장소를 찾아 투표한다.
     */
    fun voteForPlace(placeId: Long, result: Int) {
        val place = _uiState.value.pendingPlaces.firstOrNull { it.placeId == placeId } ?: return
        viewModelScope.launch {
            runCatching { VoteRepository.submitVote(currentBandId, place.placeId, result) }
                .onSuccess {
                    val remaining = _uiState.value.pendingPlaces.filter { it.placeId != placeId }
                    _uiState.value = _uiState.value.copy(
                        pendingPlaces = remaining,
                        votedPlaces   = _uiState.value.votedPlaces + place,
                    )
                    if (remaining.isEmpty()) refreshStatus()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            runCatching {
                val my    = VoteRepository.getMyVoteStatus(currentBandId)
                val group = VoteRepository.getGroupVoteStatus(currentBandId)
                _uiState.value = _uiState.value.copy(myStatus = my, groupStatus = group)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
