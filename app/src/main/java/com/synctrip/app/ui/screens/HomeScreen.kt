package com.synctrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.synctrip.app.ui.components.*
import com.synctrip.app.ui.theme.SynctripTheme

@Composable
fun HomeScreen(
    recommendedContent: List<RecommendedContent>,
    myTripBands: List<TripBand>,
    selectedNavItem: BottomNavDestination,
    onNavItemSelected: (BottomNavDestination) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContentCardClick: (String) -> Unit,
    onTripBandClick: (String) -> Unit,
    onCreateTripClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier     = modifier,
        topBar       = {
            HomeTopAppBar(
                onMenuClick            = onMenuClick,
                onSearchClick          = onSearchClick,
                onNotificationsClick   = onNotificationsClick,
                hasUnreadNotifications = hasUnreadNotifications,
            )
        },
        bottomBar    = {
            SyncTripBottomNav(
                selectedDestination   = selectedNavItem,
                onDestinationSelected = onNavItemSelected,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onCreateTripClick,
                icon           = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text           = { Text("새 여행") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                elevation      = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text     = "SyncTrip",
                style    = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(8.dp))

            SectionHeader(
                title    = "추천 여행지",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(
                contentPadding        = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recommendedContent, key = { it.id }) { content ->
                    RecommendedCard(
                        content  = content,
                        onClick  = { onContentCardClick(content.id) },
                        modifier = Modifier.width(256.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader(
                title    = "내 여행 밴드",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            if (myTripBands.isEmpty()) {
                EmptyTripsPlaceholder(
                    onCreateTripClick = onCreateTripClick,
                    modifier          = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(myTripBands, key = { it.id }) { band ->
                        TripTicketCard(
                            band    = band,
                            onClick = { onTripBandClick(band.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    hasUnreadNotifications: Boolean,
) {
    TopAppBar(
        title = {
            Text(
                text  = "SyncTrip",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Outlined.Menu, "메뉴", tint = MaterialTheme.colorScheme.primary)
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Outlined.Search, "검색", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = onNotificationsClick) {
                    Icon(Icons.Outlined.Notifications, "알림", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasUnreadNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, shape = RoundedCornerShape(50)),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun RecommendedCard(
    content: RecommendedContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model              = content.imageUrl,
            contentDescription = content.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.4f to Color.Transparent,
                        1f   to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        )
        Text(
            text     = content.title,
            style    = MaterialTheme.typography.titleLarge.copy(
                color      = Color.White,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun EmptyTripsPlaceholder(
    onCreateTripClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CardTravel, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("아직 진행 중인 여행이 없어요", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCreateTripClick) { Text("새 여행 만들기") }
        }
    }
}

private val previewBands = listOf(
    TripBand("1", "제주도", "", "Aug 12", "Aug 18", TripStatus.PLANNING, listOf(TripMember("u1", "Alex", null), TripMember("u2", "Jamie", null)), 45),
    TripBand("2", "도쿄", "", "Sep 3", "Sep 8", TripStatus.PLANNING, listOf(TripMember("u3", "Sam", null)), 20),
)

private val previewContent = listOf(
    RecommendedContent("c1", "교토의 자연과 산책", "", "NATURE", "Kyoto"),
    RecommendedContent("c2", "Canal City 하카타", "", "URBAN", "Fukuoka"),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    SynctripTheme {
        HomeScreen(
            recommendedContent   = previewContent,
            myTripBands          = previewBands,
            selectedNavItem      = BottomNavDestination.Home,
            onNavItemSelected    = {},
            onMenuClick          = {},
            onSearchClick        = {},
            onNotificationsClick = {},
            onContentCardClick   = {},
            onTripBandClick      = {},
            onCreateTripClick    = {},
            hasUnreadNotifications = true,
        )
    }
}
