package com.ashkb.app.domain

import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.VaccineType

/**
 * v1.0.73：活疫苗安全警报的**唯一判定口径**（纯函数，可单测）。
 *
 * 背景（第三份审查报告 P0-2，已逐行复核）：判定原先内联在
 * `HealthRepository.saveVaccineRecord` 里，写成
 * `record.vaccineType == "LIVE" && record.doctorConfirm == "PENDING"` 这样的**裸字符串精确比较**；
 * 而实体默认值写的是小写 `"pending"`（`Entities.kt`）、表单默认值写的是 `CONFIRMED`
 * （`CheckupForms.kt`）——两侧都不匹配，于是「活疫苗 × 待确认」的 high 级安全警报
 * **形同虚设（安全默认值反转）**，README 还把这条当卖点。
 *
 * 本对象固化三条口径：
 *  1. 比较**一律走枚举** `fromKey`（大小写无关），未知/脏取值兜底为 `PENDING`/`UNKNOWN`
 *     —— 即往**保守**方向落，绝不让脏数据变成「已确认」；
 *  2. 「未表态」与「待确认」等价（都落 `PENDING`）：医学上二者都不能被当成「医生已同意」；
 *  3. 只有明确 `CONFIRMED` / `DECLINED` 才不报警（后者属复诊沟通议题，不在本警报范围）。
 *
 * ## v1.2.13：补上「当前是否在用免疫抑制药物」这一前提（审查报告 §十 第 2 条 + P0-2 第 14 条）
 *
 * 原先这里只有一句免责声明（「本判定不评估用户当前是否在用生物制剂 / DMARD」），于是
 * `R.string.ui_alert_live_vaccine` 那句「AS 患者使用生物制剂 / DMARD 期间接种活疫苗有严重
 * 感染风险」始终停留在**一般性患者教育**层面：应用从未看过药单，用户是不是真的在用
 * 生物制剂，代码一无所知。
 *
 * [immunosuppressantDrugs] 把这条前提接上：从**在用**药单里挑出属于
 * [IMMUNOSUPPRESSANT_CLASSES] 的条目，供警报文案分档引用。
 *
 * ### 刻意不做的三件事（都有理由，改动前请先读）
 *
 *  1. **不改变「要不要报警」**：只在疫苗 × 医生待确认上判定。用户在不在用免疫抑制剂
 *     **不决定警报是否存在**——AS 患者接种活疫苗本身就该与医生确认（而且他随时可能开始
 *     用生物制剂）。用免疫抑制状态做开关会造成「一停药警报就消失」的漏报。
 *  2. **不在「没在用」时声称「没在用」**：`immunosuppressantDrugs` 返回空只代表**药单里没有**
 *     这类药，不代表用户没在吃（可能压根没录药单、或分类填成了 OTHER）。
 *     故文案只做「命中时加强说明」，绝不做「未命中时否认风险」的表述——
 *     否则一条三天后过时的警报会变成**错误的医学信息**，比它不写更糟。
 *  3. **不把糖皮质激素算进来**：低剂量泼尼松在 AS 中常见，而活疫苗禁忌主要针对**大剂量**
 *     激素；本项目拿不到可靠剂量判定（`Medication.dose` 是自由文本），把 GLUCOCORTICOID
 *     一并计入会让每支激素药都触发「免疫抑制」文案，淹没真正需要警示的生物制剂。
 *     这是**知道边界在哪、所以不装懂**，不是遗漏——`GLUCOCORTICOID` 出现在下面的
 *     [IMMUNOSUPPRESSANT_CLASSES] 之外是刻意安排。
 */
object VaccineSafety {

    /**
     * 判定所需的**最小药品投影**：只要名字与药理分类。
     *
     * 为什么不直接收 `List<Medication>`：那个实体有 30 多列，单测要为每个用例拼一整套
     * 构造参数；而这里真正用到的只有两列。窄接口也让 domain 不必知道仓储/实体的其余形状。
     */
    data class ActiveDrug(val name: String, val medClass: String?)

    /** 需要额外提示的免疫抑制类别（`MedClass` 的**名字**，容忍大小写与脏值）。 */
    private val IMMUNOSUPPRESSANT_CLASSES = setOf(MedClass.BIOLOGIC, MedClass.JAK, MedClass.CSDMARD)

    /** 文案里最多点名几支药——再多就该说「等多种药」，而不是印一串念不完的名字。 */
    const val MAX_NAMED_DRUGS = 3

    /** 是否需要留一条「活疫苗待医生确认」的 high 级警报。 */
    fun needsLiveVaccineAlert(vaccineType: String?, doctorConfirm: String?): Boolean =
        VaccineType.fromKey(vaccineType) == VaccineType.LIVE &&
            DoctorConfirm.fromKey(doctorConfirm) == DoctorConfirm.PENDING

    /**
     * 从**在用**药单里挑出当前属于免疫抑制类别的药（按原顺序、去重、截断到 [MAX_NAMED_DRUGS]）。
     *
     * 返回空列表有两种含义（「没有这类药」与「根本没有药单」），本函数**刻意不区分**：
     * 调用方拿它只做一件事——命中时在警报文案里加一句更强的说明。要真的区分，
     * 得再给一个「药单是否为空」的入参，而那个区分在生产路径上改变不了任何行为（见 KDoc ②）。
     *
     * 空名字的条目会被丢掉：文案里出现「你当前在用 （空）」比不说更糟。
     */
    fun immunosuppressantDrugs(active: List<ActiveDrug>): List<ActiveDrug> =
        active.asSequence()
            .filter { it.name.isNotBlank() }
            .filter { MedClass.fromKey(it.medClass) in IMMUNOSUPPRESSANT_CLASSES }
            .distinctBy { it.name.trim() }
            .take(MAX_NAMED_DRUGS)
            .toList()
}
