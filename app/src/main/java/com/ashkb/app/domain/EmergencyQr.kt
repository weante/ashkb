package com.ashkb.app.domain

/**
 * 紧急卡二维码的**内容组装**（v1.2.13 · B6 重做）。
 *
 * ### 这一格二维码要解决什么
 * 钥匙扣 / 钱包里的一张贴纸，急救人员用手机一扫就能读到「血型 / 诊断 / 过敏 / 急救电话」——
 * 不需要装 App、不需要解锁患者手机。因此内容必须是**纯文本**，不是深链接：
 * 任何扫码器都能直接显示，也不依赖本应用是否安装。
 *
 * ### 为什么是这几项，不是全部档案
 * 用户拍板「A 精简急救必需」（2026-10-31）。取舍是容量与可读性：
 * 纠错等级取最高（H，30% 冗余）后，v8（49×49 模块）在 30mm 印刷时约 0.6mm/模块，
 * 老年机也能扫；再往里塞用药清单就会跳到 v10 以上，钥匙扣尺寸印不下。
 * 需要完整用药清单时走「紧急卡打印版 PDF」——那里有整页空间。
 *
 * ### 为什么把标签做参数
 * 与 [EmergencyLockscreen]、[EmergencyMeds] 同一条约定：本对象是**纯函数、不碰 Context**，
 * 患者可见文案全部由调用方从资源注入。这样中文标签与英文标签可以各自排版，
 * 也让这个文件的逻辑能直接单测。
 */
object EmergencyQr {

    /** 品牌行——扫码结果的第一行，让急救人员知道这张卡来自哪个应用、要往哪找更多信息。 */
    const val BRAND = "ASHKB"

    /** 过敏条目上限：急救现场只需知道「对什么过敏」，药学清单不在这一格二维码里。 */
    const val MAX_ALLERGIES = 4

    /**
     * 患者可见标签——由调用方 `stringResource` / `context.getString` 注入。
     *
     * [allergyJoiner] 也走注入：中文的多条并列用「、」，英文用「, 」——
     * 硬编码其中一种，另一种语言的扫码结果就会出现别种的顿号。
     */
    data class Labels(
        val blood: String,
        val diagnosis: String,
        val allergy: String,
        val call: String,
        val allergyJoiner: String,
    )

    /** 参与组装的字段（均可能缺失；缺失的字段整行不出）。 */
    data class Fields(
        val bloodType: String?,
        val diagnosis: String?,
        val allergies: List<String>,
        val emergencyPhone: String?,
    )

    /**
     * 组装二维码正文。
     *
     * 返回 `null` 表示**没有任何可用信息**——此时不应生成二维码：
     * 一张只有「ASHKB」五个字母的贴纸对急救毫无价值，还会让患者误以为自己已经准备好了。
     *
     * @param allergiesJson `profile.allergies` 的原始 JSON 数组字符串；解析口径复用
     *   [ProfileLists]（与该字段在编辑页的读写同一口径，避免两处对同一列有两种理解）。
     */
    fun payload(labels: Labels, fields: Fields, allergiesJson: String?): String? {
        val blood = fields.bloodType?.trim()?.takeIf { it.isNotBlank() }
        val diagnosis = fields.diagnosis?.trim()?.takeIf { it.isNotBlank() }
        val phone = fields.emergencyPhone?.trim()?.takeIf { it.isNotBlank() }
        val allergies = allergiesOf(allergiesJson, fields.allergies)
        if (isEmpty(blood, diagnosis, phone, allergies)) return null

        return buildList {
            add(BRAND)
            blood?.let { add(keyValue(labels.blood, it)) }
            diagnosis?.let { add(keyValue(labels.diagnosis, it)) }
            if (allergies.isNotEmpty()) {
                add(keyValue(labels.allergy, allergies.joinToString(labels.allergyJoiner)))
            }
            phone?.let { add(keyValue(labels.call, it)) }
        }.joinToString(LINE_SEPARATOR)
    }

    /** 四项全空 —— 此时不该生成二维码（一张只有品牌名的贴纸对急救毫无价值）。 */
    private fun isEmpty(
        blood: String?,
        diagnosis: String?,
        phone: String?,
        allergies: List<String>,
    ): Boolean = blood == null && diagnosis == null && phone == null && allergies.isEmpty()

    /** 过敏条目：优先用调用方已解析好的列表，否则从 JSON 列解析。 */
    private fun allergiesOf(allergiesJson: String?, allergies: List<String>): List<String> {
        val raw = if (allergies.isEmpty()) parseAllergiesJson(allergiesJson) else allergies
        return raw.filter { it.isNotBlank() }.distinct().take(MAX_ALLERGIES)
    }

    private fun parseAllergiesJson(allergiesJson: String?): List<String> =
        ProfileLists.fromJson(allergiesJson).split(ALLERGY_SPLITTER_REGEX).map { it.trim() }

    private fun keyValue(label: String, value: String): String = "$label$KEY_SEPARATOR$value"

    /** 行分隔符——扫码器读到的换行。 */
    private const val LINE_SEPARATOR = "\n"

    /** 键值分隔符（半角冒号，兼容英文界面与各扫码器的展示）。 */
    private const val KEY_SEPARATOR = ":"

    /** `ProfileLists.fromJson` 产出的是半角逗号分隔文本；存量脏值里也可能出现全角逗号。 */
    private val ALLERGY_SPLITTER_REGEX = Regex("[,，]")
}
