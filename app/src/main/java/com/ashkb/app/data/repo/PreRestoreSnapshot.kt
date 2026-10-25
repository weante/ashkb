package com.ashkb.app.data.repo

import com.ashkb.app.data.backup.BackupEngine
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * v1.2.11（缺陷工单 B07）：**pre-restore 快照的应用内撤销**。
 *
 * ### 缺陷原状
 * `BackupRepository.restore()` 每次恢复前都会把当前库导出一份加密快照写到
 * `files/backups/pre-restore-<yyyyMMdd-HHmmss>.ashkb`——那是用户误恢复后唯一的退路。
 * 但备份页从来没有列出、也没有选择它的路径：用户只能靠文件管理器进私有目录（做不到），
 * 或重新恢复另一份备份（手上未必有）。工单因此判定「退路存在但不可达」。
 *
 * ### 这个文件负责什么
 * 只负责**读侧**：命名规则（与写侧同一套字面量）、列举、挑选最近一次、有界读取。
 * 写侧仍在 [BackupRepository.restore] 里（快照必须在覆盖写入之前、用本机密钥加密，
 * 与恢复同处一个互斥区，不能搬出来）。
 *
 * ### 为什么命名工具是文件级 `internal` 函数
 * 它们要被 JVM 单测直接调用（`uniquePreRestoreName` 的碰撞后缀、`parsePreRestoreStamp`
 * 的解析、`isPreRestoreName` 的路径穿越拒绝），而 `BackupRepository` 的 companion 是
 * `private`。放在同包的文件级声明，`restore()` 里无需 import 就能引用，测试也够得着。
 *
 * 命名与解析**必须**与写侧逐字一致：写侧用 [PRE_RESTORE_STAMP] 生成，读侧用
 * [parsePreRestoreStamp] 反向解析。两处共用同一个 `DateTimeFormatter` 常量，
 * 避免「改了 pattern 忘了另一边」。
 */

/** 快照文件名前缀。与 [BackupRepository.restore] 写侧同源。 */
internal const val PRE_RESTORE_PREFIX = "pre-restore-"

/** 快照文件名后缀。刻意与用户备份（`ashkb-*.ashkb`）区分，列举时不会误抓。 */
internal const val PRE_RESTORE_SUFFIX = ".ashkb"

/** 快照文件名里的时间戳格式（到秒）。写侧生成、读侧解析共用这一个实例。 */
internal val PRE_RESTORE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** 碰撞后缀的上限。到这个数还撞，说明目录里堆了上千份同秒快照，宁可换秒也不再加后缀。 */
private const val MAX_COLLISION_SUFFIX = 999

/**
 * `yyyyMMdd-HHmmss` 的字符长度（8 + 1 + 6）。
 *
 * 解析碰撞后缀时要先切出这 15 个字符再 parse；抽成常量是为了让「切点」只有一处定义，
 * 也避免裸数字在 `parsePreRestoreStamp` / `preRestoreCollisionIndex` 里各写一遍。
 */
private const val STAMP_LENGTH = 15

/**
 * 生成一份**不与已有文件重名**的快照文件名。
 *
 * 写侧原实现只有到秒的时间戳，两次恢复落在同一秒（撤销紧接着恢复、脚本化演练、秒级时钟
 * 回拨）时第二份会静默覆盖第一份——而被覆盖的那份恰好是用户唯一的退路。工单第 6 条
 * 明确「快照轮换/清理不得把唯一退路静默删掉」，故这里碰撞时追加 `-2`/`-3`…
 *
 * @param exists 判断「该文件名是否已被占用」。传入闭包而不是目录，是为了让这条规则
 *   可以在纯 JVM 单测里验（不需要真文件系统）。
 */
internal fun uniquePreRestoreName(stamp: String, exists: (String) -> Boolean): String {
    val base = "$PRE_RESTORE_PREFIX$stamp"
    val first = "$base$PRE_RESTORE_SUFFIX"
    if (!exists(first)) return first
    for (n in 2..MAX_COLLISION_SUFFIX) {
        val candidate = "$base-$n$PRE_RESTORE_SUFFIX"
        if (!exists(candidate)) return candidate
    }
    // 极端情形：同秒已堆满 MAX_COLLISION_SUFFIX 份。仍然不能覆盖——用毫秒级时间戳兜底。
    return "$PRE_RESTORE_PREFIX${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))}$PRE_RESTORE_SUFFIX"
}

/**
 * 这个文件名是不是本应用写出的 pre-restore 快照。
 *
 * 判据刻意收紧到「前缀 + 可解析的时间戳 + 后缀 + 不含路径分隔符与 `..`」：
 * 列举结果会直接喂给 [PreRestoreStore.read] 去拼路径，任何能拼出 `../` 的名字都必须
 * 在**进入列表之前**被拒掉，而不是等到读文件时才靠 `canonicalPath` 兜。
 */
internal fun isPreRestoreName(name: String): Boolean {
    if (!name.startsWith(PRE_RESTORE_PREFIX) || !name.endsWith(PRE_RESTORE_SUFFIX)) return false
    if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
    return parsePreRestoreStamp(name) != null
}

/**
 * 从文件名解析快照的生成时间；不是合法快照名时返回 null。
 *
 * 碰撞后缀（`-2`/`-3`…）要剥掉再解析：`pre-restore-20261026-013000-2.ashkb` 的时间
 * 仍是 `20261026-013000`，不能因为多了后缀就判成非法（否则第 2 份之后的快照全被藏起来）。
 */
