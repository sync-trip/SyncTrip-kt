package com.synctrip.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synctrip.app.data.models.AlbumPhotoMapResponse
import com.synctrip.app.data.models.AlbumPhotoResponse
import com.synctrip.app.data.repository.AlbumRepository
import com.synctrip.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 공유 앨범 화면 UI 상태 */
data class AlbumUiState(
    /** 피드 목록 (최신순, Base64 이미지 포함) */
    val photos: List<AlbumPhotoResponse> = emptyList(),
    /** 지도 핀 목록 (좌표 있는 사진만, Base64 제외) */
    val mapPins: List<AlbumPhotoMapResponse> = emptyList(),
    val isLoading: Boolean = false,
    /** 사진 업로드 진행 중 여부 */
    val isUploading: Boolean = false,
    val error: String? = null,
)

/**
 * 공유 앨범(USR-023) ViewModel.
 * 피드 로드, 지도 핀 로드, 사진 업로드/삭제를 담당한다.
 */
class AlbumViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    /** 피드 + 지도 핀 동시 로드 */
    fun loadAlbum(bandId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // 피드와 지도 핀은 독립적이므로 순차 호출 (동시 실패 방지)
                val feed = AlbumRepository.getFeed(bandId)
                val pins = AlbumRepository.getMapPins(bandId)
                Pair(feed, pins)
            }.onSuccess { (feed, pins) ->
                _uiState.update { it.copy(photos = feed, mapPins = pins, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    /**
     * 사진 업로드.
     * @param photoData  Base64 인코딩된 이미지 문자열
     * @param caption    사진 설명 (선택)
     * @param latitude   EXIF GPS 위도 (선택)
     * @param longitude  EXIF GPS 경도 (선택)
     * @param takenAt    EXIF 촬영 시각 ISO 8601 (선택)
     * @param bandId     업로드할 밴드 ID
     * @param onSuccess  업로드 성공 콜백
     */
    fun uploadPhoto(
        bandId: Long,
        photoData: String,
        caption: String?,
        latitude: Double?,
        longitude: Double?,
        takenAt: String?,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            runCatching {
                AlbumRepository.uploadPhoto(bandId, photoData, caption, latitude, longitude, takenAt)
            }.onSuccess { newPhoto ->
                // 낙관적 업데이트: 새 사진을 피드 맨 앞에 삽입
                _uiState.update { state ->
                    val updatedPins = if (newPhoto.latitude != null && newPhoto.longitude != null) {
                        state.mapPins + AlbumPhotoMapResponse(
                            id           = newPhoto.id,
                            latitude     = newPhoto.latitude,
                            longitude    = newPhoto.longitude,
                            uploaderName = newPhoto.uploaderName,
                        )
                    } else state.mapPins
                    state.copy(
                        photos     = listOf(newPhoto) + state.photos,
                        mapPins    = updatedPins,
                        isUploading = false,
                    )
                }
                onSuccess()
            }.onFailure { e ->
                _uiState.update { it.copy(isUploading = false, error = e.toUserMessage()) }
            }
        }
    }

    /**
     * 사진 삭제.
     * 낙관적으로 UI에서 먼저 제거하고, 실패 시 재로드로 복구한다.
     */
    fun deletePhoto(bandId: Long, photoId: Long) {
        viewModelScope.launch {
            // 낙관적 삭제
            _uiState.update { state ->
                state.copy(
                    photos  = state.photos.filter { it.id != photoId },
                    mapPins = state.mapPins.filter { it.id != photoId },
                )
            }
            runCatching {
                AlbumRepository.deletePhoto(bandId, photoId)
            }.onFailure {
                // 삭제 실패 시 피드 재로드로 실제 상태 복구
                loadAlbum(bandId)
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
