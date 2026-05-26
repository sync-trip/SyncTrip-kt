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
                snackbarHostState    = snackbarState,
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

        // 투표 완료 → 허브로 돌아감. 허브의 scheduleReadyEvent가 일정 탭 자동 전환 처리
        composable("aiLoading/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId") ?: ""
            AiLoadingSimulated(
                onComplete = {
                    // 허브(tripLobby)가 백스택에 있으므로 popBackStack으로 복귀
                    navController.popBackStack()
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
            // 이전 상태 추적 — GENERATING→TRAVELLING 전환 시에만 탭 자동 전환
            var prevBandStatus by remember { mutableStateOf<BandStatus?>(null) }

            // 진입 시 기본 데이터 로드
            LaunchedEffect(bandIdLong) {
                currentUserId = TokenDataStore.userIdFlow(context).first() ?: 0L
                bandViewModel.loadBands()
                bandViewModel.loadMembers(bandIdLong)
                bandViewModel.loadPicks(bandIdLong)
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
                        if (bandUiState.settlement == null) {
                            bandViewModel.loadSettlement(bandIdLong)
                        }
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
                    schedule          = scheduleUiState.schedule,
                    altOptions        = scheduleUiState.altOptions,
                    isScheduleLoading = scheduleUiState.isLoading,
                    isEditing         = scheduleUiState.isEditing,
                    settlement        = bandUiState.settlement,
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
                    onSettleClick     = {},
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
                places            = uiState.searchResults,
                isLoading         = uiState.isSearchLoading,
                picks             = uiState.picks?.items ?: emptyList(),
                maxPickCount      = uiState.picks?.maxCount ?: 5,
                onPlaceClick      = {},
                onCartToggle      = { externalId -> bandViewModel.togglePick(bandId, externalId) },
                onBackClick       = { navController.popBackStack() },
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

        // 투표 화면 — 스와이프 카드 방식, 완료 시 일정 생성 화면으로 이동
        composable("blindVoting/{bandId}") { backStackEntry ->
            val bandId        = backStackEntry.arguments?.getString("bandId")?.toLongOrNull() ?: return@composable
            val voteViewModel: VoteViewModel = viewModel()
            val uiState       by voteViewModel.uiState.collectAsState()

            // 화면 진입 시 투표 장소 로드 + WebSocket 연결
            LaunchedEffect(bandId) {
                voteViewModel.loadVotePlaces(bandId)
                voteViewModel.connectWebSocket(ApiClient.accessToken ?: "", bandId)
            }

            // 내 투표 완료 여부: pendingPlaces 소진 또는 서버 응답 isComplete
            val isMyComplete = uiState.myStatus?.isComplete == true ||
                (uiState.pendingPlaces.isEmpty() && uiState.votedPlaces.isNotEmpty())
            // 전원 투표 완료 여부: 서버 groupStatus 기준
            val isAllComplete = uiState.groupStatus?.isAllComplete == true

            SwipeVotingScreen(
                pendingPlaces    = uiState.pendingPlaces,
                votedCount       = uiState.votedPlaces.size,
                isMyVoteComplete = isMyComplete,
                isAllComplete    = isAllComplete,
                isLoading        = uiState.isLoading,
                onVote           = { placeId, result -> voteViewModel.voteForPlace(placeId, result) },
                onBackClick      = { navController.popBackStack() },
                onVotingDone     = {
                    // 전원 투표 완료 → 일정 생성 로딩 화면으로 이동 (투표 화면 백스택 제거)
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
            val vm: NotificationViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.loadNotifications() }
            NotificationScreen(
                groups        = uiState.groups,
                onMarkAllRead = { vm.markAllRead() },
                onItemClick   = { id -> vm.markRead(id) },
                onBackClick   = { navController.popBackStack() },
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

            val destination = bandState.bands.find { it.id == bandId }?.destination ?: "일정"

            Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { _ ->
                ScheduleScreen(
                    destination     = destination,
                    schedule        = scheduleState.schedule,
                    altOptions      = scheduleState.altOptions,
                    isLoading       = scheduleState.isLoading,
                    isEditing       = scheduleState.isEditing,
                    canEdit         = false,
                    onStartEditing  = { scheduleViewModel.startEditing(bandId) },
                    onFinishEditing = { scheduleViewModel.finishEditing(bandId) },
                    onSwapSlot      = { sid, pid -> scheduleViewModel.swapSlot(bandId, sid, pid) },
                    onLoadAlts      = { scheduleViewModel.loadAlts(bandId) },
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

            LaunchedEffect(bandId) { bandViewModel.loadSettlement(bandId) }

            val settlement = uiState.settlement
            if (settlement != null) {
                SettlementScreen(
                    settlement    = settlement,
                    onSettleClick = {},
                    onBackClick   = { navController.popBackStack() },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlaneLoadingIndicator()
                }
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
