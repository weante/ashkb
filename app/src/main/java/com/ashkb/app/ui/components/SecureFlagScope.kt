package com.ashkb.app.ui.components

import android.view.Window
import android.view.WindowManager

/**
 * v1.2.11（缺陷工单 B06）：`SecureWindow` 的**标志所有权**记账。
 *
 * ### 缺陷
 * 旧实现在进入组合时给 Activity 窗口与 Dialog 窗口都 `setFlags(FLAG_SECURE)`，离开时
 * **无条件** `clearFlags(FLAG_SECURE)`。于是「备份页（自身已加保护）→ 打开恢复码弹窗 →
 * 关掉弹窗」这条最普通的路：弹窗的 effect 销毁时把 **Activity 窗口**的 FLAG_SECURE 一起清了，
 * 用户回到备份页，这页已经可以截屏。弹窗自己从没加过 Activity 的保护，却把它解掉了。
 *
 * ### 修法
 * 记下**每个窗口在本 effect 执行前**是否已有该标志；只有「本 effect 加上去的」才在销毁时清除。
 * 父页面自己加的保护因此不会被子弹窗误清。不需要引用计数：一个窗口的标志只有
 * 「本来有 / 本来没有」两种起点，而本 effect 至多加一次，`clearFlags` 又是幂等的。
 *
 * 抽成纯类是为了能纯 JVM 单测——真 `Window` 需要 Android 运行时，而这条判据的关键
 * 恰恰是「记没记住原始状态」。
 */
internal class SecureFlagScope {

    /** 每个窗口：本 effect 执行前是否已有 FLAG_SECURE。 */
    private val hadFlagBefore = mutableMapOf<Window, Boolean>()

    /**
     * 给窗口加保护，并记下它原本的状态。
     *
     * 幂等：同一窗口重复调用只记第一次的原始状态（否则第二次会把「已被本 effect 改过」的
     * 状态当成本来就有，销毁时就不清了——保护会泄漏到整个进程）。
     */
    fun acquire(window: Window) {
        if (hadFlagBefore.containsKey(window)) {
            // 已经记过原始状态：仍然确保标志在位（调用方可能刚清过），但不要覆盖记录
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            return
        }
        val attrs = window.attributes
        hadFlagBefore[window] = (attrs.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** 只清除**由本 effect 新增**的标志；原本就有的保护原样留着。 */
    fun release() {
        hadFlagBefore.forEach { (window, had) ->
            if (!had) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        hadFlagBefore.clear()
    }
}
