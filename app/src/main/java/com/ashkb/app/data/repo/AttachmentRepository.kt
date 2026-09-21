package com.ashkb.app.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.CheckupAttachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * v10（B10）：复诊附件归档——化验单 / 影像报告的拍照、相册图片或 PDF 存档。
 *
 * 文件落在应用内部存储 `filesDir/checkup_attachments/`（非缓存目录，不会被系统回收），
 * 数据库只存元数据（`checkup_attachments` 表）。
 *
 * ⚠️ **附件不随数据库备份**：`BackupEngine` 导出的是各表 JSON，不含二进制文件。
 * 换机恢复后附件需重新导入。这是为保住备份轻量（WebDAV 上传 120s 超时）的取舍。
 */
class AttachmentRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.checkupAttachmentDao()

    private val syncPrefs: SharedPreferences =
        context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)

    companion object {
        /** 单文件上限 20 MB——防止用户误选超大视频/文件把内部存储塞满。 */
        const val MAX_BYTES = 20L * 1024 * 1024

        private const val DIR = "checkup_attachments"
        private const val PREF_SYNC_ENABLED = "attachment_sync_enabled"
    }

    fun observeAll(): Flow<List<CheckupAttachment>> = dao.observeAll()

    fun observeByCheckup(checkupId: String): Flow<List<CheckupAttachment>> = dao.observeByCheckup(checkupId)

    suspend fun byId(id: String): CheckupAttachment? = dao.byId(id)

    /** 附件目录（懒创建）。 */
    private fun dir(): File = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 附件在磁盘上的绝对路径。 */
    fun fileOf(attachment: CheckupAttachment): File = File(dir(), attachment.fileName)

    /** 供分享 / 外部查看的 content:// URI（FileProvider，路径见 res/xml/file_paths.xml）。 */
    fun uriOf(attachment: CheckupAttachment): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileOf(attachment))

    /**
     * 相机拍照：先建空目标文件并返回其 content URI，交给 `TakePicture` 契约写入。
     * 拍照成功后调用 [registerCameraFile] 入库；用户取消则调用 [discardCameraFile] 清理空文件。
     */
    fun newCameraTarget(): Pair<File, Uri> {
        val f = File(dir(), "catt-${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }

    /** 相机拍照成功后入库（文件已由系统相机写入）。 */
    suspend fun registerCameraFile(file: File, checkupId: String?, note: String? = null): CheckupAttachment? =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) return@withContext null
            if (file.length() > MAX_BYTES) {
                file.delete()
                return@withContext null
            }
            val row = CheckupAttachment(
                id = Ids.new("catt"),
                checkupId = checkupId,
                kind = "PHOTO",
                fileName = file.name,
                mime = "image/jpeg",
                sizeBytes = file.length(),
                displayName = null,
                note = note?.trim()?.ifBlank { null },
                createdAt = nowIso(),
            )
            dao.upsert(row)
            row
        }

    /** 用户取消拍照——清掉占位的空文件。 */
    fun discardCameraFile(file: File) {
        runCatching { if (file.exists() && file.length() == 0L) file.delete() }
    }

    /**
     * 从系统选择器（相册图片 / PDF 文档）导入：把内容复制进内部存储后入库。
     *
     * @return null = 读取失败或超限（调用方据此提示用户）
     */
    suspend fun importFromUri(
        uri: Uri,
        kind: String,
        checkupId: String?,
        displayName: String? = null,
        note: String? = null,
    ): CheckupAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = when {
                kind == "PDF" -> "pdf"
                displayName?.contains('.') == true -> displayName.substringAfterLast('.').take(5)
                else -> "jpg"
            }
            val target = File(dir(), "catt-${System.currentTimeMillis()}.$ext")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: return@runCatching null

            if (copied == 0L || copied > MAX_BYTES) {
                target.delete()
                return@runCatching null
            }

            val row = CheckupAttachment(
                id = Ids.new("catt"),
                checkupId = checkupId,
                kind = kind,
                fileName = target.name,
                mime = context.contentResolver.getType(uri),
                sizeBytes = copied,
                displayName = displayName?.trim()?.ifBlank { null },
                note = note?.trim()?.ifBlank { null },
                createdAt = nowIso(),
            )
            dao.upsert(row)
            row
        }.getOrNull()
    }

    /** 删除附件：先删本地文件，再决定远端怎么处理（见下）。 */
    suspend fun delete(attachment: CheckupAttachment): Boolean = withContext(Dispatchers.IO) {
        runCatching { fileOf(attachment).delete() }
        // 有远端副本且开关开启 → 留墓碑等同步流程清远端；否则直接物理删行
        if (attachment.remotePath != null && isSyncEnabled()) {
            dao.markDeleted(attachment.id, nowIso())
        } else {
            dao.delete(attachment.id)
        }
        true
    }

    /** 远端已清理（或无需清理）→ 物理删行，由同步流程调用。 */
    suspend fun hardDelete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** v11：改归属复诊记录（null = 解除归属）。 */
    suspend fun linkToCheckup(attachmentId: String, checkupId: String?) = withContext(Dispatchers.IO) {
        dao.linkToCheckup(attachmentId, checkupId)
    }

    // ---- v12（v1.0.35）WebDAV 同步 ----

    /** 附件同步开关。关闭时：新附件不上传、删除不动远端。存普通 prefs（非密钥）。 */
    fun isSyncEnabled(): Boolean = syncPrefs.getBoolean(PREF_SYNC_ENABLED, false)

    fun setSyncEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean(PREF_SYNC_ENABLED, enabled).apply()
    }

    fun observeSyncCounts(): Flow<AttachmentSyncCounts> = dao.observeSyncCounts()

    suspend fun pendingUpload(): List<CheckupAttachment> = withContext(Dispatchers.IO) { dao.pendingUpload() }

    suspend fun pendingRemoteDelete(): List<CheckupAttachment> =
        withContext(Dispatchers.IO) { dao.pendingRemoteDelete() }

    suspend fun markUploaded(id: String, remotePath: String, sha256: String) =
        withContext(Dispatchers.IO) { dao.markUploaded(id, remotePath, sha256, nowIso()) }

    /** 本地文件是否在（懒下载据此判断要不要去远端拉）。 */
    fun hasLocalFile(attachment: CheckupAttachment): Boolean = fileOf(attachment).exists()

    /** 读本地明文（上传前加密用）。 */
    suspend fun readLocal(attachment: CheckupAttachment): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { fileOf(attachment).readBytes() }.getOrNull()
    }

    /** 懒下载后把解密内容写回本地缓存目录。 */
    suspend fun writeLocal(attachment: CheckupAttachment, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { fileOf(attachment).writeBytes(bytes) }.isSuccess
        }

    /** 磁盘占用总量（字节）——设置页 / 列表提示用。 */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dao.listAll().sumOf { it.sizeBytes }
    }
}
