package com.synctrip.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

// ═════════════════════════════════════════════════════════════════════════════
// 1. Swipe Voting Screen  ← 메인 투표 화면 (스와이프 카드 방식)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 스와이프 투표 화면.
 * 장소 카드를 한 장씩 보여주며 좋아요(♥) / 싫어요(✗) 버튼으로 투표한다.
 * 내 투표가 끝나면 다른 멤버를 기다리는 UI를 표시하고,
 * 전원 투표 완료(isAllComplete) 시 완료 화면을 표시한다.
 *
 * @param pendingPlaces    아직 투표하지 않은 장소 목록
 * @param votedCount       이미 투표한 장소 수 (진행 표시용)
 * @param isMyVoteComplete 내 투표가 완전히 끝났는지 여부
 * @param isAllComplete    방 전원의 투표가 완전히 끝났는지 여부
 * @param isLoading        장소 목록 로딩 중 여부
 * @param onVote           투표 콜백 (placeId, result: 1=좋아요/-1=싫어요)
 * @param onBackClick      뒤로가기
 * @param isOwner          방장 여부 — true이면 "마감하기" 버튼 표시
 * @param onForceClose     방장 강제 마감 콜백 (advanceBandStatus 재활용)
 * @param onVotingDone     전원 투표 완료 감지 시 호출 → 일정 생성 화면으로 이동 (현재 미사용)
 */
