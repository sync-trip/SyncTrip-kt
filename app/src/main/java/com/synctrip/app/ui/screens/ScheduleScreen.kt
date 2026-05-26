package com.synctrip.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onLoadAlts: (scheduleId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
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
                    if (canEdit) {
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
                            IconButton(onClick = onStartEditing) {
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
            }

            when {
                isLoading -> ScheduleLoadingContent(modifier = Modifier.fillMaxSize())
                schedule == null || days.isEmpty() -> ScheduleEmptyContent(modifier = Modifier.fillMaxSize())
                else -> {
                    val dayIndex = selectedDayIndex.coerceIn(0, days.lastIndex)
                    SlotTimeline(
                        slots = days[dayIndex].slots,
                        isEditing = isEditing,
                        isPlanBLoading = isPlanBLoading,
                        onSlotClick = { slot -> detailSlot = slot },
                        onSwapClick = { slot ->
                            detailSlot = slot
                            onLoadAlts(slot.scheduleId)
                            showSwapSheet = true
                        },
                        onPlanBClick = { slot ->
                            planBTargetSlot = slot
                            showPlanBSheet = true
                            onRequestPlanB(slot.place.placeId)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
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
private fun TravelTimeConnector(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (minutes > 0) {
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Outlined.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "약 ${minutes}분 이동",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
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
            (slot.travelTimeFromPrev ?: 0).takeIf { it > 0 }?.let {
                DetailRow(Icons.Outlined.DirectionsWalk, "이동 시간", "이전 장소에서 약 ${it}분")
            }
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
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onSwapSlot: (scheduleId: Long, newPlaceId: Long) -> Unit,
    onLoadAlts: (scheduleId: Long) -> Unit,
    onRequestPlanB: (targetPlaceId: Long) -> Unit,
    onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,
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

        // 편집 중 배너
        if (isEditing) EditingBanner()

        // 주요 콘텐츠 (로딩·빈상태·타임라인)
        when {
            isLoading                          -> ScheduleLoadingContent(modifier = Modifier.weight(1f))
            schedule == null || days.isEmpty() -> ScheduleEmptyContent(modifier = Modifier.weight(1f))
            else -> {
                val dayIndex = selectedDayIndex.coerceIn(0, days.lastIndex)
                SlotTimeline(
                    slots        = days[dayIndex].slots,
                    isEditing    = isEditing,
                    isPlanBLoading = isPlanBLoading,
                    onSlotClick  = { slot -> detailSlot = slot },
                    onSwapClick  = { slot ->
                        detailSlot = slot
                        onLoadAlts(slot.scheduleId)
                        showSwapSheet = true
                    },
                    onPlanBClick = { slot ->
                        planBTargetSlot = slot
                        showPlanBSheet = true
                        onRequestPlanB(slot.place.placeId)
                    },
                    modifier = Modifier.weight(1f),
                )
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
            isLoading = false, isEditing = false, canEdit = true,
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
            isLoading = false, isEditing = true, canEdit = true,
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
            isLoading = true, isEditing = false, canEdit = false,
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
            isLoading = false, isEditing = false, canEdit = false,
            onStartEditing = {}, onFinishEditing = {},
            onSwapSlot = { _, _ -> }, onLoadAlts = {},
            onRequestPlanB = {}, onExecutePlanBSwap = { _, _ -> },
            onBackClick = {}, onShareClick = {},
        )
    }
}
