package com.synctrip.app.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.*
import com.synctrip.app.network.ApiClient
import com.synctrip.app.ui.components.BottomNavDestination
import com.synctrip.app.ui.screens.*
import com.synctrip.app.ui.viewmodel.AuthUiState
import com.synctrip.app.ui.viewmodel.AuthViewModel
import com.synctrip.app.ui.viewmodel.BandViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SyncTripNavGraph() {
    val navController = rememberNavController()

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
                            CircularProgressIndicator()
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
            var selectedNavItem by remember { mutableStateOf(BottomNavDestination.Home) }

            // 화면 진입 시 밴드 목록 로드
            LaunchedEffect(Unit) { bandViewModel.loadBands() }

            HomeScreen(
                recommendedContent   = emptyList(),
                myTripBands          = bandUiState.bands.map { it.toTripBand() },
                selectedNavItem      = selectedNavItem,
                onNavItemSelected    = { dest ->
                    selectedNavItem = dest
                    when (dest) {
                        BottomNavDestination.Explore  -> navController.navigate("placeSearch")
                        BottomNavDestination.Passport -> navController.navigate("passport")
                        else                          -> {}
                    }
                },
                onSearchClick        = {},
                onNotificationsClick = { navController.navigate("notifications") },
                onContentCardClick   = {},
                onTripBandClick      = { bandId -> navController.navigate("tripLobby/$bandId") },
                onCreateTripClick    = { navController.navigate("createTrip") },
                onPassportClick      = { navController.navigate("passport") },
                // 로그아웃: 토큰 삭제 후 로그인 화면으로 이동 (백 스택 초기화)
                onLogout             = {
                    authViewModel.logout(context)
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
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
            var bandName            by remember { mutableStateOf("") }
            var startDate           by remember { mutableStateOf("") }
            var endDate             by remember { mutableStateOf("") }
            var travelStyle         by remember { mutableStateOf(BandTravelStyle.RELAXED) }

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
                    travelStyle         = travelStyle,
                    onTravelStyleChange = { travelStyle = it },
                    isLoading           = bandUiState.isLoading,
                    onCreateTrip        = {
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
                            )
                        ) { newBand ->
                            // 생성 성공 → 로비로 이동 (createTrip은 백스택에서 제거)
                            navController.navigate("tripLobby/${newBand.id}") {
                                popUpTo("createTrip") { inclusive = true }
                            }
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }
        }

        composable("aiLoading") {
            AiLoadingScreen(
                status     = AiGenerationStatus("job1", 0, "일정 생성 중…", false),
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
            )
        }

        composable("tripLobby/{bandId}") { backStackEntry ->
            val context       = LocalContext.current
            val bandViewModel = viewModel<BandViewModel>()
            val bandUiState   by bandViewModel.uiState.collectAsState()
            val snackbarState = remember { SnackbarHostState() }

            // URL 경로에서 bandId 파싱
            val bandIdLong = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: 0L

            // 현재 로그인 유저 ID (DataStore에서 읽음)
            var currentUserId by remember { mutableStateOf(0L) }

            // 화면 진입 시 밴드 목록·멤버·장바구니 로드 및 유저 ID 확인
            LaunchedEffect(bandIdLong) {
                currentUserId = TokenDataStore.userIdFlow(context).first() ?: 0L
                bandViewModel.loadBands()
                bandViewModel.loadMembers(bandIdLong)
                bandViewModel.loadPicks(bandIdLong)
            }

            // 에러 스낵바
            LaunchedEffect(bandUiState.error) {
                bandUiState.error?.let {
                    snackbarState.showSnackbar(it)
                    bandViewModel.clearError()
                }
            }

            // 밴드 목록에서 현재 bandId에 해당하는 밴드 찾기
            val band = bandUiState.bands.find { it.id == bandIdLong }

            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                if (band == null) {
                    // 밴드 로딩 중이거나 찾을 수 없을 때 중앙 스피너 표시
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    TripLobbyScreen(
                        band              = band,
                        members           = bandUiState.members,
                        picks             = bandUiState.picks,
                        inviteCode        = bandUiState.inviteCode,
                        currentUserId     = currentUserId,
                        isLoading         = bandUiState.isLoading,
                        onBackClick       = { navController.popBackStack() },
                        onReadyClick      = { bandViewModel.setReady(bandIdLong) },
                        onGetInviteCodeClick = { bandViewModel.getInviteCode(bandIdLong) },
                        onShareInviteCode = { code ->
                            // 시스템 공유 시트로 초대 코드 공유
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "SyncTrip 여행에 초대합니다! 초대 코드: $code")
                            }
                            context.startActivity(Intent.createChooser(intent, "초대 코드 공유"))
                        },
                        onAdvanceStatus   = {
                            bandViewModel.advanceBandStatus(bandIdLong) { updated ->
                                // VOTING 상태가 되면 투표 화면으로 자동 이동
                                if (updated.status == BandStatus.VOTING) {
                                    navController.navigate("blindVoting/$bandIdLong")
                                }
                            }
                        },
                        onGoToPlaceSearch = { navController.navigate("placeSearch/$bandIdLong") },
                        onGoToVoting      = { navController.navigate("blindVoting/$bandIdLong") },
                        onGoToSchedule    = { navController.navigate("schedule/$bandIdLong") },
                    )
                }
            }
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
                cartCount        = 0,
                onPlaceClick     = {},
                onCartToggle     = {},
                onViewCartClick  = {},
                onBackClick      = { navController.popBackStack() },
            )
        }

        // 로비에서 진입하는 경우 — bandId 포함
        composable("placeSearch/{bandId}") { backStackEntry ->
            val bandIdArg        = backStackEntry.arguments?.getString("bandId") ?: ""
            var query            by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf(com.synctrip.app.data.models.PlaceCategory.ALL) }

            PlaceSearchScreen(
                query            = query,
                onQueryChange    = { query = it },
                selectedCategory = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                places           = emptyList(),
                cartCount        = 0,
                onPlaceClick     = {},
                onCartToggle     = {},
                onViewCartClick  = {},
                onBackClick      = { navController.popBackStack() },
            )
        }

        composable("passport") {
            MyPassportScreen(
                user = UserProfile(
                    id              = "",
                    nickname        = "여행자",
                    profileImageUrl = null,
                    homeTown        = null,
                    totalTrips      = 0,
                ),
                onBackClick = { navController.popBackStack() },
            )
        }

        composable("notifications") {
            NotificationScreen(
                groups        = emptyMap(),
                onMarkAllRead = {},
                onItemClick   = {},
                onBackClick   = { navController.popBackStack() },
            )
        }
    }
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
    destination       = destination,
    heroImageUrl      = "",   // 서버에 이미지 URL 없음
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
