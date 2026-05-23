package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.theme.SynctripTheme

@Composable
fun TripLobbyScreen(
    lobby: TripLobby,
    onBackClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onInviteClick: () -> Unit,
    onStartAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier  = modifier,
        topBar    = {
            LobbyTopBar(title = lobby.destination, onBackClick = onBackClick)
        },
        bottomBar = {
            LobbyBottomBar(
                isReadyToGenerate = lobby.overallProgress >= 60,
                onStartAi         = onStartAi,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            LobbyHeroSection(lobby = lobby)

            Spacer(Modifier.height(20.dp))

            FriendsSection(
                members       = lobby.members,
                onInviteClick = onInviteClick,
                modifier      = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(24.dp))

            OverallProgressSection(
                progress = lobby.overallProgress,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text     = "준비 현황",
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            LobbyTaskGrid(
                tasks       = lobby.tasks,
                onTaskClick = onTaskClick,
                modifier    = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

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
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "더보기") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun LobbyHeroSection(lobby: TripLobby) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
    ) {
        AsyncImage(
            model              = lobby.heroImageUrl,
            contentDescription = lobby.destination,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.2f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text  = lobby.destination,
                style = MaterialTheme.typography.displayLarge.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text  = lobby.dateRange,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.85f),
                ),
            )
        }
    }
}

@Composable
private fun FriendsSection(
    members: List<TripMember>,
    onInviteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("함께하는 친구들", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            members.forEach { member ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                contentDescription = member.nickname,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text  = member.nickname.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = member.nickname, style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.clickable(
                    onClick           = onInviteClick,
                    indication        = null,
                    interactionSource = MutableInteractionSource(),
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PersonAdd, "초대", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("초대", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun OverallProgressSection(progress: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        ),
        border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("전체 준비 현황", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { progress / 100f },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun LobbyTaskGrid(tasks: List<LobbyTask>, onTaskClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tasks.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { task ->
                    TaskCard(task = task, onClick = { onTaskClick(task.id) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TaskCard(task: LobbyTask, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val taskIcon = when (task.taskType) {
        LobbyTaskType.VOTING     -> Icons.Outlined.HowToVote
        LobbyTaskType.BOOKING    -> Icons.Outlined.Hotel
        LobbyTaskType.SCHEDULE   -> Icons.Outlined.DateRange
        LobbyTaskType.SETTLEMENT -> Icons.Outlined.Payments
        LobbyTaskType.OTHER      -> Icons.Outlined.CheckCircle
    }

    Card(
        onClick   = onClick,
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (task.isComplete) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector        = taskIcon,
                contentDescription = null,
                tint               = if (task.isComplete) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { task.progress / 100f },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color      = if (task.isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Spacer(Modifier.height(4.dp))
            Text("${task.progress}% 완료", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun LobbyBottomBar(isReadyToGenerate: Boolean, onStartAi: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick  = onStartAi,
                enabled  = isReadyToGenerate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    contentColor           = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (isReadyToGenerate) "AI로 일정 생성하기" else "준비가 더 필요해요",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

private val previewLobby = TripLobby(
    tripId      = "t1",
    destination = "도쿄, 일본",
    heroImageUrl = "",
    dateRange   = "2024.09.03 ~ 09.08",
    members     = listOf(TripMember("u1", "Alex", null), TripMember("u2", "Jamie", null), TripMember("u3", "Sam", null)),
    tasks = listOf(
        LobbyTask("ta1", "숙소 투표", 66, false, LobbyTaskType.VOTING),
        LobbyTask("ta2", "항공권 예약", 100, true, LobbyTaskType.BOOKING),
        LobbyTask("ta3", "일정 확정", 30, false, LobbyTaskType.SCHEDULE),
        LobbyTask("ta4", "정산 준비", 0, false, LobbyTaskType.SETTLEMENT),
    ),
    overallProgress = 66,
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripLobbyScreenPreview() {
    SynctripTheme {
        TripLobbyScreen(lobby = previewLobby, onBackClick = {}, onTaskClick = {}, onInviteClick = {}, onStartAi = {})
    }
}
