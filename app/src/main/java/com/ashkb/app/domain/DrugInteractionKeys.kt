package com.ashkb.app.domain

/**
 * v1.1.2（批次 18）：**用药安全核对链的结构化匹配**——把「知识条目 ↔ 患者药单」的关联
 * 从 `payload LIKE '%key%'` 子串匹配换成**显式词汇表 + 家族归属 + 双药并存判定**。
 *
 * ### 为什么必须换掉子串匹配（审查报告 §2，本批次实测复现）
 * 旧实现是 `SELECT * FROM kb_entries WHERE category='interaction' AND payload LIKE '%'||:key||'%'`
 * （`Daos.kt`），键由 `med.nameKey` / `med.medClass` / 硬编码的 `medClassKeys()` 三部分拼成。
 * 它**在两个方向上都不可靠**，且三条都已实测复现：
 *
 * 1. **漏**：`medClassKeys` 没有 JAK 分支 → 乌帕替尼 / 托法替布 / 巴瑞替尼药单**零提示**。
 * 2. **错**：`MedClass.OTHER.name` = 字符串 `"OTHER"` 被当 LIKE 模式传进去，而 itx-015 引的
 *    FDA 原文含英文单词 `other` → **所有 OTHER 类药**都收到「阿仑膦酸钠：晨起空腹白水送服」。
 * 3. **来氟米特 / 艾拉莫德**（都属 csDMARD）收到「**甲氨蝶呤**必须每周一次，误当每日服用可致死」。
 *
 * 正确性无法靠「把键拼得更细」补回来——子串匹配本身不是「药物身份」的表达方式。
 *
 * ### 匹配怎么做
 * ① [vocabulary] 把药单上的每个词（`nameKey` / 药名 / 商品名 / `medClass` / 补剂名 / 补剂类目）
 *    归一成**规范键**；
 * ② 规范键再归到**家族键**（[parents]）——`adalimumab` → {adalimumab, tnf_inhibitor, biologic}；
 * ③ 条目声明它关心的键（[classify]），药单侧与条目侧的键集合相交即适用（[active]）；
 * ④ **药品–药品**的组合条目要求两侧都真的在药单上（[matches]），否则组合风险会被错报成单药固有风险。
 *
 * ### 一处刻意的例外：`csdmard` 不登记成员
 * `csdmard`（传统 DMARD）**只登记类目词、不登记具体药**。理由：种子里传统 DMARD 的条目
 * （itx-001 甲氨蝶呤 / itx-005 柳氮磺吡啶 / itx-006 / itx-007 / itx-011）**全部挂在具体药上**，
 * 没有一条挂在 `csdmard` 上。若把 methotrexate / sulfasalazine / leflunomide / iguratimod
 * 登记成 `csdmard` 的成员，「同属一个治疗线」就会变成「互相命中对方的条目」——
 * 那正是旧实现把来氟米特错配到甲氨蝶呤致死警告的根因（本批次踩过一次，见该键的注释）。
 *
 * 反过来，`nsaid` / `glucocorticoid` / `tnf_inhibitor` / `biologic` **必须**登记成员：
 * 种子把条目挂在这些类目上（itx-003/004/008/009/011/013/014 = `nsaid`，itx-010 = `tnf_inhibitor`，
 * itx-004 的 `drug_b` = `glucocorticoid`），不登记成员它们会对所有患者**静默失效**。
 *
 * ### 边界
 * 本文件只做**匹配**，不做医学判断：词汇表里的每一个键都来自种子 JSON 自身已经写下的
 * `drug_a` / `drug_b` 取值与 `DrugKeyCatalog` / `MedClass` 既有枚举，没有新增任何医学结论。
 * 「JAK 抑制剂按生物制剂家族级条目提示」是**匹配层的覆盖取舍**（宁可多提示一条，也不要零提示），
 * 条目该不该按家族改写仍由维护者裁决（见批次 18 回报的第三阶段提案表）。
 */
internal object DrugInteractionKeys {

