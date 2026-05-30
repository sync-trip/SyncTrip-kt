package com.synctrip.app.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.synctrip.app.SyncTripApplication
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.AuthRepository
import com.synctrip.app.data.repository.BandRepository
import com.synctrip.app.network.ApiClient
import com.synctrip.app.ui.screens.*
import com.synctrip.app.ui.viewmodel.AlbumViewModel
import com.synctrip.app.ui.viewmodel.AuthUiState
import com.synctrip.app.ui.viewmodel.AuthViewModel
import com.synctrip.app.ui.viewmodel.BandViewModel
import com.synctrip.app.ui.viewmodel.NotificationViewModel
import com.synctrip.app.ui.viewmodel.ScheduleViewModel
import com.synctrip.app.ui.viewmodel.VoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun SyncTripNavGraph(
    pendingDeepLinkCode: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingNotificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scope         = rememberCoroutineScope()

    // 앱 실행 중 알림 탭 → 현재 화면이 splash가 아닐 때 즉시 이동
    // 앱 종료 상태에서 탭한 경우는 splash 완료 시점에 처리
    val navBackStack by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingNotificationRoute) {
        val route = pendingNotificationRoute ?: return@LaunchedEffect
        val currentRoute = navBackStack?.destination?.route
        // null: NavHost 초기화 전 / "splash": 종료 상태 탭 케이스 → 둘 다 skip, splash에서 처리
        if (currentRoute != null && currentRoute != "splash") {
            navController.navigate(route)
            onNotificationRouteConsumed()
        }
    }

    // 딥링크 초대 코드 상태 — NavHost 밖에 선언해야 어느 화면에서도 다이얼로그 표시 가능
    var pendingJoinCode by remember { mutableStateOf<String?>(null) }
    var joinError       by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingDeepLinkCode) {
        if (!pendingDeepLinkCode.isNullOrEmpty()) {
            pendingJoinCode = pendingDeepLinkCode
            joinError = null
            onDeepLinkConsumed()
        }
    }

    if (pendingJoinCode != null) {
        AlertDialog(
            onDismissRequest = { pendingJoinCode = null; joinError = null },
            title            = { Text("초대 링크로 참여") },
            text             = {
                if (joinError != null)
                    Text("초대 코드: ${pendingJoinCode!!}\n이 여행 방에 참여할까요?\n\n⚠️ $joinError")
                else
                    Text("초대 코드: ${pendingJoinCode!!}\n이 여행 방에 참여할까요?")
            },
            confirmButton    = {
                TextButton(onClick = {
                    val code = pendingJoinCode!!
                    scope.launch {
                        runCatching { BandRepository.joinBand(code) }
                            .onSuccess { band ->
                                pendingJoinCode = null
                                joinError = null
                                navController.navigate("tripLobby/${band.id}")
                            }
                            .onFailure { e ->
                                joinError = e.message ?: "참여 실패"
                            }
                    }
                }) { Text("참여하기") }
            },
            dismissButton    = {
                TextButton(onClick = { pendingJoinCode = null; joinError = null }) { Text("취소") }
            },
        )
    }

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            val context = LocalContext.current
            // DataStore에서 저장된 토큰 복구 결과 (null = 아직 로딩 중)
            var tokenDest    by remember { mutableStateOf<String?>(null) }
            var splashDone   by remember { mutableStateOf(false) }

            // 앱 시작 시 DataStore 토큰 확인 → ApiClient 메모리에 복구
            LaunchedEffect(Unit) {
                val access  = TokenDataStore.accessTokenFlow(context).first()
                val refresh = TokenDataStore.refreshTokenFlow(context).first()
                if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                    ApiClient.accessToken  = access
                    ApiClient.refreshToken = refresh
                    // 자동로그인 시에도 FCM 토큰을 서버에 등록 — onNewToken은 토큰 갱신 시에만 호출되므로
                    AuthRepository.ensureFcmTokenRegistered()
                    tokenDest = "home"
                } else {
                    tokenDest = "login"
                }
            }

            // 스플래시 애니메이션 완료 + 토큰 확인 모두 끝나면 이동
            LaunchedEffect(splashDone, tokenDest) {
                if (splashDone && tokenDest != null) {
                    navController.navigate(tokenDest!!) {
                        popUpTo("splash") { inclusive = true }
                    }
                    // 앱 종료 상태에서 알림 탭 → 로그인 상태(home)일 때만 추가 이동
                    if (tokenDest == "home" && !pendingNotificationRoute.isNullOrEmpty()) {
                        navController.navigate(pendingNotificationRoute)
                        onNotificationRouteConsumed()
                    }
                }
            }

            SplashScreen(onSplashComplete = { splashDone = true })
        }

        composable("login") {
            val context        = LocalContext.current
            val authViewModel  = viewModel<AuthViewModel>()
            val uiState        by authViewModel.uiState.collectAsState()
            val snackbarState  = remember { SnackbarHostState() }

            // 로그인 성공 → Home 이동
            LaunchedEffect(uiState) {
                when (val state = uiState) {
                    is AuthUiState.Success -> {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                        authViewModel.resetState()
                    }
                    is AuthUiState.Error -> {
                        snackbarState.showSnackbar(state.message)
                        authViewModel.resetState()
                    }
                    else -> {}
                }
            }

            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                Box {
                    LoginScreen(
                        onKakaoLogin  = { authViewModel.kakaoLogin(context) },
                        onGoogleLogin = { authViewModel.googleLogin(context) },
                        onEmailLogin  = {
                            // 개발용: 인증 없이 홈 진입 (발표 전 제거)
                            navController.navigate("home") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                    )
                    // 로그인 진행 중 전체화면 로딩 오버레이
                    if (uiState is AuthUiState.Loading) {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlaneLoadingIndicator()
                        }
                    }
                }
            }
        }

        composable("home") {
            val context         = LocalContext.current
            val bandViewModel   = viewModel<BandViewModel>()
            val authViewModel   = viewModel<AuthViewModel>()
            val bandUiState     by bandViewModel.uiState.collectAsState()
            val snackbarState   = remember { SnackbarHostState() }
            var isRefreshing    by remember { mutableStateOf(false) }

            // 화면 진입 시 밴드 목록 + 유저 프로필 + 추천 여행지 로드
            LaunchedEffect(Unit) {
                bandViewModel.loadBands()
                bandViewModel.loadMyProfile()
                bandViewModel.loadRecommendedDestinations()
            }

            // 로딩 완료 시 새로고침 인디케이터 해제
            LaunchedEffect(bandUiState.isLoading) {
                if (!bandUiState.isLoading) isRefreshing = false
            }

            // BottomSheet 수동 코드 입력 에러 → 스낵바 표시
            LaunchedEffect(bandUiState.error) {
                bandUiState.error?.let { err ->
                    snackbarState.showSnackbar(err)
                    bandViewModel.clearError()
                }
            }

            HomeScreen(
                recommendedContent   = bandUiState.recommendedDestinations,
                myTripBands          = bandUiState.bands.reversed().map { it.toTripBand() },
                userName             = bandUiState.userProfile?.name ?: "",
                userProfileImageUrl  = bandUiState.userProfile?.profileImageUrl,
                snackbarHostState    = snackbarState,
                isRefreshing         = isRefreshing,
                onRefresh            = {
                    isRefreshing = true
                    bandViewModel.loadBands()
                    bandViewModel.loadMyProfile()
                    bandViewModel.loadRecommendedDestinations()
                },
                onSearchClick        = {},
                onNotificationsClick = { navController.navigate("notifications") },
                onContentCardClick   = {},
                onTripBandClick      = { bandId -> navController.navigate("tripLobby/$bandId") },
                onCreateTripClick    = { navController.navigate("createTrip") },
                onPassportClick      = { navController.navigate("passport") },
                onAlarmSettingsClick = { navController.navigate("notificationSettings") },
                onProfileEditClick   = { navController.navigate("profileEdit") },
                onPastTripsClick     = { navController.navigate("pastTrips") },
                onJoinWithCode       = { code ->
                    bandViewModel.joinBand(code) { band ->
                        bandViewModel.loadBands()
                        navController.navigate("tripLobby/${band.id}")
                    }
                },
                // 로그아웃: 토큰 삭제 후 로그인 화면으로 이동 (백 스택 초기화)
                onLogout             = {
                    authViewModel.logout(context)
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onWithdrawClick      = {
                    authViewModel.withdraw(
                        context   = context,
                        onSuccess = {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onFailure = { msg ->
                            scope.launch { snackbarState.showSnackbar(msg) }
                        },
                    )
                },
            )
        }

        composable("createTrip") {
            val bandViewModel       = viewModel<BandViewModel>()
            val bandUiState         by bandViewModel.uiState.collectAsState()
            val snackbarState       = remember { SnackbarHostState() }
            // 검색 API 호출에 사용할 코루틴 스코프 (Composable 수명과 함께)
            val scope               = rememberCoroutineScope()

            var destinations        by remember { mutableStateOf<List<DestinationResponse>>(emptyList()) }
            var selectedDestination by remember { mutableStateOf<DestinationResponse?>(null) }
            var destinationQuery    by remember { mutableStateOf("") }
            var bandName               by remember { mutableStateOf("") }
            var startDate              by remember { mutableStateOf("") }
            var endDate                by remember { mutableStateOf("") }
            var travelStyle            by remember { mutableStateOf(BandTravelStyle.RELAXED) }
            var selectedAccommodation  by remember { mutableStateOf<ApiPlaceSearchResult?>(null) }
            var accommodationQuery     by remember { mutableStateOf("") }
            var accommodationResults   by remember { mutableStateOf<List<ApiPlaceSearchResult>>(emptyList()) }
            var isAccommodationLoading by remember { mutableStateOf(false) }

            // 화면 진입 시 인기 여행지 로드
            LaunchedEffect(Unit) {
                runCatching { ApiClient.api.getPopularDestinations() }
                    .onSuccess { destinations = it }
            }

            // 밴드 생성 에러 스낵바
            LaunchedEffect(bandUiState.error) {
                bandUiState.error?.let {
                    snackbarState.showSnackbar(it)
                    bandViewModel.clearError()
                }
            }

            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                CreateTripScreen(
                    destinations             = destinations,
                    selectedDestination      = selectedDestination,
                    destinationQuery         = destinationQuery,
                    onDestinationQueryChange = { query ->
                        destinationQuery = query
                        selectedDestination = null
                        // 검색어 지우면(X 버튼 또는 탭 전환) 인기 목록으로 복구
                        if (query.isEmpty()) {
                            scope.launch {
                                runCatching { ApiClient.api.getPopularDestinations() }
                                    .onSuccess { destinations = it }
                            }
                        }
                    },
                    // 키보드 검색 버튼 클릭 시에만 API 호출 — 구 앱과 동일 (비용 절감)
                    onSearchDestination = { query ->
                        scope.launch {
                            try {
                                val results = ApiClient.api.searchDestinations(query)
                                destinations = results
                                if (results.isEmpty()) snackbarState.showSnackbar("검색 결과가 없어요.")
                            } catch (e: Exception) {
                                snackbarState.showSnackbar("검색 실패. 다시 시도해주세요.")
                            }
                        }
                    },
                    onDestinationSelect = { dest ->
                        selectedDestination = dest
                        // destinationQuery는 건드리지 않음 — 검색창에 도시명 올라가는 현상 방지
                        bandName = "${dest.name} 여행"
                    },
                    bandName            = bandName,
                    onBandNameChange    = { bandName = it },
                    startDate           = startDate,
                    onStartDateChange   = { startDate = it },
                    endDate             = endDate,
                    onEndDateChange     = { endDate = it },
                    travelStyle                  = travelStyle,
                    onTravelStyleChange          = { travelStyle = it },
                    destinationLat               = selectedDestination?.lat ?: 37.5665,
                    destinationLng               = selectedDestination?.lng ?: 126.9780,
                    accommodationQuery           = accommodationQuery,
                    onAccommodationQueryChange   = { accommodationQuery = it },
                    onAccommodationSearch        = { keyword ->
                        val dest = selectedDestination ?: return@CreateTripScreen
                        scope.launch {
                            isAccommodationLoading = true
                            runCatching {
                                ApiClient.api.searchAccommodations(dest.lat, dest.lng, keyword.ifBlank { null })
                            }.onSuccess { accommodationResults = it }
                                .onFailure { snackbarState.showSnackbar("숙소 검색 실패. 다시 시도해주세요.") }
                            isAccommodationLoading = false
                        }
                    },
                    accommodationResults         = accommodationResults,
                    isAccommodationLoading       = isAccommodationLoading,
                    selectedAccommodation        = selectedAccommodation,
                    onAccommodationSelect        = { acc ->
                        selectedAccommodation = if (selectedAccommodation?.externalId == acc.externalId) null else acc
                    },
                    isLoading                    = bandUiState.isLoading,
                    onCreateTrip                 = {
                        val dest = selectedDestination ?: return@CreateTripScreen
                        bandViewModel.createBand(
                            BandCreateRequest(
                                name              = bandName.ifBlank { "${dest.name} 여행" },
                                startDate         = startDate,
                                endDate           = endDate,
                                destination       = dest.name,
                                destinationLat    = dest.lat,
                                destinationLng    = dest.lng,
                                countryCode       = dest.countryCode,
                                overseas          = dest.overseas,
                                travelStyle       = travelStyle,
                                thumbnailUrl      = dest.thumbnailUrl,
                                accommodationName = selectedAccommodation?.name,
                                accommodationLat  = selectedAccommodation?.latitude,
                                accommodationLng  = selectedAccommodation?.longitude,
                            )
                        ) { newBand ->
                            navController.navigate("tripLobby/${newBand.id}") {
                                popUpTo("createTrip") { inclusive = true }
                            }
                        }
                    },
                    onSkipAccommodationAndCreate = {
                        val dest = selectedDestination ?: return@CreateTripScreen
                        bandViewModel.createBand(
                            BandCreateRequest(
                                name           = bandName.ifBlank { "${dest.name} 여행" },
                                startDate      = startDate,
                                endDate        = endDate,
                                destination    = dest.name,
                                destinationLat = dest.lat,
                                destinationLng = dest.lng,
                                countryCode    = dest.countryCode,
                                overseas       = dest.overseas,
                                travelStyle    = travelStyle,
                                thumbnailUrl   = dest.thumbnailUrl,
                            )
                        ) { newBand ->
                            navController.navigate("tripLobby/${newBand.id}") {
                                popUpTo("createTrip") { inclusive = true }
                            }
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }
        }

        // bandId 없이 진입하는 경우 (로비에서 상태 전환 시) — 완료 후 홈으로
        composable("aiLoading") {
            AiLoadingSimulated(
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
            )
        }

        // 투표 완료 → 허브로 돌아감. 허브의 scheduleReadyEvent가 일정 탭 자동 전환 처리
        composable("aiLoading/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId") ?: ""
            AiLoadingSimulated(
                onComplete = {
                    // voteResults가 중간에 있을 수 있으므로 tripLobby까지 팝
                    navController.popBackStack("tripLobby/$bandId", inclusive = false)
                },
            )
        }

        // ──────────────────────────────────────────────────────────────────────
        // 밴드 방 허브 — 하단 탭(밴드·일정·정산·사진)을 통해 모든 기능 접근
        // ──────────────────────────────────────────────────────────────────────
        composable("tripLobby/{bandId}") { backStackEntry ->
            val context           = LocalContext.current
            val bandViewModel     = viewModel<BandViewModel>()
            val scheduleViewModel = viewModel<ScheduleViewModel>()
            val albumViewModel    = viewModel<AlbumViewModel>()
            val bandUiState       by bandViewModel.uiState.collectAsState()
            val scheduleUiState   by scheduleViewModel.uiState.collectAsState()
            val albumUiState      by albumViewModel.uiState.collectAsState()
            val snackbarState     = remember { SnackbarHostState() }

            val bandIdLong = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: 0L
            var currentUserId by remember { mutableStateOf(0L) }

            // 허브 탭 선택 상태 — NavGraph에서 관리해야 scheduleReadyEvent와 연동 가능
            var selectedTab by remember { mutableStateOf(BandHubTab.BAND) }
            var isRefreshing by remember { mutableStateOf(false) }
            // 이전 상태 추적 — GENERATING→TRAVELLING 전환 시에만 탭 자동 전환
            var prevBandStatus by remember { mutableStateOf<BandStatus?>(null) }

            // 진입 시 기본 데이터 로드
            LaunchedEffect(bandIdLong) {
                currentUserId = TokenDataStore.userIdFlow(context).first() ?: 0L
                bandViewModel.loadBands()
                bandViewModel.loadMembers(bandIdLong)
                bandViewModel.loadPicks(bandIdLong)
            }

            // 15초마다 자동 갱신 — 다른 멤버 입장·장바구니 변경·Ready 상태 변화를 polling으로 반영
            // Compose Navigation 특성상 서브 화면 이동 후 돌아와도 LaunchedEffect가 재실행되지 않아 필요
            LaunchedEffect("poll_$bandIdLong") {
                while (true) {
                    delay(15_000L)
                    bandViewModel.loadBands()
                    bandViewModel.loadMembers(bandIdLong)
                    bandViewModel.loadPicks(bandIdLong)
                }
            }

            val band = bandUiState.bands.find { it.id == bandIdLong }

            // 밴드 상태 변화 감지 — GENERATING 폴링 시작, GENERATING→TRAVELLING 전환 시에만 일정 탭 전환
            // 재진입 시 이미 TRAVELLING/DONE인 경우엔 탭 자동 전환 안 함
            LaunchedEffect(band?.status) {
                val current = band?.status
                when (current) {
                    BandStatus.GENERATING -> bandViewModel.startGeneratingPoll(bandIdLong)
                    BandStatus.TRAVELLING, BandStatus.DONE -> {
                        if (prevBandStatus == BandStatus.GENERATING && selectedTab == BandHubTab.BAND) {
                            selectedTab = BandHubTab.SCHEDULE
                        }
                    }
                    else -> {}
                }
                prevBandStatus = current
            }

            // scheduleReadyEvent 수신 → 일정 탭 전환 + 데이터 즉시 로드
            // SharedFlow(replay=0)이므로 허브 재진입 시 과거 이벤트 재수신 없음
            LaunchedEffect(Unit) {
                bandViewModel.scheduleReadyEvent.collect {
                    selectedTab = BandHubTab.SCHEDULE
                    scheduleViewModel.loadSchedule(bandIdLong)
                }
            }

            // 탭 전환 시 필요 데이터 지연 로드
            LaunchedEffect(selectedTab) {
                when (selectedTab) {
                    BandHubTab.SCHEDULE   -> {
                        if (scheduleUiState.schedule == null) {
                            scheduleViewModel.loadSchedule(bandIdLong)
                        }
                    }
                    BandHubTab.SETTLEMENT -> {
                        // settlement null 여부 무관하게 항상 재조회 — 다른 멤버 지출 반영
                        bandViewModel.loadSettlement(bandIdLong, currentUserId)
                        bandViewModel.loadExpenses(bandIdLong)
                    }
                    BandHubTab.PHOTO -> {
                        // 사진 탭 최초 진입 시 피드 + 지도 핀 로드
                        if (albumUiState.photos.isEmpty() && !albumUiState.isLoading) {
                            albumViewModel.loadAlbum(bandIdLong)
                        }
                    }
                    else -> {}
                }
            }

            // 에러 스낵바
            LaunchedEffect(bandUiState.error) {
                bandUiState.error?.let {
                    snackbarState.showSnackbar(it)
                    bandViewModel.clearError()
                }
            }

            // 현재 탭 로딩 완료 시 새로고침 인디케이터 해제
            val currentTabLoading = when (selectedTab) {
                BandHubTab.BAND       -> bandUiState.isLoading
                BandHubTab.SCHEDULE   -> scheduleUiState.isLoading
                BandHubTab.SETTLEMENT -> bandUiState.isExpensesLoading
                BandHubTab.PHOTO      -> albumUiState.isLoading
            }
            LaunchedEffect(currentTabLoading) {
                if (!currentTabLoading) isRefreshing = false
            }

            // FCM 수신 시 즉시 갱신 — 멤버 합류·장바구니 변경 등 이벤트를 폴링 없이 반영
            val application = context.applicationContext as SyncTripApplication
            LaunchedEffect(Unit) {
                application.bandRefreshFlow.collect { refreshedBandId ->
                    if (refreshedBandId == bandIdLong) {
                        bandViewModel.loadMembers(bandIdLong)
                        bandViewModel.loadPicks(bandIdLong)
                    }
                }
            }

            // 허브 이탈 시 폴링 중단
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { bandViewModel.stopGeneratingPoll() }
            }

            if (band == null) {
                // 밴드 로딩 전 임시 스피너
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlaneLoadingIndicator()
                }
            } else {
                TripBandHubScreen(
                    band              = band,
                    members           = bandUiState.members,
                    picks             = bandUiState.picks,
                    currentUserId     = currentUserId,
                    isBandLoading     = bandUiState.isLoading,
                    isRefreshing      = isRefreshing,
                    onRefresh         = {
                        isRefreshing = true
                        when (selectedTab) {
                            BandHubTab.BAND -> {
                                bandViewModel.loadBands()
                                bandViewModel.loadMembers(bandIdLong)
                                bandViewModel.loadPicks(bandIdLong)
                            }
                            BandHubTab.SCHEDULE   -> scheduleViewModel.loadSchedule(bandIdLong)
                            BandHubTab.SETTLEMENT -> {
                                bandViewModel.loadSettlement(bandIdLong)
                                bandViewModel.loadExpenses(bandIdLong)
                            }
                            BandHubTab.PHOTO      -> albumViewModel.loadAlbum(bandIdLong)
                        }
                    },
                    schedule          = scheduleUiState.schedule,
                    altOptions        = scheduleUiState.altOptions,
                    isScheduleLoading = scheduleUiState.isLoading,
                    isEditing         = scheduleUiState.isEditing,
                    settlement        = bandUiState.settlement,
                    expenses          = bandUiState.expenses,
                    isExpensesLoading = bandUiState.isExpensesLoading,
                    selectedTab       = selectedTab,
                    onTabSelected     = { tab -> selectedTab = tab },
                    snackbarHostState = snackbarState,
                    onBackClick       = { navController.popBackStack() },
                    onReadyClick      = { bandViewModel.setReady(bandIdLong) },
                    onInviteClick     = { navController.navigate("invite/$bandIdLong") },
                    onAdvanceStatus   = {
                        bandViewModel.advanceBandStatus(bandIdLong) { transition ->
                            when (transition.currentStatus) {
                                BandStatus.VOTING     -> navController.navigate("blindVoting/$bandIdLong")
                                BandStatus.GENERATING -> navController.navigate("aiLoading/$bandIdLong")
                                // TRAVELLING→DONE: 별도 화면 이동 없이 밴드 상태 갱신으로 UI 자동 반영
                                BandStatus.DONE       -> bandViewModel.loadBands()
                                else                  -> {}
                            }
                        }
                    },
                    onGoToPlaceSearch = { navController.navigate("placeSearch/$bandIdLong") },
                    onGoToVoting      = { navController.navigate("blindVoting/$bandIdLong") },
                    onLoadAlts        = { scheduleViewModel.loadAlts(bandIdLong) },
                    onSwapSlot        = { sid, pid -> scheduleViewModel.swapSlot(bandIdLong, sid, pid) },
                    onStartEditing    = { scheduleViewModel.startEditing(bandIdLong) },
                    onFinishEditing   = { scheduleViewModel.finishEditing(bandIdLong) },
                    planBResults      = scheduleUiState.planBResults,
                    isPlanBLoading    = scheduleUiState.isPlanBLoading,
                    onRequestPlanB    = { pid -> scheduleViewModel.loadPlanB(bandIdLong, pid) },
                    onExecutePlanBSwap = { sid, pid -> scheduleViewModel.executePlanBSwap(bandIdLong, sid, pid) },
                    onSettleClick     = {},
                    onAddExpense      = { itemName, amount, currency, payerId, memberIds ->
                        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        bandViewModel.createExpense(
                            bandIdLong,
                            ExpenseCreateRequest(
                                itemName  = itemName,
                                amount    = amount,
                                currency  = currency,
                                payerId   = payerId,
                                paidAt    = now,
                                memberIds = memberIds,
                            ),
                            currentUserId,
                        )
                    },
                    onDeleteExpense   = { expenseId -> bandViewModel.deleteExpense(bandIdLong, expenseId, currentUserId) },
                    // 앨범 탭 연결
                    albumPhotos       = albumUiState.photos,
                    albumMapPins      = albumUiState.mapPins,
                    isAlbumLoading    = albumUiState.isLoading,
                    isAlbumUploading  = albumUiState.isUploading,
                    onUploadAlbumPhoto = { photoData, caption, lat, lng, takenAt ->
                        albumViewModel.uploadPhoto(bandIdLong, photoData, caption, lat, lng, takenAt)
                    },
                    onDeleteAlbumPhoto = { photoId ->
                        albumViewModel.deletePhoto(bandIdLong, photoId)
                    },
                    onEditAccommodationClick = {
                        val dest     = bandUiState.bands.find { it.id == bandIdLong }
                        val lat      = dest?.destinationLat ?: 37.5665
                        val lng      = dest?.destinationLng ?: 126.9780
                        val destName = java.net.URLEncoder.encode(dest?.destination ?: "", "UTF-8")
                        navController.navigate("accommodationSearch/$bandIdLong/$lat/$lng/$destName")
                    },
                    onDeleteBand      = {
                        bandViewModel.deleteBand(bandIdLong) {
                            navController.navigate("home") {
                                popUpTo("tripLobby/$bandIdLong") { inclusive = true }
                            }
                        }
                    },
                )
            }
        }

        // 숙소 검색 화면 — 로비에서 방장이 숙소 수정 시 진입
        composable("accommodationSearch/{bandId}/{lat}/{lng}/{destinationName}") { backStackEntry ->
            val bandIdLong      = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val lat             = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 37.5665
            val lng             = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull() ?: 126.9780
            val destinationName = backStackEntry.arguments?.getString("destinationName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
            val bandViewModel: BandViewModel = viewModel()

            AccommodationSearchScreen(
                destinationLat  = lat,
                destinationLng  = lng,
                destinationName = destinationName,
                onBack         = { navController.popBackStack() },
                onSave         = { selectedPlace ->
                    bandViewModel.updateAccommodation(
                        bandId = bandIdLong,
                        name   = selectedPlace?.name,
                        lat    = selectedPlace?.latitude,
                        lng    = selectedPlace?.longitude,
                    )
                    navController.popBackStack()
                },
            )
        }

        // 초대 화면 — +초대 버튼에서 진입, 코드 복사/공유 제공
        composable("invite/{bandId}") { backStackEntry ->
            val bandId        = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val context       = LocalContext.current
            val bandViewModel: BandViewModel = viewModel()
            val uiState       by bandViewModel.uiState.collectAsState()

            // 진입 시 밴드 정보·멤버·초대 코드 로드
            LaunchedEffect(bandId) {
                bandViewModel.loadBands()
                bandViewModel.loadMembers(bandId)
                bandViewModel.getInviteCode(bandId)
            }

            val band = uiState.bands.find { it.id == bandId }

            InviteScreen(
                bandName    = band?.name ?: "",
                memberCount = uiState.members.count { it.role == BandRole.MEMBER },
                inviteCode  = uiState.inviteCode,
                onBackClick = { navController.popBackStack() },
                onCopyCode  = { code ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", code))
                    Toast.makeText(context, "초대 코드를 복사했어요", Toast.LENGTH_SHORT).show()
                },
                onShareLink = { code, link ->
                    val shareText = if (!link.isNullOrEmpty())
                        "SyncTrip에서 같이 여행 계획해요! 👇\n$link"
                    else
                        "SyncTrip 여행에 초대합니다! 초대 코드: $code"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "초대 링크 공유"))
                },
            )
        }

        // bandId 없이 진입하는 경우(홈 탐색 탭)를 위한 fallback 라우트
        composable("placeSearch") {
            var query            by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf(com.synctrip.app.data.models.PlaceCategory.ALL) }

            PlaceSearchScreen(
                query            = query,
                onQueryChange    = { query = it },
                selectedCategory = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                places           = emptyList(),
                picks            = emptyList(),
                onPlaceClick     = {},
                onCartToggle     = {},
                onBackClick      = { navController.popBackStack() },
            )
        }

        // 로비에서 진입하는 경우 — bandId 포함, BandViewModel 연결
        composable("placeSearch/{bandId}") { backStackEntry ->
            val bandId           = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val bandViewModel: BandViewModel = viewModel()
            val uiState          by bandViewModel.uiState.collectAsState()
            var query            by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf(PlaceCategory.ALL) }
            val snackbarState    = remember { SnackbarHostState() }

            // 진입 시 픽 목록만 로드 — 장소 검색은 키워드 입력 후 수동 실행
            LaunchedEffect(bandId) {
                bandViewModel.loadPicks(bandId)
            }

            // 검색 에러 스낵바 — 실패 원인을 사용자에게 표시
            LaunchedEffect(uiState.error) {
                uiState.error?.let { err ->
                    snackbarState.showSnackbar(err)
                    bandViewModel.clearError()
                }
            }

            PlaceSearchScreen(
                query             = query,
                onQueryChange     = { query = it },
                selectedCategory  = selectedCategory,
                onCategoryChange  = { cat ->
                    selectedCategory = cat
                    // 키워드 없이 카테고리만 바꿔도 API를 호출하지 않음 — keyword 필수 정책
                    if (query.isNotBlank()) {
                        bandViewModel.searchPlaces(
                            bandId   = bandId,
                            keyword  = query,
                            category = if (cat == PlaceCategory.ALL) null else cat.name,
                        )
                    }
                },
                onSearch = {
                    if (query.isNotBlank()) {
                        bandViewModel.searchPlaces(
                            bandId   = bandId,
                            keyword  = query,
                            category = if (selectedCategory == PlaceCategory.ALL) null else selectedCategory.name,
                        )
                    }
                },
                places            = uiState.searchResults,
                isLoading         = uiState.isSearchLoading,
                picks             = uiState.picks?.items ?: emptyList(),
                maxPickCount      = uiState.picks?.maxCount ?: 5,
                onPlaceClick      = {},
                onCartToggle      = { externalId -> bandViewModel.togglePick(bandId, externalId) },
                onBackClick       = { navController.popBackStack() },
                onReadyClick      = {
                    bandViewModel.setReady(bandId)
                    navController.popBackStack()
                },
                snackbarHostState = snackbarState,
            )

            // 장바구니 한도 초과 다이얼로그
            if (uiState.pickLimitReached) {
                AlertDialog(
                    onDismissRequest = { bandViewModel.clearPickLimit() },
                    title = { Text("장소 한도 도달") },
                    text  = { Text("장소는 최대 ${uiState.picks?.maxCount ?: 5}개까지 담을 수 있어요.\n기존 장소를 삭제하고 새로 담아보세요.") },
                    confirmButton = {
                        TextButton(onClick = { bandViewModel.clearPickLimit() }) { Text("확인") }
                    },
                )
            }
        }

        // 투표 화면 — 스와이프 카드 방식, 전원 완료 시 1.5초 후 aiLoading 자동 이동
        composable("blindVoting/{bandId}") { backStackEntry ->
            val bandId        = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val voteViewModel: VoteViewModel = viewModel()
            val bandViewModel: BandViewModel = viewModel()
            val uiState       by voteViewModel.uiState.collectAsState()
            val bandUiState   by bandViewModel.uiState.collectAsState()

            val voteSnackbarState = remember { SnackbarHostState() }

            // 화면 진입 시 투표 장소 로드 + WebSocket 연결
            LaunchedEffect(bandId) {
                voteViewModel.loadVotePlaces(bandId)
                voteViewModel.connectWebSocket(ApiClient.accessToken ?: "", bandId)
            }

            // 투표 실패 에러 스낵바
            LaunchedEffect(uiState.error) {
                uiState.error?.let { err ->
                    voteSnackbarState.showSnackbar(err)
                    voteViewModel.clearError()
                }
            }

            // 내 투표 완료 여부: pendingPlaces 소진 또는 서버 응답 myComplete
            // 로딩 중에는 초기 빈 상태를 완료로 잘못 판단하지 않도록 isLoading 가드 추가
            val isMyComplete  = uiState.myStatus?.myComplete == true ||
                (!uiState.isLoading && uiState.pendingPlaces.isEmpty() && uiState.votedPlaces.isNotEmpty())
            // 전원 투표 완료 여부: 서버 groupStatus 기준
            val isAllComplete = uiState.groupStatus?.isAllComplete == true

            // 전원 투표 완료 → 1.5초 후 투표 결과 화면으로 자동 이동
            LaunchedEffect(isAllComplete) {
                if (isAllComplete) {
                    delay(1_500L)
                    navController.navigate("voteResults/$bandId") {
                        popUpTo("blindVoting/$bandId") { inclusive = true }
                    }
                }
            }

            SwipeVotingScreen(
                pendingPlaces    = uiState.pendingPlaces,
                votedCount       = uiState.votedPlaces.size,
                isMyVoteComplete = isMyComplete,
                isAllComplete    = isAllComplete,
                isLoading        = uiState.isLoading,
                onVote           = { placeId, result -> voteViewModel.voteForPlace(placeId, result) },
                onBackClick      = { navController.popBackStack() },
                onVotingDone      = {},
                isOwner           = bandUiState.selectedBand?.isOwner == true,
                snackbarHostState = voteSnackbarState,
                onForceClose     = {
                    bandViewModel.advanceBandStatus(bandId) { transition ->
                        when (transition.currentStatus) {
                            BandStatus.GENERATING -> navController.navigate("aiLoading/$bandId") {
                                popUpTo("blindVoting/$bandId") { inclusive = true }
                            }
                            else -> navController.popBackStack()
                        }
                    }
                },
            )
        }

        // 투표 결과 화면 — 전원 투표 완료 후 blindVoting에서 자동 이동
        composable("voteResults/{bandId}") { backStackEntry ->
            val bandId        = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val voteViewModel: VoteViewModel = viewModel()
            val uiState       by voteViewModel.uiState.collectAsState()
            var isRefreshing  by remember { mutableStateOf(false) }

            LaunchedEffect(bandId) { voteViewModel.loadVoteResults(bandId) }
            LaunchedEffect(uiState.isLoading) {
                if (!uiState.isLoading) isRefreshing = false
            }

            VoteResultScreen(
                results          = uiState.voteResults,
                isLoading        = uiState.isLoading,
                isRefreshing     = isRefreshing,
                onRefresh        = { isRefreshing = true; voteViewModel.loadVoteResults(bandId) },
                onCreateSchedule = {
                    navController.navigate("aiLoading/$bandId") {
                        popUpTo("voteResults/$bandId") { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        composable("passport") {
            val context       = LocalContext.current
            val bandViewModel: BandViewModel = viewModel()
            val uiState by bandViewModel.uiState.collectAsState()

            // 마지막 여권 열람 시각 — 진입 즉시 읽어서 "신규 기준선"으로 사용
            var lastVisitedMs by remember { mutableStateOf(Long.MAX_VALUE) }  // MAX = 아직 읽기 전 (모두 기존으로 처리)
            var visitLoaded   by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                // 1) 이전 방문 시각 읽기 → 신규 스탬프 판별 기준
                lastVisitedMs = TokenDataStore.passportLastVisitedFlow(context).first()
                visitLoaded   = true
                // 2) 현재 시각으로 갱신 (다음 방문 때 기준이 됨)
                TokenDataStore.markPassportVisited(context)
                // 3) 데이터 로드
                if (uiState.userProfile == null) bandViewModel.loadMyProfile()
                bandViewModel.loadPassportStamps()
            }

            // lastVisitedMs 이후에 찍힌 스탬프 ID만 "신규" 처리
            val newStampIds = remember(uiState.passportStamps, visitLoaded) {
                if (!visitLoaded) emptySet()
                else uiState.passportStamps
                    .filter { it.stampedAtMs > lastVisitedMs }
                    .map { it.id }
                    .toSet()
            }

            var isRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(uiState.isPassportLoading) {
                if (!uiState.isPassportLoading) isRefreshing = false
            }

            MyPassportScreen(
                user = UserProfile(
                    id              = uiState.userProfile?.id?.toString() ?: "",
                    nickname        = uiState.userProfile?.name ?: "여행자",
                    profileImageUrl = uiState.userProfile?.profileImageUrl,
                    homeTown        = null,
                    totalTrips      = uiState.passportStamps.size,
                    passportStamps  = uiState.passportStamps,
                ),
                newStampIds  = newStampIds,
                isLoading    = uiState.isPassportLoading,
                isRefreshing = isRefreshing,
                onRefresh    = {
                    isRefreshing = true
                    bandViewModel.loadMyProfile()
                    bandViewModel.loadPassportStamps()
                },
                onBackClick  = { navController.popBackStack() },
            )
        }

        composable("notifications") {
            val vm: NotificationViewModel = viewModel()
            val uiState      by vm.uiState.collectAsState()
            var isRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { vm.loadNotifications() }
            LaunchedEffect(uiState.isLoading) {
                if (!uiState.isLoading) isRefreshing = false
            }
            NotificationScreen(
                groups        = uiState.groups,
                onMarkAllRead = { vm.markAllRead() },
                onItemClick   = { id -> vm.markRead(id) },
                onBackClick   = { navController.popBackStack() },
                isRefreshing  = isRefreshing,
                onRefresh     = { isRefreshing = true; vm.loadNotifications() },
            )
        }

        // 일정 화면 — AI 로딩 완료 후 자동 이동, 로비 "일정 보기" 버튼에서도 진입
        composable("schedule/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val scheduleViewModel: ScheduleViewModel = viewModel()
            val bandViewModel: BandViewModel = viewModel()
            val scheduleState by scheduleViewModel.uiState.collectAsState()
            val bandState by bandViewModel.uiState.collectAsState()
            val snackbarState = remember { SnackbarHostState() }

            LaunchedEffect(bandId) {
                bandViewModel.loadBands()
                scheduleViewModel.loadSchedule(bandId)
            }

            LaunchedEffect(scheduleState.error) {
                scheduleState.error?.let {
                    snackbarState.showSnackbar(it)
                    scheduleViewModel.clearError()
                }
            }

            val band        = bandState.bands.find { it.id == bandId }
            val destination = band?.destination ?: "일정"
            val isOverseas  = band?.isOverseas ?: false

            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                ScheduleScreen(
                    destination     = destination,
                    schedule        = scheduleState.schedule,
                    altOptions      = scheduleState.altOptions,
                    isLoading       = scheduleState.isLoading,
                    isEditing       = scheduleState.isEditing,
                    canEdit         = false,
                    isOverseas      = isOverseas,
                    onStartEditing      = { scheduleViewModel.startEditing(bandId) },
                    onFinishEditing     = { scheduleViewModel.finishEditing(bandId) },
                    onSwapSlot          = { sid, pid -> scheduleViewModel.swapSlot(bandId, sid, pid) },
                    onLoadAlts          = { scheduleViewModel.loadAlts(bandId) },
                    planBResults        = scheduleState.planBResults,
                    isPlanBLoading      = scheduleState.isPlanBLoading,
                    onRequestPlanB      = { pid -> scheduleViewModel.loadPlanB(bandId, pid) },
                    onExecutePlanBSwap  = { sid, pid -> scheduleViewModel.executePlanBSwap(bandId, sid, pid) },
                    onBackClick     = { navController.popBackStack() },
                    onShareClick    = {},
                )
            }
        }

        // 정산 화면 — DONE 상태 밴드에서 진입
        composable("settlement/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val bandViewModel: BandViewModel = viewModel()
            val uiState by bandViewModel.uiState.collectAsState()
            var settleCurrentUserId by remember { mutableStateOf(0L) }

            val context = LocalContext.current
            LaunchedEffect(bandId) {
                settleCurrentUserId = TokenDataStore.userIdFlow(context).first() ?: 0L
                bandViewModel.loadSettlement(bandId, settleCurrentUserId)
            }

            val settlement = uiState.settlement
            if (settlement != null) {
                SettlementScreen(
                    settlement    = settlement,
                    currentUserId = settleCurrentUserId,
                    onSettleClick = {},
                    onBackClick   = { navController.popBackStack() },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlaneLoadingIndicator()
                }
            }
        }

        // ── 알림 설정 화면 ─────────────────────────────────────────────────
        composable("notificationSettings") {
            val bandViewModel: BandViewModel = viewModel()
            val uiState by bandViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { bandViewModel.loadNotificationSettings() }
            NotificationSettingsScreen(
                settings    = uiState.notificationSettings,
                isLoading   = uiState.isNotificationSettingsLoading,
                onToggle    = { type, enabled -> bandViewModel.updateNotificationSetting(type, enabled) },
                onBackClick = { navController.popBackStack() },
            )
        }

        // ── 지난 여행 기록 화면 (USR-025) ───────────────────────────────────
        composable("pastTrips") {
            val bandViewModel: BandViewModel = viewModel()
            val uiState by bandViewModel.uiState.collectAsState()
            // 밴드 목록이 아직 없으면 로드
            LaunchedEffect(Unit) { if (uiState.bands.isEmpty()) bandViewModel.loadBands() }
            PastTripsScreen(
                // DONE 상태 밴드만 최신순으로 정렬하여 전달
                pastBands   = uiState.bands.reversed().filter { it.status == BandStatus.DONE },
                onBandClick = { bandId -> navController.navigate("tripLobby/$bandId") },
                onBackClick = { navController.popBackStack() },
            )
        }

        // ── 프로필 편집 화면 ────────────────────────────────────────────────
        composable("profileEdit") {
            val bandViewModel: BandViewModel = viewModel()
            val uiState by bandViewModel.uiState.collectAsState()
            val snackbarState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { bandViewModel.loadMyProfile() }
            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                ProfileEditScreen(
                    initialName            = uiState.userProfile?.name ?: "",
                    initialProfileImageUrl = uiState.userProfile?.profileImageUrl,
                    isLoading              = uiState.isLoading,
                    onSave                 = { name, imageUrl ->
                        bandViewModel.updateProfile(
                            name            = name,
                            profileImageUrl = imageUrl,
                            onSuccess       = { navController.popBackStack() },
                            onError         = { msg -> scope.launch { snackbarState.showSnackbar(msg) } },
                        )
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 일정 생성 시뮬레이션 헬퍼
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 일정 생성 로딩 화면.
 * 백엔드 폴링 대신 단계별 진행률을 시뮬레이션하여 UX를 유지한다.
 * 완료(100%) 도달 후 0.6초 뒤 onComplete 호출.
 */
@Composable
private fun AiLoadingSimulated(onComplete: () -> Unit) {
    // 각 단계: (목표 진행률, 단계 메시지, 딜레이ms)
    val steps = listOf(
        Triple(15,  "투표 결과 분석 중…",    900L),
        Triple(35,  "장소 동선 최적화 중…",  1100L),
        Triple(58,  "일정 배정 중…",         1000L),
        Triple(78,  "세부 일정 조율 중…",    900L),
        Triple(92,  "마지막 손질 중…",       800L),
        Triple(100, "일정 생성 완료!",        600L),
    )

    var progress    by remember { mutableIntStateOf(0) }
    var currentStep by remember { mutableStateOf("일정 생성 시작 중…") }
    var isComplete  by remember { mutableStateOf(false) }

    // 단계별 진행률 순차 실행
    LaunchedEffect(Unit) {
        for ((target, label, delayMs) in steps) {
            delay(delayMs)
            progress    = target
            currentStep = label
        }
        delay(600)
        isComplete = true
    }

    AiLoadingScreen(
        status     = AiGenerationStatus("sim", progress, currentStep, isComplete),
        onComplete = onComplete,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 매퍼: 백엔드 모델 → UI 모델
// ─────────────────────────────────────────────────────────────────────────────

/**
 * BandResponse(서버 모델)를 HomeScreen용 TripBand(UI 모델)로 변환.
 * 멤버 상세는 별도 API 호출이 필요하므로 홈 화면에서는 빈 목록 사용.
 */
private fun BandResponse.toTripBand() = TripBand(
    id                = id.toString(),
    name              = name,
    destination       = destination,
    heroImageUrl      = thumbnailUrl ?: "",   // 여행지 생성 시 저장된 썸네일, 없으면 그라디언트 플레이스홀더
    startDate         = startDate,
    endDate           = endDate,
    status            = when (status) {
        BandStatus.TRAVELLING -> TripStatus.ACTIVE
        BandStatus.DONE       -> TripStatus.COMPLETED
        else                  -> TripStatus.PLANNING
    },
    members           = emptyList(),   // 홈 목록에서는 memberCount만 있음
    completionPercent = when (status) {
        BandStatus.PLANNING   -> 10
        BandStatus.VOTING     -> 30
        BandStatus.GENERATING -> 50
        BandStatus.TRAVELLING -> 70
        BandStatus.DONE       -> 100
    },
)
