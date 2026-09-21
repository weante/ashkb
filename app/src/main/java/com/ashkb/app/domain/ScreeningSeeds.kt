package com.ashkb.app.domain

import com.ashkb.app.data.entity.CheckupType

/**
 * C10（v1.0.37）：生物制剂相关筛查与续方节点种子。
 *
 * 生物制剂（TNF 抑制剂 / JAK 等）用药前需完成结核 / 乙肝 / 丙肝筛查，用药期间按医嘱定期复查；
 * 续方也需提前预约门诊以免断药。规划要求「内置种子」，避免用户自己逐条建。
 *
 * 纯数据 + 纯函数（不依赖 Android），可单测。
 */
object ScreeningSeeds {

    data class Seed(val name: String, val checkType: String, val cycleDays: Int?, val notes: String)

    /** 生物制剂筛查 / 续方节点（检测到生物制剂时一键种入，按 name 幂等去重）。 */
    val BIOLOGIC: List<Seed> = listOf(
        Seed("结核筛查（T-SPOT / PPD）", CheckupType.LAB.name, 365, "生物制剂用药前必查；用药期间按医嘱定期复查"),
        Seed("乙肝筛查（HBV 三系 + DNA）", CheckupType.LAB.name, 365, "生物制剂用药前必查；乙肝携带者需按医嘱监测"),
        Seed("丙肝筛查（HCV 抗体）", CheckupType.LAB.name, 365, "生物制剂用药前必查"),
        Seed("生物制剂续方 / 门诊随访", CheckupType.CONSULT.name, 90, "续方前提前预约门诊，避免断药"),
    )

    /**
     * 计算还需种入的项（按 name 去重，幂等）。
     * @param existingNames 当前在用的复诊项名称集合
     */
    fun pending(existingNames: Set<String>): List<Seed> =
        BIOLOGIC.filter { it.name !in existingNames }
}
