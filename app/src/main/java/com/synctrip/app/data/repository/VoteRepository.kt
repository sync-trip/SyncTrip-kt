package com.synctrip.app.data.repository

import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient

object VoteRepository {

    suspend fun getVotePlaces(bandId: Long): List<VotePlaceResponse> =
        ApiClient.api.getVotePlaces(bandId)

    /** result: 1 = 좋아요, -1 = 싫어요, 0 = 패스 */
    suspend fun submitVote(bandId: Long, placeId: Long, result: Int): VoteResponse =
        ApiClient.api.submitVote(bandId, VoteRequest(placeId, result))

    suspend fun getMyVoteStatus(bandId: Long): VoteStatusResponse =
        ApiClient.api.getVoteStatus(bandId)

    suspend fun getGroupVoteStatus(bandId: Long): GroupVoteStatusResponse =
        ApiClient.api.getGroupVoteStatus(bandId)

    suspend fun getVoteResults(bandId: Long): List<VotePlaceResult> =
        ApiClient.api.getVoteResults(bandId)
}
