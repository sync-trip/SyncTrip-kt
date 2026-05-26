package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.BandRepository
import com.synctrip.app.data.repository.ScheduleRepository
import com.synctrip.app.network.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
    // 장바구니 최대 개수 초과 시 다이얼로그 트리거
    val pickLimitReached: Boolean              = false,
    // 로그인한 유저 프로필 (드로어 표시용)
    val userProfile: UserProfileResponse?      = null,
    // 정산 화면 데이터
    val settlement: Settlement?                = null,
    val expenses: List<ExpenseResponse>        = emptyList(),
    val isExpensesLoading: Boolean             = false,
    // 홈 화면 추천 여행지
    val recommendedDestinations: List<RecommendedContent> = emptyList(),
    // 알림 설정
    val notificationSettings: NotificationSettingsResponse? = null,
    val isNotificationSettingsLoading: Boolean = false,
    // 여권 스탬프 목록 (화면 표시용 UI 모델)
    val passportStamps: List<PassportStamp> = emptyList(),
    val isPassportLoading: Boolean = false,
)

class BandViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BandUiState())
    val uiState: StateFlow<BandUiState> = _uiState

    // 구 앱과 동일한 폴링 방식 — GENERATING 상태일 때 3초마다 밴드 상태 조회
    private var pollingJob: Job? = null

    /**
     * 일정 생성 완료 이벤트 (SharedFlow, replay=0).
     * 폴링이 GENERATING→다른 상태 전환을 감지하면 한 번 발행 — NavGraph가 collect해 일정 화면으로 이동.
     * replay=0이므로 로비 재진입 시 과거 이벤트가 재수신되지 않아 무한루프 방지.
     */
    private val _scheduleReadyEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scheduleReadyEvent: SharedFlow<Unit> = _scheduleReadyEvent.asSharedFlow()

    /** GENERATING 상태에서 호출 — 3초마다 밴드 상태 폴링, 전환 감지 시 scheduleReadyEvent 발행 */
    fun startGeneratingPoll(bandId: Long) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(3000L)
                runCatching { BandRepository.getBands() }
                    .onSuccess { bands ->
                        val updated = bands.find { it.id == bandId } ?: return@onSuccess
                        if (updated.status != BandStatus.GENERATING) {
                            // 1차: 밴드 상태가 바뀐 경우
                            _uiState.update { state ->
                                state.copy(bands = state.bands.map { if (it.id == bandId) updated else it })
                            }
                            _scheduleReadyEvent.tryEmit(Unit)
                            pollingJob?.cancel()
                        } else {
                            // 2차 fallback (구 앱 동일): 상태가 아직 GENERATING이어도
                            // 일정 데이터가 이미 존재하면 생성 완료로 처리
                            checkScheduleExistsAsBackup(bandId)
                        }
                    }
            }
        }
    }

    /** 구 앱 checkScheduleReady() 동일 — 일정 API 호출해 days가 있으면 완료 이벤트 발행 */
    private suspend fun checkScheduleExistsAsBackup(bandId: Long) {
        runCatching { ScheduleRepository.getSchedule(bandId) }
            .onSuccess { schedule ->
                if (schedule.days.isNotEmpty()) {
                    _scheduleReadyEvent.tryEmit(Unit)
                    pollingJob?.cancel()
                }
            }
    }

    /** 화면 이탈 시 폴링 중단 */
    fun stopGeneratingPoll() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** 로그인한 유저 프로필 조회 — 드로어 닉네임/프로필 이미지 표시 */
    fun loadMyProfile() {
        viewModelScope.launch {
            runCatching { BandRepository.getMyProfile() }
                .onSuccess { _uiState.value = _uiState.value.copy(userProfile = it) }
                .onFailure { /* 프로필 오류는 조용히 무시 — 드로어에 기본값 표시 */ }
        }
    }

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
                .onSuccess { resp ->
                    // members 목록에서 내 isReady 상태 즉시 반영 — 재조회 없이 UI 즉시 갱신
                    _uiState.value = _uiState.value.copy(
                        readyStatus = resp,
                        members = _uiState.value.members.map { member ->
                            if (member.userId == resp.userId) member.copy(isReady = resp.isReady) else member
                        },
                    )
                }
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
     * 담기 시 한도 초과면 pickLimitReached 플래그만 세우고 API 호출하지 않음.
     * 낙관적 업데이트: UI에 먼저 반영 후 API 호출, 실패 시 롤백.
     */
    fun togglePick(bandId: Long, externalId: String) {
        val existingPick = _uiState.value.picks?.items?.find { it.externalId == externalId }
        if (existingPick != null) {
            doDeletePick(bandId, existingPick.placeId, externalId)
        } else {
            // 담기 전 한도 체크 — 초과 시 다이얼로그 표시
            val picks = _uiState.value.picks
            if (picks != null && picks.currentCount >= picks.maxCount) {
                _uiState.value = _uiState.value.copy(pickLimitReached = true)
                return
            }
            val place = _uiState.value.searchResults.find { it.externalId == externalId } ?: return
            doAddPick(bandId, place)
        }
    }

    /** 한도 초과 다이얼로그 닫기 */
    fun clearPickLimit() {
        _uiState.value = _uiState.value.copy(pickLimitReached = false)
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

    fun advanceBandStatus(bandId: Long, onSuccess: (BandStatusTransitionResponse) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { BandRepository.advanceBandStatus(bandId) }
                .onSuccess { transition ->
                    // bands 목록에서 해당 밴드의 status만 업데이트 — BandStatusTransitionResponse로 교체 불가
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        selectedBand = _uiState.value.selectedBand?.copy(status = transition.currentStatus),
                        bands        = _uiState.value.bands.map { b ->
                            if (b.id == bandId) b.copy(status = transition.currentStatus) else b
                        },
                    )
                    onSuccess(transition)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /** GET /api/bands/{bandId}/settlement → UI Settlement 모델로 변환 */
    fun loadSettlement(bandId: Long) {
        viewModelScope.launch {
            val currentUserId = _uiState.value.userProfile?.id
            runCatching { ApiClient.api.getSettlement(bandId) }
                .onSuccess { resp ->
                    val ui = resp.toUiSettlement(bandId, currentUserId)
                    _uiState.update { it.copy(settlement = ui) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /** GET /api/bands/{bandId}/expenses — 지출 목록 로드 */
    fun loadExpenses(bandId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExpensesLoading = true) }
            runCatching { ApiClient.api.getExpenses(bandId) }
                .onSuccess { list -> _uiState.update { it.copy(expenses = list, isExpensesLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isExpensesLoading = false, error = e.message) } }
        }
    }

    /** POST /api/bands/{bandId}/expenses — 지출 추가, 성공 시 목록 맨 앞에 추가 */
    fun createExpense(bandId: Long, request: ExpenseCreateRequest) {
        viewModelScope.launch {
            runCatching { ApiClient.api.createExpense(bandId, request) }
                .onSuccess { created ->
                    _uiState.update { it.copy(expenses = listOf(created) + it.expenses) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /** PUT /api/bands/{bandId}/expenses/{expenseId} — 지출 수정 */
    fun updateExpense(bandId: Long, expenseId: Long, request: ExpenseUpdateRequest) {
        viewModelScope.launch {
            runCatching { ApiClient.api.updateExpense(bandId, expenseId, request) }
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(expenses = state.expenses.map { if (it.id == expenseId) updated else it })
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /** DELETE /api/bands/{bandId}/expenses/{expenseId} — 지출 삭제 (낙관적 업데이트) */
    fun deleteExpense(bandId: Long, expenseId: Long) {
        val prev = _uiState.value.expenses
        _uiState.update { it.copy(expenses = it.expenses.filter { e -> e.id != expenseId }) }
        viewModelScope.launch {
            runCatching { ApiClient.api.deleteExpense(bandId, expenseId) }
                .onFailure { e -> _uiState.update { it.copy(expenses = prev, error = e.message) } }
        }
    }

    /** 밴드 삭제 — 방장 전용, 성공 시 onSuccess 호출 */
    fun deleteBand(bandId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { BandRepository.deleteBand(bandId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /**
     * 인기 여행지 28개를 받아 계절 가중치 + 셔플로 6개 추천을 뽑는다.
     * 봄(3~5월)=일본·유럽, 여름(6~8월)=국내·미주오세아니아, 가을(9~11월)=일본·유럽·동남아, 겨울=동남아·미주오세아니아
     */
    fun loadRecommendedDestinations() {
        viewModelScope.launch {
            runCatching { ApiClient.api.getPopularDestinations() }
                .onSuccess { all ->
                    val picks = pickSeasonalDestinations(all)
                    _uiState.update { it.copy(recommendedDestinations = picks) }
                }
        }
    }

    private fun pickSeasonalDestinations(all: List<DestinationResponse>): List<RecommendedContent> {
        val month = LocalDate.now().monthValue
        // 계절별 선호 지역 — 앞에 있을수록 우선순위 높음
        val preferred = when (month) {
            3, 4, 5   -> listOf("일본", "유럽", "중화권")
            6, 7, 8   -> listOf("국내", "미주/오세아니아", "유럽")
            9, 10, 11 -> listOf("일본", "유럽", "동남아시아")
            else      -> listOf("동남아시아", "미주/오세아니아", "일본")
        }
        // 선호 지역 여부로 두 그룹으로 나눠 선호 그룹을 먼저 배치
        val (high, low) = all.partition { preferred.contains(it.region) }
        // 각 그룹 내부 셔플 후 합쳐서 앞 6개 선택
        return (high.shuffled() + low.shuffled()).take(6).map { it.toRecommendedContent() }
    }

    private fun DestinationResponse.toRecommendedContent() = RecommendedContent(
        id          = "$name-$countryCode",
        title       = "$name, $country",
        imageUrl    = thumbnailUrl ?: "",
        category    = region ?: "해외",
        destination = name,
        // ", "로 구분된 명소 목록 → " · " 구분자로 변환
        subtitle    = description?.replace(", ", " · ") ?: "",
    )

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** GET /api/users/notification-settings — 알림 설정 조회 */
    fun loadNotificationSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isNotificationSettingsLoading = true) }
            runCatching { ApiClient.api.getNotificationSettings() }
                .onSuccess { settings ->
                    _uiState.update { it.copy(notificationSettings = settings, isNotificationSettingsLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isNotificationSettingsLoading = false) }
                }
        }
    }

    /** PATCH /api/users/notification-settings — 알림 타입 하나 on/off */
    fun updateNotificationSetting(type: ApiNotificationType, enabled: Boolean) {
        // 낙관적 업데이트 — UI를 즉시 반영 후 서버 동기화
        val current = _uiState.value.notificationSettings ?: return
        val optimistic = when (type) {
            ApiNotificationType.VOTE_STARTED        -> current.copy(voteStarted = enabled)
            ApiNotificationType.SCHEDULE_UPDATED    -> current.copy(scheduleUpdated = enabled)
            ApiNotificationType.SETTLEMENT_REQUEST  -> current.copy(settlementRequest = enabled)
            ApiNotificationType.MEMBER_READY        -> current.copy(memberReady = enabled)
            ApiNotificationType.MEMBER_JOINED       -> current.copy(memberJoined = enabled)
            else                                    -> current
        }
        _uiState.update { it.copy(notificationSettings = optimistic) }

        viewModelScope.launch {
            runCatching {
                ApiClient.api.updateNotificationSettings(NotificationSettingUpdateRequest(type, enabled))
            }.onFailure {
                // 실패 시 원복
                _uiState.update { it.copy(notificationSettings = current) }
            }
        }
    }

    /**
     * GET /api/users/me/stamps — 여권 스탬프 목록 조회.
     * API는 stampedAt DESC 정렬 → 역순(오래된 것 먼저)으로 변환하여 저장.
     * 신규 스탬프(마지막 아이템)가 애니메이션 강조 대상이 됨.
     */
    fun loadPassportStamps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPassportLoading = true) }
            runCatching { ApiClient.api.getMyStamps() }
                .onSuccess { apiStamps ->
                    // DESC → ASC 역순 변환 (오래된 것 먼저, 최신 것이 마지막에 도장 찍힘)
                    val uiStamps = apiStamps.reversed().mapIndexed { idx, s ->
                        s.toPassportStamp(colorIndex = idx)
                    }
                    _uiState.update { it.copy(passportStamps = uiStamps, isPassportLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isPassportLoading = false) }
                }
        }
    }

    /**
     * API 스탬프 응답 → UI PassportStamp 변환.
     * stampedAt은 ISO 문자열("yyyy-MM-dd'T'HH:mm:ss") 또는 배열("[2023,10,15,...]") 두 형식 모두 처리.
     */
    private fun ApiPassportStampResponse.toPassportStamp(colorIndex: Int): PassportStamp {
        val colors       = StampColor.entries.toTypedArray()
        val visitDate    = parseStampDate(stampedAt)
        val stampedAtMs  = parseStampDateMs(stampedAt)
        return PassportStamp(
            id          = id.toString(),
            cityCode    = city.take(3).uppercase(),
            cityName    = "$city, $countryCode",
            visitDate   = visitDate,
            iconName    = "flight_land",
            accentColor = colors[colorIndex % colors.size],
            stampedAtMs = stampedAtMs,
        )
    }

    /** stampedAt 문자열 → epoch millis (신규 스탬프 판별용) */
    private fun parseStampDateMs(raw: String): Long {
        return runCatching {
            java.time.LocalDateTime.parse(raw)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.recoverCatching {
            val parts = raw.trim('[', ']').split(",")
            java.time.LocalDateTime.of(
                parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt(),
                parts.getOrNull(3)?.trim()?.toIntOrNull() ?: 0,
                parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 0,
            ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    /** "2023-10-15T12:00:00" 또는 "[2023,10,15,12,0,0]" 양쪽 포맷 모두 파싱 → "OCT 2023" */
    private fun parseStampDate(raw: String): String {
        return runCatching {
            val dt = java.time.LocalDateTime.parse(raw)
            "${dt.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase()} ${dt.year}"
        }.recoverCatching {
            // 배열 형식 "[2023,10,15,0,0]" — 연도·월만 추출
            val parts = raw.trim('[', ']').split(",")
            val year  = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            val m = java.time.Month.of(month)
            "${m.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase()} $year"
        }.getOrDefault("")
    }

    /**
     * PUT /api/users/me — 프로필 이름·사진 수정.
     * 성공 시 userProfile 갱신 후 onSuccess 콜백.
     */
    fun updateProfile(
        name: String,
        profileImageUrl: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                ApiClient.api.updateProfile(UserProfileUpdateRequest(name = name, profileImageUrl = profileImageUrl))
            }.onSuccess { updated ->
                _uiState.update { it.copy(userProfile = updated, isLoading = false) }
                onSuccess()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false) }
                onError(e.message ?: "프로필 저장 실패")
            }
        }
    }

    /** SettlementResponse(백엔드) → Settlement(UI) 변환 */
    private fun SettlementResponse.toUiSettlement(bandId: Long, currentUserId: Long?) = Settlement(
        tripId    = bandId.toString(),
        tripTitle = "정산",
        totalAmount = totalExpense.toLong(),
        currency  = baseCurrency,
        myBalance = memberSummaries.firstOrNull { it.userId == currentUserId }?.netAmount?.toLong() ?: 0L,
        summary   = memberSummaries.map { m ->
            SettlementItem(
                id          = m.userId.toString(),
                category    = "지출",
                description = "${m.userName} 정산",
                amount      = m.totalPaid.toLong(),
                paidBy      = m.userName,
            )
        },
        pendingTransfers = transactions.map { t ->
            PendingTransfer(
                fromNickname = t.fromUserName,
                toNickname   = t.toUserName,
                amount       = t.amount.toLong(),
                isResolved   = false,
            )
        },
    )
}
