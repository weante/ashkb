package com.ashkb.app.ui.components

import android.content.Context
import android.content.Intent
import com.ashkb.app.R
import com.ashkb.app.ui.GlobalMessages

/**
 * v1.2.10（审查第二批 M5）：拉起系统分享面板，**失败要让用户看见**。
 *
 * ### 为什么抽成一个组件而不是各处内联
 * 原先全仓有四个调用点，写法还不一样：
 *  · `ui/report/ReportExportPage.kt` 两处是
 *    `runCatching { … }.onFailure { context.getString(R.string.report_share_fail, it.message) }`
 *    ——`getString` 求值完就丢，**没有任何接收者**；
 *  · `ui/backup/BackupScreen.kt` 两处更彻底，连 `onFailure` 都没有。
 * 四处的用户可见结果完全一样：「分享面板没起来」在界面上表现为**什么都没发生**，
 * 用户只会反复点，或者以为文件没生成。
 *
 * ### 为什么用 GlobalMessages 而不是就地弹 Snackbar
 * 本组件不是 Composable（要能从 ViewModel 回调里调），拿不到 `SnackbarHostState`。
 * [GlobalMessages] 是既有的跨页消息通道（`AppShell` 根级订阅，自带无障碍播报），
 * 参照实现就是 `EmergencyScreen.kt` 的 `Context.dial` —— 那一条是 v1.2.x 已经踩过
 * 同一个坑之后补的，本处与之对齐。
 *
 * ### 这一条兜不住什么
 * `Intent.createChooser` 找不到任何可接收的应用时**未必抛异常**（系统会给出自己的空态），
 * 所以本函数只兜「真的抛了」的情况——但正是那种情况原先完全无声。
 */
fun shareOrReport(context: Context, intent: Intent, title: String) {
    runCatching { context.startActivity(Intent.createChooser(intent, title)) }
        .onFailure { GlobalMessages.post(context.getString(R.string.report_share_fail, it.message)) }
}
