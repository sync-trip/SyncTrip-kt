package com.synctrip.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.synctrip.app.data.models.AlbumPhotoMapResponse
import com.synctrip.app.data.models.AlbumPhotoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// 앨범 뷰 탭 열거형
// ─────────────────────────────────────────────────────────────────────────────

private enum class AlbumViewTab { FEED, MAP }

// ─────────────────────────────────────────────────────────────────────────────
// AlbumContent — TripBandHubScreen PHOTO 탭에 임베드되는 공유 앨범 콘텐츠
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 공유 앨범 콘텐츠 (USR-023).
 * 피드 탭(인스타그램 스타일)과 지도 탭(좌표 핀)을 제공한다.
 * 사진 선택·EXIF 추출·Base64 변환을 내부에서 처리하고
 * onUploadPhoto 콜백으로 상위에 업로드를 위임한다.
 *
 * @param photos           피드 목록 (최신순)
 * @param mapPins          지도 핀 목록 (좌표 있는 사진만)
 * @param isLoading        피드 로딩 중 여부
 * @param isUploading      업로드 진행 중 여부
 * @param currentUserId    현재 로그인 사용자 ID (삭제 권한 판단용)
 * @param destinationLat   밴드 여행지 위도 — 지도 탭 초기 중심 좌표
 * @param destinationLng   밴드 여행지 경도
 * @param onUploadPhoto    업로드 실행 콜백
 * @param onDeletePhoto    삭제 실행 콜백
 */
