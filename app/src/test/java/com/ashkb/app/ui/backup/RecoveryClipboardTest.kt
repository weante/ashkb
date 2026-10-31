package com.ashkb.app.ui.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.13（审查报告 S-3）：恢复码进剪贴板时的两道保护。
 *
 * 这里测的是**真剪贴板行为**（Robolectric 的 ShadowClipboardManager 完全按 AOSP 语义记录
 * `setPrimaryClip` / `primaryClip` / `clearPrimaryClip`），不是反射读源码——因为这两道保护
 * 的价值全在「别的 App 能不能读到」这个副作用上，只有真的走一遍剪贴板才验得出来。
 *
 * 三条断言对应三件会失效得很难察觉的事：
 * ① 复制后剪贴板里确实是那段码（否则用户点复制没反应）；
 * ② 条目带敏感标记（否则系统剪贴板历史/跨设备同步会长期留存这段凭据）；
 * ③ 那段码还在剪贴板里时会被清空，而用户后来复制了别的东西时**不被误清**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecoveryClipboardTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(ClipboardManager::class.java)!!

    private val sampleCode = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"

    @After
    fun clearClipboard() {
        runCatching { manager.clearPrimaryClip() }
    }

    @Test
    fun `复制恢复码后剪贴板里就是那段码`() {
        RecoveryClipboard.copyRecoveryCode(context, sampleCode)
        assertEquals(
            "复制后必须能立刻粘贴出这段码",
            sampleCode,
            manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString(),
        )
    }

    @Test
    fun `剪贴板条目必须打上敏感标记`() {
        // API 33+ 才有 extras 里的 IS_SENSITIVE；本项目 minSdk 31，32 上不存在这个能力，
        // 那时只靠下面的定时清除兜底——这也是本测试只在 34 上跑的原因。
        assertTrue("本测试要求 API 33+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        RecoveryClipboard.copyRecoveryCode(context, sampleCode)
        val extras = manager.primaryClip?.description?.extras
        assertTrue(
            "缺少 IS_SENSITIVE 时剪贴板浮层会明文预览，且可能被写进剪贴板历史",
            extras?.getBoolean(RecoveryClipboard.EXTRA_IS_SENSITIVE) == true,
        )
    }

    @Test
    fun `剪贴板里仍是恢复码时会被清空`() {
        RecoveryClipboard.copyRecoveryCode(context, sampleCode)
        RecoveryClipboard.clearIfStillTheCode(manager, sampleCode)
        // clearPrimaryClip 在 Robolectric 里把主剪贴板置空
        val left = runCatching { manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }
            .getOrNull()
        assertTrue(
            "定时清除是 API 31/32 上唯一的保护，这里必须真的清掉（实际残留：$left）",
            left == null || left != sampleCode,
        )
    }

    @Test
    fun `用户后来复制了别的东西时不会被误清`() {
        // 先放恢复码，再模拟用户在 60 秒内复制了别的内容；到点清理时不该动它。
        RecoveryClipboard.copyRecoveryCode(context, sampleCode)
        manager.setPrimaryClip(ClipData.newPlainText("note", "unrelated"))
        RecoveryClipboard.clearIfStillTheCode(manager, sampleCode)
        assertEquals(
            "清理只该针对我们放进去的那段码",
            "unrelated",
            manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString(),
        )
    }

    @Test
    fun `判等只看完全相同的内容`() {
        assertTrue(RecoveryClipboard.shouldClear(sampleCode, sampleCode))
        assertFalse("空剪贴板无需清理", RecoveryClipboard.shouldClear(null, sampleCode))
        assertFalse(
            "用户把码粘进更长的文本再复制，那不是我们要清的对象",
            RecoveryClipboard.shouldClear("前缀 $sampleCode 后缀", sampleCode),
        )
        assertFalse(RecoveryClipboard.shouldClear("", sampleCode))
    }

    @Test
    fun `清除等待时长为 60 秒`() {
        // 这个数字是取舍本身：够粘贴一次，再长就是给攻击者留窗口。
        assertEquals(60_000L, RecoveryClipboard.CLEAR_DELAY_MS)
    }

    @Test
    fun `剪贴板标签能让人看出这是什么`() {
        RecoveryClipboard.copyRecoveryCode(context, sampleCode)
        val label = manager.primaryClip?.description?.label?.toString()
        assertEquals(RecoveryClipboard.LABEL, label)
        // 标签里不得出现码本身——标签在部分系统界面上是不脱敏展示的。
        assertFalse("标签不得泄露内容", label?.contains(sampleCode) == true)
    }
}
