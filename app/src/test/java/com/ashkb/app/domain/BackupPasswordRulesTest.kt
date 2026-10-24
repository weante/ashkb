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
