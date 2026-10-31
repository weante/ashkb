package com.ashkb.app.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 C01）：新备份口令下限 12 位。
 *
 * 这里只测「新备份」这一侧。恢复侧刻意不受约束（旧 6 位口令建的备份必须还能解开），
 * 而恢复路径根本不引用 [BackupPasswordRules]——那条边界由源码断言钉住，
 * 因为一旦有人顺手在恢复里也加个长度检查，用户的历史备份就全废了。
 */
class BackupPasswordRulesTest {

    @Test
    fun `11 位不能新建`() {
        assertFalse(
            "C01 的验收条件：11 位必须被拦下",
            BackupPasswordRules.newBackupOk("a".repeat(11)),
        )
    }

    @Test
    fun `12 位可以新建`() {
        assertTrue(BackupPasswordRules.newBackupOk("a".repeat(12)))
    }

    @Test
    fun `旧 6 位口令不达新下限`() {
        assertFalse(
            "6 位旧口令不达新下限——正因如此，恢复路径必须完全不看这条规则",
            BackupPasswordRules.newBackupOk("abc123"),
        )
    }

    @Test
    fun `恢复路径不引用口令长度规则`() {
        val src = readSource("app/src/main/java/com/ashkb/app/ui/backup/BackupViewModel.kt")
        val verify = src.substringAfter("fun verifyBackup(", "")
            .substringBefore("fun restore")
        assertFalse(
            "verifyBackup 一旦引用 newBackupOk，所有旧 6 位口令的备份都会解不开",
            verify.contains("BackupPasswordRules"),
        )
        assertTrue(
            "恢复应当直接走解密自检，由口令本身决定成败",
            verify.contains("decryptAndSelfCheck"),
        )
    }

    /**
     * v1.2.12（C01 真机遗留）：标签里的「≥6 位」在真机上原样显示，而下限早已是 12 位。
     *
     * 这不是文案瑕疵而是**误导**：用户照着「≥6 位」敲 8 位，两个按钮静默变灰，
     * 屏幕上没有任何一行字解释为什么点不动。所以断言分两层：
     * ① 标签与提示都把位数交给占位符，不再写死数字；
     * ② 调用点确实把 [BackupPasswordRules.MIN_LENGTH] 传了进去——只改字符串不改调用点，
     *    格式化参数缺失会直接崩在运行期，比写死更难查。
     */
    @Test
    fun `口令标签与提示的位数都来自同一个常量`() {
        for (file in listOf("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml")) {
            val xml = readSource(file)
            val field = xml.substringAfter("<string name=\"backup_password_field\">", "")
                .substringBefore("</string>")
            val short = xml.substringAfter("<string name=\"backup_password_too_short\">", "")
                .substringBefore("</string>")
            assertTrue("$file 的标签必须带 %1\$d 占位符，否则位数又会与常量漂移：$field", field.contains("%1\$d"))
            assertFalse("$file 的标签不得写死 6 位：$field", field.contains("6 位") || field.contains("6 characters"))
            assertTrue("$file 缺少口令过短的提示文案", short.contains("%1\$d"))
        }

        // 这两处调用点在 LocalBackupSection 里。v1.2.13（C1-①）把 1325 行的 BackupScreen.kt
        // 按分区拆成 8 个文件后，写死单个路径会立刻失配，所以改成扫整个 ui/backup 目录：
        // 断言的本意是「调用点确实存在」，而不是「它恰好在某个具体文件里」。
        val screen = readSourcesUnder("app/src/main/java/com/ashkb/app/ui/backup")
        assertTrue(
            "BackupScreen 的标签必须把 MIN_LENGTH 传给占位符，否则 %1\$d 无值可填",
            screen.contains("R.string.backup_password_field, BackupPasswordRules.MIN_LENGTH"),
        )
        assertTrue(
            "口令不足时必须就地给出原因，不能只把按钮变灰",
            screen.contains("R.string.backup_password_too_short, BackupPasswordRules.MIN_LENGTH"),
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

    /** 把某个源码目录下所有 .kt 拼起来读——用于断言「某调用点在目录里某处」。 */
    private fun readSourcesUnder(relativeDir: String): String {
        val dir = File(repoRoot(), relativeDir)
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }
        return files.joinToString("\n") { it.readText() }
    }
}
