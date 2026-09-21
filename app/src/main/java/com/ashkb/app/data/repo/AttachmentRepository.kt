package com.ashkb.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ashkb.app.data.db.AppDatabase
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

    companion object {
        /** 单文件上限 20 MB——防止用户误选超大视频/文件把内部存储塞满。 */
        const val MAX_BYTES = 20L * 1024 * 1024

        private const val DIR = "checkup_attachments"
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

    /** 删除附件：先删文件再删行（文件删失败不阻塞行删除，避免留下看不见的脏行）。 */
    suspend fun delete(attachment: CheckupAttachment): Boolean = withContext(Dispatchers.IO) {
        runCatching { fileOf(attachment).delete() }
        dao.delete(attachment.id)
        true
    }

    /** v11：改归属复诊记录（null = 解除归属）。 */
    suspend fun linkToCheckup(attachmentId: String, checkupId: String?) = withContext(Dispatchers.IO) {
        dao.linkToCheckup(attachmentId, checkupId)
    }

    /** 磁盘占用总量（字节）——设置页 / 列表提示用。 */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dao.listAll().sumOf { it.sizeBytes }
    }
}
