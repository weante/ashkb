package com.ashkb.app.ui.wellness

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.WeightTarget
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatTile
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.components.TrendChart
import com.ashkb.app.ui.components.TrendPoint
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/**
 * v1.0.91（批次 16）：「体征」与「身体成分」两组卡片——它们各自收自己渲染的 Flow。
 *
 * ### 为什么是 `LazyListScope` 扩展
 * 卡片必须仍是**宿主** `LazyColumn` 的 item（虚拟化、key、滚动位置都在宿主手里），
 * 所以这里只负责**登记 item**，不接管列表布局。两条硬边界（批次 12 / 14 实测）：
 *  · 本函数**不能**标 `@Composable`——在 `LazyColumn` 的内容 lambda 里调用 `@Composable`
 *    扩展会被判为「调用点不是组合上下文」（批次 16 用 `ui/Probe.kt` 复现过同一处报错）；
 *  · 本函数体里**不能**直接写 `remember` / `collectAsStateWithLifecycle`（列表作用域没有组合上下文）。
 * 所以收 Flow 的动作全部落在 `item { }` 的**内容 composable** 里（[VitalsHero] / [WeightCard] /
 * [BodyCompositionCard]）——item 的内容 lambda 本身是组合上下文（`@Composable LazyItemScope.() -> Unit`）。
 *
 * ### 为什么值得拆
 * 拆分前 `vitalsToday` / `weightToday` / `weightRecent` / `bodyMeasureLatest` / `profile` 五条流收在
 * `WellnessScreen` 根部，**任何一次体重录入或体征保存都会让整屏（补剂列表、合并时间表、饮食画像、
 * 忌口清单）跟着重组**。现在每条流的读点都落在它自己那张卡（或那一块 item）里：
 *  · `vitalsToday` 只喂体征 hero——取数在 `WellnessScreen.kt` 的 `VitalsSection` 里
 *    （摆放层 `VitalsHero` 被 detekt 基线钉死在原文件与 `private`，见那里的注释）；
 *  · `weightToday` / `weightRecent` 只喂 [WeightCard]；
 *  · `bodyMeasureLatest` 只喂 [BodyCompositionCard]；
 *  · `profile` 只喂体重卡里的目标区间提示（[WeightTargetHint]），所以收在 [WeightCard] 内即可。
 * `profile` 同时被补剂 / 饮食等 section 用于各自的判断，那些 section 各收一份——
 * 它是**只读**的档案流（`repo.observeProfile()`，值变了所有读者看到的是同一个新值），
 * 不像日期那样存在「两处各读一次系统时钟」的时间漂移风险，故不需要在根部收一份广播给所有人。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun LazyListScope.vitalsSections(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    // ---- 分组一：体征 ----
    stickyHeader { WellnessGroupHeader(stringResource(R.string.vitals_short)) }
    item { VitalsSection(vm = vm, sheets = sheets) }

    // ---- 分组二：身体成分 ----
    stickyHeader { WellnessGroupHeader(stringResource(R.string.vitals_body_composition)) }
    item { WeightCard(vm = vm, sheets = sheets) }
    item { BodyCompositionCard(vm = vm, sheets = sheets) }
}

// 今日体征 hero（[VitalsHero]）留在 `WellnessScreen.kt`：它带着 baseline 里按「文件名 + 签名」
// 登记的历史条目，换文件会让条目失配、detekt 报新问题（本批不得新增基线）。

/**
 * 体重卡：趋势图 + 今日体重 + 目标区间提示。
 *
 * v1.0.91（批次 16）：`weightToday` / `weightRecent` / `profile` 三条流改由本函数自收。
 * `profile` 只在这里被用于 [WeightTargetHint]（档案里的目标区间），所以跟着收在这里，
 * 不需要根部代收——档案是只读流，各 section 各收一份不会出现「两个真相」。
 */
