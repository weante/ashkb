package com.ashkb.app.domain

/**
 * v1.0.72：小米 / Redmi / POCO（MIUI、HyperOS）专属权限的判定（纯函数、无 Android 依赖，可单测）。
 *
 * **官方依据**（小米《开发最佳实践与兼容性建议（适配常见问题）》，`dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/`）：
 *  - **§9「为什么不能在锁屏显示 Activity」**：MIUI 引入了**锁屏显示窗口权限控制**，
 *    **默认不能在锁屏上显示 Activity**（`WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD`），
 *    需要用户主动授予「锁屏上显示权限」；
 *  - **§12「我的应用为什么不能自启动」**：MIUI 上自启动由用户控制，**默认不开放**，
 *    且自启动包含**开机自启动与接收系统广播**两种方式；
 *  - **§10「如何获取某项权限是否开启」**：**暂时没有这个查询接口**，只能引导用户跳转
 *    「应用权限管理」页手动开启——`miui.intent.action.APP_PERM_EDITOR` + `extra_pkgname`。
 *
 * **对应到本应用**（两条功能都被这三条官方说明直接命中）：
 *  - `ReminderFullScreenActivity`（v1.0.61 末级强提醒，在锁屏上拉起全屏）：MIUI 上除 AOSP 的
 *    `USE_FULL_SCREEN_INTENT` 之外，还受私有 appops **`MIUIOP(10020)`（锁屏显示内容）**与
 *    **`MIUIOP(10021)`（后台弹出界面）**控制，两者**默认 `ignore`**；
 *  - 「锁屏紧急信息」常驻通知（v1.0.66）属于「在锁屏上显示内容」，同样受 `MIUIOP(10020)` 控制；
 *  - 开机后重排提醒闹钟（`BootReceiver`）依赖**自启动**（官方明说「接收系统广播」也属自启动范畴）。
 *
 * 既然官方明确「没有查询接口」，本应用的做法只能是**给文字指引 + 一个跳转按钮**，
 * 并**不假装能显示状态**——与 v1.0.62 对自启动的处理立场一致（HANDOFF §7）。
 */
object XiaomiCompat {

    /** 是否小米系设备（MIUI / HyperOS）：品牌或厂商任一命中即算。 */
    fun isXiaomi(brand: String?, manufacturer: String?): Boolean {
        val b = brand.orEmpty().trim().lowercase()
        val m = manufacturer.orEmpty().trim().lowercase()
        return XIAOMI_KEYS.any { b.contains(it) || m.contains(it) }
    }

    /**
     * 小米「权限管理 → 其他权限」里与本应用相关的开关（指引文案逐条列出，用户照着点即可）。
     *
     * 名称取自 MIUI / HyperOS 权限管理页的实际条目；顺序即重要性。
     */
    fun requiredSwitches(): List<String> = listOf(
        SWITCH_LOCKSCREEN,   // MIUIOP(10020)：锁屏显示——强提醒全屏与锁屏紧急卡都需要
        SWITCH_BACKGROUND,   // MIUIOP(10021)：后台弹出界面——末级强提醒要从后台拉起全屏 Activity
        SWITCH_AUTOSTART,    // 开机 / 广播拉起（BootReceiver 重排闹钟）
    )

    const val SWITCH_LOCKSCREEN = "锁屏显示"
    const val SWITCH_BACKGROUND = "后台弹出界面"
    const val SWITCH_AUTOSTART = "自启动"

    private val XIAOMI_KEYS = listOf("xiaomi", "redmi", "poco")
}
