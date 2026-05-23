package com.synctrip.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synctrip.app.ui.theme.SynctripTheme

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logoScale by rememberInfiniteTransition(label = "logo-pulse").animateFloat(
        initialValue  = 1f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-scale",
    )

    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 800),
        )
        kotlinx.coroutines.delay(1800)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00B2FF),
                        Color(0xFF006492),
                        Color(0xFF001E2F),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier
                .alpha(alphaAnim.value)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.FlightTakeoff,
                contentDescription = "SyncTrip logo",
                tint               = Color.White,
                modifier           = Modifier
                    .size(80.dp)
                    .scale(logoScale),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "SyncTrip",
                style     = MaterialTheme.typography.displayLarge.copy(
                    color         = Color.White,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "함께 떠나는 여행의 시작",
                style     = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.75f),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    SynctripTheme {
        SplashScreen(onSplashComplete = {})
    }
}
