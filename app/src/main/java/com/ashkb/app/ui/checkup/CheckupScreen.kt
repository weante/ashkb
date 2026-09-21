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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
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
    val items by vm.checkupItems.collectAsStateWithLifecycle()
    val records by vm.checkupRecords.collectAsStateWithLifecycle()
    val vaccines by vm.vaccineRecords.collectAsStateWithLifecycle()
    val labRecent by vm.labRecent.collectAsStateWithLifecycle()
    val labLimit by vm.labLimit.collectAsStateWithLifecycle()
    val imagingRecords by vm.imagingRecords.collectAsStateWithLifecycle()
    val seedResult by vm.seedResult.collectAsStateWithLifecycle()

    // C10 种入结果是本页的一次性反馈：VM 常驻在 AppShell，退出本页时清空，
    // 否则下次进来还会看到上次的"已添加 N 个节点"
    DisposableEffect(Unit) {
        onDispose { vm.consumeSeedResult() }
    }

    var showItemForm by remember { mutableStateOf(false) }
    var showRecordForm by remember { mutableStateOf(false) }
    var showVaccineForm by remember { mutableStateOf(false) }
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
                CheckupTab.ITEMS -> CheckupItemsList(
                    items = items,
                    onAdd = { showItemForm = true },
                    onDeactivate = { vm.deactivateCheckupItem(it) },
                    seedResult = seedResult,
                    onSeed = { vm.seedBiologicScreening() },
                )
                CheckupTab.RECORDS -> CheckupRecordsList(
                    records = records,
                    onAdd = { showRecordForm = true },
                    onViewLab = { showLabDetail = it },
                    onAttach = { attachTarget = it },
                    onOpenAllAttachments = { showAllAttachments = true },
                    // 准备清单排在最前：复诊管理的首要问题是"下次该做什么"，其次才是翻历史
                    prepHeader = { CheckupPrepCard(items, records, vm.date) },
                )
                CheckupTab.LABS -> LabsList(
                    labs = labRecent,
                    canLoadMore = labRecent.size >= labLimit,
                    onLoadMore = { vm.loadMoreLabs() },
                    onImport = { showLabImport = true },
                    onAttachDate = { attachLabDate = it },
                    onLinkDate = { pickLabsDate = it },
                )
                CheckupTab.IMAGING -> ImagingList(
                    records = imagingRecords,
                    onImport = { showImagingImport = true },
                    onView = { imagingDetail = it },
                    onAttach = { attachImaging = it },
                    onLink = { pickImaging = it },
                )
                CheckupTab.VACCINES -> VaccineList(
                    vaccines = vaccines,
                    onAdd = { showVaccineForm = true },
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
        onSave = { vm.saveCheckupRecord(it); showRecordForm = false },
        onDismiss = { showRecordForm = false },
    )
    if (showVaccineForm) VaccineFormSheet(
        onSave = { vm.saveVaccineRecord(it); showVaccineForm = false },
        onDismiss = { showVaccineForm = false },
    )
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
        AttachmentSheet(
            vm = vm,
            checkupId = labRecent.firstOrNull { it.date == d }?.checkupId,
            title = d,
            allowLink = true,
            onLinkSource = { vm.linkLabsByDate(d, it) },
            onDismiss = { attachLabDate = null },
        )
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
        CheckupRecordPickerDialog(
            records = records,
            currentId = labRecent.firstOrNull { it.date == d }?.checkupId,
            onPick = { vm.linkLabsByDate(d, it); pickLabsDate = null },
            onDismiss = { pickLabsDate = null },
        )
    }
    pickImaging?.let { r ->
        CheckupRecordPickerDialog(
            records = records,
            currentId = r.checkupId,
            onPick = { vm.linkImaging(r.id, it); pickImaging = null },
            onDismiss = { pickImaging = null },
        )
    }
}