internal fun parsePreRestoreStamp(name: String): LocalDateTime? {
    if (!name.startsWith(PRE_RESTORE_PREFIX) || !name.endsWith(PRE_RESTORE_SUFFIX)) return null
    val core = name.removePrefix(PRE_RESTORE_PREFIX).removeSuffix(PRE_RESTORE_SUFFIX)
    // 剥掉可选的 `-<n>` 碰撞后缀（只认纯数字，避免把畸形名当合法）
    val stamp = if (core.length > STAMP_LENGTH && core[STAMP_LENGTH] == '-') {
        core.substring(0, STAMP_LENGTH)
    } else {
        core
    }
    if (stamp.length != STAMP_LENGTH) return null
    return try {
        LocalDateTime.parse(stamp, PRE_RESTORE_STAMP)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * 同秒快照的写入次序：无后缀 = 第 1 份，`-2` = 第 2 份，`-3` = 第 3 份……
 *
 * 排序要它是因为「同秒两份」时时间戳完全相同，光比时间分不出谁更新——
 * 而后写的那份才是**更近**的状态，撤销入口必须指向它。
 */
internal fun preRestoreCollisionIndex(name: String): Int {
    val core = name.removePrefix(PRE_RESTORE_PREFIX).removeSuffix(PRE_RESTORE_SUFFIX)
    if (core.length <= STAMP_LENGTH || core[STAMP_LENGTH] != '-') return 1
    return core.substring(STAMP_LENGTH + 1).toIntOrNull() ?: 1
}

/**
 * 按「最近一次在前」排序；时间相同（同秒两份）时按碰撞序号倒序，让后写的 `-2` 排在前面。
 *
 * 排序是纯函数，单测可脱离文件系统验；[PreRestoreStore.list] 只负责把 `File` 名字喂进来。
 */
internal fun sortPreRestoreNames(names: List<String>): List<String> =
    names.filter { isPreRestoreName(it) }
        .sortedWith(
            compareByDescending<String> { parsePreRestoreStamp(it) }
                .thenByDescending { preRestoreCollisionIndex(it) },
        )

/**
 * 一份 pre-restore 快照在 UI 上的全部信息。
 *
 * @param createdAt 由文件名解析；解析不出时为 null（此时 [available] 必为 false）
 * @param available 「可用状态」：文件在、可读、非空、且不超过 [BackupEngine.PICK_LIMIT_BYTES]。
 *   工单要求把可用状态显示出来，因为「文件被系统清理了」和「文件还在但坏了」对用户
 *   是两种完全不同的处境——前者要用户别再指望它，后者提示重试或找别的备份。
 */
data class PreRestoreSnapshot(
    val name: String,
    val createdAt: LocalDateTime?,
    val sizeBytes: Long,
    val available: Boolean,
)

/**
 * 读侧：列出 / 挑选 / 有界读取 `files/backups/` 下的 pre-restore 快照。
 *
 * 刻意独立于 [BackupRepository]：那个类的成员函数已接近 detekt 的
 * `TooManyFunctions.thresholdInClasses: 40`，而撤销入口只需要「读」这一半能力，
 * 不需要把仓库的全部依赖（Room、Vault、WebDAV）一起带进 ViewModel。
 */
class PreRestoreStore(private val context: android.content.Context) {

    private fun dir(): File = File(context.filesDir, "backups")

    /**
     * 列出全部快照，最近一次在前。目录不存在时返回空表（**不创建**目录——
     * 这里只读，创建目录是写侧的事）。
     */
    fun list(): List<PreRestoreSnapshot> {
        val files = dir().listFiles { f -> f.isFile } ?: return emptyList()
        val byName = files.associateBy { it.name }
        return sortPreRestoreNames(files.map { it.name }).mapNotNull { name ->
            byName[name]?.let { f ->
                val size = runCatching { f.length() }.getOrDefault(0L)
                PreRestoreSnapshot(
                    name = name,
                    createdAt = parsePreRestoreStamp(name),
                    sizeBytes = size,
                    available = size > 0 && BackupEngine.withinPickLimit(size),
                )
            }
        }
    }

    /** 最近一次可读的快照；没有则 null（UI 据此决定**不渲染**撤销入口）。 */
    fun latest(): PreRestoreSnapshot? = list().firstOrNull { it.available }

    /**
     * 有界读取快照字节。
     *
     * 复用 [BackupEngine.readBounded]：先看声明长度、再逐块累计，超限抛带 `resId` 的
     * [BackupEngine.BackupException]，UI 侧既有的 `errText` 就能把上限报给用户。
     * 路径用 `File(dir, name)` 而不是 `File(name)`——名字已在 [isPreRestoreName] 里
     * 拒绝过路径分隔符与 `..`，双重保险。
     */
    fun read(snapshot: PreRestoreSnapshot): ByteArray {
        if (!isPreRestoreName(snapshot.name)) {
            throw BackupEngine.BackupException(
                "非法快照文件名：${snapshot.name}",
                resId = com.ashkb.app.R.string.backup_snapshot_missing,
            )
        }
        val f = File(dir(), snapshot.name)
        if (!f.isFile) {
            throw BackupEngine.BackupException(
                "快照文件不存在：${snapshot.name}",
                resId = com.ashkb.app.R.string.backup_snapshot_missing,
            )
        }
        return f.inputStream().use { BackupEngine.readBounded(it, BackupEngine.PICK_LIMIT_BYTES, snapshot.name) }
    }
}
