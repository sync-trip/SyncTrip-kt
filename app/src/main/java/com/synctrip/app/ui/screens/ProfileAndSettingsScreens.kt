package com.synctrip.app.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.synctrip.app.data.models.ApiNotificationType
import com.synctrip.app.data.models.NotificationSettingsResponse

// ─────────────────────────────────────────────────────────────────────────────
// 알림 설정 화면
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 알림 설정 화면.
 * 각 알림 타입을 Switch로 on/off할 수 있다.
 * 변경 즉시 PATCH API 호출 (낙관적 업데이트는 ViewModel에서 처리).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: NotificationSettingsResponse?,
    isLoading: Boolean,
    onToggle: (ApiNotificationType, Boolean) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림 설정", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading || settings == null) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // 알림 타입 정의 — (라벨, 설명, 현재값, 타입)
        val items = listOf(
            Triple("투표 시작", "밴드에서 투표가 시작되면 알림", settings.voteStarted to ApiNotificationType.VOTE_STARTED),
            Triple("일정 업데이트", "AI 일정이 완성되거나 수정되면 알림", settings.scheduleUpdated to ApiNotificationType.SCHEDULE_UPDATED),
            Triple("정산 요청", "다른 멤버로부터 정산 요청이 오면 알림", settings.settlementRequest to ApiNotificationType.SETTLEMENT_REQUEST),
            Triple("멤버 준비 완료", "밴드 멤버가 준비 완료 표시하면 알림", settings.memberReady to ApiNotificationType.MEMBER_READY),
            Triple("멤버 합류", "새 멤버가 밴드에 참여하면 알림", settings.memberJoined to ApiNotificationType.MEMBER_JOINED),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (label, description, stateAndType) ->
                val (enabled, type) = stateAndType
                NotificationToggleRow(
                    label       = label,
                    description = description,
                    enabled     = enabled,
                    onToggle    = { onToggle(type, it) },
                )
            }
        }
    }
}

/** 알림 항목 하나 — 아이콘 없이 텍스트 + Switch로 간결하게 */
@Composable
private fun NotificationToggleRow(
    label: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) }
            .padding(vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ─────────────────────────────────────────────────────────────────────────────
// 프로필 편집 화면
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 프로필 편집 화면.
 * 이름 변경 + 갤러리에서 프로필 사진 선택 → PUT /api/users/me.
 * 이미지는 Base64로 인코딩해 profileImageUrl 필드로 전달 (백엔드 기존 방식과 동일).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    initialName: String,
    initialProfileImageUrl: String?,
    isLoading: Boolean,
    onSave: (name: String, profileImageUrl: String?) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    // API 응답 도착 후 initialName이 채워지면 name 동기화 (최초 1회, 사용자가 입력 중이면 덮어쓰지 않음)
    LaunchedEffect(initialName) {
        if (name.isEmpty() && initialName.isNotEmpty()) name = initialName
    }
    // 갤러리에서 고른 이미지 URI (null이면 기존 URL 유지)
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    // 저장 시 넘길 Base64 문자열 (null이면 기존 URL 유지)
    var encodedImage by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedImageUri = uri
            // URI → Base64 변환 (data: URI 형식으로 감싸지 않고 순수 Base64 전달)
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes != null) {
                encodedImage = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필 편집", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(
                        onClick  = { onSave(name, encodedImage ?: initialProfileImageUrl) },
                        enabled  = name.isNotBlank() && !isLoading && (name != initialName || pickedImageUri != null),
                    ) {
                        Text("저장", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            // ── 프로필 사진 선택 ───────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                val displayModel: Any? = pickedImageUri ?: initialProfileImageUrl
                if (displayModel != null) {
                    AsyncImage(
                        model              = displayModel,
                        contentDescription = "프로필 사진",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text  = name.firstOrNull()?.uppercase() ?: "S",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                // 카메라 오버레이 아이콘
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.padding(bottom = 10.dp).size(22.dp),
                    )
                }
            }

            Text(
                text  = "사진을 눌러 변경",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 이름 입력 ─────────────────────────────────────────────────
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("이름") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
            )

            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}
