package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.ScheduleRepository
import com.synctrip.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 일정 화면 UI 상태 */
data class ScheduleUiState(
    val schedule: ScheduleResponse?          = null,
    val altOptions: List<ScheduleAltResponse> = emptyList(),
    val destination: String                  = "",
    val isLoading: Boolean                   = false,
    val isEditing: Boolean                   = false,
    /** Plan B 추천 결과 목록 */
    val planBResults: List<PlanBResponse>    = emptyList(),
    /** Plan B 추천 로딩 중 여부 */
    val isPlanBLoading: Boolean              = false,
    val error: String?                       = null,
)

/** 일정 화면 ViewModel — ScheduleRepository 위임, 편집 락 포함 */
class ScheduleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    /** GET /api/bands/{bandId}/schedule */
    fun loadSchedule(bandId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { ScheduleRepository.getSchedule(bandId) }
                .onSuccess { resp ->
                    _uiState.update { it.copy(schedule = resp, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    /** GET /api/bands/{bandId}/schedule/alts — 슬롯 선택 시 대체 후보 로드 */
    fun loadAlts(bandId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.getAlts(bandId) }
                .onSuccess { alts -> _uiState.update { it.copy(altOptions = alts) } }
                .onFailure { /* 대체 후보 오류는 UI에서 빈 목록으로 처리 */ }
        }
    }

    /** POST /api/bands/{bandId}/schedule/swap — 장소 교체 후 일정 새로고침 */
    fun swapSlot(bandId: Long, scheduleId: Long, newPlaceId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.swapSlot(bandId, scheduleId, newPlaceId) }
                .onSuccess {
                    _uiState.update { it.copy(altOptions = emptyList()) }
                    loadSchedule(bandId)
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    /** POST /api/bands/{bandId}/schedule/move — 슬롯을 다른 Day로 이동. 실패 시 서버 상태로 롤백 */
    fun moveSlot(bandId: Long, scheduleId: Long, targetDayNumber: Int, targetSlotOrder: Int) {
        viewModelScope.launch {
            runCatching {
                ScheduleRepository.moveSlot(bandId, ScheduleMoveRequest(scheduleId, targetDayNumber, targetSlotOrder))
            }
                .onSuccess { loadSchedule(bandId) }
                .onFailure { e ->
                    loadSchedule(bandId)
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /** PATCH /api/bands/{bandId}/schedule/reorder — Drag & Drop 순서 저장. 실패 시 서버 상태로 롤백 */
    fun reorderSlots(bandId: Long, dayNumber: Int, orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching {
                ScheduleRepository.reorderSchedule(bandId, ScheduleReorderRequest(dayNumber, orderedIds))
            }
                .onSuccess { loadSchedule(bandId) }
                .onFailure { e ->
                    loadSchedule(bandId)  // 실패 시 서버 순서로 롤백
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /** POST /api/bands/{bandId}/schedule/edit/start — 편집 락 획득 */
    fun startEditing(bandId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.startEditing(bandId) }
                .onSuccess { _uiState.update { it.copy(isEditing = true) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    /** POST /api/bands/{bandId}/schedule/edit/finish — 편집 락 반환 후 일정 새로고침 (editingUserId 초기화) */
    fun finishEditing(bandId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.finishEditing(bandId) }
                .onSuccess {
                    _uiState.update { it.copy(isEditing = false) }
                    loadSchedule(bandId)  // 허브 화면에서 "○○님이 편집 중" 배너가 남지 않도록 갱신
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    /**
     * POST /api/bands/{bandId}/schedule/plan-b
     * 현재 슬롯 장소 근처의 대안 장소 추천 (최대 7개)
     */
    fun loadPlanB(bandId: Long, targetPlaceId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(planBResults = emptyList(), isPlanBLoading = true) }
            runCatching { ScheduleRepository.getPlanB(bandId, targetPlaceId) }
                .onSuccess { results ->
                    _uiState.update { it.copy(planBResults = results, isPlanBLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isPlanBLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Plan B 교체 — 편집 락 획득 → 교체 → 락 반환을 순서대로 원자적으로 실행.
     * UI에서 별도의 편집 모드 진입 없이 호출 가능.
     */
    fun executePlanBSwap(bandId: Long, scheduleId: Long, newPlaceId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, planBResults = emptyList()) }
            runCatching {
                ScheduleRepository.startEditing(bandId)
                ScheduleRepository.swapSlot(bandId, scheduleId, newPlaceId)
                ScheduleRepository.finishEditing(bandId)
            }
            .onSuccess { loadSchedule(bandId) }
            .onFailure { e ->
                // 교체 실패 시에도 락 반환 시도
                runCatching { ScheduleRepository.finishEditing(bandId) }
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
