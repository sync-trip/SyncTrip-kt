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
import androidx.navigation.compose.rememberNavController
import com.synctrip.app.core.TokenDataStore
import com.synctrip.app.data.models.*
import com.synctrip.app.data.repository.BandRepository
import com.synctrip.app.network.ApiClient
import com.synctrip.app.ui.components.BottomNavDestination
import com.synctrip.app.ui.screens.*
import com.synctrip.app.ui.viewmodel.AuthUiState
import com.synctrip.app.ui.viewmodel.AuthViewModel
import com.synctrip.app.ui.viewmodel.BandViewModel
import com.synctrip.app.ui.viewmodel.VoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SyncTripNavGraph(
    pendingDeepLinkCode: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scope         = rememberCoroutineScope()

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
            var selectedNavItem by remember { mutableStateOf(BottomNavDestination.Home) }
            val snackbarState   = remember { SnackbarHostState() }

            // 화면 진입 시 밴드 목록 + 유저 프로필 로드
            LaunchedEffect(Unit) {
                bandViewModel.loadBands()
                bandViewModel.loadMyProfile()
            }

            // BottomSheet 수동 코드 입력 에러 → 스낵바 표시
            LaunchedEffect(bandUiState.error) {
                bandUiState.error?.let { err ->
                    snackbarState.showSnackbar(err)
                    bandViewModel.clearError()
                }
            }

            HomeScreen(
                recommendedContent   = emptyList(),
                myTripBands          = bandUiState.bands.reversed().map { it.toTripBand() },
                userName             = bandUiState.userProfile?.name ?: "",
                userProfileImageUrl  = bandUiState.userProfile?.profileImageUrl,
                selectedNavItem      = selectedNavItem,
                snackbarHostState    = snackbarState,
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
                                thumbnailUrl   = dest.thumbnailUrl,
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

        // 투표 완료 후 진입하는 경우 — 완료 후 해당 밴드 일정 화면으로
        composable("aiLoading/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId") ?: ""
            AiLoadingSimulated(
                onComplete = {
                    navController.navigate("schedule/$bandId") {
                        popUpTo("aiLoading/$bandId") { inclusive = true }
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
                        PlaneLoadingIndicator()
                    }
                } else {
                    TripLobbyScreen(
                        band              = band,
                        members           = bandUiState.members,
                        picks             = bandUiState.picks,
                        currentUserId     = currentUserId,
                        isLoading         = bandUiState.isLoading,
                        onBackClick       = { navController.popBackStack() },
                        onReadyClick      = { bandViewModel.setReady(bandIdLong) },
                        onInviteClick     = { navController.navigate("invite/$bandIdLong") },
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
                cartCount        = 0,
                onPlaceClick     = {},
                onCartToggle     = {},
                onViewCartClick  = {},
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

            // 진입 시 픽 목록 + 전체 장소 초기 로드
            LaunchedEffect(bandId) {
                bandViewModel.loadPicks(bandId)
                bandViewModel.searchPlaces(bandId)
            }

            PlaceSearchScreen(
                query            = query,
                onQueryChange    = { query = it },
                selectedCategory = selectedCategory,
                onCategoryChange = { cat ->
                    selectedCategory = cat
                    bandViewModel.searchPlaces(
                        bandId   = bandId,
                        keyword  = query.takeIf { it.isNotBlank() },
                        category = if (cat == PlaceCategory.ALL) null else cat.name,
                    )
                },
                onSearch = {
                    bandViewModel.searchPlaces(
                        bandId   = bandId,
                        keyword  = query.takeIf { it.isNotBlank() },
                        category = if (selectedCategory == PlaceCategory.ALL) null else selectedCategory.name,
                    )
                },
                places           = uiState.searchResults,
                isLoading        = uiState.isSearchLoading,
                cartCount        = uiState.picks?.items?.size ?: 0,
                onPlaceClick     = {},
                onCartToggle     = { externalId -> bandViewModel.togglePick(bandId, externalId) },
                onViewCartClick  = { navController.popBackStack() },
                onBackClick      = { navController.popBackStack() },
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

        // 투표 화면 — 스와이프 카드 방식, 완료 시 일정 생성 화면으로 이동
        composable("blindVoting/{bandId}") { backStackEntry ->
            val bandId        = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val voteViewModel: VoteViewModel = viewModel()
            val uiState       by voteViewModel.uiState.collectAsState()

            // 화면 진입 시 투표 대상 장소 로드
            LaunchedEffect(bandId) {
                voteViewModel.loadVotePlaces(bandId)
            }

            // pendingPlaces 소진 또는 서버 응답 isComplete = 모두 투표 완료
            val isComplete = uiState.myStatus?.isComplete == true ||
                (uiState.pendingPlaces.isEmpty() && uiState.votedPlaces.isNotEmpty())

            SwipeVotingScreen(
                pendingPlaces    = uiState.pendingPlaces,
                votedCount       = uiState.votedPlaces.size,
                isMyVoteComplete = isComplete,
                isLoading        = uiState.isLoading,
                onVote           = { placeId, result -> voteViewModel.voteForPlace(placeId, result) },
                onBackClick      = { navController.popBackStack() },
                onVotingDone     = {
                    // 투표 완료 → 일정 생성 로딩 화면으로 이동 (bandId 포함, 투표 화면 백스택 제거)
                    navController.navigate("aiLoading/$bandId") {
                        popUpTo("blindVoting/$bandId") { inclusive = true }
                    }
                },
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
        Triple(58,  "숙소 및 식당 배정 중…", 1000L),
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
