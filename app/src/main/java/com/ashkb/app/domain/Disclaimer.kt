package com.ashkb.app.domain

/**
 * v1.0.63 C12：全局免责声明与「自动提示统一前缀」的唯一文案来源。
 *
 * **为什么放在 domain（而非 strings.xml）**：与 [RecipeSources] 的 DISCLAIMER 同理——
 * 这是**合规底线文案**，措辞一旦被改动必须能被单测发现；而 domain 层是纯 JVM、无 Context，
 * 本就无法读资源。UI 直接引用本对象的常量。
 *
 * **「统一前缀」的用法**：应用**自动生成**的健康提示（漏服处理 / 复诊准备 / 跨院化验等）
 * 一律在正文前冠以 [PREFIX]，避免各处的「以…为准」措辞各自漂移。
 * 知识库 / 食谱等**条目级**声明与 PDF 页脚仍保留各自完整的自足声明（它们要脱离 App 被阅读）。
 */
object Disclaimer {

    /** 自动提示统一前缀——所有自动生成的健康提示都应冠以此句。 */
    const val PREFIX = "仅供参考，以主治医师医嘱为准。"

    /** 核心定性：非医疗建议、不替代诊疗。 */
    const val NOT_MEDICAL_ADVICE =
        "本应用是个人健康管理记录工具，不构成任何医疗建议，不能替代医生诊疗。"

    /** 就医边界：用药与治疗方案始终由医生决定。 */
    const val FOLLOW_DOCTOR =
        "用药与治疗方案请始终遵从主治医师医嘱，任何调整以医嘱为准。"

    /** 数据边界：离线优先、数据仅存本机。 */
    const val DATA_LOCAL =
        "所有健康数据仅存于本机，不上传任何服务器。"

    /** 器械边界：不是医疗器械、不用于诊断治疗决策。 */
    const val NOT_MEDICAL_DEVICE =
        "本应用不是医疗器械，记录与提示均不用于诊断或治疗决策。"

    /** 首启声明的要点清单（UI 自行加项目符号）。 */
    val FIRST_LAUNCH_POINTS = listOf(
        NOT_MEDICAL_ADVICE,
        FOLLOW_DOCTOR,
        DATA_LOCAL,
        NOT_MEDICAL_DEVICE,
    )
}
