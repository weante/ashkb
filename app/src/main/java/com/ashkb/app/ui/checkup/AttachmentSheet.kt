package com.ashkb.app.ui.checkup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupAttachment
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 复诊附件归档（v10 / B10）：拍照、相册图片、本地 PDF 三个入口 + 已归档列表。
 *
 * @param checkupId 归属的复诊记录；传 null 表示从"附件总览"进入，不绑定具体记录
 * @param title     表头文案（记录页传记录日期，总览传通用标题）
 * @param allowLink true = 显示「归属复诊记录」区块（从化验/影像进入、或全部附件总览时）
 * @param onLinkSource 归属变更回调：由调用方把「来源行」（某天化验 / 某条影像）一并关联。null = 无需
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(
    vm: CheckupViewModel,
    checkupId: String?,
    title: String,
    onDismiss: () -> Unit,
    allowLink: Boolean = false,
    onLinkSource: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 相机契约是"先给 URI、再由系统写入"：目标文件必须跨两次交互记住，回调时才知道该登记谁
    var pendingFile by remember { mutableStateOf<File?>(null) }
    // 结果就地展示而非走 GlobalMessages：ModalBottomSheet 是独立覆盖层，
    // 挂在 App Scaffold 上的 Snackbar 会被遮罩压住，用户根本看不见。
    var msg by remember { mutableStateOf<Int?>(null) }
    // v11 归属：link 是本地可变状态而非直接用 checkupId——用户在 sheet 内改了归属，
    // 之后新增的附件与下方列表都必须立刻跟随新归属，不能停留在进 sheet 时的旧值
    var link by remember(checkupId) { mutableStateOf(checkupId) }
    var picking by remember { mutableStateOf(false) }
    // 全部附件总览里逐条重归档的目标：早期未归属的附件只有这里能补录
    var linkTarget by remember { mutableStateOf<CheckupAttachment?>(null) }
    // 归属提示是带记录名的格式化字符串，而 stringResource 只能在 @Composable 里调用，
    // 无法直接存进状态，故这里存已格式化好的文本
    var linkMsg by remember { mutableStateOf<String?>(null) }
    val records by vm.checkupRecords.collectAsStateWithLifecycle()

    /** 记录名用于归属状态文案；records 只取最近 50 条，查不到时退回裸 id 也好过空白。 */
    fun labelOf(id: String?): String =
        records.firstOrNull { it.id == id }?.let { "${it.date} · ${it.itemName}" } ?: (id ?: "")

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        pendingFile = null
        if (file != null) {
            if (ok) {
                scope.launch {
                    val saved = vm.registerCameraPhoto(file, link)
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
                val saved = vm.importAttachment(uri, "PHOTO", link, null)
                msg = if (saved != null) R.string.attach_added else R.string.attach_failed
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // 原始文件名要在回调里同步取：SAF 的读权限只在本次回调内有效，别丢进协程
            val name = displayNameOf(context, uri)
            scope.launch {
                val saved = vm.importAttachment(uri, "PDF", link, name)
                msg = if (saved != null) R.string.attach_added else R.string.attach_failed
            }
        }
    }

    // 归属到具体记录时只订阅该记录的附件；总览（link == null）才拉全量。
    // 两条 Flow 归并成一条，是为了让 collectAsStateWithLifecycle 无条件调用——
    // 分支里各订阅一次会让 remember 槽位随参数漂移，切记录时容易错位。
    // 键必须是 link 而不是 checkupId：sheet 内改了归属就要重新划定列表范围
    val flow: Flow<List<CheckupAttachment>> = remember(link) {
        // 先落成普通局部 val：委托属性（by remember）无法智能转换，直接判断 link != null 通不过编译
        val id = link
        if (id == null) vm.attachments else vm.attachmentsFor(id)
    }
    val rows by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    // 同步开关存在 prefs 里、不是可观察流：sheet 打开期间用户不可能在备份页改它，
    // 故读一次快照即可，不必为它引入额外的 Flow 订阅
    val syncOn = vm.attachmentSyncOn()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (allowLink) {
                // 归属放在标题正下方：用户从"某天化验/某条影像"进来，第一件想确认的就是"这是谁的"
                Text(stringResource(R.string.attach_link_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    // 不按日期自动猜归属：日期相同不代表同一次复诊，猜错会让记录串味，宁可让用户手点
                    if (link == null) {
                        stringResource(R.string.attach_link_unlinked)
                    } else {
                        stringResource(R.string.attach_link_linked, labelOf(link))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { picking = true },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.attach_link_pick)) }
                linkMsg?.let { text ->
                    // 归属是本地库写入，没有可枚举的失败分支，故只用主色；语义与上面的 msg 一致
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.attach_link_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                    color = if (
                        res == R.string.attach_failed ||
                        res == R.string.attach_open_failed ||
                        res == R.string.attach_download_failed
                    ) {
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
                val onLinkRow: (() -> Unit)? = if (allowLink) {
                    { linkTarget = a }
                } else {
                    null
                }
                AttachmentRow(
                    attachment = a,
                    syncOn = syncOn,
                    onView = {
                        // 本地文件可能已被清理：先判断在不在，不在而云端有就懒下载回来再开
                        if (vm.attachmentHasLocal(a)) {
                            openAttachment(context, vm, a) { msg = R.string.attach_open_failed }
                        } else if (a.remotePath != null) {
                            scope.launch {
                                msg = R.string.attach_downloading
                                val ok = vm.ensureAttachmentLocal(a)
                                if (ok) {
                                    msg = R.string.attach_download_ok
                                    openAttachment(context, vm, a) { msg = R.string.attach_open_failed }
                                } else {
                                    msg = R.string.attach_download_failed
                                }
                            }
                        } else {
                            msg = R.string.attach_open_failed
                        }
                    },
                    onDelete = {
                        vm.deleteAttachment(a)
                        msg = R.string.attach_deleted
                    },
                    onLink = onLinkRow,
                )
            }

            Text(
                stringResource(R.string.attach_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 选择器作为 sheet 的兄弟节点挂在窗口层：塞进 SheetColumn 会被 sheet 的滚动容器裁剪
    if (picking) {
        CheckupRecordPickerDialog(
            records = records,
            currentId = link,
            onPick = { picked ->
                link = picked
                onLinkSource?.invoke(picked)
                linkMsg = if (picked == null) {
                    context.getString(R.string.attach_link_cleared)
                } else {
                    context.getString(R.string.attach_link_done, labelOf(picked))
                }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    linkTarget?.let { target ->
        CheckupRecordPickerDialog(
            records = records,
            currentId = target.checkupId,
            onPick = { picked ->
                vm.linkAttachment(target.id, picked)
                linkMsg = if (picked == null) {
                    context.getString(R.string.attach_link_cleared)
                } else {
                    context.getString(R.string.attach_link_done, labelOf(picked))
                }
                linkTarget = null
            },
            onDismiss = { linkTarget = null },
        )
    }
}

/**
 * 复诊记录选择器：手动选归属（不按日期自动猜）。currentId 用于高亮当前归属。
 *
 * 选中即生效、随即关闭：归属是可反复修改的弱关联，再加一层"确定"只会让用户多点一次。
 */
@Composable
internal fun CheckupRecordPickerDialog(
    records: List<CheckupRecord>,
    currentId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_link_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 兜住高度：复诊记录最多 50 条，不限高会把按钮挤出屏幕外
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 「不关联」必须排第一：解除归属是常用操作，藏在列表末尾会被当成"没有这个选项"
                PickerRow(
                    label = stringResource(R.string.attach_link_none),
                    selected = currentId == null,
                    onClick = { onPick(null) },
                )
                if (records.isEmpty()) {
                    Text(
                        stringResource(R.string.attach_link_empty_records),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                records.forEach { rec ->
                    PickerRow(
                        label = "${rec.date} · ${rec.itemName}",
                        selected = currentId == rec.id,
                        onClick = { onPick(rec.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** 选择器内的一行：整行可点，触摸区域覆盖 RadioButton 与文字，避免 28dp 圆圈成为唯一靶点。 */
@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.touchMin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 单条附件：同步态 / 归属 / 查看 / 删除。 */
@Composable
private fun AttachmentRow(
    attachment: CheckupAttachment,
    syncOn: Boolean,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onLink: (() -> Unit)? = null,
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
        // 未启用同步时"未同步"是纯噪音，故整块随开关隐藏；启用后独立一行，
        // 不挤进下面那排按钮：按钮已有 3 个，再并一个胶囊窄屏必然折行
        if (syncOn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (attachment.remotePath != null) {
                    StatusChip(stringResource(R.string.attach_state_synced), StatusTone.Success)
                } else {
                    StatusChip(stringResource(R.string.attach_state_pending), StatusTone.Neutral)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 归属排在"查看"之前：早期从总览导入、尚未归属的附件，这里是唯一的补录入口
            onLink?.let { openPicker ->
                TextButton(onClick = openPicker, modifier = Modifier.heightIn(min = Size.touchMin)) {
                    Text(stringResource(R.string.attach_link_title))
                }
            }
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

/**
 * 用系统查看器打开本地附件。
 *
 * 抽成函数是因为「查看」现在有直开、懒下载后再开两条路径，而失败兜底必须共用一套：
 * 各自 runCatching 一遍，很容易只改了其中一个分支。
 */
private fun openAttachment(
    context: Context,
    vm: CheckupViewModel,
    a: CheckupAttachment,
    onFail: () -> Unit,
) {
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
    if (!opened) onFail()
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
