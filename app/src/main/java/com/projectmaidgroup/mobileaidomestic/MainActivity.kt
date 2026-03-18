package com.projectmaidgroup.mobileaidomestic

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.projectmaidgroup.ui.avatar.AvatarModels
import com.projectmaidgroup.ui.avatar.Live2DAvatarScreen
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 检查 Shizuku 服务是否运行
        if (Shizuku.pingBinder()) {
            // 2. 检查权限
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                // 3. 发起请求，只有这一步执行了，管理器里才会出现你的 App
                Shizuku.requestPermission(101)
            }
        } else {
            // 说明 Shizuku 服务本身没启动，或者你的 App 没连上
            Log.e("MAID", "Shizuku Service not running")
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Live2DTalk()
                }
            }
        }
    }
}