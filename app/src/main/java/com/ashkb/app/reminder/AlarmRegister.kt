package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.ashkb.app.CrashLogger
import kotlinx.coroutines.CancellationException

/**
 * v1.0.73（P0-1）：**闹钟注册的唯一入口**——注册失败绝不静默。
 *
 * 原实现是 6 处各自 `runCatching { setExactAndAllowWhileIdle / setWindow }`，异常被完全吞掉：
 * 无日志、无计数、无用户可见信号。配合小米 `MIUIOP(10014)`（精确闹钟）默认 `ignore` 的行为，
 * 结果是「所有闹钟静默丢弃 + 自检页显示已授权」的自相矛盾状态（第三份审查报告 P0-1）。
 *
 * 本入口统一做三件事：
 *  1. `setExactAndAllowWhileIdle`，精确闹钟不可用时降级 `setWindow(15min)`（原行为不变）；
 *  2. 失败 → `CrashLogger.recordNonFatal` 留档（首因可取）+ 计入 [ReminderHealth]；
 *  3. 每次尝试都更新台账，供自检页显示「尝试 N / 失败 M」。
 *
 * ---
 *
 * ### v1.2.11（缺陷工单 B09）：**轮次报告**
 *
 * [ReminderHealth] 的台账是**跨轮次累计**的（每次 `persist` 写的是 object 级累计值），
 * 它回答不了「刚才这一次保存有没有把提醒排上」。而 B09 的原症状正是：保存路径把整段重排
 * `runCatching` 掉，用户看到「已保存」、其实提醒一条都没排上，全程无声。
 *
 * 于是补一层**轮次**计数：[reschedule] 包住一轮重排，返回的 [ReminderHealth.RunReport] 就是
 * 「这一轮尝试了几个、失败几个、首个错误是什么、有没有被异常整体中断」。调用方据此把
 * 「数据已保存」与「提醒排上了没有」分开报告——数据写入成功绝不因重排失败而报成失败。
 *
 * 计数用 `ThreadLocal` 而不是全局字段：重排分散在各 ViewModel 的 `Dispatchers.IO` 上，
 * 同一时刻可能有两轮并行，全局字段会把它们串成一个数。`ThreadLocal` 天然按线程隔离，
 * 且 [reschedule] 用 `try/finally` 恢复上一层（支持嵌套）。
 */
internal object AlarmRegister {

    /**
     * 累计台账（跨轮次）用的计数器。
     *
     * **刻意保持非线程安全**：它服务的是 [ReminderHealth] 的「最近一次重排」展示，
     * 语义本就是近似值（多链并行时后写的覆盖先写的）。真正需要精确的**单轮**计数走
     * [current] 的 `ThreadLocal`，两者互不干扰。
     */
    private var source: String = "?"
    private var attempted: Int = 0
    private var failed: Int = 0
    private var lastError: String? = null

    /** 一轮重排内的注册计数（B09）。可变、非线程安全——只在 [reschedule] 的 block 内、单线程上使用。 */
    internal class RunTally {
        var attempted: Int = 0
            private set
        var failed: Int = 0
            private set
        var firstError: String? = null
            private set

        private var aborted: Boolean = false

        /** 记一次注册结果；失败时保留**首个**错误（后续失败通常同因，首个最有诊断价值）。 */
        fun record(ok: Boolean, error: String?) {
            attempted++
            if (ok) return
            failed++
            if (firstError == null) firstError = error
        }

        /** 整轮被异常中断（还没试到任何槽位就失败）：这是最严重的情形，单独标记。 */
        fun abort(error: String?) {
            aborted = true
            if (firstError == null) firstError = error
        }

        fun toReport(): ReminderHealth.RunReport = ReminderHealth.RunReport(
            attempted = attempted,
            failed = failed,
            aborted = aborted,
            firstError = firstError,
        )
    }

    private val current = ThreadLocal<RunTally?>()

    /**
     * 把 [block] 当作**一轮重排**执行，返回本轮报告（B09）。
     *
     * 异常处理的三条约定：
     *  - [CancellationException] **原样抛出**（协程取消不是失败，不该被记成「提醒排不上」，
     *    更不该被吞掉破坏结构化并发）；
     *  - 其它异常**不向外抛**，改为 `aborted = true` + `CrashLogger.recordNonFatal`——
     *    这正是工单要的「重排失败留档，且不把数据保存伪报成失败」；
     *  - 无论哪条路径，`finally` 都把 [current] 恢复成上一层，避免线程上残留半轮计数。
     *
     * 兜底捕获写成 `catch (expected: Throwable)` 而不是 `catch (e: Throwable)`：这是**预期内**
     * 的失败（系统拒绝精确闹钟、DAO 读失败），不是意外，detekt 的
     * `TooGenericExceptionCaught` 按变量名 `expected` 认定其为有意为之（项目铁律：新问题改代码，
     * 不进基线）。仍要捕 `Throwable` 而非 `Exception`——`AlarmManager` 在厂商 ROM 上可能抛
     * `LinkageError` 之类的 `Error`，漏掉就会让「保存已成功」的路径整段崩掉。
     */
    fun reschedule(context: Context, source: String, block: () -> Unit): ReminderHealth.RunReport {
        val tally = RunTally()
        val prev = current.get()
        current.set(tally)
        this.source = source
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (expected: Throwable) {
            tally.abort(expected.message ?: expected.javaClass.simpleName)
            CrashLogger.recordNonFatal(context, "提醒重排失败（$source）", expected)
        } finally {
            current.set(prev)
        }
        return tally.toReport()
    }

    fun set(
        context: Context,
        am: AlarmManager,
        at: Long,
        pi: PendingIntent,
        exact: Boolean,
        source: String,
    ): Boolean {
        this.source = source
        attempted++
        val result = runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
            }
        }.fold(
            onSuccess = {
                ReminderHealth.persist(context, source, attempted, failed, lastError)
                true
            },
            onFailure = { e ->
                failed++
                lastError = e.message ?: e.javaClass.simpleName
                CrashLogger.recordNonFatal(context, "闹钟注册失败（$source）", e)
                ReminderHealth.persist(context, source, attempted, failed, lastError)
                false
            },
        )
        current.get()?.record(result, if (result) null else lastError)
        return result
    }
}
