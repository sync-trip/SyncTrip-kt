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
}
