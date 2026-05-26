package com.synctrip.app.data.repository

import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient

object BandRepository {

    suspend fun getBands(): List<BandResponse> =
        ApiClient.api.getBands()

    suspend fun createBand(request: BandCreateRequest): BandResponse =
        ApiClient.api.createBand(request)

    suspend fun joinBand(inviteCode: String): BandResponse =
        ApiClient.api.joinBand(BandJoinRequest(inviteCode))

    suspend fun getMembers(bandId: Long): List<BandMemberResponse> =
        ApiClient.api.getBandMembers(bandId)

    suspend fun getInviteCode(bandId: Long): BandInviteCodeResponse =
        ApiClient.api.getInviteCode(bandId)

    suspend fun setReady(bandId: Long): BandReadyResponse =
        ApiClient.api.setReady(bandId)

    suspend fun cancelReady(bandId: Long): BandReadyResponse =
        ApiClient.api.cancelReady(bandId)

    suspend fun advanceBandStatus(bandId: Long): BandStatusTransitionResponse =
        ApiClient.api.advanceBandStatus(bandId)

    suspend fun getPicks(bandId: Long): PlacePickListResponse =
        ApiClient.api.getPicks(bandId)

    /** 장바구니 조회 — 이미 정의됨 */
    // getPicks 는 위에 있음

    /**
     * 장소 검색 — 국내/해외 모두 Google Places Text Search 사용.
     * keyword 필수, 없으면 백엔드에서 400 반환.
     */
    suspend fun searchPlaces(
        bandId: Long,
        keyword: String? = null,
        category: String? = null,
    ): List<ApiPlaceSearchResult> =
        ApiClient.api.searchPlaces(bandId, keyword, category)

    /** 장소 담기 — 장소 전체 정보를 서버에 전달 (placeId 단독 전달 불가) */
    suspend fun addPick(bandId: Long, request: PlacePickRequest): PlacePickResponse =
        ApiClient.api.addPick(bandId, request)

    /** 장소 삭제 — placeId (서버 내부 ID) 사용 */
    suspend fun deletePick(bandId: Long, placeId: Long) =
        ApiClient.api.deletePick(bandId, placeId)

    suspend fun deleteBand(bandId: Long) =
        ApiClient.api.deleteBand(bandId)

    /** 로그인한 유저 프로필 조회 */
    suspend fun getMyProfile(): UserProfileResponse =
        ApiClient.api.getMyProfile()
}
