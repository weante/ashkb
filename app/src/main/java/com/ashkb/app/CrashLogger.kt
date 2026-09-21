package com.ashkb.app

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime

/**
 * v1.0.40：崩溃日志留档（**只写本地 + Logcat，不上报**）。
 *
 * v1.0.41 两处改进（起因：v1.0.39/40 的 `NoClassDefFoundError: K1.n` 极难定位）：
 * 1. **被业务兜底吞掉的异常也留档**（`last_nonfatal.txt`）——那次崩溃的真实链条是
 *    「启动期首次触碰 → 初始化失败 → 被 `runCatching` 吞掉 → 稍后二次触碰才抛
 *    `NoClassDefFoundError`」。只记未捕获异常会丢掉根因。
 * 2. **摘要带上 `Caused by` 与栈帧**——release 包类名经 R8 混淆（如 `K1.n`），
 *    原先「只取 `at com.ashkb` 帧」在混淆后恒为空，摘要退化成一行，无法定位；
 *    现取「异常首行 + Caused by 首行 + 前 4 帧」，可用 `mapping.txt` 反查。
 *
 * 与「零网络权限 / 不上报」的红线一致：本文件不含任何上传逻辑。
 */
object CrashLogger {

    private const val TAG = "ASHKB-CRASH"
    private const val FILE_FATAL = "last_crash.txt"
    private const val FILE_NONFATAL = "last_nonfatal.txt"

    /** 安装全局未捕获异常处理器；记录后仍交还原处理器（保持原有崩溃行为）。 */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { Log.e(TAG, "uncaught exception", error) }
            runCatching { write(context, FILE_FATAL, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * 记录**被业务代码兜底吞掉**的异常（非致命，追加写入，一次可见多次失败）。
     * 与致命崩溃分开存：`takeLast` 会同时取出并标注，避免前者被后者覆盖。
     */
    fun recordNonFatal(context: Context, label: String, error: Throwable) {
        runCatching {
            val text = buildString {
                append("time=").append(LocalDateTime.now())
                    .append(" label=").append(label).append('\n')
                append(scrubCredentials(Log.getStackTraceString(error)))
                append("\n\n")
            }
            File(context.filesDir, FILE_NONFATAL).appendText(text)
        }
    }

    private fun write(context: Context, name: String, thread: Thread, error: Throwable) {
        val text = buildString {
            append("time=").append(LocalDateTime.now()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append(scrubCredentials(Log.getStackTraceString(error)))
        }
        File(context.filesDir, name).writeText(text)
    }

    /** 读取并清空上次记录（优先致命崩溃，非致命一并带出并标注）；无记录返回 null。 */
    fun takeLast(context: Context): String? {
        val fatal = readAndClear(context, FILE_FATAL)
        val nonFatal = readAndClear(context, FILE_NONFATAL)
        return when {
            fatal != null && nonFatal != null ->
                nonFatal + "\n===== 致命崩溃 =====\n" + fatal
            fatal != null -> fatal
            else -> nonFatal
        }
    }

    private fun readAndClear(context: Context, name: String): String? {
        val f = File(context.filesDir, name)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.delete() }
        return text?.takeIf { it.isNotBlank() }
    }

    /**
     * 摘要：异常首行 + `Caused by` 首行 + 前 4 条栈帧。
     * 刻意**不**按包名过滤——release 包类名被混淆，过滤会得到空摘要。
     */
    fun summary(text: String): String {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val head = lines.firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?: lines.firstOrNull().orEmpty()
        val caused = lines.firstOrNull { it.startsWith("Caused by:") }
        val frames = lines.filter { it.startsWith("at ") }.take(4)
        return (listOfNotNull(head.ifBlank { null }, caused) + frames).joinToString("\n")
    }
}

/**
 * v1.0.44（S5）：崩溃留档落盘前的**凭据脱敏**。
 *
 * 堆栈 / 异常消息可能带上 WebDAV 地址里内嵌的凭据（`https://user:pass@host/dav`）。留档本身
 * 只写 app 私有目录（`allowBackup=false`）且永不上报，但医疗类应用应当主动划清边界：
 * **凭据一律不入盘**。只滤「URL 内嵌凭据」这一种形态，保留主机名以便定位（主机名不敏感）。
 *
 * Logcat 那一路仍是原始堆栈——Logcat 是瞬时的、且自 Android 4.1 起非特权应用读不到，
 * 真正的持久化边界是这里。
 *
 * 正则以字符类表达、**不含任何孤立花括号**（v1.0.39–42 的崩溃链正是孤立 `}` 在 Android ICU 下
 * 抛 `PatternSyntaxException`；本文件属于崩溃留档自身，更不能在这里埋雷）。
 */
internal val CREDENTIALS_IN_URL = Regex("(://)[^/@\\s]+@")

/**
 * 把 URL 内嵌的凭据段（`user:pass@`）收敛为 `***@`，主机名保留。
 *
 * 注：本行刻意不写出替换后的完整 URL 字面量——Kotlin 的块注释是**可嵌套**的，
 * 而「scheme 冒号 + 双斜杠 + 三个星号」恰好含 `斜杠+星号` 序列，会意外开启嵌套注释。
 */
internal fun scrubCredentials(text: String): String =
    CREDENTIALS_IN_URL.replace(text) { m -> m.groupValues[1] + "***@" }

