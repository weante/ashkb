package com.ashkb.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 恢复串行化闸门（v1.2.15）。
 *
 * ### 为什么把锁从 `BackupRepository` 里抽出来
 *
 * `restore()` 是全应用唯一「清表 + 重写全部医疗记录」的路径，并发两次会静默半恢复。
 * 它此前已经加了 `Mutex`，但那条互斥**零测试覆盖**——原因很实在：`restore()` 在 JVM 上
 * 跑不起来。它要 `AndroidKeyStore`（`VaultKeyStore.getOrCreate()` 立刻抛
 * `KeyStoreException: AndroidKeyStore not found`），还要真实 Room 库与 `filesDir` 下的
 * 加密快照。于是「两次并发恢复会不会交错」这条最该被机器守住的性质，只能靠人读代码相信。
 *
 * 抽出来的只是**锁本身**，业务逻辑一行没动：`BackupRepository.restore` 仍然在闸门里
 * 做完它的「pre-restore 快照 → 覆盖写入 → 双校验 → 台账登记」五步。
 *
 * ### 被抽出来之后能测什么
 *
 * [Mutex] 的三条语义在这里都是可断言的行为，而不是注释：
 *  - **互斥**：任意时刻至多一个临界区在跑（并发 N 个任务时实测峰值并发数为 1）；
 *  - **不丢事**：后到者排队而不是被丢弃（用户点了「恢复」，就该恢复完）；
 *  - **失败不粘锁**：临界区抛异常或协程被取消后，锁必须释放——否则一次失败的恢复
 *    会让 App 在本次进程内再也恢复不了，而那正是用户最想重试的时刻。
 *
 * `Mutex` 本身不保证公平（不保证先来先得），所以测试只断言集合而不是顺序。
 */
internal class RestoreGate {
    private val mutex = Mutex()

    /**
     * 串行执行 [block]。
     *
     * 返回值原样透传；异常原样抛出（[withLock] 在 `finally` 里释放锁，取消时同样如此）。
     */
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
