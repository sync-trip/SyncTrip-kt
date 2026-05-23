package com.synctrip.app.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme

// ═════════════════════════════════════════════════════════════════════════════
// 1. Place Search Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PlaceSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: PlaceCategory,
    onCategoryChange: (PlaceCategory) -> Unit,
    places: List<ApiPlaceSearchResult>,
    isLoading: Boolean = false,
    cartCount: Int,
    onSearch: () -> Unit = {},
    onPlaceClick: (String) -> Unit,
    onCartToggle: (String) -> Unit,
    onViewCartClick: () -> Unit,
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
                actions = {
                    BadgedBox(badge = { if (cartCount > 0) Badge { Text("$cartCount") } }) {
                        IconButton(onClick = onViewCartClick) {
                            Icon(Icons.Outlined.ShoppingCart, "장바구니", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            onPlaceClick = { onPlaceClick(place.externalId) },
                            onCartToggle = { onCartToggle(place.externalId) },
                        )
                    }
                }
            }
        }
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
                    onClick  = onCartToggle,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(
                        imageVector        = if (place.isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "장바구니",
                        tint               = Color.White,
                        modifier           = Modifier.size(18.dp),
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

// ═════════════════════════════════════════════════════════════════════════════
// 2. My Passport Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun MyPassportScreen(
    user: UserProfile,
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

                if (user.passportStamps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "아직 방문한 도시가 없어요\n첫 여행을 떠나보세요!",
                            style     = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    PassportStampGrid(stamps = user.passportStamps)
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

@Composable
private fun PassportStampGrid(stamps: List<PassportStamp>) {
    val rotations = listOf(-4f, 3f, -2f, 5f, -3f, 2f)

    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.height((((stamps.size + 2) / 3) * 140).dp),
        userScrollEnabled     = false,
    ) {
        items(stamps, key = { it.id }) { stamp ->
            val rotation    = rotations[stamps.indexOf(stamp) % rotations.size]
            val accentColor = when (stamp.accentColor) {
                StampColor.PRIMARY   -> MaterialTheme.colorScheme.primary
                StampColor.SECONDARY -> MaterialTheme.colorScheme.secondary
                StampColor.ERROR     -> MaterialTheme.colorScheme.error
                StampColor.TERTIARY  -> MaterialTheme.colorScheme.tertiary
                StampColor.FIXED     -> MaterialTheme.colorScheme.primaryFixedDim
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .rotate(rotation)
                    .clip(CircleShape)
                    .border(3.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FlightLand, null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                    Text(stamp.cityCode, style = MaterialTheme.typography.titleLarge.copy(color = accentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Text(stamp.cityName, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), textAlign = TextAlign.Center)
                    Text(stamp.visitDate, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
                }
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
            query             = "",
            onQueryChange     = {},
            selectedCategory  = PlaceCategory.ALL,
            onCategoryChange  = {},
            places            = previewPlaces,
            cartCount         = 1,
            onPlaceClick      = {},
            onCartToggle      = {},
            onViewCartClick   = {},
            onBackClick       = {},
        )
    }
}

private val previewUser = UserProfile(
    id              = "u1",
    nickname        = "Skybound Explorer",
    profileImageUrl = null,
    homeTown        = "Seoul",
    totalTrips      = 6,
    passportStamps  = listOf(
        PassportStamp("s1", "TYO", "Tokyo, JP",  "OCT 2023", "flight_land", StampColor.PRIMARY),
        PassportStamp("s2", "PAR", "Paris, FR",  "MAY 2023", "train",       StampColor.SECONDARY),
        PassportStamp("s3", "SYD", "Sydney, AU", "JAN 2024", "sailing",     StampColor.ERROR),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MyPassportPreview() {
    SynctripTheme { MyPassportScreen(user = previewUser, onBackClick = {}) }
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
