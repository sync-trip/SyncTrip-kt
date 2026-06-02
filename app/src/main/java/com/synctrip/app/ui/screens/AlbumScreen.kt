package com.synctrip.app.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import java.util.TimeZone

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
@OptIn(ExperimentalMaterial3Api::class)
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
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
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

    // 갤러리 그리드 시트 상태 — MediaStore를 직접 조회한 사진 URI 목록
    var showPickerSheet by remember { mutableStateOf(false) }
    var deviceImages    by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 그리드에서 사진 선택 → EXIF/GPS 추출 + 압축 후 업로드 다이얼로그로.
    // MediaStore content URI라 extractPhotoData의 setRequireOriginal()이 동작해 원본 GPS 접근 가능.
    val handlePicked: (Uri) -> Unit = { uri ->
        showPickerSheet = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { extractPhotoData(context, uri) }
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

    // MediaStore에서 기기 사진 목록을 읽어 그리드 시트 열기
    val openPickerSheet = {
        scope.launch {
            deviceImages = withContext(Dispatchers.IO) { queryDeviceImages(context) }
            showPickerSheet = true
        }
        Unit
    }

    // 사진/위치 권한 요청 런처 — 사진 읽기(전체/부분) 허용 시 그리드 오픈
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val canRead = granted[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            granted[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        if (canRead) openPickerSheet()
    }

    // 사진 추가 진입점 — 권한 확인 후 그리드 오픈, 없으면 요청
    val onAddPhotoClick = {
        if (hasMediaReadPermission(context)) {
            openPickerSheet()
        } else {
            permissionLauncher.launch(MEDIA_PERMISSIONS)
        }
    }

    // 지도 핀 클릭 → 피드 탭으로 전환 + 해당 아이템으로 스크롤
    val onPinClick: (photoId: Long) -> Unit = { photoId ->
        currentTab = AlbumViewTab.FEED
        val index = photos.indexOfFirst { it.id == photoId }
        if (index >= 0) scope.launch { feedListState.animateScrollToItem(index) }
    }

    // 새 사진이 맨 앞에 추가되면(업로드 등) 피드 최상단으로 자동 스크롤.
    // 낙관적 업데이트로 photos[0]이 바뀌므로 그 id를 키로 감지한다.
    // (핀 클릭 스크롤은 firstId가 안 바뀌어 이 효과를 재실행하지 않음)
    val newestPhotoId = photos.firstOrNull()?.id
    LaunchedEffect(newestPhotoId) {
        if (newestPhotoId != null && currentTab == AlbumViewTab.FEED) {
            feedListState.animateScrollToItem(0)
        }
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

    // 갤러리 사진 선택 그리드 시트
    if (showPickerSheet) {
        AlbumPickerSheet(
            images     = deviceImages,
            onPick     = handlePicked,
            onDismiss  = { showPickerSheet = false },
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
                AlbumViewTab.FEED -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = onRefresh,
                    modifier     = Modifier.fillMaxSize(),
                ) {
                    AlbumFeedList(
                        photos        = photos,
                        isLoading     = isLoading,
                        currentUserId = currentUserId,
                        listState     = feedListState,
                        onDeletePhoto = onDeletePhoto,
                        modifier      = Modifier.fillMaxSize(),
                    )
                }
                AlbumViewTab.MAP  -> AlbumMapView(
                    pins           = mapPins,
                    photos         = photos,
                    destinationLat = destinationLat,
                    destinationLng = destinationLng,
                    onPinClick     = onPinClick,
                    modifier       = Modifier.fillMaxSize(),
                )
            }
        }

        // 사진 업로드 FAB
        FloatingActionButton(
            onClick      = { onAddPhotoClick() },
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
    photos: List<AlbumPhotoResponse>,
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

    // 마커에 표시할 썸네일 — 피드(photos)의 Base64를 핀 id로 매칭해 작게 디코딩.
    // 지도용 응답(mapPins)에는 이미지가 없으므로 피드 데이터에서 가져온다.
    val pinThumbnails by produceState(initialValue = emptyMap<Long, Bitmap>(), pins, photos) {
        value = withContext(Dispatchers.IO) {
            val photoById = photos.associateBy { it.id }
            pins.mapNotNull { pin ->
                val data = photoById[pin.id]?.photoData ?: return@mapNotNull null
                runCatching {
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    // 마커는 작으므로 8배 축소 디코딩으로 메모리 절약
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }.getOrNull()?.let { pin.id to it }
            }.toMap()
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings          = MapUiSettings(zoomControlsEnabled = true),
        ) {
            pins.forEach { pin ->
                val thumb = pinThumbnails[pin.id]
                key(pin.id) {
                    // 사진 썸네일을 흰 틀 안에 보여주는 커스텀 마커
                    MarkerComposable(
                        keys    = arrayOf(pin.id, thumb != null),
                        state   = rememberMarkerState(position = LatLng(pin.latitude, pin.longitude)),
                        title   = pin.uploaderName,
                        onClick = {
                            onPinClick(pin.id)
                            true   // true 반환 시 기본 동작(info window) 생략, 피드 이동만 수행
                        },
                    ) {
                        PhotoMarker(bitmap = thumb)
                    }
                }
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

/**
 * 지도 핀용 사진 마커 — 흰색 둥근 틀 안에 사진 썸네일을 보여준다.
 * 썸네일 로딩 전에는 회색 플레이스홀더(사진 아이콘)를 표시한다.
 * @param bitmap 표시할 썸네일 (null이면 플레이스홀더)
 */
@Composable
private fun PhotoMarker(bitmap: Bitmap?) {
    Box(
        modifier         = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(2.dp, Color.White, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = "사진 위치",
                modifier           = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale       = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint     = Color(0xFF9E9E9E),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 갤러리 사진 선택 그리드 시트 (MediaStore 직접 조회)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 기기 갤러리 사진을 3열 그리드로 보여주는 바텀시트.
 * 시스템 포토피커가 위치 EXIF를 제거하는 것과 달리, MediaStore content URI를
 * 직접 넘기므로 선택 후 원본 GPS를 읽을 수 있다.
 * @param images   MediaStore에서 조회한 사진 URI 목록(최신순)
 * @param onPick   사진 선택 콜백
 * @param onDismiss 시트 닫기 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumPickerSheet(
    images: List<Uri>,
    onPick: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Text(
                "사진 선택",
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            if (images.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "표시할 사진이 없어요",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    modifier              = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp),
                    contentPadding        = PaddingValues(bottom = 24.dp),
                ) {
                    items(images, key = { it.toString() }) { uri ->
                        AsyncImage(
                            model              = uri,
                            contentDescription = "갤러리 사진",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(uri) },
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

                // 위치 정보 포함 여부 안내 — 위치가 있으면 지도 표시 안내, 없으면 표시 안 됨 안내
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
                            "위치 정보가 포함돼요 · 지도 탭에 표시됩니다",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOff,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "위치 정보가 없는 사진이에요 · 지도 탭에는 표시되지 않아요",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 사진 추가 시 요청할 권한 — 사진 읽기(전체/부분) + 원본 위치 접근 */
private val MEDIA_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    Manifest.permission.ACCESS_MEDIA_LOCATION,
)

/** 사진 목록 조회 권한(전체 허용 또는 14+ 부분 선택)이 있는지 확인 */
private fun hasMediaReadPermission(context: Context): Boolean {
    val full = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
    val partial = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    return full == PackageManager.PERMISSION_GRANTED || partial == PackageManager.PERMISSION_GRANTED
}

/**
 * MediaStore에서 기기 사진의 content URI 목록을 최신순으로 읽는다.
 * 반환되는 URI는 `content://media/...` 형태라 setRequireOriginal()로 원본 GPS 접근이 가능하다.
 * @param limit 최대 조회 장수(그리드 성능을 위해 상한)
 */
private fun queryDeviceImages(context: Context, limit: Int = 300): List<Uri> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder  = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val result = ArrayList<Uri>(limit)
    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && result.size < limit) {
            result.add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
        }
    }
    return result
}

/** 업로드 이미지 최대 변 길이(px) — 이보다 큰 사진은 비율 유지하며 축소 */
private const val MAX_UPLOAD_DIMENSION = 1080
/** 업로드 JPEG 압축 품질 (0~100) */
private const val UPLOAD_JPEG_QUALITY = 80

/**
 * 이미지 바이트를 메모리 효율적으로 디코딩하면서 최대 변이 [maxDimension] 이하가 되도록 축소한다.
 * 1차로 inSampleSize(2의 거듭제곱)로 근사 축소 후, 필요 시 createScaledBitmap으로 정확히 맞춘다.
 * @return 축소된 Bitmap, 디코딩 실패 시 null
 */
private fun decodeDownscaledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    // 1) 실제 디코딩 없이 원본 크기만 읽기
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
    val (srcW, srcH) = boundsOpts.outWidth to boundsOpts.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    // 2) maxDimension에 근접하도록 2의 거듭제곱 샘플링 계수 계산
    var sample = 1
    while (srcW / (sample * 2) >= maxDimension || srcH / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

    // 3) 샘플링은 2배 단위라 여전히 클 수 있으므로 정확히 maxDimension으로 캡
    val longSide = maxOf(decoded.width, decoded.height)
    if (longSide <= maxDimension) return decoded
    val ratio = maxDimension.toFloat() / longSide
    return Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * ratio).toInt().coerceAtLeast(1),
        (decoded.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

/**
 * EXIF Orientation 값에 따라 비트맵을 실제로 회전/반전시킨다.
 * 재인코딩 시 방향 태그가 사라지므로, 픽셀 자체를 올바른 방향으로 만들어야 한다.
 * @return 보정된 비트맵 (보정 불필요 시 원본 그대로)
 */
private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90     -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180    -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270    -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE     -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE    -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap   // ORIENTATION_NORMAL / UNDEFINED — 보정 불필요
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * 갤러리에서 선택한 URI에서 이미지 데이터, EXIF GPS, 촬영 시각을 추출한다.
 * @return Triple(Base64 문자열, Bitmap, 위도, 경도, 촬영시각 ISO 8601) 또는 null
 */
private fun extractPhotoData(
    context: Context,
    uri: Uri,
): PhotoExtractionResult? {
    return runCatching {
        // 1) 이미지 바이트 읽기
        val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        // 2) EXIF 메타데이터 추출 (방향, GPS, 촬영 시각)
        //    Android 10(API 29)+ 는 위치 EXIF를 redact하므로, 원본 위치를 읽으려면
        //    setRequireOriginal()로 변환한 URI로 스트림을 열어야 한다.
        //    (ACCESS_MEDIA_LOCATION 권한 필요. SAF 등 비-MediaStore URI면 예외 → 원본 URI로 fallback)
        val exifUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else {
            uri
        }
        val exif = runCatching {
            context.contentResolver.openInputStream(exifUri)?.use { ExifInterface(it) }
        }.getOrElse {
            // 권한 미허용 등으로 원본 접근 실패 시, redact된 원본 URI로 재시도 (GPS는 없을 수 있음)
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        }

        // 3) 업로드용 리사이즈 + EXIF 방향 보정 + JPEG 압축
        //    원본 그대로 Base64로 보내면 수~십 MB라 업로드가 느려 1080px/품질80%로 수백 KB로 줄인다.
        //    재인코딩 시 EXIF Orientation 태그가 사라지므로, 압축 전에 방향만큼 비트맵을 실제로
        //    회전시켜야 피드/마커에서 사진이 90도 눕지 않는다. (좌표·촬영시각은 위 EXIF에서 별도 추출)
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        val bitmap = decodeDownscaledBitmap(imageBytes, MAX_UPLOAD_DIMENSION)
            ?.let { applyExifOrientation(it, orientation) }
            ?: return null
        val base64 = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, baos)
            // NO_WRAP: 개행 없이 인코딩해 페이로드를 더 줄임 (디코딩 측은 DEFAULT로도 호환)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }

        // 4) GPS 좌표
        val latLong = FloatArray(2)
        val hasGps = exif?.getLatLong(latLong) == true
        // 갤러리/포토피커가 위치를 redact하면 GPS 태그는 남기되 값을 0,0으로 비우는 기기가 있다.
        // (0,0)은 대서양 한복판이라 실제 사진 좌표일 수 없으므로 "위치 없음"으로 처리한다.
        val hasValidGps = hasGps && !(latLong[0] == 0f && latLong[1] == 0f)

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

        // [진단] EXIF 추출 결과 로그 — 위치가 0,0이거나 null이면 redact 원인 추적용
        android.util.Log.d(
            "AlbumExif",
            "uri=$uri (scheme=${uri.scheme}), exifUri=$exifUri, " +
                "hasGps=$hasGps, lat=${latLong[0]}, lng=${latLong[1]}, " +
                "hasValidGps=$hasValidGps, rawDate=$rawDate, isoDate=$isoDate, " +
                "bytes=${imageBytes.size}"
        )

        PhotoExtractionResult(
            photoData  = base64,
            bitmap     = bitmap,
            latitude   = if (hasValidGps) latLong[0].toDouble() else null,
            longitude  = if (hasValidGps) latLong[1].toDouble() else null,
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
        // 서버 uploadedAt은 타임존 표기 없는 UTC 시각이므로 UTC로 파싱해야
        // 기기 로컬 시간(KST 등)과의 차이가 상대시간에 잘못 반영되지 않는다.
        sdf.timeZone = TimeZone.getTimeZone("UTC")
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
