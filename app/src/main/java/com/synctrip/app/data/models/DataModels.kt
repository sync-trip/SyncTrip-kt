package com.synctrip.app.data.models

// ─────────────────────────────────────────────────────────────────────────────
// Auth  (POST /auth/kakao/login, POST /auth/google/login)
// ─────────────────────────────────────────────────────────────────────────────

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
)

data class KakaoLoginRequest(val accessToken: String)

data class GoogleLoginRequest(val idToken: String)

data class TokenRefreshRequest(val refreshToken: String)

// ─────────────────────────────────────────────────────────────────────────────
// Band  (GET /api/bands, POST /api/bands, etc.)
// ─────────────────────────────────────────────────────────────────────────────

enum class BandStatus { PLANNING, VOTING, GENERATING, TRAVELLING, DONE }

enum class BandRole { OWNER, MEMBER }

// BandTravelStyle — renamed to avoid conflict with future UI-level enums
enum class BandTravelStyle { RELAXED, PACKED }

data class BandResponse(
    val id: Long,
    val name: String,
    val destination: String,
    val startDate: String,          // "yyyy-MM-dd"
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
    val joinedAt: String,           // ISO-8601 LocalDateTime
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
// Schedule  (GET /api/bands/{bandId}/schedule)
// ─────────────────────────────────────────────────────────────────────────────

enum class PlaceApiSource { KAKAO, GOOGLE }

// ApiPlaceCategory — renamed to avoid conflict with any UI-level PlaceCategory
enum class ApiPlaceCategory { FOOD, CULTURE, ACTIVITY, SHOPPING, NATURE, ETC }

data class ScheduleResponse(
    val bandId: Long,
    val startDate: String,
    val endDate: String,
    val days: List<ScheduleDayResponse>,
)

data class ScheduleDayResponse(
    val dayNumber: Int,
    val date: String,               // "yyyy-MM-dd"
    val slots: List<ScheduleSlotResponse>,
)

data class ScheduleSlotResponse(
    val scheduleId: Long,
    val slotOrder: Int,
    val startTime: String,          // "HH:mm"
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
// Vote  (GET /api/bands/{bandId}/votes/places, POST /api/bands/{bandId}/votes)
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
    val myVoteResult: Int?,         // 1=LIKE, -1=DISLIKE, null=미투표
)

data class VoteRequest(
    val placeId: Long,
    val result: Int,                // 1=LIKE, -1=DISLIKE
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
// Place Search  (GET /api/bands/{bandId}/places/search)
// ─────────────────────────────────────────────────────────────────────────────

data class PlaceSearchResult(
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
// Expense  (GET/POST /api/bands/{bandId}/expenses)
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
// Settlement  (GET /api/bands/{bandId}/settlement)
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
    val balance: Double,            // positive = receives, negative = owes
)

data class SettlementTransaction(
    val fromUserId: Long,
    val fromName: String,
    val toUserId: Long,
    val toName: String,
    val amount: Double,
)

// ─────────────────────────────────────────────────────────────────────────────
// Notification  (GET /api/notifications)
// ─────────────────────────────────────────────────────────────────────────────

enum class NotificationType {
    MEMBER_READY, MEMBER_JOINED, VOTE_STARTED, SCHEDULE_UPDATED, SETTLEMENT_REQUEST
}

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val content: String,
    val isRead: Boolean,
    val createdAt: String,
)
