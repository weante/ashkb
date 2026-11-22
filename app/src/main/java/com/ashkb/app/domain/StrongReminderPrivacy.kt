package com.ashkb.app.domain

/**
 * v1.2.22（F2）：末级强提醒（全屏）的**药名隐私口径**。
 *
 * 背景（独立审查 F2）：末级强提醒会用 `fullScreenIntent` 拉起全屏页直接盖在锁屏上，
 * 原实现把「药名 + 剂量」明文画在最显眼的位置——旁人瞥一眼锁屏就知道用户在用什么药
 * （生物制剂等对 AS 患者等于等于自曝病情）。
 *
 * 口径：**默认显示**（保持既有功能），由用户在「我的 → 提醒设置」里显式关闭。
 * 关闭后强提醒只说「该服药了」，不出现药名与剂量。
 *
 * 为什么关闭后功能不残：强提醒通知上的「已服用」动作按钮与全屏页的「已服用」按钮
 * 都按 Intent 里的 `med_id` 写回，**不依赖文案**——所以掩码不会打错打卡记录。
 * 用户想看具体药品，通知点进应用后到「药单」里看得到。
 */
object StrongReminderPrivacy {

    /**
     * 是否要掩码（不显示药名与剂量）。
     *
     * @param showMedication 用户开关：true = 显示药名与剂量（默认）
     */
    fun maskMedication(showMedication: Boolean): Boolean = !showMedication

    /**
     * 要显示的「药名 + 剂量」文本。
     *
     * 两个值都空时返回空串（此时页面不画这一行，而不是画一行空白）。
     */
    fun medicationLine(medName: String, medDose: String): String =
        listOf(medName, medDose)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
}
