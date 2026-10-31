package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：WebDavSection / AttachSyncSection / AttachSyncToggle / AttachSyncStats / AttachSyncActions / AttachSyncStatus。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.R
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * WebDAV 配置入口区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 地址 / 用户名收在本 section：它们只用于这一行的副标题与"改配置还是首次配置"的按钮文案，
 * 根部不再需要为了这行字而持有 davUrl / davUser。
 */
@Composable
internal fun WebDavSection(vm: BackupViewModel, busy: Boolean, onOpenSheet: () -> Unit) {
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()
    val davUser by vm.davUser.collectAsStateWithLifecycle()

    SectionCard(
        title = stringResource(R.string.backup_dav_section_title),
        subtitle = if (davUrl.isNotBlank()) stringResource(R.string.backup_dav_configured, davUrl, davUser) else null,
    ) {
        OutlinedButton(
            onClick = onOpenSheet,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
        ) { Text(if (davUrl.isNotBlank()) stringResource(R.string.backup_modify_dav) else stringResource(R.string.backup_configure_dav)) }
    }
}

/**
 * 附件同步区（v1.0.35 引入；v1.0.85 批次 10 收口为独立 section，本批的主目标）。
 *
 * **为什么必须自己收**：`attachSyncStage` / `attachSyncMsg` / `attachBytes` 在附件同步期间
 * 每秒发射多次（进度 tick）。原先这三条在 [BackupScreen] 根级收集，读点也在根级，
 * 于是每个 tick 都让整屏重组——正在输备份口令的用户会被进度刷新打断。现在读点搬进本
 * section，tick 的失效范围收窄到本 section 自己。
 *
 * 本 section 在内部收集（共 7 条，全部只被本区渲染）：
 * `attachSyncEnabled` / `attachCounts` / `attachSyncStage` / `attachSyncMsg` / `attachBytes` /
 * `attachVerifyMsg` / `davUrl`（davUrl 只用来判断"按钮能不能点"与给缺配置提示）。
 * 入参只给 vm：这 7 条 Flow 与 3 个动作若逐条透传会超出 detekt 的 LongParameterList 阈值（8），
 * 而"状态一律在本 section 内读"这点由上面的清单与本函数的 collect 语句共同保证——
 * **往下加东西时请只读这里列出的状态**。
 */
@Composable
internal fun AttachSyncSection(vm: BackupViewModel) {
    val attachSyncEnabled by vm.attachSyncEnabled.collectAsStateWithLifecycle()
    val attachCounts by vm.attachCounts.collectAsStateWithLifecycle()
    val attachSyncStage by vm.attachSyncStage.collectAsStateWithLifecycle()
    val attachSyncMsg by vm.attachSyncMsg.collectAsStateWithLifecycle()
    val attachBytes by vm.attachBytes.collectAsStateWithLifecycle()
    val attachVerifyMsg by vm.attachVerifyMsg.collectAsStateWithLifecycle()
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()

    SectionCard(title = stringResource(R.string.attach_sync_section)) {
        AttachSyncToggle(
            enabled = attachSyncEnabled,
            onSetEnabled = { vm.setAttachSyncEnabled(it) },
        )
        Spacer(Modifier.height(Spacing.sm))
        AttachSyncStats(counts = attachCounts, bytes = attachBytes)
        Spacer(Modifier.height(Spacing.sm))
        AttachSyncActions(
            enabled = attachSyncEnabled,
            davUrl = davUrl,
            // 附件同步是一趟独立长任务（几百 MB），所以只看自身状态，不占用全局 busy
            running = attachSyncStage != null,
            onSync = { vm.syncAttachments() },
            onVerify = { vm.verifyAttachments() },
        )
        AttachSyncStatus(
            stage = attachSyncStage,
            davUrl = davUrl,
            enabled = attachSyncEnabled,
            msg = attachSyncMsg,
            verifyMsg = attachVerifyMsg,
        )
    }
}

/** 附件同步总闸：开关 + 一行说明。 */
@Composable
private fun AttachSyncToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.attach_sync_switch),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onSetEnabled,
        )
    }
    Text(
        stringResource(R.string.attach_sync_switch_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 附件同步摘要 + 本地占用。只吃两个值，所以进度 tick 不会重组它。 */
@Composable
private fun AttachSyncStats(counts: AttachmentSyncCounts, bytes: Long) {
    Text(
        stringResource(
            R.string.attach_sync_summary,
            counts.visible, counts.synced,
            counts.pending, counts.toDelete,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        stringResource(R.string.attach_sync_size, formatBytes(bytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 同步 / 校验两个按钮 + 校验说明。
 *
 * `running` 由父级从 `attachSyncStage != null` 派生后传值：进度字符串每 tick 都在变，
 * 但"是否在跑"只在开始/结束时翻转，本块因此不会跟着 tick 重组。
 */
@Composable
private fun AttachSyncActions(
    enabled: Boolean,
    davUrl: String,
    running: Boolean,
    onSync: () -> Unit,
    onVerify: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onSync,
            enabled = enabled && davUrl.isNotBlank() && !running,
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.attach_sync_button)) }
        OutlinedButton(
            onClick = onVerify,
            // 校验与同步共用进度流，两者互斥；同样不占用全局 busy
            enabled = enabled && davUrl.isNotBlank() && !running,
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.attach_verify_button)) }
    }
    Text(
        stringResource(R.string.attach_verify_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 附件同步的进度 / 缺配置提示 / 结果文案。
 *
 * 这是进度 tick 的实际落点（`stage` 每 tick 都变），所以它只收三个值、只渲染这几行文字——
 * 旁边那些不会变的东西（开关、按钮、摘要）都在别的块里，tick 时会被跳过。
 */
@Composable
private fun AttachSyncStatus(
    stage: String?,
    davUrl: String,
    enabled: Boolean,
    msg: Pair<Boolean, String>?,
    verifyMsg: Pair<Boolean, String>?,
) {
    // 阶段文案自带动作（正在补传附件 / 正在清理远端多余文件），不再叠前缀
    stage?.let { progress ->
        Text(
            progress,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 禁用态按钮自己不会解释原因，缺哪一步就直说是缺哪一步
    if (davUrl.isBlank()) {
        Text(
            stringResource(R.string.attach_sync_need_webdav),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (!enabled) {
        Text(
            stringResource(R.string.attach_sync_need_enable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    msg?.let { (ok, detail) ->
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(
                if (ok) R.string.attach_sync_done else R.string.attach_sync_failed,
                detail,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
    verifyMsg?.let { (ok, detail) ->
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(
                if (ok) R.string.attach_verify_done else R.string.attach_verify_failed,
                detail,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}