@Composable
fun AlbumContent(
    photos: List<AlbumPhotoResponse>,
    mapPins: List<AlbumPhotoMapResponse>,
    isLoading: Boolean,
    isUploading: Boolean,
    currentUserId: Long,
    destinationLat: Double,
    destinationLng: Double,
    onUploadPhoto: (
        photoData: String,
        caption: String?,
        latitude: Double?,
        longitude: Double?,
        takenAt: String?,
    ) -> Unit,
    onDeletePhoto: (photoId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(AlbumViewTab.FEED) }
    val feedListState = rememberLazyListState()

    // 업로드 다이얼로그 상태
    var pendingPhotoData  by remember { mutableStateOf<String?>(null) }
    var pendingLatitude   by remember { mutableStateOf<Double?>(null) }
    var pendingLongitude  by remember { mutableStateOf<Double?>(null) }
    var pendingTakenAt    by remember { mutableStateOf<String?>(null) }
    var pendingBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var captionInput      by remember { mutableStateOf("") }
    var showUploadDialog  by remember { mutableStateOf(false) }

    // 갤러리 선택 런처 — 선택 후 EXIF 추출 + Base64 변환
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                extractPhotoData(context, uri)
            }
            result?.let { (data, bitmap, lat, lng, takenAt) ->
                pendingPhotoData  = data
                pendingBitmap     = bitmap
                pendingLatitude   = lat
                pendingLongitude  = lng
                pendingTakenAt    = takenAt
                captionInput      = ""
                showUploadDialog  = true
            }
        }
    }

    // 지도 핀 클릭 → 피드 탭으로 전환 + 해당 아이템으로 스크롤
    val onPinClick: (photoId: Long) -> Unit = { photoId ->
        currentTab = AlbumViewTab.FEED
        val index = photos.indexOfFirst { it.id == photoId }
        if (index >= 0) scope.launch { feedListState.animateScrollToItem(index) }
    }

    // 업로드 확인 다이얼로그
    if (showUploadDialog && pendingPhotoData != null) {
        AlbumUploadDialog(
            bitmap       = pendingBitmap,
            captionInput = captionInput,
            onCaptionChange = { captionInput = it },
            hasLocation  = pendingLatitude != null,
            isUploading  = isUploading,
            onConfirm    = {
                val data = pendingPhotoData ?: return@AlbumUploadDialog
                onUploadPhoto(data, captionInput.takeIf { it.isNotBlank() }, pendingLatitude, pendingLongitude, pendingTakenAt)
                showUploadDialog = false
                pendingPhotoData = null
                pendingBitmap    = null
            },
            onDismiss = {
                showUploadDialog = false
                pendingPhotoData = null
                pendingBitmap    = null
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 피드/지도 전환 탭
            AlbumTabRow(
                selected  = currentTab,
                onSelect  = { currentTab = it },
                hasPins   = mapPins.isNotEmpty(),
            )

            when (currentTab) {
                AlbumViewTab.FEED -> AlbumFeedList(
                    photos        = photos,
                    isLoading     = isLoading,
                    currentUserId = currentUserId,
                    listState     = feedListState,
                    onDeletePhoto = onDeletePhoto,
                    modifier      = Modifier.fillMaxSize(),
                )
                AlbumViewTab.MAP  -> AlbumMapView(
                    pins           = mapPins,
                    destinationLat = destinationLat,
                    destinationLng = destinationLng,
                    onPinClick     = onPinClick,
                    modifier       = Modifier.fillMaxSize(),
                )
            }
        }

        // 사진 업로드 FAB
        FloatingActionButton(
            onClick      = { galleryLauncher.launch("image/*") },
            modifier     = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color    = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Outlined.AddAPhoto,
                    contentDescription = "사진 추가",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 피드/지도 탭 전환 바
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumTabRow(
    selected: AlbumViewTab,
    onSelect: (AlbumViewTab) -> Unit,
    hasPins: Boolean,
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier         = Modifier.fillMaxWidth(),
    ) {
        Tab(
            selected = selected == AlbumViewTab.FEED,
            onClick  = { onSelect(AlbumViewTab.FEED) },
            icon     = { Icon(Icons.Outlined.ViewStream, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text     = { Text("피드", style = MaterialTheme.typography.labelMedium) },
        )
        Tab(
            selected = selected == AlbumViewTab.MAP,
            onClick  = { onSelect(AlbumViewTab.MAP) },
            icon     = {
                BadgedBox(
                    badge = {
                        if (hasPins) Badge()
                    }
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            text = { Text("지도", style = MaterialTheme.typography.labelMedium) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 피드 목록 — 인스타그램 스타일 LazyColumn
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumFeedList(
    photos: List<AlbumPhotoResponse>,
    isLoading: Boolean,
    currentUserId: Long,
    listState: LazyListState,
    onDeletePhoto: (photoId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        photos.isEmpty() -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "아직 사진이 없어요",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "우하단 버튼으로 여행 사진을 공유해보세요",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        else -> LazyColumn(
            state         = listState,
            modifier      = modifier,
            contentPadding = PaddingValues(bottom = 80.dp), // FAB 공간 확보
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { _, photo ->
                AlbumFeedCard(
                    photo         = photo,
                    currentUserId = currentUserId,
                    onDeletePhoto = { onDeletePhoto(photo.id) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 피드 카드 — 인스타그램 스타일 단일 포스트
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumFeedCard(
    photo: AlbumPhotoResponse,
    currentUserId: Long,
    onDeletePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Base64 → Bitmap 변환 (백그라운드에서 한 번만 수행)
    val bitmap by produceState<Bitmap?>(initialValue = null, photo.id, photo.photoData) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(photo.photoData, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title  = { Text("사진 삭제") },
            text   = { Text("이 사진을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeletePhoto() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }

    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            // ── 헤더: 업로더 아바타 + 이름 + 위치 + 메뉴 ────────────────────
            Row(
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 이름 첫 글자 아바타 (프로필 이미지 URL 없음)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = photo.uploaderName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = photo.uploaderName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // GPS 좌표가 있으면 위치 정보 표시
                    if (photo.latitude != null && photo.longitude != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text  = formatCoordinate(photo.latitude, photo.longitude),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 업로더 본인만 더보기 메뉴 표시
                if (photo.uploaderId == currentUserId) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "더보기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded         = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text    = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showDeleteDialog = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ── 사진 ───────────────────────────────────────────────────────────
            bitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = "앨범 사진",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),           // 1:1 정사각형 (인스타 기본)
                    contentScale       = ContentScale.Crop,
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }

            // ── 하단: 캡션 + 시간 ─────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!photo.caption.isNullOrBlank()) {
                    Text(
                        text  = photo.caption,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text  = formatRelativeTime(photo.uploadedAt),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 지도 뷰 — GPS 좌표 있는 사진 핀 표시
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumMapView(
    pins: List<AlbumPhotoMapResponse>,
    destinationLat: Double,
    destinationLng: Double,
    onPinClick: (photoId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 핀이 있으면 핀 중심, 없으면 밴드 여행지 좌표로 초기 중심 설정
    val centerLat = if (pins.isNotEmpty()) pins.map { it.latitude }.average() else destinationLat
    val centerLng = if (pins.isNotEmpty()) pins.map { it.longitude }.average() else destinationLng
    // 핀 수에 따라 줌 레벨 결정 (여러 핀이 퍼져있으면 넓게, 없으면 도시 레벨)
    val zoom = if (pins.isNotEmpty()) 12f else 11f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), zoom)
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings          = MapUiSettings(zoomControlsEnabled = true),
        ) {
            pins.forEach { pin ->
                Marker(
                    state   = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                    title   = pin.uploaderName,
                    snippet = "탭하여 피드에서 보기",
                    onClick = {
                        onPinClick(pin.id)
                        false   // false 반환 시 기본 info window도 표시됨
                    },
                )
            }
        }

        // 핀이 없을 때 지도 위에 안내 카드 오버레이
        if (pins.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.PinDrop,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            "아직 위치 정보가 있는 사진이 없어요",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Text(
                            "GPS가 켜진 상태에서 촬영한 사진을 올리면 핀으로 표시돼요",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 업로드 확인 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumUploadDialog(
    bitmap: Bitmap?,
    captionInput: String,
    onCaptionChange: (String) -> Unit,
    hasLocation: Boolean,
    isUploading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("사진 공유") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 선택한 사진 미리보기
                bitmap?.let {
                    Image(
                        bitmap      = it.asImageBitmap(),
                        contentDescription = "선택한 사진",
                        modifier    = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                // 캡션 입력
                OutlinedTextField(
                    value         = captionInput,
                    onValueChange = onCaptionChange,
                    label         = { Text("글 (선택)") },
                    placeholder   = { Text("이 순간을 설명해주세요...") },
                    modifier      = Modifier.fillMaxWidth(),
                    maxLines      = 3,
                    enabled       = !isUploading,
                )

                // 위치 정보 포함 여부 안내
                if (hasLocation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "위치 정보가 포함돼요",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = !isUploading,
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("공유")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("취소") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 유틸리티 함수
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 갤러리에서 선택한 URI에서 이미지 데이터, EXIF GPS, 촬영 시각을 추출한다.
 * @return Triple(Base64 문자열, Bitmap, 위도, 경도, 촬영시각 ISO 8601) 또는 null
 */
private fun extractPhotoData(
    context: Context,
    uri: Uri,
): PhotoExtractionResult? {
    return runCatching {
        // 1) 이미지 바이트 읽기 — Base64 인코딩
        val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val base64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)

        // 2) Bitmap 미리보기 (다이얼로그 표시용, 메모리 절약을 위해 샘플링)
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        // 3) EXIF 메타데이터 추출 (GPS, 촬영 시각)
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val latLong = FloatArray(2)
        val hasGps = exif?.getLatLong(latLong) == true

        // EXIF 날짜 형식: "yyyy:MM:dd HH:mm:ss" → ISO 8601 변환
        val rawDate = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
        val isoDate = rawDate?.let {
            runCatching {
                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val date: Date = sdf.parse(it) ?: return@let null
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(date)
            }.getOrNull()
        }

        PhotoExtractionResult(
            photoData  = base64,
            bitmap     = bitmap,
            latitude   = if (hasGps) latLong[0].toDouble() else null,
            longitude  = if (hasGps) latLong[1].toDouble() else null,
            takenAt    = isoDate,
        )
    }.getOrNull()
}

/** extractPhotoData 반환값 컨테이너 */
private data class PhotoExtractionResult(
    val photoData: String,
    val bitmap: Bitmap?,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: String?,
)

/** 위도/경도 좌표를 간략한 문자열로 표현 */
private fun formatCoordinate(lat: Double, lng: Double): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    return "%.4f°%s, %.4f°%s".format(Math.abs(lat), latDir, Math.abs(lng), lngDir)
}

/**
 * uploadedAt(ISO 8601 문자열)을 상대 시간으로 변환.
 * 예: "5분 전", "2시간 전", "3일 전"
 */
private fun formatRelativeTime(uploadedAt: String): String {
    return runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val then = sdf.parse(uploadedAt)?.time ?: return "방금 전"
        val diffMs = System.currentTimeMillis() - then
        val diffMin = diffMs / 60_000
        when {
            diffMin < 1    -> "방금 전"
            diffMin < 60   -> "${diffMin}분 전"
            diffMin < 1440 -> "${diffMin / 60}시간 전"
            else           -> "${diffMin / 1440}일 전"
        }
    }.getOrDefault(uploadedAt)
}
