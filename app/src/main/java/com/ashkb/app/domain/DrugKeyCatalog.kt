package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedClass

/** 通用名键候选：一键选中自动回填（键 / 药名 / 商品名 / 类别） */
data class DrugKeyEntry(
    val key: String,
    val display: String,
    val brand: String? = null,
    val medClass: MedClass = MedClass.OTHER,
    val aliases: List<String> = emptyList(),
)

/**
 * P5 修订 R8：通用名键自动匹配目录。
 * 覆盖 AS 常用药（生物制剂 / DMARD / NSAID / JAK / 激素 / 骨健康）+ 类别键。
 * 检索：键 / 中文名 / 商品名 / 拼音别名 模糊命中。
 */
object DrugKeyCatalog {

    val entries = listOf(
        // ---- TNF 抑制剂 ----
        DrugKeyEntry("adalimumab", "阿达木单抗", "修美乐", MedClass.BIOLOGIC, listOf("humira", "阿达")),
        DrugKeyEntry("etanercept", "依那西普", "恩利", MedClass.BIOLOGIC, listOf("enbrel", "益赛普")),
        DrugKeyEntry("infliximab", "英夫利西单抗", "类克", MedClass.BIOLOGIC, listOf("remicade")),
        DrugKeyEntry("golimumab", "戈利木单抗", "欣普尼", MedClass.BIOLOGIC),
        DrugKeyEntry("certolizumab", "培塞利珠单抗", "希敏佳", MedClass.BIOLOGIC),
        // ---- IL-17 / IL-12·23 / IL-23 ----
        DrugKeyEntry("secukinumab", "司库奇尤单抗", "可善挺", MedClass.BIOLOGIC, listOf("cosentyx")),
        DrugKeyEntry("ixekizumab", "依奇珠单抗", "拓咨", MedClass.BIOLOGIC, listOf("taltz")),
        DrugKeyEntry("ustekinumab", "乌司奴单抗", "喜达诺", MedClass.BIOLOGIC, listOf("stelara")),
        DrugKeyEntry("guselkumab", "古塞奇尤单抗", "特诺雅", MedClass.BIOLOGIC, listOf("tremfya")),
        DrugKeyEntry("risankizumab", "瑞莎珠单抗", "利生奇珠", MedClass.BIOLOGIC, listOf("skyrizi")),
        // ---- 传统 DMARD ----
        DrugKeyEntry("methotrexate", "甲氨蝶呤", null, MedClass.CSDMARD, listOf("mtx", "甲氨喋呤")),
        DrugKeyEntry("sulfasalazine", "柳氮磺吡啶", "维柳芬", MedClass.CSDMARD, listOf("sas", "ssz", "柳氮")),
        DrugKeyEntry("leflunomide", "来氟米特", "爱若华", MedClass.CSDMARD),
        DrugKeyEntry("iguratimod", "艾拉莫德", "艾得辛", MedClass.CSDMARD),
        // ---- NSAIDs ----
        DrugKeyEntry("naproxen", "萘普生", null, MedClass.NSAID),
        DrugKeyEntry("ibuprofen", "布洛芬", "芬必得", MedClass.NSAID),
        DrugKeyEntry("diclofenac", "双氯芬酸", "扶他林", MedClass.NSAID, listOf("戴芬")),
        DrugKeyEntry("celecoxib", "塞来昔布", "西乐葆", MedClass.NSAID),
        DrugKeyEntry("etoricoxib", "依托考昔", "安康信", MedClass.NSAID),
        DrugKeyEntry("meloxicam", "美洛昔康", "莫比可", MedClass.NSAID),
        DrugKeyEntry("indomethacin", "吲哚美辛", "消炎痛", MedClass.NSAID),
        DrugKeyEntry("acetaminophen", "对乙酰氨基酚", "泰诺林", MedClass.NSAID, listOf("扑热息痛", "paracetamol")),
        // ---- JAK 抑制剂 ----
        DrugKeyEntry("tofacitinib", "托法替布", "尚杰", MedClass.JAK, listOf("xeljanz")),
        DrugKeyEntry("upadacitinib", "乌帕替尼", "艾乐明", MedClass.JAK, listOf("rinvoq")),
        DrugKeyEntry("baricitinib", "巴瑞替尼", "艾乐铭", MedClass.JAK, listOf("olumiant")),
        // ---- 糖皮质激素 ----
        DrugKeyEntry("prednisone", "泼尼松", "强的松", MedClass.GLUCOCORTICOID),
        DrugKeyEntry("methylprednisolone", "甲泼尼龙", "美卓乐", MedClass.GLUCOCORTICOID, listOf("甲强龙")),
        DrugKeyEntry("dexamethasone", "地塞米松", null, MedClass.GLUCOCORTICOID),
        // ---- 骨健康 / 辅助 ----
        DrugKeyEntry("alendronate", "阿仑膦酸钠", "福善美", MedClass.OTHER, listOf("福善美", "固邦")),
        DrugKeyEntry("zoledronic", "唑来膦酸", "密固达", MedClass.OTHER),
        DrugKeyEntry("calcitriol", "骨化三醇", "罗盖全", MedClass.OTHER),
        DrugKeyEntry("colecalciferol", "维生素 D3", null, MedClass.OTHER, listOf("vitamin_d3", "维d", "vd")),
        DrugKeyEntry("calcium", "钙剂", null, MedClass.OTHER, listOf("碳酸钙", "迪巧", "钙尔奇")),
        DrugKeyEntry("folic_acid", "叶酸", null, MedClass.OTHER, listOf("folate", "亚叶酸")),
        // ---- 类别键（整类检索） ----
        DrugKeyEntry("nsaid", "【类别】全部 NSAIDs", null, MedClass.NSAID, listOf("消炎镇痛类")),
        DrugKeyEntry("biologic", "【类别】全部生物制剂", null, MedClass.BIOLOGIC, listOf("生物类")),
        DrugKeyEntry("csdmard", "【类别】传统 DMARD", null, MedClass.CSDMARD, listOf("dmard")),
        DrugKeyEntry("tnf", "【类别】TNF 抑制剂", null, MedClass.BIOLOGIC),
        DrugKeyEntry("il17", "【类别】IL-17 抑制剂", null, MedClass.BIOLOGIC),
        DrugKeyEntry("jak", "【类别】JAK 抑制剂", null, MedClass.JAK),
        DrugKeyEntry("glucocorticoid", "【类别】糖皮质激素", null, MedClass.GLUCOCORTICOID, listOf("激素类")),
    )

    /** 模糊检索：键前缀 > 键包含 > 中文/别名包含；空查询返回空 */
    fun suggest(query: String, limit: Int = 6): List<DrugKeyEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            val hit = when {
                e.key.startsWith(q) -> 0
                e.key.contains(q) -> 1
                e.display.lowercase().contains(q) -> 2
                e.brand?.lowercase()?.contains(q) == true -> 2
                e.aliases.any { it.lowercase().contains(q) } -> 3
                else -> return@mapNotNull null
            }
            hit to e
        }.sortedWith(compareBy({ it.first }, { it.second.key.length })).take(limit).map { it.second }
    }

    /** 输入是否已是一个确定键（避免已键入完整键时还显示建议列表） */
    fun isExactKey(query: String): Boolean =
        entries.any { it.key.equals(query.trim().lowercase(), ignoreCase = true) }
}
