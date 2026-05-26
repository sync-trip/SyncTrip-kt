package com.synctrip.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// 1. Place Search Screen
// ═════════════════════════════════════════════════════════════════════════════

/** 장소 탐색 화면 — 하단 장바구니 바(구 앱 cartCard 스타일)와 바텀시트로 장바구니 확인 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: PlaceCategory,
    onCategoryChange: (PlaceCategory) -> Unit,
    places: List<ApiPlaceSearchResult>,
    isLoading: Boolean = false,
    picks: List<PlacePickResponse> = emptyList(),
    maxPickCount: Int = 5,
    onSearch: () -> Unit = {},
    onPlaceClick: (String) -> Unit,
    onCartToggle: (String) -> Unit,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    var showCartSheet by remember { mutableStateOf(false) }
    // 선택된 장소 externalId — 목록에서 실시간 조회해 북마크 상태 반영
    var selectedExternalId by remember { mutableStateOf<String?>(null) }
    val selectedPlace = selectedExternalId?.let { id -> places.find { it.externalId == id } }
    val cartCount = picks.size

    Scaffold(
        modifier  = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar    = {
            TopAppBar(
                title = {
                    Text(
                        "SyncTrip",
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
        },
        // 구 앱 cartCard 스타일 — 하단에 N/5 카운트 표시
        bottomBar = {
            CartBottomBar(
                cartCount    = cartCount,
                maxPickCount = maxPickCount,
                onClick      = { showCartSheet = true },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("장소 탐색", style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onBackground))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("장소 검색") },
                    leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                    shape           = RoundedCornerShape(12.dp),
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.height(12.dp))
            }

            CategoryTabRow(selectedCategory = selectedCategory, onCategoryChange = onCategoryChange)

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlaneLoadingIndicator()
                }
            } else if (places.isEmpty()) {
                // 검색 전 또는 결과 없음 — 안내 문구 표시
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier           = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "검색하여 장소를 담아봐요!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "원하는 장소를 검색하고 장바구니에 담아보세요.",
                            style     = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.weight(1f),
                ) {
                    items(places, key = { it.externalId }) { place ->
                        PlaceCard(
                            place        = place,
                            onPlaceClick = { selectedExternalId = place.externalId },
                            onCartToggle = { onCartToggle(place.externalId) },
                        )
                    }
                }
            }
        }
    }

    // 장바구니 바텀시트 — 담은 장소 목록 + 개별 삭제
    if (showCartSheet) {
        ModalBottomSheet(onDismissRequest = { showCartSheet = false }) {
            CartBottomSheetContent(
                picks        = picks,
                maxPickCount = maxPickCount,
                onDeletePick = { externalId -> onCartToggle(externalId) },
            )
        }
    }

    // 장소 상세 바텀시트 — 카드 클릭 시 표시
    if (selectedPlace != null) {
        PlaceSearchDetailBottomSheet(
            place        = selectedPlace,
            onDismiss    = { selectedExternalId = null },
            onCartToggle = { onCartToggle(selectedPlace.externalId) },
        )
    }
}

@Composable
private fun CategoryTabRow(selectedCategory: PlaceCategory, onCategoryChange: (PlaceCategory) -> Unit) {
    val tabs = mapOf(
        PlaceCategory.ALL      to "전체",
        PlaceCategory.FOOD     to "음식점",
        PlaceCategory.CULTURE  to "관광지",
        PlaceCategory.ACTIVITY to "액티비티",
        PlaceCategory.SHOPPING to "쇼핑",
        PlaceCategory.NATURE   to "자연",
    )

    ScrollableTabRow(
        selectedTabIndex = PlaceCategory.entries.indexOf(selectedCategory),
        containerColor   = MaterialTheme.colorScheme.surface,
        contentColor     = MaterialTheme.colorScheme.primary,
        edgePadding      = 20.dp,
    ) {
        tabs.entries.forEachIndexed { _, (category, label) ->
            Tab(
                selected = selectedCategory == category,
                onClick  = { onCategoryChange(category) },
                text     = {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PlaceCard(place: ApiPlaceSearchResult, onPlaceClick: () -> Unit, onCartToggle: () -> Unit) {
    // 북마크 아이콘 스프링 바운스 애니메이션
    val iconScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // isBookmarked 변경 시 스프링 반동 효과
    LaunchedEffect(place.isBookmarked) {
        if (place.isBookmarked) {
            // 담기 → 통통 튀어오름
            iconScale.animateTo(1.45f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
            iconScale.animateTo(1f,    spring(dampingRatio = Spring.DampingRatioLowBouncy))
        } else {
            // 제거 → 살짝 찌그러졌다 복귀
            iconScale.animateTo(0.75f, spring(stiffness = Spring.StiffnessMedium))
            iconScale.animateTo(1f,    spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    // 버튼 배경색 smooth 전환 — 미담김: 반투명 검정 / 담김: primary 색상
    val buttonBgColor by animateColorAsState(
        targetValue   = if (place.isBookmarked) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        else Color.Black.copy(alpha = 0.42f),
        label         = "cartButtonBg",
    )

    Card(
        onClick   = onPlaceClick,
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(modifier = Modifier.height(120.dp)) {
                if (place.thumbnailUrl != null) {
                    AsyncImage(model = place.thumbnailUrl, contentDescription = place.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    }
                }

                IconButton(
                    onClick  = {
                        onCartToggle()
                        // 클릭 시 즉각 반응감 — LaunchedEffect와 별도로 퀵 탭 피드백
                        scope.launch {
                            iconScale.animateTo(1.2f, spring(stiffness = Spring.StiffnessHigh))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(buttonBgColor, CircleShape),
                ) {
                    Icon(
                        imageVector        = if (place.isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "장바구니",
                        tint               = Color.White,
                        modifier           = Modifier.size(18.dp).scale(iconScale.value),
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (place.rating != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Star, null, tint = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("%.1f".format(place.rating), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 화면 하단 장바구니 바 — 구 앱 cartCard 디자인 참고, N/5 카운트 표시 */
@Composable
private fun CartBottomBar(cartCount: Int, maxPickCount: Int, onClick: () -> Unit) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // 시스템 네비게이션 바 높이만큼 하단 패딩 추가 — 겹침 방지
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ShoppingCart,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "담은 장소",
                style    = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // 카운트 변경 시 숫자가 위로 슬라이드하며 교체됨
            AnimatedContent(
                targetState  = cartCount,
                transitionSpec = {
                    if (targetState > initialState) {
                        // 담기 → 숫자 아래에서 올라옴
                        (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                    } else {
                        // 삭제 → 숫자 위에서 내려옴
                        (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                    }
                },
                label = "cartCount",
            ) { count ->
                Text(
                    "$count / $maxPickCount",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = if (count >= maxPickCount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}

/** 장바구니 바텀시트 본문 — 담은 장소 목록 + 개별 삭제 버튼 */
@Composable
private fun CartBottomSheetContent(
    picks: List<PlacePickResponse>,
    maxPickCount: Int,
    onDeletePick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "담은 장소 (${picks.size} / $maxPickCount)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(16.dp))

        if (picks.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "아직 담은 장소가 없어요",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        } else {
            picks.forEach { pick ->
                CartPickListItem(pick = pick, onDelete = { onDeletePick(pick.externalId) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 장바구니 바텀시트 내 개별 장소 행 — 썸네일, 이름, 카테고리, 삭제 버튼 */
@Composable
private fun CartPickListItem(pick: PlacePickResponse, onDelete: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (pick.thumbnailUrl != null) {
                AsyncImage(
                    model              = pick.thumbnailUrl,
                    contentDescription = pick.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(pick.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                pick.address ?: pick.category.name,
                style   = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, contentDescription = "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Place Search Detail Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

/** 장소 탐색 화면에서 카드 클릭 시 나타나는 장소 상세 바텀시트 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSearchDetailBottomSheet(
    place: ApiPlaceSearchResult,
    onDismiss: () -> Unit,
    onCartToggle: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            // 썸네일 이미지
            if (place.thumbnailUrl != null) {
                AsyncImage(
                    model              = place.thumbnailUrl,
                    contentDescription = place.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(16.dp))
            }

            // 카테고리 칩
            PlaceDetailCategoryChip(category = place.category)
            Spacer(Modifier.height(6.dp))

            // 장소명
            Text(
                text  = place.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                ),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // 주소 / 평점 상세 행
            place.address?.let { PlaceDetailRow(Icons.Outlined.LocationOn, "주소", it) }
            place.rating?.let  { PlaceDetailRow(Icons.Outlined.Star, "평점", "%.1f / 5.0".format(it)) }

            Spacer(Modifier.height(20.dp))

            // 장바구니 담기 / 제거 버튼
            if (place.isBookmarked) {
                OutlinedButton(
                    onClick  = onCartToggle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("장바구니에서 제거", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            } else {
                Button(
                    onClick  = onCartToggle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("장바구니에 담기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            Spacer(Modifier.height(8.dp))

            // 설치된 지도 앱(카카오/네이버/구글)으로 장소 상세 보기
            OutlinedButton(
                onClick  = {
                    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("지도에서 보기", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PlaceDetailCategoryChip(category: ApiPlaceCategory) {
    val tint = placeDetailCategoryColor(category)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(placeDetailCategoryIcon(category), contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            Text(placeDetailCategoryLabel(category), style = MaterialTheme.typography.labelSmall.copy(color = tint))
        }
    }
}

@Composable
private fun PlaceDetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    }
}

@Composable
private fun placeDetailCategoryColor(category: ApiPlaceCategory): Color = when (category) {
    ApiPlaceCategory.FOOD     -> MaterialTheme.colorScheme.error
    ApiPlaceCategory.CULTURE  -> MaterialTheme.colorScheme.tertiary
    ApiPlaceCategory.ACTIVITY -> MaterialTheme.colorScheme.secondary
    ApiPlaceCategory.SHOPPING -> MaterialTheme.colorScheme.primary
    ApiPlaceCategory.NATURE   -> Color(0xFF2E7D32)
    ApiPlaceCategory.ETC      -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun placeDetailCategoryIcon(category: ApiPlaceCategory): ImageVector = when (category) {
    ApiPlaceCategory.FOOD     -> Icons.Outlined.Restaurant
    ApiPlaceCategory.CULTURE  -> Icons.Outlined.Museum
    ApiPlaceCategory.ACTIVITY -> Icons.Outlined.SportsBasketball
    ApiPlaceCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    ApiPlaceCategory.NATURE   -> Icons.Outlined.Park
    ApiPlaceCategory.ETC      -> Icons.Outlined.Place
}

private fun placeDetailCategoryLabel(category: ApiPlaceCategory): String = when (category) {
    ApiPlaceCategory.FOOD     -> "음식"
    ApiPlaceCategory.CULTURE  -> "문화"
    ApiPlaceCategory.ACTIVITY -> "액티비티"
    ApiPlaceCategory.SHOPPING -> "쇼핑"
    ApiPlaceCategory.NATURE   -> "자연"
    ApiPlaceCategory.ETC      -> "기타"
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. My Passport Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun MyPassportScreen(
    user: UserProfile,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    newStampIds: Set<String> = emptySet(),
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        "My Passport",
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
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { PassportProfileCard(user = user) }

            item {
                Text("방문한 도시", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    user.passportStamps.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "아직 방문한 도시가 없어요\n첫 여행을 떠나보세요!",
                                style     = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        PassportStampGrid(stamps = user.passportStamps, newStampIds = newStampIds)
                    }
                }
            }
        }
    }
}

@Composable
private fun PassportProfileCard(user: UserProfile) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (user.profileImageUrl != null) {
                    AsyncImage(model = user.profileImageUrl, contentDescription = user.nickname, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(user.nickname.take(1).uppercase(), style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(user.nickname, style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FlightTakeoff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${user.totalTrips}번의 여행", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn(label = "방문 도시", value = "${user.passportStamps.size}")
                VerticalDivider(Modifier.height(36.dp))
                StatColumn(label = "총 여행", value = "${user.totalTrips}")
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

/**
 * 여권 스탬프 그리드.
 * 진입 시 스탬프마다 순차적으로 도장을 쾅 찍는 애니메이션이 실행된다.
 * newestStampId 에 해당하는 스탬프는 마지막에 더 강한 바운스 + 잉크 번짐 효과.
 */
/**
 * 여권 스탬프 그리드.
 * - 기존 스탬프(newStampIds에 없는 것): 즉시 표시, 애니메이션 없음
 * - 신규 스탬프(newStampIds에 있는 것): 순차 stagger + spring 바운스 + 잉크 번짐
 * 신규 스탬프는 기존 스탬프가 모두 표시된 뒤 순서대로 쾅쾅 찍힌다.
 */
@Composable
private fun PassportStampGrid(
    stamps: List<PassportStamp>,
    newStampIds: Set<String> = emptySet(),
) {
    val rotations = listOf(-4f, 3f, -2f, 5f, -3f, 2f)

    // 신규 스탬프 인덱스만 순차 활성화 (기존 스탬프는 처음부터 true)
    val stampIds      = stamps.map { it.id }
    val newStampList  = stamps.filter { it.id in newStampIds }
    val visibleStates = remember(stampIds) {
        stamps.map { mutableStateOf(it.id !in newStampIds) }
    }

    LaunchedEffect(stampIds, newStampIds) {
        // 신규 스탬프만 stagger 애니메이션 — 300ms 간격으로 쾅쾅
        newStampList.forEach { newStamp ->
            val idx = stamps.indexOf(newStamp)
            if (idx >= 0) {
                delay(300L * newStampList.indexOf(newStamp))
                visibleStates[idx].value = true
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stamps.chunked(3).forEachIndexed { rowIdx, row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { colIdx, stamp ->
                    val idx      = rowIdx * 3 + colIdx
                    val rotation = rotations[idx % rotations.size]
                    val isNew    = stamp.id in newStampIds
                    val accentColor = when (stamp.accentColor) {
                        StampColor.PRIMARY   -> MaterialTheme.colorScheme.primary
                        StampColor.SECONDARY -> MaterialTheme.colorScheme.secondary
                        StampColor.ERROR     -> MaterialTheme.colorScheme.error
                        StampColor.TERTIARY  -> MaterialTheme.colorScheme.tertiary
                        StampColor.FIXED     -> MaterialTheme.colorScheme.primaryFixedDim
                    }

                    AnimatedVisibility(
                        visible = visibleStates[idx].value,
                        // 기존 스탬프: 즉시 나타남 (fadeIn 0ms). 신규: 도장 쾅 spring 바운스
                        enter   = if (isNew) {
                            scaleIn(
                                initialScale  = 1.7f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessMedium,
                                ),
                            ) + fadeIn(animationSpec = tween(80))
                        } else {
                            fadeIn(animationSpec = tween(0))  // 즉시 표시
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        StampCell(
                            stamp       = stamp,
                            rotation    = rotation,
                            accentColor = accentColor,
                            isNew       = isNew,
                        )
                    }
                }
                // 행의 빈 칸 채우기 (3열 고정)
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 도장 하나 셀.
 * isNew=true이면 잉크 번짐 링이 fade-out되는 효과 추가.
 */
@Composable
private fun StampCell(
    stamp: PassportStamp,
    rotation: Float,
    accentColor: Color,
    isNew: Boolean,
) {
    // 잉크 번짐 링 알파 — 신규 스탬프가 찍힌 직후 밝았다가 사라짐
    val inkAlpha = remember { Animatable(if (isNew) 0.9f else 0f) }
    if (isNew) {
        LaunchedEffect(Unit) {
            delay(250L)
            inkAlpha.animateTo(0f, animationSpec = tween(durationMillis = 1400))
        }
    }

    Box(modifier = Modifier.aspectRatio(1f)) {
        // 잉크 번짐 링 (신규 스탬프 전용, 점차 사라짐)
        if (isNew) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation)
                    .border(8.dp, accentColor.copy(alpha = inkAlpha.value), CircleShape),
            )
        }

        // 스탬프 본체
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
                .clip(CircleShape)
                .border(3.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.FlightLand,
                    contentDescription = null,
                    tint               = accentColor.copy(alpha = 0.7f),
                    modifier           = Modifier.size(28.dp),
                )
                Text(
                    text  = stamp.cityCode,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color      = accentColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    ),
                )
                Text(
                    text      = stamp.cityName,
                    style     = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text  = stamp.visitDate,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. Notification Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NotificationScreen(
    groups: Map<String, List<NotificationItem>>,
    onMarkAllRead: () -> Unit,
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        "알림",
                        style     = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary),
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Outlined.ArrowBack, "뒤로") }
                },
                actions = {
                    IconButton(onClick = onMarkAllRead) { Icon(Icons.Outlined.DoneAll, "모두 읽음", tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (group, groupItems) ->
                item(key = "header-$group") {
                    Text(
                        group.uppercase(),
                        style    = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }

                items(groupItems, key = { it.id }) { item ->
                    NotificationCard(item = item, onClick = { onItemClick(item.id) })
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit) {
    val iconAndColor: Pair<ImageVector, Color> = when (item.type) {
        NotificationType.VOTING          -> Pair(Icons.Outlined.HowToVote, MaterialTheme.colorScheme.primaryContainer)
        NotificationType.SETTLEMENT      -> Pair(Icons.Outlined.Payments, MaterialTheme.colorScheme.secondaryContainer)
        NotificationType.SCHEDULE_CHANGE -> Pair(Icons.Outlined.EventBusy, MaterialTheme.colorScheme.surfaceContainerHighest)
        NotificationType.FLIGHT_UPDATE   -> Pair(Icons.Outlined.FlightTakeoff, MaterialTheme.colorScheme.surfaceContainerHighest)
        NotificationType.GENERAL         -> Pair(Icons.Outlined.Notifications, MaterialTheme.colorScheme.surfaceContainerHighest)
    }

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (!item.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border    = if (!item.isRead) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        elevation = if (!item.isRead) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (!item.isRead) {
                Box(modifier = Modifier.padding(top = 8.dp, end = 4.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            } else {
                Spacer(Modifier.width(12.dp))
            }

            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconAndColor.second), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconAndColor.first,
                    contentDescription = null,
                    tint        = if (!item.isRead) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier    = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.isRead) 0.8f else 1f),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        item.timeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = if (!item.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(item.body, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.isRead) 0.8f else 1f)))
            }
        }
    }
}

private val previewPlaces = listOf(
    ApiPlaceSearchResult(null, PlaceApiSource.KAKAO, "p1", "N 서울타워",       ApiPlaceCategory.CULTURE,  37.55, 126.98, "용산구", 4.7f, null, false),
    ApiPlaceSearchResult(null, PlaceApiSource.KAKAO, "p2", "명동 미식 본점",   ApiPlaceCategory.FOOD,     37.56, 126.98, "중구",   4.5f, null, true),
    ApiPlaceSearchResult(null, PlaceApiSource.KAKAO, "p3", "한강 자연공원",    ApiPlaceCategory.NATURE,   37.52, 126.99, "영등포구", 4.3f, null, false),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlaceSearchPreview() {
    SynctripTheme {
        PlaceSearchScreen(
            query            = "",
            onQueryChange    = {},
            selectedCategory = PlaceCategory.ALL,
            onCategoryChange = {},
            places           = previewPlaces,
            picks            = emptyList(),
            maxPickCount     = 5,
            onPlaceClick     = {},
            onCartToggle     = {},
            onBackClick      = {},
        )
    }
}

private val previewUser = UserProfile(
    id              = "u1",
    nickname        = "Skybound Explorer",
    profileImageUrl = null,
    homeTown        = "Seoul",
    totalTrips      = 5,
    passportStamps  = listOf(
        PassportStamp("s1", "TYO", "Tokyo, JP",   "OCT 2023", "flight_land", StampColor.PRIMARY),
        PassportStamp("s2", "PAR", "Paris, FR",   "MAY 2023", "flight_land", StampColor.SECONDARY),
        PassportStamp("s3", "SYD", "Sydney, AU",  "JAN 2024", "flight_land", StampColor.ERROR),
        PassportStamp("s4", "BKK", "Bangkok, TH", "MAY 2026", "flight_land", StampColor.TERTIARY),
        PassportStamp("s5", "NYC", "New York, US","MAY 2026", "flight_land", StampColor.FIXED),
    ),
)

// 기존 스탬프만 있는 상태
@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Passport - 신규 없음")
@Composable
private fun MyPassportPreview() {
    SynctripTheme { MyPassportScreen(user = previewUser, onBackClick = {}) }
}

// ▶ Interactive Mode 버튼 클릭 → s4·s5가 순서대로 쾅쾅 찍히는 애니메이션 확인
@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Passport - 신규 스탬프 애니메이션")
@Composable
private fun MyPassportStampAnimPreview() {
    SynctripTheme {
        MyPassportScreen(
            user        = previewUser,
            newStampIds = setOf("s4", "s5"),
            onBackClick = {},
        )
    }
}

private val previewNotifications = mapOf(
    "오늘" to listOf(
        NotificationItem("n1", NotificationType.VOTING,     "투표 시작",  "교토 레스토랑 투표가 열렸어요.", "2분 전",  false),
        NotificationItem("n2", NotificationType.SETTLEMENT, "정산 요청",  "Alex가 45,000원 정산 요청.", "1시간 전", false),
    ),
    "어제" to listOf(
        NotificationItem("n3", NotificationType.SCHEDULE_CHANGE, "일정 변경", "후지산 투어가 오전 8시로 변경됐어요.", "어제", true),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationPreview() {
    SynctripTheme {
        NotificationScreen(groups = previewNotifications, onMarkAllRead = {}, onItemClick = {}, onBackClick = {})
    }
}
