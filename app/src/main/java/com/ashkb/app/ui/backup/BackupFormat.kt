package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：formatFileSize / formatBytes。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。
// 唯一改动：把 1_024 / 1_048_576 提成命名常量——这两个数字在 detekt 基线里是按
// 「文件名 + 字面量」登记的，文件一改名基线就失配、会重新上报为 MagicNumber。
// 按项目口径（历史问题进基线、基线外新问题改代码），这里直接把问题消掉而不是改基线。

/** 1 KiB。 */
private const val BYTES_PER_KB = 1_024L

/** 1 MiB。 */
private const val BYTES_PER_MB = 1_048_576L

/** 字节大小格式化（固定 Locale.US，避免部分 locale 小数点变逗号）。 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> String.format(java.util.Locale.US, "%.1f MB", bytes / BYTES_PER_MB.toDouble())
    bytes >= BYTES_PER_KB -> String.format(java.util.Locale.US, "%.0f KB", bytes / BYTES_PER_KB.toDouble())
    else -> "$bytes B"
}

/** 附件本地占用：只分 MB / KB 两档（附件量级到不了 GB），同样锁 Locale 免小数点变逗号。 */
internal fun formatBytes(bytes: Long): String =
    if (bytes >= BYTES_PER_MB) String.format(java.util.Locale.US, "%.1f MB", bytes / BYTES_PER_MB.toDouble())
    else String.format(java.util.Locale.US, "%.0f KB", bytes / BYTES_PER_KB.toDouble())
