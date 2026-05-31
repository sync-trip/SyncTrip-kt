package com.synctrip.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synctrip.app.ui.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme

// ═════════════════════════════════════════════════════════════════════════════
// Schedule Screen  (여행 경로 일정 화면)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Day-by-day trip itinerary with slot swap/edit support.
 *
 * API:
 *   GET  /api/bands/{bandId}/schedule            → schedule
 *   GET  /api/bands/{bandId}/schedule/alts       → altOptions (after onLoadAlts)
 *   POST /api/bands/{bandId}/schedule/swap       → onSwapSlot
 *   POST /api/bands/{bandId}/schedule/edit/start  → onStartEditing
 *   POST /api/bands/{bandId}/schedule/edit/finish → onFinishEditing
 *
 * 상태 호이스팅: 모든 데이터·비즈니스 상태는 ViewModel(상위)에서 주입.
 * 순수 UI 상태(선택 탭, 바텀시트 열림 여부)만 내부에서 관리.
 *
 * @param destination          툴바에 표시할 여행지 (예: "도쿄, 일본")
 * @param schedule             전체 일정; null이면 로딩 중 또는 미생성
 * @param altOptions           교체 선택 중인 슬롯의 대체 후보 목록
 * @param planBResults         Plan B 추천 결과 목록
 * @param isPlanBLoading       Plan B 추천 API 진행 중
 * @param isLoading            일정 API 요청 진행 중
 * @param isEditing            현재 사용자가 서버 편집 락을 보유 중
 * @param canEdit              편집 가능 여부 (밴드 오너 + 상태 TRAVELLING)
 * @param onStartEditing       POST /edit/start
 * @param onFinishEditing      POST /edit/finish
 * @param onSwapSlot           POST /swap — (scheduleId, newPlaceId) 전달
 * @param onLoadAlts           GET /alts 트리거 — ViewModel이 altOptions 업데이트
 * @param onRequestPlanB       POST /plan-b 트리거 — (targetPlaceId) 전달
 * @param onExecutePlanBSwap   Plan B 교체 실행 — 락 획득·교체·반환 원자적 처리
 * @param onBackClick          뒤로 이동
 * @param onShareClick         일정 공유
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    destination: String,
    schedule: ScheduleResponse?,
    altOptions: List<ScheduleAltResponse>,
    planBResults: List<PlanBResponse>,
    isPlanBLoading: Boolean,
    isLoading: Boolean,
    isEditing: Boolean,
    canEdit: Boolean,
    isOverseas: Boolean,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onLoadAlts: (scheduleId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit = {},
    accommodationName: String? = null,
    accommodationLat: Double? = null,
    accommodationLng: Double? = null,
    modifier: Modifier = Modifier,
) {
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var detailSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showSwapSheet by remember { mutableStateOf(false) }
    var showPlanBSheet by remember { mutableStateOf(false) }
    var planBTargetSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = destination,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // 백엔드 canEdit(DONE/후합류/편집 락)도 함께 반영 — 서버가 막으면 버튼 숨김
                    if (canEdit && schedule?.canEdit != false) {
                        if (isEditing) {
                            TextButton(onClick = onFinishEditing) {
                                Text(
                                    "편집 완료",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        } else {
                            IconButton(onClick = onEditClick) {
                                Icon(Icons.Outlined.EditNote, contentDescription = "일정 편집")
                            }
                        }
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Outlined.Share, contentDescription = "공유")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val days = schedule?.days ?: emptyList()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (days.isNotEmpty()) {
                DayTabRow(
                    days = days,
                    selectedIndex = selectedDayIndex,
                    onDaySelected = { selectedDayIndex = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (isEditing) {
                EditingBanner()
            } else if (schedule?.editingUserId != null) {
                EditingByOtherBanner(schedule?.editingUserName ?: "다른 사용자")
            }

            when {
                isLoading -> ScheduleLoadingContent(modifier = Modifier.fillMaxSize())
                schedule == null || days.isEmpty() -> ScheduleEmptyContent(modifier = Modifier.fillMaxSize())
                else -> {
                    val dayIndex = selectedDayIndex.coerceIn(0, days.lastIndex)
                    val currentSlots = days[dayIndex].slots
                    // 지도(240dp 고정) + 타임라인 분할 화면
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScheduleDayMapView(
                            slots             = currentSlots,
                            isOverseas        = isOverseas,
                            accommodationName = accommodationName,
                            accommodationLat  = accommodationLat,
                            accommodationLng  = accommodationLng,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SlotTimeline(
                            slots             = currentSlots,
                            isEditing         = isEditing,
                            isPlanBLoading    = isPlanBLoading,
                            accommodationName = accommodationName,
                            onSlotClick       = { slot -> detailSlot = slot },
                            onSwapClick       = { slot ->
                                detailSlot = slot
                                onLoadAlts(slot.scheduleId)
                                showSwapSheet = true
                            },
                            onPlanBClick      = { slot ->
                                planBTargetSlot = slot
                                showPlanBSheet  = true
                                onRequestPlanB(slot.place.placeId)
                            },
                            modifier          = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    // 장소 상세 바텀시트 (swap/planB 시트가 닫혀 있을 때만 표시)
    val activeDetailSlot = detailSlot
    if (activeDetailSlot != null && !showSwapSheet && !showPlanBSheet) {
        PlaceDetailBottomSheet(
            slot = activeDetailSlot,
            isEditing = isEditing,
            onDismiss = { detailSlot = null },
            onSwapClick = {
                onLoadAlts(activeDetailSlot.scheduleId)
                showSwapSheet = true
            },
        )
    }

    // 대체 장소 선택 바텀시트 — 같은 카테고리의 후보만 표시
    val swapSlot = detailSlot
    if (showSwapSheet && swapSlot != null) {
        SlotSwapBottomSheet(
            slot = swapSlot,
            options = altOptions.filter { it.category == swapSlot.place.category },
            onDismiss = {
                showSwapSheet = false
                detailSlot = null
            },
            onSelectAlt = { newPlaceId ->
                onSwapSlot(swapSlot.scheduleId, newPlaceId)
                showSwapSheet = false
                detailSlot = null
            },
        )
    }

    // Plan B 추천 바텀시트
    val planBSlot = planBTargetSlot
    if (showPlanBSheet && planBSlot != null) {
        PlanBBottomSheet(
            slot = planBSlot,
            results = planBResults,
            isLoading = isPlanBLoading,
            onDismiss = {
                showPlanBSheet  = false
                planBTargetSlot = null
            },
            onSelect = { newPlaceId ->
                onExecutePlanBSwap(planBSlot.scheduleId, newPlaceId)
                showPlanBSheet  = false
                planBTargetSlot = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day Tab Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayTabRow(
    days: List<ScheduleDayResponse>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(days) { index, day ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onDaySelected(index) },
                label = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = "Day ${day.dayNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        Text(
                            text = day.date.toShortDate(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selected)
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Editing Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditingBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "편집 모드 — 카드의 ⇄ 버튼을 눌러 장소를 교체하세요",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Editing-by-other Banner  (다른 멤버가 편집 락 보유 중일 때)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditingByOtherBanner(editorName: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${editorName}님이 편집 중입니다 — 편집이 끝나면 수정할 수 있어요",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot Warning Badges  (알고리즘 경고 플래그 5종 표시)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotWarningBadges(slot: ScheduleSlotResponse, modifier: Modifier = Modifier) {
    // (라벨, 색) 쌍 — 켜진 플래그만 배지로 노출
    val warnings = buildList {
        if (slot.openingHoursViolation)  add("⚠ 영업시간 위반" to MaterialTheme.colorScheme.error)
        if (slot.lateSchedule)           add("🌙 심야 일정" to MaterialTheme.colorScheme.tertiary)
        if (slot.mealWindowViolation)    add("🍽 식사시간 어긋남" to MaterialTheme.colorScheme.secondary)
        if (slot.isOutlierCandidate)     add("📍 동선 이탈" to MaterialTheme.colorScheme.outline)
    }
    if (warnings.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        warnings.forEach { (label, color) -> WarningBadge(label, color) }
    }
}

@Composable
private fun WarningBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = color),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot Timeline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlotTimeline(
    slots: List<ScheduleSlotResponse>,
    isEditing: Boolean,
    isPlanBLoading: Boolean,
    onSlotClick: (ScheduleSlotResponse) -> Unit,
    onSwapClick: (ScheduleSlotResponse) -> Unit,
    onPlanBClick: (ScheduleSlotResponse) -> Unit,
    accommodationName: String? = null,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) {
        ScheduleEmptyContent(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        // 숙소 출발 행 — 첫 슬롯 이동시간의 기준점을 명확히 표시
        if (accommodationName != null) {
            item(key = "accommodation_header") {
                AccommodationDepartureRow(
                    name = accommodationName,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                slots.firstOrNull()?.travelTimeFromPrev?.let { minutes ->
                    TravelTimeConnector(
                        minutes = minutes,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
            }
        }
        itemsIndexed(slots) { index, slot ->
            if (index > 0) {
                TravelTimeConnector(
                    minutes = slot.travelTimeFromPrev ?: 0,
                    // 타임라인 선을 노드 원 중앙에 맞춤: horizontal(16) + nodeWidth(38)/2 - lineWidth(2)/2 = 34dp
                    modifier = Modifier.padding(start = 34.dp),
                )
            }
            ScheduleSlotItem(
                slot = slot,
                isEditing = isEditing,
                isPlanBLoading = isPlanBLoading,
                onClick = { onSlotClick(slot) },
                onSwapClick = { onSwapClick(slot) },
                onPlanBClick = { onPlanBClick(slot) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Travel Time Connector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccommodationDepartureRow(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Hotel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "09:00 출발",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TravelTimeConnector(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (minutes > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "${minutes}분",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Schedule Slot Item  (타임라인 도트 + 카드)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleSlotItem(
    slot: ScheduleSlotResponse,
    isEditing: Boolean,
    isPlanBLoading: Boolean,
    onClick: () -> Unit,
    onSwapClick: () -> Unit,
    onPlanBClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimelineNode(
            category = slot.place.category,
            time = slot.startTime,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            ScheduleSlotCard(
                slot = slot,
                isEditing = isEditing,
                onClick = onClick,
                onSwapClick = onSwapClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            // 슬롯 카드 아래 Plan B 추천 버튼 — 항상 노출
            OutlinedButton(
                onClick = onPlanBClick,
                enabled = !isPlanBLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (isPlanBLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "Plan B 추천받기",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TimelineNode(
    category: ApiPlaceCategory,
    time: String?,
    modifier: Modifier = Modifier,
) {
    val dotColor = category.color()
    Column(
        modifier = modifier.width(38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.12f))
                .border(1.5.dp, dotColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = time ?: "--:--",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Schedule Slot Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleSlotCard(
    slot: ScheduleSlotResponse,
    isEditing: Boolean,
    onClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 썸네일
            AsyncImage(
                model = slot.place.thumbnailUrl,
                contentDescription = slot.place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            // 텍스트 정보
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CategoryChip(category = slot.place.category)

                Text(
                    text = slot.place.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                slot.place.address?.let { addr ->
                    Text(
                        text = addr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    slot.place.rating?.let { rating ->
                        MetaBadge(
                            icon = Icons.Outlined.Star,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            label = "%.1f".format(rating),
                        )
                    }
                    slot.durationMinutes?.let { mins ->
                        MetaBadge(icon = Icons.Outlined.Schedule, label = "${mins}분")
                    }
                }

                // 알고리즘 경고 배지 (영업시간/심야/식사/동선이탈/미확인)
                SlotWarningBadges(slot = slot)
            }

            // 편집 모드일 때 교체 버튼
            if (isEditing) {
                IconButton(
                    onClick = onSwapClick,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = "장소 교체",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaBadge(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryChip(category: ApiPlaceCategory, modifier: Modifier = Modifier) {
    val tint = category.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(category.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            Text(category.label(), style = MaterialTheme.typography.labelSmall.copy(color = tint))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Place Detail Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceDetailBottomSheet(
    slot: ScheduleSlotResponse,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSwapClick: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            if (slot.place.thumbnailUrl != null) {
                AsyncImage(
                    model = slot.place.thumbnailUrl,
                    contentDescription = slot.place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(16.dp))
            }

            CategoryChip(category = slot.place.category)
            Spacer(Modifier.height(6.dp))
            Text(
                text = slot.place.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            slot.startTime?.let { DetailRow(Icons.Outlined.Schedule, "시작 시간", it) }
            slot.durationMinutes?.let { DetailRow(Icons.Outlined.Timer, "예상 소요", "약 ${it}분") }
            slot.place.address?.let { DetailRow(Icons.Outlined.LocationOn, "주소", it) }
            slot.place.rating?.let { DetailRow(Icons.Outlined.Star, "평점", "%.1f / 5.0".format(it)) }

            if (isEditing) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSwapClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("장소 교체", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot Swap Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSwapBottomSheet(
    slot: ScheduleSlotResponse,
    options: List<ScheduleAltResponse>,
    onDismiss: () -> Unit,
    onSelectAlt: (newPlaceId: Long) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "대체 장소 선택",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${slot.place.name} 대신 방문할 장소를 선택하세요",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            if (options.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.SearchOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("추천 가능한 대체 장소가 없습니다",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { alt ->
                        AltOptionCard(alt = alt, onSelect = { onSelectAlt(alt.place.placeId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AltOptionCard(
    alt: ScheduleAltResponse,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = alt.place.thumbnailUrl,
                contentDescription = alt.place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CategoryChip(category = alt.place.category)
                Text(
                    text = alt.place.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                alt.place.address?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                alt.place.rating?.let { rating ->
                    MetaBadge(icon = Icons.Outlined.Star, iconTint = MaterialTheme.colorScheme.secondary, label = "%.1f".format(rating))
                }
            }
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("선택", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan B Bottom Sheet — 근처 대안 장소 추천 목록
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Plan B 추천 바텀시트.
 * 현재 슬롯 장소의 위경도 기준으로 1~3km 반경 내 동일 카테고리 대안 장소를 최대 7개 표시한다.
 * 장소 선택 시 onSelect 콜백으로 newPlaceId 전달 → ViewModel이 락 획득·교체·반환을 처리한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanBBottomSheet(
    slot: ScheduleSlotResponse,
    results: List<PlanBResponse>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (newPlaceId: Long) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Plan B 추천",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${slot.place.name} 대신 방문할 근처 장소",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    // 추천 API 호출 중
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "주변 대안 장소를 찾는 중…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                results.isEmpty() -> {
                    // 추천 결과 없음
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.SearchOff, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "근처에 추천 가능한 장소가 없습니다",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        results.forEach { result ->
                            PlanBOptionCard(
                                result = result,
                                onSelect = { onSelect(result.placeId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Plan B 추천 결과 카드 한 장 */
@Composable
private fun PlanBOptionCard(
    result: PlanBResponse,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = result.placeInfo.thumbnailUrl,
                contentDescription = result.placeInfo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CategoryChip(category = result.placeInfo.category)
                Text(
                    text = result.placeInfo.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                result.placeInfo.address?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.placeInfo.rating?.let { rating ->
                        MetaBadge(
                            icon = Icons.Outlined.Star,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            label = "%.1f".format(rating),
                        )
                    }
                    // 거리 정보 — 소수점 1자리까지 표시
                    MetaBadge(
                        icon = Icons.Outlined.NearMe,
                        label = "%.1fkm".format(result.distanceKmToTarget),
                    )
                }
            }
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("선택", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Schedule Day Map View — 당일 슬롯을 번호 핀으로 표시하는 지도 (240dp 고정 높이)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 현재 선택된 날짜의 슬롯 장소를 순번 마커로 지도에 표시한다.
 * 마커 탭 시 길찾기 버튼이 포함된 바텀시트를 띄운다.
 * 위경도가 없는 슬롯은 마커를 렌더링하지 않는다.
 *
 * @param slots      현재 날짜의 슬롯 목록
 * @param isOverseas 해외 여행 여부 — 길찾기 앱 분기에 사용
 */
@Composable
private fun ScheduleDayMapView(
    slots: List<ScheduleSlotResponse>,
    isOverseas: Boolean,
    accommodationName: String? = null,
    accommodationLat: Double? = null,
    accommodationLng: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 위경도가 있는 슬롯만 마커로 사용
    val validSlots = remember(slots) {
        slots.filter { it.place.latitude != 0.0 && it.place.longitude != 0.0 }
    }

    // 탭된 슬롯 — 길찾기 바텀시트 표시용
    var tappedSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    // 호텔 마커 탭 — 숙소명 팝업 표시용
    var tappedHotel by remember { mutableStateOf(false) }

    // 지도 카메라 상태
    val cameraPositionState = rememberCameraPositionState()

    // 슬롯 목록이 바뀔 때마다 카메라를 첫 번째 유효 슬롯 위치로 이동
    LaunchedEffect(validSlots) {
        if (validSlots.isNotEmpty()) {
            val first = validSlots.first()
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(
                        LatLng(first.place.latitude, first.place.longitude),
                        14f,
                    )
                )
            )
        }
    }

    Box(modifier = modifier) {
        if (validSlots.isEmpty()) {
            // 위경도 데이터 없으면 안내 문구
            Box(
                modifier          = Modifier.fillMaxSize(),
                contentAlignment  = Alignment.Center,
            ) {
                Text(
                    "지도 정보를 불러올 수 없습니다",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        } else {
            GoogleMap(
                modifier             = Modifier.fillMaxSize(),
                cameraPositionState  = cameraPositionState,
                uiSettings           = MapUiSettings(
                    zoomControlsEnabled  = false,
                    myLocationButtonEnabled = false,
                ),
            ) {
                validSlots.forEachIndexed { index, slot ->
                    val position = LatLng(slot.place.latitude, slot.place.longitude)
                    // key()로 슬롯 ID가 바뀌면 rememberMarkerState를 강제 재생성
                    // — key() 없으면 Day 전환 시 이전 Day의 좌표가 유지되는 버그 발생
                    key(slot.scheduleId) {
                        MarkerComposable(
                            keys     = arrayOf(slot.scheduleId, index),
                            state    = rememberMarkerState(position = position),
                            onClick  = { _ ->
                                tappedSlot = slot
                                true
                            },
                        ) {
                            NumberedMarker(
                                number   = index + 1,
                                category = slot.place.category,
                            )
                        }
                    }
                }
                // 숙소 핀 — amber 원 "숙" 마커. 탭 시 숙소명 팝업
                if (accommodationLat != null && accommodationLng != null) {
                    MarkerComposable(
                        keys  = arrayOf("hotel"),
                        state = rememberMarkerState(position = LatLng(accommodationLat, accommodationLng)),
                        onClick = { _ -> tappedHotel = true; true },
                    ) {
                        HotelMarker()
                    }
                }
            }
        }
    }

    // 호텔 마커 탭 → 숙소명 다이얼로그
    if (tappedHotel && accommodationName != null) {
        AlertDialog(
            onDismissRequest = { tappedHotel = false },
            title = { Text("숙소") },
            text  = { Text(accommodationName) },
            confirmButton = {
                TextButton(onClick = { tappedHotel = false }) { Text("확인") }
            },
        )
    }

    // 마커 탭 → 장소 미니 바텀시트
    tappedSlot?.let { slot ->
        MapPlaceBottomSheet(
            slot       = slot,
            isOverseas = isOverseas,
            onDismiss  = { tappedSlot = null },
            onNavigate = { lat, lng, name ->
                openDirections(context, lat, lng, name, isOverseas)
                tappedSlot = null
            },
        )
    }
}

/**
 * 타임라인 순번을 표시하는 지도 마커.
 * 카테고리 색 원 + 흰 테두리 + 흰 숫자로 구성된다.
 */
@Composable
private fun NumberedMarker(
    number: Int,
    category: ApiPlaceCategory,
) {
    val dotColor = category.color()
    Box(
        modifier         = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(dotColor)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text      = number.toString(),
            style     = MaterialTheme.typography.labelMedium.copy(
                color      = Color.White,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * 숙소 위치를 나타내는 지도 마커.
 * amber 배경 원 + 흰 "숙" 텍스트로 슬롯 마커와 구분된다.
 */
@Composable
private fun HotelMarker() {
    Box(
        modifier         = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFC107))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "숙",
            style = MaterialTheme.typography.labelMedium.copy(
                color      = Color.White,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * 지도 마커 탭 시 표시되는 미니 바텀시트.
 * 장소 이름·카테고리·주소·평점과 길찾기 버튼을 제공한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapPlaceBottomSheet(
    slot: ScheduleSlotResponse,
    isOverseas: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (lat: Double, lng: Double, name: String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            // 썸네일 (있을 때만)
            slot.place.thumbnailUrl?.let { url ->
                AsyncImage(
                    model             = url,
                    contentDescription = slot.place.name,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(12.dp))
            }

            CategoryChip(category = slot.place.category)
            Spacer(Modifier.height(6.dp))
            Text(
                text  = slot.place.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                ),
            )

            slot.place.address?.let { addr ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = addr,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 2,
                    )
                }
            }

            slot.place.rating?.let { rating ->
                Spacer(Modifier.height(4.dp))
                MetaBadge(
                    icon     = Icons.Outlined.Star,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    label    = "%.1f".format(rating),
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = {
                    onNavigate(slot.place.latitude, slot.place.longitude, slot.place.name)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "길찾기",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/**
 * 장소 길찾기를 외부 앱으로 열어준다.
 * - 국내: geo: URI → 시스템 앱 선택기 (카카오맵·네이버지도 등 포함)
 * - 해외: Google Maps 강제 실행, 미설치 시 브라우저 폴백
 */
private fun openDirections(
    context: Context,
    lat: Double,
    lng: Double,
    name: String,
    isOverseas: Boolean,
) {
    if (isOverseas) {
        // 해외 → Google Maps URI
        val uri    = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Google Maps 미설치 시 브라우저로 폴백
            val webUri     = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            val webIntent  = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(webIntent)
        }
    } else {
        // 국내 → geo: URI (시스템 앱 선택기 — 카카오·네이버·구글 모두 처리)
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        context.startActivity(Intent.createChooser(intent, "길찾기 앱 선택"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading / Empty States
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlaneLoadingIndicator()
            Spacer(Modifier.height(16.dp))
            Text("일정을 불러오는 중…",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun ScheduleEmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(Icons.Outlined.EventNote, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("아직 일정이 없습니다",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            Text(
                "투표가 완료되면 자동으로\n최적의 여행 일정을 생성합니다",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ApiPlaceCategory helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ApiPlaceCategory.color(): Color = when (this) {
    ApiPlaceCategory.FOOD     -> MaterialTheme.colorScheme.error
    ApiPlaceCategory.CULTURE  -> MaterialTheme.colorScheme.tertiary
    ApiPlaceCategory.ACTIVITY -> MaterialTheme.colorScheme.primary
    ApiPlaceCategory.SHOPPING -> Color(0xFF7B1FA2)
    ApiPlaceCategory.NATURE   -> Color(0xFF2E7D32)
    ApiPlaceCategory.ETC      -> MaterialTheme.colorScheme.outline
}

private fun ApiPlaceCategory.icon(): ImageVector = when (this) {
    ApiPlaceCategory.FOOD     -> Icons.Outlined.Restaurant
    ApiPlaceCategory.CULTURE  -> Icons.Outlined.Museum
    ApiPlaceCategory.ACTIVITY -> Icons.Outlined.Attractions
    ApiPlaceCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    ApiPlaceCategory.NATURE   -> Icons.Outlined.Park
    ApiPlaceCategory.ETC      -> Icons.Outlined.Place
}

private fun ApiPlaceCategory.label(): String = when (this) {
    ApiPlaceCategory.FOOD     -> "음식"
    ApiPlaceCategory.CULTURE  -> "문화"
    ApiPlaceCategory.ACTIVITY -> "액티비티"
    ApiPlaceCategory.SHOPPING -> "쇼핑"
    ApiPlaceCategory.NATURE   -> "자연"
    ApiPlaceCategory.ETC      -> "기타"
}

// "2024-08-15" → "8/15"
private fun String.toShortDate(): String {
    val parts = split("-")
    return if (parts.size >= 3) "${parts[1].trimStart('0')}/${parts[2].trimStart('0')}" else this
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleContent — Scaffold·TopAppBar 없는 순수 콘텐츠 (TripBandHubScreen 임베드용)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 일정 탭 콘텐츠.
 * ScheduleScreen과 동일한 UI지만 Scaffold/TopAppBar 없이 콘텐츠만 포함한다.
 * TripBandHubScreen의 일정 탭에서 호출한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleContent(
    schedule: ScheduleResponse?,
    altOptions: List<ScheduleAltResponse>,
    planBResults: List<PlanBResponse>,
    isPlanBLoading: Boolean,
    isLoading: Boolean,
    isEditing: Boolean,
    canEdit: Boolean,
    isOverseas: Boolean,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onLoadAlts: (scheduleId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,
    accommodationName: String? = null,
    accommodationLat: Double? = null,
    accommodationLng: Double? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var detailSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showSwapSheet by remember { mutableStateOf(false) }
    var showPlanBSheet by remember { mutableStateOf(false) }
    var planBTargetSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }

    val days = schedule?.days ?: emptyList()

    Column(modifier = modifier.fillMaxSize()) {
        // 날짜 탭
        if (days.isNotEmpty()) {
            DayTabRow(
                days          = days,
                selectedIndex = selectedDayIndex,
                onDaySelected = { selectedDayIndex = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // 편집 중 배너 — 본인 편집 중이면 편집 배너, 타인이 락 보유 중이면 안내 배너
        if (isEditing) {
            EditingBanner()
        } else if (schedule?.editingUserId != null) {
            EditingByOtherBanner(schedule?.editingUserName ?: "다른 사용자")
        }

        // 주요 콘텐츠 (로딩·빈상태·지도+타임라인 분할)
        when {
            isLoading                          -> ScheduleLoadingContent(modifier = Modifier.weight(1f))
            schedule == null || days.isEmpty() -> ScheduleEmptyContent(modifier = Modifier.weight(1f))
            else -> {
                val dayIndex     = selectedDayIndex.coerceIn(0, days.lastIndex)
                val currentSlots = days[dayIndex].slots
                // 지도(240dp 고정) + 타임라인 분할 화면
                Column(modifier = Modifier.weight(1f)) {
                    ScheduleDayMapView(
                        slots             = currentSlots,
                        isOverseas        = isOverseas,
                        accommodationName = accommodationName,
                        accommodationLat  = accommodationLat,
                        accommodationLng  = accommodationLng,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // 구글맵이 제스처를 소비하므로 타임라인 영역에만 PullToRefreshBox 배치
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh    = onRefresh,
                        modifier     = Modifier.weight(1f),
                    ) {
                        SlotTimeline(
                            slots             = currentSlots,
                            isEditing         = isEditing,
                            isPlanBLoading    = isPlanBLoading,
                            accommodationName = accommodationName,
                            onSlotClick       = { slot -> detailSlot = slot },
                            onSwapClick       = { slot ->
                                detailSlot = slot
                                onLoadAlts(slot.scheduleId)
                                showSwapSheet = true
                            },
                            onPlanBClick      = { slot ->
                                planBTargetSlot = slot
                                showPlanBSheet  = true
                                onRequestPlanB(slot.place.placeId)
                            },
                            modifier          = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    // 장소 상세 바텀시트
    val activeDetailSlot = detailSlot
    if (activeDetailSlot != null && !showSwapSheet && !showPlanBSheet) {
        PlaceDetailBottomSheet(
            slot        = activeDetailSlot,
            isEditing   = isEditing,
            onDismiss   = { detailSlot = null },
            onSwapClick = {
                onLoadAlts(activeDetailSlot.scheduleId)
                showSwapSheet = true
            },
        )
    }

    // 대체 장소 선택 바텀시트 — 같은 카테고리의 후보만 표시
    val swapSlot = detailSlot
    if (showSwapSheet && swapSlot != null) {
        SlotSwapBottomSheet(
            slot      = swapSlot,
            options   = altOptions.filter { it.category == swapSlot.place.category },
            onDismiss = {
                showSwapSheet = false
                detailSlot    = null
            },
            onSelectAlt = { newPlaceId ->
                onSwapSlot(swapSlot.scheduleId, newPlaceId)
                showSwapSheet = false
                detailSlot    = null
            },
        )
    }

    // Plan B 추천 바텀시트
    val planBSlot = planBTargetSlot
    if (showPlanBSheet && planBSlot != null) {
        PlanBBottomSheet(
            slot = planBSlot,
            results = planBResults,
            isLoading = isPlanBLoading,
            onDismiss = {
                showPlanBSheet = false
                planBTargetSlot = null
            },
            onSelect = { newPlaceId ->
                onExecutePlanBSwap(planBSlot.scheduleId, newPlaceId)
                showPlanBSheet = false
                planBTargetSlot = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview data
// ─────────────────────────────────────────────────────────────────────────────

private val previewPlace = SchedulePlaceInfo(
    placeId = 1L, apiSource = PlaceApiSource.KAKAO,
    name = "아사쿠사 센소지", category = ApiPlaceCategory.CULTURE,
    latitude = 35.7147, longitude = 139.7966,
    address = "도쿄 다이토구 아사쿠사 2-3-1", rating = 4.5f, thumbnailUrl = null,
)

private val previewSlots = listOf(
    ScheduleSlotResponse(1L, 1, "09:00", 90, null,
        previewPlace.copy(name = "아사쿠사 센소지", category = ApiPlaceCategory.CULTURE)),
    ScheduleSlotResponse(2L, 2, "11:30", 60, 25,
        previewPlace.copy(name = "도쿄 스카이트리", category = ApiPlaceCategory.ACTIVITY, address = "도쿄 스미다구 오시아게 1-1-2", rating = 4.6f)),
    ScheduleSlotResponse(3L, 3, "13:30", 60, 20,
        previewPlace.copy(name = "우에노 공원", category = ApiPlaceCategory.NATURE, address = "도쿄 다이토구 우에노 공원")),
    ScheduleSlotResponse(4L, 4, "15:00", 120, 15,
        previewPlace.copy(name = "아키하바라", category = ApiPlaceCategory.SHOPPING, address = "도쿄 치요다구 외신다이마치 1")),
    ScheduleSlotResponse(5L, 5, "18:30", 90, 35,
        previewPlace.copy(name = "이치란 신주쿠점", category = ApiPlaceCategory.FOOD, address = "도쿄 신주쿠구 가부키초 1-22-7", rating = 4.3f)),
)

private val previewSchedule = ScheduleResponse(
    bandId = 1L, startDate = "2024-08-15", endDate = "2024-08-18",
    days = listOf(
        ScheduleDayResponse(1, "2024-08-15", previewSlots),
        ScheduleDayResponse(2, "2024-08-16", previewSlots.take(3)),
        ScheduleDayResponse(3, "2024-08-17", previewSlots.takeLast(2)),
        ScheduleDayResponse(4, "2024-08-18", previewSlots.take(1)),
    ),
)

private val previewAlts = listOf(
    ScheduleAltResponse(10L, ApiPlaceCategory.CULTURE, 0.85f,
        previewPlace.copy(name = "도쿄 국립 박물관", address = "도쿄 다이토구 우에노 공원 13-9")),
    ScheduleAltResponse(11L, ApiPlaceCategory.ACTIVITY, 0.78f,
        previewPlace.copy(name = "시부야 스카이", category = ApiPlaceCategory.ACTIVITY, address = "도쿄 시부야구 도겐자카 2-24-12", rating = 4.6f)),
    ScheduleAltResponse(12L, ApiPlaceCategory.FOOD, 0.71f,
        previewPlace.copy(name = "쓰키지 시장", category = ApiPlaceCategory.FOOD, address = "도쿄 주오구 쓰키지 5-2-1")),
)

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScheduleScreenPreview() {
    SynctripTheme {
        ScheduleScreen(
            destination = "도쿄, 일본",
            schedule = previewSchedule,
            altOptions = emptyList(),
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = false, canEdit = true, isOverseas = true,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> }, onLoadAlts = {},
            onRequestPlanB = {}, onExecutePlanBSwap = { _, _ -> },
            onBackClick = {}, onShareClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "편집 모드")
@Composable
private fun ScheduleScreenEditingPreview() {
    SynctripTheme {
        ScheduleScreen(
            destination = "도쿄, 일본",
            schedule = previewSchedule,
            altOptions = emptyList(),
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = true, canEdit = true, isOverseas = true,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> }, onLoadAlts = {},
            onRequestPlanB = {}, onExecutePlanBSwap = { _, _ -> },
            onBackClick = {}, onShareClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "로딩 중")
@Composable
private fun ScheduleScreenLoadingPreview() {
    SynctripTheme {
        ScheduleScreen(
            destination = "도쿄, 일본",
            schedule = null,
            altOptions = emptyList(),
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = true, isEditing = false, canEdit = false, isOverseas = false,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> }, onLoadAlts = {},
            onRequestPlanB = {}, onExecutePlanBSwap = { _, _ -> },
            onBackClick = {}, onShareClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "일정 없음")
@Composable
private fun ScheduleScreenEmptyPreview() {
    SynctripTheme {
        ScheduleScreen(
            destination = "도쿄, 일본",
            schedule = ScheduleResponse(1L, "2024-08-15", "2024-08-18", emptyList()),
            altOptions = emptyList(),
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = false, canEdit = false, isOverseas = false,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> }, onLoadAlts = {},
            onRequestPlanB = {}, onExecutePlanBSwap = { _, _ -> },
            onBackClick = {}, onShareClick = {},
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ScheduleEditScreen — 전용 일정 편집 화면 (크로스 Day 드래그 + swap)
// ═════════════════════════════════════════════════════════════════════════════

/** 플랫 리스트 아이템 — Day 헤더(비드래그)와 슬롯(드래그 가능) */
private sealed class FlatItem {
    data class DayHeader(val dayNumber: Int, val date: String) : FlatItem()
    data class SlotItem(val slot: ScheduleSlotResponse, val originalDayNumber: Int) : FlatItem()
}

private fun buildFlatItems(days: List<ScheduleDayResponse>): List<FlatItem> = buildList {
    for (day in days) {
        add(FlatItem.DayHeader(day.dayNumber, day.date))
        for (slot in day.slots) add(FlatItem.SlotItem(slot, day.dayNumber))
    }
}

/** Day 헤더 행 — 비드래그, 구분선 역할 */
@Composable
private fun EditDayHeaderRow(dayNumber: Int, date: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Day $dayNumber",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                date.toShortDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 일정 편집 전용 화면.
 * - 진입 시 편집 락 획득, 이탈 시 자동 해제.
 * - 전체 Day를 하나의 플랫 리스트로 표시 — Day 헤더를 넘어 크로스 Day 드래그 가능.
 * - 같은 Day 내 이동: PATCH /schedule/reorder, 다른 Day 이동: POST /schedule/move.
 * - 슬롯 교체(swap) 및 Plan B 선택 시 확인 다이얼로그 표시 후 실행.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    bandId: Long,
    @Suppress("UNUSED_PARAMETER") isOverseas: Boolean,
    viewModel: ScheduleViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLockErrorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bandId) {
        viewModel.startEditing(bandId)
        viewModel.loadSchedule(bandId)
        viewModel.loadAlts(bandId)
    }

    DisposableEffect(bandId) {
        onDispose { viewModel.finishEditing(bandId) }
    }

    LaunchedEffect(uiState.error, uiState.isEditing) {
        if (uiState.error != null && !uiState.isEditing) showLockErrorDialog = true
    }

    // 편집 중 발생한 에러(드래그 실패, 네트워크 등)는 스낵바로 표시 — 세션 만료 다이얼로그 금지
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            if (uiState.isEditing) snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (showLockErrorDialog) {
        AlertDialog(
            onDismissRequest = { showLockErrorDialog = false; onBack() },
            title = { Text("편집 불가") },
            text  = {
                val editorName = uiState.schedule?.editingUserName
                Text(if (editorName != null) "${editorName}님이 편집 중입니다." else "다른 멤버가 편집 중입니다.")
            },
            confirmButton = {
                TextButton(onClick = { showLockErrorDialog = false; onBack() }) { Text("확인") }
            },
        )
    }

    // ── 모든 remember는 조건부 return 앞에 선언 (Compose 규칙) ─────────────
    val schedule = uiState.schedule
    val days     = schedule?.days ?: emptyList()

    // 전체 Day를 포함한 플랫 리스트 — schedule 갱신 시 LaunchedEffect로 동기화
    val flatItems = remember { mutableStateListOf<FlatItem>() }

    // 드래그 추적 — onMove에서 최초 1회 원본 Day 캡처
    var draggedScheduleId   by remember { mutableLongStateOf(-1L) }
    var dragSourceDayNumber by remember { mutableIntStateOf(-1) }
    var hasDragged          by remember { mutableStateOf(false) }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            // 드래그 시작 시 최초 1회 원본 Day 캡처
            if (draggedScheduleId == -1L) {
                (flatItems.getOrNull(from.index) as? FlatItem.SlotItem)?.let {
                    draggedScheduleId   = it.slot.scheduleId
                    dragSourceDayNumber = it.originalDayNumber
                }
            }
            // 첫 번째 Day 헤더(인덱스 0) 앞으로는 이동 불가
            if (to.index == 0) return@rememberReorderableLazyListState
            // to.index를 그대로 사용: removeAt 후 인덱스 시프트로 인해 헤더 직전/직후 정확한 위치에 삽입됨
            // (while 루프로 건너뛰면 removeAt 후 targetIdx가 실제 위치보다 1 밀려 오삽입됨)
            flatItems.add(to.index, flatItems.removeAt(from.index))
            hasDragged = true
        }
    )

    var swapTargetSlot      by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showSwapSheet       by remember { mutableStateOf(false) }
    var planBTargetSlot     by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showPlanBSheet      by remember { mutableStateOf(false) }
    var pendingSwapPlaceId  by remember { mutableStateOf<Long?>(null) }
    var pendingPlanBPlaceId by remember { mutableStateOf<Long?>(null) }

    // schedule 갱신 시 flatItems 동기화 — 드래그 중에는 갱신 무시
    LaunchedEffect(schedule) {
        if (!reorderState.isAnyItemDragging) {
            flatItems.clear()
            flatItems.addAll(buildFlatItems(days))
        }
    }

    // 드래그 완료 시 같은 Day/다른 Day 분기 처리
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging && hasDragged && draggedScheduleId != -1L) {
            hasDragged = false

            val movedIndex = flatItems.indexOfFirst {
                it is FlatItem.SlotItem && it.slot.scheduleId == draggedScheduleId
            }

            if (movedIndex == -1) {
                viewModel.loadSchedule(bandId)
            } else {
                // movedIndex 앞의 가장 가까운 DayHeader → 목적지 Day
                val targetDayHeaderIdx = (0 until movedIndex).lastOrNull {
                    flatItems[it] is FlatItem.DayHeader
                } ?: -1
                val targetDayNumber = if (targetDayHeaderIdx >= 0)
                    (flatItems[targetDayHeaderIdx] as FlatItem.DayHeader).dayNumber
                else dragSourceDayNumber

                // DayHeader 다음부터 movedIndex까지의 SlotItem 개수 = 1-based slotOrder
                val targetSlotOrder = flatItems
                    .subList(targetDayHeaderIdx + 1, movedIndex + 1)
                    .count { it is FlatItem.SlotItem }

                if (targetDayNumber == dragSourceDayNumber) {
                    // 같은 Day: originalDayNumber가 targetDay인 슬롯만 포함 (스테일 데이터 방어)
                    // 이전 drag의 loadSchedule이 미완료 상태에서 재드래그 시 다른 Day 슬롯이
                    // 섞일 수 있으므로 originalDayNumber로 필터링
                    val orderedIds = buildList {
                        for (i in (targetDayHeaderIdx + 1) until flatItems.size) {
                            val item = flatItems[i]
                            if (item is FlatItem.DayHeader) break
                            if (item is FlatItem.SlotItem && item.originalDayNumber == targetDayNumber) {
                                add(item.slot.scheduleId)
                            }
                        }
                    }
                    if (orderedIds.isEmpty()) {
                        // 유효한 슬롯이 없으면 서버 상태로 롤백
                        viewModel.loadSchedule(bandId)
                    } else {
                        viewModel.reorderSlots(bandId, targetDayNumber, orderedIds)
                    }
                } else {
                    // 다른 Day: moveSlot
                    viewModel.moveSlot(bandId, draggedScheduleId, targetDayNumber, targetSlotOrder)
                }
            }

            draggedScheduleId   = -1L
            dragSourceDayNumber = -1
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("일정 편집", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        if (!uiState.isEditing) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (days.isEmpty()) {
            ScheduleEmptyContent(modifier = Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            EditingBanner()

            LazyColumn(
                state          = lazyListState,
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = flatItems,
                    key   = { item ->
                        when (item) {
                            is FlatItem.DayHeader -> "header_${item.dayNumber}"
                            is FlatItem.SlotItem  -> "slot_${item.slot.scheduleId}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is FlatItem.DayHeader -> {
                            EditDayHeaderRow(item.dayNumber, item.date)
                        }
                        is FlatItem.SlotItem -> {
                            val slot = item.slot
                            ReorderableItem(reorderState, key = "slot_${slot.scheduleId}") { isDragging ->
                                val elevation by androidx.compose.animation.core.animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "drag_elevation",
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(elevation = elevation, shape = RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.DragHandle,
                                            contentDescription = "순서 변경",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .draggableHandle(),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        ScheduleSlotCard(
                                            slot      = slot,
                                            isEditing = true,
                                            onClick   = {},
                                            onSwapClick = {
                                                swapTargetSlot = slot
                                                viewModel.loadAlts(bandId)
                                                showSwapSheet = true
                                            },
                                            modifier  = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = {
                                            planBTargetSlot = slot
                                            viewModel.loadPlanB(bandId, slot.place.placeId)
                                            showPlanBSheet = true
                                        }) {
                                            Icon(
                                                Icons.Outlined.AutoAwesome,
                                                contentDescription = "Plan B",
                                                tint = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val swapSlot = swapTargetSlot
        if (showSwapSheet && swapSlot != null) {
            SlotSwapBottomSheet(
                slot        = swapSlot,
                options     = uiState.altOptions.filter { it.category == swapSlot.place.category },
                onDismiss   = { showSwapSheet = false; swapTargetSlot = null },
                onSelectAlt = { id -> pendingSwapPlaceId = id },
            )
        }

        val planBSlot = planBTargetSlot
        if (showPlanBSheet && planBSlot != null) {
            PlanBBottomSheet(
                slot      = planBSlot,
                results   = uiState.planBResults,
                isLoading = uiState.isPlanBLoading,
                onDismiss = { showPlanBSheet = false; planBTargetSlot = null },
                onSelect  = { id -> pendingPlanBPlaceId = id },
            )
        }

        if (pendingSwapPlaceId != null) {
            AlertDialog(
                onDismissRequest = { pendingSwapPlaceId = null },
                title = { Text("장소 교체") },
                text  = { Text("이 장소로 교체할까요?\n동선이 자동으로 재계산됩니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.swapSlot(bandId, swapTargetSlot!!.scheduleId, pendingSwapPlaceId!!)
                        pendingSwapPlaceId = null; showSwapSheet = false; swapTargetSlot = null
                    }) { Text("교체") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSwapPlaceId = null }) { Text("취소") }
                },
            )
        }

        if (pendingPlanBPlaceId != null) {
            AlertDialog(
                onDismissRequest = { pendingPlanBPlaceId = null },
                title = { Text("장소 교체") },
                text  = { Text("이 장소로 교체할까요?\n동선이 자동으로 재계산됩니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.swapSlot(bandId, planBTargetSlot!!.scheduleId, pendingPlanBPlaceId!!)
                        pendingPlanBPlaceId = null; showPlanBSheet = false; planBTargetSlot = null
                    }) { Text("교체") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPlanBPlaceId = null }) { Text("취소") }
                },
            )
        }
    }
}
