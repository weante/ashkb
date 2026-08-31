package com.ashkb.app.ui.checkup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.repo.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CheckupViewModel(private val repo: HealthRepository) : ViewModel() {
    val date: LocalDate = LocalDate.now()

    val checkupItems: StateFlow<List<CheckupItem>> = repo.observeCheckupItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkupRecords: StateFlow<List<CheckupRecord>> = repo.observeCheckupRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaccineRecords: StateFlow<List<VaccineRecord>> = repo.observeVaccinesAll()
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
    fun labResultsFor(checkupId: String) = repo.observeLabByCheckup(checkupId)

    fun saveLabResult(result: LabResult) {
        viewModelScope.launch { repo.saveLabResult(result) }
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
