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
import java.time.LocalDate
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
        rescheduleReminders(context)
    }

    fun stopMedication(context: android.content.Context, med: Medication, reason: String, note: String?) =
        viewModelScope.launch {
            repo.stopMedication(med, reason, note)
            // R6 治本：归档≠删除，rescheduleAll 只遍历活跃药、取消不到被停药的未来闹钟——
            // 先单独取消该药全部闹钟（slotsFor 不查归档标志，request code 仍可算对），再重排活跃药
            ReminderScheduler.cancelAllFuture(context, listOf(med))
            rescheduleReminders(context)
        }

    /**
     * 重排提醒。v1.0.43：一并传入「今日已打卡槽位」——`rescheduleAll` 会重建今日未打卡槽位
     * 尚未到时的升级重查，若不传会让已打卡槽位也重建出无用的重查闹钟。
     */
    private suspend fun rescheduleReminders(context: android.content.Context) {
        val list = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
        val today = LocalDate.now()
        val doneRefs = repo.logsForDate(today)
            .filter { it.status == "done" }
            .map { ReminderScheduler.slotRef(it.medId, it.slotKey) }
            .toSet()
        ReminderScheduler.rescheduleAll(context, list, doneRefs)
    }

    /** A3（v1.0.43）：按 id 直接查（不限于「在用」）——编辑页预填用，避免在 meds 流上无限等待 */
    suspend fun medicationById(id: String): Medication? = repo.medicationById(id)

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
