package com.synctrip.app.navigation

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
            var destination    by remember { mutableStateOf("") }
            var startDate      by remember { mutableStateOf("") }
            var endDate        by remember { mutableStateOf("") }
            var selectedStyles by remember { mutableStateOf(emptySet<TravelStyle>()) }

            CreateTripScreen(
                suggestions         = listOf(
                    DestinationSuggestion("도쿄", "일본",  "", "TYO"),
                    DestinationSuggestion("파리", "프랑스", "", "CDG"),
                    DestinationSuggestion("뉴욕", "미국",  "", "JFK"),
                    DestinationSuggestion("제주", "한국",  "", "CJU"),
                ),
                destination         = destination,
                onDestinationChange = { destination = it },
                startDate           = startDate,
                onStartDateChange   = { startDate = it },
                endDate             = endDate,
                onEndDateChange     = { endDate = it },
                selectedStyles      = selectedStyles,
                onStyleToggle       = { style ->
                    selectedStyles = if (style in selectedStyles)
                        selectedStyles - style else selectedStyles + style
                },
                onCreateTrip        = { navController.navigate("aiLoading") },
                onBackClick         = { navController.popBackStack() },
            )
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
            val bandId = backStackEntry.arguments?.getString("bandId") ?: ""
            TripLobbyScreen(
                lobby = TripLobby(
                    tripId          = bandId,
                    destination     = "여행지",
                    heroImageUrl    = "",
                    dateRange       = "",
                    members         = emptyList(),
                    tasks           = emptyList(),
                    overallProgress = 0,
                ),
                onBackClick   = { navController.popBackStack() },
                onTaskClick   = {},
                onInviteClick = {},
                onStartAi     = { navController.navigate("aiLoading") },
            )
        }

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
