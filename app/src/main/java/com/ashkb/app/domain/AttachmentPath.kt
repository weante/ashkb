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

    /** 校验 remote_path 形状，防止备份里的脏值拼出越权路径。 */
    fun isValidRemotePath(p: String): Boolean =
        p.isNotBlank() && p.endsWith(EXT) && !p.contains("..") &&
            !p.contains('\\') && !p.startsWith('/') && p.count { it == '/' } == 1

    private const val FALLBACK_FOLDER = "0000-00-00"
    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
}
