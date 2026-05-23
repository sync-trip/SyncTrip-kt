package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.*
import com.synctrip.app.ui.theme.SynctripTheme
import kotlinx.coroutines.launch

/**
 * 홈 화면.
 * 추천 여행지 + 내 여행 밴드 목록을 보여주고,
 * 우측 상단 햄버거 메뉴로 사이드 드로어를 열 수 있다.
 */
@Composable
fun HomeScreen(
    recommendedContent: List<RecommendedContent>,
    myTripBands: List<TripBand>,
    selectedNavItem: BottomNavDestination,
    onNavItemSelected: (BottomNavDestination) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContentCardClick: (String) -> Unit,
    onTripBandClick: (String) -> Unit,
    onCreateTripClick: () -> Unit,
    onPassportClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnreadNotifications: Boolean = false,
) {
    val drawerState      = rememberDrawerState(DrawerValue.Closed)
    val scope            = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 드로어가 열린 상태에서 뒤로가기 → 앱 종료 대신 드로어 닫기
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // 로그아웃 확인 다이얼로그
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title            = { Text("로그아웃") },
            text             = { Text("정말 로그아웃 하시겠어요?") },
            confirmButton    = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("취소") }
            },
        )
    }

    // RTL로 감싸면 ModalNavigationDrawer가 오른쪽에서 열림
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            modifier      = modifier,
            drawerState   = drawerState,
            drawerContent = {
                // 드로어 내부는 LTR로 복구
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        HomeDrawerContent(
                            onPassportClick      = { scope.launch { drawerState.close() }; onPassportClick() },
                            onNotificationsClick = { scope.launch { drawerState.close() }; onNotificationsClick() },
                            onLogout             = { scope.launch { drawerState.close() }; showLogoutDialog = true },
                        )
                    }
                }
            },
        ) {
            // 메인 콘텐츠도 LTR 복구
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        HomeTopAppBar(
                            onMenuClick            = { scope.launch { drawerState.open() } },
                            onSearchClick          = onSearchClick,
                            onNotificationsClick   = onNotificationsClick,
                            hasUnreadNotifications = hasUnreadNotifications,
                        )
                    },
                    bottomBar = {
                        SyncTripBottomNav(
                            selectedDestination   = selectedNavItem,
                            onDestinationSelected = onNavItemSelected,
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick        = onCreateTripClick,
                            icon           = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text           = { Text("새 여행") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                            elevation      = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(innerPadding)
                            .padding(bottom = 16.dp),
                    ) {
                        Text(
                            text     = "SyncTrip",
                            style    = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        SectionHeader(
                            title    = "추천 여행지",
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        Spacer(Modifier.height(12.dp))

                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recommendedContent, key = { it.id }) { content ->
                                RecommendedCard(
                                    content  = content,
                                    onClick  = { onContentCardClick(content.id) },
                                    modifier = Modifier.width(256.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        SectionHeader(
                            title    = "내 여행 밴드",
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        Spacer(Modifier.height(12.dp))

                        if (myTripBands.isEmpty()) {
                            EmptyTripsPlaceholder(
                                onCreateTripClick = onCreateTripClick,
                                modifier          = Modifier.padding(horizontal = 20.dp),
                            )
                        } else {
                            LazyRow(
                                contentPadding        = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(myTripBands, key = { it.id }) { band ->
                                    TripTicketCard(
                                        band    = band,
                                        onClick = { onTripBandClick(band.id) },
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

// ─────────────────────────────────────────────────────────────────────────────
// 사이드 드로어 콘텐츠
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 홈 화면의 사이드 드로어 내용.
 * 여권, 알림 바로가기와 로그아웃 버튼을 포함한다.
 */
@Composable
private fun HomeDrawerContent(
    onPassportClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        // 드로어 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Column {
                Icon(
                    imageVector        = Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = "SyncTrip",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text  = "함께하는 여행 계획",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        DrawerMenuItem(
            icon    = Icons.Outlined.BookmarkBorder,
            label   = "내 여권",
            onClick = onPassportClick,
        )
        DrawerMenuItem(
            icon    = Icons.Outlined.Notifications,
            label   = "알림",
            onClick = onNotificationsClick,
        )

        Spacer(Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        DrawerMenuItem(
            icon    = Icons.Outlined.Logout,
            label   = "로그아웃",
            onClick = onLogout,
            tint    = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    NavigationDrawerItem(
        icon     = { Icon(icon, contentDescription = null, tint = tint) },
        label    = {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge.copy(color = tint),
            )
        },
        selected = false,
        onClick  = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TopAppBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    hasUnreadNotifications: Boolean,
) {
    TopAppBar(
        title = {
            Text(
                text  = "SyncTrip",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Outlined.Search, "검색", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = onNotificationsClick) {
                    Icon(Icons.Outlined.Notifications, "알림", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasUnreadNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, shape = RoundedCornerShape(50)),
                    )
                }
            }
            // 햄버거 메뉴 — 우측 끝에 배치
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Outlined.Menu, "메뉴", tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 내부 컴포넌트
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendedCard(
    content: RecommendedContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model              = content.imageUrl,
            contentDescription = content.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.4f to Color.Transparent,
                        1f   to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        )
        Text(
            text     = content.title,
            style    = MaterialTheme.typography.titleLarge.copy(
                color      = Color.White,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun EmptyTripsPlaceholder(
    onCreateTripClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.CardTravel,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "아직 진행 중인 여행이 없어요",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCreateTripClick) { Text("새 여행 만들기") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

private val previewBands = listOf(
    TripBand(
        id                = "1",
        destination       = "제주도",
        heroImageUrl      = "",
        startDate         = "Aug 12",
        endDate           = "Aug 18",
        status            = TripStatus.PLANNING,
        members           = listOf(TripMember("u1", "Alex", null), TripMember("u2", "Jamie", null)),
        completionPercent = 45,
    ),
    TripBand(
        id                = "2",
        destination       = "도쿄",
        heroImageUrl      = "",
        startDate         = "Sep 3",
        endDate           = "Sep 8",
        status            = TripStatus.PLANNING,
        members           = listOf(TripMember("u3", "Sam", null)),
        completionPercent = 20,
    ),
)

private val previewContent = listOf(
    RecommendedContent("c1", "교토의 자연과 산책", "", "NATURE", "Kyoto"),
    RecommendedContent("c2", "Canal City 하카타", "", "URBAN", "Fukuoka"),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    SynctripTheme {
        HomeScreen(
            recommendedContent   = previewContent,
            myTripBands          = previewBands,
            selectedNavItem      = BottomNavDestination.Home,
            onNavItemSelected    = {},
            onSearchClick        = {},
            onNotificationsClick = {},
            onContentCardClick   = {},
            onTripBandClick      = {},
            onCreateTripClick    = {},
            onPassportClick      = {},
            onLogout             = {},
            hasUnreadNotifications = true,
        )
    }
}
