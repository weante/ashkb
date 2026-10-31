package com.ashkb.app.ui.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * v1.2.13 S-3：把**恢复码**放进剪贴板时做的两件保护。
 *
 * 背景（审查报告 §十 第 3 条裁决「不信任 WebDAV 服务商」）：恢复码是云端备份的第二个
 * 解锁凭据，与口令等权。剪贴板是全机共享的，任何 App 在用户复制之后的任意时刻都能读到
 * ——而且 Android 的剪贴板历史会跨应用留存。所以「一键复制恢复码」此前等于把后门凭据
 * 永久留在系统级共享区里。
 *
 * 两道保护：
 *  1. **敏感标记**（API 33+）：`ClipDescription.extras` 里的
 *     `android.content.extra.IS_SENSITIVE`。系统据此在剪贴板浮层里隐藏内容预览，
 *     并（在支持的 ROM 上）不把它写进剪贴板历史。
 *  2. **定时清除**：60 秒后若剪贴板里仍是这段恢复码就清空。之所以要判断「仍是这段」，
 *     是因为用户完全可能在这 60 秒里复制了别的东西——那时清空会误删与他无关的内容。
 *
 * 刻意**不做**的两件事：
 *  - 不把时限做成可配置：60 秒足够粘贴一次，再长就是给攻击者留窗口；
 *  - 不在清除失败时提示用户：`clearPrimaryClip()` 在 API 28+ 需要焦点，后台进程调用
 *    可能被静默忽略。这不是用户能处理的事，报错只会让人以为恢复码泄露了。
 */
internal object RecoveryClipboard {

    /** 敏感标记键：API 33 的 `ClipDescription.EXTRA_IS_SENSITIVE` 的字面值。 */
    internal const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** 自动清空的等待时长（毫秒）。 */
    internal const val CLEAR_DELAY_MS = 60_000L

    /** 剪贴板标签——不是内容，只是让用户在系统剪贴板浮层里看出这是什么。 */
    internal const val LABEL = "ASHKB recovery code"

    /**
     * 复制恢复码并登记 60 秒后的清理。
     *
     * 用 `String` 入参而不是 `CharSequence`：这里是**明文凭据**，不接受任何会保留
     * 原对象引用的子类（`Spanned` 之类可能被别处持有）。
     */
    fun copyRecoveryCode(context: Context, code: String) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(LABEL, code)
        markSensitive(clip)
        runCatching { manager.setPrimaryClip(clip) }
        handler.postDelayed({ clearIfStillTheCode(manager, code) }, CLEAR_DELAY_MS)
    }

    /**
     * 60 秒后的清理动作：只有剪贴板内容仍是这段恢复码才清空。
     *
     * 单独抽成 `internal` 函数是为了能在**不等待 60 秒**的前提下测它——
     * 断言它与 `copyRecoveryCode` 之间的分工（谁负责判断、谁负责清空）。
     */
    internal fun clearIfStillTheCode(manager: ClipboardManager, code: String) {
        val current = runCatching { manager.primaryClip }.getOrNull()
            ?: return
        val text = runCatching {
            if (current.itemCount == 0) null else current.getItemAt(0).coerceToText(null)?.toString()
        }.getOrNull()
        if (!shouldClear(text, code)) return
        runCatching { manager.clearPrimaryClip() }
    }

    /**
     * 剪贴板里的 [current] 是否仍是我们放进去的 [code]。
     *
     * 判等而不是判「包含」：恢复码是 32 字符定长串，用户若把它粘进一段更长的文本再复制，
     * 那就不是我们要清理的对象（也清不掉——剪贴板里是那段更长文本）。
     */
    internal fun shouldClear(current: String?, code: String): Boolean = current != null && current == code

    /**
     * 给剪贴板条目打上「敏感」标记（仅 API 33+；本项目 minSdk 31，故必须做版本判断
     * 而不是加 `@RequiresApi`——加了就等于在 31/32 上崩溃或要靠调用方记得判断）。
     */
    private fun markSensitive(clip: ClipData) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }

    private val handler = Handler(Looper.getMainLooper())
}
