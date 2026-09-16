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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.ui.components.ScreenTopBar

enum class CheckupTab(val label: String) {
    ITEMS("项目"), RECORDS("记录"), LABS("化验"), IMAGING("影像"), VACCINES("疫苗")
}

/** M6 复诊管理页（协议 §M6）：项目 / 记录 / 化验 / 影像 / 疫苗五个 Tab。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupScreen(vm: CheckupViewModel, onBack: () -> Unit) {
    // 五路主导航：FilterChip 的语义是"筛选"而非"切换视图"，且 32dp 不满足触摸标准。
    // 改为 ScrollableTabRow + HorizontalPager，与报表页统一，并自带 selectedTabIndex 语义。
    val pager = rememberPagerState(pageCount = { CheckupTab.entries.size })
    val scope = rememberCoroutineScope()
    val items by vm.checkupItems.collectAsState()
    val records by vm.checkupRecords.collectAsState()
    val vaccines by vm.vaccineRecords.collectAsState()
    val labRecent by vm.labRecent.collectAsState()
    val imagingRecords by vm.imagingRecords.collectAsState()

    var showItemForm by remember { mutableStateOf(false) }
    var showRecordForm by remember { mutableStateOf(false) }
    var showVaccineForm by remember { mutableStateOf(false) }
    var showLabDetail by remember { mutableStateOf<CheckupRecord?>(null) }
    var showLabImport by remember { mutableStateOf(false) }
    var showImagingImport by remember { mutableStateOf(false) }
    var imagingDetail by remember { mutableStateOf<ImagingRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "复诊管理", onBack = onBack)

        ScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CheckupTab.entries.forEachIndexed { index, t ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(t.label) },
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
                )
                CheckupTab.RECORDS -> CheckupRecordsList(
                    records = records,
                    onAdd = { showRecordForm = true },
                    onViewLab = { showLabDetail = it },
                )
                CheckupTab.LABS -> LabsList(
                    labs = labRecent,
                    onImport = { showLabImport = true },
                )
                CheckupTab.IMAGING -> ImagingList(
                    records = imagingRecords,
                    onImport = { showImagingImport = true },
                    onView = { imagingDetail = it },
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
}
