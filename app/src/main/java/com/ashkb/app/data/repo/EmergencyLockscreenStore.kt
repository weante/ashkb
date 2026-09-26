package com.ashkb.app.data.repo

import android.content.Context

/**
 * v1.0.66 B6a：锁屏紧急信息开关。
 *
 * 存 `app_prefs`（普通 prefs，与 [DisclaimerStore] 同文件）。
 * **默认关闭**——健康信息上锁屏属敏感操作，必须由用户显式开启；
 * prefs 不跨备份恢复，换机后需重新开启（合规上期望如此）。
 */
object EmergencyLockscreenStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ENABLED = "emergency_lockscreen_enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
