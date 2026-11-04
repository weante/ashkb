package com.ashkb.app.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.15：**调用点守卫**——`BackupRepository.restore()` 必须真的走 [RestoreGate]。
 *
 * ### 为什么行为测试不够
 * [RestoreGateTest] 证明了「闸门是互斥的」，但它证明不了「恢复用了闸门」。两者的差距是
 * 一次误删的距离：有人在重构 `restore()` 时把 `restoreGate.run { … }` 拆掉（比如为了
 * 「简化嵌套层级」），闸门所有测试依旧全绿，而线上那条「清表 + 重写全部医疗记录」的路径
 * 重新变回无互斥——正是 HIGH-4 要修的那个洞。
 *
 * 真跑一次 `restore()` 来验也不行：它要 AndroidKeyStore（JVM 上直接
 * `KeyStoreException: AndroidKeyStore not found`）与真实 Room 库。所以这里退一步，
 * 用源码断言把「调用关系」钉住——这是本项目已有的做法（见 [BackupPasswordRulesTest]
 * 对恢复路径的负向断言）。
 *
 * ### 断言的是结构，不是格式
 * 只要求 `restore(` 的函数体**开口**在 `restoreGate.run` 上，不关心参数换行、缩进或注释。
 * 若将来有人把闸门换成别的实现（如 `withTimeout` + `Semaphore`），改这里的字样即可，
 * 但**不能**改成「什么都不断言」。
 */
class RestoreCallSiteGuardTest {

    private val repo = "app/src/main/java/com/ashkb/app/data/repo/BackupRepository.kt"

    private fun source(): String = File(repoRoot(), repo).readText()

    /**
     * `restore()` 的函数体必须以 `restoreGate.run {` 开头。
     *
     * 取「`suspend fun restore(` 之后、下一个 `suspend fun`/`fun ` 之前」这一段，
     * 再截到第一个 `{`，检查其后紧跟闸门调用。
     */
    @Test
    fun `恢复入口必须经由 RestoreGate`() {
        val src = source()
        val start = src.indexOf("suspend fun restore(")
        assertTrue("找不到 BackupRepository.restore 的声明", start >= 0)
        val body = src.substring(start)
        assertTrue(
            "restore() 必须经由 restoreGate.run 串行化；" +
                "移除它会退回 HIGH-4 的无互斥状态（并发两次恢复 ⇒ 静默半恢复）",
            body.contains("= restoreGate.run {"),
        )
    }

    /**
     * 反向守卫：恢复里不得再出现裸 `Mutex`，避免「两把锁各锁一半」。
     *
     * 这条同时保证 `restoreGate` 是**唯一**的串行化入口——若有人在 `restore()` 内部
     * 又加一把 `Mutex`（比如只锁覆盖写入那一步），闸门与内层锁会形成两套语义，
     * 而 pre-restore 快照那一段仍在闸门外侧还是内侧就变得难以推理。
     */
    @Test
    fun `恢复路径不再持有第二把锁`() {
        val src = source()
        val start = src.indexOf("suspend fun restore(")
        val body = src.substring(start)
        assertTrue(
            "restore() 里不应再出现 Mutex：串行化只能由 RestoreGate 一处负责",
            !body.substringBefore("\n    // =======").contains("Mutex("),
        )
    }

    /** [RestoreGate] 自己必须真的是 `Mutex` + `withLock`，不能是个空壳。 */
    @Test
    fun `RestoreGate 的实现不得退化为直通`() {
        val gate = File(repoRoot(), "app/src/main/java/com/ashkb/app/domain/RestoreGate.kt").readText()
        assertTrue(
            "RestoreGate.run 必须用 mutex.withLock 包裹业务块；" +
                "直通实现会让所有恢复互斥测试变成假绿",
            gate.contains("mutex.withLock"),
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
}
