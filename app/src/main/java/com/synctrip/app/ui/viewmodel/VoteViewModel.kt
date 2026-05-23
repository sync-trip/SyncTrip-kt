package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.VoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
