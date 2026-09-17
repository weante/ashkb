package com.ashkb.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeViewModel(private val repo: MedicationRepository) : ViewModel() {

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val meds: StateFlow<List<Medication>> =
        repo.observeMedications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProfile(p: Profile) = viewModelScope.launch { repo.saveProfile(p) }

    fun saveMedication(context: android.content.Context, med: Medication) = viewModelScope.launch {
        repo.saveMedication(med)
        val actives = repo.observeMedications()
        val list = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
        ReminderScheduler.rescheduleAll(context, list)
    }

    fun stopMedication(context: android.content.Context, med: Medication, reason: String, note: String?) =
        viewModelScope.launch {
            repo.stopMedication(med, reason, note)
            // R6 治本：归档≠删除，rescheduleAll 只遍历活跃药、取消不到被停药的未来闹钟——
            // 先单独取消该药全部闹钟（slotsFor 不查归档标志，request code 仍可算对），再重排活跃药
            ReminderScheduler.cancelAllFuture(context, listOf(med))
            val list = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
            ReminderScheduler.rescheduleAll(context, list)
        }

    /** R03：新增药核对清单查询（suspend 由 UI 协程调用） */
    suspend fun interactionsFor(med: Medication): List<KbEntry> = repo.interactionsFor(med)

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                MeViewModel(app.medicationRepository)
            }
        }
    }
}
