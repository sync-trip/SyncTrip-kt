package com.synctrip.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.theme.SynctripTheme
import java.text.NumberFormat
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// 1. Blind Voting Screen
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
// 2. Settlement Screen
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

private val previewSession = VotingSession(
    sessionId  = "vs1",
    tripId     = "t1",
    title      = "숙소 블라인드 투표",
    deadline   = "2024-08-01 23:59",
    candidates = listOf(
        VoteCandidate("c1", "숙소 후보 A", null, "시부야역 도보 5분, 조식 포함", 120_000L, 3, 0.6f),
        VoteCandidate("c2", "숙소 후보 B", null, "신주쿠 뷰, 온천 포함", 95_000L, 1, 0.2f),
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
