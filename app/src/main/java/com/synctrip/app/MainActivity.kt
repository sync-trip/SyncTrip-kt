package com.synctrip.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kakao.sdk.common.util.Utility
import com.synctrip.app.navigation.SyncTripNavGraph
import com.synctrip.app.ui.theme.SynctripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 카카오 키 해시 확인을 위한 로그 추가
        val keyHash = Utility.getKeyHash(this)
        Log.d("KakaoKeyHash", "Key Hash: $keyHash")
        
        enableEdgeToEdge()
        setContent {
            SynctripTheme {
                SyncTripNavGraph()
            }
        }
    }
}
