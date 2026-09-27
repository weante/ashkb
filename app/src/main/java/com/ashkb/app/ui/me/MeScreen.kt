package com.ashkb.app.ui.me

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.domain.Labels
import com.ashkb.app.domain.Lifestyle
import com.ashkb.app.domain.LifestylePrescription
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
    onOpenReminderCheck: () -> Unit = {},
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val meds by vm.meds.collectAsStateWithLifecycle()
    var showReminderSettings by rememberSaveable { mutableStateOf(false) }

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
                    KeyValueRow(stringResource(R.string.profile_sacroiliitis_field), Labels.sacroiliitisGrade(p.sacroiliitisGrade))
                    KeyValueRow(stringResource(R.string.profile_lifestyle_title), lifestyleLabel(p.lifestyle))
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

        // ---- M10 提醒与权限自检（v1.0.72：整块移入二级页，这里只留入口 + 状态摘要）----
        item {
            val checkState = rememberReminderCheckState()
            NavRow(
                icon = Icons.Rounded.CheckCircle,
                title = stringResource(R.string.reminder_selfcheck_title),
                subtitle = stringResource(R.string.reminder_selfcheck_summary, checkState.passed, checkState.total),
                badge = {
                    if (checkState.passed < checkState.total) {
                        StatusChip("${checkState.total - checkState.passed}", StatusTone.Warning)
                    }
                },
                onClick = onOpenReminderCheck,
            )
        }

        // ---- v1.0.59 B5：多源提醒设置入口 ----
        item {
            NavRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.reminder_settings_title),
                subtitle = stringResource(R.string.reminder_settings_subtitle),
                onClick = { showReminderSettings = true },
            )
        }

        // ---- P4 R20 备份与数据自主 ----
        item {
            NavRow(
                icon = Icons.Rounded.Backup,
                title = stringResource(R.string.me_backup_section),
                subtitle = stringResource(R.string.backup_section_subtitle),
                onClick = onOpenBackup,
            )
        }

        // ---- 关于：版本号 + MIT 开源协议 + 全局免责声明 ----
        item { AboutCard() }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }

    // v1.0.59 B5：多源提醒设置面板（开关 + BASDAI 周期）
    if (showReminderSettings) {
        ReminderSettingsSheet { showReminderSettings = false }
    }
}

/** 关于卡：用 PackageManager 取版本（不动 buildFeatures.buildConfig，AGP 8 默认关闭）。 */
@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "v${pi.versionName} ($code)"
        }.getOrDefault("")
    }
    SectionCard(title = stringResource(R.string.me_about_title)) {
        KeyValueRow(stringResource(R.string.me_version_field), version)
        Text(
            stringResource(R.string.me_license_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun stageLabel(k: String?) = when (k) {
    "stable" -> stringResource(R.string.stage_stable)
    "controlled" -> stringResource(R.string.stage_controlled)
    "flare" -> stringResource(R.string.stage_flare_filtered)
    else -> stringResource(R.string.stage_not_set_conservative)
}

/** v1.0.64 B13：生活方式一行摘要——只列已登记项，全空则显示「未填」。 */
@Composable
private fun lifestyleLabel(raw: String?): String {
    val l = Lifestyle.fromJson(raw)
    if (!LifestylePrescription.hasContent(l)) return stringResource(R.string.common_unfilled)
    return buildList {
        val smokingText = when (l.smoking) {
            Lifestyle.SMOKING_NEVER -> stringResource(R.string.profile_smoking_never)
            Lifestyle.SMOKING_FORMER -> stringResource(R.string.profile_smoking_former)
            Lifestyle.SMOKING_CURRENT -> stringResource(R.string.profile_smoking_current)
            else -> null
        }
        if (smokingText != null) add("${stringResource(R.string.profile_smoking_field)} $smokingText")
        l.sedentaryHours?.let { add(stringResource(R.string.profile_sedentary_short, it)) }
        val habitText = when (l.exerciseHabit) {
            Lifestyle.HABIT_NONE -> stringResource(R.string.profile_habit_none)
            Lifestyle.HABIT_OCCASIONAL -> stringResource(R.string.profile_habit_occasional)
            Lifestyle.HABIT_REGULAR -> stringResource(R.string.profile_habit_regular)
            else -> null
        }
        if (habitText != null) add("${stringResource(R.string.profile_habit_field)} $habitText")
        l.sleepHours?.let { add(stringResource(R.string.profile_sleep_short, it)) }
    }.joinToString(" · ")
}

@Composable
private fun spineLabel(k: String?) = when (k) {
    "none" -> stringResource(R.string.profile_mobility_none); "mild" -> stringResource(R.string.severity_mild)
    "moderate" -> stringResource(R.string.profile_mobility_moderate_cervical); "severe" -> stringResource(R.string.profile_mobility_severe_cervical)
    else -> stringResource(R.string.common_unfilled)
}

