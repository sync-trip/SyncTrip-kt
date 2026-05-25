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
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.PlaneLoadingIndicator
import com.synctrip.app.ui.theme.SynctripTheme
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// 1. Swipe Voting Screen  ← 메인 투표 화면 (스와이프 카드 방식)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * 스와이프 투표 화면.
 * 장소 카드를 한 장씩 보여주며 좋아요(♥) / 싫어요(✗) 버튼으로 투표한다.
 * 모든 장소에 투표가 끝나면 자동으로 onVotingDone이 호출된다.
 *
 * @param pendingPlaces    아직 투표하지 않은 장소 목록
 * @param votedCount       이미 투표한 장소 수 (진행 표시용)
 * @param isMyVoteComplete 내 투표가 완전히 끝났는지 여부
 * @param isLoading        장소 목록 로딩 중 여부
 * @param onVote           투표 콜백 (placeId, result: 1=좋아요/-1=싫어요)
 * @param onBackClick      뒤로가기
 * @param onVotingDone     모든 투표 완료 후 호출 → 일정 생성 화면으로 이동
 */
@Composable
fun SwipeVotingScreen(
    pendingPlaces: List<VotePlaceResponse>,
    votedCount: Int,
    isMyVoteComplete: Boolean,
    isLoading: Boolean,
    onVote: (placeId: Long, result: Int) -> Unit,
    onBackClick: () -> Unit,
    onVotingDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount   = votedCount + pendingPlaces.size
    val currentPlace = pendingPlaces.firstOrNull()
    val scope        = rememberCoroutineScope()

    // 카드 수평 이탈 애니메이션 값
    val cardOffsetX  = remember { Animatable(0f) }

    // 새 카드 진입 시 위치 초기화
    LaunchedEffect(currentPlace?.placeId) {
        cardOffsetX.snapTo(0f)
    }

    // 모든 투표 완료 → 잠깐 완료 UI 표시 후 자동 이동
    LaunchedEffect(isMyVoteComplete) {
        if (isMyVoteComplete) {
            delay(1200)
            onVotingDone()
        }
    }

    /** 버튼 클릭 → 카드 날리기 애니메이션 → 투표 제출 */
    fun vote(result: Int) {
        val place = currentPlace ?: return
        scope.launch {
            val target = if (result == 1) 1400f else -1400f
            cardOffsetX.animateTo(target, tween(270, easing = FastOutLinearInEasing))
            onVote(place.placeId, result)
        }
    }

    Scaffold(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
                    Icon(
                        Icons.Outlined.HowToVote,
                        null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp),
                    )
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

                // 모든 투표 완료
                isMyVoteComplete -> Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) { VotingCompleteContent() }

                // 투표 중: 카드 + 액션 버튼
                currentPlace != null -> {
                    // 카드 영역 — 이탈 방향에 따라 기울어짐
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = cardOffsetX.value
                                rotationZ    = cardOffsetX.value / 30f
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

/** 모든 투표 완료 후 보여주는 완료 콘텐츠 */
@Composable
private fun VotingCompleteContent() {
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
            "일정 생성 화면으로 이동합니다...",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
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
                TransferCard(transfer = transfer, onSettleClick = onSettleClick)
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
private fun TransferCard(transfer: PendingTransfer, onSettleClick: (String) -> Unit) {
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
            if (!transfer.isResolved) {
                OutlinedButton(onClick = { onSettleClick("") }, shape = RoundedCornerShape(8.dp)) { Text("정산 완료") }
            } else {
                Icon(Icons.Outlined.CheckCircle, "완료", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
 * SettlementScreen과 동일한 목록 UI지만 Scaffold 없이 콘텐츠만 포함한다.
 * TripBandHubScreen의 정산 탭에서 호출한다.
 */
@Composable
internal fun SettlementContent(
    settlement: Settlement?,
    onSettleClick: (transferId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 데이터 미로드 시 중앙 로딩 스피너
    if (settlement == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlaneLoadingIndicator()
        }
        return
    }

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                settlement.tripTitle,
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            )
        }
        item { TotalAmountCard(settlement = settlement) }
        item { MyBalanceCard(balance = settlement.myBalance, currency = settlement.currency) }
        item {
            Text("정산 현황", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface))
        }
        items(settlement.pendingTransfers) { transfer ->
            TransferCard(transfer = transfer, onSettleClick = onSettleClick)
        }
        item {
            Text("상세 지출 내역", style = MaterialTheme.typography.titleLarge)
        }
        items(settlement.summary) { item ->
            ExpenseItemRow(item = item)
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
            isLoading        = false,
            onVote           = { _, _ -> },
            onBackClick      = {},
            onVotingDone     = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SwipeVotingCompletePreview() {
    SynctripTheme {
        SwipeVotingScreen(
            pendingPlaces    = emptyList(),
            votedCount       = 4,
            isMyVoteComplete = true,
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
    pendingTransfers = listOf(PendingTransfer("나", "Alex", 45_000L, false)),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettlementPreview() {
    SynctripTheme {
        SettlementScreen(settlement = previewSettlement, onSettleClick = {}, onBackClick = {})
    }
}
