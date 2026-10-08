package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * B3（v1.0.39）：推荐食谱**种子内容**（10 条）。
 *
 * 内容口径（与用户逐条核对后定稿）：
 *  - 只写「食材 + 做法」，**不做任何疗效宣称**；每条都带出处编号（见 [RecipeSources]）
 *  - 标签：抗炎 / 胃肠友好 / 控热量；第 6 条按用户要求**弱化**为「减少精制淀粉」，
 *    不写成「低淀粉疗法」（R2 明确指出 AS 膳食证据极为有限且不确定）
 *  - 幂等：种子 id 固定（`rec-s01`…），已存在即跳过，用户自建食谱不受影响
 *
 * i18n（v1.2.6）：标题 / 配料 / 做法改为 `@StringRes`，**由仓储在种入时按当前语言取词**
 * 再写进数据库——`Recipe.title` 等列存的是文本，不是资源 id（用户可编辑，且要能导出备份）。
 * 因此改语言后已种下的行仍是旧语言，重种逻辑见 `RecipeRepository.seedIfMissing`。
 * `id` / `tags` / `sources` 是**匹配键与编号**，与语言无关，一律不动。
 */
object RecipeSeeds {

    const val TAG_ANTI = "anti_inflammatory"
    const val TAG_GUT = "gut_friendly"
    const val TAG_CALORIE = "calorie_control"

    data class Seed(
        val id: String,
        @StringRes val titleRes: Int,
        val tags: List<String>,
        @StringRes val ingredientsRes: Int,
        @StringRes val stepsRes: Int,
        val sources: List<String>,
    )

    val ALL: List<Seed> = listOf(
        Seed(
            id = "rec-s01",
            titleRes = R.string.recipe_seed_s01_title,
            tags = listOf(TAG_ANTI),
            ingredientsRes = R.string.recipe_seed_s01_ingredients,
            stepsRes = R.string.recipe_seed_s01_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R4, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s02",
            titleRes = R.string.recipe_seed_s02_title,
            tags = listOf(TAG_ANTI, TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s02_ingredients,
            stepsRes = R.string.recipe_seed_s02_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s03",
            titleRes = R.string.recipe_seed_s03_title,
            tags = listOf(TAG_ANTI),
            ingredientsRes = R.string.recipe_seed_s03_ingredients,
            stepsRes = R.string.recipe_seed_s03_steps,
            sources = listOf(RecipeSources.R3, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s04",
            titleRes = R.string.recipe_seed_s04_title,
            tags = listOf(TAG_ANTI),
            ingredientsRes = R.string.recipe_seed_s04_ingredients,
            stepsRes = R.string.recipe_seed_s04_steps,
            sources = listOf(RecipeSources.R7, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s05",
            titleRes = R.string.recipe_seed_s05_title,
            tags = listOf(TAG_ANTI, TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s05_ingredients,
            stepsRes = R.string.recipe_seed_s05_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s06",
            titleRes = R.string.recipe_seed_s06_title,
            tags = listOf(TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s06_ingredients,
            stepsRes = R.string.recipe_seed_s06_steps,
            sources = listOf(RecipeSources.R2, RecipeSources.R5, RecipeSources.R6),
        ),
        Seed(
            id = "rec-s07",
            titleRes = R.string.recipe_seed_s07_title,
            tags = listOf(TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s07_ingredients,
            stepsRes = R.string.recipe_seed_s07_steps,
            sources = listOf(RecipeSources.R4, RecipeSources.R5),
        ),
        Seed(
            id = "rec-s08",
            titleRes = R.string.recipe_seed_s08_title,
            tags = listOf(TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s08_ingredients,
            stepsRes = R.string.recipe_seed_s08_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R5),
        ),
        Seed(
            id = "rec-s09",
            titleRes = R.string.recipe_seed_s09_title,
            tags = listOf(TAG_CALORIE),
            ingredientsRes = R.string.recipe_seed_s09_ingredients,
            stepsRes = R.string.recipe_seed_s09_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R8),
        ),
        Seed(
            id = "rec-s10",
            titleRes = R.string.recipe_seed_s10_title,
            tags = listOf(TAG_CALORIE, TAG_GUT),
            ingredientsRes = R.string.recipe_seed_s10_ingredients,
            stepsRes = R.string.recipe_seed_s10_steps,
            sources = listOf(RecipeSources.R1, RecipeSources.R8),
        ),
    )

    /** 尚未种入的条目（按 id 幂等去重）。 */
    fun pending(existingIds: Set<String>): List<Seed> = ALL.filter { it.id !in existingIds }

    /**
     * 标签 → 资源 id（界面展示用）。
     *
     * 未知标签返回 `null` 而不是「返回 tag 本身」：调用方据此回退成原始 tag，
     * 与改版前 `else -> tag` 的行为逐字一致。
     */
    @StringRes
    fun tagLabelRes(tag: String): Int? = when (tag) {
        TAG_ANTI -> R.string.recipe_tag_anti
        TAG_GUT -> R.string.recipe_tag_gut
        TAG_CALORIE -> R.string.recipe_tag_calorie
        else -> null
    }

    val ALL_TAGS: List<String> = listOf(TAG_ANTI, TAG_GUT, TAG_CALORIE)
}
