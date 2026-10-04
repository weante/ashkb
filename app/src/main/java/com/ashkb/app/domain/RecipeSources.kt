package com.ashkb.app.domain

/**
 * B3（v1.0.39）：食谱**出处编号台账**。
 *
 * 界面**只显示编号**（`R1`…），完整题录仅在某条食谱的**详情里展开**——列表不占版面。
 * 这些文献支撑的是「膳食模式与摄入」层面的结论，**不是**单条食谱的临床验证；
 * 且现有证据整体有限（尤其 AS 专项），故不构成医疗建议。
 *
 * v1.1.2：编号由 `S1`…`S9` 改为 `R1`…`R9`。**为什么必须改前缀**：知识库那一套
 * `S1`–`S4` 是**证据层级**（官方指南 / 期刊全文 / 患者组织 / 商业平台，见
 * `ui/knowledge/KbSourceTiers.kt`），本台账的编号是**出处序号**——两者语义毫无关系，
 * 却都以裸 `S1` 出现在同一个应用里，患者无从分辨「这个 S1 是等级还是第 1 篇文献」。
 * 加 `R`（Recipe）前缀后两者一眼可分，且将来若食谱编号扩到 99 条也不会与层级混淆。
 */
object RecipeSources {

    data class Source(val id: String, val citation: String, val note: String)

    const val R1 = "R1"
    const val R2 = "R2"
    const val R3 = "R3"
    const val R4 = "R4"
    const val R5 = "R5"
    const val R6 = "R6"
    const val R7 = "R7"
    const val R8 = "R8"
    const val R9 = "R9"

    val ALL: List<Source> = listOf(
        Source(
            R1,
            "Ramonda R, Cozzi G, Oliviero F. Nutritional guidance in spondyloarthritis: confronting the evidence gap. Clin Exp Rheumatol 2025;37:269.",
            "综述：地中海饮食可能减轻炎症与症状；超重 / 肥胖者低热量饮食与减重可获益；加工食品 / 饱和脂肪 / 精制糖可能加重",
        ),
        Source(
            R2,
            "Macfarlane TV, et al. Relationship between diet and ankylosing spondylitis: a systematic review. Eur J Rheumatol 2018;5(1):45-52.",
            "系统综述：AS 膳食证据**极为有限且不确定**（多为小样本、单臂研究）",
        ),
        Source(
            R3,
            "Van den Bruel K, et al. Nutrition and diet in rheumatoid arthritis, axial spondyloarthritis, and psoriatic arthritis: a systematic review. Front Med 2025;12:1655165.",
            "系统综述：axSpA 证据有限；ω-3（PUFA）补充有潜在获益；姜 / 姜黄等香辛料有抗炎证据",
        ),
        Source(
            R4,
            "Hulander E, et al. Dietary intake is related to disease activity and inflammation in radiographic axial spondyloarthritis. Scand J Rheumatol 2025;54(5).",
            "横断面研究：海洋 ω-3 摄入低与疾病活动度高相关；膳食纤维密度低与炎症更重相关",
        ),
        Source(
            R5,
            "Yemula N, Sheikh R. Gut microbiota in axial spondyloarthritis: genetics, medications and future treatments. ARP Rheumatology 2024;3:216-225.",
            "综述：增加纤维摄入有助维持肠道环境；低淀粉饮食机制存在但临床证据有限；低果糖饮食为潜在方向",
        ),
        Source(
            R6,
            "Yang L, et al. A Possible Role of Intestinal Microbiota in the Pathogenesis of Ankylosing Spondylitis. Int J Mol Sci 2016;17(12):2126.",
            "机制综述：肠道菌群与 HLA-B27 分子模拟假说（解释「为什么饮食可能与 AS 相关」）",
        ),
        Source(
            R7,
            "Kalogeropoulou AA. Nutritional aspects and vitamin D supplementation in ankylosing spondylitis. JRPMS 2017;1(2):23-30.",
            "综述：低淀粉饮食与地中海饮食为两大方向；维生素 D 与骨健康",
        ),
        Source(
            R8,
            "NASS（英国国家强直性脊柱炎协会）Your Diet. nass.co.uk",
            "患者组织实操口径：每日 ≥4 份蔬菜 + 2 份水果；钙约 700mg/日；ω-3 ≥2g/日；少盐少糖、多用香辛料；地中海饮食为值得遵循的均衡模式；警惕「能治愈 AS」的饮食宣称",
        ),
        Source(
            R9,
            "Erciyes University. Evaluation of the Effect of the Mediterranean Diet on Disease Activity … in Patients With Axial Spondyloarthritis Receiving Biologic Therapy (NCT07170384，进行中).",
            "提示：该方向仍在随机对照试验验证中，**尚无定论**",
        ),
    )

    private val byId: Map<String, Source> = ALL.associateBy { it.id }

    /**
     * v1.1.2：旧编号（`S1`…`S9`）→ 新编号（`R1`…`R9`）。
     *
     * **为什么必须有这层兼容**：出处编号不是每次渲染现算的，它随种子一起**写进数据库**
     * （`recipes.sources` 里是 `["S1","S4"]` 这样的 JSON 数组），而 `seedIfMissing` 按
     * 固定 id 幂等——**已安装用户库里那 10 条种子食谱永远不会被重新种一遍**，它们存着的
     * 还是旧编号。只改台账不改这一层的话，所有老用户打开食谱详情会看到**出处整节消失**
     * （`of` 查不到就 `mapNotNull` 掉），比改前的「编号撞车」严重得多。
     *
     * 映射表由 [ALL] **反推**而不是写死 `S<n> → R<n>` 的通配：只有真实存在的那 9 个旧值
     * 才会被认领。于是 `S10` / `S0` / `S99` 这类脏数据仍然查不到（若走通配写法，
     * `S99` 会被洗成 `R9`——凭空给一条不存在的出处安上题录）。
     */
    private val legacyIds: Map<String, String> =
        ALL.associate { "S" + it.id.removePrefix("R") to it.id }

    /** 查表前归一编号：命中新编号就原样用，否则才试旧编号；仍不认识就原样返回（照旧查不到）。 */
    private fun canonical(id: String): String = if (byId.containsKey(id)) id else legacyIds[id] ?: id

    fun citation(id: String): String? = byId[canonical(id)]?.citation

    fun note(id: String): String? = byId[canonical(id)]?.note

    /** 某条食谱的出处（按传入顺序，未知编号忽略——脏数据不致崩）。 */
    fun of(ids: List<String>): List<Source> = ids.mapNotNull { byId[canonical(it)] }

    /** 全局免责声明（食谱详情固定展示）。 */
    const val DISCLAIMER: String =
        "参考食谱，非医疗建议；膳食不能替代药物。现有证据整体有限（尤其强直专项），调整饮食前请与风湿科医生或营养师确认。"
}
