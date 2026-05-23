package com.synctrip.app.data.repository

import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient

object ScheduleRepository {

    suspend fun generateSchedule(bandId: Long) =
        ApiClient.api.generateSchedule(bandId)

    suspend fun getSchedule(bandId: Long): ScheduleResponse =
        ApiClient.api.getSchedule(bandId)

    suspend fun getAlts(bandId: Long): List<ScheduleAltResponse> =
        ApiClient.api.getScheduleAlts(bandId)

    suspend fun swapSlot(bandId: Long, scheduleId: Long, newPlaceId: Long) =
        ApiClient.api.swapScheduleSlot(bandId, ScheduleSwapRequest(scheduleId, newPlaceId))

    suspend fun getPlanB(bandId: Long, targetPlaceId: Long): List<PlanBResponse> =
        ApiClient.api.getPlanB(bandId, PlanBRequest(targetPlaceId))
}
