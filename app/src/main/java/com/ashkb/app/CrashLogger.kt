package com.ashkb.app

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime

/**
 * v1.0.40：崩溃日志留档（**只写本地 + Logcat，不上报**）。
 *
 * 背景：本应用是纯离线自用工具，没有崩溃上报通道；一旦冷启动闪退，开发者拿不到任何线索，
 * 只能靠猜——这正是 v1.0.39 闪退排查的困境。
 *
 * 做法：把未捕获异常的堆栈写入 `filesDir/last_crash.txt`（`adb pull` 可拉取），
 * 并在**下次启动**时由界面读取一次、以 Snackbar 摘要提示（便于截图反馈），读后即清。
 *
 * 与「零网络权限 / 不上报」的红线一致：本文件不含任何上传逻辑。
 */
object CrashLogger {

    private const val TAG = "ASHKB-CRASH"
    private const val FILE_NAME = "last_crash.txt"

    /** 安装全局未捕获异常处理器；记录后仍交还原处理器（保持原有崩溃行为）。 */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { Log.e(TAG, "uncaught exception", error) }
            runCatching { record(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val text = buildString {
            append("time=").append(LocalDateTime.now()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append(Log.getStackTraceString(error))
        }
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    /** 读取并清空上次崩溃全文（下次启动调用；无记录返回 null）。 */
    fun takeLast(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.delete() }
        return text?.takeIf { it.isNotBlank() }
    }

    /** 摘要：异常首行 + 第一条应用内栈帧（够定位，也能塞进 Snackbar）。 */
    fun summary(text: String): String {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val head = lines.firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?: lines.firstOrNull().orEmpty()
        val where = lines.firstOrNull { it.startsWith("at com.ashkb") }
        return listOfNotNull(head.ifBlank { null }, where).joinToString(" @ ")
    }
}
