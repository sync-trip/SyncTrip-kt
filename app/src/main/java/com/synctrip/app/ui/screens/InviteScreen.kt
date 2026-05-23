package com.synctrip.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.synctrip.app.data.models.BandInviteCodeResponse
import com.synctrip.app.ui.theme.SynctripTheme

/**
 * 멤버 초대 화면.
 * +초대 버튼에서 진입하며, 화면 진입 시 자동으로 초대 코드를 발급/조회한다.
 * 코드 복사와 링크 공유 두 가지 방식을 제공한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    bandName: String,
    memberCount: Int,
    inviteCode: BandInviteCodeResponse?,    // null = 로딩 중
    onBackClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareLink: (code: String, link: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 여행 친구 수 + 안내 문구
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "여행 친구 ",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text  = "$memberCount",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text  = "명",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text  = "함께 여행할 친구나 가족을 초대해보세요.\n초대 코드나 링크를 공유하면 돼요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            if (inviteCode == null) {
                // 코드 발급 중 로딩 표시
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // 초대 코드 카드
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text  = "초대 코드",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Text(
                            text  = inviteCode.inviteCode,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }

                // 공유 액션 버튼
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick  = { onCopyCode(inviteCode.inviteCode) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = "초대 코드 복사",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    OutlinedButton(
                        onClick  = { onShareLink(inviteCode.inviteCode, inviteCode.inviteShareLink) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = "다른 앱으로 초대",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InviteScreenPreview() {
    SynctripTheme {
        InviteScreen(
            bandName    = "도쿄 여행",
            memberCount = 2,
            inviteCode  = BandInviteCodeResponse(1L, "SYNC1234", "2024-09-02T00:00:00", null, null),
            onBackClick = {},
            onCopyCode  = {},
            onShareLink = { _, _ -> },
        )
    }
}
