package com.synctrip.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.*
import com.synctrip.app.ui.theme.SynctripTheme

// ═════════════════════════════════════════════════════════════════════════════
// 1. Create Trip Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CreateTripScreen(
    suggestions: List<DestinationSuggestion>,
    destination: String,
    onDestinationChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    selectedStyles: Set<TravelStyle>,
    onStyleToggle: (TravelStyle) -> Unit,
    onCreateTrip: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        "새 여행 만들기",
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
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick  = onCreateTrip,
                    enabled  = destination.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("여행 만들기", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            CreateTripSection(title = "어디로 떠나시나요?") {
                OutlinedTextField(
                    value         = destination,
                    onValueChange = onDestinationChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("목적지 검색") },
                    leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(suggestions) { sug ->
                            DestinationChip(
                                suggestion = sug,
                                isSelected = sug.name == destination,
                                onClick    = { onDestinationChange(sug.name) },
                            )
                        }
                    }
                }
            }

            CreateTripSection(title = "언제 떠나시나요?") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(label = "출발일", value = startDate, onChange = onStartDateChange, modifier = Modifier.weight(1f))
                    DateField(label = "귀국일", value = endDate, onChange = onEndDateChange, modifier = Modifier.weight(1f))
                }
            }

            CreateTripSection(title = "어떤 여행을 선호하시나요?") {
                TravelStyleGrid(selectedStyles = selectedStyles, onStyleToggle = onStyleToggle)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreateTripSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface))
        content()
    }
}

@Composable
private fun DestinationChip(suggestion: DestinationSuggestion, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected    = isSelected,
        onClick     = onClick,
        label       = { Text("${suggestion.name}, ${suggestion.country}", style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (isSelected) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null,
        shape       = RoundedCornerShape(999.dp),
        colors      = FilterChipDefaults.filterChipColors(
            selectedContainerColor   = MaterialTheme.colorScheme.primary,
            selectedLabelColor       = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        modifier      = modifier,
        label         = { Text(label) },
        leadingIcon   = { Icon(Icons.Outlined.CalendarMonth, null) },
        shape         = RoundedCornerShape(12.dp),
        singleLine    = true,
        readOnly      = true,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun TravelStyleGrid(selectedStyles: Set<TravelStyle>, onStyleToggle: (TravelStyle) -> Unit) {
    val styles = mapOf(
        TravelStyle.NATURE     to Pair(Icons.Outlined.Park,            "자연"),
        TravelStyle.URBAN      to Pair(Icons.Outlined.LocationCity,    "도시"),
        TravelStyle.FOOD       to Pair(Icons.Outlined.Restaurant,      "음식"),
        TravelStyle.CULTURE    to Pair(Icons.Outlined.Museum,          "문화"),
        TravelStyle.ADVENTURE  to Pair(Icons.Outlined.Hiking,          "모험"),
        TravelStyle.RELAXATION to Pair(Icons.Outlined.SelfImprovement, "휴양"),
        TravelStyle.NIGHTLIFE  to Pair(Icons.Outlined.NightlifeSharp,  "나이트"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        styles.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (style, pair) ->
                    val (icon, label) = pair
                    val selected = style in selectedStyles
                    Card(
                        onClick   = { onStyleToggle(style) },
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        border    = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, tint = if (selected) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
                if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. AI Loading Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AiLoadingScreen(
    status: AiGenerationStatus,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(status.isComplete) {
        if (status.isComplete) onComplete()
    }

    val pulseAnim by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "ai-pulse",
    )

    Box(
        modifier         = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 30.dp, y = 80.dp)
                .size(300.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape)
                .alpha(pulseAnim),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-20).dp, y = (-60).dp)
                .size(250.dp)
                .background(MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.25f), CircleShape)
                .alpha(1f - (pulseAnim - 0.85f) * 5f),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FlightTakeoff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
            }

            Spacer(Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status.currentStep, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text("${status.progressPercent}%", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { status.progressPercent / 100f },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "AI가 최적의 여행 일정을 만들고 있어요",
                style     = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "그룹의 취향과 동선을 분석해서\n최고의 경험을 설계하고 있어요",
                style     = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    Pair(Icons.Outlined.Route,      "동선 최적화"),
                    Pair(Icons.Outlined.Schedule,   "시간 안배"),
                    Pair(Icons.Outlined.Restaurant, "맛집 추천"),
                ).forEach { (icon, label) -> AiFeatureChip(icon = icon, label = label) }
            }
        }
    }
}

@Composable
private fun AiFeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. Final Itinerary Screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ItineraryScreen(
    itinerary: TripItinerary,
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onExportClick: () -> Unit,
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
                        style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Outlined.ArrowBack, "뒤로") }
                },
                actions = {
                    IconButton(onClick = onExportClick) { Icon(Icons.Outlined.Share, "공유", tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model              = itinerary.heroImageUrl,
                    contentDescription = itinerary.destination,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                Text(
                    text     = itinerary.destination,
                    style    = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(text = itinerary.title, style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            itinerary.days.forEach { day ->
                DaySection(day = day, onEventClick = onEventClick)
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DaySection(day: ItineraryDay, onEventClick: (String) -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text     = day.title,
                style    = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            day.events.forEachIndexed { idx, event ->
                TimelineEvent(event = event, isLast = idx == day.events.lastIndex, onEventClick = onEventClick)
            }
        }
    }
}

@Composable
private fun TimelineEvent(event: ItineraryEvent, isLast: Boolean, onEventClick: (String) -> Unit) {
    val eventColor = when (event.category) {
        EventCategory.TRANSPORT     -> MaterialTheme.colorScheme.primary
        EventCategory.ACCOMMODATION -> MaterialTheme.colorScheme.secondary
        EventCategory.FOOD          -> MaterialTheme.colorScheme.tertiary
        EventCategory.ACTIVITY      -> MaterialTheme.colorScheme.error
        EventCategory.REST          -> MaterialTheme.colorScheme.outline
    }

    val eventIcon = when (event.category) {
        EventCategory.TRANSPORT     -> Icons.Outlined.FlightTakeoff
        EventCategory.ACCOMMODATION -> Icons.Outlined.Hotel
        EventCategory.FOOD          -> Icons.Outlined.Restaurant
        EventCategory.ACTIVITY      -> Icons.Outlined.Attractions
        EventCategory.REST          -> Icons.Outlined.Hotel
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEventClick(event.id) }
            .padding(vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(eventColor), contentAlignment = Alignment.Center) {
                Icon(eventIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = if (!isLast) 16.dp else 0.dp)) {
            Text(event.time, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.height(2.dp))
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            if (event.description != null) {
                Text(event.description, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateTripScreenPreview() {
    SynctripTheme {
        CreateTripScreen(
            suggestions         = listOf(DestinationSuggestion("도쿄", "일본", "", "TYO"), DestinationSuggestion("파리", "프랑스", "", "CDG")),
            destination         = "도쿄",
            onDestinationChange = {},
            startDate           = "2024-09-03",
            onStartDateChange   = {},
            endDate             = "2024-09-08",
            onEndDateChange     = {},
            selectedStyles      = setOf(TravelStyle.FOOD, TravelStyle.CULTURE),
            onStyleToggle       = {},
            onCreateTrip        = {},
            onBackClick         = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AiLoadingScreenPreview() {
    SynctripTheme {
        AiLoadingScreen(
            status     = AiGenerationStatus("j1", 65, "동선 최적화 중…", false),
            onComplete = {},
        )
    }
}
