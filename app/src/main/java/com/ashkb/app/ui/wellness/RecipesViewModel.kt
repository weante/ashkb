package com.ashkb.app.ui.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.repo.RecipeRepository
import com.ashkb.app.data.repo.RecipeRepository.RecipeView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * B3（v1.0.39）：推荐食谱库 ViewModel。
 *
 * 仓储已经把 JSON（tags / sources）解析成 `List<String>`，并按 `is_favorite DESC` 排序——
 * 所以这里只做三件事：暴露列表流、持有标签筛选态、转发写操作（收藏 / 保存 / 删除）。
 * 写库后由 `observeAll()` 回流刷新，界面不维护本地副本（避免收藏态与库不一致）。
 */
class RecipesViewModel(private val repo: RecipeRepository) : ViewModel() {

    /** 全部食谱（已解析标签与出处；收藏置顶由仓储 SQL 保证） */
    val recipes: StateFlow<List<RecipeView>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filterTag = MutableStateFlow<String?>(null)

    /** 当前筛选标签：null = 全部（`R.string.recipes_filter_all`） */
    val filterTag: StateFlow<String?> = _filterTag

    /** 设置筛选标签；传 null 回到「全部」。筛选只影响展示，不改库。 */
    fun setFilter(tag: String?) {
        _filterTag.value = tag
    }

    /** 收藏 / 取消收藏（种子与自建食谱通用） */
    fun toggleFavorite(view: RecipeView) {
        viewModelScope.launch { repo.setFavorite(view.recipe.id, !view.recipe.isFavorite) }
    }

    /**
     * 新增（`id = null`）或更新既有食谱。
     * 编辑种子食谱时，出处与 `isSeed` 由仓储保留（用户改口味不至于丢掉证据来源）。
     */
    fun save(
        id: String?,
        title: String,
        tags: List<String>,
        ingredients: String,
        steps: String,
        notes: String?,
    ) {
        viewModelScope.launch { repo.save(id, title, tags, ingredients, steps, notes) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                RecipesViewModel(app.recipeRepository)
            }
        }
    }
}
