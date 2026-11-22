package com.ashkb.app

import android.content.Context
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.22（独立审查 F5）：非致命留档的**写盘串行化**。
 *
 * 原实现每次 `recordNonFatal` 都在多线程的 `Dispatchers.IO` 上起一个独立协程，
 * 落盘逻辑是「读现有内容 → 拼接 → 写回」三步；两个失败同时发生时，
 * 后写的会用**同一份旧内容**覆盖先写的（丢记录），或两次 `appendText` 交错（内容半截）。
 *
 * 现在两条约束一起用：`Dispatchers.IO.limitedParallelism(1)` 让后台写入串行，
 * 再加一把 [java.util.concurrent.locks.ReentrantLock] 兜住「崩溃线程写致命日志」
 * 与「后台写非致命日志」这两条不同路径。
 *
 * 这里刻意只压 **40 条**：留档有 32 KB 上限，超限会保留最新一段并打截断标记，
 * 那时测到的就不是「丢记录」而是「截断」（另一条既有行为）。
 * 单条实际约 550 字节（`printStackTrace` 打印的是真实栈，不受空栈覆写影响），
 * 40 条约 22 KB，稳稳落在上限内。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashLoggerConcurrencyTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val nonFatalFile: File get() = File(context.filesDir, "last_nonfatal.txt")

    @Before
    fun clean() {
        nonFatalFile.delete()
        File(context.filesDir, "last_crash.txt").delete()
    }

    @After
    fun tearDown() {
        nonFatalFile.delete()
        File(context.filesDir, "last_crash.txt").delete()
    }

    /** 普通异常即可：留档走 `Log.getStackTraceString`，打印的是真实栈。 */
    private fun tinyError(message: String): Throwable = IllegalStateException(message)

    @Test
    fun `并发记录不丢条目且不重复`() {
        val threads = 8
        val perThread = 5
        val total = threads * perThread
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)

        repeat(threads) { t ->
            pool.execute {
                try {
                    repeat(perThread) { i ->
                        CrashLogger.recordNonFatal(context, "T$t-$i", tinyError("boom"))
                    }
                } finally {
                    done.countDown()
                }
            }
        }

        assertTrue("并发线程未在超时内完成", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        val text = awaitEntries(total)

        for (t in 0 until threads) {
            for (i in 0 until perThread) {
                val label = "label=T$t-$i"
                assertEquals("条目 $label 出现次数不为 1", 1, countOccurrences(text, label))
            }
        }
    }

    @Test
    fun `takeLast 读走全部条目并清空文件`() {
        repeat(6) { i -> CrashLogger.recordNonFatal(context, "once-$i", tinyError("boom")) }
        awaitEntries(6)

        val text = kotlinx.coroutines.runBlocking { CrashLogger.takeLast(context) }

        assertTrue("takeLast 返回空", text != null && text.isNotBlank())
        repeat(6) { i -> assertTrue("缺少 once-$i", text!!.contains("label=once-$i")) }
        assertTrue("读走后文件应被删除", !nonFatalFile.exists())
    }

    private fun countOccurrences(text: String, needle: String): Int =
        text.windowed(needle.length, 1).count { it == needle }

    /**
     * 等到文件里出现够数的条目。
     *
     * 写入是后台异步的，没有完成回调可等；这里轮询既有文件，超时就失败并给出实际条数，
     * 便于区分「真的丢了」和「跑得太慢」。
     */
    private fun awaitEntries(expected: Int): String {
        val deadline = System.currentTimeMillis() + 20_000
        var text = ""
        while (System.currentTimeMillis() < deadline) {
            text = if (nonFatalFile.exists()) runCatching { nonFatalFile.readText() }.getOrDefault("") else ""
            if (countOccurrences(text, "label=") >= expected) return text
            Thread.sleep(50)
        }
        val actual = countOccurrences(text, "label=")
        assertEquals(
            "等待超时：期望 $expected 条，实际 $actual 条，字符数 ${text.length}，" +
                "截断=${text.contains("older entries dropped")}",
            expected,
            actual,
        )
        return text
    }
}
