package com.ashkb.app.domain

/**
 * B3（v1.0.39）：食谱**出处编号台账**。
 *
 * 界面**只显示编号**（`S1`…），完整题录仅在某条食谱的**详情里展开**——列表不占版面。
 * 这些文献支撑的是「膳食模式与摄入」层面的结论，**不是**单条食谱的临床验证；
 * 且现有证据整体有限（尤其 AS 专项），故不构成医疗建议。
 */
object RecipeSources {

    data class Source(val id: String, val citation: String, val note: String)

    const val S1 = "S1"
    const val S2 = "S2"
    const val S3 = "S3"
    const val S4 = "S4"
    const val S5 = "S5"
    const val S6 = "S6"
    const val S7 = "S7"
    const val S8 = "S8"
    const val S9 = "S9"

    val ALL: List<Source> = listOf(
        Source(
            S1,
            "Ramonda R, Cozzi G, Oliviero F. Nutritional guidance in spondyloarthritis: confronting the evidence gap. Clin Exp Rheumatol 2025;37:269.",
            "综述：地中海饮食可能减轻炎症与症状；超重 / 肥胖者低热量饮食与减重可获益；加工食品 / 饱和脂肪 / 精制糖可能加重",
        ),
        Source(
            S2,
            "Macfarlane TV, et al. Relationship between diet and ankylosing spondylitis: a systematic review. Eur J Rheumatol 2018;5(1):45-52.",
            "系统综述：AS 膳食证据**极为有限且不确定**（多为小样本、单臂研究）",
        ),
        Source(
            S3,
            "Van den Bruel K, et al. Nutrition and diet in rheumatoid arthritis, axial spondyloarthritis, and psoriatic arthritis: a systematic review. Front Med 2025;12:1655165.",
            "系统综述：axSpA 证据有限；ω-3（PUFA）补充有潜在获益；姜 / 姜黄等香辛料有抗炎证据",
        ),
        Source(
            S4,
            "Hulander E, et al. Dietary intake is related to disease activity and inflammation in radiographic axial spondyloarthritis. Scand J Rheumatol 2025;54(5).",
            "横断面研究：海洋 ω-3 摄入低与疾病活动度高相关；膳食纤维密度低与炎症更重相关",
        ),
        Source(
            S5,
            "Yemula N, Sheikh R. Gut microbiota in axial spondyloarthritis: genetics, medications and future treatments. ARP Rheumatology 2024;3:216-225.",
            "综述：增加纤维摄入有助维持肠道环境；低淀粉饮食机制存在但临床证据有限；低果糖饮食为潜在方向",
        ),
        Source(
            S6,
            "Yang L, et al. A Possible Role of Intestinal Microbiota in the Pathogenesis of Ankylosing Spondylitis. Int J Mol Sci 2016;17(12):2126.",
            "机制综述：肠道菌群与 HLA-B27 分子模拟假说（解释「为什么饮食可能与 AS 相关」）",
        ),
        Source(
            S7,
            "Kalogeropoulou AA. Nutritional aspects and vitamin D supplementation in ankylosing spondylitis. JRPMS 2017;1(2):23-30.",
            "综述：低淀粉饮食与地中海饮食为两大方向；维生素 D 与骨健康",
        ),
        Source(
            S8,
            "NASS（英国国家强直性脊柱炎协会）Your Diet. nass.co.uk",
            "患者组织实操口径：每日 ≥4 份蔬菜 + 2 份水果；钙约 700mg/日；ω-3 ≥2g/日；少盐少糖、多用香辛料；地中海饮食为值得遵循的均衡模式；警惕「能治愈 AS」的饮食宣称",
        ),
        Source(
            S9,
            "Erciyes University. Evaluation of the Effect of the Mediterranean Diet on Disease Activity … in Patients With Axial Spondyloarthritis Receiving Biologic Therapy (NCT07170384，进行中).",
            "提示：该方向仍在随机对照试验验证中，**尚无定论**",
        ),
    )

    private val byId: Map<String, Source> = ALL.associateBy { it.id }

    fun citation(id: String): String? = byId[id]?.citation

    fun note(id: String): String? = byId[id]?.note

    /** 某条食谱的出处（按传入顺序，未知编号忽略——脏数据不致崩）。 */
    fun of(ids: List<String>): List<Source> = ids.mapNotNull { byId[it] }

    /** 全局免责声明（食谱详情固定展示）。 */
    const val DISCLAIMER: String =
        "参考食谱，非医疗建议；膳食不能替代药物。现有证据整体有限（尤其强直专项），调整饮食前请与风湿科医生或营养师确认。"
}
