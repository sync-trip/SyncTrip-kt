package com.synctrip.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.kakao.sdk.common.util.Utility
import com.synctrip.app.navigation.SyncTripNavGraph
import com.synctrip.app.ui.theme.SynctripTheme

class MainActivity : ComponentActivity() {

    // Compose와 딥링크 코드를 공유하는 상태 — onNewIntent에서도 갱신됨
    private val deepLinkCode = mutableStateOf<String?>(null)

    // Android 13+ POST_NOTIFICATIONS 런타임 권한 요청 런처
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과에 따른 별도 처리 없음 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyHash = Utility.getKeyHash(this)
        Log.d("KakaoKeyHash", "Key Hash: $keyHash")

        // Android 13(API 33)+ 에서는 POST_NOTIFICATIONS 권한을 런타임으로 요청해야 알림이 표시됨
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        deepLinkCode.value = extractCode(intent)

        enableEdgeToEdge()
        setContent {
            SynctripTheme {
                SyncTripNavGraph(
                    pendingDeepLinkCode = deepLinkCode.value,
                    onDeepLinkConsumed  = { deepLinkCode.value = null },
                )
            }
        }
    }

    // 앱이 이미 실행 중일 때 딥링크 수신 (launchMode="singleTop")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkCode.value = extractCode(intent)
    }

    private fun extractCode(intent: Intent?): String? =
        intent?.data?.getQueryParameter("code")?.takeIf { it.isNotEmpty() }
}
