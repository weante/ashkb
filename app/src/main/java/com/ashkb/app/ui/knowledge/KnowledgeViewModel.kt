package com.ashkb.app.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.Lifestyle
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** K 模块分类（与种子五类对应） */
val KB_CATEGORIES: List<Pair<String?, Int>> = listOf(
    null to R.string.vm_kb_category_all,
    "interaction" to R.string.vm_kb_category_interaction,
    "food_drug" to R.string.vm_kb_category_food_drug,
    "exercise" to R.string.vm_kb_category_exercise,
    "emergency" to R.string.vm_kb_category_emergency,
    "edu" to R.string.vm_kb_category_edu,
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

    /** 跨零点日期 ticker：复核到期计数依赖今天，不能冻结在构造时。 */
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
                _date.value = LocalDate.now()
            }
        }
    }

    /**
     * v9 优化：检索防抖——输入停顿 [KbSearch.DEBOUNCE_MS] 后才真正查库，
     * 替代此前「每敲一键就查一次」（原实现对 query 直接 flatMapLatest 连 DAO）。
     * 空串（含初始态与清空）不进延迟分支，立即回落列表 / 分类视图，
     * 避免清空输入后仍停留在上一次检索结果。
     */
    private val searchQuery: kotlinx.coroutines.flow.Flow<String> = query
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf("") else flow { delay(KbSearch.DEBOUNCE_MS); emit(q) }
        }
        .distinctUntilChanged()

    private val entries: kotlinx.coroutines.flow.Flow<List<KbEntry>> =
        combine(searchQuery, category) { q, c -> q to c }
            .flatMapLatest { (q, c) ->
                when {
                    q.isNotBlank() -> repo.searchKb(q.trim())
                    c != null -> repo.observeKbByCategory(c)
                    else -> repo.observeKbAll()
                }
            }

    /** 复核到期条目数（今日已过 review_due）——随跨零点日期重算（原 combine(flowOf(Unit)) 等价于 map）。 */
    private val overdue: kotlinx.coroutines.flow.Flow<Int> =
        combine(repo.observeKbAll(), _date) { list, d ->
            val today = d.toString()
            list.count { it.reviewDue < today }
        }

    val uiState: StateFlow<KnowledgeUiState> =
        combine(entries, query, category, overdue, repo.observeProfile()) { list, q, c, od, p ->
            // v1.0.64 B13：生活方式画像置顶——修掉 kb_seed_edu.json 里 edu-003（吸烟条目）
            // 的悬空挂点（其 applicable_scene 写着「profile 吸烟状态登记后知识库置顶」，
            // 但 lifestyle 此前从未被采集，联动永远不触发）。
            // 只在「全部」视图置顶：检索结果与分类视图保持用户自己的排序意图。
            val pinned = Lifestyle.fromJson(p?.lifestyle).pinnedKbIds()
            val ordered = if (q.isBlank() && c == null && pinned.isNotEmpty()) {
                val pinnedSet = pinned.toSet()
                list.filter { it.id in pinnedSet } + list.filter { it.id !in pinnedSet }
            } else {
                list
            }
            KnowledgeUiState(
                entries = ordered,
                category = c, query = q, overdueCount = od,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KnowledgeUiState())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(c: String?) { category.value = c }

    /** v10（B2）：个人备注层——有备注的条目数（列表页提示） */
    val noteCount: StateFlow<Int> = repo.observeKbNoteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** v10（B2）：写入个人备注（空白即清除）。只动 user_note，种子内容不受影响。 */
    fun saveNote(id: String, note: String?) {
        viewModelScope.launch { repo.saveKbNote(id, note) }
    }

    fun refreshReviewCheck() {
        val today = _date.value.toString()
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
