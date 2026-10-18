package com.ashkb.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *
 * ---
 *
 * v1.2.10（审查第二批 HIGH-5）两处修复：
 *
 * 1. **不再在主线程做文件 IO**。`takeLast` 原先在 `AppShell` 的 `LaunchedEffect(Unit)` 里被直接
 *    调用，而它内部 `exists() / readText() / delete()` 三连是实打实的磁盘操作，冷启动时**两个文件
 *    各来一遍**。改为 `suspend` + `Dispatchers.IO`（调用方本就是协程，无需改结构）。
 *    `recordNonFatal` 改为投递到本文件的 [ioScope]——它的调用点（`AlarmRegister.set`、
 *    `AshkbApplication` 的启动兜底）都在非挂起位置，且都是「出错了顺手记一笔」，
 *    没有「记完才继续」的语义，故fire-and-forget 是安全的。
 * 2. **`last_nonfatal.txt` 有上限**。原先是 `appendText` 无脑追加：非致命失败一旦每次冷启动都发生
 *    （例如某条链持续失败），文件会无限增长，而它每次启动都要被整个读出来。
 *    现在超过 [MAX_NONFATAL_CHARS] 就只保留**最新的** [KEEP_NONFATAL_CHARS] 个字符，
 *    并从行首开始（不留半行），前缀一句截断标注——排查关心的是最近几次，不是全部历史。
 */
object CrashLogger {

    private const val TAG = "ASHKB-CRASH"
    private const val FILE_FATAL = "last_crash.txt"
    private const val FILE_NONFATAL = "last_nonfatal.txt"

    /**
     * v1.2.10（HIGH-5）：非致命留档的字符上限。超过就截断。
     *
     * 量级依据：单条堆栈（含 `Caused by` 与全部帧）通常几百到几千字符，32K 字符约等于
     * 「最近几十次失败」——足够覆盖「同一问题反复发生」与「多个不同问题」两种排查场景。
     */
    private const val MAX_NONFATAL_CHARS = 32 * 1024

    /** 触发截断后保留的**最新**字符数（取 [MAX_NONFATAL_CHARS] 的 1/4，留出继续追加的余量）。 */
    private const val KEEP_NONFATAL_CHARS = 8 * 1024

    /** 截断标注。刻意用纯 ASCII，避免它自己成为编码问题的现场。 */
    private const val TRUNCATED_MARK = "--- older entries dropped ---\n"

    /**
     * v1.2.10（HIGH-5）：非致命留档的写盘作用域。
     *
     * 用独立 scope 而非 `AshkbApplication.appScope`：本文件在 `install()` **之前**就可能被触碰
     * （留档自身不能依赖 Application 的其余初始化），且这块 IO 与业务生命周期无关。
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
     *
     * v1.2.10（HIGH-5）：改为投递到 [ioScope] 的后台写盘，调用方不再因留档而阻塞。
     * 函数仍是同步签名的「投递即返回」——调用点都在 `runCatching` 兜底路径上，
     * 没有「写完才能继续」的语义。
     */
    fun recordNonFatal(context: Context, label: String, error: Throwable) {
        val dir = context.filesDir
        val text = buildString {
            append("time=").append(LocalDateTime.now())
                .append(" label=").append(label).append('\n')
            append(scrubCredentials(Log.getStackTraceString(error)))
            append("\n\n")
        }
        ioScope.launch {
            runCatching { appendBounded(File(dir, FILE_NONFATAL), text) }
        }
    }

    /**
     * v1.2.10（HIGH-5）：带上限的追加。
     *
     * 先读现有内容（这两个文件都在几十 KB 量级，读一次比维护额外的长度状态更不容易出错——
     * 长度状态会被外部删除文件、进程被杀等情况弄脏），拼上新内容，超限则只保留**最新**的一段，
     * 并从行首切起（不留半行，否则 `summary` 的第一行会是垃圾）。
     */
    private fun appendBounded(file: File, text: String) {
        val existing = if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
        val merged = existing + text
        if (merged.length <= MAX_NONFATAL_CHARS) {
            file.appendText(text)
            return
        }
        val tail = merged.takeLast(KEEP_NONFATAL_CHARS)
        val aligned = tail.substringAfter('\n', tail)
        file.writeText(TRUNCATED_MARK + aligned)
    }

    private fun write(context: Context, name: String, thread: Thread, error: Throwable) {
        val text = buildString {
            append("time=").append(LocalDateTime.now()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append(scrubCredentials(Log.getStackTraceString(error)))
        }
        File(context.filesDir, name).writeText(text)
    }

    /**
     * 读取并清空上次记录（优先致命崩溃，非致命一并带出并标注）；无记录返回 null。
     *
     * v1.2.10（HIGH-5）：改为 `suspend` + [Dispatchers.IO]——调用方是 `AppShell` 的
     * `LaunchedEffect`，本来就在协程里，改挂起零成本；而原先这一步在冷启动的
     * **帧渲染路径**上做了 2 组 `exists()/readText()/delete()`。
     */
    suspend fun takeLast(context: Context): String? = withContext(Dispatchers.IO) {
        val fatal = readAndClear(context, FILE_FATAL)
        val nonFatal = readAndClear(context, FILE_NONFATAL)
        when {
            fatal != null && nonFatal != null ->
                nonFatal + context.getString(R.string.ui_crash_fatal_separator) + fatal
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

