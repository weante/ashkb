package com.ashkb.app.reminder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * v1.0.62 C11：系统设置导航——电池白名单与自启动。
 *
 * **为什么需要**：本应用的提醒完全依赖本机 `AlarmManager`。国产 ROM（小米 / 华为 /
 * OPPO / vivo 等）默认会用「电池优化 + 自启动管控」把后台应用连带其精确闹钟一起掐掉，
 * 表现为「权限全给了但提醒不响」——这是本类要解决的用户可见故障。
 *
 * **两类引导的差异**：
 *  - **电池白名单**：有官方查询接口（[PowerManager.isIgnoringBatteryOptimizations]），
 *    故自检卡能显示真实状态。
 *  - **自启动**：**没有任何公开查询接口**，各厂商页面组件名各不相同且随版本漂移。
 *    因此只做「尽力跳转 + 兜底到应用详情页」，且**不假装能查到状态**（不做 CheckRow）。
 */
object SystemSetupGuides {

    /** 是否已加入电池白名单（API 23+，minSdk 26 恒可用）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    /**
     * 打开「加入电池白名单」。
     *
     * 优先官方直连弹窗（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，需 Manifest 声明
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）；部分 ROM 不支持时兜底到电池优化列表页。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (runCatching { context.startActivity(direct) }.isSuccess) return
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    /**
     * v1.0.71：打开「精确闹钟」设置页。
     *
     * **必须带 `data=package:`**：不带时本 ROM 只解析到「全部应用」的闹钟列表
     * （`Settings$AlarmsAndRemindersActivity`），用户还得自己在列表里找 ASHKB；带上才是
     * 本应用专属页（`Settings$AlarmsAndRemindersAppActivity`）。真机实测见 v1.0.71 CHANGELOG。
     */
    fun openExactAlarmSettings(context: Context): Boolean =
        openAppSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)

    /**
     * v1.0.71：打开「强提醒（全屏）」设置页。
     *
     * **这是 v1.0.70 的一处真缺陷**：原实现为
     * `runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)) }` ——
     * 既**没带 `data=package:`**，又把异常**静默吞掉**。该 action 的 intent-filter 要求 package
     * 数据，不带即 `No activity found` → `ActivityNotFoundException` → 按钮点了毫无反应
     * （HyperOS / Android 16 真机实测；logcat 可见 `START … act=…MANAGE_APP_USE_FULL_SCREEN_INTENT`
     * 之后没有任何设置页被拉起）。
     */
    fun openFullScreenIntentSettings(context: Context): Boolean =
        openAppSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)

    /**
     * 统一的系统设置导航：**带 package 数据** → 失败兜底「应用详情」页 → 仍失败返回 `false`。
     *
     * 调用方拿到 `false` 必须给用户一句文字提示（「请到设置里手动开启」）——**不能静默**：
     * 静默失败表现为「按钮点了没反应」，用户既不知道坏了、也不知道该去哪开。
     */
    private fun openAppSettings(context: Context, action: String): Boolean {
        val target = Intent(action).apply { data = Uri.parse("package:${context.packageName}") }
        if (runCatching { context.startActivity(target) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /**
     * 打开自启动设置：按厂商组件名依次尝试，全部失败则落到「应用详情」页
     * （详情页内通常可找到权限 / 自启动相关入口）。
     *
     * 组件名随 ROM 版本变化，故一律 `runCatching` 逐个吞掉失败——
     * 跳不进去不是错误，只是这家 ROM 不认这个入口。
     */
    fun openAutoStartSettings(context: Context) {
        for ((pkg, cls) in AUTO_START_COMPONENTS) {
            val intent = Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        // 兜底：应用详情页
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /** 常见厂商自启动管理页组件（尽力而为，顺序即优先级）。 */
    private val AUTO_START_COMPONENTS = listOf(
        // 小米 / 红米
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // 华为 / 荣耀
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        // OPPO / 一加 / realme
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // vivo / iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // 三星
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        // 魅族
        "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC",
    )
}
