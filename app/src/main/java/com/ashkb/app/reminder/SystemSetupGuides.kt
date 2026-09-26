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
