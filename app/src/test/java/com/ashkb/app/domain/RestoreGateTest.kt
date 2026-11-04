package com.ashkb.app.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.15：恢复串行化闸门的**行为**测试（HIGH-4 的补丁此前零覆盖）。
 *
 * ### 为什么不「两次真恢复并发跑一遍」
 * 真跑不起来。`BackupRepository.restore()` 在本机 JVM 上有两道硬门槛：`VaultKeyStore.getOrCreate()`
 * 走 `AndroidKeyStore`（实测抛 `KeyStoreException: AndroidKeyStore not found`），以及 pre-restore
 * 快照要往 `filesDir/backups` 落真实加密文件。所以「并发两次恢复会不会把库写坏」这件事，
 * 只能在**闸门这一层**验证——锁以下全是纯 Kotlin，锁以上是业务。
 *
 * 这正是把锁抽成 [RestoreGate] 的全部理由：抽出来之后，被守的性质从注释变成了断言。
 *
 * ### 这里的测试与「真恢复」的距离（诚实标注）
 * 断言的是**闸门的语义**：任意时刻至多一个临界区、后到者不被丢弃、抛异常/被取消后锁必须释放。
 * 它不能证明 `restore()` 的五步业务全对——那要真机。它能证明的是：即便业务里有慢 IO、
 * 有异常、有用户中途退出，互斥本身不会失效，也不会因为一次失败把 App 锁死到下次重启。
 *
 * 不加 `kotlinx-coroutines-test`：项目没有这个依赖，且这里要验的是**真实线程并发**
 * （虚拟时间调度器会把「同时进来」测成串行，反而验不出互斥）。
 *
 * ### 临界区里为什么用 `yield()` 而不是 `Thread.sleep()`
 * `yield()` 会交出线程但**不释放锁**——若没有锁，让出的这一刻正好是别人挤进来的窗口，
 * 于是重叠必然发生；有锁时它什么也挤不进来。用它既避免了阻塞调度线程，也让负向对照可靠复现。
 */
class RestoreGateTest {

    /**
     * 核心断言：N 个任务同时冲进闸门，临界区内的并发峰值必须是 1。
     *
     * 进入时 `inside++` 并记录峰值，做完一段会让出的工作后 `inside--`。若没有互斥，
     * 两个任务会同时读到 `inside == 1` 而峰值升到 2 以上。
     */
    @Test
    fun `并发任务在临界区内绝不重叠`() = runBlocking {
        val gate = RestoreGate()
        val inside = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val done = AtomicInteger(0)

        coroutineScope {
            repeat(16) {
                launch(Dispatchers.Default) {
                    gate.run {
                        val now = inside.incrementAndGet()
                        peak.updateAndGet { maxOf(it, now) }
                        repeat(50) { yield() }
                        inside.decrementAndGet()
                        done.incrementAndGet()
                    }
                }
            }
        }

        assertEquals("临界区内至多一个任务", 1, peak.get())
        assertEquals("后到者不得被丢弃：每个任务都要跑完", 16, done.get())
        assertEquals("退出后计数必须归零", 0, inside.get())
    }

    /**
     * 「排队执行」而不是「被丢弃」：用户点了两次恢复，第二次不该悄无声息地不发生。
     *
     * 这条对应 `restore()` KDoc 里的明确取舍——后到的恢复排队执行，因为用户点了就该恢复完。
     * 反面（用户想撤销）由 UI 的 `busy` 守卫与取消通道负责，不归闸门管。
     */
    @Test
    fun `后到的任务排队执行而不是被丢弃`() = runBlocking {
        val gate = RestoreGate()
        val executed = AtomicInteger(0)

        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) {
                    gate.run { executed.incrementAndGet() }
                }
            }
        }

        assertEquals("8 次调用必须执行 8 次", 8, executed.get())
    }

    /**
     * 失败不粘锁：第一次恢复在「双校验」阶段抛异常，用户正是最想重试的时刻。
     *
     * 若锁在异常路径上没被释放，第二次恢复会永久挂住——测试用 `withTimeout` 把它变成失败，
     * 而不是让整个测试套件卡死。
     */
    @Test
    fun `临界区抛异常后闸门必须可再次进入`() = runBlocking {
        val gate = RestoreGate()
        val boom = IllegalStateException("恢复写入失败")

        val thrown = runCatching {
            gate.run<Unit> { throw boom }
        }.exceptionOrNull()
        assertEquals("异常必须原样抛出，不能被吞掉", boom, thrown)

        // 第二次必须能进去，且能正常拿到返回值
        val result = withTimeout(5_000) { gate.run { 42 } }
        assertEquals(42, result)
    }

    /**
     * 取消不粘锁：用户在恢复过程中退出/取消，锁同样必须释放。
     *
     * 协程取消抛 `CancellationException`，`withLock` 靠 `finally` 释放——这里把它钉在测试里，
     * 防止将来有人把它「优化」成手写 `lock()/unlock()`（那样取消路径就不会释放了）。
     */
    @Test
    fun `临界区被取消后闸门必须可再次进入`() = runBlocking {
        val gate = RestoreGate()
        val entered = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            gate.run {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancel()
        job.join()

        val result = withTimeout(5_000) { gate.run { "recovered" } }
        assertEquals("recovered", result)
    }

    /** 返回值必须**原样透传**——闸门是纯粹的包装，不得吞值或改类型。 */
    @Test
    fun `返回值原样透传`() = runBlocking {
        val gate = RestoreGate()
        assertEquals("hello", gate.run { "hello" })
        assertEquals(0, gate.run { 0 })
        assertTrue(gate.run { true })
    }

    /**
     * 负向对照：证明上面的互斥断言**确实能失败**。
     *
     * 没有这一段，`assertEquals(1, peak)` 可能只是因为「测试本身没能让任务重叠」而恒真
     * （例如协程全在同一线程上串行执行）。这里用**完全相同的测量方法**跑一遍无锁版本，
     * 峰值必须大于 1——若它也是 1，说明测量方式失效，上面那条测试的绿灯就不可信。
     */
    @Test
    fun `负向对照 无锁时峰值必然大于一`() = runBlocking {
        val inside = AtomicInteger(0)
        val peak = AtomicInteger(0)

        coroutineScope {
            repeat(16) {
                launch(Dispatchers.Default) {
                    // 与上面的测试逐行对应，只是不经过 RestoreGate
                    val now = inside.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    repeat(50) { yield() }
                    inside.decrementAndGet()
                }
            }
        }

        assertTrue(
            "测量方式失效：无锁版本也没有产生重叠（peak=${peak.get()}），" +
                "则「并发任务在临界区内绝不重叠」的绿灯不可信",
            peak.get() > 1,
        )
    }
}
