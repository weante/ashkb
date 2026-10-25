package com.ashkb.app.data.repo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 C03）：**附件导入必须是有界复制**。
 *
 * 缺陷本体：`importFromUri` 原来用 `input.copyTo(out)` 把文件整段落盘，**落完才检查大小**。
 * 用户误选一个 2 GB 的视频时，这 2 GB 会真切写进内部存储——低端机上可能直接把磁盘写满、
 * 把应用其他文件挤爆，之后即便删掉，全量写的代价也已经付过了。
 *
 * 这组用例只测 [AttachmentRepository.copyBounded] 这个纯字节搬运函数（不需要 ContentResolver、
 * 不需要 Robolectric）：它是否**提前停手**这件事，用计数流就能证明，而且比造一个超大真文件快得多。
 */
class AttachmentCopyBoundedTest {

    private val limit = AttachmentRepository.MAX_BYTES

    /** 无限流：只在被读了 [readCalls] 次后返回 EOF，用来证明「没有读到尾部」。 */
    private class CountingStream(private val chunk: Int, private val maxReads: Int) : InputStream() {
        var readCalls = 0
            private set
        var bytesServed = 0L
            private set

        override fun read(): Int = throw UnsupportedOperationException("应走 read(ByteArray)")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (readCalls >= maxReads) return -1
            readCalls++
            val n = minOf(chunk, len)
            for (i in 0 until n) b[off + i] = 0x41
            bytesServed += n
            return n
        }
    }

    @Test
    fun `小文件完整复制`() {
        val data = ByteArray(1234) { it.toByte() }
        val out = ByteArrayOutputStream()
        val copied = AttachmentRepository.copyBounded(ByteArrayInputStream(data), out)
        assertEquals(1234L, copied)
        assertTrue("字节内容应与源一致", data.contentEquals(out.toByteArray()))
    }

    @Test
    fun `恰好等于上限的文件不被截断`() {
        // 边界最容易写错的地方：若判据写成 `>=`，一个合法的 20 MB 文件会被误判超限。
        val exact = ByteArray(limit.toInt()) { 0x42 }
        val out = ByteArrayOutputStream()
        val copied = AttachmentRepository.copyBounded(ByteArrayInputStream(exact), out)
        assertEquals(limit, copied)
        assertEquals("恰好等于上限时不应丢字节", limit, out.size().toLong())
    }

    @Test
    fun `超限文件在读到上限之后立刻停手`() {
        // 源里有 10 个「上限」大小的数据（远超 MAX_BYTES）。函数必须早停：
        // 最多只比上限多写 1 个缓冲区。
        val reads = 10 * ((limit / (8 * 1024)).toInt() + 2)
        val src = CountingStream(chunk = 8 * 1024, maxReads = reads)
        val out = ByteArrayOutputStream()
        val copied = AttachmentRepository.copyBounded(src, out)

        assertTrue("必须报出超限（> MAX_BYTES），实际 $copied", copied > limit)
        assertTrue(
            "写了 $copied 字节，远超上限 + 一个缓冲区——说明没有提前停手",
            copied <= limit + 8 * 1024,
        )
        assertTrue(
            "读了 ${src.readCalls} 次，应在达到上限后立即停止，而不是读到源末尾",
            src.readCalls < reads,
        )
        assertTrue("源里还有大量数据没被读走", src.bytesServed < limit + 2L * 8 * 1024)
    }

    @Test
    fun `读中途抛异常时已写字节数不对外承诺`() {
        // 契约：本函数不吞异常——调用方（importFromUri）负责删掉半截文件。
        val src = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served >= 8 * 1024) throw IOException("模拟存储拔出")
                served += 1024
                return 1024
            }
        }
        val out = ByteArrayOutputStream()
        val thrown = try {
            AttachmentRepository.copyBounded(src, out)
            null
        } catch (e: IOException) {
            e
        }
        assertNotNull("读取异常必须向上抛，否则调用方无从知道要删半截文件", thrown)
        assertEquals("模拟存储拔出", thrown?.message)
        assertTrue("半截字节已经写出去了（由调用方删除文件）", out.size() > 0)
    }

    @Test
    fun `空流不产生文件内容`() {
        val out = ByteArrayOutputStream()
        val copied = AttachmentRepository.copyBounded(ByteArrayInputStream(ByteArray(0)), out)
        assertEquals(0L, copied)
        assertEquals(0, out.size())
    }
}
