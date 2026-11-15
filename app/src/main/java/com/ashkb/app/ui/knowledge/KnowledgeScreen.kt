package com.ashkb.app.ui.knowledge

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.ashkb.app.ui.components.AshkbTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.KbUserEntry
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.FilterChips
import com.ashkb.app.ui.components.PageHeader
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.colors
import kotlinx.coroutines.delay

/** 导入个人条目时的文件大小上限（字节）。真正的条数/字符上限在 [KbUserEntry.parseExport] 里。 */
private const val KB_IMPORT_PICK_LIMIT = 8L * 1024 * 1024

/** K 知识库：种子条目 + 个人条目的浏览 / 搜索 / 详情 / 编辑 / 导入导出 + 复核到期提示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(vm: KnowledgeViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<KbEntry?>(null) }
    val todayDate by vm.date.collectAsStateWithLifecycle()
    val today = remember(todayDate) { todayDate.toString() }
    val noteCount by vm.noteCount.collectAsStateWithLifecycle()
    val userCount by vm.userCount.collectAsStateWithLifecycle()

    // ---- v1.2.16：个人条目的编辑 / 导入导出 ----
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<KbEntry?>(null) }

    // 搜索框本地态 + 防抖：避免每敲一键就重算 uiState 导致整屏重组（VM 内防抖只护住了 DAO 查询）
    var queryText by rememberSaveable { mutableStateOf(ui.query) }
    LaunchedEffect(queryText) {
        if (queryText == ui.query) return@LaunchedEffect
        delay(KbSearch.DEBOUNCE_MS)
        vm.setQuery(queryText)
    }

    LaunchedEffect(Unit) { vm.refreshReviewCheck() }

    /** 保存个人条目成功后清空搜索词与分类筛选，让新条目一定能出现在列表里。 */
    val resetFilters: () -> Unit = {
        queryText = ""
        vm.setQuery("")
        vm.setCategory(null)
    }

    // 导入：系统文件选择器 → 有界读取 → 解析（校验与事务都在下层）。
    // 读取失败与解析失败给的是同一句「导入失败」+ 具体原因各自一句话，见 VM.importUserEntries。
    val importLauncher = rememberKbImportLauncher(vm, onImported = resetFilters)

    Column(Modifier.fillMaxSize()) {
        // v1.2.10（UI 2.0 批次 C「换件」）：标题卡片退役，改**直排页头**（设计稿 §5.13 的 L1）。
        // v1.2.3 把标题装进卡片是为了让它与下方白卡片"同一套视觉语言"，真机用下来出现了新问题：
        // 标题也是卡片、内容也是卡片，一屏三张同款平权排开，**页面名与区块名分不出来**。
        // overline 放这行定位语（原在副题位置，没有副题时页头会显得空）。
        PageHeader(
            title = stringResource(R.string.knowledge_title_count, ui.entries.size),
            overline = stringResource(R.string.kb_overline),
            // v1.2.16：新建 + 导入/导出。都放页头 actions——知识库是「查」的页面，
            // 这两个是低频「写」的入口，不该占列表的版面。
            actions = {
                KnowledgeHeaderActions(
                    onNew = { creating = true },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onExport = {
                        vm.exportUserEntries { intent ->
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.kb_user_export_action)),
                            )
                        }
                    },
                )
            },
        )
        Spacer(Modifier.height(Spacing.sm))

        // 搜索 + 分类筛选固定吸顶，滚动不消失
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            AshkbTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text(stringResource(R.string.knowledge_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.sm))
            // v1.2.10（UI 2.0 批次 C）：这里是**筛选**（"筛出哪一类"），不是页面级切换，
            // 故改用 `FilterChips`（横滚、不等宽、选中实心 brand）。原 `TabChips` 与报表页签
            // 共用一份渐变胶囊实现，结果是"换页"和"筛东西"长得一模一样；渐变本身也按红线 8 退场。
            FilterChips(
                labels = KB_CATEGORIES.map { stringResource(it.second) },
                selectedIndex = KB_CATEGORIES.indexOfFirst { it.first == ui.category }.coerceAtLeast(0),
                onSelect = { i -> vm.setCategory(KB_CATEGORIES[i].first) },
            )
            KnowledgeFilterMeta(noteCount = noteCount, userCount = userCount, overdueCount = ui.overdueCount)
            Spacer(Modifier.height(Spacing.xs))
        }

        KbList(entries = ui.entries, query = ui.query, today = today, onOpen = { detail = it })
    }

    // 用列表里的最新 entry 渲染弹窗：备注保存后 Room 会推新实例，若沿用点开那一刻的旧对象，
    // 弹窗内的 userNote 永远停在旧值，保存看起来「没生效」。
    detail?.let { opened ->
        KnowledgeDetailHost(
            entries = ui.entries,
            opened = opened,
            today = today,
            onSaveNote = { id, note -> vm.saveNote(id, note) },
            onEdit = { live -> editTarget = live; detail = null },
            onDelete = { id -> vm.deleteUserEntry(id); detail = null },
            onDismiss = { detail = null },
        )
    }

    // 新建 / 编辑弹层：保存成功才关闭（失败时留在表单里，用户不用重打一遍）。
    // 保存成功后把搜索与分类筛选清掉——否则新条目可能落在别的分类/被当前关键词筛掉，
    // 用户刚点完保存却看不见它，会以为没存上。
    KnowledgeEditHost(
        creating = creating,
        editTarget = editTarget,
        onCreate = { draft, done ->
            vm.createUserEntry(draft) { ok ->
                if (ok) resetFilters()
                done(ok)
            }
        },
        onUpdate = { draft, done ->
            vm.updateUserEntry(draft) { ok ->
                if (ok) resetFilters()
                done(ok)
            }
        },
        onClosed = { creating = false; editTarget = null },
    )
}

