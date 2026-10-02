package com.ashkb.app.data.backup

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * v1.0.86（批次 11 / D6）：WebDAV 响应体读取上限。
 *
 * 纯 JVM、无网络：直接喂字节流断言边界。**核心不变量是"绝不静默截断"**——
 * 超限必须整体拒绝（抛 [WebDavClient.DavResponseTooLargeException]），
 * 而不是返回一个"看起来正常"的前半截（截断的备份比没有备份更危险：
 * 解密阶段只会报"文件损坏"，用户会以为是自己记错了口令）。
 */
class WebDavBoundedReadTest {

    /** 假流：`available()` 恒为 0（真实 HTTPS 流在数据未到齐时也会这样），故判定只能靠实读字节数。 */
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

    /** 假流：`available()` 谎报一个超大值（模拟 Content-Length 撒谎 / 中间人塞大响应）。 */
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

    @Test
    fun `accepts payload exactly at the limit`() {
        val limit = 64L
        val data = ByteArray(limit.toInt()) { (it % 251).toByte() }
        val out = WebDavClient.readBounded(ByteArrayInputStream(data), limit, "ashkb/backup/x.ashkb")
        assertEquals(limit.toInt(), out.size)
        assertArrayEquals(data, out)
    }

    @Test
    fun `rejects payload one byte over the limit`() {
        val limit = 64L
        val data = ByteArray(limit.toInt() + 1) { 7 }
        try {
            WebDavClient.readBounded(ByteArrayInputStream(data), limit, "ashkb/backup/x.ashkb")
            fail("expected DavResponseTooLargeException")
        } catch (e: WebDavClient.DavResponseTooLargeException) {
            assertEquals(limit, e.limitBytes)
            assertEquals("ashkb/backup/x.ashkb", e.path)
            // 错误必须可诊断：带上路径、已知大小与上限
            assertTrue(e.message!!.contains("ashkb/backup/x.ashkb"))
            assertNotNull(e.actualBytes)
            assertTrue("actualBytes should exceed limit", e.actualBytes!! > limit)
        }
    }

    /** 大流（跨多个 8 KiB 读缓冲）也要在超限处**立刻**中止，而不是读完再判。 */
    @Test
    fun `aborts a large stream instead of reading it all`() {
        val limit = 1024L
        val data = ByteArray(4 * 1024 * 1024) { 1 }
        try {
            WebDavClient.readBounded(ByteArrayInputStream(data), limit, "ashkb/attachments/a.enc")
            fail("expected DavResponseTooLargeException")
        } catch (e: WebDavClient.DavResponseTooLargeException) {
            assertTrue(e.message!!.contains("已中止读取"))
        }
    }

    /** 声明长度就超限时，连读都不读（省掉一次可能很慢的下载）。 */
    @Test
    fun `rejects immediately when declared size exceeds limit`() {
        val limit = 1024L
        val stream = LyingAvailableStream(ByteArray(8), claimed = 10 * 1024 * 1024)
        try {
            WebDavClient.readBounded(stream, limit, "ashkb/backup/big.ashkb")
            fail("expected DavResponseTooLargeException")
        } catch (e: WebDavClient.DavResponseTooLargeException) {
            assertEquals(10L * 1024 * 1024, e.actualBytes)
        }
    }

    /** available() 不可靠（恒 0 / 抛异常）不能影响判定——判定只看实读字节数。 */
    @Test
    fun `available reporting zero does not bypass the limit`() {
        val limit = 32L
        val data = ByteArray(100)
        try {
            WebDavClient.readBounded(AvailableZeroStream(data), limit, "p")
            fail("expected DavResponseTooLargeException")
        } catch (e: WebDavClient.DavResponseTooLargeException) {
            assertEquals(limit, e.limitBytes)
        }
    }

    @Test
    fun `empty response is accepted as empty`() {
        val out = WebDavClient.readBounded(ByteArrayInputStream(ByteArray(0)), 8L, "p")
        assertEquals(0, out.size)
    }

    /** 读流本身出错时如实抛出（不吞、不转成空结果）。 */
    @Test
    fun `propagates read errors`() {
        val boom = object : InputStream() {
            override fun read(): Int = throw IOException("socket reset")
        }
        try {
            WebDavClient.readBounded(boom, 8L, "p")
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("socket reset", e.message)
        }
    }

    /** 上限常量本身的口径：文本 1 MiB、二进制 6 MiB（依据见 WebDavClient 注释）。 */
    @Test
    fun `limits match the documented sizes`() {
        assertEquals(1L * 1024 * 1024, WebDavClient.TEXT_RESPONSE_LIMIT)
        assertEquals(6L * 1024 * 1024, WebDavClient.BINARY_RESPONSE_LIMIT)
        assertTrue(WebDavClient.BINARY_RESPONSE_LIMIT > WebDavClient.TEXT_RESPONSE_LIMIT)
    }
}
