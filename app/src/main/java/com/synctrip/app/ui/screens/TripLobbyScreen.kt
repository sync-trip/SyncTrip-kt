package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.theme.SynctripTheme

/**
 * 여행 로비 화면.
 * 밴드 상태(PLANNING→VOTING→GENERATING→TRAVELLING→DONE)에 따라
 * 다른 액션 버튼과 정보를 표시한다.
 */
@Composable
fun TripLobbyScreen(
    band: BandResponse,
    members: List<BandMemberResponse>,
    picks: PlacePickListResponse?,          // null = 로드 전
    inviteCode: BandInviteCodeResponse?,
    currentUserId: Long,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onReadyClick: () -> Unit,
    onGetInviteCodeClick: () -> Unit,
    onShareInviteCode: (String) -> Unit,
    onAdvanceStatus: () -> Unit,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onGoToSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 현재 유저의 멤버 정보 — ready 상태 확인에 사용
    val currentMember = members.find { it.userId == currentUserId }
    val readyCount    = members.count { it.isReady }
    val allReady      = readyCount == members.size && members.isNotEmpty()

    // 방장이 투표 시작 시 미ready 멤버가 있으면 경고 다이얼로그 표시
    var showAdvanceDialog by remember { mutableStateOf(false) }

    if (showAdvanceDialog) {
        val notReadyCount = members.count { !it.isReady }
        AlertDialog(
            onDismissRequest = { showAdvanceDialog = false },
            title            = { Text("투표 시작") },
            text             = {
                if (notReadyCount > 0)
                    Text("아직 준비 안 된 멤버가 ${notReadyCount}명 있어요. 그래도 투표를 시작할까요?")
                else
                    Text("모든 멤버가 준비됐어요. 투표를 시작할게요!")
            },
            confirmButton = {
                TextButton(onClick = { showAdvanceDialog = false; onAdvanceStatus() }) {
                    Text("시작", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvanceDialog = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        modifier  = modifier,
        topBar    = { LobbyTopBar(title = band.destination, onBackClick = onBackClick) },
        bottomBar = {
            LobbyBottomBar(
                status             = band.status,
                isOwner            = band.isOwner,
                isLoading          = isLoading,
                onGoToPlaceSearch  = onGoToPlaceSearch,
                onGoToVoting       = onGoToVoting,
                onGoToSchedule     = onGoToSchedule,
                onAdvanceStatusClick = { showAdvanceDialog = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 밴드 기본 정보 카드 ───────────────────────────────────────────
            LobbyInfoCard(band = band, readyCount = readyCount, totalCount = members.size)

            // ── 멤버 목록 ─────────────────────────────────────────────────────
            MembersSection(
                members       = members,
                onInviteClick = onGetInviteCodeClick,
            )

            // ── 초대 코드 섹션 (PLANNING 단계만) ──────────────────────────────
            if (band.status == BandStatus.PLANNING) {
                InviteCodeSection(
                    inviteCode   = inviteCode,
                    onGetCode    = onGetInviteCodeClick,
                    onShare      = onShareInviteCode,
                )
            }

            // ── 내 준비 현황 (PLANNING 단계만) ────────────────────────────────
            if (band.status == BandStatus.PLANNING) {
                MyStatusSection(
                    picks          = picks,
                    isReady        = currentMember?.isReady ?: false,
                    onReadyClick   = onReadyClick,
                    onSearchClick  = onGoToPlaceSearch,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopAppBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyTopBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text  = title,
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
}

// ─────────────────────────────────────────────────────────────────────────────
// 밴드 정보 카드
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyInfoCard(band: BandResponse, readyCount: Int, totalCount: Int) {
    val statusLabel = when (band.status) {
        BandStatus.PLANNING   -> "준비 중"
        BandStatus.VOTING     -> "투표 중"
        BandStatus.GENERATING -> "일정 생성 중"
        BandStatus.TRAVELLING -> "여행 중"
        BandStatus.DONE       -> "여행 완료"
    }
    val statusColor = when (band.status) {
        BandStatus.PLANNING   -> MaterialTheme.colorScheme.primary
        BandStatus.VOTING     -> MaterialTheme.colorScheme.tertiary
        BandStatus.GENERATING -> MaterialTheme.colorScheme.secondary
        BandStatus.TRAVELLING -> MaterialTheme.colorScheme.error
        BandStatus.DONE       -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = band.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text     = statusLabel,
                        style    = MaterialTheme.typography.labelMedium.copy(color = statusColor, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip(icon = Icons.Outlined.CalendarMonth, text = "${band.startDate} ~ ${band.endDate}")
                InfoChip(
                    icon = Icons.Outlined.DirectionsWalk,
                    text = if (band.travelStyle == BandTravelStyle.RELAXED) "여유롭게" else "알차게",
                )
            }

            // 준비 현황 바
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("준비 완료", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(
                        "$readyCount / $totalCount 명",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                    )
                }
                LinearProgressIndicator(
                    progress   = { if (totalCount > 0) readyCount.toFloat() / totalCount else 0f },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 멤버 섹션
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MembersSection(
    members: List<BandMemberResponse>,
    onInviteClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("함께하는 멤버", style = MaterialTheme.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            members.forEach { member ->
                MemberAvatar(member = member)
            }
            // 초대 버튼
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(horizontal = 2.dp),
            ) {
                FilledIconButton(
                    onClick  = onInviteClick,
                    modifier = Modifier.size(48.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor   = MaterialTheme.colorScheme.primary,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Outlined.PersonAdd, "초대", modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("초대", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: BandMemberResponse) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp)) {
            // 프로필 아이콘
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (member.profileImageUrl != null) {
                    AsyncImage(
                        model              = member.profileImageUrl,
                        contentDescription = member.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text  = member.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            // ready 상태 뱃지
            if (member.isReady) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text  = member.name.let { if (it.length > 5) it.take(4) + "…" else it },
            style = MaterialTheme.typography.labelSmall,
        )
        // 장바구니 담은 수
        Text(
            text  = "🗂 ${member.bookmarkCount}",
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 초대 코드 섹션
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InviteCodeSection(
    inviteCode: BandInviteCodeResponse?,
    onGetCode: () -> Unit,
    onShare: (String) -> Unit,
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Share, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Text("초대 코드", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }

            if (inviteCode != null) {
                // 코드 표시
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = inviteCode.inviteCode,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        IconButton(onClick = { onShare(inviteCode.inviteCode) }) {
                            Icon(Icons.Outlined.ContentCopy, "복사", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OutlinedButton(
                    onClick  = { onShare(inviteCode.inviteCode) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("카카오·링크로 공유")
                }
            } else {
                Text(
                    text  = "초대 코드를 발급해서 친구를 초대하세요",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Button(
                    onClick  = onGetCode,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("초대 코드 발급")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 내 준비 현황 섹션
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyStatusSection(
    picks: PlacePickListResponse?,
    isReady: Boolean,
    onReadyClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.PendingActions,
                    null,
                    tint     = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text  = if (isReady) "준비 완료!" else "내 준비 현황",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // 장바구니 현황
            val count    = picks?.currentCount ?: 0
            val maxCount = picks?.maxCount ?: 5
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "장소 ${count}개 / ${maxCount}개 담음",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                if (count >= maxCount) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            "최대",
                            style    = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (!isReady) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onSearchClick,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("장소 탐색")
                    }
                    // 장바구니 1개 이상이어야 Ready 가능 (인수인계 문서 §4 조건)
                    Button(
                        onClick  = onReadyClick,
                        enabled  = count >= 1,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("준비 완료")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 하단 액션 바 — 밴드 상태에 따라 다른 버튼 표시
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyBottomBar(
    status: BandStatus,
    isOwner: Boolean,
    isLoading: Boolean,
    onGoToPlaceSearch: () -> Unit,
    onGoToVoting: () -> Unit,
    onGoToSchedule: () -> Unit,
    onAdvanceStatusClick: () -> Unit,
) {
    // Surface가 nav bar 뒤까지 배경색을 채우고, Column은 navigationBarsPadding으로 버튼을 nav bar 위에 위치시킴
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier            = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                BandStatus.PLANNING -> {
                    Button(
                        onClick  = onGoToPlaceSearch,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("장소 탐색하기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    // 방장만 투표 강제 시작 가능
                    if (isOwner) {
                        OutlinedButton(
                            onClick  = onAdvanceStatusClick,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.HowToVote, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("투표 시작하기")
                        }
                    }
                }
                BandStatus.VOTING -> {
                    Button(
                        onClick  = onGoToVoting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.HowToVote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("투표하러 가기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                BandStatus.GENERATING -> {
                    Button(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text("AI 일정 생성 중…")
                    }
                }
                BandStatus.TRAVELLING, BandStatus.DONE -> {
                    Button(
                        onClick  = onGoToSchedule,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.DateRange, null)
                        Spacer(Modifier.width(8.dp))
                        Text("일정 보기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripLobbyScreenPreview() {
    val previewBand = BandResponse(
        id               = 1L,
        name             = "도쿄 여행",
        destination      = "도쿄",
        startDate        = "2024-09-03",
        endDate          = "2024-09-08",
        inviteCode       = "SYNC1234",
        status           = BandStatus.PLANNING,
        isOwner          = true,
        isOverseas       = true,
        travelStyle      = BandTravelStyle.RELAXED,
        accommodationName = null,
        memberCount      = 2,
    )
    val previewMembers = listOf(
        BandMemberResponse(1L, "Alex", null, BandRole.OWNER, true,  "2024-09-01", 3),
        BandMemberResponse(2L, "Jamie", null, BandRole.MEMBER, false, "2024-09-01", 1),
    )
    val previewPicks = PlacePickListResponse(currentCount = 3, maxCount = 5, items = emptyList())
    SynctripTheme {
        TripLobbyScreen(
            band             = previewBand,
            members          = previewMembers,
            picks            = previewPicks,
            inviteCode       = BandInviteCodeResponse(1L, "SYNC1234", "2024-09-02T00:00:00", null, null),
            currentUserId    = 1L,
            isLoading        = false,
            onBackClick      = {},
            onReadyClick     = {},
            onGetInviteCodeClick = {},
            onShareInviteCode = {},
            onAdvanceStatus  = {},
            onGoToPlaceSearch = {},
            onGoToVoting     = {},
            onGoToSchedule   = {},
        )
    }
}