    /**
     * 种子里会被写成 `drug_b`、但**不是药单上的药品**的语境键。
     *
     * `live_vaccine`（itx-010 / itx-012）来自疫苗记录，`food_calcium`（itx-015）来自补剂里的钙/维生素 D。
     * 它们登记在 [contextKeys] 而不是 [vocabulary]：这两个键**不作适用性前提**——
     * 「生物制剂治疗期间避免活疫苗」「阿仑膦酸盐需与钙/食物间隔」都是**该药患者迟早要看到的规则**，
     * 患者还没录疫苗 / 没录钙剂就不显示，等于把整条用药规则藏起来。
     * 真正要求「两侧都在药单上」的只有药品–药品的组合（见 [matches]）。
     */
    const val LIVE_VACCINE = "live_vaccine"

    /** itx-009 的 `drug_b`：**数量**条件键（药单上 ≥2 种不同的抗炎药），不是「另一支具体的药」。 */
    private const val MULTI_ANTI_INFLAMMATORY = "anti_inflammatory_multi"

    /**
     * 词 → 规范键的映射。**声明顺序有意义**，它是 [parents] 与 [drugOf] 结果的顺序：
     * 具体药在前、家族键在后，且家族键**由细到粗**（`tnf_inhibitor` 必须早于 `biologic`），
     * 这样「具体药 → 类别 → 生物制剂」的自然层级在结果里也是这个顺序。
     */
    private val vocabulary: Map<String, List<String>> = mapOf(
        // ---- 具体药（键名即通用名；别名来自 DrugKeyCatalog 的 key / display / brand / aliases）----
        "methotrexate" to listOf("methotrexate", "甲氨蝶呤", "甲氨喋呤", "mtx"),
        "sulfasalazine" to listOf("sulfasalazine", "柳氮磺吡啶", "维柳芬", "sas", "ssz", "柳氮"),
        "leflunomide" to listOf("leflunomide", "来氟米特", "爱若华"),
        "iguratimod" to listOf("iguratimod", "艾拉莫德", "艾得辛"),
        "adalimumab" to listOf("adalimumab", "阿达木单抗", "修美乐", "humira", "阿达"),
        "etanercept" to listOf("etanercept", "依那西普", "恩利", "enbrel", "益赛普"),
        "infliximab" to listOf("infliximab", "英夫利西单抗", "类克", "remicade"),
        "golimumab" to listOf("golimumab", "戈利木单抗", "欣普尼"),
        "certolizumab" to listOf("certolizumab", "培塞利珠单抗", "希敏佳"),
        "secukinumab" to listOf("secukinumab", "司库奇尤单抗", "可善挺", "cosentyx"),
        "ixekizumab" to listOf("ixekizumab", "依奇珠单抗", "拓咨", "taltz"),
        "ustekinumab" to listOf("ustekinumab", "乌司奴单抗", "喜达诺", "stelara"),
        "guselkumab" to listOf("guselkumab", "古塞奇尤单抗", "特诺雅", "tremfya"),
        "risankizumab" to listOf("risankizumab", "瑞莎珠单抗", "利生奇珠", "skyrizi"),
        "tofacitinib" to listOf("tofacitinib", "托法替布", "尚杰", "xeljanz"),
        "upadacitinib" to listOf("upadacitinib", "乌帕替尼", "艾乐明", "rinvoq"),
        "baricitinib" to listOf("baricitinib", "巴瑞替尼", "艾乐铭", "olumiant"),
        "prednisone" to listOf("prednisone", "泼尼松", "强的松"),
        "methylprednisolone" to listOf("methylprednisolone", "甲泼尼龙", "美卓乐", "甲强龙"),
        "dexamethasone" to listOf("dexamethasone", "地塞米松"),
        "naproxen" to listOf("naproxen", "萘普生"),
        "ibuprofen" to listOf("ibuprofen", "布洛芬", "芬必得"),
        "diclofenac" to listOf("diclofenac", "双氯芬酸", "扶他林", "戴芬"),
        "celecoxib" to listOf("celecoxib", "塞来昔布", "西乐葆"),
        "etoricoxib" to listOf("etoricoxib", "依托考昔", "安康信"),
        "meloxicam" to listOf("meloxicam", "美洛昔康", "莫比可"),
        "indomethacin" to listOf("indomethacin", "吲哚美辛", "消炎痛"),
        "acetaminophen" to listOf("acetaminophen", "对乙酰氨基酚", "泰诺林", "扑热息痛", "paracetamol"),
        "alendronate" to listOf("alendronate", "阿仑膦酸钠", "福善美", "固邦"),
        "zoledronic" to listOf("zoledronic", "唑来膦酸", "密固达"),
        "calcitriol" to listOf("calcitriol", "骨化三醇", "罗盖全"),
        // 「维生素 D」是 `SupplementCategory.VITAMIN_D.label` 的字面量，补剂档案会把它当 token 递进来；
        // 「维生素 D3」是同一支药在药单上的常见写法。两种写法都登记，否则补剂侧的写法永远匹配不上。
        "colecalciferol" to listOf(
            "colecalciferol", "维生素 D3", "维生素d3", "维生素 D", "维生素d", "vitamin_d3", "维d", "vd",
        ),
        // `SupplementCategory.CALCIUM.label` = 「钙」（补剂档案的类目名会作为 token 参与匹配），
        // 故这里把单字「钙」也登记进来——否则「补剂类目 = 钙」的那条补剂对钙相关条目不可见。
        "calcium" to listOf("calcium", "钙", "钙剂", "碳酸钙", "迪巧", "钙尔奇"),
        "folic_acid" to listOf("folic_acid", "叶酸", "folate", "亚叶酸"),
        // ---- 药单上不会单独出现、但条目拿它当 drug_b 用的「另一类药」键 ----
        // `warfarin` / `ssri` / `ppi` / `antihypertensive` 这几类药**不在 DrugKeyCatalog 目录里**
        // （审查报告附录 B「注意二」已核，本批次复核为真）。它们只能靠 nameKey / 药名手工录入命中，
        // 故这里把各自的常见写法收进词汇表，让「手工录了华法林」也能触发 itx-003。
        "warfarin" to listOf("warfarin", "华法林", "华法林钠", "coumadin", "可迈丁"),
        "ssri" to listOf(
            "ssri", "舍曲林", "sertraline", "氟西汀", "fluoxetine", "帕罗西汀", "paroxetine",
            "西酞普兰", "citalopram", "艾司西酞普兰", "escitalopram", "氟伏沙明", "fluvoxamine",
        ),
        "ppi" to listOf(
            "ppi", "质子泵抑制剂", "奥美拉唑", "omeprazole", "泮托拉唑", "pantoprazole",
            "兰索拉唑", "lansoprazole", "雷贝拉唑", "rabeprazole", "艾司奥美拉唑", "esomeprazole",
        ),
        "antihypertensive" to listOf(
            "antihypertensive", "降压药",
            "依那普利", "enalapril", "缬沙坦", "valsartan", "氯沙坦", "losartan",
            "厄贝沙坦", "irbesartan", "氨氯地平", "amlodipine", "美托洛尔", "metoprolol",
            "比索洛尔", "bisoprolol", "培哚普利", "perindopril",
        ),
        // ---- 家族键：药单上的 medClass 与 DrugKeyCatalog 的类别条目都落到这里 ----
        //
        // ⚠️ 家族键的成员清单必须与「具体药」的登记**一致**：具体药漏登记所属家族，
        // 那个家族下的条目就会对该药静默失效（itx-003~004/008/009/011/013/014 都挂在 `nsaid` 上）。
        "nsaid" to listOf(
            "nsaid", "nsaids", "非甾体抗炎药", "消炎镇痛", "消炎镇痛类",
            "naproxen", "ibuprofen", "diclofenac", "celecoxib", "etoricoxib",
            "meloxicam", "indomethacin", "acetaminophen",
        ),
        // 激素这一条**例外**：它是 ACR 原文里的类目名，除目录里的三种外还有别的糖皮质激素
        // （布地奈德 / 可的松…）。故激素类药物不但容不下 TNF 的具体药——那会让「泼尼松」
        // 的展开变成「五种 TNF 抑制剂」，进而让激素用户收到生物制剂警告。
        // ⚠️ 这条不是可选的：itx-002 / itx-010 的 `drug_a` 是 `adalimumab` / `tnf_inhibitor`，
        // 只要 `glucocorticoid` 的成员里混进 TNF 药，激素用户就会收到生物制剂黑框警告。
        "glucocorticoid" to listOf(
            "glucocorticoid", "steroid", "激素", "激素类", "糖皮质激素",
            "prednisone", "methylprednisolone", "dexamethasone",
        ),
        // 传统 DMARD：**只登记类目词，不登记具体药**——理由见类文档。
        // 本条目按治疗线描述，而种子的传统 DMARD 条目全部挂在具体药上；
        // 若在这里登记 methotrexate / sulfasalazine / leflunomide / iguratimod，
        // 「同属传统 DMARD」就会变成「互相命中对方的条目」，来氟米特又会收到甲氨蝶呤的致死警告。
        "csdmard" to listOf("csdmard", "dmard", "传统dmard", "传统 dmard"),
        "jak" to listOf("jak", "jak抑制剂", "jak抑制剂类", "tofacitinib", "upadacitinib", "baricitinib"),
        // `tnf_inhibitor`：itx-010 的 drug_a，也是 itx-002 的去品牌化归属目标。
        "tnf_inhibitor" to listOf(
            "tnf", "tnf抑制剂", "tnf_inhibitor",
            "adalimumab", "etanercept", "infliximab", "golimumab", "certolizumab",
        ),
        // IL-17 / IL-23 类：用于把「生物制剂」这个类别词展开到具体药，
        // 让 TNF 条目**不**命中 IL-17 药（种子至今没有 IL-17 专属条目，见审查报告 §4①）。
        "il17_inhibitor" to listOf("il17", "il-17", "il17抑制剂", "il-17抑制剂", "secukinumab", "ixekizumab"),
        "il23_inhibitor" to listOf("il23", "il-23", "il12_23", "il-12/23", "ustekinumab", "guselkumab", "risankizumab"),
        // 生物制剂（含 JAK 抑制剂这一「靶向治疗」家族）。
        // 为什么把三支 JAK 抑制剂登记进来：种子的生物制剂级条目（itx-002 严重感染/结核黑框、
        // itx-010 活疫苗接种窗口）挂在**具体品牌**上，而 JAK 抑制剂在旧实现下命中 0 条
        // （审查报告 §2.3①，本批次已用 `MedicationInteractionChainTest` 复现）。
        // 同属靶向治疗家族却一条提示都没有，比「多给一条家族级提示」危险得多。
        // ⚠️ 这只是**覆盖取舍**，不等于 JAK 与 TNF 可互换：它x-002 的用药人群是否要按家族重写，
        // 属医学内容，交维护者裁决（批次 18 回报的第三阶段提案表）。
        "biologic" to listOf(
            "biologic", "生物制剂", "生物类", "bmdard",
            "adalimumab", "etanercept", "infliximab", "golimumab", "certolizumab",
            "secukinumab", "ixekizumab", "ustekinumab", "guselkumab", "risankizumab",
            "jak", "tofacitinib", "upadacitinib", "baricitinib",
        ),
        // ---- 条目专用限定键（`drug_a` 里出现，但语义是「本药 + 某种使用强度」）----
        // 这些键**不覆盖家族键**，只列具体药：家族键 `nsaid` / `glucocorticoid` 本身已经涵盖具体药，
        // 若在这里再写一遍家族键，语义上没有任何增益，反而让「限定键 = 家族键」这层意图变得含糊。
        //
        // `glucocorticoid_high_dose`（itx-012）：ACR 2022 表 4 的分层阈值是
        // **≥20 mg/天泼尼松等效**（本批次已核原文）。实现层仍是「激素在药单上即命中」
        // ——阈值判定需要药单剂量解析与替代激素换算，属独立议题（列入「未做」清单）。
        // 刻意从宽：宁可多提示一条，也不要因为算不出等效剂量而漏掉。
        "glucocorticoid_high_dose" to listOf("prednisone", "methylprednisolone", "dexamethasone"),
        // `nsaid_longterm`（itx-014）：条目语义是「长期 / 高危 NSAID 使用」，同样是强度限定词。
        "nsaid_longterm" to listOf(
            "naproxen", "ibuprofen", "diclofenac", "celecoxib",
            "etoricoxib", "meloxicam", "indomethacin", "acetaminophen",
        ),
        // `anti_inflammatory_multi`（itx-009）：条目讲「**同时**使用 3–4 种抗炎药」，
        // 没有「另一个具体的药」，只要求药单上的抗炎药 ≥2 种——它是**数量条件**，
        // 故不进 [matches] 的「双药并存」分支，由 [antiInflammatoryCount] 处理。
        MULTI_ANTI_INFLAMMATORY to listOf(
            MULTI_ANTI_INFLAMMATORY, "多药叠加", "多种抗炎药", "多种抗炎镇痛药",
        ),
    )

