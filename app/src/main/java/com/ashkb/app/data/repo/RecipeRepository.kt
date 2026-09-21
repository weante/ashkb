package com.ashkb.app.data.repo

import android.content.Context
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

    /** 视图模型：已解析的标签与出处编号。 */
    data class RecipeView(
        val recipe: Recipe,
        val tags: List<String>,
        val sources: List<String>,
    )

    fun observeAll(): Flow<List<RecipeView>> = dao.observeAll().map { list -> list.map { it.toView() } }

    suspend fun listAll(): List<RecipeView> = withContext(Dispatchers.IO) { dao.listAll().map { it.toView() } }

    suspend fun byId(id: String): RecipeView? = withContext(Dispatchers.IO) { dao.byId(id)?.toView() }

    /** 幂等种入种子食谱。@return 本次新增条数 */
    suspend fun seedIfMissing(): Int = withContext(Dispatchers.IO) {
        val existing = dao.seedIds().toSet()
        val pending = RecipeSeeds.pending(existing)
        if (pending.isEmpty()) return@withContext 0
        val now = nowIso()
        pending.forEach { s ->
            dao.upsert(
                Recipe(
                    id = s.id,
                    title = s.title,
                    tags = JSONArray(s.tags).toString(),
                    ingredients = s.ingredients,
                    steps = s.steps,
                    sources = JSONArray(s.sources).toString(),
                    isFavorite = false,
                    isSeed = true,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        pending.size
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
