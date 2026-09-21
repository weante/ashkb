package com.ashkb.app.domain

import com.ashkb.app.data.entity.DoseState
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.StopReason

/**
 * C6（v1.0.37）：停药警示判定。
 *
 * 规则：警示文案来自 `StopReason.warning`；但**医生批准的减量方案**（`DoseState.TAPERING`）
 * 下豁免「自行停药」类警示——医嘱减量到停药是方案的自然终点，不该按自行停药风险报警。
 * 其它原因（副作用 / 感染 / 手术）的提示与减量状态无关，照常展示。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object StopWarning {

    /** 停药原因自带警示；减量中且原因为「自行停药」时豁免。 */
    fun forStop(reason: StopReason, med: Medication): String? =
        if (isTapering(reason, med)) null else reason.warning

    /** 生物制剂「自行停药」强化警示是否展示（减量中豁免）。 */
    fun showBiologicStopWarning(reason: StopReason, med: Medication): Boolean =
        reason == StopReason.SELF_STOPPED &&
            MedClass.fromKey(med.medClass) == MedClass.BIOLOGIC &&
            !isTapering(reason, med)

    private fun isTapering(reason: StopReason, med: Medication): Boolean =
        reason == StopReason.SELF_STOPPED && DoseState.of(med) == DoseState.TAPERING
}
