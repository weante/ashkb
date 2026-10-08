package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * v1.0.63 C12：全局免责声明与「自动提示统一前缀」的唯一文案来源。
 *
 * **为什么文案仍然只从这里取（而不是让各 UI 自己写 `R.string.…`）**：与 [RecipeSources] 的
 * DISCLAIMER 同理——这是**合规底线文案**，措辞一旦被改动必须能被单测发现。这里保留
 * 「唯一来源」的约束，只是把来源从字面量换成了**资源 id**：调用方必须经过本对象，
 * 于是 `DisclaimerTest` 能继续用 Robolectric 读中文资源、锁住「必须点明主治医师」「必须以句号收尾」
 * 这类措辞断言；若谁绕过本对象直接写别的字符串，测试不再覆盖得到，故 KDoc 明确要求不得绕过。
 *
 * **i18n（v1.2.6）**：全部字段改为 `@StringRes Int`，落地文案见 `values/strings_domain.xml`
 * 的 `dom_disc_*`。因此不再能是 `const val`（AGP 8 起 `R` 字段非编译期常量），
 * 调用方一律 `stringResource(...)` / `context.getString(...)`。
 *
 * **「统一前缀」的用法**：应用**自动生成**的健康提示（漏服处理 / 复诊准备 / 跨院化验等）
 * 一律在正文前冠以 [PREFIX]，避免各处的「以…为准」措辞各自漂移。
 * 知识库 / 食谱等**条目级**声明与 PDF 页脚仍保留各自完整的自足声明（它们要脱离 App 被阅读）。
 */
object Disclaimer {

    /** 自动提示统一前缀——所有自动生成的健康提示都应冠以此句。 */
    @StringRes
    val PREFIX = R.string.dom_disc_prefix

    /** 核心定性：非医疗建议、不替代诊疗。 */
    @StringRes
    val NOT_MEDICAL_ADVICE = R.string.dom_disc_not_advice

    /** 就医边界：用药与治疗方案始终由医生决定。 */
    @StringRes
    val FOLLOW_DOCTOR = R.string.dom_disc_follow_doctor

    /** 数据边界：离线优先、数据仅存本机。 */
    @StringRes
    val DATA_LOCAL = R.string.dom_disc_data_local

    /** 器械边界：不是医疗器械、不用于诊断治疗决策。 */
    @StringRes
    val NOT_MEDICAL_DEVICE = R.string.dom_disc_not_device

    /** 首启声明的要点清单（UI 自行加项目符号）。 */
    val FIRST_LAUNCH_POINTS = listOf(
        NOT_MEDICAL_ADVICE,
        FOLLOW_DOCTOR,
        DATA_LOCAL,
        NOT_MEDICAL_DEVICE,
    )
}
