package com.ashkb.app.domain

/**
 * B3（v1.0.39）：推荐食谱**种子内容**（10 条）。
 *
 * 内容口径（与用户逐条核对后定稿）：
 *  - 只写「食材 + 做法」，**不做任何疗效宣称**；每条都带出处编号（见 [RecipeSources]）
 *  - 标签：抗炎 / 胃肠友好 / 控热量；第 6 条按用户要求**弱化**为「减少精制淀粉」，
 *    不写成「低淀粉疗法」（S2 明确指出 AS 膳食证据极为有限且不确定）
 *  - 幂等：种子 id 固定（`rec-s01`…），已存在即跳过，用户自建食谱不受影响
 */
object RecipeSeeds {

    const val TAG_ANTI = "anti_inflammatory"
    const val TAG_GUT = "gut_friendly"
    const val TAG_CALORIE = "calorie_control"

    data class Seed(
        val id: String,
        val title: String,
        val tags: List<String>,
        val ingredients: String,
        val steps: String,
        val sources: List<String>,
    )

    val ALL: List<Seed> = listOf(
        Seed(
            id = "rec-s01",
            title = "橄榄油烤三文鱼配西兰花",
            tags = listOf(TAG_ANTI),
            ingredients = "三文鱼排 150g\n西兰花 200g\n特级初榨橄榄油 1 汤匙\n蒜末、柠檬汁、黑胡椒 适量",
            steps = "三文鱼抹橄榄油、蒜末与黑胡椒，静置 10 分钟\n西兰花切小朵，沸水焯 1 分钟后捞出\n烤箱 200℃ 预热，鱼与西兰花同盘烤 12–15 分钟\n出锅淋柠檬汁即可",
            sources = listOf(RecipeSources.S1, RecipeSources.S4, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s02",
            title = "地中海杂蔬鹰嘴豆沙拉",
            tags = listOf(TAG_ANTI, TAG_GUT),
            ingredients = "熟鹰嘴豆 150g\n番茄 1 个\n黄瓜 半根\n彩椒 半个\n紫洋葱 少许\n橄榄油 1 汤匙\n柠檬汁、欧芹、黑胡椒 适量",
            steps = "蔬菜洗净切丁\n与鹰嘴豆拌匀\n淋橄榄油与柠檬汁，撒欧芹与黑胡椒\n冷藏 10 分钟风味更佳",
            sources = listOf(RecipeSources.S1, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s03",
            title = "姜黄姜末藜麦饭",
            tags = listOf(TAG_ANTI),
            ingredients = "藜麦 80g\n姜末 1 小勺\n姜黄粉 1/4 小勺\n橄榄油 1 小勺\n黑胡椒、葱花 适量",
            steps = "藜麦淘洗后按 1:2 加水煮 15 分钟\n另起锅用橄榄油炒香姜末\n拌入藜麦与姜黄粉翻匀\n撒黑胡椒与葱花（黑胡椒有助姜黄素吸收）",
            sources = listOf(RecipeSources.S3, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s04",
            title = "核桃亚麻籽酸奶杯",
            tags = listOf(TAG_ANTI),
            ingredients = "无糖酸奶 150g\n核桃碎 15g\n亚麻籽粉 1 小勺\n蓝莓或当季水果 50g",
            steps = "酸奶盛入杯中\n撒核桃碎与亚麻籽粉\n铺上水果即可",
            sources = listOf(RecipeSources.S7, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s05",
            title = "番茄橄榄油炖白豆",
            tags = listOf(TAG_ANTI, TAG_GUT),
            ingredients = "熟白豆 200g\n番茄 2 个\n洋葱 半个\n蒜 2 瓣\n橄榄油 1 汤匙\n罗勒或欧芹、少盐 适量",
            steps = "洋葱与蒜末用橄榄油炒软\n加番茄丁炒出汁\n放入白豆小火炖 10 分钟\n撒香草、少盐调味",
            sources = listOf(RecipeSources.S1, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s06",
            title = "蔬菜鸡蛋饼（减少精制淀粉）",
            tags = listOf(TAG_GUT),
            ingredients = "鸡蛋 2 个\n西葫芦丝、胡萝卜丝 各 50g\n全麦粉 1 汤匙\n橄榄油 少许",
            steps = "蔬菜擦丝后略挤去水分\n与蛋液、全麦粉拌匀\n平底锅少油小火，两面煎熟\n切块食用",
            sources = listOf(RecipeSources.S2, RecipeSources.S5, RecipeSources.S6),
        ),
        Seed(
            id = "rec-s07",
            title = "燕麦南瓜粥",
            tags = listOf(TAG_GUT),
            ingredients = "燕麦片 40g\n南瓜 150g\n水或低脂奶 300ml\n肉桂粉 少许",
            steps = "南瓜蒸熟压成泥\n燕麦加水煮 5 分钟\n拌入南瓜泥再煮 2 分钟\n撒少许肉桂粉",
            sources = listOf(RecipeSources.S4, RecipeSources.S5),
        ),
        Seed(
            id = "rec-s08",
            title = "清蒸鳕鱼配胡萝卜泥",
            tags = listOf(TAG_GUT),
            ingredients = "鳕鱼 150g\n胡萝卜 150g\n姜片、葱段 适量\n橄榄油 1 小勺",
            steps = "鳕鱼铺姜片葱段，大火蒸 8 分钟\n胡萝卜蒸熟压成泥\n鱼淋少许橄榄油与蒸出的汤汁\n配胡萝卜泥同食",
            sources = listOf(RecipeSources.S1, RecipeSources.S5),
        ),
        Seed(
            id = "rec-s09",
            title = "鸡胸时蔬大拌菜",
            tags = listOf(TAG_CALORIE),
            ingredients = "鸡胸肉 120g\n生菜、番茄、黄瓜、紫甘蓝 共 250g\n橄榄油 1 小勺\n柠檬汁、黑胡椒 适量",
            steps = "鸡胸水煮或空气炸至熟，切片\n蔬菜洗净撕成适口大小\n混合后淋橄榄油与柠檬汁\n撒黑胡椒拌匀",
            sources = listOf(RecipeSources.S1, RecipeSources.S8),
        ),
        Seed(
            id = "rec-s10",
            title = "杂豆蔬菜汤",
            tags = listOf(TAG_CALORIE, TAG_GUT),
            ingredients = "混合豆类（红豆 / 鹰嘴豆 / 扁豆）80g\n番茄 1 个\n洋葱、芹菜、胡萝卜 各 50g\n香草、黑胡椒、少盐 适量",
            steps = "豆类提前泡发\n蔬菜切丁下锅炒香\n加水与豆类，小火煮约 30 分钟\n少盐调味、撒香草",
            sources = listOf(RecipeSources.S1, RecipeSources.S8),
        ),
    )

    /** 尚未种入的条目（按 id 幂等去重）。 */
    fun pending(existingIds: Set<String>): List<Seed> = ALL.filter { it.id !in existingIds }

    /** 标签 → 中文（界面展示用）。 */
    fun tagLabel(tag: String): String = when (tag) {
        TAG_ANTI -> "抗炎"
        TAG_GUT -> "胃肠友好"
        TAG_CALORIE -> "控热量"
        else -> tag
    }

    val ALL_TAGS: List<String> = listOf(TAG_ANTI, TAG_GUT, TAG_CALORIE)
}
