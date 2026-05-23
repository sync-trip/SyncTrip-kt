package com.synctrip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.TripBand
import com.synctrip.app.ui.theme.*

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
                AsyncImage(
                    model              = band.heroImageUrl,
                    contentDescription = band.destination,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
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
