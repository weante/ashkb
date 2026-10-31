package com.ashkb.app.ui.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.13（审查报告 S-3）：恢复码「查看」这条路的接线守卫。
 *
 * 为什么静态断言 + 少量 Robolectric 而不是 Compose UI 测试：项目没有 Compose UI 测试基建，
 * 而这里要钉的恰恰是**接线**——「验证界面挡在 reveal 之前」「明文不可选中复制」
 * 「复制走带保护的通道」。这三件事任一漏接，功能表面都正常：码照样能看、照样能复制，
 * 只是保护没了。只有盯源码字面量才抓得住。
 *
 * 判据本身（剪贴板打标记 / 60 秒清除）由 `RecoveryClipboardTest` 覆盖，这里不重复。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecoveryRevealWiringTest {

    private val section = readSource("app/src/main/java/com/ashkb/app/ui/backup/BackupRecoverySection.kt")
    private val guard = readSource("app/src/main/java/com/ashkb/app/ui/backup/RecoveryRevealGuard.kt")

    @Test
    fun `查看恢复码前必须先过设备凭证`() {
        assertTrue(
            "「查看」按钮必须走 deviceCredentialPrompt，否则恢复码可被无验证翻出来",
            section.contains("RecoveryRevealGuard.deviceCredentialPrompt("),
        )
        assertTrue(
            "必须在 ActivityResult 回调里、且 resultCode == RESULT_OK 时才 reveal",
            section.contains("ActivityResultContracts.StartActivityForResult()") &&
                section.contains("Activity.RESULT_OK) vm.revealRecoveryCode()"),
        )
        // 关键：reveal 只能出现在 launcher 的回调与「本机没设锁屏」的放行分支里，
        // 不能出现在按钮 onClick 的直接调用路径上（那就是验证被绕过的形态）。
        val buttonBlock = section.substringAfter("R.string.backup_recovery_view")
        assertFalse(
            "reveal 不得被 onClick 直接调用（必须先经过 launcher）",
            buttonBlock.contains("onClick = { vm.revealRecoveryCode() }"),
        )
    }

    @Test
    fun `恢复码明文不可长按选中复制`() {
        // SelectionContainer 会让用户绕过带敏感标记与 60 秒清除的复制按钮，
        // 直接把明文塞进系统剪贴板——那等于把这层保护作废。
        assertFalse(
            "恢复码弹窗里不得再出现 SelectionContainer",
            section.contains("SelectionContainer {"),
        )
        assertFalse(
            "SelectionContainer 的 import 也该一起删掉",
            section.contains("import androidx.compose.foundation.text.selection.SelectionContainer"),
        )
    }

    @Test
    fun `复制按钮走带保护的剪贴板通道`() {
        assertTrue(
            "onCopy 必须调 RecoveryClipboard.copyRecoveryCode",
            section.contains("RecoveryClipboard.copyRecoveryCode(context, code)"),
        )
        assertFalse(
            "不得再直接 clipboard.setText(...)——那条路不会打敏感标记也不会定时清除",
            section.contains("clipboard.setText("),
        )
    }

    @Test
    fun `设备凭证的构造与取舍都写在这一个地方`() {
        assertTrue(
            "guard 必须问 isDeviceSecure",
            guard.contains("isDeviceSecure"),
        )
        assertTrue(
            "guard 必须用平台的 createConfirmDeviceCredentialIntent（不引第三方生物认证库）",
            guard.contains("createConfirmDeviceCredentialIntent("),
        )
        assertTrue(
            "没设锁屏时必须能返回 null 让调用方放行",
            guard.contains("return null"),
        )
        assertFalse(
            // 查带括号的调用形态而不是裸名字：类 KDoc 里刻意写着「不给密钥加
            // setUserAuthenticationRequired」作为取舍说明，裸名字查法会把自己绊倒。
            "guard 不得调用 setUserAuthenticationRequired——它会牵连 WebDAV 凭据且需轮换密钥",
            guard.contains("setUserAuthenticationRequired("),
        )
    }

    @Test
    fun `没设锁屏时 guard 返回 null 而不是崩溃`() {
        // Robolectric 默认 KeyguardManager.isDeviceSecure == false。
        val context = RuntimeEnvironment.getApplication()
        val intent = RecoveryRevealGuard.deviceCredentialPrompt(context, "t", "d")
        assertTrue(
            "没设锁屏就没有身份屏障可验，必须放行（返回 null）而不是抛异常",
            intent == null,
        )
        assertFalse(
            "没设锁屏时 hasSecureLock 应为 false",
            RecoveryRevealGuard.hasSecureLock(context),
        )
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File("../")
    }

    private fun readSource(relative: String): String = File(repoRoot(), relative).readText()
}
