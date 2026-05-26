package com.synctrip.app.network

import com.synctrip.app.data.models.*
import okhttp3.MultipartBody
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
    suspend fun advanceBandStatus(@Path("bandId") bandId: Long): BandStatusTransitionResponse

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
    ): List<ApiPlaceSearchResult>

    // ── Expense ───────────────────────────────────────────────────────────────

    @Multipart
    @POST("api/bands/{bandId}/expenses/ocr")
    suspend fun scanReceipt(
        @Path("bandId") bandId: Long,
        @Part image: MultipartBody.Part,
    ): OcrReceiptResponse

    @GET("api/bands/{bandId}/expenses")
    suspend fun getExpenses(@Path("bandId") bandId: Long): List<ExpenseResponse>

    @POST("api/bands/{bandId}/expenses")
    suspend fun createExpense(
        @Path("bandId") bandId: Long,
        @Body body: ExpenseCreateRequest,
    ): ExpenseResponse

    @PUT("api/bands/{bandId}/expenses/{expenseId}")
    suspend fun updateExpense(
        @Path("bandId") bandId: Long,
        @Path("expenseId") expenseId: Long,
        @Body body: ExpenseUpdateRequest,
    ): ExpenseResponse

    @DELETE("api/bands/{bandId}/expenses/{expenseId}")
    suspend fun deleteExpense(
        @Path("bandId") bandId: Long,
        @Path("expenseId") expenseId: Long,
    )

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

    /** 회원 탈퇴 — Soft Delete. 성공 시 204 No Content */
    @DELETE("auth/kakao/withdraw")
    suspend fun withdraw()

    // ── Destination ───────────────────────────────────────────────────────────

    /** 인기 여행지 목록 (하드코딩 28개) */
    @GET("api/destinations/popular")
    suspend fun getPopularDestinations(): List<DestinationResponse>

    /** 여행지 검색 (Google Places + Spring Cache) */
    @GET("api/destinations/search")
    suspend fun searchDestinations(@Query("query") query: String): List<DestinationResponse>

    // ── Holiday (공휴일) ──────────────────────────────────────────────────────

    /** 국가+연도별 공휴일 목록 — 달력 마킹용. Nager.Date API 래핑 */
    @GET("api/holidays")
    suspend fun getHolidays(
        @Query("countryCode") countryCode: String,
        @Query("year") year: Int,
    ): List<HolidayInfo>

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

    // ── Passport Stamps ───────────────────────────────────────────────────────

    /** 내 여권 스탬프 목록 — stampedAt DESC 정렬 */
    @GET("api/users/me/stamps")
    suspend fun getMyStamps(): List<ApiPassportStampResponse>

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

    // ── Album (USR-023 공유 앨범) ──────────────────────────────────────────────

    /** 사진 + 글 + 좌표 업로드. Base64 photoData 필수. 성공 시 201 Created */
    @POST("api/bands/{bandId}/album")
    suspend fun uploadAlbumPhoto(
        @Path("bandId") bandId: Long,
        @Body body: AlbumPhotoUploadRequest,
    ): AlbumPhotoResponse

    /** 피드 목록 — 최신순, Base64 이미지 포함 */
    @GET("api/bands/{bandId}/album")
    suspend fun getAlbumFeed(@Path("bandId") bandId: Long): List<AlbumPhotoResponse>

    /** 지도 핀용 목록 — 좌표 있는 사진만, Base64 제외 경량 응답 */
    @GET("api/bands/{bandId}/album/map")
    suspend fun getAlbumMapPins(@Path("bandId") bandId: Long): List<AlbumPhotoMapResponse>

    /** 사진 상세 조회 */
    @GET("api/bands/{bandId}/album/{photoId}")
    suspend fun getAlbumPhoto(
        @Path("bandId") bandId: Long,
        @Path("photoId") photoId: Long,
    ): AlbumPhotoResponse

    /** 캡션 수정 — 업로더만 가능 */
    @PATCH("api/bands/{bandId}/album/{photoId}")
    suspend fun updateAlbumPhoto(
        @Path("bandId") bandId: Long,
        @Path("photoId") photoId: Long,
        @Body body: AlbumPhotoUpdateRequest,
    ): AlbumPhotoResponse

    /** 사진 삭제 — 업로더 또는 방장만 가능. 성공 시 204 No Content */
    @DELETE("api/bands/{bandId}/album/{photoId}")
    suspend fun deleteAlbumPhoto(
        @Path("bandId") bandId: Long,
        @Path("photoId") photoId: Long,
    )
}
