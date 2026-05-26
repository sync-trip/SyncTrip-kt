package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 알림 화면 UI 상태 */
data class NotificationUiState(
    /** 날짜 레이블 → 알림 목록 (예: "오늘", "2026-05-23") */
    val groups: Map<String, List<NotificationItem>> = emptyMap(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 알림 화면 ViewModel — 백엔드 NotificationResponse → UI NotificationItem 변환 포함 */
class NotificationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState

    /** GET /api/notifications → 날짜별 그룹으로 변환 */
    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { ApiClient.api.getNotifications() }
                .onSuccess { list ->
                    val items = list.map { it.toUiItem() }
                    val groups = items.groupBy { it.timeLabel.take(10) }   // "YYYY-MM-DD" 기준 그룹
                        .mapKeys { (date, _) -> formatDateLabel(date) }
                    _uiState.update { it.copy(groups = groups, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
        viewModelScope.launch {
            runCatching { ApiClient.api.getUnreadNotificationCount() }
                .onSuccess { resp -> _uiState.update { it.copy(unreadCount = resp.count.toInt()) } }
        }
    }

    /** PATCH /api/notifications/read-all */
    fun markAllRead() {
        viewModelScope.launch {
            runCatching { ApiClient.api.markAllNotificationsRead() }
                .onSuccess {
                    // 로컬 상태에서 전부 읽음 처리
                    val updated = _uiState.value.groups.mapValues { (_, items) ->
                        items.map { it.copy(isRead = true) }
                    }
                    _uiState.update { it.copy(groups = updated, unreadCount = 0) }
                }
        }
    }

    /** PATCH /api/notifications/{id}/read */
    fun markRead(id: String) {
        val longId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            runCatching { ApiClient.api.markNotificationRead(longId) }
                .onSuccess {
                    val updated = _uiState.value.groups.mapValues { (_, items) ->
                        items.map { if (it.id == id) it.copy(isRead = true) else it }
                    }
                    val newUnread = (_uiState.value.unreadCount - 1).coerceAtLeast(0)
                    _uiState.update { it.copy(groups = updated, unreadCount = newUnread) }
                }
        }
    }

    /** DELETE /api/notifications/{id} */
    fun deleteNotification(id: String) {
        val longId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            runCatching { ApiClient.api.deleteNotification(longId) }
                .onSuccess {
                    val updated = _uiState.value.groups.mapValues { (_, items) ->
                        items.filter { it.id != id }
                    }.filterValues { it.isNotEmpty() }
                    _uiState.update { it.copy(groups = updated) }
                }
        }
    }

    /** ApiNotificationType(백엔드) → NotificationType(UI 모델) 변환. null(미지원 타입)은 GENERAL */
    private fun ApiNotificationType?.toUiType(): NotificationType = when (this) {
        ApiNotificationType.VOTE_STARTED       -> NotificationType.VOTING
        ApiNotificationType.SETTLEMENT_REQUEST -> NotificationType.SETTLEMENT
        ApiNotificationType.SCHEDULE_UPDATED   -> NotificationType.SCHEDULE_CHANGE
        ApiNotificationType.HOLIDAY_WARNING    -> NotificationType.GENERAL
        else                                   -> NotificationType.GENERAL
    }

    /** NotificationResponse → NotificationItem 변환 */
    private fun NotificationResponse.toUiItem() = NotificationItem(
        id        = id.toString(),
        type      = type.toUiType(),
        title     = title,
        body      = content,
        timeLabel = createdAt,
        isRead    = isRead,
    )

    /** "YYYY-MM-DD" → 표시용 레이블 (오늘/어제/날짜) */
    private fun formatDateLabel(date: String): String {
        val today     = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        return when (date) {
            today     -> "오늘"
            yesterday -> "어제"
            else      -> date
        }
    }
}
