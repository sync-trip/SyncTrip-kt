package com.synctrip.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.theme.SynctripTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ═════════════════════════════════════════════════════════════════════════════
// 1. Create Trip Screen (2단계 플로우)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 새 여행 생성 화면 — 2단계 플로우.
 * 1단계(여행지 선택): 목록 + 검색 + 해외/국내 탭 + 카테고리 필터
 * 2단계(여행 정보): 밴드 이름, 날짜, 여행 스타일 입력 → createBand API 호출
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    destinations: List<DestinationResponse>,
    selectedDestination: DestinationResponse?,
    destinationQuery: String,
    onDestinationQueryChange: (String) -> Unit,
    onSearchDestination: (String) -> Unit,
    onDestinationSelect: (DestinationResponse) -> Unit,
    bandName: String,
    onBandNameChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    travelStyle: BandTravelStyle,
    onTravelStyleChange: (BandTravelStyle) -> Unit,
    isLoading: Boolean,
    onCreateTrip: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 현재 단계: 1 = 여행지 선택, 2 = 여행 정보 입력
    var page by remember { mutableStateOf(1) }

    val pageTitle  = if (page == 1) "여행지 선택" else "여행 정보"
    val progress   = if (page == 1) 0.5f else 1.0f

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                // 상단 바: X 닫기 + 중앙 타이틀 + N/2 표시
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    },
                    navigationIcon = {
                        // X 버튼: 어느 단계에서든 화면 전체 종료
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Outlined.Close, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        Text(
                            text = "$page/2",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                // 단계 진행 표시 바 (50% / 100%)
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier.fillMaxWidth().height(3.dp),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        },
        bottomBar = {
            // Surface가 nav bar 뒤까지 배경색을 채우고, 버튼은 navigationBarsPadding으로 nav bar 위에 위치
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color          = MaterialTheme.colorScheme.surface,
            ) {
                if (page == 1) {
                    // 1단계 하단: "계속하기" (여행지 선택 후 활성화)
                    Button(
                        onClick  = { page = 2 },
                        enabled  = selectedDestination != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(52.dp),
                        shape  = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("계속하기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                } else {
                    // 2단계 하단: "이전" + "방 만들기"
                    val canCreate = startDate.isNotBlank() && endDate.isNotBlank() && bandName.isNotBlank()
                    Row(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick  = { page = 1 },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(999.dp),
                            border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text("이전", style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick  = onCreateTrip,
                            enabled  = canCreate && !isLoading,
                            modifier = Modifier.weight(2f).height(52.dp),
                            shape    = RoundedCornerShape(999.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    color       = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("방 만들기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (page == 1) {
            DestinationSelectPage(
                destinations             = destinations,
                selectedDestination      = selectedDestination,
                destinationQuery         = destinationQuery,
                onDestinationQueryChange = onDestinationQueryChange,
                onSearchDestination      = onSearchDestination,
                onDestinationSelect      = onDestinationSelect,
                modifier                 = Modifier.padding(innerPadding),
            )
        } else {
            TripInfoPage(
                selectedDestination = selectedDestination!!,
                bandName            = bandName,
                onBandNameChange    = onBandNameChange,
                startDate           = startDate,
                onStartDateChange   = onStartDateChange,
                endDate             = endDate,
                onEndDateChange     = onEndDateChange,
                travelStyle         = travelStyle,
                onTravelStyleChange = onTravelStyleChange,
                modifier            = Modifier.padding(innerPadding),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1단계: 여행지 선택 페이지
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 여행지 선택 페이지 (1/2).
 * 검색창 + 해외/국내 탭 + 카테고리 칩 + 목록으로 여행지를 고른다.
 *
 * 검색 동작:
 * - 검색어 입력 시: NavGraph에서 400ms 디바운스 후 `api/destinations/search` 호출, destinations 갱신
 * - 검색어 있을 때: 탭/카테고리 필터 무시, API 결과 전체 표시 (구 앱 동일 방식)
 * - 검색어 지울 때: NavGraph에서 `api/destinations/popular` 재호출, 필터 복원
 */
/**
 * 여행지 선택 페이지 (1/2).
 * 검색창 + 해외/국내 탭 + 카테고리 칩 + 목록으로 여행지를 고른다.
 *
 * 검색 동작 (구 앱 동일):
 * - 키보드 검색 버튼 클릭 시에만 API 호출 (비용 절감)
 * - 탭/카테고리는 검색 중에도 항상 표시 (탭 전환 시 검색 초기화)
 * - 검색 결과는 탭/카테고리 필터 미적용, 인기 목록은 필터 적용
 */
@Composable
private fun DestinationSelectPage(
    destinations: List<DestinationResponse>,
    selectedDestination: DestinationResponse?,
    destinationQuery: String,
    onDestinationQueryChange: (String) -> Unit,
    onSearchDestination: (String) -> Unit,
    onDestinationSelect: (DestinationResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager  = LocalFocusManager.current
    // 0 = 해외, 1 = 국내
    var selectedTab    by remember { mutableStateOf(0) }
    // null = 인기(전체), "일본" 등 = 해당 지역
    var selectedRegion by remember { mutableStateOf<String?>(null) }

    val regions = listOf("인기", "일본", "동남아시아", "유럽", "미주/오세아니아")

    // 검색 중일 때는 탭/카테고리 필터 무시 (API가 이미 검색한 결과를 그대로 표시)
    // 검색어 없을 때만 탭/카테고리 필터 적용
    val isSearching = destinationQuery.isNotBlank()
    val filtered = if (isSearching) {
        destinations
    } else {
        destinations.filter { dest ->
            val tabOk = if (selectedTab == 0) dest.overseas else !dest.overseas
            val regionOk = when (selectedRegion) {
                null, "인기" -> true
                "일본"        -> dest.countryCode == "JP"
                "동남아시아"  -> dest.region == "동남아시아"
                "유럽"        -> dest.region == "유럽"
                "미주/오세아니아" -> dest.region == "미주" || dest.region == "오세아니아" || dest.region == "미주/오세아니아"
                else          -> dest.region == selectedRegion
            }
            tabOk && regionOk
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 검색 필드 — 키보드 검색 버튼 클릭 시에만 API 호출 (구 앱 동일)
        OutlinedTextField(
            value         = destinationQuery,
            onValueChange = onDestinationQueryChange,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 4.dp),
            placeholder     = { Text("도시 이름으로 검색") },
            leadingIcon     = { Text("🔍", fontSize = 18.sp) },
            trailingIcon    = if (destinationQuery.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        onDestinationQueryChange("")
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "검색어 지우기", modifier = Modifier.size(18.dp))
                    }
                }
            } else null,
            shape           = RoundedCornerShape(12.dp),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                // 검색 버튼 클릭 시에만 API 호출 — 자동 검색 없음
                if (destinationQuery.isNotBlank()) onSearchDestination(destinationQuery)
                focusManager.clearFocus()
            }),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        // 해외 / 국내 탭 — 탭 전환 시 검색창 초기화 (구 앱 동일)
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = MaterialTheme.colorScheme.primary,
        ) {
            listOf("해외", "국내").forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick  = {
                        selectedTab    = i
                        selectedRegion = null
                        // 탭 전환 시 검색창 초기화 → NavGraph에서 인기 목록 재로드
                        onDestinationQueryChange("")
                    },
                    text = {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    },
                )
            }
        }

        // 카테고리 필터 칩 (해외 탭일 때만 표시)
        if (selectedTab == 0) {
            LazyRow(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(horizontal = 20.dp),
            ) {
                items(regions) { region ->
                    val isSel = (region == "인기" && selectedRegion == null) || selectedRegion == region
                    FilterChip(
                        selected = isSel,
                        onClick  = { selectedRegion = if (region == "인기") null else region },
                        label    = { Text(region, style = MaterialTheme.typography.labelLarge) },
                        shape    = RoundedCornerShape(999.dp),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // 여행지 목록 (LazyColumn)
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { "${it.countryCode}_${it.name}" }) { dest ->
                DestinationListItem(
                    destination = dest,
                    isSelected  = dest.name == selectedDestination?.name,
                    onSelect    = { onDestinationSelect(dest) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/**
 * 여행지 목록 아이템.
 * 썸네일 + 국기 이모지 + 도시명/국가/설명 + 선택 버튼으로 구성된다.
 * 선택 시 버튼이 체크 원형으로 바뀐다.
 */
@Composable
private fun DestinationListItem(
    destination: DestinationResponse,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val flag = countryFlagEmoji(destination.countryCode)

    // 선택 여부와 관계없이 카드 배경은 항상 흰색/surface 유지 — 이중 네모 방지
    // 선택 표시는 파란 테두리 + 썸네일 위 체크 배지로만 표현
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border    = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 썸네일 (없으면 국기 이모지로 대체)
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (!destination.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = destination.thumbnailUrl,
                        contentDescription = destination.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(flag, fontSize = 32.sp)
                }
            }

            // 도시 이름, 국가, 설명
            Column(
                modifier             = Modifier.weight(1f),
                verticalArrangement  = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text  = "$flag  ${destination.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text  = destination.country,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                if (!destination.description.isNullOrBlank()) {
                    Text(
                        text     = destination.description,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                    )
                }
            }

            // 선택 버튼: 버튼 형태를 유지하면서 선택 상태를 표현
            // 미선택 → 아웃라인 "선택" / 선택됨 → 파란 채움 "선택됨 ✓"
            if (isSelected) {
                Button(
                    onClick        = onSelect,
                    shape          = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier       = Modifier.height(36.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = Color.White,
                    ),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("선택됨", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick        = onSelect,
                    shape          = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier       = Modifier.height(36.dp),
                ) {
                    Text("선택", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2단계: 여행 정보 입력 페이지
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 여행 정보 입력 페이지 (2/2).
 * 선택된 여행지 확인 카드, 밴드 이름, 날짜 2개, 여행 스타일을 입력한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripInfoPage(
    selectedDestination: DestinationResponse,
    bandName: String,
    onBandNameChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    travelStyle: BandTravelStyle,
    onTravelStyleChange: (BandTravelStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flag = countryFlagEmoji(selectedDestination.countryCode)

    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Text(
            text  = "여행 정보를 입력해주세요",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )

        // 선택된 여행지 확인 카드 (파란 배경)
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier              = Modifier.padding(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(flag, fontSize = 36.sp)
                Column {
                    Text(
                        text  = "선택한 여행지",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        ),
                    )
                    Text(
                        text  = "${selectedDestination.name}, ${selectedDestination.country}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }

        // 밴드 이름 입력
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("밴드 이름", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            OutlinedTextField(
                value         = bandName,
                onValueChange = onBandNameChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("여행 이름을 입력하세요") },
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

        // 여행 기간 — 트리플 스타일 범위 선택 카드
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("여행 기간", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            DateRangeCard(
                startDate          = startDate,
                endDate            = endDate,
                onDateRangeSelected = { start, end ->
                    onStartDateChange(start)
                    onEndDateChange(end)
                },
            )
        }

        // 여행 스타일 선택
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("여행 스타일", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text  = "일정 생성에 반영돼요",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            TravelStyleToggle(selected = travelStyle, onSelect = onTravelStyleChange)
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 출발일/귀국일 범위 카드.
 * 탭 시 트리플 스타일 풀스크린 캘린더 다이얼로그를 열어 범위를 선택한다.
 */
@Composable
private fun DateRangeCard(
    startDate: String,
    endDate: String,
    onDateRangeSelected: (start: String, end: String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Card(
        onClick  = { showPicker = true },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text("출발일", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (startDate.isBlank()) "날짜를 선택하세요" else startDate,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = if (startDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.End) {
                Text("귀국일", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (endDate.isBlank()) "날짜를 선택하세요" else endDate,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = if (endDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }

    if (showPicker) {
        DateRangePickerDialog(
            initialStart = startDate,
            initialEnd   = endDate,
            onConfirm    = { start, end ->
                onDateRangeSelected(start, end)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 트리플 스타일 날짜 범위 선택 풀스크린 다이얼로그.
 * - 현재 달~11개월 후 세로 스크롤 캘린더
 * - 일/토 빨간색, 오늘 "오늘" 라벨, 과거 날짜 비활성
 * - 첫 탭 = 출발일, 두 번째 탭 = 귀국일, 선택 범위 파란 하이라이트
 * - 공휴일 API 없이 DayOfWeek 으로 일/토 판별
 */
@Composable
private fun DateRangePickerDialog(
    initialStart: String,
    initialEnd: String,
    onConfirm: (start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val fmt   = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val dispFmt = remember { DateTimeFormatter.ofPattern("M월 d일") }

    var startDate by remember { mutableStateOf(runCatching { LocalDate.parse(initialStart, fmt) }.getOrNull()) }
    var endDate   by remember { mutableStateOf(runCatching { LocalDate.parse(initialEnd, fmt) }.getOrNull()) }

    val months = remember { (0..11).map { YearMonth.now().plusMonths(it.toLong()) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

                // ── 헤더 ──────────────────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.ArrowBack, "닫기") }
                    Text(
                        "여행날짜 수정",
                        style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
                Text(
                    "일정에 따른 날씨예보, 여행 정보를 알려드립니다.",
                    style    = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))

                // ── 출발일 / 귀국일 칩 ────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateSelectionChip("출발일", startDate?.format(dispFmt))
                    Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DateSelectionChip("귀국일", endDate?.format(dispFmt))
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                // ── 요일 헤더 (일~토) ─────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, day ->
                        Text(
                            day,
                            modifier  = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style     = MaterialTheme.typography.labelMedium.copy(
                                color      = if (i == 0 || i == 6) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                HorizontalDivider()

                // ── 월별 캘린더 스크롤 ────────────────────────────────────
                LazyColumn(
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    months.forEach { yearMonth ->
                        item(key = yearMonth.toString()) {
                            CalendarMonth(
                                yearMonth  = yearMonth,
                                today      = today,
                                startDate  = startDate,
                                endDate    = endDate,
                                onDayClick = { date ->
                                    when {
                                        // 범위 완성됐거나 출발일 없으면 → 출발일 재설정
                                        startDate == null || (startDate != null && endDate != null) -> {
                                            startDate = date; endDate = null
                                        }
                                        // 출발일보다 이전 탭 → 출발일 재설정
                                        date < startDate!! -> {
                                            startDate = date; endDate = null
                                        }
                                        // 귀국일 설정
                                        else -> endDate = date
                                    }
                                },
                            )
                        }
                    }
                }

                // ── 확인 버튼 ─────────────────────────────────────────────
                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick  = { if (startDate != null && endDate != null) onConfirm(startDate!!.format(fmt), endDate!!.format(fmt)) },
                        enabled  = startDate != null && endDate != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(52.dp),
                        shape    = RoundedCornerShape(999.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("확인", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

/** 출발일/귀국일 현황 칩 — 선택 전: 회색 / 선택 후: 파란 테두리 */
@Composable
private fun DateSelectionChip(label: String, value: String?) {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = if (value != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                 else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
                value ?: "선택하세요",
                style = MaterialTheme.typography.titleSmall.copy(
                    color      = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

/** 한 달 캘린더 — 년/월 헤더 + 날짜 그리드 */
@Composable
private fun CalendarMonth(
    yearMonth: YearMonth,
    today: LocalDate,
    startDate: LocalDate?,
    endDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDay    = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    // Java DayOfWeek: MON=1..SUN=7 → col 인덱스 0=일,1=월..6=토: (value % 7)
    val startOffset = firstDay.dayOfWeek.value % 7

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            "${yearMonth.year}년 ${yearMonth.monthValue}월",
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(vertical = 16.dp),
        )
        val rows = (startOffset + daysInMonth + 6) / 7
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - startOffset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (dayNumber in 1..daysInMonth) {
                            CalendarDay(
                                date       = yearMonth.atDay(dayNumber),
                                today      = today,
                                startDate  = startDate,
                                endDate    = endDate,
                                col        = col,
                                onDayClick = onDayClick,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 날짜 셀 — 범위 배경 레이어 + 선택 원 + 텍스트 */
@Composable
private fun CalendarDay(
    date: LocalDate,
    today: LocalDate,
    startDate: LocalDate?,
    endDate: LocalDate?,
    col: Int,
    onDayClick: (LocalDate) -> Unit,
) {
    val isStart   = date == startDate
    val isEnd     = date == endDate
    val isInRange = startDate != null && endDate != null && date > startDate && date < endDate
    val isToday   = date == today
    val isPast    = date < today

    val primary    = MaterialTheme.colorScheme.primary
    val rangeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(enabled = !isPast) { onDayClick(date) },
        contentAlignment = Alignment.Center,
    ) {
        // 범위 배경
        if (isInRange) Box(Modifier.fillMaxSize().background(rangeColor))
        // 시작일 오른쪽 절반 (귀국일이 있을 때)
        if (isStart && endDate != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd).background(rangeColor))
        }
        // 귀국일 왼쪽 절반
        if (isEnd) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterStart).background(rangeColor))
        }
        // 선택된 날짜 파란 원
        if (isStart || isEnd) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(primary))
        } else if (isToday) {
            // 오늘 테두리 원
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.5.dp, primary, CircleShape))
        }
        // 날짜 숫자 + 오늘 라벨
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = when {
                        isPast           -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        isStart || isEnd -> Color.White
                        col == 0 || col == 6 -> Color(0xFFE53935)   // 일/토
                        else             -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isStart || isEnd || isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (isToday && !isStart && !isEnd) {
                Text(
                    "오늘",
                    style = MaterialTheme.typography.labelSmall.copy(color = primary, fontSize = 9.sp),
                )
            }
        }
    }
}

/**
 * RELAXED / PACKED 여행 스타일 카드 토글.
 * 이모지 텍스트로 시각적 표현 (🌴 = 여유롭게, ⚡ = 알차게).
 */
@Composable
private fun TravelStyleToggle(selected: BandTravelStyle, onSelect: (BandTravelStyle) -> Unit) {
    // IntrinsicSize.Max — 두 카드 중 높은 쪽에 맞춰 동일 높이 보장
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        TravelStyleCard(
            emoji    = "🌴",
            title    = "여유롭게",
            subtitle = "느긋하게 즐기는 여행",
            isSelected = selected == BandTravelStyle.RELAXED,
            onClick  = { onSelect(BandTravelStyle.RELAXED) },
            modifier = Modifier.weight(1f),
        )
        TravelStyleCard(
            emoji    = "⚡",
            title    = "알차게",
            subtitle = "최대한 많이 보는 여행",
            isSelected = selected == BandTravelStyle.PACKED,
            onClick  = { onSelect(BandTravelStyle.PACKED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TravelStyleCard(
    emoji: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border    = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp),
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style     = MaterialTheme.typography.bodySmall.copy(
                    color = if (isSelected) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** countryCode(ISO 3166-1 alpha-2) → 국기 이모지 변환 */
private fun countryFlagEmoji(code: String): String = when (code.uppercase()) {
    "JP" -> "🇯🇵"; "TH" -> "🇹🇭"; "ID" -> "🇮🇩"; "FR" -> "🇫🇷"
    "IT" -> "🇮🇹"; "ES" -> "🇪🇸"; "US" -> "🇺🇸"; "AU" -> "🇦🇺"
    "VN" -> "🇻🇳"; "SG" -> "🇸🇬"; "GB" -> "🇬🇧"; "HK" -> "🇭🇰"
    "TW" -> "🇹🇼"; "KR" -> "🇰🇷"; "PH" -> "🇵🇭"; "MY" -> "🇲🇾"
    "DE" -> "🇩🇪"; "NZ" -> "🇳🇿"; "CN" -> "🇨🇳"; "TR" -> "🇹🇷"
    "GR" -> "🇬🇷"; "MX" -> "🇲🇽"; "CA" -> "🇨🇦"; "PT" -> "🇵🇹"
    else -> "🌍"
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. AI Loading Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AiLoadingScreen(
    status: AiGenerationStatus,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(status.isComplete) {
        if (status.isComplete) onComplete()
    }

    val pulseAnim by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "ai-pulse",
    )

    Box(
        modifier         = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 30.dp, y = 80.dp)
                .size(300.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape)
                .alpha(pulseAnim),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-20).dp, y = (-60).dp)
                .size(250.dp)
                .background(MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.25f), CircleShape)
                .alpha(1f - (pulseAnim - 0.85f) * 5f),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FlightTakeoff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
            }

            Spacer(Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status.currentStep, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text("${status.progressPercent}%", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { status.progressPercent / 100f },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "AI가 최적의 여행 일정을 만들고 있어요",
                style     = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "그룹의 취향과 동선을 분석해서\n최고의 경험을 설계하고 있어요",
                style     = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    Pair(Icons.Outlined.Route,      "동선 최적화"),
                    Pair(Icons.Outlined.Schedule,   "시간 안배"),
                    Pair(Icons.Outlined.Restaurant, "맛집 추천"),
                ).forEach { (icon, label) -> AiFeatureChip(icon = icon, label = label) }
            }
        }
    }
}

@Composable
private fun AiFeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. Final Itinerary Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ItineraryScreen(
    itinerary: TripItinerary,
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        "SyncTrip",
                        style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Outlined.ArrowBack, "뒤로") }
                },
                actions = {
                    IconButton(onClick = onExportClick) { Icon(Icons.Outlined.Share, "공유", tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model              = itinerary.heroImageUrl,
                    contentDescription = itinerary.destination,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                Text(
                    text     = itinerary.destination,
                    style    = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(text = itinerary.title, style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            itinerary.days.forEach { day ->
                DaySection(day = day, onEventClick = onEventClick)
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DaySection(day: ItineraryDay, onEventClick: (String) -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text     = day.title,
                style    = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            day.events.forEachIndexed { idx, event ->
                TimelineEvent(event = event, isLast = idx == day.events.lastIndex, onEventClick = onEventClick)
            }
        }
    }
}

@Composable
private fun TimelineEvent(event: ItineraryEvent, isLast: Boolean, onEventClick: (String) -> Unit) {
    val eventColor = when (event.category) {
        EventCategory.TRANSPORT     -> MaterialTheme.colorScheme.primary
        EventCategory.ACCOMMODATION -> MaterialTheme.colorScheme.secondary
        EventCategory.FOOD          -> MaterialTheme.colorScheme.tertiary
        EventCategory.ACTIVITY      -> MaterialTheme.colorScheme.error
        EventCategory.REST          -> MaterialTheme.colorScheme.outline
    }

    val eventIcon = when (event.category) {
        EventCategory.TRANSPORT     -> Icons.Outlined.FlightTakeoff
        EventCategory.ACCOMMODATION -> Icons.Outlined.Hotel
        EventCategory.FOOD          -> Icons.Outlined.Restaurant
        EventCategory.ACTIVITY      -> Icons.Outlined.Attractions
        EventCategory.REST          -> Icons.Outlined.Hotel
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEventClick(event.id) }
            .padding(vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(eventColor), contentAlignment = Alignment.Center) {
                Icon(eventIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = if (!isLast) 16.dp else 0.dp)) {
            Text(event.time, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.height(2.dp))
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            if (event.description != null) {
                Text(event.description, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Preview
// ═════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateTripPage1Preview() {
    val dests = listOf(
        DestinationResponse("도쿄", "일본", "JP", lat = 35.68, lng = 139.69, overseas = true, region = "일본", description = "신주쿠, 시부야, 아키하바라", thumbnailUrl = null),
        DestinationResponse("오사카", "일본", "JP", lat = 34.69, lng = 135.50, overseas = true, region = "일본", description = "오사카, 교토, 고베, 나라", thumbnailUrl = null),
        DestinationResponse("방콕", "태국", "TH", lat = 13.75, lng = 100.50, overseas = true, region = "동남아시아", description = "방콕, 파타야, 아유타야", thumbnailUrl = null),
    )
    SynctripTheme {
        CreateTripScreen(
            destinations             = dests,
            selectedDestination      = null,
            destinationQuery         = "",
            onDestinationQueryChange = {},
            onSearchDestination      = {},
            onDestinationSelect      = {},
            bandName                 = "",
            onBandNameChange         = {},
            startDate                = "",
            onStartDateChange        = {},
            endDate                  = "",
            onEndDateChange          = {},
            travelStyle              = BandTravelStyle.RELAXED,
            onTravelStyleChange      = {},
            isLoading                = false,
            onCreateTrip             = {},
            onBackClick              = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AiLoadingScreenPreview() {
    SynctripTheme {
        AiLoadingScreen(
            status     = AiGenerationStatus("j1", 65, "동선 최적화 중…", false),
            onComplete = {},
        )
    }
}
