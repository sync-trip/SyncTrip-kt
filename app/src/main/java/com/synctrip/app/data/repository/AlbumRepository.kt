package com.synctrip.app.data.repository

import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient

/**
 * 공유 앨범(USR-023) Repository.
 * 모든 함수는 suspend 함수로 API 호출을 담당하며,
 * 에러 처리는 ViewModel 레이어에서 runCatching으로 수행한다.
 */
object AlbumRepository {

    /** 피드 목록 로드 — 최신순, Base64 이미지 포함 */
    suspend fun getFeed(bandId: Long): List<AlbumPhotoResponse> =
        ApiClient.api.getAlbumFeed(bandId)

    /** 지도 핀용 목록 — 좌표 있는 사진만, Base64 제외 */
    suspend fun getMapPins(bandId: Long): List<AlbumPhotoMapResponse> =
        ApiClient.api.getAlbumMapPins(bandId)

    /** 사진 업로드 — EXIF에서 추출한 좌표·촬영 시각 포함 전달 */
    suspend fun uploadPhoto(
        bandId: Long,
        photoData: String,
        caption: String?,
        latitude: Double?,
        longitude: Double?,
        takenAt: String?,
    ): AlbumPhotoResponse = ApiClient.api.uploadAlbumPhoto(
        bandId,
        AlbumPhotoUploadRequest(
            photoData = photoData,
            caption   = caption?.takeIf { it.isNotBlank() },
            latitude  = latitude,
            longitude = longitude,
            takenAt   = takenAt,
        ),
    )

    /** 캡션 수정 */
    suspend fun updateCaption(bandId: Long, photoId: Long, caption: String?): AlbumPhotoResponse =
        ApiClient.api.updateAlbumPhoto(bandId, photoId, AlbumPhotoUpdateRequest(caption))

    /** 사진 삭제 */
    suspend fun deletePhoto(bandId: Long, photoId: Long) =
        ApiClient.api.deleteAlbumPhoto(bandId, photoId)
}