/** 导入用的系统文件选择器：只做「有界读取 + 交给 VM」，解析与校验都在领域层。 */
@Composable
private fun rememberKbImportLauncher(
    vm: KnowledgeViewModel,
    onImported: () -> Unit,
): ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BackupEngine.readBounded(stream, KB_IMPORT_PICK_LIMIT, "ashkb-kb-user.json")
                }
            }.getOrNull()
        }
        if (bytes == null) {
            if (uri != null) GlobalMessages.post(context.getString(R.string.kb_user_import_failed))
        } else {
            vm.importUserEntries(bytes) { ok -> if (ok) onImported() }
        }
    }
}

/** 列表区：空结果提示 + 卡片行。抽出来是为了让主函数只负责组装，不承担列表分支。 */
@Composable
private fun KbList(entries: List<KbEntry>, query: String, today: String, onOpen: (KbEntry) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (entries.isEmpty() && query.isNotBlank()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.knowledge_no_match),
                    body = stringResource(R.string.knowledge_search_empty),
                )
            }
        }
        entries.forEach { entry ->
            item(key = entry.id) {
                KbListCard(
                    entry = entry,
                    overdue = entry.reviewDue < today,
                    hasNote = !entry.userNote.isNullOrBlank(),
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

/** 新建 / 编辑弹层的宿主：保存回调带「是否成功」，成功才关，失败留在表单里。 */
@Composable
private fun KnowledgeEditHost(
    creating: Boolean,
    editTarget: KbEntry?,
    onCreate: (KbUserEntry.Draft, (Boolean) -> Unit) -> Unit,
    onUpdate: (KbUserEntry.Draft, (Boolean) -> Unit) -> Unit,
    onClosed: () -> Unit,
) {
    if (!creating && editTarget == null) return
    KbEditSheet(
        initial = editTarget,
        onDismiss = onClosed,
        onSave = { draft ->
            if (draft.id == null) onCreate(draft) { if (it) onClosed() } else onUpdate(draft) { if (it) onClosed() }
        },
    )
}

/** 页头两个写入口：新建；`⋮` 里是导入 / 导出。抽出来是为了不把重组菜单的开关塞进主函数的分支复杂度。 */
@Composable
private fun KnowledgeHeaderActions(
    onNew: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onNew, modifier = Modifier.size(Size.touchMin)) {
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.kb_user_new_title))
    }
    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Size.touchMin)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.kb_user_import_action))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.kb_user_import_action)) },
                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onImport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.kb_user_export_action)) },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onExport()
                },
            )
        }
    }
}

/**
 * 筛选行下面的元信息带：先「有备注 N 条」（B2），再「我的条目 N 条」（v1.2.16），
 * 最后是复核到期告警。
 *
 * 两行计数都是**只在自己积累过之后才出现**——不给空知识库加噪音。
 */
@Composable
private fun KnowledgeFilterMeta(noteCount: Int, userCount: Int, overdueCount: Int) {
    if (noteCount > 0) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.kb_note_count, noteCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (userCount > 0) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.kb_user_count, userCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (overdueCount > 0) {
        Spacer(Modifier.height(Spacing.sm))
        AlertBanner(
            tone = StatusTone.Warning,
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.knowledge_overdue_count, overdueCount),
            body = stringResource(R.string.common_doctor_final_note),
        )
    }
}

/**
 * 详情弹窗的宿主：负责把「点开那一刻的 entry」换成列表里的最新实例，并按来源决定是否给「编辑 / 删除」。
 *
 * v1.2.16：只有自己写的条目才有这两个动作——种子条目的内容归 App 版本所有，
 * 就地改会被下一次种子刷新（`KbSeedRefresh`）按字段比对覆盖回去。
 */
