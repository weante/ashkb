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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord

enum class CheckupTab(val label: String) {
    ITEMS("项目"), RECORDS("记录"), LABS("化验"), IMAGING("影像"), VACCINES("疫苗")
}

/** M6 复诊管理页（协议 §M6）：项目 / 记录 / 化验 / 影像 / 疫苗五个 Tab。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupScreen(vm: CheckupViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(CheckupTab.ITEMS) }
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
        TopAppBar(title = { Text("复诊管理") })

        // Tab 切换（5 个 Chip，小屏可横向滚动）
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckupTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t, onClick = { tab = t },
                    label = { Text(t.label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (tab) {
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

    if (showItemForm) CheckupItemFormDialog(
        onSave = { vm.saveCheckupItem(it); showItemForm = false },
        onDismiss = { showItemForm = false },
    )
    if (showRecordForm) CheckupRecordFormDialog(
        items = items,
        onSave = { vm.saveCheckupRecord(it); showRecordForm = false },
        onDismiss = { showRecordForm = false },
    )
    if (showVaccineForm) VaccineFormDialog(
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
    if (showLabImport) AiImportDialog(
        kind = ImportKind.LAB,
        vm = vm,
        onDismiss = { showLabImport = false },
    )
    if (showImagingImport) AiImportDialog(
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
