package com.ashkb.app.ui.checkup

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.ui.components.ScreenTopBar

enum class CheckupTab { ITEMS, RECORDS, LABS, IMAGING, VACCINES }

@Composable
private fun CheckupTab.label(): String = when (this) {
    CheckupTab.ITEMS -> stringResource(R.string.common_item)
    CheckupTab.RECORDS -> stringResource(R.string.common_record)
    CheckupTab.LABS -> stringResource(R.string.checkup_lab_tab)
    CheckupTab.IMAGING -> stringResource(R.string.checkup_imaging_tab)
    CheckupTab.VACCINES -> stringResource(R.string.checkup_vaccine_tab)
}

/** M6 复诊管理页（协议 §M6）：项目 / 记录 / 化验 / 影像 / 疫苗五个 Tab。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupScreen(vm: CheckupViewModel, onBack: () -> Unit) {
    // 五路主导航：FilterChip 的语义是"筛选"而非"切换视图"，且 32dp 不满足触摸标准。
    // 改为 ScrollableTabRow + HorizontalPager，与报表页统一，并自带 selectedTabIndex 语义。
    val pager = rememberPagerState(pageCount = { CheckupTab.entries.size })
    val scope = rememberCoroutineScope()
    // v1.2.18（性能 · 深层 Flow 下沉）：根部只留 items 一路。
    // 它要参与「记录」tab 的筛选解析（filterItem）与记录表单的项目预填，且项目只在增删改时变；
    // 其余七路（records / vaccines / labRecent / labLimit / labTotal / imagingRecords / seedResult）
    // 已下沉到各自 tab 或用到它的 sheet 内部收集——此前任何一路变化都会把五个 tab 全部重组一遍。
    val items by vm.checkupItems.collectAsStateWithLifecycle()

    // v1.0.87（批次 13）：「项目」点一行 →「记录」tab 只看这个项目的记录。
    // 默认 null = 不筛选，「记录」tab 的默认展示（全部记录）因此没有变化。
    // rememberSaveable：转屏 / 切 tab 不丢筛选；离开本页（返回栈弹出）才清空。
    var recordsFilterItemId by rememberSaveable { mutableStateOf<String?>(null) }
    // 项目被「停用」后就不再出现在 items 里（列表只查 is_active = 1），此处的筛选随之失效——
    // 于是不再做「自动清除」这类跨状态同步：解析不到项目就当没筛选，天然不会出现
    // 「顶栏挂着一个查不到名字的标识、列表却永远空」的状态
    val filterItem = items.firstOrNull { it.id == recordsFilterItemId }

    // 记录表单的项目名预填（见下方 onAdd）：记录与项目的关联靠名字快照，
    // 带筛选补录时不预填就会「补录完仍然不在该项目下」——空态里那句引导也就成了空话
    var recordFormPresetItem by remember { mutableStateOf<CheckupItem?>(null) }

    // 筛选走 SQL（而不是「先取最近 50 条再在内存里筛」）：项目的记录可能整段落在窗口之外，
    // 那样筛出来是「该项目没有记录」——恰好是这一批要消灭的「找不到东西」。
    // 冷 Flow + remember(filterItem)：项目改名后按新名字重订阅（查询条件里含名字快照）。
    // v1.2.18：这段随 records 一起下沉到 CheckupRecordsTab。

    // C10 种入结果是本页的一次性反馈：VM 常驻在 AppShell，退出本页时清空，
    // 否则下次进来还会看到上次的"已添加 N 个节点"
    DisposableEffect(Unit) {
        onDispose { vm.consumeSeedResult() }
    }

    var showItemForm by remember { mutableStateOf(false) }
    var showRecordForm by remember { mutableStateOf(false) }
    var showVaccineForm by remember { mutableStateOf(false) }
    // v1.0.80（批次 6）：编辑目标（null = 新增）。三个表单共用「新增 / 编辑」两态，
    // 于是「回填原值」这件事只在一处实现，不会出现「新增能校验、编辑漏了校验」的分叉。
    var editItem by remember { mutableStateOf<CheckupItem?>(null) }
    var editRecord by remember { mutableStateOf<CheckupRecord?>(null) }
    var editVaccine by remember { mutableStateOf<VaccineRecord?>(null) }
    // 级联删除确认框的目标（条数在框内异步取，见 CheckupDeleteDialogs）
    var deleteRecord by remember { mutableStateOf<CheckupRecord?>(null) }
    // 化验 / 影像的修改目标：表单里既能保存也能删除
    var editLab by remember { mutableStateOf<LabResult?>(null) }
    var editImaging by remember { mutableStateOf<ImagingRecord?>(null) }
    var showLabDetail by remember { mutableStateOf<CheckupRecord?>(null) }
    var showLabImport by remember { mutableStateOf(false) }
    var showImagingImport by remember { mutableStateOf(false) }
    var imagingDetail by remember { mutableStateOf<ImagingRecord?>(null) }
    // 附件归档的目标记录：null = 未打开。用整条记录而非 id，是为了把日期直接当 sheet 表头
    var attachTarget by remember { mutableStateOf<CheckupRecord?>(null) }
    // v11：化验按"日期分组"整组处理、影像按单条处理，故各留一个待归档/待归属的目标
    var attachLabDate by remember { mutableStateOf<String?>(null) }
    var attachImaging by remember { mutableStateOf<ImagingRecord?>(null) }
    // 全部附件总览：未归属复诊的附件只能从这里找到并补归
    var showAllAttachments by remember { mutableStateOf(false) }
    var pickLabsDate by remember { mutableStateOf<String?>(null) }
    var pickImaging by remember { mutableStateOf<ImagingRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.checkup_manage_title), onBack = onBack)

        ScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CheckupTab.entries.forEachIndexed { index, t ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(t.label()) },
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (CheckupTab.entries[page]) {
                CheckupTab.ITEMS -> CheckupItemsTab(
                    vm = vm,
                    items = items,
                    onAdd = { showItemForm = true },
                    onEdit = { editItem = it },
                    // v1.0.87（批次 13）：先落筛选再翻页——翻页是协程，若先翻页，
                    // 记录 tab 会有一帧显示"全部记录"，看起来像筛选没生效
                    onOpenRecords = { item ->
                        recordsFilterItemId = item.id
                        scope.launch { pager.animateScrollToPage(CheckupTab.RECORDS.ordinal) }
                    },
                )
                CheckupTab.RECORDS -> CheckupRecordsTab(
                    vm = vm,
                    items = items,
                    filterItem = filterItem,
                    onAdd = {
                        recordFormPresetItem = filterItem
                        showRecordForm = true
                    },
                    actions = RecordsTabActions(
                        onClearFilter = { recordsFilterItemId = null },
                        onViewLab = { showLabDetail = it },
                        onAttach = { attachTarget = it },
                        onOpenAllAttachments = { showAllAttachments = true },
                        onEdit = { editRecord = it },
                        onDelete = { deleteRecord = it },
                    ),
                )
                CheckupTab.LABS -> CheckupLabsTab(
                    vm = vm,
                    onImport = { showLabImport = true },
                    onAttachDate = { attachLabDate = it },
                    onLinkDate = { pickLabsDate = it },
                    onEditLab = { editLab = it },
                )
                CheckupTab.IMAGING -> CheckupImagingTab(
                    vm = vm,
                    onImport = { showImagingImport = true },
                    onView = { imagingDetail = it },
                    onAttach = { attachImaging = it },
                    onLink = { pickImaging = it },
                    onEdit = { editImaging = it },
                )
                CheckupTab.VACCINES -> CheckupVaccinesTab(
                    vm = vm,
                    onAdd = { showVaccineForm = true },
                    onEdit = { editVaccine = it },
                )
            }
        }
    }

    if (showItemForm) CheckupItemFormSheet(
        onSave = { vm.saveCheckupItem(it); showItemForm = false },
        onDismiss = { showItemForm = false },
    )
    if (showRecordForm) CheckupRecordFormSheet(
        items = items,
        presetItemName = recordFormPresetItem?.name,
        onSave = { vm.saveCheckupRecord(it); showRecordForm = false },
        onDismiss = { showRecordForm = false },
    )
    if (showVaccineForm) VaccineFormSheet(
        onSave = { vm.saveVaccineRecord(it); showVaccineForm = false },
        onDismiss = { showVaccineForm = false },
    )
    // v1.0.80（批次 6）：编辑三态——复用同一张表单，回填原值并沿用主键
    editItem?.let { item ->
        CheckupItemFormSheet(
            existing = item,
            onSave = { vm.saveCheckupItem(it); editItem = null },
            onDismiss = { editItem = null },
        )
    }
    editRecord?.let { rec ->
        CheckupRecordFormSheet(
            items = items,
            existing = rec,
            onSave = { vm.saveCheckupRecord(it); editRecord = null },
            onDismiss = { editRecord = null },
        )
    }
    editVaccine?.let { vac ->
        VaccineFormSheet(
            existing = vac,
            onSave = { vm.saveVaccineRecord(it); editVaccine = null },
            onDelete = { vm.deleteVaccineRecord(it.id) },
            onDismiss = { editVaccine = null },
        )
    }
    // 级联删除确认：条数在对话框内异步取，逐类报出将一并删除的化验 / 影像 / 附件
    deleteRecord?.let { rec ->
        CheckupRecordDeleteDialog(record = rec, vm = vm, onDismiss = { deleteRecord = null })
    }
    editLab?.let { lab ->
        LabResultFormSheet(
            existing = lab, date = lab.date, checkupId = lab.checkupId,
            onSave = { vm.saveLabResult(it); editLab = null },
            onDelete = { vm.deleteLabResult(it.id) },
            onDismiss = { editLab = null },
        )
    }
    editImaging?.let { img ->
        ImagingFormSheet(
            existing = img,
            onSave = { vm.saveImagingRecord(it); editImaging = null },
            onDelete = { vm.deleteImagingRecord(it.id) },
            onDismiss = { editImaging = null },
        )
    }
    showLabDetail?.let { rec ->
        LabDetailDialog(
            record = rec,
            vm = vm,
            onDismiss = { showLabDetail = null },
        )
    }
    if (showLabImport) AiImportSheet(
        kind = ImportKind.LAB,
        vm = vm,
        onDismiss = { showLabImport = false },
    )
    if (showImagingImport) AiImportSheet(
        kind = ImportKind.IMAGING,
        vm = vm,
        onDismiss = { showImagingImport = false },
    )
    imagingDetail?.let { rec ->
        ImagingDetailDialog(
            record = rec,
            onDismiss = { imagingDetail = null },
        )
    }
    // B10 附件归档：从记录卡进入时绑定该记录 id，表头用记录日期便于确认归属
    attachTarget?.let { rec ->
        AttachmentSheet(
            vm = vm,
            checkupId = rec.id,
            title = rec.date,
            onDismiss = { attachTarget = null },
        )
    }
    // v11 化验附件归档：初始归属取该日期分组的现有归属，
    // allowLink 让用户在归档的同时把"这天的化验"整组归到某条复诊，省一次往返
    attachLabDate?.let { d ->
        LabAttachSheetHost(vm = vm, date = d, onDismiss = { attachLabDate = null })
    }
    attachImaging?.let { r ->
        AttachmentSheet(
            vm = vm,
            checkupId = r.checkupId,
            title = "${r.examDate} · ${r.bodyPart}",
            allowLink = true,
            onLinkSource = { vm.linkImaging(r.id, it) },
            onDismiss = { attachImaging = null },
        )
    }
    // 总览不绑定具体记录（checkupId = null）：未归属的附件才有机会被看见并补归
    if (showAllAttachments) {
        AttachmentSheet(
            vm = vm,
            checkupId = null,
            title = stringResource(R.string.attach_title),
            allowLink = true,
            onDismiss = { showAllAttachments = false },
        )
    }
    // 从化验/影像列表直接发起归属：与 sheet 内改归属走同一套 vm 接口，两边状态自然同步
    pickLabsDate?.let { d ->
        LabsLinkPickerHost(vm = vm, date = d, onDismiss = { pickLabsDate = null })
    }
    pickImaging?.let { r ->
        ImagingLinkPickerHost(vm = vm, record = r, onDismiss = { pickImaging = null })
    }
}

// ---------------------------------------------------------------------------
// v1.2.18（性能 · 深层 Flow 下沉）
// 五个 tab 与三个用到化验/记录数据的 sheet 各自收集自己那一路 StateFlow。
// 此前八路都在 CheckupScreen 根部收集：化验翻页（labRecent）会让「项目」「记录」「疫苗」
// 三个 tab 一起重组，反之亦然。下沉后各路互不牵连。
// 「记录」tab 的回调有 6 个，收进 RecordsTabActions 以免踩 detekt 的 8 参数线。
// ---------------------------------------------------------------------------

private class RecordsTabActions(
    val onClearFilter: () -> Unit,
    val onViewLab: (CheckupRecord) -> Unit,
    val onAttach: (CheckupRecord) -> Unit,
    val onOpenAllAttachments: () -> Unit,
    val onEdit: (CheckupRecord) -> Unit,
    val onDelete: (CheckupRecord) -> Unit,
)

@Composable
private fun CheckupItemsTab(
    vm: CheckupViewModel,
    items: List<CheckupItem>,
    onAdd: () -> Unit,
    onEdit: (CheckupItem) -> Unit,
    onOpenRecords: (CheckupItem) -> Unit,
) {
    val seedResult by vm.seedResult.collectAsStateWithLifecycle()
    CheckupItemsList(
        items = items,
        onAdd = onAdd,
        onDeactivate = { vm.deactivateCheckupItem(it) },
        seedResult = seedResult,
        onSeed = { vm.seedBiologicScreening() },
        onEdit = onEdit,
        onOpenRecords = onOpenRecords,
    )
}

@Composable
private fun CheckupRecordsTab(
    vm: CheckupViewModel,
    items: List<CheckupItem>,
    filterItem: CheckupItem?,
    onAdd: () -> Unit,
    actions: RecordsTabActions,
) {
    val records by vm.checkupRecords.collectAsStateWithLifecycle()
    val itemRecordsFlow: Flow<List<CheckupRecord>> = remember(filterItem) {
        filterItem?.let { vm.checkupRecordsForItem(it) } ?: emptyFlow()
    }
    val itemRecords by itemRecordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // 未筛选时仍用 VM 那份常驻 StateFlow——「记录」tab 的默认展示与筛选前完全一致
    val visibleRecords = if (filterItem != null) itemRecords else records
    CheckupRecordsList(
        records = visibleRecords,
        filterItemName = filterItem?.name,
        onClearFilter = actions.onClearFilter,
        onAdd = onAdd,
        onViewLab = actions.onViewLab,
        onAttach = actions.onAttach,
        onOpenAllAttachments = actions.onOpenAllAttachments,
        onEdit = actions.onEdit,
        onDelete = actions.onDelete,
        // 准备清单排在最前：复诊管理的首要问题是"下次该做什么"，其次才是翻历史。
        // 它算的是**全部**记录（下次复诊与当前筛选无关），故这里传未筛选的 records。
        // v1.1.1（MEDIUM-4）：日期改为**收集** VM 的 StateFlow（此前是普通 getter，
        // 跨零点不会触发重组，卡片里的「还有 N 天」会冻在前一天）。
        prepHeader = {
            val today by vm.date.collectAsStateWithLifecycle()
            CheckupPrepCard(items, records, today)
        },
    )
}

@Composable
private fun CheckupLabsTab(
    vm: CheckupViewModel,
    onImport: () -> Unit,
    onAttachDate: (String) -> Unit,
    onLinkDate: (String) -> Unit,
    onEditLab: (LabResult) -> Unit,
) {
    val labRecent by vm.labRecent.collectAsStateWithLifecycle()
    val labLimit by vm.labLimit.collectAsStateWithLifecycle()
    val labTotal by vm.labTotal.collectAsStateWithLifecycle()
    LabsList(
        labs = labRecent,
        totalCount = labTotal,
        canLoadMore = labRecent.size >= labLimit,
        onLoadMore = { vm.loadMoreLabs() },
        onImport = onImport,
        onAttachDate = onAttachDate,
        onLinkDate = onLinkDate,
        onEditLab = onEditLab,
    )
}

@Composable
private fun CheckupImagingTab(
    vm: CheckupViewModel,
    onImport: () -> Unit,
    onView: (ImagingRecord) -> Unit,
    onAttach: (ImagingRecord) -> Unit,
    onLink: (ImagingRecord) -> Unit,
    onEdit: (ImagingRecord) -> Unit,
) {
    val imagingRecords by vm.imagingRecords.collectAsStateWithLifecycle()
    ImagingList(
        records = imagingRecords,
        onImport = onImport,
        onView = onView,
        onAttach = onAttach,
        onLink = onLink,
        onEdit = onEdit,
    )
}

@Composable
private fun CheckupVaccinesTab(
    vm: CheckupViewModel,
    onAdd: () -> Unit,
    onEdit: (VaccineRecord) -> Unit,
) {
    val vaccines by vm.vaccineRecords.collectAsStateWithLifecycle()
    VaccineList(
        vaccines = vaccines,
        onAdd = onAdd,
        onEdit = onEdit,
    )
}

/** 化验附件的归属 sheet 要用「该日期那组化验当前的 checkupId」，故只在这里收集 labRecent。 */
@Composable
private fun LabAttachSheetHost(vm: CheckupViewModel, date: String, onDismiss: () -> Unit) {
    val labRecent by vm.labRecent.collectAsStateWithLifecycle()
    AttachmentSheet(
        vm = vm,
        checkupId = labRecent.firstOrNull { it.date == date }?.checkupId,
        title = date,
        allowLink = true,
        onLinkSource = { vm.linkLabsByDate(date, it) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LabsLinkPickerHost(vm: CheckupViewModel, date: String, onDismiss: () -> Unit) {
    val records by vm.checkupRecords.collectAsStateWithLifecycle()
    val labRecent by vm.labRecent.collectAsStateWithLifecycle()
    CheckupRecordPickerDialog(
        records = records,
        currentId = labRecent.firstOrNull { it.date == date }?.checkupId,
        onPick = { vm.linkLabsByDate(date, it); onDismiss() },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ImagingLinkPickerHost(vm: CheckupViewModel, record: ImagingRecord, onDismiss: () -> Unit) {
    val records by vm.checkupRecords.collectAsStateWithLifecycle()
    CheckupRecordPickerDialog(
        records = records,
        currentId = record.checkupId,
        onPick = { vm.linkImaging(record.id, it); onDismiss() },
        onDismiss = onDismiss,
    )
}
