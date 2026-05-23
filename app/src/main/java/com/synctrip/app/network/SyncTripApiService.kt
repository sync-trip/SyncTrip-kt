package com.synctrip.app.network

import com.synctrip.app.data.models.*
import retrofit2.http.*

interface SyncTripApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────

    @POST("auth/kakao/login")
    suspend fun kakaoLogin(@Body body: KakaoLoginRequest): LoginResponse

    @POST("auth/google/login")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): LoginResponse

    @POST("auth/kakao/refresh")
    suspend fun refreshToken(@Body body: TokenRefreshRequest): LoginResponse

    @POST("auth/kakao/logout")
    suspend fun logout(): Unit

    // ── Band ─────────────────────────────────────────────────────────────────

    @GET("api/bands")
    suspend fun getBands(): List<BandResponse>

    @POST("api/bands")
    suspend fun createBand(@Body body: BandCreateRequest): BandResponse

    @POST("api/bands/join")
    suspend fun joinBand(@Body body: BandJoinRequest): BandResponse

    @GET("api/bands/{bandId}/members")
    suspend fun getBandMembers(@Path("bandId") bandId: Long): List<BandMemberResponse>

    @POST("api/bands/{bandId}/invite-code")
    suspend fun getInviteCode(@Path("bandId") bandId: Long): BandInviteCodeResponse

    @POST("api/bands/{bandId}/ready")
    suspend fun setReady(@Path("bandId") bandId: Long): BandReadyResponse

    @DELETE("api/bands/{bandId}/ready")
    suspend fun cancelReady(@Path("bandId") bandId: Long): BandReadyResponse

    @POST("api/bands/{bandId}/status/advance")
    suspend fun advanceBandStatus(@Path("bandId") bandId: Long): BandResponse

    // ── Schedule ─────────────────────────────────────────────────────────────

    @POST("api/bands/{bandId}/schedule/generate")
    suspend fun generateSchedule(@Path("bandId") bandId: Long)

    @GET("api/bands/{bandId}/schedule")
    suspend fun getSchedule(@Path("bandId") bandId: Long): ScheduleResponse

    @GET("api/bands/{bandId}/schedule/alts")
    suspend fun getScheduleAlts(@Path("bandId") bandId: Long): List<ScheduleAltResponse>

    @POST("api/bands/{bandId}/schedule/swap")
    suspend fun swapScheduleSlot(
        @Path("bandId") bandId: Long,
        @Body body: ScheduleSwapRequest,
    ): Unit

    @POST("api/bands/{bandId}/schedule/edit/start")
    suspend fun startEditing(@Path("bandId") bandId: Long)

    @POST("api/bands/{bandId}/schedule/edit/finish")
    suspend fun finishEditing(@Path("bandId") bandId: Long)

    @POST("api/bands/{bandId}/schedule/plan-b")
    suspend fun getPlanB(
        @Path("bandId") bandId: Long,
        @Body body: PlanBRequest,
    ): List<PlanBResponse>

    // ── Vote ─────────────────────────────────────────────────────────────────

    @GET("api/bands/{bandId}/votes/places")
    suspend fun getVotePlaces(@Path("bandId") bandId: Long): List<VotePlaceResponse>

    @POST("api/bands/{bandId}/votes")
    suspend fun submitVote(
        @Path("bandId") bandId: Long,
        @Body body: VoteRequest,
    ): VoteResponse

    @GET("api/bands/{bandId}/votes/status")
    suspend fun getVoteStatus(@Path("bandId") bandId: Long): VoteStatusResponse

    @GET("api/bands/{bandId}/votes/status/group")
    suspend fun getGroupVoteStatus(@Path("bandId") bandId: Long): GroupVoteStatusResponse

    // ── Place Search ──────────────────────────────────────────────────────────

    @GET("api/bands/{bandId}/places/search")
    suspend fun searchPlaces(
        @Path("bandId") bandId: Long,
        @Query("keyword") keyword: String? = null,
        @Query("category") category: String? = null,
        @Query("radiusMeters") radiusMeters: Int = 5000,
    ): List<ApiPlaceSearchResult>

    // ── Expense ───────────────────────────────────────────────────────────────

    @GET("api/bands/{bandId}/expenses")
    suspend fun getExpenses(@Path("bandId") bandId: Long): List<ExpenseResponse>

    @POST("api/bands/{bandId}/expenses")
    suspend fun createExpense(
        @Path("bandId") bandId: Long,
        @Body body: ExpenseCreateRequest,
    ): ExpenseResponse

    // ── Settlement ────────────────────────────────────────────────────────────

    @GET("api/bands/{bandId}/settlement")
    suspend fun getSettlement(@Path("bandId") bandId: Long): SettlementResponse

    // ── Notification ──────────────────────────────────────────────────────────

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): List<NotificationResponse>

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") notificationId: Long)

    // ── FCM ───────────────────────────────────────────────────────────────────

    @POST("api/users/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest)

    // ── User Profile ──────────────────────────────────────────────────────────

    @GET("api/users/me")
    suspend fun getMyProfile(): UserProfileResponse

    /** 실제 HTTP 메서드는 PUT (백엔드 UserController 기준) */
    @PUT("api/users/me")
    suspend fun updateProfile(@Body body: UserProfileUpdateRequest): UserProfileResponse

    // ── Destination ───────────────────────────────────────────────────────────

    /** 인기 여행지 목록 (하드코딩 28개) */
    @GET("api/destinations/popular")
    suspend fun getPopularDestinations(): List<DestinationResponse>

    /** 여행지 검색 (Google Places + Spring Cache) */
    @GET("api/destinations/search")
    suspend fun searchDestinations(@Query("query") query: String): List<DestinationResponse>

    // ── Place Picks (장바구니) ─────────────────────────────────────────────────

    /** 장바구니 목록 — {currentCount, maxCount, items} 래퍼로 반환됨 */
    @GET("api/bands/{bandId}/picks")
    suspend fun getPicks(@Path("bandId") bandId: Long): PlacePickListResponse

    @POST("api/bands/{bandId}/picks")
    suspend fun addPick(
        @Path("bandId") bandId: Long,
        @Body body: PlacePickRequest,
    ): PlacePickResponse

    @DELETE("api/bands/{bandId}/picks/{placeId}")
    suspend fun deletePick(
        @Path("bandId") bandId: Long,
        @Path("placeId") placeId: Long,
    )

    // ── Band Delete ───────────────────────────────────────────────────────────

    /** 밴드 삭제 — 방장 전용, Soft Delete */
    @DELETE("api/bands/{bandId}")
    suspend fun deleteBand(@Path("bandId") bandId: Long)

    // ── Notification (추가 엔드포인트) ────────────────────────────────────────

    @GET("api/notifications/unread-count")
    suspend fun getUnreadNotificationCount(): UnreadCountResponse

    @PATCH("api/notifications/read-all")
    suspend fun markAllNotificationsRead()

    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") notificationId: Long)

    // ── Notification Settings ─────────────────────────────────────────────────

    @GET("api/users/notification-settings")
    suspend fun getNotificationSettings(): NotificationSettingsResponse

    /** 한 번에 타입 하나씩 on/off — {type, enabled} 형식 */
    @PATCH("api/users/notification-settings")
    suspend fun updateNotificationSettings(
        @Body body: NotificationSettingUpdateRequest,
    ): NotificationSettingsResponse

    // ── Settlement Request ────────────────────────────────────────────────────

    /** 정산 요청 알림 발송 */
    @POST("api/bands/{bandId}/settlement/request")
    suspend fun requestSettlement(@Path("bandId") bandId: Long)
}
