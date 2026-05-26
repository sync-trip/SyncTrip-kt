package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme

// ═════════════════════════════════════════════════════════════════════════════
// BandHubTab — 하단 네비게이션 탭 정의
// ═════════════════════════════════════════════════════════════════════════════

/** 밴드 허브 화면의 하단 탭 목록 */
enum class BandHubTab(val label: String, val icon: ImageVector) {
    BAND("밴드", Icons.Outlined.Group),
    SCHEDULE("일정", Icons.Outlined.DateRange),
    SETTLEMENT("정산", Icons.Outlined.AccountBalanceWallet),
    PHOTO("사진", Icons.Outlined.PhotoLibrary),
}

// ═════════════════════════════════════════════════════════════════════════════
// TripBandHubScreen — 밴드 방 허브 (하단 탭 네비게이션 포함)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 밴드 방 허브 화면.
 * 하단 NavigationBar로 밴드·일정·정산·사진 탭을 전환한다.
 * 모든 탭 콘텐츠를 인라인으로 포함하므로 별도 라우트 이동 없이 즉시 전환된다.
 *
 * @param selectedTab        현재 선택된 탭 (상위에서 관리 — scheduleReadyEvent 연동)
 * @param onTabSelected      탭 선택 콜백
 * @param schedule           일정 데이터; null이면 로딩 중 또는 미생성
 * @param altOptions         일정 슬롯 교체 후보 목록
 * @param isScheduleLoading  일정 탭 로딩 상태
 * @param isEditing          일정 편집 락 보유 여부
 * @param settlement         정산 데이터; null이면 로딩 중
 * @param snackbarHostState  에러 스낵바 (NavGraph에서 생성 후 전달)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripBandHubScreen(
    band: BandResponse,
    members: List<BandMemberResponse>,
    picks: PlacePickListResponse?,
    currentUserId: Long,
    isBandLoading: Boolean,
    schedule: ScheduleResponse?,
    altOptions: List<ScheduleAltResponse>,
    isScheduleLoading: Boolean,
    isEditing: Boolean,
    settlement: Settlement?,
    expenses: List<com.synctrip.app.data.models.ExpenseResponse>,
    isExpensesLoading: Boolean,
    selectedTab: BandHubTab,
    onTabSelected: (BandHubTab) -> Unit,
    snackbarHostState: SnackbarHostState,
    // 밴드 탭 콜백
    onBackClick: () -> Unit,
    onReadyClick: () -> Unit,
    onInviteClick: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    // 일정 탭 콜백
    onLoadAlts: (scheduleId: Long) -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    planBResults: List<PlanBResponse>,
    isPlanBLoading: Boolean,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,
    // 정산 탭 콜백
    onSettleClick: (transferId: String) -> Unit,
    onAddExpense: (itemName: String, amount: Double, currency: String, payerId: Long, memberIds: List<Long>) -> Unit,
    onDeleteExpense: (expenseId: Long) -> Unit,
    // 사진 탭 — 앨범 상태 + 콜백
    albumPhotos: List<com.synctrip.app.data.models.AlbumPhotoResponse>,
    albumMapPins: List<com.synctrip.app.data.models.AlbumPhotoMapResponse>,
    isAlbumLoading: Boolean,
    isAlbumUploading: Boolean,
    onUploadAlbumPhoto: (
        photoData: String,
        caption: String?,
        latitude: Double?,
        longitude: Double?,
        takenAt: String?,
    ) -> Unit,
    onDeleteAlbumPhoto: (photoId: Long) -> Unit,
    // 방 삭제 (방장 전용)
    onDeleteBand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 투표 강제 시작 전 확인 다이얼로그
    var showAdvanceDialog by remember { mutableStateOf(false) }
    // 방 삭제 확인 다이얼로그
    var showDeleteDialog  by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("방을 삭제하시겠습니까?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("삭제된 여행 방은 7일 후 영구 삭제돼요.")
                    Text(
                        text  = "※ 7일 이내에는 복구를 요청할 수 있습니다",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteBand() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }

    if (showAdvanceDialog) {
        val notReadyCount = members.count { !it.isReady }
        AlertDialog(
            onDismissRequest = { showAdvanceDialog = false },
            title            = { Text("투표 시작") },
            text             = {
                if (notReadyCount > 0)
                    Text("아직 준비 안 된 멤버가 ${notReadyCount}명 있어요. 그래도 투표를 시작할까요?")
                else
                    Text("모든 멤버가 준비됐어요. 투표를 시작할게요!")
            },
            confirmButton = {
                TextButton(onClick = { showAdvanceDialog = false; onAdvanceStatus() }) {
                    Text("시작", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvanceDialog = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar       = {
            HubTopBar(
                title         = band.destination,
                selectedTab   = selectedTab,
                isEditing     = isEditing,
                canEdit       = false,
                isOwner       = band.isOwner,
                onBackClick   = onBackClick,
                onStartEditing  = onStartEditing,
                onFinishEditing = onFinishEditing,
                onDeleteBand    = { showDeleteDialog = true },
            )
        },
        bottomBar = {
            HubNavigationBar(
                selectedTab   = selectedTab,
                onTabSelected = onTabSelected,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                BandHubTab.BAND -> BandHubTabContent(
                    band          = band,
                    members       = members,
                    picks         = picks,
                    currentUserId = currentUserId,
                    isBandLoading = isBandLoading,
                    onReadyClick  = onReadyClick,
                    onInviteClick = onInviteClick,
                    onGoToPlaceSearch  = onGoToPlaceSearch,
                    onGoToVoting       = onGoToVoting,
                    onAdvanceStatusClick = { showAdvanceDialog = true },
                    modifier      = Modifier.fillMaxSize(),
                )

                BandHubTab.SCHEDULE -> {
                    // GENERATING 상태이면서 아직 일정이 없으면 생성 중 스피너 표시
                    if (band.status == BandStatus.GENERATING && (schedule == null || schedule.days.isEmpty())) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PlaneLoadingIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "일정 생성 중…",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    } else {
                        ScheduleContent(
                            schedule            = schedule,
                            altOptions          = altOptions,
                            planBResults        = planBResults,
                            isPlanBLoading      = isPlanBLoading,
                            isLoading           = isScheduleLoading,
                            isEditing           = isEditing,
                            canEdit             = false,
                            isOverseas          = band.isOverseas,
                            onStartEditing      = onStartEditing,
                            onFinishEditing     = onFinishEditing,
                            onSwapSlot          = onSwapSlot,
                            onLoadAlts          = onLoadAlts,
                            onRequestPlanB      = onRequestPlanB,
                            onExecutePlanBSwap  = onExecutePlanBSwap,
                            modifier            = Modifier.fillMaxSize(),
                        )
                    }
                }

                BandHubTab.SETTLEMENT -> SettlementContent(
                    bandId            = band.id,
                    settlement        = settlement,
                    expenses          = expenses,
                    members           = members,
                    currentUserId     = currentUserId,
                    isExpensesLoading = isExpensesLoading,
                    onAddExpense      = onAddExpense,
                    onDeleteExpense   = onDeleteExpense,
                    onSettleClick     = onSettleClick,
                    modifier          = Modifier.fillMaxSize(),
                )

                BandHubTab.PHOTO -> AlbumContent(
                    photos          = albumPhotos,
                    mapPins         = albumMapPins,
                    isLoading       = isAlbumLoading,
                    isUploading     = isAlbumUploading,
                    currentUserId   = currentUserId,
                    destinationLat  = band.destinationLat,
                    destinationLng  = band.destinationLng,
                    onUploadPhoto   = onUploadAlbumPhoto,
                    onDeletePhoto   = onDeleteAlbumPhoto,
                    modifier        = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub TopAppBar — 탭별로 타이틀·액션 다름
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubTopBar(
    title: String,
    selectedTab: BandHubTab,
    isEditing: Boolean,
    canEdit: Boolean,
    isOwner: Boolean,
    onBackClick: () -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onDeleteBand: () -> Unit,
) {
    TopAppBar(
        title = {
            val topTitle = when (selectedTab) {
                BandHubTab.SETTLEMENT -> "정산"
                BandHubTab.PHOTO      -> "사진"
                else                  -> title
            }
            Text(
                text  = topTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            // 일정 탭: 편집 버튼
            if (selectedTab == BandHubTab.SCHEDULE && canEdit) {
                if (isEditing) {
                    TextButton(onClick = onFinishEditing) {
                        Text("편집 완료", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                } else {
                    IconButton(onClick = onStartEditing) {
                        Icon(Icons.Outlined.EditNote, contentDescription = "일정 편집")
                    }
                }
            }
            // 방장만: 방 삭제 버튼 (임시)
            if (isOwner) {
                TextButton(onClick = onDeleteBand) {
                    Text(
                        "방 삭제",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub NavigationBar — 하단 탭 바
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HubNavigationBar(
    selectedTab: BandHubTab,
    onTabSelected: (BandHubTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        BandHubTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick  = { onTabSelected(tab) },
                icon     = { Icon(tab.icon, contentDescription = tab.label) },
                label    = {
                    Text(
                        text  = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BandHubTabContent — 밴드 탭 콘텐츠 (스크롤 가능 정보 + 하단 고정 액션 버튼)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 밴드 탭 본문.
 * 상단 풀와이드 히어로 이미지(BandHeroSection) 아래로 멤버·준비 현황을 배치하고,
 * 액션 버튼은 하단에 고정한다.
 */
@Composable
private fun BandHubTabContent(
    band: BandResponse,
    members: List<BandMemberResponse>,
    picks: PlacePickListResponse?,
    currentUserId: Long,
    isBandLoading: Boolean,
    onReadyClick: () -> Unit,
    onInviteClick: () -> Unit,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onAdvanceStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMember = members.find { it.userId == currentUserId }
    val readyCount    = members.count { it.isReady }

    Column(modifier = modifier) {
        // 스크롤 가능 영역 — 가로 패딩 없음 (히어로가 전체 너비 사용)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 풀와이드 히어로 이미지 ────────────────────────────────────────
            BandHeroSection(band = band, readyCount = readyCount, totalCount = members.size)

            // ── 이하 콘텐츠: 20dp 가로 패딩 ──────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                MembersSection(members = members, onInviteClick = onInviteClick)

                // PLANNING 단계일 때만 내 준비 현황 표시
                if (band.status == BandStatus.PLANNING) {
                    MyStatusSection(
                        picks         = picks,
                        isReady       = currentMember?.isReady ?: false,
                        onReadyClick  = onReadyClick,
                        onSearchClick = onGoToPlaceSearch,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // 하단 고정 액션 버튼 영역 — NavigationBar 바로 위에 위치
        BandActionArea(
            status               = band.status,
            isOwner              = band.isOwner,
            isBandLoading        = isBandLoading,
            onGoToPlaceSearch    = onGoToPlaceSearch,
            onGoToVoting         = onGoToVoting,
            onAdvanceStatusClick = onAdvanceStatusClick,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BandHeroSection — 밴드 탭 상단 풀와이드 히어로 이미지
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 밴드 탭 최상단 히어로 섹션.
 * thumbnailUrl이 있으면 실제 사진, 없으면 그라디언트 폴백으로 표시하고
 * 상태 칩·목적지명·날짜·멤버 준비 현황을 오버레이로 합성한다.
 */
@Composable
private fun BandHeroSection(
    band: BandResponse,
    readyCount: Int,
    totalCount: Int,
) {
    val statusLabel = when (band.status) {
        BandStatus.PLANNING   -> "여행 준비 중"
        BandStatus.VOTING     -> "투표 진행 중"
        BandStatus.GENERATING -> "일정 생성 중"
        BandStatus.TRAVELLING -> "여행 중"
        BandStatus.DONE       -> "여행 완료"
    }
    val statusIcon = when (band.status) {
        BandStatus.PLANNING   -> Icons.Outlined.Group
        BandStatus.VOTING     -> Icons.Outlined.HowToVote
        BandStatus.GENERATING -> Icons.Outlined.Schedule
        BandStatus.TRAVELLING -> Icons.Outlined.FlightTakeoff
        BandStatus.DONE       -> Icons.Outlined.EmojiEvents
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        // 배경: 썸네일 이미지 또는 그라디언트 폴백
        if (band.thumbnailUrl != null) {
            AsyncImage(
                model              = band.thumbnailUrl,
                contentDescription = band.destination,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1565C0), Color(0xFF0288D1), Color(0xFF00838F)),
                        ),
                    ),
            )
        }

        // 하단 그라디언트 오버레이 — 텍스트 가독성 확보
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.05f),
                            1.0f  to Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        // 상태 칩 (좌상단, 반투명 검은 배경 필)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.48f),
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(statusIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    text  = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        // 목적지명 + 날짜 (좌하단)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 100.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = band.destination,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text  = "${band.startDate} ~ ${band.endDate}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.85f),
                ),
            )
        }

        // 준비 현황 뱃지 (우하단)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.48f),
        ) {
            Text(
                text     = "👥 $readyCount / $totalCount 준비",
                style    = MaterialTheme.typography.labelMedium.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BandActionArea — 밴드 탭 하단 고정 버튼 (밴드 상태별 분기)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BandActionArea(
    status: BandStatus,
    isOwner: Boolean,
    isBandLoading: Boolean,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onAdvanceStatusClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier            = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                BandStatus.PLANNING -> {
                    Button(
                        onClick  = onGoToPlaceSearch,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("장소 탐색하기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    // 방장만 투표 강제 시작 가능
                    if (isOwner) {
                        OutlinedButton(
                            onClick  = onAdvanceStatusClick,
                            enabled  = !isBandLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            if (isBandLoading) {
                                PlaneLoadingIndicator(size = 20.dp, showCircle = false)
                            } else {
                                Icon(Icons.Outlined.HowToVote, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isBandLoading) "처리 중…" else "투표 시작하기")
                        }
                    }
                }
                BandStatus.VOTING -> {
                    Button(
                        onClick  = onGoToVoting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.HowToVote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("투표하러 가기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                BandStatus.GENERATING -> {
                    // 일정 생성 중 — 탭 전환 유도
                    OutlinedButton(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        PlaneLoadingIndicator(size = 24.dp, showCircle = false)
                        Spacer(Modifier.width(8.dp))
                        Text("일정 생성 중…")
                    }
                }
                BandStatus.TRAVELLING -> {
                    // 방장만 여행 완료 처리 가능
                    if (isOwner) {
                        OutlinedButton(
                            onClick  = onAdvanceStatusClick,
                            enabled  = !isBandLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            if (isBandLoading) {
                                PlaneLoadingIndicator(size = 20.dp, showCircle = false)
                            } else {
                                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isBandLoading) "처리 중…" else "여행 완료하기")
                        }
                    }
                }
                BandStatus.DONE -> {
                    // 완료 상태 — 별도 액션 없음
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 기존 TripLobbyScreen (하위 호환용 — 필요 시 유지)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 여행 로비 화면.
 * 밴드 상태(PLANNING→VOTING→GENERATING→TRAVELLING→DONE)에 따라
 * 다른 액션 버튼과 정보를 표시한다.
 */
@Composable
fun TripLobbyScreen(
    band: BandResponse,
    members: List<BandMemberResponse>,
    picks: PlacePickListResponse?,          // null = 로드 전
    currentUserId: Long,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onReadyClick: () -> Unit,
    onInviteClick: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onGoToSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 현재 유저의 멤버 정보 — ready 상태 확인에 사용
    val currentMember = members.find { it.userId == currentUserId }
    val readyCount    = members.count { it.isReady }
    val allReady      = readyCount == members.size && members.isNotEmpty()

    // 방장이 투표 시작 시 미ready 멤버가 있으면 경고 다이얼로그 표시
    var showAdvanceDialog by remember { mutableStateOf(false) }

    if (showAdvanceDialog) {
        val notReadyCount = members.count { !it.isReady }
        AlertDialog(
            onDismissRequest = { showAdvanceDialog = false },
            title            = { Text("투표 시작") },
            text             = {
                if (notReadyCount > 0)
                    Text("아직 준비 안 된 멤버가 ${notReadyCount}명 있어요. 그래도 투표를 시작할까요?")
                else
                    Text("모든 멤버가 준비됐어요. 투표를 시작할게요!")
            },
            confirmButton = {
                TextButton(onClick = { showAdvanceDialog = false; onAdvanceStatus() }) {
                    Text("시작", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvanceDialog = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        modifier  = modifier,
        topBar    = { LobbyTopBar(title = band.destination, onBackClick = onBackClick) },
        bottomBar = {
            LobbyBottomBar(
                status             = band.status,
                isOwner            = band.isOwner,
                isLoading          = isLoading,
                onGoToPlaceSearch  = onGoToPlaceSearch,
                onGoToVoting       = onGoToVoting,
                onGoToSchedule     = onGoToSchedule,
                onAdvanceStatusClick = { showAdvanceDialog = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 밴드 기본 정보 카드 ───────────────────────────────────────────
            LobbyInfoCard(band = band, readyCount = readyCount, totalCount = members.size)

            // ── 멤버 목록 ─────────────────────────────────────────────────────
            MembersSection(
                members       = members,
                onInviteClick = onInviteClick,
            )

            // ── 내 준비 현황 (PLANNING 단계만) ────────────────────────────────
            if (band.status == BandStatus.PLANNING) {
                MyStatusSection(
                    picks          = picks,
                    isReady        = currentMember?.isReady ?: false,
                    onReadyClick   = onReadyClick,
                    onSearchClick  = onGoToPlaceSearch,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopAppBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyTopBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text  = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(Icons.Outlined.ArrowBack, "뒤로") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 밴드 정보 카드
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyInfoCard(band: BandResponse, readyCount: Int, totalCount: Int) {
    val statusLabel = when (band.status) {
        BandStatus.PLANNING   -> "준비 중"
        BandStatus.VOTING     -> "투표 중"
        BandStatus.GENERATING -> "일정 생성 중"
        BandStatus.TRAVELLING -> "여행 중"
        BandStatus.DONE       -> "여행 완료"
    }
    val statusColor = when (band.status) {
        BandStatus.PLANNING   -> MaterialTheme.colorScheme.primary
        BandStatus.VOTING     -> MaterialTheme.colorScheme.tertiary
        BandStatus.GENERATING -> MaterialTheme.colorScheme.secondary
        BandStatus.TRAVELLING -> MaterialTheme.colorScheme.error
        BandStatus.DONE       -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = band.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text     = statusLabel,
                        style    = MaterialTheme.typography.labelMedium.copy(color = statusColor, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip(icon = Icons.Outlined.CalendarMonth, text = "${band.startDate} ~ ${band.endDate}")
                InfoChip(
                    icon = Icons.Outlined.DirectionsWalk,
                    text = if (band.travelStyle == BandTravelStyle.RELAXED) "여유롭게" else "알차게",
                )
            }

            // 준비 현황 바
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("준비 완료", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(
                        "$readyCount / $totalCount 명",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                    )
                }
                LinearProgressIndicator(
                    progress   = { if (totalCount > 0) readyCount.toFloat() / totalCount else 0f },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 멤버 섹션
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MembersSection(
    members: List<BandMemberResponse>,
    onInviteClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("함께하는 멤버", style = MaterialTheme.typography.titleLarge)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ) {
                Text(
                    text     = "👥 ${members.size}명",
                    style    = MaterialTheme.typography.labelMedium.copy(
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            members.forEach { member ->
                MemberAvatar(member = member)
            }
            // 초대 버튼
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(horizontal = 2.dp),
            ) {
                FilledIconButton(
                    onClick  = onInviteClick,
                    modifier = Modifier.size(48.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor   = MaterialTheme.colorScheme.primary,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Outlined.PersonAdd, "초대", modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("초대", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: BandMemberResponse) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp)) {
            // 프로필 아이콘
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (member.profileImageUrl != null) {
                    AsyncImage(
                        model              = member.profileImageUrl,
                        contentDescription = member.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text  = member.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            // ready 상태 뱃지
            if (member.isReady) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text  = member.name.let { if (it.length > 5) it.take(4) + "…" else it },
            style = MaterialTheme.typography.labelSmall,
        )
        // 장바구니 담은 수
        Text(
            text  = "🗂 ${member.bookmarkCount}",
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 내 준비 현황 섹션
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyStatusSection(
    picks: PlacePickListResponse?,
    isReady: Boolean,
    onReadyClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.PendingActions,
                    null,
                    tint     = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text  = if (isReady) "준비 완료!" else "내 준비 현황",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // 장바구니 현황
            val count    = picks?.currentCount ?: 0
            val maxCount = picks?.maxCount ?: 5
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "장소 ${count}개 / ${maxCount}개 담음",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                if (count >= maxCount) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            "최대",
                            style    = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (!isReady) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onSearchClick,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("장소 탐색")
                    }
                    // 장바구니 1개 이상이어야 Ready 가능 (인수인계 문서 §4 조건)
                    Button(
                        onClick  = onReadyClick,
                        enabled  = count >= 1,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("준비 완료")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 하단 액션 바 — 밴드 상태에 따라 다른 버튼 표시
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyBottomBar(
    status: BandStatus,
    isOwner: Boolean,
    isLoading: Boolean,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onGoToSchedule: () -> Unit,
    onAdvanceStatusClick: () -> Unit,
) {
    // Surface가 nav bar 뒤까지 배경색을 채우고, Column은 navigationBarsPadding으로 버튼을 nav bar 위에 위치시킴
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier            = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                BandStatus.PLANNING -> {
                    Button(
                        onClick  = onGoToPlaceSearch,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("장소 탐색하기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    // 방장만 투표 강제 시작 가능 — API 호출 중에는 비활성화
                    if (isOwner) {
                        OutlinedButton(
                            onClick  = onAdvanceStatusClick,
                            enabled  = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            if (isLoading) {
                                PlaneLoadingIndicator(size = 20.dp, showCircle = false)
                            } else {
                                Icon(Icons.Outlined.HowToVote, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isLoading) "처리 중…" else "투표 시작하기")
                        }
                    }
                }
                BandStatus.VOTING -> {
                    Button(
                        onClick  = onGoToVoting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.HowToVote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("투표하러 가기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                BandStatus.GENERATING -> {
                    Button(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        PlaneLoadingIndicator(size = 28.dp, showCircle = false)
                        Spacer(Modifier.width(8.dp))
                        Text("일정 생성 중…")
                    }
                }
                BandStatus.TRAVELLING, BandStatus.DONE -> {
                    Button(
                        onClick  = onGoToSchedule,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.DateRange, null)
                        Spacer(Modifier.width(8.dp))
                        Text("일정 보기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripLobbyScreenPreview() {
    val previewBand = BandResponse(
        id               = 1L,
        name             = "도쿄 여행",
        destination      = "도쿄",
        startDate        = "2024-09-03",
        endDate          = "2024-09-08",
        inviteCode       = "SYNC1234",
        status           = BandStatus.PLANNING,
        isOwner          = true,
        isOverseas       = true,
        travelStyle      = BandTravelStyle.RELAXED,
        accommodationName = null,
        memberCount      = 2,
    )
    val previewMembers = listOf(
        BandMemberResponse(1L, "Alex", null, BandRole.OWNER, true,  "2024-09-01", 3),
        BandMemberResponse(2L, "Jamie", null, BandRole.MEMBER, false, "2024-09-01", 1),
    )
    val previewPicks = PlacePickListResponse(currentCount = 3, maxCount = 5, items = emptyList())
    SynctripTheme {
        TripLobbyScreen(
            band              = previewBand,
            members           = previewMembers,
            picks             = previewPicks,
            currentUserId     = 1L,
            isLoading         = false,
            onBackClick       = {},
            onReadyClick      = {},
            onInviteClick     = {},
            onAdvanceStatus   = {},
            onGoToPlaceSearch = {},
            onGoToVoting      = {},
            onGoToSchedule    = {},
        )
    }
}
