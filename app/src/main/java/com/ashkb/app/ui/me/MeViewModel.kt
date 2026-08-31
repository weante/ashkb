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
