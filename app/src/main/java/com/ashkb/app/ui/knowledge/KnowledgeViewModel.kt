package com.ashkb.app.ui.knowledge

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.domain.DateProvider
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.KbUserEntry
import com.ashkb.app.domain.Lifestyle
import com.ashkb.app.ui.GlobalMessages
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
class KnowledgeViewModel(
    /** v1.2.16：导出物要落到 app 私有目录并走 FileProvider 分享，故需要 Context。 */
    private val app: Context,
    private val repo: HealthRepository,
    /** v1.0.86（批次 11）：全应用唯一的「今天」来源，取代此前的跨零点 ticker。 */
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)

    /**
     * 跨零点日期流：复核到期计数依赖今天，不能冻结在构造时。
     *
     * v1.0.86（批次 11）：底层改为进程级日期流（系统跨日广播驱动）——语义不变，仍是「今天」；
     * 原 ticker 走 `Handler.postDelayed`（uptimeMillis，深睡不计时），夜里跨零点不触发。
     */
    val date: StateFlow<LocalDate> = dateProvider.today

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
        combine(repo.observeKbAll(), date) { list, d ->
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
        val today = date.value.toString()
        viewModelScope.launch { repo.checkReviewDue(today) }
    }

    // ---- v1.2.16：个人条目（知识库编辑器 / 导入导出） ----

    /** 个人条目数（列表页「我的 N 条」提示） */
    val userCount: StateFlow<Int> = repo.observeKbUserCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun createUserEntry(draft: KbUserEntry.Draft, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repo.createKbUserEntry(draft, date.value.toString()) }
            if (result.isSuccess) {
                GlobalMessages.post(app.getString(R.string.kb_user_saved))
            } else {
                GlobalMessages.post(
                    app.getString(R.string.kb_user_save_failed, result.exceptionOrNull()?.message.orEmpty()),
                )
            }
            onDone(result.isSuccess)
        }
    }

    fun updateUserEntry(draft: KbUserEntry.Draft, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repo.updateKbUserEntry(draft, date.value.toString()) }
            val ok = result.getOrDefault(false)
            if (ok) {
                GlobalMessages.post(app.getString(R.string.kb_user_saved))
            } else {
                GlobalMessages.post(
                    app.getString(R.string.kb_user_save_failed, result.exceptionOrNull()?.message.orEmpty()),
                )
            }
            onDone(ok)
        }
    }

    fun deleteUserEntry(id: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { repo.deleteKbUserEntry(id) }.getOrDefault(false)
            GlobalMessages.post(
                app.getString(if (ok) R.string.kb_user_deleted else R.string.kb_user_delete_failed),
            )
            onDone(ok)
        }
    }

    /**
     * 导出全部个人条目为 JSON，写到 `files/exports/` 后交给系统的分享面板。
     *
     * 与档案导出（[com.ashkb.app.ui.backup.BackupViewModel.exportProfileJson]）同一套机制：
     * exports 目录启动时清空、`file_paths.xml` 已声明，分享用 FileProvider + ACTION_SEND。
     */
    fun exportUserEntries(onShare: (Intent) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val json = repo.exportKbUserJson()
                val count = repo.listKbUserEntries().size
                val file = File(File(app.filesDir, "exports"), "ashkb-kb-user-${LocalDate.now()}.json")
                file.parentFile?.mkdirs()
                file.writeText(json)
                count to file
            }.onSuccess { (count, file) ->
                if (count == 0) {
                    GlobalMessages.post(app.getString(R.string.kb_user_export_empty))
                } else {
                    GlobalMessages.post(
                        app.getString(R.string.kb_user_export_done, count, file.length()),
                    )
                    onShare(shareFile(file))
                }
            }.onFailure {
                GlobalMessages.post(app.getString(R.string.kb_user_export_failed, it.message.orEmpty()))
            }
        }
    }

    /** 从文件导入个人条目（整批一个事务，见 [HealthRepository.importKbUserJson]）。 */
    fun importUserEntries(bytes: ByteArray, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val text = String(bytes, Charsets.UTF_8)
            when (val plan = runCatching { repo.importKbUserJson(text, date.value.toString()) }.getOrNull()) {
                null -> {
                    GlobalMessages.post(app.getString(R.string.kb_user_import_failed))
                    onDone(false)
                }
                is KbUserEntry.ImportPlan.Ready -> {
                    GlobalMessages.post(
                        app.getString(R.string.kb_user_import_done, plan.drafts.size, plan.skipped),
                    )
                    onDone(true)
                }
                is KbUserEntry.ImportPlan.Rejected -> {
                    GlobalMessages.post(importErrorMessage(plan.error))
                    onDone(false)
                }
            }
        }
    }

    private fun importErrorMessage(error: KbUserEntry.ImportError): String = app.getString(
        when (error) {
            KbUserEntry.ImportError.TOO_LARGE -> R.string.kb_user_import_err_too_large
            KbUserEntry.ImportError.BAD_JSON -> R.string.kb_user_import_err_bad_json
            KbUserEntry.ImportError.NO_ENTRIES -> R.string.kb_user_import_err_no_entries
            KbUserEntry.ImportError.TOO_MANY -> R.string.kb_user_import_err_too_many
        },
    )

    private fun shareFile(file: File): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                KnowledgeViewModel(app, app.healthRepository, app.dateProvider)
            }
        }
    }
}
