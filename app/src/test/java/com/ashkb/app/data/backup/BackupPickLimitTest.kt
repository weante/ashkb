package com.ashkb.app.data.backup

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * v1.2.10（审查 CRITICAL-2）：本机选中文件的读入上限。
 *
 * 与 [WebDavBoundedReadTest] 同源但**不同一条路径**：那边守服务器响应
 * （[WebDavClient.BINARY_RESPONSE_LIMIT] = 6 MiB），这边守用户在自己手机的文件选择器里
 * 挑中的那份（[BackupEngine.PICK_LIMIT_BYTES] = 64 MiB）。两条上限服务不同对象，
 * 所以这里第一组断言就是"它们没有被绑成同一个数"——将来谁改了其中一个，
 * 另一条路径的行为不该跟着变。
 *
 * 核心不变量仍是**绝不静默截断**：超限整体拒绝，且异常自带 `resId`，UI 侧复用
 * 既有的 `errText` 就能把上限数字报给用户（只说"文件太大"用户无从判断该换哪个文件）。
 */
class BackupPickLimitTest {

    /** 假流：`available()` 恒为 0（SAF / 网盘 provider 的常见行为），判定只能靠实读字节数。 */
    private class AvailableZeroStream(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos >= data.size) -1 else data[pos++].toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
        override fun available(): Int = 0
    }

    /** 假流：`available()` 谎报一个超大值（模拟 provider 报了个不实的长度）。 */
    private class LyingAvailableStream(private val data: ByteArray, private val claimed: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos >= data.size) -1 else data[pos++].toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
        override fun available(): Int = claimed
    }

    // ---------- 常量本身 ----------

    @Test
    fun `本机上限与网络上限是两个数`() {
        assertEquals(
            "本机选中文件的上限就是 64 MiB（维护者否决过 6 MiB：附件累计会很多）",
            64L * 1024 * 1024, BackupEngine.PICK_LIMIT_BYTES,
        )
        assertTrue(
            "本机上限必须远大于网络侧 6 MiB，否则等于把网络侧的保守取值抄给了本机",
            BackupEngine.PICK_LIMIT_BYTES > WebDavClient.BINARY_RESPONSE_LIMIT,
        )
    }

    @Test
    fun `恰好等于上限算通过`() {
        assertTrue(BackupEngine.withinPickLimit(BackupEngine.PICK_LIMIT_BYTES))
        assertTrue(BackupEngine.withinPickLimit(0))
        assertFalse(BackupEngine.withinPickLimit(BackupEngine.PICK_LIMIT_BYTES + 1))
    }

    // ---------- readBounded ----------

    @Test
    fun `上限内原样读出`() {
        val data = ByteArray(64 * 1024) { (it % 251).toByte() }
        val out = BackupEngine.readBounded(AvailableZeroStream(data), BackupEngine.PICK_LIMIT_BYTES, "small.ashkb")
        assertArrayEquals(data, out)
    }

    @Test
    fun `恰好等于上限仍然读出`() {
        val data = ByteArray(4096)
        assertEquals(4096, BackupEngine.readBounded(ByteArrayInputStream(data), 4096, "exact.ashkb").size)
    }

    @Test
    fun `available 恒为零时超限一样被拒`() {
        // 关键用例：`available()` 报 0（读不出长度）的流必须靠逐块累计兜住，
        // 否则这一步会"以为很小"然后一路把整份读进内存。
        val data = ByteArray(8192)
        try {
            BackupEngine.readBounded(AvailableZeroStream(data), 4096, "big.ashkb")
            fail("超限必须抛异常，不能返回前半截")
        } catch (e: BackupEngine.BackupException) {
            assertTrue("异常必须带资源 id 才能出本地化文案", e.resId != 0)
        }
    }

    @Test
    fun `available 谎报超大时在读第一个字节前就拒绝`() {
        // 这条路径存在的意义就是省掉一次无用的搬运：声明长度已超限，不必再读。
        var reads = 0
        val stream = object : InputStream() {
            override fun read(): Int { reads++; return 0 }
            override fun available(): Int = Int.MAX_VALUE
        }
        try {
            BackupEngine.readBounded(stream, 4096, "lying.ashkb")
            fail("声明长度超限就该立即拒绝")
        } catch (e: BackupEngine.BackupException) {
            assertEquals("不该真的去读那个流", 0, reads)
            // 断言异常本身带上了正确的名字（顺带让 detekt 看到它没被吞掉）
            assertEquals("lying.ashkb", e.resArgs.first())
        }
    }

    @Test
    fun `谎报的流若声明值不超限仍按实读判断`() {
        // 反向：provider 少报长度时不能因此放过——逐块累计才是最终判据。
        try {
            BackupEngine.readBounded(LyingAvailableStream(ByteArray(8192), 10), 4096, "under.ashkb")
            fail("实读超限也要拒")
        } catch (e: BackupEngine.BackupException) {
            assertTrue(e.resArgs.isNotEmpty())
        }
    }

    @Test
    fun `超限异常带着文件名与两个 MB 数字`() {
        // UI 侧 errText → text(resId, resArgs) 直接吃这三个实参，顺序不能乱。
        try {
            BackupEngine.readBounded(ByteArrayInputStream(ByteArray(3 * 1024 * 1024)), 1024 * 1024, "my.ashkb")
            fail("应抛异常")
        } catch (e: BackupEngine.BackupException) {
            assertEquals(3, e.resArgs.size)
            assertEquals("my.ashkb", e.resArgs[0])
            assertEquals("实际大小按 MiB 取整", 3L, e.resArgs[1])
            assertEquals("上限按 MiB 取整", 1L, e.resArgs[2])
        }
    }

    @Test
    fun `sizeMb 是向下取整不是四舍五入`() {
        assertEquals(0L, BackupEngine.sizeMb(1024L * 1024L - 1))
        assertEquals(1L, BackupEngine.sizeMb(1024L * 1024L))
        assertEquals(1L, BackupEngine.sizeMb(2L * 1024 * 1024 - 1))
        assertEquals(2L, BackupEngine.sizeMb(2L * 1024 * 1024))
    }

    @Test
    fun `流一定被关闭`() {
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(8)) {
            override fun close() { closed = true; super.close() }
        }
        BackupEngine.readBounded(stream, 4096, "x.ashkb")
        assertTrue("use{} 必须关流，否则 SAF 的 fd 会泄漏", closed)
    }

    @Test
    fun `超限时流也会被关闭`() {
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(8192)) {
            override fun close() { closed = true; super.close() }
        }
        runCatching { BackupEngine.readBounded(stream, 4096, "x.ashkb") }
        assertTrue(closed)
    }
}
