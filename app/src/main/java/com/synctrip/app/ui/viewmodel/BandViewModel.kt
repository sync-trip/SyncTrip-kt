package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.BandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BandUiState(
    val bands: List<BandResponse>              = emptyList(),
    val selectedBand: BandResponse?            = null,
    val members: List<BandMemberResponse>      = emptyList(),
    val inviteCode: BandInviteCodeResponse?    = null,
    val readyStatus: BandReadyResponse?        = null,
    val picks: PlacePickListResponse?          = null,
    val searchResults: List<ApiPlaceSearchResult> = emptyList(),
    val isSearchLoading: Boolean               = false,
    val isLoading: Boolean                     = false,
    val error: String?                         = null,
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

    /**
     * 주변 장소 검색.
     * @param category 백엔드 ApiPlaceCategory 이름 문자열 (예: "FOOD"). null이면 전체.
     */
    fun searchPlaces(bandId: Long, keyword: String? = null, category: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchLoading = true)
            runCatching { BandRepository.searchPlaces(bandId, keyword, category) }
                .onSuccess { _uiState.value = _uiState.value.copy(searchResults = it, isSearchLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isSearchLoading = false, error = it.message) }
        }
    }

    /**
     * 장바구니 토글 — externalId로 담기/삭제 분기.
     * 낙관적 업데이트: UI에 먼저 반영 후 API 호출, 실패 시 롤백.
     */
    fun togglePick(bandId: Long, externalId: String) {
        val existingPick = _uiState.value.picks?.items?.find { it.externalId == externalId }
        if (existingPick != null) {
            doDeletePick(bandId, existingPick.placeId, externalId)
        } else {
            val place = _uiState.value.searchResults.find { it.externalId == externalId } ?: return
            doAddPick(bandId, place)
        }
    }

    /** 장소 담기 — 낙관적 업데이트 후 API, 실패 시 롤백 */
    private fun doAddPick(bandId: Long, place: ApiPlaceSearchResult) {
        // 낙관적: isBookmarked → true
        _uiState.value = _uiState.value.copy(
            searchResults = _uiState.value.searchResults.map {
                if (it.externalId == place.externalId) it.copy(isBookmarked = true) else it
            }
        )
        viewModelScope.launch {
            val req = PlacePickRequest(
                apiSource  = place.apiSource,
                externalId = place.externalId,
                name       = place.name,
                category   = place.category,
                latitude   = place.latitude,
                longitude  = place.longitude,
                address    = place.address,
                rating     = place.rating,
                thumbnailUrl = place.thumbnailUrl,
            )
            runCatching { BandRepository.addPick(bandId, req) }
                .onSuccess { loadPicks(bandId) }
                .onFailure {
                    // 롤백
                    _uiState.value = _uiState.value.copy(
                        searchResults = _uiState.value.searchResults.map {
                            if (it.externalId == place.externalId) it.copy(isBookmarked = false) else it
                        },
                        error = it.message,
                    )
                }
        }
    }

    /** 장소 삭제 — 낙관적 업데이트 후 API, 실패 시 롤백 */
    private fun doDeletePick(bandId: Long, placeId: Long, externalId: String) {
        // 낙관적: isBookmarked → false
        _uiState.value = _uiState.value.copy(
            searchResults = _uiState.value.searchResults.map {
                if (it.externalId == externalId) it.copy(isBookmarked = false) else it
            }
        )
        viewModelScope.launch {
            runCatching { BandRepository.deletePick(bandId, placeId) }
                .onSuccess { loadPicks(bandId) }
                .onFailure {
                    // 롤백
                    _uiState.value = _uiState.value.copy(
                        searchResults = _uiState.value.searchResults.map {
                            if (it.externalId == externalId) it.copy(isBookmarked = true) else it
                        },
                        error = it.message,
                    )
                }
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
