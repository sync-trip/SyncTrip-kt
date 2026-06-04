package com.synctrip.app.util

import retrofit2.HttpException
import java.io.IOException

/** Throwable을 사용자에게 보여줄 메시지로 변환 */
fun Throwable.toUserMessage(): String = when {
    this is IOException -> "네트워크 연결을 확인해주세요."
    this is HttpException -> when (code()) {
        401 -> "로그인이 필요합니다."
        403 -> "권한이 없습니다."
        in 500..599 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        else -> "오류가 발생했습니다. (${code()})"
    }
    else -> "오류가 발생했습니다."
}
