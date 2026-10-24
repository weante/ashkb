package com.ashkb.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查第二批 M7 附带项）：远程备份列表的行数上限。
 *
 * **为什么要钉住这个上限**：`BackupScreen` 的 DAV 选择弹层是 `ModalBottomSheet` +
 * `Column + verticalScroll`（**没有虚拟化**），`DividerList` 会把每一行都组合出来。
 * 服务器侧轮换保留「日 7 / 周 4 / 月 6」共 17 份基准，但**同一天可以有多份时间戳备份**，
 * 而 [WebDavClient.parseDavBackups] 本身不限条数（PROPFIND 响应上限 1 MiB）。
 * 一旦真实份数超出上限，`visibleBackups` 必须截断、`backupsTruncated` 必须为真——
 * 后者是「另给一行说明」的唯一判据，判错就会出现「服务器上只有 N 份」的误导。
 *
 * 与 [com.ashkb.app.domain.MedHistoryTest] / [com.ashkb.app.domain.SupplementHistoryTest]
 * 同一套断言形状（纯函数、纯 JVM），刻意不合并：三处的窗口与上限各自独立。
 */
class DavBackupListLimitTest {

    private fun files(count: Int): List<WebDavClient.DavBackupFile> =
        (1..count).map { i ->
            WebDavClient.DavBackupFile(
                name = "ashkb-backup-2026-10-01-1200$i.ashkb",
                size = 1024L * i,
                modified = "Wed, 01 Oct 2026 12:00:0$i GMT",
            )
        }

    @Test
    fun `列表恰好等于上限时不截断`() {
        val all = files(WebDavClient.MAX_BACKUP_FILES)
        assertEquals(WebDavClient.MAX_BACKUP_FILES, all.size)
        assertEquals(all.size, WebDavClient.visibleBackups(all).size)
        assertFalse(
            "份数正好等于上限时不该显示截断说明——否则用户会去找并不存在的更早备份",
            WebDavClient.backupsTruncated(all.size),
        )
    }

    @Test
    fun `超出上限时只列最近的那一份上限`() {
        val all = files(WebDavClient.MAX_BACKUP_FILES + 1)
        val visible = WebDavClient.visibleBackups(all)
        assertEquals(WebDavClient.MAX_BACKUP_FILES, visible.size)
        assertEquals(
            "截断必须取列表头部（parseDavBackups 已按备份时间倒序、最新在前），不能取尾部",
            all.take(WebDavClient.MAX_BACKUP_FILES),
            visible,
        )
        assertTrue(
            "份数超过上限时必须显示截断说明",
            WebDavClient.backupsTruncated(all.size),
        )
    }

    /** 判据是**真实份数**：只有真实份数才能回答「是不是还有更早的没显示」。 */
    @Test
    fun `截断判据用真实份数而不是截断后的行数`() {
        val all = files(WebDavClient.MAX_BACKUP_FILES * 3)
        val visible = WebDavClient.visibleBackups(all)
        assertTrue(
            "visible 永远不超过上限，若拿它当判据就永远判不出截断",
            visible.size <= WebDavClient.MAX_BACKUP_FILES,
        )
        assertTrue(WebDavClient.backupsTruncated(all.size))
        assertFalse(
            "拿截断后的行数去判，会得出「没截断」的错误结论",
            WebDavClient.backupsTruncated(visible.size),
        )
    }

    @Test
    fun `空列表与单份都不算截断`() {
        assertEquals(emptyList<WebDavClient.DavBackupFile>(), WebDavClient.visibleBackups(emptyList()))
        assertFalse(WebDavClient.backupsTruncated(0))
        val one = files(1)
        assertEquals(one, WebDavClient.visibleBackups(one))
        assertFalse(WebDavClient.backupsTruncated(1))
    }

    /** 上限本身要落在一个「够用但不至于卡顿」的区间：太小学生翻不到，太大就失去意义。 */
    @Test
    fun `上限落在合理区间`() {
        assertTrue(
            "上限至少覆盖轮换保留的 17 份基准（日 7 / 周 4 / 月 6）并留余量",
            WebDavClient.MAX_BACKUP_FILES >= 20,
        )
        assertTrue(
            "上限不该大到失去截断的意义（一屏约 20 行，取到 100 就是三屏以上）",
            WebDavClient.MAX_BACKUP_FILES <= 60,
        )
    }
}
