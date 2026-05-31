package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.VoteRepository
import com.synctrip.app.util.toUserMessage
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
    val voteResults:   List<VotePlaceResult>     = emptyList(),
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
                // 다른 멤버 투표 이벤트 수신 → 그룹 진행 현황만 재조회 (내 상태는 로컬에서 관리)
                refreshGroupStatus()
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
                    // 이미 투표된 장소 (자동 LIKE 포함)
                    val voted    = all.filter { it.myVoteResult != null }
                    // 내가 담았지만 아직 자동 LIKE 미제출 — 카드에 표시하지 않고 백그라운드 제출
                    val autoLike = all.filter { it.myBookmark && it.myVoteResult == null }
                    // 실제 투표 카드에 표시할 장소 — 내 북마크 제외
                    val pending  = all.filter { !it.myBookmark && it.myVoteResult == null }

                    _uiState.value = _uiState.value.copy(
                        pendingPlaces = pending,
                        votedPlaces   = voted,
                        isLoading     = false,
                    )

                    // 내가 담은 장소들에 자동 LIKE 순차 제출 (백엔드에서 result=0으로 저장)
                    // result=1로 보내도 백엔드가 myBookmark 확인 후 0으로 고정함
                    autoLike.forEach { place ->
                        runCatching { VoteRepository.submitVote(bandId, place.placeId, 1) }
                        // CONFLICT(이미 투표)는 무시 — 화면 재진입 시 중복 방지
                    }
                    // autoLike 장소도 votedPlaces에 포함해야 totalCount(진행률 분모)가 정확함
                    // 미포함 시 "8/8" 완료인데 서버는 "10/10"으로 판단 → 화면 불일치
                    if (autoLike.isNotEmpty()) {
                        _uiState.update { it.copy(votedPlaces = it.votedPlaces + autoLike) }
                        refreshStatus()
                    }
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.toUserMessage()) }
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
                .onFailure { _uiState.value = _uiState.value.copy(error = it.toUserMessage()) }
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
                .onFailure { _uiState.value = _uiState.value.copy(error = it.toUserMessage()) }
        }
    }

    /** 내 상태 + 그룹 상태 모두 재조회 — 투표 완료 직후 등 전체 동기화가 필요할 때 */
    fun refreshStatus() {
        viewModelScope.launch {
            runCatching {
                val my    = VoteRepository.getMyVoteStatus(currentBandId)
                val group = VoteRepository.getGroupVoteStatus(currentBandId)
                _uiState.value = _uiState.value.copy(myStatus = my, groupStatus = group)
            }
        }
    }

    /** 그룹 상태만 재조회 — WebSocket 이벤트 수신 시 사용 (내 상태는 로컬에서 최신 유지됨) */
    private fun refreshGroupStatus() {
        viewModelScope.launch {
            runCatching { VoteRepository.getGroupVoteStatus(currentBandId) }
                .onSuccess { group -> _uiState.value = _uiState.value.copy(groupStatus = group) }
        }
    }

    /** 투표 결과 조회 — 투표 종료 후 결과 화면 진입 시 호출 */
    fun loadVoteResults(bandId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { VoteRepository.getVoteResults(bandId) }
                .onSuccess { results -> _uiState.update { it.copy(voteResults = results, isLoading = false) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.toUserMessage()) } }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
