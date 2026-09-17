package com.ashkb.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.ashkb.app.ui.AppShell
import com.ashkb.app.ui.theme.AshkbTheme

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A4：通知权限只请求一次——被拒后不再每次冷启动反复触发系统弹窗（可去系统设置自行开启）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("notif_permission_asked", false)) {
                prefs.edit().putBoolean("notif_permission_asked", true).apply()
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            AshkbTheme {
                AppShell()
            }
        }
    }
}