    /**
     * 词 → 登记了它的键（父键），按 [vocabulary] 的声明顺序。
     *
     * 这是「家族归属」的唯一事实来源：键**只向上一层**展开，不再传递。
     * 早期版本沿用双向闭包（把家族键的成员再展开一遍），于是「来氟米特」展开出 `methotrexate`、
     * 「艾拉莫德」展开出 `sulfasalazine`——闭包把「同属一类」当成了「同一支药」。
     */
    private val parents: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for ((key, tokens) in vocabulary) {
            for (raw in tokens) {
                val t = raw.trim().lowercase()
                if (t.isEmpty() || t == key) continue
                val list = getOrPut(t) { mutableListOf() }
                if (key !in list) list.add(key)
            }
        }
    }

    /**
     * 那些**不对应一支具体的药**的键（类目 / 家族 / 限定词）。
     *
     * 只在 [antiInflammatoryCount]（数「药单上有几支不同的抗炎药」）里用：
     * 一支萘普生会贡献 nameKey / 药名 / medClass 三个 token，若按 token 数，
     * `naproxen` / `萘普生` / `NSAID` 会被数成三支药。类别键必须从计数里剔掉。
     */
    private val categoryKeys: Set<String> = setOf(
        "nsaid", "glucocorticoid", "csdmard", "jak", "biologic",
        "tnf_inhibitor", "il17_inhibitor", "il23_inhibitor",
        "glucocorticoid_high_dose", "nsaid_longterm", MULTI_ANTI_INFLAMMATORY,
        "antihypertensive", "ssri", "ppi",
    )

    /** 语境键：种子里会被写成 `drug_b`、但判定入口不在 `medications` 表的那些（见 [LIVE_VACCINE]）。 */
    private val contextKeys = setOf(LIVE_VACCINE, "food_calcium")

    /**
     * `food_calcium` 在药单侧对应哪些词。
     *
     * 种子的 itx-015 是「阿仑膦酸盐与钙 / 食物的服用间隔」，`drug_b = food_calcium`；
     * 而钙剂 / 维生素 D3 在 App 里记在**补剂档案**（`supplements`），药名是自由文本
     * （「碳酸钙」「钙尔奇」「维生素 D3」…），补剂的 `category` 也参与 token。
     */
    private val foodCalciumTokens = setOf(
        "calcium", "钙", "钙剂", "碳酸钙", "迪巧", "钙尔奇",
        "colecalciferol", "维生素 d3", "维生素d3", "维生素 d", "维生素d", "vitamin_d3", "维d", "vd",
        "calcitriol", "骨化三醇", "罗盖全",
    )

    /**
     * 该键是否已被词汇表登记。
     *
     * 存在的理由只有一个：**种子新增一条 `drug_a` 而词汇表没跟上时，该条目会对所有药单静默失效**
     * ——没有任何报错，只是永远不出现。回归测试拿它逐条扫种子的 `drug_a`/`drug_b`
     * （见 `MedicationInteractionChainTest`「种子里的每个 drug_a 与 drug_b 都被词汇表覆盖」），
     * 把这种静默失效变成一条会红的断言。
     */
    fun isKnownKey(key: String): Boolean {
        val k = key.trim().lowercase()
        return vocabulary.containsKey(k) || k in contextKeys
    }

    /**
     * 一个「键」涵盖哪些词（正查）——用于把条目声明的键翻译成它管的那些词。
     *
     * `food_calcium` 走 [foodCalciumTokens]（补剂侧的写法），其余未登记的键**回落为它自己**：
     * 既不静默漏掉，也不会因为回落而把别的药卷进来。
     */
    private fun coveredTokens(key: String): Set<String> =
        when (key) {
            "food_calcium" -> foodCalciumTokens
            else -> vocabulary[key]?.toSet() ?: setOf(key)
        }

    /**
     * 药单上的某一个词直接登记在哪些键下（反查，**不向家族展开**）。
     *
     * **大小写不敏感**（旧 LIKE 对 ASCII 亦然）；同时按空白 / 全角空格切分，
     * 因为 `DrugKeyCatalog` 的显示名里出现过空格（如「维生素 D3」），
     * 而 `nameKey` 只要求是小写、未规定不能带空格。
     */
    private fun keysOf(token: String): Set<String> {
        val t = token.trim().lowercase()
        if (t.isEmpty()) return emptySet()
        val out = linkedSetOf<String>()
        if (t in vocabulary) out += t
        out += parents[t].orEmpty()
        // 「维生素 D3」这种带空格的写法：先试**去掉空格**的整体（词汇表里登记的是 `维生素d3`），
        // 再退一步按空格切分后的每个片段各试一次。两条都要求片段长度 ≥3，
        // 避免让 "d3" / "vd" 这种短片段去撞别的键（那是子串匹配的老毛病，不能从后门放进来）。
        if (out.isEmpty() && t.contains(' ')) {
            out += keysOf(t.replace(" ", ""))
            for (piece in t.split(' ')) {
                if (piece.length >= 3) out += keysOf(piece)
            }
        }
        return out
    }

    /**
     * 药单上的一个词归一到哪些键：**该词直接命中的键 + 这些键所属的家族键**。
     *
     * 例：`"adalimumab"` → {adalimumab, tnf_inhibitor, biologic}（顺序即声明顺序）；
     * `"依那西普"` → {etanercept, tnf_inhibitor, biologic}（中文名与英文名收敛到同一组键）；
     * `"爱若华"` → {leflunomide}（**不会**带出 methotrexate——`csdmard` 不登记成员）；
     * `"未收录的词"` → 空集，不兜底。
     *
     * @param token 药单 / 补剂贡献的一个词
     */
    fun drugOf(token: String): Set<String> {
        val direct = keysOf(token)
        if (direct.isEmpty()) return direct
        val out = linkedSetOf<String>()
        out += direct
        for (k in direct) out += parents[k].orEmpty()
        return out
    }

    /** 一个键归一后落在**哪些键的空间**里——即「这条条目关心哪些键」的规范化形式。 */
    private fun keySpace(key: String): Set<String> {
        val out = linkedSetOf<String>()
        for (t in coveredTokens(key)) out += drugOf(t)
        return out
    }

    /**
     * 判断「患者药单上是否有东西对得上条目声明的键」。
     *
     * [tokensOf] 是**药单上全部药品 + 补剂**的 token 生成器（每个药贡献 nameKey / 药名 / 商品名 / medClass）。
     * 任一 token 归一后的键与该键的键空间相交即命中。
     *
     * ⚠️ 这是**「存在性」判定**（某个键在药单上有对应物），不是「两药并存」判定——后者见 [matches]。
     */
    fun active(key: String, tokensOf: List<String>): Boolean {
        val want = keySpace(key)
        if (want.isEmpty()) return false
        for (t in tokensOf) {
            if (drugOf(t).any { it in want }) return true
        }
        return false
    }

    /** 药单上**有几支不同的抗炎药**（itx-009 的数量条件）。 */
    private fun antiInflammatoryCount(medicationTokens: List<String>): Int {
        val nsaidSpace = keySpace("nsaid")
        return medicationTokens
            .flatMap { drugOf(it) }
            .filter { it in nsaidSpace && it !in categoryKeys }
            .distinct()
            .size
    }

    /** 条目声明的 `drug_a` / `drug_b`（种子 payload 里的两个字段，只做归一化，不改语义）。 */
    data class Declared(val drugA: String?, val drugB: String?)

    /**
     * v1.1.2（批次 18）：**甲氨蝶呤的频次只能是每周一次**（itx-001 的行为承诺，第四份审查报告 §一）。
     *
     * 种子 itx-001 的 `action` 白纸黑字写着「本 APP 设置甲氨蝶呤时频次只能选 weekly，
     * 选其他值弹出本条目强提示」，而 `edu-th-001` 同族条目的引用是瑞士治疗产品局
     * 18 例误将甲氨蝶呤**每日**使用的中毒通报（其中 4 例死亡）。
     * 此前代码里**没有任何一处**兑现它：`MedEditSections` 的频次 chips 对所有药无条件可选，
     * step 1 校验只查名称 / 剂量 / 锚点日期 / BIW 两日重复——新增能存、**编辑改成每日同样能存**。
     *
     * 判据走 [drugOf] 而不是 `nameKey == "methotrexate"` 字面比较：手输「甲氨蝶呤」「甲氨喋呤」
     * 「mtx」以及商品名都要落在同一条规则上，否则「换个写法就绕过去了」是这类校验最常见的失效方式。
     */
    fun requiresWeeklyFrequency(nameKey: String): Boolean = drugOf(nameKey).contains("methotrexate")

    /**
     * 把一条 interaction 条目**按自身的 payload 分类**。
     *
     * 分类规则（不是医学判断，是**字段语义**的读法）：把 `drug_a` / `drug_b` 归一为小写，
     * 空串 / `null` 字面量一律当作「没有这一侧」。
     */
    fun classify(drugA: String?, drugB: String?): Declared = Declared(
        drugA = drugA?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "null" },
        drugB = drugB?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "null" },
    )

    /**
     * 本批次唯一入口的**纯函数**：给定条目声明的两侧 + 药单 token，判定该条目是否适用。
     *
     * @param declared 条目的 `drug_a` / `drug_b`
     * @param medicationTokens 药单上**全部在用药品 + 在用补剂**贡献的 token（含正在新增 / 编辑的那一支）
     */
    fun matches(declared: Declared, medicationTokens: List<String>): Boolean {
        val a = declared.drugA ?: return false
        if (!active(a, medicationTokens)) return false

        val b = declared.drugB ?: return true // 单药条目：A 命中即适用

        // 语境键（活疫苗 / 钙与食物）：不是药单上的药品，不作适用性前提。
        // 理由见 [LIVE_VACCINE]——患者还没录疫苗记录就不显示，等于把用药规则藏起来。
        if (b in contextKeys) return true

        // 多药叠加（itx-009）：要求药单上**存在 ≥2 种不同的抗炎药**，不是「随便一支 NSAID」。
        if (b == MULTI_ANTI_INFLAMMATORY) return antiInflammatoryCount(medicationTokens) >= 2

        // 其余都是药品–药品的组合：B 侧必须真的也在药单上。
        // 旧的单侧命中会把「MTX + NSAIDs 合用需监测」错报成「吃 MTX 就有这条风险」。
        return active(b, medicationTokens)
    }
}
