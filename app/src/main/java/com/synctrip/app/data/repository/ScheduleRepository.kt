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

    suspend fun reorderSchedule(bandId: Long, request: ScheduleReorderRequest) =
        ApiClient.api.reorderSchedule(bandId, request)

    suspend fun startEditing(bandId: Long) =
        ApiClient.api.startEditing(bandId)

    suspend fun finishEditing(bandId: Long) =
        ApiClient.api.finishEditing(bandId)

    suspend fun moveSlot(bandId: Long, request: ScheduleMoveRequest) =
        ApiClient.api.moveSchedule(bandId, request)

    suspend fun getPlanB(bandId: Long, targetPlaceId: Long): List<PlanBResponse> =
        ApiClient.api.getPlanB(bandId, PlanBRequest(targetPlaceId))
}
