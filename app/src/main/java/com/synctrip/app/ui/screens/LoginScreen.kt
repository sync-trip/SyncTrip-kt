package com.synctrip.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synctrip.app.ui.theme.SynctripTheme

@Composable
fun LoginScreen(
    onKakaoLogin:   () -> Unit,
    onGoogleLogin:  () -> Unit,
    onEmailLogin:   () -> Unit,
    onTermsClick:   () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier         = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            LogoSection()

            Spacer(Modifier.height(40.dp))

            SocialLoginButton(
                label          = "카카오로 계속하기",
                icon           = Icons.Filled.Person,
                onClick        = onKakaoLogin,
                containerColor = Color(0xFFFECC00),
                contentColor   = Color(0xFF191C1D),
            )

            Spacer(Modifier.height(12.dp))

            DividerWithLabel(label = "또는 다른 방법으로")

            Spacer(Modifier.height(12.dp))

            SocialLoginButton(
                label          = "Google로 계속하기",
                icon           = Icons.Filled.Person,
                onClick        = onGoogleLogin,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor   = MaterialTheme.colorScheme.onSurface,
                border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            )

            Spacer(Modifier.height(8.dp))

            SocialLoginButton(
                label          = "이메일로 계속하기",
                icon           = Icons.Filled.Email,
                onClick        = onEmailLogin,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor   = MaterialTheme.colorScheme.onSurface,
                border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            )

            Spacer(Modifier.height(24.dp))

            LegalText(onTermsClick = onTermsClick, onPrivacyClick = onPrivacyClick)
        }
    }
}

@Composable
private fun LogoSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector        = Icons.Filled.FlightTakeoff,
            contentDescription = "SyncTrip",
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(72.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text  = "SyncTrip",
            style = MaterialTheme.typography.headlineMedium.copy(
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "그룹 여행을 더 쉽고 즐겁게",
            style     = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SocialLoginButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick   = onClick,
        modifier  = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape     = RoundedCornerShape(999.dp),
        colors    = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor   = contentColor,
        ),
        border    = border,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.5.dp.value.sp,
            ),
        )
    }
}

@Composable
private fun DividerWithLabel(label: String) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 16.dp),
            style    = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun LegalText(onTermsClick: () -> Unit, onPrivacyClick: () -> Unit) {
    Text(
        text      = buildAnnotatedString {
            append("계속하면 ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                append("서비스 이용약관")
            }
            append(" 및 ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                append("개인정보처리방침")
            }
            append("에 동의하는 것으로 간주됩니다.")
        },
        style     = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.outline,
        ),
        textAlign = TextAlign.Center,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    SynctripTheme {
        LoginScreen(
            onKakaoLogin  = {},
            onGoogleLogin = {},
            onEmailLogin  = {},
        )
    }
}
