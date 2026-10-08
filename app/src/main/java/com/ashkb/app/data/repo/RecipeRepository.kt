package com.ashkb.app.data.repo

import android.content.Context
import androidx.compose.runtime.Immutable
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.Recipe
import com.ashkb.app.domain.RecipeSeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * B3（v1.0.39）：推荐食谱库仓储。
 *
 * 标签 / 出处以 JSON 数组存库（与 riskTags / injSites 同惯例），**在仓储层解析**成
 * `List<String>` 后交给 UI——界面不碰 JSON。种子幂等：按固定 id（`rec-s01`…）判重。
 */
class RecipeRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.recipeDao()

    /**
     * 视图模型：已解析的标签与出处编号。
     *
     * v1.0.84（批次 9）：加 `@Immutable` 让食谱卡片在列表里可 skip。该断言**为真**：
     * [Recipe] 是全部字段 `val` 的 Room 实体（String / String? / Boolean），本类的两个
     * `List<String>` 由 [parseArray] 每次新建、构建后全项目只读（表单改标签走 `list + tag`
     * 生成新列表，不就地改）。Compose 无法自行证明 `List` 接口不可变，故需开发者断言。
     */
    @Immutable
    data class RecipeView(
        val recipe: Recipe,
        val tags: List<String>,
        val sources: List<String>,
    )

    fun observeAll(): Flow<List<RecipeView>> = dao.observeAll().map { list -> list.map { it.toView() } }

    suspend fun listAll(): List<RecipeView> = withContext(Dispatchers.IO) { dao.listAll().map { it.toView() } }

    suspend fun byId(id: String): RecipeView? = withContext(Dispatchers.IO) { dao.byId(id)?.toView() }

    /**
     * 幂等种入种子食谱，并把**未被用户编辑过**的种子行刷新到当前语言。
     *
     * 为什么需要刷新：种子文案是 `@StringRes`，但 `recipes.title` / `ingredients` / `steps`
     * 存的是**文本**（用户可编辑、要能导出备份），所以种入那一刻就把语言定死了。
     * 中文装机的用户切到英文后，若不刷新就永远看到中文食谱。
     *
     * 判定「没被编辑过」用的是**内容比对**而不是时间戳：`updated_at` 会被收藏之类的
     * 非内容操作顶掉，而「三个字段仍与某一已知语言的种子逐字相同」只可能意味着没编辑过。
     * 只要用户动过其中任何一个字段，就不再匹配，刷新会跳过它。
     *
     * @return 本次新增条数（刷新不算新增）
     */
    suspend fun seedIfMissing(): Int = withContext(Dispatchers.IO) {
        val rows = dao.listAll()
        val seedIds = rows.filter { it.isSeed }.map { it.id }.toSet()
        val pending = RecipeSeeds.pending(seedIds)
        val now = nowIso()

        pending.forEach { s ->
            dao.upsert(
                Recipe(
                    id = s.id,
                    title = context.getString(s.titleRes),
                    tags = JSONArray(s.tags).toString(),
                    ingredients = context.getString(s.ingredientsRes),
                    steps = context.getString(s.stepsRes),
                    sources = JSONArray(s.sources).toString(),
                    isFavorite = false,
                    isSeed = true,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        refreshSeedLanguage(rows.filter { it.isSeed }, now)

        pending.size
    }

    /** 把仍是「原封不动的种子」的行改写成当前语言；用户编辑过的原样保留。 */
    private suspend fun refreshSeedLanguage(seedRows: List<Recipe>, now: String) {
        val byId = seedRows.associateBy { it.id }
        RecipeSeeds.ALL.forEach { s ->
            val row = byId[s.id] ?: return@forEach
            val title = context.getString(s.titleRes)
            val ingredients = context.getString(s.ingredientsRes)
            val steps = context.getString(s.stepsRes)
            if (title == row.title && ingredients == row.ingredients && steps == row.steps) {
                return@forEach // 已经是当前语言
            }
            if (!isUntouchedSeed(s, row)) return@forEach // 用户改过，不碰
            dao.upsert(
                row.copy(
                    title = title,
                    ingredients = ingredients,
                    steps = steps,
                    updatedAt = now,
                )
            )
        }
    }

    /**
     * 该行是否仍是「原封不动的种子」：三个内容字段与**某一已知语言**的种子逐字相同。
     *
     * 必须比对所有已知语言，而不是只比当前语言——库里那行是**种入当时**的语言写的。
     */
    private fun isUntouchedSeed(s: RecipeSeeds.Seed, row: Recipe): Boolean =
        SeedLocales.ALL.any { locale ->
            val c = SeedLocales.contextIn(context, locale)
            c.getString(s.titleRes) == row.title &&
                c.getString(s.ingredientsRes) == row.ingredients &&
                c.getString(s.stepsRes) == row.steps
        }

    /**
     * 新增或更新**自建**食谱。
     * 更新种子食谱时保留其 `sources` / `isSeed`（种子出处不因用户编辑而丢）。
     */
    suspend fun save(
        id: String?,
        title: String,
        tags: List<String>,
        ingredients: String,
        steps: String,
        notes: String?,
    ) = withContext(Dispatchers.IO) {
        val now = nowIso()
        val existing = id?.let { dao.byId(it) }
        dao.upsert(
            Recipe(
                id = existing?.id ?: Ids.new("rec"),
                title = title.trim(),
                tags = JSONArray(tags).toString(),
                ingredients = ingredients.trim(),
                steps = steps.trim(),
                sources = existing?.sources,
                isFavorite = existing?.isFavorite ?: false,
                isSeed = existing?.isSeed ?: false,
                notes = notes?.trim()?.ifBlank { null },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    suspend fun setFavorite(id: String, favorite: Boolean) =
        withContext(Dispatchers.IO) { dao.setFavorite(id, favorite, nowIso()) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    private fun Recipe.toView() = RecipeView(this, parseArray(tags), parseArray(sources))

    private fun parseArray(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
        }.getOrDefault(emptyList())
    }
}
