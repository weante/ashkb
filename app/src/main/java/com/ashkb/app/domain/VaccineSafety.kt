package com.ashkb.app.domain

import com.ashkb.app.data.entity.DoctorConfirm
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
 * ⚠️ **本判定不评估**「用户当前是否在用生物制剂 / DMARD」——那需要药物清单与医学规则，
 * 属另一批工作。警报文案只能做通用患者教育，**不得声称应用已核对该前提**。
 */
object VaccineSafety {

    /** 是否需要留一条「活疫苗待医生确认」的 high 级警报。 */
    fun needsLiveVaccineAlert(vaccineType: String?, doctorConfirm: String?): Boolean =
        VaccineType.fromKey(vaccineType) == VaccineType.LIVE &&
            DoctorConfirm.fromKey(doctorConfirm) == DoctorConfirm.PENDING
}
