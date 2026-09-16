package com.ashkb.app.ui.me

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ashkb.app.domain.Labels
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/** 我的（L1）：档案 / 药单入口 / 提醒自检 / 备份入口。 */
@Composable
fun MeScreen(
    vm: MeViewModel,
    onOpenMeds: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onEditProfile: () -> Unit = {},
) {
    val profile by vm.profile.collectAsState()
    val meds by vm.meds.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.xxl)) }

        // ---- 档案卡 ----
        item {
            SectionCard(
                title = "健康档案",
                subtitle = if (profile == null) "尚未建档" else null,
                action = {
                    if (profile != null) {
                        TextButton(onClick = onEditProfile) { Text("编辑") }
                    }
                },
            ) {
                val p = profile
                if (p == null) {
                    Text(
                        "过敏史、血型与用药史是禁忌检查与紧急卡的数据基础",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = onEditProfile) { Text("开始建档") }
                } else {
                    KeyValueRow("称呼", p.displayName)
                    KeyValueRow("诊断", p.diagnosis)
                    KeyValueRow("确诊年份", p.diagnoseYear?.toString() ?: "未填")
                    KeyValueRow("HLA-B27", Labels.hlaB27(p.hlaB27))
                    KeyValueRow("病情分期", stageLabel(p.diseaseStage))
                    KeyValueRow("脊柱活动度", spineLabel(p.spineMobility))
                    KeyValueRow("过敏史", p.allergies ?: "未填")
                    KeyValueRow("血型", p.emergencyBloodType ?: "未填")
                }
            }
        }

        // ---- 药单管理入口（实时摘要 + 数量 badge）----
        item {
            NavRow(
                icon = Icons.Rounded.Medication,
                title = "药单管理",
                subtitle = if (meds.isEmpty()) {
                    "尚未添加药品"
                } else {
                    "在用 ${meds.size} 种 · " + meds.joinToString("、") { it.name }
                },
                badge = {
                    if (meds.isNotEmpty()) {
                        StatusChip("${meds.size}", StatusTone.Info)
                    }
                },
                onClick = onOpenMeds,
            )
        }

        // ---- M10 提醒与权限自检 ----
        item { ReminderSelfCheckCard() }

        // ---- P4 R20 备份与数据自主 ----
        item {
            NavRow(
                icon = Icons.Rounded.Backup,
                title = "备份与数据",
                subtitle = "全量加密备份 · 恢复自证 · WebDAV 远程备份 · 档案 JSON 导出导入",
                onClick = onOpenBackup,
            )
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

private fun stageLabel(k: String?) = when (k) {
    "active" -> "活动期（运动处方已保守过滤）"
    "stable" -> "缓解期"
    else -> "未设置（按活动期保守处理）"
}

private fun spineLabel(k: String?) = when (k) {
    "none" -> "无受限"; "mild" -> "轻度"
    "moderate" -> "中度（颈椎受累）"; "severe" -> "重度（颈椎受累）"
    else -> "未填"
}

@Composable
private fun ReminderSelfCheckCard() {
    val context = LocalContext.current
    val notifOk = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactOk = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true

    SectionCard(title = "提醒可靠性自检") {
        CheckRow("通知权限", if (notifOk) "已授权" else "未授权——提醒将无法显示", notifOk)
        CheckRow("精确闹钟", if (exactOk) "已允许——按分钟准时提醒" else "未允许——提醒可能有 15 分钟内偏差", exactOk)
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }) { Text("申请精确闹钟") }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }) { Text("通知设置") }
        }
        Text(
            "提醒为本机系统通知，不依赖任何服务器。部分厂商系统可能限制后台——若提醒未响，请将本应用加入电池白名单。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** 状态三重编码：图标 + 文字 + 颜色（此前是裸 `"✓"` / `"!"`）。 */
@Composable
private fun CheckRow(label: String, desc: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = if (ok) "已通过" else "未通过",
            modifier = Modifier.size(Size.iconMd),
            tint = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
