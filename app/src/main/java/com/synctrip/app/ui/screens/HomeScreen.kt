package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.Intent
import android.net.Uri
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recommendedContent: List<RecommendedContent>,
    myTripBands: List<TripBand>,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContentCardClick: (String) -> Unit,
    onTripBandClick: (String) -> Unit,
    onCreateTripClick: () -> Unit,
    onPassportClick: () -> Unit,
    onJoinWithCode: (String) -> Unit,
    onLogout: () -> Unit,
    onWithdrawClick: () -> Unit = {},
    onAlarmSettingsClick: () -> Unit = {},
    onProfileEditClick: () -> Unit = {},
    onPastTripsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    hasUnreadNotifications: Boolean = false,
    userName: String = "",
    userProfileImageUrl: String? = null,
) {
    val context          = LocalContext.current
    val drawerState      = rememberDrawerState(DrawerValue.Closed)
    val scope            = rememberCoroutineScope()
    var showLogoutDialog     by remember { mutableStateOf(false) }
    var showWithdrawDialog   by remember { mutableStateOf(false) }
    var showJoinSheet        by remember { mutableStateOf(false) }
    var joinCodeInput        by remember { mutableStateOf("") }
    // 추천 여행지 카드 클릭 시 상세 BottomSheet에 표시할 항목
    var selectedRecommended  by remember { mutableStateOf<RecommendedContent?>(null) }

    // 계절별 섹션 서브타이틀
    val seasonSubtitle by remember {
        derivedStateOf {
            when (java.time.LocalDate.now().monthValue) {
                3, 4, 5   -> "봄에 떠나기 좋은 여행지를 골라봤어요 🌸"
                6, 7, 8   -> "시원하게 즐길 수 있는 여름 여행지예요 ☀️"
                9, 10, 11 -> "단풍과 함께할 가을 여행지예요 🍂"
                else      -> "따뜻하게 즐길 수 있는 겨울 여행지예요 ❄️"
            }
        }
    }

    // 가장 가까운 미래 여행 — D-day 배너에 사용
    val upcomingBand = remember(myTripBands) {
        myTripBands
            .filter { it.status != TripStatus.COMPLETED }
            .minByOrNull { band ->
                runCatching {
                    java.time.LocalDate.parse(band.startDate).toEpochDay()
                }.getOrDefault(Long.MAX_VALUE)
            }
    }

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

    // 회원탈퇴 확인 다이얼로그
    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title            = { Text("회원탈퇴") },
            text             = { Text("정말 탈퇴하시겠어요?") },
            confirmButton    = {
                TextButton(onClick = { showWithdrawDialog = false; onWithdrawClick() }) {
                    Text("탈퇴하기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showWithdrawDialog = false }) { Text("취소") }
            },
        )
    }

    // 추천 여행지 상세 팝업 다이얼로그
    if (selectedRecommended != null) {
        val dest = selectedRecommended!!
        val month = java.time.LocalDate.now().monthValue
        val seasonCopy = when (month) {
            3, 4, 5   -> "이번 봄에 가기 딱 좋은 여행지예요"
            6, 7, 8   -> "여름 휴가로 완벽한 선택이에요"
            9, 10, 11 -> "단풍 물드는 가을에 가면 더 아름다워요"
            else      -> "겨울에 따뜻하게 즐길 수 있는 여행지예요"
        }
        val regionFeature = when (dest.category) {
            "일본"            -> "🍜 미식  ♨️ 온천  🎌 문화"
            "동남아시아"      -> "🏖️ 리조트  🌴 열대 자연  🌃 야시장"
            "유럽"            -> "🏛️ 역사 유적  🎨 감성 거리  🍷 미식"
            "미주/오세아니아" -> "🗽 도시 라이프  🏔️ 자연 경관  🏄 액티비티"
            "중화권"          -> "🌃 야경  🥟 식도락  🛍️ 쇼핑"
            "국내"            -> "🚄 접근 편리  💸 가성비  📸 감성 여행"
            else              -> "✈️ 이국적인 풍경  🌏 다채로운 문화"
        }
        val landmarks = dest.subtitle.split(" · ").filter { it.isNotBlank() }

        Dialog(
            onDismissRequest = { selectedRecommended = null },
            properties       = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column {
                    // 여행지 이미지 (상단)
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        AsyncImage(
                            model              = dest.imageUrl,
                            contentDescription = dest.title,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
                            ),
                        )
                        // 지역 칩 (우측 상단)
                        Surface(
                            modifier  = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            shape     = RoundedCornerShape(50),
                            color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                        ) {
                            Text(
                                text     = dest.category,
                                style    = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        // 닫기 버튼 (좌측 상단)
                        IconButton(
                            onClick  = { selectedRecommended = null },
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Icon(Icons.Outlined.Close, "닫기", tint = Color.White)
                        }
                        // 제목 + 계절 문구 오버레이 (하단)
                        Column(
                            modifier            = Modifier.align(Alignment.BottomStart).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text  = dest.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text  = seasonCopy,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                ),
                            )
                        }
                    }

                    // 내용 영역
                    Column(
                        modifier            = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text  = regionFeature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (landmarks.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Text(
                                text  = "이런 곳이 있어요",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(landmarks.size) { idx ->
                                    // 랜드마크 칩 클릭 → 기기에 설치된 지도 앱에서 해당 장소 검색
                                    Surface(
                                        shape   = RoundedCornerShape(50),
                                        color   = MaterialTheme.colorScheme.secondaryContainer,
                                        onClick = {
                                            val query = "${landmarks[idx]}, ${dest.title}"
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
                                            )
                                        },
                                    ) {
                                        Text(
                                            text     = landmarks[idx],
                                            style    = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick  = { selectedRecommended = null; onCreateTripClick() },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("이 여행지로 계획 세우기")
                        }
                    }
                }
            }
        }
    }

    // 초대 코드 입력 BottomSheet
    if (showJoinSheet) {
        ModalBottomSheet(
            onDismissRequest = { showJoinSheet = false; joinCodeInput = "" },
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text  = "초대 코드로 참여",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value         = joinCodeInput,
                    onValueChange = { if (it.length <= 8) joinCodeInput = it.uppercase() },
                    label         = { Text("초대 코드") },
                    placeholder   = { Text("8자리 코드 입력") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick  = {
                        onJoinWithCode(joinCodeInput)
                        showJoinSheet = false
                        joinCodeInput = ""
                    },
                    enabled  = joinCodeInput.length == 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                ) { Text("참여하기") }
            }
        }
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
                            userName              = userName,
                            userProfileImageUrl   = userProfileImageUrl,
                            upcomingBand          = upcomingBand,
                            onClose               = { scope.launch { drawerState.close() } },
                            onBandClick           = { bandId -> scope.launch { drawerState.close() }; onTripBandClick(bandId) },
                            onPassportClick       = { scope.launch { drawerState.close() }; onPassportClick() },
                            onNotificationsClick  = { scope.launch { drawerState.close() }; onNotificationsClick() },
                            onJoinWithCode        = { scope.launch { drawerState.close() }; showJoinSheet = true },
                            onAlarmSettingsClick  = { scope.launch { drawerState.close() }; onAlarmSettingsClick() },
                            onProfileEditClick    = { scope.launch { drawerState.close() }; onProfileEditClick() },
                            onPastTripsClick      = { scope.launch { drawerState.close() }; onPastTripsClick() },
                            onLogout              = { scope.launch { drawerState.close() }; showLogoutDialog = true },
                            onWithdraw            = { scope.launch { drawerState.close() }; showWithdrawDialog = true },
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
                    snackbarHost        = { SnackbarHost(snackbarHostState) },
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
                        Spacer(Modifier.height(8.dp))

                        SectionHeader(
                            title    = "추천 여행지",
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Text(
                            text     = seasonSubtitle,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recommendedContent, key = { it.id }) { content ->
                                RecommendedCard(
                                    content  = content,
                                    onClick  = { selectedRecommended = content },
                                    modifier = Modifier.width(256.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        SectionHeader(
                            title    = "내 여행 밴드",
                            modifier = Modifier.padding(horizontal = 20.dp),
                            action   = {
                                TextButton(onClick = { showJoinSheet = true }) {
                                    Text("코드로 참여")
                                }
                            },
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
 * 홈 화면 사이드 드로어.
 * 프로필 섹션 + 가장 가까운 여행 D-day 배너 + 퀵 액션 + 로그아웃으로 구성.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDrawerContent(
    userName: String,
    userProfileImageUrl: String?,
    upcomingBand: TripBand?,
    onClose: () -> Unit,
    onBandClick: (bandId: String) -> Unit,
    onPassportClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onJoinWithCode: () -> Unit,
    onAlarmSettingsClick: () -> Unit,
    onProfileEditClick: () -> Unit,
    onPastTripsClick: () -> Unit,
    onLogout: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxHeight()) {

        // ── 상단 바 — 닫기 + 알림 ────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, "닫기", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Outlined.Notifications, "알림", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 프로필 섹션 ───────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = userName.ifBlank { "SyncTrip 유저" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Box(
                modifier         = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (userProfileImageUrl != null) {
                    AsyncImage(
                        model              = userProfileImageUrl,
                        contentDescription = "프로필",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text  = userName.firstOrNull()?.uppercase() ?: "S",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── D-day 배너 — 가장 가까운 여행 ────────────────────────────────
        if (upcomingBand != null) {
            val dDayText = remember(upcomingBand.startDate) {
                runCatching {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(),
                        java.time.LocalDate.parse(upcomingBand.startDate),
                    )
                    when {
                        days > 0L  -> "D-$days"
                        days == 0L -> "D-DAY"
                        else       -> "여행 중"
                    }
                }.getOrDefault("")
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color    = MaterialTheme.colorScheme.primary,
                onClick  = { onBandClick(upcomingBand.id) },
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.FlightTakeoff, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(
                            text  = upcomingBand.destination,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    if (dDayText.isNotEmpty()) {
                        Text(
                            text  = dDayText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 퀵 액션 (2×2 그리드) ─────────────────────────────────────────
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerQuickItem(
                    icon     = Icons.Outlined.CardTravel,
                    label    = "내 여권",
                    modifier = Modifier.weight(1f),
                    onClick  = onPassportClick,
                )
                DrawerQuickItem(
                    icon     = Icons.Outlined.PersonAdd,
                    label    = "코드 참여",
                    modifier = Modifier.weight(1f),
                    onClick  = onJoinWithCode,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerQuickItem(
                    icon     = Icons.Outlined.NotificationsActive,
                    label    = "알림 설정",
                    modifier = Modifier.weight(1f),
                    onClick  = onAlarmSettingsClick,
                )
                DrawerQuickItem(
                    icon     = Icons.Outlined.ManageAccounts,
                    label    = "프로필 편집",
                    modifier = Modifier.weight(1f),
                    onClick  = onProfileEditClick,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        NavigationDrawerItem(
            icon     = { Icon(Icons.Outlined.History, null) },
            label    = { Text("지난 여행") },
            selected = false,
            onClick  = onPastTripsClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        NavigationDrawerItem(
            icon     = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            label    = {
                Text(
                    "로그아웃",
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error),
                )
            },
            selected = false,
            onClick  = onLogout,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        TextButton(
            onClick  = onWithdraw,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text  = "회원탈퇴",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerQuickItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick  = onClick,
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model              = content.imageUrl,
            contentDescription = content.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        // 하단 그라디언트
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.35f to Color.Transparent,
                        1f   to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
        // 지역 배지 (우측 상단)
        Surface(
            modifier  = Modifier.align(Alignment.TopEnd).padding(10.dp),
            shape     = RoundedCornerShape(50),
            color     = Color.Black.copy(alpha = 0.45f),
        ) {
            Text(
                text     = content.category,
                style    = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        // 하단 텍스트 영역
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = content.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (content.subtitle.isNotBlank()) {
                Text(
                    text  = content.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f),
                    ),
                    maxLines = 1,
                )
            }
        }
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
        name              = "여름 제주 여행",
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
        name              = "도쿄 겨울 여행",
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

// ─────────────────────────────────────────────────────────────────────────────
// 지난 여행 기록 화면 (USR-025)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 지난 여행 기록 화면.
 * DONE 상태의 밴드 목록을 표시하며, 각 카드 클릭 시 해당 밴드 허브로 이동한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastTripsScreen(
    pastBands: List<BandResponse>,
    onBandClick: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "지난 여행",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (pastBands.isEmpty()) {
            // 빈 상태
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text  = "완료된 여행이 없어요",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Text(
                        text  = "여행을 마치면 여기에 기록이 남아요",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pastBands, key = { it.id }) { band ->
                    PastTripCard(
                        band    = band,
                        onClick = { onBandClick(band.id.toString()) },
                    )
                }
            }
        }
    }
}

/** 지난 여행 목록 카드 — 여행지·기간·인원수 표시 */
@Composable
private fun PastTripCard(
    band: BandResponse,
    onClick: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        onClick   = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // 썸네일 (없으면 그라디언트 플레이스홀더)
            Box(
                modifier         = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (band.thumbnailUrl != null) {
                    AsyncImage(
                        model              = band.thumbnailUrl,
                        contentDescription = band.destination,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.FlightTakeoff,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // 여행 정보
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text     = band.destination,
                    style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text  = "${band.startDate} ~ ${band.endDate}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.People,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text  = "${band.memberCount}명",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            // 완료 뱃지
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text     = "완료",
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    SynctripTheme {
        HomeScreen(
            recommendedContent     = previewContent,
            myTripBands            = previewBands,
            onSearchClick          = {},
            onNotificationsClick   = {},
            onContentCardClick     = {},
            onTripBandClick        = {},
            onCreateTripClick      = {},
            onPassportClick        = {},
            onJoinWithCode         = {},
            onLogout               = {},
            hasUnreadNotifications = true,
            userName               = "구민우",
            userProfileImageUrl    = null,
        )
    }
}
