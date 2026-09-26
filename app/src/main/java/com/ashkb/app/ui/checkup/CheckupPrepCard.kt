package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoMeals
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate

/**
 * 复诊前准备清单卡片（规划缺口 C4）。
 *
 * "下次哪天、要不要空腹、要带什么"全部由纯函数 [CheckupPrep.plan] 推出，
 * 本卡片只做文案映射与排版——规则能脱离 Compose 单测，界面改动也不会碰算法。
 */
@Composable
internal fun CheckupPrepCard(
    items: List<CheckupItem>,
    records: List<CheckupRecord>,
    today: LocalDate,
) {
    // plan() 内部要解析 ISO 日期串，只在三路输入真正变化时重算，别搭上每次重组
    val plan = remember(items, records, today) { CheckupPrep.plan(items, records, today) }

    SectionCard(title = stringResource(R.string.checkup_prep_title)) {
        if (!plan.hasPlan) {
            // 没有下次复诊日就倒推不出清单：与其给一张空卡，不如指路去补记录
            Text(
                stringResource(R.string.checkup_prep_no_plan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
                Text(
                    stringResource(R.string.checkup_next_visit_line, it),
                    Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            DisclaimerNote(R.string.checkup_prep_disclaimer)
        }
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
