package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupType

/**
 * C10（v1.0.37）：生物制剂相关筛查与续方节点种子。
 *
 * 生物制剂（TNF 抑制剂 / JAK 等）用药前需完成结核 / 乙肝 / 丙肝筛查，用药期间按医嘱定期复查；
 * 续方也需提前预约门诊以免断药。规划要求「内置种子」，避免用户自己逐条建。
 *
 * 纯数据 + 纯函数（不依赖 Android），可单测。
 *
 * i18n（v1.2.6）：⚠️ **`name` 同时是展示文案与幂等键**（`checkup_items.name` 既是列表标题，
 * 也是 `pending` 判重的依据，且用户可改）。因此这里做两件事：
 *  1. 名称与备注改为 `@StringRes`，由仓储在种入时按当前语言取词；
 *  2. `pending` 的判重不再只比当前语言——见 [pending] 的 `localizedNames` 参数。
 * 不做第 2 件的话，中文用户切到英文后一进药档就会被**重复种入一份英文同名项**。
 */
object ScreeningSeeds {

    data class Seed(
        @StringRes val nameRes: Int,
        val checkType: String,
        val cycleDays: Int?,
        @StringRes val notesRes: Int,
    )

    /** 生物制剂筛查 / 续方节点（检测到生物制剂时一键种入，按 name 幂等去重）。 */
    val BIOLOGIC: List<Seed> = listOf(
        Seed(R.string.screen_seed_1_name, CheckupType.LAB.name, 365, R.string.screen_seed_1_notes),
        Seed(R.string.screen_seed_2_name, CheckupType.LAB.name, 365, R.string.screen_seed_2_notes),
        Seed(R.string.screen_seed_3_name, CheckupType.LAB.name, 365, R.string.screen_seed_3_notes),
        Seed(R.string.screen_seed_4_name, CheckupType.CONSULT.name, 90, R.string.screen_seed_4_notes),
    )

    /**
     * 计算还需种入的项（按名称去重，幂等）。
     *
     * @param existingNames 当前在用的复诊项名称集合
     * @param localizedNames 把一条种子渲染成它在**所有已知语言**下的名称。
     *   必须是「所有已知语言」而不是「当前语言」：库里已有的行是**种入当时的语言**，
     *   切语言后当前语言的名字认不出它，会重复种入。
     */
    fun pending(existingNames: Set<String>, localizedNames: (Seed) -> List<String>): List<Seed> =
        BIOLOGIC.filter { s -> localizedNames(s).none { it in existingNames } }
}
