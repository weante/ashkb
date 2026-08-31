package com.ashkb.app.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.HealthRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** K 模块分类（与种子五类对应） */
val KB_CATEGORIES = listOf(
    null to "全部",
    "interaction" to "相互作用",
    "food_drug" to "食物药物",
    "exercise" to "运动",
    "emergency" to "应急",
    "edu" to "教育",
)

data class KnowledgeUiState(
    val entries: List<KbEntry> = emptyList(),
    val category: String? = null,
    val query: String = "",
    val overdueCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeViewModel(private val repo: HealthRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val today = LocalDate.now().toString()

    private val entries: kotlinx.coroutines.flow.Flow<List<KbEntry>> =
        combine(query, category) { q, c -> q to c }
            .flatMapLatest { (q, c) ->
                when {
                    q.isNotBlank() -> repo.searchKb(q.trim())
                    c != null -> repo.observeKbByCategory(c)
                    else -> repo.observeKbAll()
                }
            }

    /** 复核到期条目数（今日已过 review_due） */
    private val overdue: kotlinx.coroutines.flow.Flow<Int> =
        repo.observeKbAll().combine(flowOf(Unit)) { list, _ ->
            list.count { it.reviewDue < today }
        }

    val uiState: StateFlow<KnowledgeUiState> =
        combine(entries, query, category, overdue) { list, q, c, od ->
            KnowledgeUiState(
                entries = if (c != null && q.isBlank()) list.filter { it.category == c } else list,
                category = c, query = q, overdueCount = od,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KnowledgeUiState())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(c: String?) { category.value = c }

    fun refreshReviewCheck() {
        viewModelScope.launch { repo.checkReviewDue(today) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                KnowledgeViewModel(app.healthRepository)
            }
        }
    }
}
