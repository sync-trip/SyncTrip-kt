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
    val schedule: ScheduleResponse?       = null,
    val destination: String               = "",
    val isLoading: Boolean                = false,
    val isEditing: Boolean                = false,
    /** 장소 교체 대안 추천 결과 목록 (POST /schedule/plan-b) */
    val planBResults: List<PlanBResponse> = emptyList(),
    /** 장소 교체 추천 API 로딩 중 여부 */
    val isPlanBLoading: Boolean           = false,
    val error: String?                    = null,
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

    /** POST /api/bands/{bandId}/schedule/swap — 장소 교체 후 일정 새로고침 */
    fun swapSlot(bandId: Long, scheduleId: Long, newPlaceId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.swapSlot(bandId, scheduleId, newPlaceId) }
                .onSuccess { loadSchedule(bandId) }
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

    /** DELETE /api/bands/{bandId}/schedule/{scheduleId} — 장소 삭제 후 일정 새로고침 */
    fun deleteSlot(bandId: Long, scheduleId: Long) {
        viewModelScope.launch {
            runCatching { ScheduleRepository.deleteSlot(bandId, scheduleId) }
                .onSuccess { loadSchedule(bandId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
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

    /**
     * POST /api/bands/{bandId}/schedule/edit/start — 편집 락 하트비트.
     * 편집 화면에 머무는 동안 주기적으로 호출해 백엔드 lastEditingAt을 갱신한다.
     * (백엔드 락 타임아웃 1분 — 액션 없이 화면만 보고 있어도 락이 만료되지 않도록 유지)
     * startEditing과 달리 isEditing/error 상태를 건드리지 않아 스낵바·UI를 방해하지 않는다.
     */
    fun heartbeatEditing(bandId: Long) {
        viewModelScope.launch {
            // 실패(일시적 네트워크 등)는 무시 — 다음 하트비트나 액션 시점에 다시 갱신된다.
            runCatching { ScheduleRepository.startEditing(bandId) }
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
                // 호출 실패 시에도 로컬 편집 상태는 종료로 정리 — 재진입 시 startEditing이 다시 호출되도록.
                // 실제 백엔드 락은 1분 하트비트 타임아웃으로 자동 해제된다.
                .onFailure { _uiState.update { it.copy(isEditing = false) } }
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
     * 저장 버튼 — 크로스 Day 이동 후 각 Day 순서 확정.
     * 알림은 마지막 API 호출 한 번에만 발송 (notify=true), 나머지는 notify=false.
     * 성공 시 onSuccess 콜백 호출 (화면 닫기 등).
     */
    fun saveScheduleChanges(
        bandId: Long,
        moves: List<ScheduleMoveRequest>,
        allDayOrders: Map<Int, List<Long>>,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val filteredDays = allDayOrders.filter { it.value.isNotEmpty() }
            val dayNums = filteredDays.keys.toList()
            runCatching {
                // 1단계: 크로스 Day 이동 — reorder가 없을 때만 마지막 move에서 알림 발송
                moves.forEachIndexed { i, move ->
                    val isLast = i == moves.lastIndex && dayNums.isEmpty()
                    ScheduleRepository.moveSlot(bandId, move.copy(notify = isLast))
                }
                // 2단계: 각 Day 최종 순서 확정 — 마지막 Day에서만 알림 발송
                dayNums.forEachIndexed { i, dayNum ->
                    val isLast = i == dayNums.lastIndex
                    ScheduleRepository.reorderSchedule(
                        bandId, ScheduleReorderRequest(dayNum, filteredDays[dayNum]!!, notify = isLast)
                    )
                }
            }
            .onSuccess {
                loadSchedule(bandId)
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }
            .onFailure { e ->
                loadSchedule(bandId)
                // move가 포함된 경우 일부만 서버에 반영됐을 수 있음
                val msg = if (moves.isNotEmpty())
                    "일부 변경사항만 저장됐을 수 있습니다. 다시 확인해주세요."
                else e.toUserMessage()
                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    /**
     * 장소 검색 결과를 특정 Day에 추가한다.
     * ScheduleEditScreen의 편집 락(isNavigatingToChild 플래그)이 유지된 채로 호출되므로
     * startEditing/finishEditing 없이 API만 호출한다.
     * 성공 시 onSuccess 콜백 호출 (화면 닫기 등).
     */
    fun addSlotFromSearch(
        bandId: Long,
        dayNumber: Int,
        place: ApiPlaceSearchResult,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                ScheduleRepository.addSlotFromSearch(
                    bandId,
                    ScheduleAddFromSearchRequest(
                        apiSource       = place.apiSource,
                        externalId      = place.externalId,
                        name            = place.name,
                        category        = place.category,
                        latitude        = place.latitude,
                        longitude       = place.longitude,
                        address         = place.address,
                        rating          = place.rating,
                        thumbnailUrl    = place.thumbnailUrl,
                        targetDayNumber = dayNumber,
                    )
                )
            }
            .onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                loadSchedule(bandId)
                onSuccess()
            }
            .onFailure { e ->
                // 락은 ScheduleEditScreen이 유지 중 — 여기서 finishEditing 하면 안 됨
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
