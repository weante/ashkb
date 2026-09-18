package com.ashkb.app.ui.checkup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.LabImport
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CheckupViewModel(private val repo: HealthRepository) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

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

    val checkupItems: StateFlow<List<CheckupItem>> = repo.observeCheckupItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkupRecords: StateFlow<List<CheckupRecord>> = repo.observeCheckupRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaccineRecords: StateFlow<List<VaccineRecord>> = repo.observeVaccinesAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _labLimit = MutableStateFlow(100)
    val labLimit: StateFlow<Int> = _labLimit.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val labRecent: StateFlow<List<LabResult>> = _labLimit
        .flatMapLatest { repo.observeLabRecent(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imagingRecords: StateFlow<List<ImagingRecord>> = repo.observeImagingRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 复诊项目 ----
    fun saveCheckupItem(item: CheckupItem) {
        viewModelScope.launch { repo.saveCheckupItem(item) }
    }

    fun deactivateCheckupItem(id: String) {
        viewModelScope.launch { repo.deactivateCheckupItem(id) }
    }

    // ---- 复诊记录 ----
    fun saveCheckupRecord(record: CheckupRecord) {
        viewModelScope.launch { repo.saveCheckupRecord(record) }
    }

    // ---- 化验结果 ----
    private val labByCheckup = mutableMapOf<String, StateFlow<List<LabResult>>>()

    fun labResultsFor(checkupId: String): StateFlow<List<LabResult>> =
        labByCheckup.getOrPut(checkupId) {
            repo.observeLabByCheckup(checkupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun saveLabResult(result: LabResult) {
        viewModelScope.launch { repo.saveLabResult(result) }
    }

    fun loadMoreLabs() {
        _labLimit.value += 100
    }

    fun importLabReport(import: LabImport) {
        viewModelScope.launch { repo.importLabReport(import) }
    }

    // ---- 影像记录（v1.0.4 AI 导入） ----
    fun saveImagingRecord(record: ImagingRecord) {
        viewModelScope.launch { repo.saveImagingRecord(record) }
    }

    fun importImagingReport(import: ImagingImport) {
        viewModelScope.launch { repo.importImagingReport(import) }
    }

    // ---- 疫苗记录 ----
    fun saveVaccineRecord(record: VaccineRecord) {
        viewModelScope.launch { repo.saveVaccineRecord(record) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                CheckupViewModel(app.healthRepository)
            }
        }
    }
}
