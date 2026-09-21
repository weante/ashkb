package com.ashkb.app.ui.checkup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupAttachment
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 复诊附件归档（v10 / B10）：拍照、相册图片、本地 PDF 三个入口 + 已归档列表。
 *
 * @param checkupId 归属的复诊记录；传 null 表示从"附件总览"进入，不绑定具体记录
 * @param title     表头文案（记录页传记录日期，总览传通用标题）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(
    vm: CheckupViewModel,
    checkupId: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 相机契约是"先给 URI、再由系统写入"：目标文件必须跨两次交互记住，回调时才知道该登记谁
    var pendingFile by remember { mutableStateOf<File?>(null) }
    // 结果就地展示而非走 GlobalMessages：ModalBottomSheet 是独立覆盖层，
    // 挂在 App Scaffold 上的 Snackbar 会被遮罩压住，用户根本看不见。
    var msg by remember { mutableStateOf<Int?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        pendingFile = null
        if (file != null) {
            if (ok) {
                scope.launch {
                    val saved = vm.registerCameraPhoto(file, checkupId)
                    msg = if (saved != null) R.string.attach_added else R.string.attach_failed
                }
            } else {
                // 用户取消：相机已建空占位文件，不清就是内部存储里永不可见的垃圾
                vm.discardCameraFile(file)
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = vm.importAttachment(uri, "PHOTO", checkupId, null)
                msg = if (saved != null) R.string.attach_added else R.string.attach_failed
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // 原始文件名要在回调里同步取：SAF 的读权限只在本次回调内有效，别丢进协程
            val name = displayNameOf(context, uri)
            scope.launch {
                val saved = vm.importAttachment(uri, "PDF", checkupId, name)
                msg = if (saved != null) R.string.attach_added else R.string.attach_failed
            }
        }
    }

    // 绑定到具体记录时只订阅该记录的附件；总览（checkupId == null）才拉全量。
    // 两条 Flow 归并成一条，是为了让 collectAsStateWithLifecycle 无条件调用——
    // 分支里各订阅一次会让 remember 槽位随参数漂移，切记录时容易错位。
    val flow: Flow<List<CheckupAttachment>> = remember(checkupId) {
        if (checkupId == null) vm.attachments else vm.attachmentsFor(checkupId)
    }
    val rows by flow.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(title, style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // 三个同级录入入口，不分主次；等宽平分，避免中文标签在窄屏互相挤
                OutlinedButton(
                    onClick = {
                        val (file, uri) = vm.newCameraTarget()
                        pendingFile = file
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.attach_take_photo)) }
                OutlinedButton(
                    onClick = {
                        imageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.attach_pick_image)) }
                OutlinedButton(
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.attach_pick_pdf)) }
            }

            msg?.let { res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    // 失败与成功用不同前景色：一次性提示会在下一次操作时被覆盖，颜色是唯一残留的语义
                    color = if (res == R.string.attach_failed || res == R.string.attach_open_failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.attach_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.forEach { a ->
                AttachmentRow(
                    attachment = a,
                    onView = {
                        val opened = runCatching {
                            val uri = vm.attachmentUri(a)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    // 没有 MIME 时用 */* 兜底，否则部分查看器会拒绝打开
                                    setDataAndType(uri, a.mime ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }.isSuccess
                        // 没有可处理该类型的 App（或 FileProvider 未配好）时明确告知，而不是静默无反应
                        if (!opened) msg = R.string.attach_open_failed
                    },
                    onDelete = {
                        vm.deleteAttachment(a)
                        msg = R.string.attach_deleted
                    },
                )
            }

            Text(
                stringResource(R.string.attach_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条附件：查看 / 删除。 */
@Composable
private fun AttachmentRow(
    attachment: CheckupAttachment,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(
        // PHOTO 无原始文件名（相机自建名无意义），统一显示"照片"；PDF 保留用户认得的原始名
        title = if (attachment.kind == "PHOTO") {
            stringResource(R.string.attach_photo_name)
        } else {
            attachment.displayName ?: attachment.fileName
        },
        subtitle = stringResource(R.string.attach_total_size, formatBytes(attachment.sizeBytes)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = onView, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Text(stringResource(R.string.attach_view))
            }
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                confirmTitle = stringResource(R.string.common_delete),
                confirmBody = stringResource(R.string.attach_delete_confirm),
                onConfirm = onDelete,
                modifier = Modifier.heightIn(min = Size.touchMin),
            )
        }
    }
}

/** SAF 文档的原始文件名；查询失败退回 URI 末段，避免列表只剩"照片"二字。 */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
    }
}.getOrNull()

/** 附件多在 100KB–5MB 区间：超过 1MB 显示一位小数，否则取整 KB 更易读。 */
private fun formatBytes(bytes: Long): String =
    if (bytes >= 1_048_576L) "%.1f MB".format(bytes / 1_048_576.0)
    else "%.0f KB".format(bytes / 1024.0)
