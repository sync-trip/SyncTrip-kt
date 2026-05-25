package com.synctrip.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.kakao.sdk.common.util.Utility
import com.synctrip.app.navigation.SyncTripNavGraph
import com.synctrip.app.ui.theme.SynctripTheme

class MainActivity : ComponentActivity() {

    // Compose와 딥링크 코드를 공유하는 상태 — onNewIntent에서도 갱신됨
    private val deepLinkCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyHash = Utility.getKeyHash(this)
        Log.d("KakaoKeyHash", "Key Hash: $keyHash")

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
