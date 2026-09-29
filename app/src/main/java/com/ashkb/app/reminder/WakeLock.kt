package com.ashkb.app.reminder

import android.content.Context
import android.os.PowerManager

/**
 * v1.0.73（P1-3）：广播接收器异步工作的**唤醒锁**。
 *
 * 问题（第三份审查报告 P1-3，已逐行复核）：清单里没有 `WAKE_LOCK`，所有 receiver 靠
 * `goAsync()` + `Dispatchers.IO` 协程干活。`goAsync()` 只保证「进程不被立刻回收」，
 * **不保证 CPU 不休眠**——若设备在协程中途进入 suspend（Doze / 灭屏后很快休眠），
 * 通知会丢失，而且**下一级升级闹钟也不会排上**，链条一直断到下次打开 App。
 *
 * 因此每个走 `goAsync()` 的 receiver 都应：进入时 [acquire]（带超时上限，避免死锁），
 * `finally` 里释放（见各 receiver 的 `isHeld` 判断）。
 *
 * 超时取 [TIMEOUT_MS]：足够跑完一次查库 + 发通知 + 排下一个闹钟，又能兜住忘记释放的情况。
 */
internal object WakeLock {
    private const val TAG = "ashkb:reminder"
    private const val TIMEOUT_MS = 60_000L

    /** 取不到（权限缺失等）时返回 null——调用方按 `lock?.` 处理，不影响主流程。 */
    fun acquire(context: Context): PowerManager.WakeLock? = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply { acquire(TIMEOUT_MS) }
    }.getOrNull()
}
