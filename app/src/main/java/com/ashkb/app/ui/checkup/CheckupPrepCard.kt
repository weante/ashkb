package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NoMeals
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.domain.CheckupPrep
import com.ashkb.app.ui.components.DisclaimerNote
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate

/**
 * 复诊前准备清单卡片（规划缺口 C4）。
 *
 * "下次哪天、要不要空腹、要带什么"全部由纯函数 [CheckupPrep.plan] 推出，
 * 本卡片只做文案映射与排版——规则能脱离 Compose 单测，界面改动也不会碰算法。
 *
 * v1.0.87（批次 13）：**默认收成摘要**。原实现把整张清单（检查项 + 携带项 + 导出提示 +
 * 免责声明）摊开，把记录卡挤出了首屏——维护者因此以为"记录不存在"。
 * 但清单本身仍是"下次复诊该做什么"的唯一答案（原注释的意图是对的），故折叠态**保留**
 * 三个关键信息：还有几天 / 哪天 / 要不要空腹，外加一句"还有几项检查"。
 * 只有那些"展开也读得完"的清单条目与导出提示收进展开区；展开态用 `rememberSaveable` 记住。
 */
@Composable
internal fun CheckupPrepCard(
    items: List<CheckupItem>,
    records: List<CheckupRecord>,
    today: LocalDate,
) {
    // plan() 内部要解析 ISO 日期串，只在三路输入真正变化时重算，别搭上每次重组
    val plan = remember(items, records, today) { CheckupPrep.plan(items, records, today) }
    // 默认收起：记录卡要能在首屏看见。rememberSaveable = 转屏 / 切 tab 不重置（与 LabDateGroup 同约定）
    var expanded by rememberSaveable { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.checkup_prep_title),
        // 没有下次复诊日时这张卡本来就短（一句话），折叠它只会连"怎么补"一起藏起来
        action = if (plan.hasPlan) {
            { PrepToggle(expanded = expanded, onToggle = { expanded = !expanded }) }
        } else {
            null
        },
    ) {
        if (!plan.hasPlan) {
            // 没有下次复诊日就倒推不出清单：与其给一张空卡，不如指路去补记录
            Text(
                stringResource(R.string.checkup_prep_no_plan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PrepSummary(plan)
            if (expanded) {
                if (plan.checkItems.isNotEmpty()) {
                    PrepBlockLabel(stringResource(R.string.checkup_prep_check_items))
                    plan.checkItems.forEach { PrepBullet(it) }
                }
                PrepBlockLabel(stringResource(R.string.checkup_prep_bring))
                plan.bringItems.forEach { PrepBullet(it) }
                Text(
                    stringResource(R.string.checkup_prep_export_hint),
                    Modifier.padding(top = Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 免责声明**不进折叠区**：C12 的约定是自动生成的健康提示必须与声明同时在场，
            // 而"需空腹"这条提示在折叠态就露在外面——把它藏起来等于让提示失去限定语
            DisclaimerNote(R.string.checkup_prep_disclaimer)
        }
    }
}

/**
 * 折叠态的摘要：倒计时 + 空腹 + 哪天 + 还有几项检查。
 *
 * 这四样是"下次复诊该做什么"里**唯一会漏做就得改天重跑**的部分（空腹尤其），
 * 故留在外面；清单条目本身（检查项名、携带建议）展开即得。
 */
@Composable
private fun PrepSummary(plan: CheckupPrep.Plan) {
    val daysLeft = plan.daysLeft
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // 今天 / 还有 N 天 / 已过 N 天对应三种不同行动，必须分开措辞；null = 日期串解析失败，留给下一行显示原文
        daysLeft?.let {
            Text(
                text = when {
                    it > 0L -> stringResource(R.string.checkup_prep_days_left, it)
                    it == 0L -> stringResource(R.string.checkup_prep_today)
                    else -> stringResource(R.string.checkup_prep_overdue, -it)
                },
                style = MaterialTheme.typography.titleMedium,
                // 临期 / 逾期要立刻行动，用主色顶出来；其余保持正文色，避免整页都在喊
                color = if (plan.isSoon || it < 0L) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        // 空腹是"漏做就得改天重跑"的硬约束，与倒计时同排展示，不埋进正文
        if (plan.fasting) {
            StatusChip(
                text = stringResource(R.string.checkup_prep_fasting_tag),
                tone = StatusTone.Warning,
                icon = Icons.Rounded.NoMeals,
            )
        }
    }
    plan.nextDate?.let {
        // 哪天 + 还有几项检查同一行：日期是必须看见的，项目数让用户知道"展开还有内容"
        Text(
            text = buildString {
                append(stringResource(R.string.checkup_next_visit_line, it))
                if (plan.checkItems.isNotEmpty()) {
                    append(stringResource(R.string.checkup_prep_check_count_suffix, plan.checkItems.size))
                }
            },
            Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 展开 / 收起开关：图标 + 文字，触达区按 [Size.touchMin]（与化验日期分组的折叠开关同形）。 */
@Composable
private fun PrepToggle(expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = Size.touchMin)) {
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null, // 装饰性：文字已承载语义
            modifier = Modifier.size(Size.iconSm),
        )
        Text(stringResource(if (expanded) R.string.common_collapse else R.string.common_expand))
    }
}

/** 小节标题：靠字号 + 上间距分组，不画分割线（与全 App 的"表面色阶分层"一致）。 */
@Composable
private fun PrepBlockLabel(text: String) {
    Text(
        text,
        Modifier.padding(top = Spacing.md),
        style = MaterialTheme.typography.titleSmall,
    )
}

/** 清单条目：手写"·"项目符号——清单只有几条，不值得引入子级 LazyColumn。 */
@Composable
private fun PrepBullet(text: String) {
    Text(
        "· $text",
        Modifier.padding(top = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
    )
}
