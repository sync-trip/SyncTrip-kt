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
    ): List<PlaceSearchResult>

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
}
