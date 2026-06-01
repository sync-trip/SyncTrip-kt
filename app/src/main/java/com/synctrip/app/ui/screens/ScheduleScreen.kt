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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
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
 *   POST /api/bands/{bandId}/schedule/swap       → onSwapSlot
 *   POST /api/bands/{bandId}/schedule/plan-b     → onRequestPlanB
 *   POST /api/bands/{bandId}/schedule/edit/start  → onStartEditing
 *   POST /api/bands/{bandId}/schedule/edit/finish → onFinishEditing
 *
 * 상태 호이스팅: 모든 데이터·비즈니스 상태는 ViewModel(상위)에서 주입.
 * 순수 UI 상태(선택 탭, 바텀시트 열림 여부)만 내부에서 관리.
 *
 * @param destination     툴바에 표시할 여행지 (예: "도쿄, 일본")
 * @param schedule        전체 일정; null이면 로딩 중 또는 미생성
 * @param planBResults    교체 대안 추천 결과 목록 (POST /plan-b)
 * @param isPlanBLoading  교체 대안 추천 API 진행 중
 * @param isLoading       일정 API 요청 진행 중
 * @param isEditing       현재 사용자가 서버 편집 락을 보유 중
 * @param canEdit         편집 가능 여부 (밴드 오너 + 상태 TRAVELLING)
 * @param onStartEditing  POST /edit/start
 * @param onFinishEditing POST /edit/finish
 * @param onSwapSlot      POST /swap — (scheduleId, newPlaceId) 전달
 * @param onRequestPlanB  POST /plan-b 트리거 — (targetPlaceId) 전달
 * @param onBackClick     뒤로 이동
 * @param onShareClick    일정 공유
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    destination: String,
    schedule: ScheduleResponse?,
    planBResults: List<PlanBResponse>,
    isPlanBLoading: Boolean,
    isLoading: Boolean,
    isEditing: Boolean,
    canEdit: Boolean,
    isOverseas: Boolean,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit = {},
    accommodationName: String? = null,
    accommodationLat: Double? = null,
    accommodationLng: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var detailSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showPlanBSheet by remember { mutableStateOf(false) }
    var planBTargetSlot by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showHotelSheet by remember { mutableStateOf(false) }

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
                            selectedSlot      = detailSlot,
                            accommodationName = accommodationName,
                            accommodationLat  = accommodationLat,
                            accommodationLng  = accommodationLng,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SlotTimeline(
                            slots                = currentSlots,
                            isEditing            = isEditing,
                            accommodationName    = accommodationName,
                            onAccommodationClick = { showHotelSheet = true },
                            onSlotClick          = { slot -> detailSlot = slot },
                            onSwapClick          = { slot ->
                                planBTargetSlot = slot
                                showPlanBSheet  = true
                                onRequestPlanB(slot.place.placeId)
                            },
                            modifier             = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showHotelSheet && accommodationName != null && accommodationLat != null && accommodationLng != null) {
        HotelBottomSheet(
            name       = accommodationName,
            lat        = accommodationLat,
            lng        = accommodationLng,
            isOverseas = isOverseas,
            onDismiss  = { showHotelSheet = false },
            context    = context,
        )
    }

    // 장소 상세 바텀시트 — 교체 시트가 닫혀 있을 때만 표시
    val activeDetailSlot = detailSlot
    if (activeDetailSlot != null && !showPlanBSheet) {
        PlaceDetailBottomSheet(
            slot       = activeDetailSlot,
            isEditing  = isEditing,
            isOverseas = isOverseas,
            onDismiss  = { detailSlot = null },
            onSwapClick = {
                planBTargetSlot = activeDetailSlot
                showPlanBSheet  = true
                onRequestPlanB(activeDetailSlot.place.placeId)
            },
        )
    }

    // 교체 대안 추천 바텀시트 — 화살표(swap) 버튼으로 진입
    val planBSlot = planBTargetSlot
    if (showPlanBSheet && planBSlot != null) {
        PlanBBottomSheet(
            slot      = planBSlot,
            results   = planBResults,
            isLoading = isPlanBLoading,
            onDismiss = {
                showPlanBSheet  = false
                planBTargetSlot = null
                detailSlot      = null
            },
            onSelect = { newPlaceId ->
                onSwapSlot(planBSlot.scheduleId, newPlaceId)
                showPlanBSheet  = false
                planBTargetSlot = null
                detailSlot      = null
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
    onSlotClick: (ScheduleSlotResponse) -> Unit,
    onSwapClick: (ScheduleSlotResponse) -> Unit,
    accommodationName: String? = null,
    onAccommodationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) {
        DayEmptyContent(modifier = modifier)
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
                    name     = accommodationName,
                    onClick  = onAccommodationClick,
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
                    modifier = Modifier.padding(start = 34.dp),
                )
            }
            ScheduleSlotItem(
                slot        = slot,
                isEditing   = isEditing,
                onClick     = { onSlotClick(slot) },
                onSwapClick = { onSwapClick(slot) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
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
                    Icons.Outlined.Apartment,
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
    onClick: () -> Unit,
    onSwapClick: () -> Unit,
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
        ScheduleSlotCard(
            slot        = slot,
            isEditing   = isEditing,
            onClick     = onClick,
            onSwapClick = onSwapClick,
            modifier    = Modifier.weight(1f),
        )
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
    isOverseas: Boolean,
    onDismiss: () -> Unit,
    onSwapClick: () -> Unit,
) {
    val context = LocalContext.current
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
            slot.place.address?.let { DetailRow(Icons.Outlined.LocationOn, "주소", it) }
            slot.place.rating?.let { DetailRow(Icons.Outlined.Star, "평점", "%.1f / 5.0".format(it)) }

            Spacer(Modifier.height(20.dp))

            if (isEditing) {
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
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    openDirections(context, slot.place.latitude, slot.place.longitude, slot.place.name, isOverseas)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("길찾기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
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
// Plan B Bottom Sheet — 근처 대안 장소 추천 목록
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Plan B 추천 바텀시트.
 * 현재 슬롯 장소의 위경도 기준으로 1~3km 반경 내 동일 카테고리 대안 장소를 최대 7개 표시한다.
 * 장소 선택 시 onSelect 콜백으로 newPlaceId 전달 → 호출자가 onSwapSlot으로 교체를 실행한다.
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
                        "근처 대안 장소 추천",
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
                        // 가까운 거리순 정렬 후 표시
                        results.sortedBy { it.distanceKmToTarget }.forEach { result ->
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
    selectedSlot: ScheduleSlotResponse? = null,
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

    // 지도 카메라 상태 — 첫 슬롯 위치로 초기화해 진입 시 이동 애니메이션 방지
    val cameraPositionState = rememberCameraPositionState {
        val first = validSlots.firstOrNull()
        if (first != null) {
            position = CameraPosition.fromLatLngZoom(
                LatLng(first.place.latitude, first.place.longitude),
                14f,
            )
        }
    }

    // Day 전환 시 카메라를 첫 번째 슬롯으로 이동 (탭 전환은 animate 유지)
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

    // 슬롯 카드 탭 시 해당 마커로 카메라 이동 + zoom in
    LaunchedEffect(selectedSlot) {
        val slot = selectedSlot ?: return@LaunchedEffect
        if (slot.place.latitude != 0.0 && slot.place.longitude != 0.0) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(
                        LatLng(slot.place.latitude, slot.place.longitude),
                        16f,
                    )
                ),
                durationMs = 500,
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
                // 숙소 → 슬롯 1 → 슬롯 2 → ... 순서로 점선 연결
                val routePoints = buildList {
                    if (accommodationLat != null && accommodationLng != null)
                        add(LatLng(accommodationLat, accommodationLng))
                    validSlots.forEach { add(LatLng(it.place.latitude, it.place.longitude)) }
                }
                if (routePoints.size >= 2) {
                    Polyline(
                        points  = routePoints,
                        color   = androidx.compose.ui.graphics.Color(0x99888888),
                        width   = 5f,
                        pattern = listOf(Dash(20f), Gap(12f)),
                    )
                }

                validSlots.forEachIndexed { index, slot ->
                    val position   = LatLng(slot.place.latitude, slot.place.longitude)
                    val isSelected = selectedSlot?.scheduleId == slot.scheduleId
                    key(slot.scheduleId) {
                        MarkerComposable(
                            keys     = arrayOf(slot.scheduleId, index, isSelected),
                            state    = rememberMarkerState(position = position),
                            onClick  = { _ ->
                                tappedSlot = slot
                                true
                            },
                        ) {
                            NumberedMarker(
                                number     = index + 1,
                                category   = slot.place.category,
                                isSelected = isSelected,
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

    // 호텔 마커 탭 → 숙소 미니 바텀시트
    if (tappedHotel && accommodationName != null && accommodationLat != null && accommodationLng != null) {
        HotelBottomSheet(
            name       = accommodationName,
            lat        = accommodationLat,
            lng        = accommodationLng,
            isOverseas = isOverseas,
            onDismiss  = { tappedHotel = false },
            context    = context,
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
    isSelected: Boolean = false,
) {
    val size        = if (isSelected) 44.dp else 32.dp
    val borderWidth = if (isSelected) 3.dp  else 2.dp
    val dotColor    = category.color()
    Box(
        modifier         = Modifier
            .size(size)
            .clip(CircleShape)
            .background(dotColor)
            .border(borderWidth, Color.White, CircleShape),
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
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFC107))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Outlined.Apartment,
            contentDescription = "숙소",
            tint               = Color.White,
            modifier           = Modifier.size(20.dp),
        )
    }
}

/** 숙소 마커 탭 시 표시되는 미니 바텀시트 — 숙소명 + 길찾기 버튼 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotelBottomSheet(
    name: String,
    lat: Double,
    lng: Double,
    isOverseas: Boolean,
    onDismiss: () -> Unit,
    context: android.content.Context,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Apartment,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "숙소",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = { openDirections(context, lat, lng, name, isOverseas); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
            ) {
                Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("숙소 길찾기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
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

/** Day 슬롯이 0개일 때 표시 */
@Composable
private fun DayEmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "이 날에는 배정된 장소가 없어요",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

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
    ApiPlaceCategory.FOOD     -> Color(0xFFE53935)  // 빨강
    ApiPlaceCategory.CULTURE  -> Color(0xFF1E88E5)  // 파랑
    ApiPlaceCategory.ACTIVITY -> Color(0xFFFB8C00)  // 주황
    ApiPlaceCategory.SHOPPING -> Color(0xFF8E24AA)  // 보라
    ApiPlaceCategory.NATURE   -> Color(0xFF43A047)  // 초록
    ApiPlaceCategory.ETC      -> Color(0xFF546E7A)  // 블루그레이
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
    planBResults: List<PlanBResponse>,
    isPlanBLoading: Boolean,
    isLoading: Boolean,
    isEditing: Boolean,
    isOverseas: Boolean,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    accommodationName: String? = null,
    accommodationLat: Double? = null,
    accommodationLng: Double? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedDayIndex    by remember { mutableIntStateOf(0) }
    var detailSlot          by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showPlanBSheet      by remember { mutableStateOf(false) }
    var planBTargetSlot     by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showHotelSheet      by remember { mutableStateOf(false) }

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
                // 지도(240dp 고정) + 타임라인 분할 화면 — 슬롯 없으면 지도 영역 숨김
                Column(modifier = Modifier.weight(1f)) {
                    if (currentSlots.isNotEmpty()) {
                        ScheduleDayMapView(
                            slots             = currentSlots,
                            isOverseas        = isOverseas,
                            selectedSlot      = detailSlot,
                            accommodationName = accommodationName,
                            accommodationLat  = accommodationLat,
                            accommodationLng  = accommodationLng,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    // 구글맵이 제스처를 소비하므로 타임라인 영역에만 PullToRefreshBox 배치
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh    = onRefresh,
                        modifier     = Modifier.weight(1f),
                    ) {
                        SlotTimeline(
                            slots                = currentSlots,
                            isEditing            = isEditing,
                            accommodationName    = accommodationName,
                            onAccommodationClick = { showHotelSheet = true },
                            onSlotClick          = { slot -> detailSlot = slot },
                            onSwapClick          = { slot ->
                                planBTargetSlot = slot
                                showPlanBSheet  = true
                                onRequestPlanB(slot.place.placeId)
                            },
                            modifier             = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    // 장소 상세 바텀시트 — 교체 시트가 닫혀 있을 때만 표시
    val activeDetailSlot = detailSlot
    if (activeDetailSlot != null && !showPlanBSheet) {
        PlaceDetailBottomSheet(
            slot        = activeDetailSlot,
            isEditing   = isEditing,
            isOverseas  = isOverseas,
            onDismiss   = { detailSlot = null },
            onSwapClick = {
                planBTargetSlot = activeDetailSlot
                showPlanBSheet  = true
                onRequestPlanB(activeDetailSlot.place.placeId)
            },
        )
    }

    // 교체 대안 추천 바텀시트 — 화살표(swap) 버튼으로 진입
    val planBSlot = planBTargetSlot
    if (showPlanBSheet && planBSlot != null) {
        PlanBBottomSheet(
            slot      = planBSlot,
            results   = planBResults,
            isLoading = isPlanBLoading,
            onDismiss = {
                showPlanBSheet  = false
                planBTargetSlot = null
                detailSlot      = null
            },
            onSelect = { newPlaceId ->
                onSwapSlot(planBSlot.scheduleId, newPlaceId)
                showPlanBSheet  = false
                planBTargetSlot = null
                detailSlot      = null
            },
        )
    }

    if (showHotelSheet && accommodationName != null && accommodationLat != null && accommodationLng != null) {
        HotelBottomSheet(
            name       = accommodationName,
            lat        = accommodationLat,
            lng        = accommodationLng,
            isOverseas = isOverseas,
            onDismiss  = { showHotelSheet = false },
            context    = context,
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
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = false, canEdit = true, isOverseas = true,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> },
            onRequestPlanB = {},
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
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = true, canEdit = true, isOverseas = true,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> },
            onRequestPlanB = {},
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
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = true, isEditing = false, canEdit = false, isOverseas = false,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> },
            onRequestPlanB = {},
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
            planBResults = emptyList(), isPlanBLoading = false,
            isLoading = false, isEditing = false, canEdit = false, isOverseas = false,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> },
            onRequestPlanB = {},
            onBackClick = {}, onShareClick = {},
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ScheduleEditScreen — 전용 일정 편집 화면 (크로스 Day 드래그 + swap)
// ═════════════════════════════════════════════════════════════════════════════

/** 플랫 리스트 아이템 — Day 헤더(비드래그), 슬롯(드래그), 빈 Day 드롭존(비드래그) */
private sealed class FlatItem {
    data class DayHeader(val dayNumber: Int, val date: String) : FlatItem()
    data class SlotItem(val slot: ScheduleSlotResponse, val originalDayNumber: Int) : FlatItem()
    // 빈 Day에 드롭 공간을 제공 — buildFlatItems에서 슬롯이 없는 Day에 삽입
    data class EmptyDayPlaceholder(val dayNumber: Int) : FlatItem()
}

private fun buildFlatItems(days: List<ScheduleDayResponse>): List<FlatItem> = buildList {
    for (day in days) {
        add(FlatItem.DayHeader(day.dayNumber, day.date))
        if (day.slots.isEmpty()) add(FlatItem.EmptyDayPlaceholder(day.dayNumber))
        else for (slot in day.slots) add(FlatItem.SlotItem(slot, day.dayNumber))
    }
}

/** Day 헤더 행 — 비드래그, 구분선 역할 */
@Composable
private fun EditDayHeaderRow(
    dayNumber: Int,
    date: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddClick) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "장소 추가",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
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
    onBack: () -> Unit,           // finishEditing은 DisposableEffect.onDispose에서 처리
    onNavigateToAddPlace: (dayNumber: Int) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLockErrorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bandId) {
        // 편집 화면 진입(최초·scheduleAddPlace "+" 복귀 모두)마다 락을 재획득/갱신한다.
        // 항상 호출해도 본인이 보유 중이면 백엔드가 lastEditingAt만 갱신(idempotent)하고,
        // 타인이 보유 중이면 CONFLICT로 즉시 "편집 불가" 안내된다.
        // (이전엔 isEditing 플래그가 true면 건너뛰었으나, 백엔드 락 미보유 상태와 어긋나면
        //  장소 추가 시 FORBIDDEN("편집 시작 버튼을 눌러주세요")으로 실패했음 — 항상 재획득으로 해소)
        viewModel.startEditing(bandId)
        viewModel.loadSchedule(bandId)
    }

    // 편집 락 하트비트 — 화면에 머무는 동안 30초마다 lastEditingAt 갱신.
    // 백엔드 락 타임아웃이 1분이므로, 드래그·swap 등 액션 없이 화면만 보고 있어도 락이 유지된다.
    // (앱 강제 종료·네트워크 단절로 화면을 떠나면 하트비트가 멈춰 최대 1분 안에 백엔드가 자동 해제)
    LaunchedEffect(bandId) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            viewModel.heartbeatEditing(bandId)
        }
    }

    // "+" 버튼으로 scheduleAddPlace 이동 시 락을 유지하기 위해 onDispose의 finishEditing을 억제하는 플래그
    val isNavigatingToChild = remember { mutableStateOf(false) }

    // scheduleAddPlace 복귀 시 일정 새로고침.
    // finishEditing: 자식 화면 이동이 아닌 경우(back arrow, 시스템 제스처)에는 onDispose에서 처리.
    var isFirstResume by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) {
                    isFirstResume = false
                } else {
                    viewModel.loadSchedule(bandId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 자식 화면(scheduleAddPlace) 이동 시에는 락 유지 — 그 외 모든 이탈(back, 시스템 제스처)에서 해제
            if (!isNavigatingToChild.value) {
                viewModel.finishEditing(bandId)
            }
            isNavigatingToChild.value = false
        }
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

    // 저장 버튼 — 드래그 변경사항이 있을 때만 활성화
    var hasPendingChanges by remember { mutableStateOf(false) }
    // 미저장 상태에서 뒤로가기 시 확인 다이얼로그
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 시스템 뒤로가기(제스처·하드웨어 버튼)에서도 미저장 확인
    BackHandler(enabled = hasPendingChanges) { showDiscardDialog = true }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            if (to.index == 0) return@rememberReorderableLazyListState
            // DayHeader·EmptyDayPlaceholder는 이동 출발지 불가
            if (flatItems.getOrNull(from.index) is FlatItem.DayHeader) return@rememberReorderableLazyListState
            if (flatItems.getOrNull(from.index) is FlatItem.EmptyDayPlaceholder) return@rememberReorderableLazyListState
            if (flatItems.getOrNull(to.index) is FlatItem.DayHeader) {
                // DayHeader 위치로 드래그 → 차단 대신 헤더 바로 다음(해당 Day 첫 위치)에 삽입
                // removeAt 후 from < to이면 to 인덱스가 1 감소하므로 조정
                val moved = flatItems.removeAt(from.index)
                val adjustedTo = if (from.index < to.index) to.index - 1 else to.index
                flatItems.add(adjustedTo + 1, moved)
            } else {
                flatItems.add(to.index, flatItems.removeAt(from.index))
            }
            hasPendingChanges = true
        }
    )

    var detailSlot          by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var planBTargetSlot     by remember { mutableStateOf<ScheduleSlotResponse?>(null) }
    var showPlanBSheet      by remember { mutableStateOf(false) }
    var pendingPlanBPlaceId by remember { mutableStateOf<Long?>(null) }

    // schedule 갱신 시 flatItems 동기화 — 드래그 중에는 갱신 무시
    // swap/Plan B 성공 후 서버 상태를 덮어쓰므로 hasPendingChanges도 초기화
    LaunchedEffect(schedule) {
        if (!reorderState.isAnyItemDragging) {
            flatItems.clear()
            flatItems.addAll(buildFlatItems(days))
            hasPendingChanges = false
        }
    }

    // flatItems → 현재 포지션 맵 (scheduleId → (dayNumber, slotOrder))
    fun buildCurrentPositions(): Map<Long, Pair<Int, Int>> {
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        var curDay = -1; var curOrder = 0
        flatItems.forEach { item ->
            when (item) {
                is FlatItem.DayHeader          -> { curDay = item.dayNumber; curOrder = 0 }
                is FlatItem.SlotItem           -> { curOrder++; result[item.slot.scheduleId] = curDay to curOrder }
                is FlatItem.EmptyDayPlaceholder -> {}
            }
        }
        return result
    }

    // 저장 버튼 클릭 — 크로스 Day 이동 → 각 Day 순서 확정
    fun onSave() {
        val orig = buildMap<Long, Pair<Int, Int>> {
            schedule?.days?.forEach { day ->
                day.slots.forEachIndexed { i, s -> put(s.scheduleId, day.dayNumber to (i + 1)) }
            }
        }
        val daySlots = mutableMapOf<Int, MutableList<Long>>()
        var curDay2 = -1
        flatItems.forEach { item ->
            when (item) {
                is FlatItem.DayHeader           -> { curDay2 = item.dayNumber; daySlots.getOrPut(curDay2) { mutableListOf() } }
                is FlatItem.SlotItem            -> daySlots.getOrPut(curDay2) { mutableListOf() }.add(item.slot.scheduleId)
                is FlatItem.EmptyDayPlaceholder -> {}
            }
        }
        val moves = mutableListOf<ScheduleMoveRequest>()
        daySlots.forEach { (dayNum, ids) ->
            ids.forEachIndexed { i, id ->
                val origDay = orig[id]?.first ?: return@forEachIndexed
                if (origDay != dayNum) moves.add(ScheduleMoveRequest(id, dayNum, i + 1))
            }
        }
        viewModel.saveScheduleChanges(
            bandId      = bandId,
            moves       = moves,
            allDayOrders = daySlots.mapValues { it.value.toList() },
            onSuccess   = { hasPendingChanges = false; onBack() },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("일정 편집", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasPendingChanges) showDiscardDialog = true else onBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (hasPendingChanges) {
                        TextButton(
                            onClick  = { onSave() },
                            enabled  = !uiState.isLoading,
                        ) {
                            Text(
                                "저장",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        // 미저장 변경사항 있을 때 뒤로가기 확인
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("저장하지 않고 나갈까요?") },
                text  = { Text("드래그로 변경한 순서가 저장되지 않습니다.") },
                confirmButton = {
                    TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                        Text("나가기", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("취소") }
                },
            )
        }
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
                            is FlatItem.DayHeader           -> "header_${item.dayNumber}"
                            is FlatItem.SlotItem            -> "slot_${item.slot.scheduleId}"
                            is FlatItem.EmptyDayPlaceholder -> "placeholder_${item.dayNumber}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is FlatItem.DayHeader -> {
                            EditDayHeaderRow(
                                dayNumber  = item.dayNumber,
                                date       = item.date,
                                onAddClick = {
                                    // 자식 화면 이동 플래그 — onDispose에서 finishEditing 억제
                                    isNavigatingToChild.value = true
                                    onNavigateToAddPlace(item.dayNumber)
                                },
                            )
                        }
                        is FlatItem.EmptyDayPlaceholder -> {
                            // ReorderableItem으로 감싸야 라이브러리가 드롭 대상으로 인식함
                            // draggableHandle 없음 → 사용자가 직접 끌 수 없고, 슬롯이 이 위치로 이동만 가능
                            ReorderableItem(reorderState, key = "placeholder_${item.dayNumber}") { _ ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .padding(4.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "여기에 장소를 드래그하세요",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
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
                                            onClick   = { detailSlot = slot },
                                            onSwapClick = {
                                                planBTargetSlot = slot
                                                viewModel.loadPlanB(bandId, slot.place.placeId)
                                                showPlanBSheet = true
                                            },
                                            modifier  = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val planBSlot = planBTargetSlot
        if (showPlanBSheet && planBSlot != null) {
            PlanBBottomSheet(
                slot      = planBSlot,
                results   = uiState.planBResults,
                isLoading = uiState.isPlanBLoading,
                onDismiss = { showPlanBSheet = false; planBTargetSlot = null },
                // 장소 선택 즉시 바텀시트를 닫아야 확인 다이얼로그가 단독 표시됨 (Bug 5)
                onSelect  = { id -> pendingPlanBPlaceId = id; showPlanBSheet = false },
            )
        }

        // 장소 상세 바텀시트 — 교체 시트가 없을 때만 표시
        val activeDetailSlot = detailSlot
        if (activeDetailSlot != null && !showPlanBSheet) {
            PlaceDetailBottomSheet(
                slot       = activeDetailSlot,
                isEditing  = false,
                isOverseas = isOverseas,
                onDismiss  = { detailSlot = null },
                onSwapClick = {},
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
