package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.BandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BandUiState(
    val bands: List<BandResponse>         = emptyList(),
    val selectedBand: BandResponse?       = null,
    val members: List<BandMemberResponse> = emptyList(),
    val inviteCode: BandInviteCodeResponse? = null,
    val readyStatus: BandReadyResponse?   = null,
    val picks: PlacePickListResponse?     = null,
    val isLoading: Boolean                = false,
    val error: String?                    = null,
)

class BandViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BandUiState())
    val uiState: StateFlow<BandUiState> = _uiState

    fun loadBands() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { BandRepository.getBands() }
                .onSuccess { _uiState.value = _uiState.value.copy(bands = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectBand(band: BandResponse) {
        _uiState.value = _uiState.value.copy(selectedBand = band)
        loadMembers(band.id)
    }

    fun loadMembers(bandId: Long) {
        viewModelScope.launch {
            runCatching { BandRepository.getMembers(bandId) }
                .onSuccess { _uiState.value = _uiState.value.copy(members = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun createBand(request: BandCreateRequest, onSuccess: (BandResponse) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { BandRepository.createBand(request) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess(it)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun joinBand(inviteCode: String, onSuccess: (BandResponse) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { BandRepository.joinBand(inviteCode) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess(it)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun getInviteCode(bandId: Long) {
        viewModelScope.launch {
            runCatching { BandRepository.getInviteCode(bandId) }
                .onSuccess { _uiState.value = _uiState.value.copy(inviteCode = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun setReady(bandId: Long) {
        viewModelScope.launch {
            runCatching { BandRepository.setReady(bandId) }
                .onSuccess { _uiState.value = _uiState.value.copy(readyStatus = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun loadPicks(bandId: Long) {
        viewModelScope.launch {
            runCatching { BandRepository.getPicks(bandId) }
                .onSuccess { _uiState.value = _uiState.value.copy(picks = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun advanceBandStatus(bandId: Long, onSuccess: (BandResponse) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { BandRepository.advanceBandStatus(bandId) }
                .onSuccess { updated ->
                    // bands 목록에서 해당 밴드 상태 갱신
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        selectedBand = updated,
                        bands        = _uiState.value.bands.map { if (it.id == bandId) updated else it },
                    )
                    onSuccess(updated)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
