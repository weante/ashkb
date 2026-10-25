package com.ashkb.app.data.repo

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.11（缺陷工单 B07）：pre-restore 快照的应用内撤销——**读侧**的回归测试。
 *
 * 工单的判定是「退路存在但不可达」：写侧一直在落盘，却没有列举/读取/选择它的路径。
 * 本文件守三件事，任何一条被改坏都会让撤销入口重新变回不可达或变得危险：
 *
 * 1. **命名契约两侧逐字一致**：写侧 `BackupRepository.restore()` 用 `PRE_RESTORE_STAMP`
 *    生成名字，读侧靠 `parsePreRestoreStamp` 反解。两边一旦漂移，快照就会「写得出、
 *    列不出」——正是缺陷本身。
 * 2. **同名绝不覆盖**（工单第 6 条「不得把唯一退路静默删掉」）：同秒两份快照必须
 *    落到两个文件上，且碰撞后缀仍能被解析回同一个时间戳。
 * 3. **列举结果可信**：只认本应用写出的 `pre-restore-*.ashkb`，路径分隔符与 `..`
 *    必须在**进入列表之前**被拒（名字会被拼进路径去读文件）。
 *
 * 用 Robolectric 只是因为 [PreRestoreStore] 需要 `filesDir`；断言本身全是纯逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PreRestoreSnapshotTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val backupsDir: File get() = File(ctx.filesDir, "backups")

    @Before
    fun setUp() {
        backupsDir.deleteRecursively()
    }

    // ---------------- 1. 命名契约 ----------------

    @Test
    fun `写侧格式能被读侧原样解析回来`() {
        val now = LocalDateTime.of(2026, 10, 26, 1, 30, 22)
        val name = uniquePreRestoreName(now.format(PRE_RESTORE_STAMP)) { false }
        assertEquals("pre-restore-20261026-013022.ashkb", name)
        assertEquals(now, parsePreRestoreStamp(name))
    }

    @Test
    fun `同秒碰撞时追加后缀且绝不覆盖已有文件`() {
        val stamp = "20261026-013022"
        // 第一份不冲突
        assertEquals("pre-restore-$stamp.ashkb", uniquePreRestoreName(stamp) { false })
        // 第一份已存在 ⇒ 拿 -2
        val taken = setOf("pre-restore-$stamp.ashkb")
        assertEquals("pre-restore-$stamp-2.ashkb", uniquePreRestoreName(stamp) { it in taken })
        // 前两份都占 ⇒ 拿 -3
        val taken2 = setOf("pre-restore-$stamp.ashkb", "pre-restore-$stamp-2.ashkb")
        assertEquals("pre-restore-$stamp-3.ashkb", uniquePreRestoreName(stamp) { it in taken2 })
    }

    @Test
    fun `碰撞后缀的快照仍解析回同一个时间戳`() {
        val expected = LocalDateTime.of(2026, 10, 26, 1, 30, 22)
        assertEquals(expected, parsePreRestoreStamp("pre-restore-20261026-013022.ashkb"))
        assertEquals(expected, parsePreRestoreStamp("pre-restore-20261026-013022-2.ashkb"))
        assertEquals(expected, parsePreRestoreStamp("pre-restore-20261026-013022-999.ashkb"))
    }

    // ---------------- 2. 名字合法性（路径穿越必须进不了列表） ----------------

    @Test
    fun `非法名字一律不认`() {
        val bad = listOf(
            // 不是本应用的快照：用户自己的备份、恢复演练产物、无关文件
            "ashkb-backup-2026-10-26.ashkb",
            "ashkb-2026-10-26.ashkb",
            "pre-restore-20261026-013022.ashkb.bak",
            "notes.txt",
            "",
            // 时间戳不合法（月份 13、长度不对、非数字）
            "pre-restore-20261326-013022.ashkb",
            "pre-restore-20261026-0130.ashkb",
            "pre-restore-abcdefgh-abcdef.ashkb",
        )
        for (n in bad) {
            assertFalse("应当拒绝：$n", isPreRestoreName(n))
            assertNull("不应当解析出时间：$n", parsePreRestoreStamp(n))
        }
    }

    /**
     * 路径穿越必须被**显式**拦掉，而不是靠"时间戳解析不通过"顺带挡住。
     *
     * 这两层是纵深防御：`parsePreRestoreStamp` 要求 15 位时间戳，恰好也能挡住大部分
     * 畸形名，所以如果样例只写 `pre-restore-xxx/../../secret`（时间戳本身就不合法），
     * 就算把 `isPreRestoreName` 里的分隔符检查整行删掉，测试依然全绿——负向对照实测
     * 正是如此。故这里**刻意构造时间戳完全合法、只多出路径成分**的名字。
     */
    @Test
    fun `时间戳合法但含路径成分的名字必须被显式拒绝`() {
        val traversals = listOf(
            "pre-restore-20261026-013022/../secret.ashkb",
            "pre-restore-20261026-013022/..",
            "../pre-restore-20261026-013022.ashkb",
            "sub/pre-restore-20261026-013022.ashkb",
            "pre-restore-20261026-013022\\..\\secret.ashkb",
            "pre-restore-20261026-013022-2/../../x.ashkb",
        )
        for (n in traversals) {
            assertFalse("必须拒绝路径成分：$n", isPreRestoreName(n))
        }
    }

    @Test
    fun `名字合法的快照即使时间戳相同也能被区分`() {
        // 正向对照：确保上面那条测试不是靠"一律拒绝"通过的
        assertTrue(isPreRestoreName("pre-restore-20261026-013022.ashkb"))
        assertTrue(isPreRestoreName("pre-restore-20261026-013022-2.ashkb"))
    }

    @Test
    fun `排序是最近在前且同秒时带后缀的在前`() {
        val names = listOf(
            "pre-restore-20261026-010000.ashkb",
            "pre-restore-20261026-020000-2.ashkb",
            "pre-restore-20261026-020000.ashkb",
            "ashkb-backup-2026-10-26.ashkb", // 非快照，必须被过滤掉
            "pre-restore-20261025-235959.ashkb",
        )
        assertEquals(
            listOf(
                "pre-restore-20261026-020000-2.ashkb",
                "pre-restore-20261026-020000.ashkb",
                "pre-restore-20261026-010000.ashkb",
                "pre-restore-20261025-235959.ashkb",
            ),
            sortPreRestoreNames(names),
        )
    }

    // ---------------- 3. PreRestoreStore：列举 / 挑最近 / 有界读取 ----------------

    @Test
    fun `没有快照时 latest 为 null 且不创建目录`() {
        val store = PreRestoreStore(ctx)
        assertNull(store.latest())
        assertTrue(store.list().isEmpty())
        // 读侧只读：目录是写侧的职责，这里不该顺手建出来
        assertFalse("读侧不应当创建 backups 目录", backupsDir.exists())
    }

    @Test
    fun `只列出本应用的快照且最近一次在前`() {
        backupsDir.mkdirs()
        File(backupsDir, "ashkb-backup-2026-10-26.ashkb").writeBytes(ByteArray(10))
        File(backupsDir, "pre-restore-20261025-120000.ashkb").writeBytes(ByteArray(10))
        File(backupsDir, "pre-restore-20261026-090000.ashkb").writeBytes(ByteArray(20))
        File(backupsDir, "sub").mkdirs() // 目录不是快照

        val store = PreRestoreStore(ctx)
        val list = store.list()
        assertEquals(
            listOf("pre-restore-20261026-090000.ashkb", "pre-restore-20261025-120000.ashkb"),
            list.map { it.name },
        )
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0, 0), list[0].createdAt)
        assertEquals(20L, list[0].sizeBytes)
        assertTrue(list[0].available)
        // latest 取的是最近那份
        assertEquals("pre-restore-20261026-090000.ashkb", store.latest()?.name)
    }

    @Test
    fun `空文件被标为不可用且 latest 跳过它`() {
        backupsDir.mkdirs()
        File(backupsDir, "pre-restore-20261026-090000.ashkb").writeBytes(ByteArray(0))
        File(backupsDir, "pre-restore-20261025-120000.ashkb").writeBytes(ByteArray(10))

        val store = PreRestoreStore(ctx)
        val list = store.list()
        assertEquals(2, list.size)
        assertFalse("0 字节快照不可用", list[0].available)
        assertTrue(list[1].available)
        // 最近那份坏掉 ⇒ latest 必须退到下一份可用的，而不是返回一个点不动的入口
        assertEquals("pre-restore-20261025-120000.ashkb", store.latest()?.name)
    }

    @Test
    fun `read 取回逐字节一致的快照内容`() {
        backupsDir.mkdirs()
        val payload = ByteArray(4096) { (it % 251).toByte() }
        File(backupsDir, "pre-restore-20261026-090000.ashkb").writeBytes(payload)

        val store = PreRestoreStore(ctx)
        val snap = store.latest()
        assertNotNull(snap)
        assertTrue(payload.contentEquals(store.read(snap!!)))
    }

    @Test
    fun `read 拒绝非法名字与不存在的文件`() {
        backupsDir.mkdirs()
        val store = PreRestoreStore(ctx)
        // 非法名字（构造一个绕过 list 的对象，模拟将来有人把名字从别处传进来）
        try {
            store.read(PreRestoreSnapshot("../etc/passwd", null, 1L, true))
            fail("应当拒绝路径穿越的名字")
        } catch (_: Exception) {
            // 预期：任何异常都比读出别的文件安全
        }
        // 名字合法但文件已被系统清理
        try {
            store.read(PreRestoreSnapshot("pre-restore-20261026-090000.ashkb", null, 1L, true))
            fail("文件不存在时应当抛异常")
        } catch (_: Exception) {
            // 预期
        }
    }

    @Test
    fun `超过读取上限的快照标为不可用`() {
        backupsDir.mkdirs()
        // 只造名字与长度，不真写 64 MiB：list() 只看 f.length()。
        // 用稀疏文件让 length() 超过 PICK_LIMIT_BYTES 而不占实际空间。
        val f = File(backupsDir, "pre-restore-20261026-090000.ashkb")
        java.io.RandomAccessFile(f, "rw").use { it.setLength(com.ashkb.app.data.backup.BackupEngine.PICK_LIMIT_BYTES + 1) }

        val store = PreRestoreStore(ctx)
        val list = store.list()
        assertEquals(1, list.size)
        assertFalse("超限快照必须标为不可用", list[0].available)
        assertNull(store.latest())
    }
}
