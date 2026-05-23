package com.synctrip.app.data.models

// ─────────────────────────────────────────────────────────────────────────────
// UI – User & Auth
// ─────────────────────────────────────────────────────────────────────────────

data class UserProfile(
    val id: String,
    val nickname: String,
    val profileImageUrl: String?,
    val homeTown: String?,
    val totalTrips: Int = 0,
    val passportStamps: List<PassportStamp> = emptyList(),
)

data class LoginRequest(
    val provider: String,           // "KAKAO" | "GOOGLE" | "EMAIL"
    val accessToken: String?,
    val email: String? = null,
    val password: String? = null,
)

data class AuthResponse(
    val jwtToken: String,
    val refreshToken: String,
    val user: UserProfile,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – Home Dashboard
// ─────────────────────────────────────────────────────────────────────────────

data class RecommendedContent(
    val id: String,
    val title: String,
    val imageUrl: String,
    val category: String,
    val destination: String,
)

data class TripBand(
    val id: String,
    val destination: String,
    val heroImageUrl: String,
    val startDate: String,
    val endDate: String,
    val status: TripStatus,
    val members: List<TripMember>,
    val completionPercent: Int,
)

enum class TripStatus { PLANNING, ACTIVE, COMPLETED }

data class TripMember(
    val id: String,
    val nickname: String,
    val profileImageUrl: String?,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – Trip Lobby
// ─────────────────────────────────────────────────────────────────────────────

data class TripLobby(
    val tripId: String,
    val destination: String,
    val heroImageUrl: String,
    val dateRange: String,
    val members: List<TripMember>,
    val tasks: List<LobbyTask>,
    val overallProgress: Int,
)

data class LobbyTask(
    val id: String,
    val title: String,
    val progress: Int,
    val isComplete: Boolean,
    val taskType: LobbyTaskType,
)

enum class LobbyTaskType { VOTING, BOOKING, SCHEDULE, SETTLEMENT, OTHER }

// ─────────────────────────────────────────────────────────────────────────────
// UI – Create Trip
// ─────────────────────────────────────────────────────────────────────────────

data class CreateTripRequest(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val travelStyles: List<TravelStyle>,
    val inviteEmails: List<String> = emptyList(),
)

enum class TravelStyle { NATURE, URBAN, FOOD, CULTURE, ADVENTURE, RELAXATION, NIGHTLIFE }

data class DestinationSuggestion(
    val name: String,
    val country: String,
    val imageUrl: String,
    val airportCode: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – AI Itinerary Generation
// ─────────────────────────────────────────────────────────────────────────────

data class AiGenerationStatus(
    val jobId: String,
    val progressPercent: Int,
    val currentStep: String,
    val isComplete: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – Itinerary
// ─────────────────────────────────────────────────────────────────────────────

data class TripItinerary(
    val tripId: String,
    val title: String,
    val destination: String,
    val heroImageUrl: String,
    val days: List<ItineraryDay>,
)

data class ItineraryDay(
    val dayNumber: Int,
    val title: String,
    val events: List<ItineraryEvent>,
)

data class ItineraryEvent(
    val id: String,
    val time: String,
    val title: String,
    val description: String?,
    val placeId: String?,
    val category: EventCategory,
    val durationMinutes: Int?,
)

enum class EventCategory { TRANSPORT, ACCOMMODATION, FOOD, ACTIVITY, REST }

// ─────────────────────────────────────────────────────────────────────────────
// UI – Blind Voting
// ─────────────────────────────────────────────────────────────────────────────

data class VotingSession(
    val sessionId: String,
    val tripId: String,
    val title: String,
    val deadline: String,
    val candidates: List<VoteCandidate>,
    val totalVotes: Int,
    val hasVoted: Boolean,
    val myVoteId: String?,
)

data class VoteCandidate(
    val id: String,
    val label: String,
    val imageUrl: String?,
    val description: String?,
    val pricePerNight: Long?,
    val voteCount: Int,
    val votePercent: Float,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – Settlement
// ─────────────────────────────────────────────────────────────────────────────

data class Settlement(
    val tripId: String,
    val tripTitle: String,
    val totalAmount: Long,
    val currency: String,
    val myBalance: Long,
    val summary: List<SettlementItem>,
    val pendingTransfers: List<PendingTransfer>,
)

data class SettlementItem(
    val id: String,
    val category: String,
    val description: String,
    val amount: Long,
    val paidBy: String,
)

data class PendingTransfer(
    val fromNickname: String,
    val toNickname: String,
    val amount: Long,
    val isResolved: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI – Place Search
// ─────────────────────────────────────────────────────────────────────────────

data class PlaceSearchResult(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val address: String,
    val rating: Float,
    val reviewCount: Int,
    val imageUrl: String?,
    val priceLevel: Int?,
    val isInCart: Boolean = false,
)

enum class PlaceCategory { ALL, ATTRACTION, FOOD, CAFE, ACCOMMODATION }

// ─────────────────────────────────────────────────────────────────────────────
// UI – My Passport
// ─────────────────────────────────────────────────────────────────────────────

data class PassportStamp(
    val id: String,
    val cityCode: String,
    val cityName: String,
    val visitDate: String,
    val iconName: String,
    val accentColor: StampColor,
)

enum class StampColor { PRIMARY, SECONDARY, ERROR, TERTIARY, FIXED }

// ─────────────────────────────────────────────────────────────────────────────
// UI – Notifications
// ─────────────────────────────────────────────────────────────────────────────

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timeLabel: String,
    val isRead: Boolean,
)

enum class NotificationType { VOTING, SETTLEMENT, SCHEDULE_CHANGE, FLIGHT_UPDATE, GENERAL }

// ═════════════════════════════════════════════════════════════════════════════
// Backend API Models
// ═════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Auth  (POST /auth/kakao/login, POST /auth/google/login)
// ─────────────────────────────────────────────────────────────────────────────

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
)

data class KakaoLoginRequest(val accessToken: String)

data class GoogleLoginRequest(@com.google.gson.annotations.SerializedName("id_token") val idToken: String)

data class TokenRefreshRequest(val refreshToken: String)

data class FcmTokenRequest(val fcmToken: String)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Band  (GET /api/bands, POST /api/bands, etc.)
// ─────────────────────────────────────────────────────────────────────────────

enum class BandStatus { PLANNING, VOTING, GENERATING, TRAVELLING, DONE }

enum class BandRole { OWNER, MEMBER }

enum class BandTravelStyle { RELAXED, PACKED }

data class BandResponse(
    val id: Long,
    val name: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val inviteCode: String?,
    val status: BandStatus,
    val isOwner: Boolean,
    val isOverseas: Boolean,
    val travelStyle: BandTravelStyle,
    val accommodationName: String?,
    val memberCount: Int,
)

data class BandMemberResponse(
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
    val role: BandRole,
    val isReady: Boolean,
    val joinedAt: String,
    val bookmarkCount: Int,
)

data class BandCreateRequest(
    val name: String,
    val startDate: String,
    val endDate: String,
    val destination: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val countryCode: String,
    val overseas: Boolean,
    val travelStyle: BandTravelStyle,
    val accommodationName: String? = null,
    val accommodationLat: Double? = null,
    val accommodationLng: Double? = null,
)

data class BandJoinRequest(val inviteCode: String)

data class BandInviteCodeResponse(
    val bandId: Long,
    val inviteCode: String,
    val inviteCodeExpiredAt: String,
    val inviteShareLink: String?,
    val inviteDeepLink: String?,
)

data class BandReadyResponse(
    val bandId: Long,
    val userId: Long,
    val isReady: Boolean,
    val readyCount: Long,
    val totalCount: Long,
    val allReady: Boolean,
    val bandStatus: BandStatus,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Schedule  (GET /api/bands/{bandId}/schedule)
// ─────────────────────────────────────────────────────────────────────────────

enum class PlaceApiSource { KAKAO, GOOGLE }

enum class ApiPlaceCategory { FOOD, CULTURE, ACTIVITY, SHOPPING, NATURE, ETC }

data class ScheduleResponse(
    val bandId: Long,
    val startDate: String,
    val endDate: String,
    val days: List<ScheduleDayResponse>,
)

data class ScheduleDayResponse(
    val dayNumber: Int,
    val date: String,
    val slots: List<ScheduleSlotResponse>,
)

data class ScheduleSlotResponse(
    val scheduleId: Long,
    val slotOrder: Int,
    val startTime: String,
    val durationMinutes: Int?,
    val travelTimeFromPrev: Int?,
    val place: SchedulePlaceInfo,
)

data class SchedulePlaceInfo(
    val placeId: Long,
    val apiSource: PlaceApiSource,
    val name: String,
    val category: ApiPlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val rating: Float?,
    val thumbnailUrl: String?,
)

data class ScheduleAltResponse(
    val scheduleAltId: Long,
    val category: ApiPlaceCategory,
    val priorityScore: Float,
    val place: SchedulePlaceInfo,
)

data class ScheduleSwapRequest(
    val scheduleId: Long,
    val newPlaceId: Long,
)

data class PlanBRequest(val targetPlaceId: Long)

data class PlanBResponse(
    val placeId: Long,
    val category: ApiPlaceCategory,
    val recommendScore: Double,
    val distanceKmToTarget: Double,
    val searchRadiusKmUsed: Double,
    val fallbackLevel: Int,
    val fromOverflow: Boolean,
    val placeInfo: SchedulePlaceInfo,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Vote
// ─────────────────────────────────────────────────────────────────────────────

data class VotePlaceResponse(
    val placeId: Long,
    val apiSource: PlaceApiSource,
    val name: String,
    val category: ApiPlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val rating: Float?,
    val thumbnailUrl: String?,
    val myBookmark: Boolean,
    val myVoteResult: Int?,
)

data class VoteRequest(
    val placeId: Long,
    val result: Int,
)

data class VoteResponse(
    val voteId: Long,
    val placeId: Long,
    val result: Int,
    val votedAt: String,
)

data class VoteStatusResponse(
    val totalPlaces: Int,
    val votedCount: Int,
    val isComplete: Boolean,
)

data class GroupVoteStatusResponse(
    val totalMembers: Int,
    val completedMembers: Int,
    val isAllComplete: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Place Search  (GET /api/bands/{bandId}/places/search)
// Prefixed "Api" to distinguish from the UI-level PlaceSearchResult above.
// ─────────────────────────────────────────────────────────────────────────────

data class ApiPlaceSearchResult(
    val placeId: Long?,
    val apiSource: PlaceApiSource,
    val externalId: String,
    val name: String,
    val category: ApiPlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val rating: Float?,
    val thumbnailUrl: String?,
    val isBookmarked: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Expense
// ─────────────────────────────────────────────────────────────────────────────

data class ExpenseResponse(
    val id: Long,
    val payerId: Long,
    val payerName: String,
    val itemName: String,
    val amount: Double,
    val currency: String,
    val receiptUrl: String?,
    val paidAt: String,
    val memberIds: List<Long>,
)

data class ExpenseCreateRequest(
    val itemName: String,
    val amount: Double,
    val currency: String,
    val receiptUrl: String? = null,
    val ocrRaw: String? = null,
    val paidAt: String,
    val memberIds: List<Long>,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Settlement  (GET /api/bands/{bandId}/settlement)
// ─────────────────────────────────────────────────────────────────────────────

data class SettlementResponse(
    val baseCurrency: String,
    val totalExpense: Double,
    val memberSummaries: List<MemberSettlementSummary>,
    val transactions: List<SettlementTransaction>,
)

data class MemberSettlementSummary(
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
    val totalPaid: Double,
    val totalShare: Double,
    val balance: Double,
)

data class SettlementTransaction(
    val fromUserId: Long,
    val fromName: String,
    val toUserId: Long,
    val toName: String,
    val amount: Double,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Notification  (GET /api/notifications)
// Prefixed "Api" to distinguish from the UI-level NotificationType above.
// ─────────────────────────────────────────────────────────────────────────────

enum class ApiNotificationType {
    MEMBER_READY, MEMBER_JOINED, VOTE_STARTED, SCHEDULE_UPDATED, SETTLEMENT_REQUEST,
    TRIP_ENDED,  // 2026-05-23 백엔드 추가
}

/**
 * 알림 목록 응답. bandId·title 포함 (백엔드 NotificationResponse Record 기준).
 */
data class NotificationResponse(
    val id: Long,
    val bandId: Long?,
    val type: ApiNotificationType,
    val title: String,
    val content: String,
    val isRead: Boolean,
    val createdAt: String,
)

/** GET /api/notifications/unread-count → {"count": N} */
data class UnreadCountResponse(val count: Long)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Destination  (GET /api/destinations/popular, /api/destinations/search)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 여행지 탐색 응답 (DestinationResponse Record).
 * 필드명은 백엔드 Java Record와 1:1 — lat/lng/overseas 주의.
 */
data class DestinationResponse(
    val name: String,
    val country: String,
    val countryCode: String,
    val lat: Double,
    val lng: Double,
    val overseas: Boolean,
    val region: String?,
    val description: String?,
    val thumbnailUrl: String?,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Place Pick (장바구니)
// GET  /api/bands/{bandId}/picks  → PlacePickListResponse
// POST /api/bands/{bandId}/picks  → PlacePickRequest
// DELETE /api/bands/{bandId}/picks/{placeId}
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 장바구니 담기 요청 (PlacePickRequest Record).
 * 검색 결과의 전체 장소 정보를 전달해야 한다 — placeId 단독 전달 불가.
 */
data class PlacePickRequest(
    val apiSource: PlaceApiSource,
    val externalId: String,
    val name: String,
    val category: ApiPlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val rating: Float?,
    val thumbnailUrl: String?,
    val openingHoursJson: String? = null,
    val estimatedDuration: Int? = null,
)

/** 장바구니 단건 응답 (PlacePickResponse Record). */
data class PlacePickResponse(
    val placeBookmarkId: Long,
    val placeId: Long,
    val apiSource: PlaceApiSource,
    val externalId: String,
    val name: String,
    val category: ApiPlaceCategory,
    val densityPoint: Int,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val rating: Float?,
    val thumbnailUrl: String?,
    val openingHoursJson: String?,
    val estimatedDuration: Int,
    val createdAt: String,
)

/** GET /api/bands/{bandId}/picks 래퍼 (PlacePickListResponse Record). */
data class PlacePickListResponse(
    val currentCount: Int,
    val maxCount: Int,
    val items: List<PlacePickResponse>,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – User Profile  (GET /api/users/me, PUT /api/users/me)
// ─────────────────────────────────────────────────────────────────────────────

/** UserProfileResponse Record. oauthProvider 필드명 주의. */
data class UserProfileResponse(
    val id: Long,
    val email: String?,
    val name: String,
    val profileImageUrl: String?,
    val oauthProvider: String,
)

/** PUT /api/users/me 요청. name은 @NotBlank 필수값. */
data class UserProfileUpdateRequest(
    val name: String,
    val profileImageUrl: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Backend – Notification Settings  (GET/PATCH /api/users/notification-settings)
// ─────────────────────────────────────────────────────────────────────────────

/** NotificationSettingResponse Record — tripEnded 없음 주의. */
data class NotificationSettingsResponse(
    val voteStarted: Boolean,
    val scheduleUpdated: Boolean,
    val settlementRequest: Boolean,
    val memberReady: Boolean,
    val memberJoined: Boolean,
)

/**
 * PATCH /api/users/notification-settings 요청 (NotificationSettingRequest Record).
 * 한 번에 하나의 타입만 on/off — {type, enabled} 형식.
 */
data class NotificationSettingUpdateRequest(
    val type: ApiNotificationType,
    val enabled: Boolean,
)
