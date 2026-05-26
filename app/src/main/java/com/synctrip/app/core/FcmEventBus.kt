package com.synctrip.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * FCM 메시지를 UI 레이어로 전달하는 이벤트 버스.
 * Service → NavGraph 방향으로만 흐른다.
 * extraBufferCapacity = 8: 빠르게 연속 수신된 알림이 드롭되지 않도록 버퍼 확보.
 */
object FcmEventBus {

    // replay=1: collector 구독 전 도착한 마지막 이벤트도 전달
    private val _events = MutableSharedFlow<FcmEvent>(replay = 1, extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** 발신 — Service에서 호출 */
    fun send(title: String, body: String) {
        _events.tryEmit(FcmEvent(title, body))
    }

    data class FcmEvent(val title: String, val body: String)
}
