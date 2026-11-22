package com.ashkb.app.domain

/**
 * 附件在 WebDAV 上的路径口径（v1.0.35）。
 *
 * 布局：`ashkb/attachments/<YYYY-MM-DD>/<附件id>.enc`
 *  - 日期文件夹取**附件内容日期**（`createdAt` 的日期）——与 App 内列表排序一致，网页端浏览也最直观
 *  - 文件名 = 附件 id + `.enc`，**刻意不含原始文件名**：像「化验单-类风湿因子-张三.pdf」这种明文名
 *    即使内容加密也已泄露病情，所以远端一律用不可读的 id 命名，浏览走 App
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object AttachmentPath {

    const val DIR = "ashkb/attachments"
    const val EXT = ".enc"

    /** 日期文件夹名；`createdAt` 形如 `2026-09-21T10:30:00`。异常输入退回固定占位目录，不抛异常。 */
    fun folderOf(createdAtIso: String): String {
        val date = createdAtIso.take(10)
        return if (DATE_PATTERN.matches(date)) date else FALLBACK_FOLDER
    }

    /** 远端相对路径（不含 `ashkb/attachments/` 前缀），存进 `checkup_attachments.remote_path`。 */
    fun remotePathOf(createdAtIso: String, attachmentId: String): String =
        "${folderOf(createdAtIso)}/${fileNameOf(attachmentId)}"

    /** 远端文件名。 */
    fun fileNameOf(attachmentId: String): String = attachmentId + EXT

    /** 拼成 WebDAV 上的完整相对路径（相对用户配置的基址）。 */
    fun fullPathOf(remotePath: String): String = "$DIR/$remotePath"

    /**
     * 校验 remote_path 形状，防止备份里的脏值拼出越权路径。
     *
     * v1.0.43 收紧：原先只拒 `..` / `\` / 前导 `/`，但**不拒 `%`** ——
     * `2026-09-21/a%2f%2e%2e%2fx.enc` 恰好 1 个字面 `/`、无字面 `..`、以 `.enc` 结尾，
     * 可整体通过；服务端若对路径做百分号解码，`%2f%2e%2e` 会还原成 `/..` 从而越出
     * `ashkb/attachments/`（可覆盖 / 删除基址下其它文件）。
     * 现改为**逐段白名单字符**（字母数字 + `.` `_` `-`），并同时校验日期目录形状——
     * 于是 `requirePath` 与「可清理形状」判定同一强度，不再存在「较弱的那个」。
     */
    fun isValidRemotePath(p: String): Boolean {
        if (p.isBlank() || !p.endsWith(EXT) || p.count { it == '/' } != 1) return false
        val folder = p.substringBefore('/')
        val file = p.substringAfter('/')
        if (!isValidFolder(folder)) return false
        if (file.removeSuffix(EXT).isEmpty()) return false     // 必须存在文件名主干（`.enc` 本身不算）
        return SEGMENT_PATTERN.matches(folder) && SEGMENT_PATTERN.matches(file)
    }

    /** 日期目录名是否为本应用可生成（合法 `YYYY-MM-DD` 或脏数据占位目录）。 */
    fun isValidFolder(folder: String): Boolean =
        DATE_PATTERN.matches(folder) || folder == FALLBACK_FOLDER

    /**
     * 本应用可能生成的远端路径（合法日期目录 + `.enc` 文件）。
     *
     * 远端校验据此决定「哪些文件才允许被清理」——`ashkb/attachments/` 是专用目录，但用户
     * 完全可能在网页端往里放别的东西（如 `readme.txt`、非日期目录），那些一律不碰。
     *
     * v1.0.43：与 [isValidRemotePath] 同为严格形状校验（后者已内含目录校验），保留此别名
     * 以免改动既有调用点语义。
     */
    fun isManagedRemotePath(p: String): Boolean = isValidRemotePath(p)

    /** 远端与本地记录的差集（纯函数，v1.0.36 远端校验的核心判定）。 */
    data class Reconcile(val missing: Set<String>, val orphans: Set<String>)

    /**
     * 比对远端实际文件集与本地记录：
     *  - `missing`：本地有记录、远端没有 → 需补传（远端被手动删除的情形）
     *  - `orphans`：远端有文件、本地无记录 → 需清理（换机残留 / 本地已删；仅限可管理形状）
     */
    fun reconcile(expected: Set<String>, remote: Set<String>): Reconcile = Reconcile(
        missing = expected - remote,
        orphans = remote.filterTo(mutableSetOf()) { isManagedRemotePath(it) && it !in expected },
    )

    // ---- v1.2.21（独立审查 F1）：本地附件的文件名校验 ----
    //
    // 背景：`checkup_attachments.file_name` 是**备份里的外部输入**。攻击者可以给用户一份
    // 口令已知的备份，把某条附件的 `file_name` 写成 `../../databases/app.db`——恢复后该值
    // 原样落库，`AttachmentRepository.fileOf()` 一拼就跑到附件目录之外，删除 / 读取 / 写入
    // / 分享四条路径全部越界。威胁模型与 `isValidRemotePath` 的注释里写的是同一个。
    //
    // 分两道闸：
    //  · [isValidLocalFileName]：**严格形状**，用在恢复侧。本机只会生成 `catt-<毫秒>.<扩展名>`，
    //    所以「不是这个形状」就整批拒绝——宁可拒绝一份畸形备份，也不让脏值进库。
    //  · [isSafeLocalSegment]：**最小安全约束**（只能是单个路径段），用在拼接侧做纵深防御。
    //    即便某条脏数据漏过了恢复侧，`fileOf()` 也拼不出目录之外的路径。

    /** 本应用生成的本地附件名主干前缀（相机与导入都是 `catt-<毫秒时间戳>.<扩展名>`）。 */
    private const val LOCAL_FILE_PREFIX = "catt-"

    /** 本地文件名长度上限：正常形状约 20 字符，留足余量但拒绝超长垃圾。 */
    private const val MAX_LOCAL_FILE_NAME_LEN = 120

    /** 扩展名长度上限——与 `AttachmentRepository.importFromUri` 的 `take(5)` 保持一致。 */
    private const val MAX_LOCAL_EXT_LEN = 5

    /**
     * 本地附件文件名是否为本应用可生成的形状（恢复侧的**形状白名单**）。
     *
     * 形状：`catt-<纯数字主干>.<1–5 字符扩展名>`，扩展名不含 `/` `\` `:` `.` 与控制字符。
     * 用白名单而不是「过滤危险字符」，是因为黑名单永远漏（`%2f`、Unicode 分隔符、
     * Windows 盘符前缀……）；而本机生成的名字形状是固定的，白名单没有误伤面。
     *
     * 扩展名刻意允许非 ASCII（老版本曾把中文文件名的末段当作扩展名），
     * 只要不含路径分隔符就无法越界。
     */
    fun isValidLocalFileName(name: String): Boolean {
        if (name.length > MAX_LOCAL_FILE_NAME_LEN) return false
        if (!name.startsWith(LOCAL_FILE_PREFIX)) return false
        val dot = name.lastIndexOf('.')
        if (dot <= LOCAL_FILE_PREFIX.length) return false          // 必须存在 `catt-<数字>` 主干
        val stem = name.substring(LOCAL_FILE_PREFIX.length, dot)
        if (stem.isEmpty() || !stem.all { it in '0'..'9' }) return false
        val ext = name.substring(dot + 1)
        if (ext.length !in 1..MAX_LOCAL_EXT_LEN) return false
        return ext.none { it == '/' || it == '\\' || it == ':' || it == '.' || it.isISOControl() }
    }

    /**
     * 本地文件名是否只能解析成附件目录下的**单个路径段**（拼接侧的纵深防御）。
     *
     * 比 [isValidLocalFileName] 宽松：不要求 `catt-` 前缀与纯数字主干，只为兼容历史数据与
     * 单测夹具；但只要含分隔符 / 盘符冒号 / 控制字符，或本身是 `.` / `..`，就一律拒绝——
     * 这几条足以保证 `File(dir, name)` 不可能落在 `dir` 之外。
     */
    fun isSafeLocalSegment(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_LOCAL_FILE_NAME_LEN) return false
        if (name == "." || name == "..") return false
        return name.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }
    }

    private const val FALLBACK_FOLDER = "0000-00-00"
    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

    /**
     * 单个路径段的允许字符：字母数字 + `.` `_` `-`。
     * 刻意排除 `%`（百分号解码可绕过 `..` 检查）、`?` / `#`（会截断 URL）与空白 / 控制字符。
     */
    private val SEGMENT_PATTERN = Regex("[A-Za-z0-9._-]+")
}
