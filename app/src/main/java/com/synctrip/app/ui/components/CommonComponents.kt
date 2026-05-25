package com.synctrip.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.TripBand
import com.synctrip.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Plane Loading Indicator — 앱 전역 로딩 표시 (CircularProgressIndicator 대체)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 비행기가 점선 원 위를 공전하는 커스텀 로딩 인디케이터.
 * 구 앱 PlaneLoadingView와 동일한 방식: 삼각함수로 아이콘 위치를 계산해 원 위에 정확히 배치.
 *
 * @param size       인디케이터 전체 크기 (기본 80dp)
 * @param showCircle 점선 원 표시 여부 — 버튼처럼 좁은 공간에서는 false로 비행기만 표시
 */
@Composable
fun PlaneLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showCircle: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    val angle by rememberInfiniteTransition(label = "plane-orbit").animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "plane-angle",
    )

    // 구 앱 PlaneLoadingView와 동일: iconHalf * 1.5 만큼 안쪽으로 궤도 설정
    val iconSizeDp = if (showCircle) 22.dp else size * 0.55f
    val density    = LocalDensity.current
    // 아이콘 중심이 놓일 궤도 반지름 (px)
    val orbitRadius = with(density) { size.toPx() / 2f - iconSizeDp.toPx() / 2f * 1.5f }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // 점선 원 — 궤도 반지름과 동일한 위치에 그려서 비행기가 정확히 원 위를 돎
        if (showCircle) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val dashPx = 7.dp.toPx()
                drawCircle(
                    color  = color.copy(alpha = 0.35f),
                    radius = orbitRadius,
                    style  = Stroke(
                        width      = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx), 0f),
                        cap        = StrokeCap.Round,
                    ),
                )
            }
        }

        // 구 앱 동일 방식: cos/sin으로 위치 계산 + graphicsLayer로 정밀 배치
        // Icons.Filled.Flight는 동쪽(→)을 향하므로 접선 방향 = angle + 90°
        Icon(
            imageVector        = Icons.Filled.Flight,
            contentDescription = null,
            tint               = color,
            modifier           = Modifier
                .size(iconSizeDp)
                .graphicsLayer {
                    val rad = Math.toRadians((angle - 90.0))
                    translationX = (orbitRadius * cos(rad)).toFloat()
                    translationY = (orbitRadius * sin(rad)).toFloat()
                    rotationZ    = angle + 100f
                },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

enum class BottomNavDestination(
    val icon: ImageVector,
    val label: String,
) {
    Home(Icons.Outlined.Home, "홈"),
    MyTrips(Icons.Outlined.CardTravel, "내 여행"),
    Explore(Icons.Outlined.Explore, "탐색"),
    Passport(Icons.Outlined.BookmarkBorder, "여권"),
    Profile(Icons.Outlined.Person, "프로필"),
}

@Composable
fun SyncTripBottomNav(
    selectedDestination: BottomNavDestination,
    onDestinationSelected: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        BottomNavDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick  = { onDestinationSelected(destination) },
                icon     = {
                    Icon(
                        imageVector        = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label    = {
                    Text(
                        text  = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor       = MaterialTheme.colorScheme.primary,
                    selectedTextColor       = MaterialTheme.colorScheme.primary,
                    indicatorColor          = MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = 0.2f),
                    unselectedIconColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ticket Card  (My Trip Bands)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TripTicketCard(
    band: TripBand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.width(300.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                if (band.heroImageUrl.isNotBlank()) {
                    AsyncImage(
                        model              = band.heroImageUrl,
                        contentDescription = band.destination,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    // 서버에서 이미지 URL을 내려주지 않을 때 여행지 이름 기반 그라디언트 플레이스홀더
                    val gradientColors = destinationGradient(band.destination)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Flight,
                            contentDescription = null,
                            tint               = Color.White.copy(alpha = 0.5f),
                            modifier           = Modifier.size(48.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            ),
                        ),
                )
            }

            TicketDivider()

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text  = "DESTINATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color         = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        fontWeight    = FontWeight.Medium,
                    ),
                )
                Text(
                    text     = band.destination,
                    style    = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text  = "DATE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Text(
                            text  = "${band.startDate} – ${band.endDate}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }

                    MemberAvatarStack(
                        members    = band.members,
                        maxVisible = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketDivider() {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .offset(x = (-6).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
        )
        DashedHorizontalDivider(
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .offset(x = 6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun DashedHorizontalDivider(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(24) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(1.dp)
                    .background(color),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member avatar stack
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberAvatarStack(
    members: List<com.synctrip.app.data.models.TripMember>,
    maxVisible: Int = 3,
    modifier: Modifier = Modifier,
) {
    val visible  = members.take(maxVisible)
    val overflow = members.size - maxVisible

    Row(modifier = modifier) {
        visible.forEachIndexed { index, member ->
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                if (member.profileImageUrl != null) {
                    AsyncImage(
                        model              = member.profileImageUrl,
                        contentDescription = member.nickname,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = member.nickname.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }

        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (-8 * visible.size).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "+$overflow",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
        action?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 내부 유틸
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 여행지 이름의 해시값을 이용해 일관된 그라디언트 색상 쌍을 반환.
 * 서버에서 이미지 URL이 없을 때 카드 배경으로 사용한다.
 */
private fun destinationGradient(destination: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF1A6B5A), Color(0xFF0D3B30)),
        listOf(Color(0xFF1B4B8A), Color(0xFF0A2550)),
        listOf(Color(0xFF6B2D6B), Color(0xFF3B0D3B)),
        listOf(Color(0xFF8A4B1B), Color(0xFF502A0A)),
        listOf(Color(0xFF1B6B8A), Color(0xFF0A3B50)),
        listOf(Color(0xFF5A1B6B), Color(0xFF2D0A3B)),
    )
    val index = Math.abs(destination.hashCode()) % palettes.size
    return palettes[index]
}
