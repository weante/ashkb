package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.domain.CheckupDeletion
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.80（批次 6）：删除复诊记录的确认框（**级联删除**）。
 *
 * 两个设计要点：
 *
 * ① **条数是异步取的**（[LaunchedEffect]）：级联范围要跑三条查询，而记录列表里可能有几十张卡片，
 *    为每张卡预先挂一次计数查询纯属白烧 IO——用户点删除的只是其中一条。取数期间确认按钮保持禁用，
 *    绝不允许「还没算清就让人点确认」。
 *
 * ② **逐类报数、0 条不显示**：这是不可逆操作，而用户在卡片上看不到这次就诊名下挂了多少东西。
 *    只说「将一并删除」而不说条数，等于让他在不知情下删掉一份化验单（与药品删除同款口径，
 *    见 `domain/MedDeletion`）。
 */
@Composable
internal fun CheckupRecordDeleteDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    var counts by remember(record.id) { mutableStateOf<CheckupDeletion.Counts?>(null) }
    // key 到 record.id：列表里换一条记录时重新取数，不会沿用上一条的条数
    LaunchedEffect(record.id) { counts = vm.checkupDeletionCounts(record.id) }
    val loaded = counts

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkup_delete_record_confirm, record.itemName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when {
                    loaded == null -> Text(
                        stringResource(R.string.checkup_delete_counting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CheckupDeletion.variant(loaded) == CheckupDeletion.Variant.CASCADE -> {
                        Text(stringResource(R.string.checkup_delete_cascade_intro), style = MaterialTheme.typography.bodyMedium)
                        CascadeLine(
                            text = stringResource(R.string.checkup_delete_cascade_labs, CheckupDeletion.normalize(loaded.labs)),
                            count = loaded.labs,
                        )
                        CascadeLine(
                            text = stringResource(R.string.checkup_delete_cascade_imaging, CheckupDeletion.normalize(loaded.imaging)),
                            count = loaded.imaging,
                        )
                        CascadeLine(
                            text = stringResource(
                                R.string.checkup_delete_cascade_attachments,
                                CheckupDeletion.normalize(loaded.attachments),
                            ),
                            count = loaded.attachments,
                        )
                    }
                    else -> Text(stringResource(R.string.checkup_delete_none), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.common_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                // 条数没取到就不许确认：宁可让用户再点一次，也不能删了他还没看清的东西
                enabled = loaded != null,
                onClick = {
                    vm.deleteCheckupRecord(record.id)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** 级联明细的一行；条数为 0 的类别整行不显示（「将删除 0 条化验」是噪声）。 */
@Composable
private fun CascadeLine(text: String, count: Int) {
    if (CheckupDeletion.normalize(count) <= 0) return
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