@Composable
fun SwipeVotingScreen(
    pendingPlaces: List<VotePlaceResponse>,
    votedCount: Int,
    isMyVoteComplete: Boolean,
    isAllComplete: Boolean,
    isLoading: Boolean,
    onVote: (placeId: Long, result: Int) -> Unit,
    onBackClick: () -> Unit,
    onVotingDone: () -> Unit,
    isOwner: Boolean = false,
    onForceClose: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val totalCount   = votedCount + pendingPlaces.size
    val currentPlace = pendingPlaces.firstOrNull()
    val scope        = rememberCoroutineScope()
    // 방장 강제 마감 확인 다이얼로그
    var showForceCloseDialog by remember { mutableStateOf(false) }

    if (showForceCloseDialog) {
        AlertDialog(
            onDismissRequest = { showForceCloseDialog = false },
            title            = { Text("투표 마감") },
            text             = { Text("아직 투표 중인 멤버가 있어도 지금 투표를 마감할까요?") },
            confirmButton    = {
                TextButton(onClick = { showForceCloseDialog = false; onForceClose() }) {
                    Text("마감", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showForceCloseDialog = false }) { Text("취소") }
            },
        )
    }

    // 카드 수평 이탈 애니메이션 값
    val cardOffsetX  = remember { Animatable(0f) }

    // 새 카드 진입 시 위치 초기화
    LaunchedEffect(currentPlace?.placeId) {
        cardOffsetX.snapTo(0f)
    }

    /** 버튼 클릭 → 카드 날리기 애니메이션 → 투표 제출 */
    fun vote(result: Int) {
        val place = currentPlace ?: return
        scope.launch {
            val target = if (result == 1) 1300f else -1300f
            cardOffsetX.animateTo(target, tween(220, easing = FastOutLinearInEasing))
            onVote(place.placeId, result)
        }
    }

    Scaffold(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar         = {
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
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 방장 전용 강제 마감 버튼
                    if (isOwner) {
                        TextButton(onClick = { showForceCloseDialog = true }) {
                            Text(
                                "마감하기",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Outlined.HowToVote,
                            null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // ── 진행 상태 배지 ─────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "$votedCount / $totalCount",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style    = MaterialTheme.typography.labelLarge.copy(
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))

            when {
                // 로딩 중
                isLoading -> Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) { PlaneLoadingIndicator() }

                // 전원 투표 완료 → 완료 화면 표시 (백엔드가 일정 자동 생성)
                isAllComplete -> Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    VotingAllCompleteContent()
                }

                // 내 투표만 완료 → 다른 멤버 기다리는 중
                isMyVoteComplete -> Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) { VotingWaitingContent() }

                // 투표 중: 카드 + 액션 버튼
                currentPlace != null -> {
                    // 카드 영역
                    // translationY: 좋아요(오른쪽) → 상승, 싫어요(왼쪽) → 하강 — 포물선 궤적
                    // alpha: 이동 거리에 비례해 페이드 아웃
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                val offset = cardOffsetX.value
                                translationX = offset
                                translationY = -offset * 0.13f
                                rotationZ    = offset / 26f
                                alpha        = (1f - kotlin.math.abs(offset) / 480f).coerceIn(0f, 1f)
                            },
                    ) {
                        VotingPlaceCard(
                            place    = currentPlace,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── 액션 버튼 행 ───────────────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // 싫어요 버튼 (빨간)
                        FilledIconButton(
                            onClick  = { vote(-1) },
                            modifier = Modifier.size(64.dp),
                            shape    = CircleShape,
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor   = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Outlined.Close, "싫어요", modifier = Modifier.size(28.dp))
                        }

                        Spacer(Modifier.width(56.dp))

                        // 좋아요 버튼 (주색)
                        FilledIconButton(
                            onClick  = { vote(1) },
                            modifier = Modifier.size(64.dp),
                            shape    = CircleShape,
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Outlined.Favorite, "좋아요", modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // 투표할 장소 없음 (장바구니에 장소 0개)
                else -> Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "투표할 장소가 없어요",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

/** 내 투표 완료 후 다른 멤버를 기다리는 콘텐츠 */
@Composable
private fun VotingWaitingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            modifier = Modifier.size(80.dp),
            tint     = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "투표 완료!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "다른 멤버의 투표를 기다리는 중...",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color    = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 전원 투표 완료 콘텐츠 — 완료 메시지와 스피너 표시, 백엔드가 일정을 자동 생성 */
@Composable
private fun VotingAllCompleteContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            modifier = Modifier.size(80.dp),
            tint     = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "전원 투표 완료!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "일정을 생성하고 있어요…",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color    = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 장소 투표 카드.
 * 상단 60% 이미지 + 하단 40% 장소 정보로 구성된다.
 */
@Composable
private fun VotingPlaceCard(place: VotePlaceResponse, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 이미지 영역 (상단 60%) ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f),
            ) {
                if (place.thumbnailUrl != null) {
                    AsyncImage(
                        model              = place.thumbnailUrl,
                        contentDescription = place.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }

                // 카테고리 + 별점 배지 (이미지 아래쪽에 오버레이)
                Row(
                    modifier              = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaceCategoryBadge(category = place.category)
                    if (place.rating != null) {
                        PlaceRatingBadge(rating = place.rating)
                    }
                }

                // 내가 담은 장소 배지 (우상단)
                if (place.myBookmark) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Bookmark,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                "내가 담은 곳",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color      = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }

            // ── 정보 영역 (하단 40%) ─────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text     = place.name,
                    style    = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!place.address.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = place.address,
                            style    = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 장소 카테고리 배지 (이미지 위 오버레이) */
@Composable
private fun PlaceCategoryBadge(category: ApiPlaceCategory) {
    val label = when (category) {
        ApiPlaceCategory.FOOD     -> "음식점"
        ApiPlaceCategory.CULTURE  -> "관광지"
        ApiPlaceCategory.ACTIVITY -> "액티비티"
        ApiPlaceCategory.SHOPPING -> "쇼핑"
        ApiPlaceCategory.NATURE   -> "자연"
        else                      -> "기타"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 별점 배지 (이미지 위 오버레이) */
@Composable
private fun PlaceRatingBadge(rating: Float) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Star,
                null,
                modifier = Modifier.size(12.dp),
                // 노란색 별점 아이콘 — 앰버 계열 고정색
                tint     = Color(0xFFF59E0B),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "%.1f".format(rating),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. Blind Voting Screen  ← 레거시 (현재 미사용, 추후 삭제 가능)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun BlindVotingScreen(
    session: VotingSession,
    onVote: (candidateId: String) -> Unit,
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
                    Icon(
                        Icons.Outlined.HowToVote,
                        null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text  = session.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text  = "마감: ${session.deadline}   •   총 ${session.totalVotes}표",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )

            Spacer(Modifier.height(24.dp))

            session.candidates.forEach { candidate ->
                VoteCandidateCard(
                    candidate = candidate,
                    hasVoted  = session.hasVoted,
                    isMyVote  = session.myVoteId == candidate.id,
                    onVote    = { onVote(candidate.id) },
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoteCandidateCard(
    candidate: VoteCandidate,
    hasVoted: Boolean,
    isMyVote: Boolean,
    onVote: () -> Unit,
) {
    val borderColor = if (isMyVote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Card(
        onClick   = { if (!hasVoted) onVote() },
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isMyVote) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface,
        ),
        border    = BorderStroke(if (isMyVote) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            if (candidate.imageUrl != null) {
                AsyncImage(
                    model              = candidate.imageUrl,
                    contentDescription = candidate.label,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxWidth().height(160.dp),
                )
            } else {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Hotel, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(candidate.label, style = MaterialTheme.typography.titleLarge)
                    if (isMyVote) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("내 선택", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                if (candidate.description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(candidate.description, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }

                if (candidate.pricePerNight != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "₩${NumberFormat.getNumberInstance(Locale.KOREA).format(candidate.pricePerNight)} / 박",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                    )
                }

                if (hasVoted) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${candidate.voteCount}표", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${(candidate.votePercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress   = { candidate.votePercent },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = if (isMyVote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = onVote,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("이걸로 선택할게요")
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. Settlement Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SettlementScreen(
    settlement: Settlement,
    currentUserId: Long,
    onSettleClick: (transferId: String) -> Unit,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(settlement.tripTitle, style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            }

            item { TotalAmountCard(settlement = settlement) }

            item { MyBalanceCard(balance = settlement.myBalance, currency = settlement.currency) }

            item {
                Text("정산 현황", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            }

            items(settlement.pendingTransfers) { transfer ->
                TransferCard(transfer = transfer, currentUserId = currentUserId, onSettleClick = onSettleClick)
            }

            item {
                Text("상세 지출 내역", style = MaterialTheme.typography.titleLarge)
            }

            items(settlement.summary) { item ->
                ExpenseItemRow(item = item)
            }
        }
    }
}

@Composable
private fun TotalAmountCard(settlement: Settlement) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("총 지출 금액", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)))
            Spacer(Modifier.height(4.dp))
            Text(
                "₩ ${NumberFormat.getNumberInstance(Locale.KOREA).format(settlement.totalAmount)}",
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun MyBalanceCard(balance: Long, currency: String) {
    val isOwed = balance < 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isOwed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isOwed) "내가 낼 금액" else "받을 금액", style = MaterialTheme.typography.titleMedium)
            Text(
                "₩ ${NumberFormat.getNumberInstance(Locale.KOREA).format(kotlin.math.abs(balance))}",
                style = MaterialTheme.typography.titleLarge.copy(
                    color      = if (isOwed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun TransferCard(
    transfer: PendingTransfer,
    currentUserId: Long,
    onSettleClick: (String) -> Unit,
) {
    // 내가 받는 사람이면 true, 보내는 사람이면 false
    val isReceiver = transfer.toUserId == currentUserId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(transfer.fromNickname, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Icon(Icons.Outlined.ArrowForward, null, Modifier.padding(horizontal = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(transfer.toNickname, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Text(
                    "₩ ${NumberFormat.getNumberInstance(Locale.KOREA).format(transfer.amount)}",
                    style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold),
                )
            }
            when {
                transfer.isResolved -> Icon(Icons.Outlined.CheckCircle, "완료", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                // 받는 사람만 정산 완료 버튼 표시
                isReceiver -> OutlinedButton(onClick = { onSettleClick("") }, shape = RoundedCornerShape(8.dp)) { Text("정산 완료") }
                // 보내는 사람은 송금 대기 상태 표시
                else -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        "송금 대기중",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemRow(item: SettlementItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            Text("결제자: ${item.paidBy}", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
        Text(
            "₩ ${NumberFormat.getNumberInstance(Locale.KOREA).format(item.amount)}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ═════════════════════════════════════════════════════════════════════════════
// SettlementContent — Scaffold 없는 순수 콘텐츠 (TripBandHubScreen 임베드용)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 정산 탭 콘텐츠.
 * 정산 요약(송금 목록/잔액) + 실제 지출 목록 + 지출 추가 FAB를 포함한다.
 * TripBandHubScreen의 정산 탭에서 호출한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettlementContent(
    bandId: Long,
    settlement: Settlement?,
    expenses: List<ExpenseResponse>,
    members: List<BandMemberResponse>,
    currentUserId: Long,
    isExpensesLoading: Boolean,
    onAddExpense: (itemName: String, amount: Double, currency: String, payerId: Long, memberIds: List<Long>) -> Unit,
    onDeleteExpense: (expenseId: Long) -> Unit,
    onSettleClick: (transferId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (settlement == null && isExpensesLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlaneLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (settlement != null) {
                    item { TotalAmountCard(settlement = settlement) }
                    item { MyBalanceCard(balance = settlement.myBalance, currency = settlement.currency) }
                    if (settlement.pendingTransfers.isNotEmpty()) {
                        item {
                            Text(
                                "정산 현황",
                                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                        items(settlement.pendingTransfers) { transfer ->
                            TransferCard(transfer = transfer, currentUserId = currentUserId, onSettleClick = onSettleClick)
                        }
                    }
                }

                item {
                    Row(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment   = Alignment.CenterVertically,
                    ) {
                        Text("지출 내역", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${expenses.size}건",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }

                if (isExpensesLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (expenses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "아직 등록된 지출이 없어요",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                } else {
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseCard(
                            expense       = expense,
                            isOwner       = expense.payerId == currentUserId,
                            onDelete      = { onDeleteExpense(expense.id) },
                        )
                    }
                }
            }
        }

        // 지출 추가 FAB
        FloatingActionButton(
            onClick           = { showAddSheet = true },
            modifier          = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor    = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "지출 추가", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (showAddSheet) {
        ExpenseInputSheet(
            bandId         = bandId,
            members        = members,
            currentUserId  = currentUserId,
            baseCurrency   = settlement?.currency ?: "KRW",
            onDismiss      = { showAddSheet = false },
            onConfirm      = { itemName, amount, currency, payerId, memberIds ->
                onAddExpense(itemName, amount, currency, payerId, memberIds)
                showAddSheet = false
            },
        )
    }
}

/** 지출 카드 — 항목명/금액/결제자/날짜 표시. 본인 지출에만 삭제 버튼 표시 */
@Composable
private fun ExpenseCard(
    expense: ExpenseResponse,
    isOwner: Boolean,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("지출 삭제") },
            text             = { Text("'${expense.itemName}' 지출을 삭제할까요?") },
            confirmButton    = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.itemName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(
                    "결제자: ${expense.payerName}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                if (expense.memberIds.isNotEmpty()) {
                    Text(
                        "${expense.memberIds.size}명 분담",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${NumberFormat.getNumberInstance(Locale.KOREA).format(expense.amount.toLong())} ${expense.currency}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                    ),
                )
                if (isOwner) {
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "삭제",
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 지출 입력 BottomSheet — 항목명, 금액, 통화, 분담자 선택, 영수증 OCR */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseInputSheet(
    bandId: Long,
    members: List<BandMemberResponse>,
    currentUserId: Long,
    baseCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (itemName: String, amount: Double, currency: String, payerId: Long, memberIds: List<Long>) -> Unit,
) {
    var itemName     by remember { mutableStateOf("") }
    var amountText   by remember { mutableStateOf("") }
    var currency     by remember { mutableStateOf(baseCurrency) }
    var isOcrLoading by remember { mutableStateOf(false) }
    // 결제자: 기본값은 현재 사용자
    var selectedPayerId by remember { mutableStateOf(currentUserId) }
    var payerDropdownExpanded by remember { mutableStateOf(false) }
    // 분담자: 기본값은 전체 멤버
    val selectedIds = remember { mutableStateListOf<Long>().apply { addAll(members.map { it.userId }) } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope      = rememberCoroutineScope()
    val context    = androidx.compose.ui.platform.LocalContext.current

    // 갤러리에서 이미지 선택 → OCR 자동 채우기
    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isOcrLoading = true
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes != null) {
                runCatching {
                    val requestFile = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("image", "receipt.jpg", requestFile)
                    com.synctrip.app.network.ApiClient.api.scanReceipt(bandId, part)
                }.onSuccess { result ->
                    if (itemName.isBlank()) itemName = result.storeName ?: ""
                    if (amountText.isBlank()) amountText = result.total?.toString() ?: ""
                    if (result.currency != null) currency = result.currency
                }
            }
            isOcrLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("지출 추가", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                OutlinedButton(
                    onClick  = { imageLauncher.launch("image/*") },
                    enabled  = !isOcrLoading,
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    if (isOcrLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("스캔 중…")
                    } else {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("영수증 스캔")
                    }
                }
            }

            OutlinedTextField(
                value         = itemName,
                onValueChange = { itemName = it },
                label         = { Text("항목명") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            // 결제자 선택 드롭다운
            val selectedPayerName = members.firstOrNull { it.userId == selectedPayerId }?.name ?: ""
            ExposedDropdownMenuBox(
                expanded         = payerDropdownExpanded,
                onExpandedChange = { payerDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value         = selectedPayerName + if (selectedPayerId == currentUserId) " (나)" else "",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("결제자") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerDropdownExpanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded         = payerDropdownExpanded,
                    onDismissRequest = { payerDropdownExpanded = false },
                ) {
                    members.forEach { member ->
                        DropdownMenuItem(
                            text    = { Text(member.name + if (member.userId == currentUserId) " (나)" else "") },
                            onClick = {
                                selectedPayerId = member.userId
                                payerDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label         = { Text("금액") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value         = currency,
                    onValueChange = { if (it.length <= 3) currency = it.uppercase() },
                    label         = { Text("통화") },
                    singleLine    = true,
                    modifier      = Modifier.width(90.dp),
                )
            }

            Text("분담자", style = MaterialTheme.typography.titleMedium)
            members.forEach { member ->
                Row(
                    verticalAlignment  = Alignment.CenterVertically,
                    modifier           = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked         = selectedIds.contains(member.userId),
                        onCheckedChange = { checked ->
                            if (checked) selectedIds.add(member.userId) else selectedIds.remove(member.userId)
                        },
                    )
                    Text(
                        member.name + if (member.userId == currentUserId) " (나)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick  = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    if (itemName.isBlank()) return@Button
                    onConfirm(itemName.trim(), amount, currency.ifBlank { "KRW" }, selectedPayerId, selectedIds.toList())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = itemName.isNotBlank() && amountText.isNotBlank() && !isOcrLoading,
            ) {
                Text("추가")
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Previews
// ═════════════════════════════════════════════════════════════════════════════

private val previewVotePlaces = listOf(
    VotePlaceResponse(
        placeId      = 1L,
        apiSource    = PlaceApiSource.GOOGLE,
        name         = "카페 드 랑브르",
        category     = ApiPlaceCategory.FOOD,
        latitude     = 35.0,
        longitude    = 135.0,
        address      = "일본 교토시 시모교구",
        rating       = 4.8f,
        thumbnailUrl = null,
        myBookmark   = false,
        myVoteResult = null,
    ),
    VotePlaceResponse(
        placeId      = 2L,
        apiSource    = PlaceApiSource.GOOGLE,
        name         = "아라시야마 대나무 숲",
        category     = ApiPlaceCategory.NATURE,
        latitude     = 35.0,
        longitude    = 135.6,
        address      = "일본 교토시 니시쿄구",
        rating       = 4.6f,
        thumbnailUrl = null,
        myBookmark   = false,
        myVoteResult = null,
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SwipeVotingPreview() {
    SynctripTheme {
        SwipeVotingScreen(
            pendingPlaces    = previewVotePlaces,
            votedCount       = 2,
            isMyVoteComplete = false,
            isAllComplete    = false,
            isLoading        = false,
            onVote           = { _, _ -> },
            onBackClick      = {},
            onVotingDone     = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SwipeVotingWaitingPreview() {
    SynctripTheme {
        SwipeVotingScreen(
            pendingPlaces    = emptyList(),
            votedCount       = 4,
            isMyVoteComplete = true,
            isAllComplete    = false,
            isLoading        = false,
            onVote           = { _, _ -> },
            onBackClick      = {},
            onVotingDone     = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SwipeVotingAllCompletePreview() {
    SynctripTheme {
        SwipeVotingScreen(
            pendingPlaces    = emptyList(),
            votedCount       = 4,
            isMyVoteComplete = true,
            isAllComplete    = true,
            isLoading        = false,
            onVote           = { _, _ -> },
            onBackClick      = {},
            onVotingDone     = {},
        )
    }
}

private val previewSession = VotingSession(
    sessionId  = "vs1",
    tripId     = "t1",
    title      = "장소 블라인드 투표",
    deadline   = "2024-08-01 23:59",
    candidates = listOf(
        VoteCandidate("c1", "장소 후보 A", null, "시부야역 도보 5분, 조식 포함", 120_000L, 3, 0.6f),
        VoteCandidate("c2", "장소 후보 B", null, "신주쿠 뷰, 온천 포함", 95_000L, 1, 0.2f),
    ),
    totalVotes = 5, hasVoted = true, myVoteId = "c1",
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BlindVotingPreview() {
    SynctripTheme {
        BlindVotingScreen(session = previewSession, onVote = {}, onBackClick = {})
    }
}

private val previewSettlement = Settlement(
    tripId    = "t1",
    tripTitle = "제주도 여행 정산",
    totalAmount = 1_450_000L,
    currency    = "KRW",
    myBalance   = -45_000L,
    summary     = listOf(
        SettlementItem("s1", "숙박", "제주 호텔 2박", 280_000L, "Alex"),
        SettlementItem("s2", "식사", "흑돼지 저녁", 120_000L, "Jamie"),
    ),
    pendingTransfers = listOf(PendingTransfer("나", "Alex", toUserId = 2L, 45_000L, false)),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettlementPreview() {
    SynctripTheme {
        SettlementScreen(settlement = previewSettlement, currentUserId = 1L, onSettleClick = {}, onBackClick = {})
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. Vote Result Screen  ← 투표 결과 화면
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 투표 결과 화면.
 * 장소별 좋아요/싫어요 집계와 통과/탈락 여부를 표시한다.
 * 좋아요 많은 순으로 정렬되며, 통과 장소는 강조 표시된다.
 * "일정 만들기" 버튼 클릭 시 AI 일정 생성 화면으로 이동한다.
 *
 * @param results          투표 결과 목록 (서버에서 likeCount 내림차순 정렬됨)
 * @param isLoading        결과 로딩 중 여부
 * @param onCreateSchedule 일정 만들기 버튼 콜백
 * @param onBackClick      뒤로가기
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteResultScreen(
    results: List<VotePlaceResult>,
    isLoading: Boolean,
    onCreateSchedule: () -> Unit,
    onBackClick: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val passedCount = results.count { it.passed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("투표 결과", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick  = onCreateSchedule,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("일정 만들기", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            modifier     = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (isLoading) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaneLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 요약 헤더
                    item {
                        Text(
                            text  = "총 ${results.size}개 중 ${passedCount}개 통과",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    items(results, key = { it.placeId }) { result ->
                        VoteResultCard(result = result)
                    }
                }
            }
        }
    }
}

/** 투표 결과 카드 — 장소 썸네일, 이름, 좋아요/싫어요 수, 통과 여부 표시 */
@Composable
private fun VoteResultCard(result: VotePlaceResult) {
    val passColor = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val passLabel = if (result.passed) "통과" else "탈락"

    Card(
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (result.passed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier            = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 썸네일
            AsyncImage(
                model             = result.thumbnailUrl,
                contentDescription = result.name,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            // 장소 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = result.name,
                    style    = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = result.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 좋아요 카운트
                    Row(
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.FavoriteBorder,
                            contentDescription = "좋아요",
                            tint               = Color(0xFFE91E63),
                            modifier           = Modifier.size(14.dp),
                        )
                        Text(
                            text  = "${result.likeCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE91E63),
                        )
                    }
                    // 싫어요 카운트
                    Row(
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.Close,
                            contentDescription = "싫어요",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp),
                        )
                        Text(
                            text  = "${result.dislikeCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 통과/탈락 배지
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = passColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text     = passLabel,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = passColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
