package com.synctrip.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.components.BottomNavDestination
import com.synctrip.app.ui.screens.*

@Composable
fun SyncTripNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
            )
        }

        composable("login") {
            LoginScreen(
                onKakaoLogin  = { /* TODO: call KakaoAuthManager then navigate */ },
                onGoogleLogin = { /* TODO: call GoogleAuthManager then navigate */ },
                onEmailLogin  = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }

        composable("home") {
            var selectedNavItem by remember { mutableStateOf(BottomNavDestination.Home) }
            HomeScreen(
                recommendedContent   = emptyList(),
                myTripBands          = emptyList(),
                selectedNavItem      = selectedNavItem,
                onNavItemSelected    = { dest ->
                    selectedNavItem = dest
                    when (dest) {
                        BottomNavDestination.Explore  -> navController.navigate("placeSearch")
                        BottomNavDestination.Passport -> navController.navigate("passport")
                        else                          -> {}
                    }
                },
                onMenuClick          = {},
                onSearchClick        = {},
                onNotificationsClick = { navController.navigate("notifications") },
                onContentCardClick   = {},
                onTripBandClick      = { bandId -> navController.navigate("tripLobby/$bandId") },
                onCreateTripClick    = { navController.navigate("createTrip") },
            )
        }

        composable("createTrip") {
            var destination    by remember { mutableStateOf("") }
            var startDate      by remember { mutableStateOf("") }
            var endDate        by remember { mutableStateOf("") }
            var selectedStyles by remember { mutableStateOf(emptySet<TravelStyle>()) }

            CreateTripScreen(
                suggestions         = listOf(
                    DestinationSuggestion("도쿄", "일본",  "", "TYO"),
                    DestinationSuggestion("파리", "프랑스", "", "CDG"),
                    DestinationSuggestion("뉴욕", "미국",  "", "JFK"),
                    DestinationSuggestion("제주", "한국",  "", "CJU"),
                ),
                destination         = destination,
                onDestinationChange = { destination = it },
                startDate           = startDate,
                onStartDateChange   = { startDate = it },
                endDate             = endDate,
                onEndDateChange     = { endDate = it },
                selectedStyles      = selectedStyles,
                onStyleToggle       = { style ->
                    selectedStyles = if (style in selectedStyles)
                        selectedStyles - style else selectedStyles + style
                },
                onCreateTrip        = { navController.navigate("aiLoading") },
                onBackClick         = { navController.popBackStack() },
            )
        }

        composable("aiLoading") {
            AiLoadingScreen(
                status     = AiGenerationStatus("job1", 0, "일정 생성 중…", false),
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
            )
        }

        composable("tripLobby/{bandId}") { backStackEntry ->
            val bandId = backStackEntry.arguments?.getString("bandId") ?: ""
            TripLobbyScreen(
                lobby = TripLobby(
                    tripId          = bandId,
                    destination     = "여행지",
                    heroImageUrl    = "",
                    dateRange       = "",
                    members         = emptyList(),
                    tasks           = emptyList(),
                    overallProgress = 0,
                ),
                onBackClick   = { navController.popBackStack() },
                onTaskClick   = {},
                onInviteClick = {},
                onStartAi     = { navController.navigate("aiLoading") },
            )
        }

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