@Composable
private fun KnowledgeDetailHost(
    entries: List<KbEntry>,
    opened: KbEntry,
    today: String,
    onSaveNote: (String, String?) -> Unit,
    onEdit: (KbEntry) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val live = entries.firstOrNull { it.id == opened.id } ?: opened
    val mine = KbUserEntry.isUserEntry(live.id)
    KbDetailDialog(
        entry = live,
        // v1.2.7（批次 14 / R8）：把本屏已经算好的 `today` 传进去，弹窗不再自己读时钟
        today = today,
        onSaveNote = { onSaveNote(live.id, it) },
        onDismiss = onDismiss,
        onEdit = if (mine) {{ onEdit(live) }} else null,
        onDelete = if (mine) {{ onDelete(live.id) }} else null,
    )
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "interaction" -> stringResource(R.string.knowledge_interaction_title)
    "food_drug" -> stringResource(R.string.knowledge_food_drug_section)
    "exercise" -> stringResource(R.string.exercise_tab)
    "emergency" -> stringResource(R.string.emergency_tab)
    "edu" -> stringResource(R.string.knowledge_education_tag)
    else -> category
}

/** 类别固定映射（方案 §10.9）：药物 Info、食物 Brand、指南 Neutral、相互作用 Warning。 */
private fun categoryTone(category: String): StatusTone = when (category) {
    "interaction" -> StatusTone.Warning
    "food_drug" -> StatusTone.Brand
    "exercise" -> StatusTone.Info
    "emergency" -> StatusTone.Danger
    else -> StatusTone.Neutral
}

/**
 * 行右侧的**类别徽章**（v1.2.10 · UI 2.0 §06 ⑤）。
 *
 * 设计稿把类别从「卡片内左上胶囊」挪到「条目行右侧的软底小徽章」，与标题同一行、
 * 左对齐标题、右贴徽章——长列表里扫一眼就能认出应急条目。形态照设计稿 `.tier`：
 * 6dp 圆角（[MaterialTheme.shapes.extraSmall]）+ 小字加粗 + 软底；**不复用 [StatusChip]**
 * 是因为胶囊形态（CircleShape + 28dp 下限）在标题行右侧会显得比标题还重，且 28dp 下限
 * 会把行高顶起来。
 *
 * ⚠️ 字号取 `labelSmall`（12sp）而非设计稿的 10px：12sp 是本项目的字号硬下限
 * （`ui/theme/Type.kt`），10sp 会让中文字形糊成一团。颜色一律走 [StatusTone.colors]，
 * 禁止在这里自己写 `when (tone)`。
 */
@Composable
private fun CategoryBadge(category: String) {
    val (bg, fg) = categoryTone(category).colors()
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Text(
            text = categoryLabel(category),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 知识库卡片。
 *
 * v1.0.90（批次 15）：标签行由 `Row`（含 `Spacer(weight(1f))`）改 `FlowRow`——四枚胶囊
 * （类别 + 高风险 + 有备注 + 已过复核日）最坏组合固有宽度 ≈72+70+66+127 = **335dp**，
 * 卡片内宽：360dp 屏 296dp、320dp 屏 256dp。原写法里 `Spacer(weight(1f))` 前是无权重文本，
 * 胶囊按固有宽度测量、放不下只会逐字折行（末位「已过复核日」折 2–3 行、被 28dp 下限压扁）；
 * `FlowRow` 让整枚胶囊折到下一行。`Spacer(weight(1f))` 同时删掉：`FlowRow` 没有权重概念，
 * 且胶囊组本来就该左对齐贴排（不再需要"把右侧徽标顶到行尾"这一步——那正是挤压的来源）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun KbListCard(
    entry: KbEntry,
    overdue: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (entry.severityLevel == "high") {
                    StatusChip(text = stringResource(R.string.knowledge_high_risk), tone = StatusTone.Danger, icon = Icons.Rounded.WarningAmber)
                }
                // v1.2.16：自己写的条目带一枚中性徽章——它没有证据层级可讲，
                // 混在种子里不加标注，翻到时很容易被当成官方内容。
                if (KbUserEntry.isUserEntry(entry.id)) {
                    StatusChip(text = stringResource(R.string.kb_user_badge), tone = StatusTone.Brand)
                }
                // B2：有备注的条目给个中性标签，方便在长列表里找回自己做过批注的那几条
                if (hasNote) {
                    StatusChip(text = stringResource(R.string.kb_note_has_tag), tone = StatusTone.Info)
                }
                if (overdue) {
                    StatusChip(text = stringResource(R.string.checkup_overdue_short), tone = StatusTone.Warning, icon = Icons.Rounded.Schedule)
                }
            }
            // v1.2.10（UI 2.0 §06 ⑤）：类别从「左上胶囊」挪到「标题行右侧徽章」。
            // 标题列用 `weight(1f)` 收紧、徽章非权重排在**之后**——v1.0.90 的挤压教训
            // 说的是「权重子项排在非权重尾部元素之前」会把后面那项压成零宽，此处顺序相反。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CategoryBadge(entry.category)
            }
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            // v1.1.2：层级不再只显示裸字母——列表给「字母 + 短释义」一行放得下，
            // 最重要的是这行是**多数人唯一会看**的地方（详情未必有人点开）
            Text(
                "${tierShortLabel(entry.sourceTier)} · ${entry.sourceName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