@Composable
private fun WeightCard(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    val weight by vm.weightToday.collectAsStateWithLifecycle()
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val onRecord = { sheets.value = sheets.value.copy(weight = true) }
    val onManage = { sheets.value = sheets.value.copy(weightManage = true) }

    SectionCard(
        title = stringResource(R.string.wellness_weight_tracking),
        subtitle = if (weightList.isEmpty()) null else stringResource(R.string.wellness_weight_records, weightList.size),
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(onClick = onRecord) {
                    Text(if (weight != null) stringResource(R.string.common_edit) else stringResource(R.string.common_record))
                }
                if (weightList.isNotEmpty()) {
                    TextButton(onClick = onManage) {
                        Text(stringResource(R.string.common_manage))
                    }
                }
            }
        },
    ) {
        // DAO 按日期倒序返回，趋势图需要从旧到新。
        // 必须 remember：List.map 每次返回新实例，会让 TrendChart 的入场动画（以 points 为 key）
        // 在任何一次父级重组时反复从头播放
        val points = remember(weightList) {
            weightList.asReversed().map { TrendPoint(it.date, it.weightKg.toFloat()) }
        }
        if (points.isNotEmpty()) {
            TrendChart(points = points, unit = "kg", label = stringResource(R.string.vitals_weight))
            Spacer(Modifier.height(Spacing.md))
        }
        StatTile(
            label = stringResource(R.string.vitals_today_weight),
            value = weight?.weightKg?.let { "%.1f".format(it) } ?: stringResource(R.string.common_not_recorded),
            unit = weight?.let { "kg" },
        )
        // v10（C9）：体重目标区间提示（区间由档案设定）
        WeightTargetHint(weightKg = weight?.weightKg, profile = profile)
    }
}

/**
 * 身体成分卡：身高 / 腰围 / 臀围 / BMI 四项，无基线时给空态。
 *
 * v1.0.91（批次 16）：`bodyMeasureLatest` 改由本函数自收（原读点在根部）。
 */
@Composable
private fun BodyCompositionCard(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    val bm by vm.bodyMeasureLatest.collectAsStateWithLifecycle()
    val onEdit = { sheets.value = sheets.value.copy(bodyMeasure = true) }

    SectionCard(
        title = stringResource(R.string.vitals_body_measures),
        action = {
            OutlinedButton(onClick = onEdit) {
                Text(if (bm != null) stringResource(R.string.common_update) else stringResource(R.string.common_input_action))
            }
        },
    ) {
        if (bm != null) {
            // 每行都拆成「先取值与缺省文案、再渲染」两段：原实现把 `?.let { … } ?: …` 全塞在一行，
            // 长度超过 detekt 的 140 字符上限（基线里记的就是这几行）。拆分只换行，取值与文案逐字不变。
            val height = bm!!.heightCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled)
            val waist = bm!!.waistCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled)
            val hip = bm!!.hipCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled)
            val bmi = bm!!.bmi?.let { "%.1f".format(it) } ?: stringResource(R.string.common_unfilled)
            KeyValueRow(stringResource(R.string.vitals_height_short), height)
            KeyValueRow(stringResource(R.string.vitals_waist), waist)
            KeyValueRow(stringResource(R.string.vitals_hip), hip)
            KeyValueRow("BMI", bmi)
        } else {
            EmptyState(
                icon = Icons.Rounded.Straighten,
                title = stringResource(R.string.report_no_baseline),
                body = stringResource(R.string.vitals_bmi_hint),
            )
        }
    }
}

/**
 * v10（C9）：体重目标区间提示。
 *
 * 判定逻辑在纯函数 `domain/WeightTarget`（可单测）；这里只呈现结论。
 * 未设目标时不打扰用户（只在填了区间后出现）；只填一侧提示补全。
 */
@Composable
private fun WeightTargetHint(weightKg: Double?, profile: Profile?) {
    val low = profile?.weightTargetLow
    val high = profile?.weightTargetHigh
    val status = WeightTarget.status(weightKg, low, high)
    if (status == WeightTarget.Status.NO_TARGET) return

    Spacer(Modifier.height(Spacing.sm))
    val range = WeightTarget.normalize(low, high)
    when (status) {
        WeightTarget.Status.INCOMPLETE -> Text(
            stringResource(R.string.weight_target_incomplete),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeightTarget.Status.IN_RANGE -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusChip(stringResource(R.string.weight_target_in_range), StatusTone.Success, Icons.Rounded.CheckCircle)
            range?.let {
                Text(
                    stringResource(R.string.weight_target_range_label, fmtNum(it.first), fmtNum(it.second)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeightTarget.Status.BELOW, WeightTarget.Status.ABOVE -> {
            val dev = WeightTarget.deviation(weightKg, low, high) ?: 0.0
            val text = if (status == WeightTarget.Status.BELOW) {
                stringResource(R.string.weight_target_below, fmtNum(-dev))
            } else {
                stringResource(R.string.weight_target_above, fmtNum(dev))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusChip(text, StatusTone.Warning, Icons.Rounded.WarningAmber)
                range?.let {
                    Text(
                        stringResource(R.string.weight_target_range_label, fmtNum(it.first), fmtNum(it.second)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> Unit
    }
}
