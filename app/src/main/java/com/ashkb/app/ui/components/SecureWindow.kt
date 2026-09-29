package com.ashkb.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * v1.0.73：**敏感页面禁截屏**（口令 / 恢复码类界面）。
 *
 * 维护者口径（2026-09-29 裁决）：只在口令与恢复码类页面加，不铺到化验 / 影像 / 急救卡
 * （那几页需要用截图做记录与反馈）。
 *
 * 为什么不能用一行 `activity.window.setFlags(...)` 了事：Compose 的 `AlertDialog` 与
 * `ModalBottomSheet` 各自是**独立窗口**，给 Activity 设 `FLAG_SECURE` 管不到它们；
 * 而恢复码展示是 AlertDialog、WebDAV 口令是 ModalBottomSheet。因此这里同时处理两种宿主：
 *  - Activity 窗口：从 `LocalView.context` 沿 `ContextWrapper.baseContext` 上溯取到 Activity；
 *  - 弹窗 / Sheet 窗口：`LocalView.parent as? DialogWindowProvider`。
 *
 * 用 `DisposableEffect` 在离开组合时清除标志，避免「进过一次口令页，之后全应用都不能截图」。
 */
@Composable
fun SecureWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = generateSequence(view.context as Context?) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val windows = listOfNotNull(activity?.window, dialogWindow).distinct()
        windows.forEach {
            it.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            windows.forEach { it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
}
