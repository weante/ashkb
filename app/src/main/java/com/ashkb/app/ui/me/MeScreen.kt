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
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
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
                title = stringResource(R.string.profile_health_record),
                subtitle = if (profile == null) stringResource(R.string.profile_not_built_short) else null,
                action = {
                    if (profile != null) {
                        TextButton(onClick = onEditProfile) { Text(stringResource(R.string.common_edit)) }
                    }
                },
            ) {
                val p = profile
                if (p == null) {
                    Text(
                        stringResource(R.string.profile_data_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = onEditProfile) { Text(stringResource(R.string.profile_start_building)) }
                } else {
                    KeyValueRow(stringResource(R.string.profile_display_name), p.displayName)
                    KeyValueRow(stringResource(R.string.profile_diagnosis), p.diagnosis)
                    KeyValueRow(stringResource(R.string.profile_diagnosis_year), p.diagnoseYear?.toString() ?: stringResource(R.string.common_unfilled))
                    KeyValueRow("HLA-B27", Labels.hlaB27(p.hlaB27))
                    KeyValueRow(stringResource(R.string.profile_disease_stage), stageLabel(p.diseaseStage))
                    KeyValueRow(stringResource(R.string.profile_spine_mobility), spineLabel(p.spineMobility))
                    KeyValueRow(stringResource(R.string.profile_allergy_history), p.allergies ?: stringResource(R.string.common_unfilled))
                    KeyValueRow(stringResource(R.string.profile_blood_type), p.emergencyBloodType ?: stringResource(R.string.common_unfilled))
                }
            }
        }

        // ---- 药单管理入口（实时摘要 + 数量 badge）----
        item {
            NavRow(
                icon = Icons.Rounded.Medication,
                title = stringResource(R.string.med_manage_title),
                subtitle = if (meds.isEmpty()) {
                    stringResource(R.string.med_not_added)
                } else {
                    stringResource(R.string.me_in_use_prefix, meds.size) + meds.joinToString("、") { it.name }
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
                title = stringResource(R.string.me_backup_section),
                subtitle = stringResource(R.string.backup_section_subtitle),
                onClick = onOpenBackup,
            )
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun stageLabel(k: String?) = when (k) {
    "stable" -> stringResource(R.string.stage_stable)
    "controlled" -> stringResource(R.string.stage_controlled)
    "flare" -> stringResource(R.string.stage_flare_filtered)
    else -> stringResource(R.string.stage_not_set_conservative)
}

@Composable
private fun spineLabel(k: String?) = when (k) {
    "none" -> stringResource(R.string.profile_mobility_none); "mild" -> stringResource(R.string.severity_mild)
    "moderate" -> stringResource(R.string.profile_mobility_moderate_cervical); "severe" -> stringResource(R.string.profile_mobility_severe_cervical)
    else -> stringResource(R.string.common_unfilled)
}

@Composable
private fun ReminderSelfCheckCard() {
    val context = LocalContext.current
    val notifOk = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactOk = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true

    SectionCard(title = stringResource(R.string.reminder_selfcheck_title)) {
        CheckRow(stringResource(R.string.reminder_notification_permission), if (notifOk) stringResource(R.string.permission_granted) else stringResource(R.string.reminder_no_permission), notifOk)
        CheckRow(stringResource(R.string.reminder_exact_alarm), if (exactOk) stringResource(R.string.reminder_exact_ok) else stringResource(R.string.reminder_no_exact), exactOk)
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }) { Text(stringResource(R.string.reminder_request_exact_alarm)) }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }) { Text(stringResource(R.string.reminder_notification_settings)) }
        }
        Text(
            stringResource(R.string.reminder_note),
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
            contentDescription = if (ok) stringResource(R.string.common_passed) else stringResource(R.string.common_not_passed),
            modifier = Modifier.size(Size.iconMd),
            tint = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
