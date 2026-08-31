package com.ashkb.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(
    private val repo: MedicationRepository,
    private val healthRepo: HealthRepository,
) : ViewModel() {

    val date: LocalDate = LocalDate.now()

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val today: StateFlow<List<TodayItem>> =
        repo.observeToday(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** P2：未读警报（红旗三通道 / BASDAI / 发作 / 复核到期） */
    val alerts: StateFlow<List<Alert>> =
        healthRepo.observeUnackedAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日症状是否已记录（false = 未记录，区分实际为 0） */
    val symptomRecorded: StateFlow<Boolean> =
        healthRepo.observeSymptom(date.toString()).map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 今日运动打卡数 */
    val exerciseDone: StateFlow<Int> =
        healthRepo.observeExerciseLogs(date.toString()).map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun checkIn(item: TodayItem, injSite: String? = null, reaction: String = "none") {
        viewModelScope.launch {
            repo.checkIn(item.med, item.slotKey, item.slotTime, reaction, injSite)
        }
    }

    fun skip(item: TodayItem, reason: String, note: String?) {
        viewModelScope.launch {
            repo.skip(item.med, item.slotKey, item.slotTime, reason, note)
        }
    }

    /** 注射顺延：锚点移至新日期（提醒由调用方重排） */
    fun postpone(item: TodayItem, date: LocalDate) {
        viewModelScope.launch {
            repo.postponeInjection(item.med, date)
        }
    }

    /** 打卡或药单变化后重排提醒（写入后取消当晚后续升级） */
    fun reschedule(context: android.content.Context) {
        viewModelScope.launch {
            val meds = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
            ReminderScheduler.rescheduleAll(context, meds)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                TodayViewModel(app.medicationRepository, app.healthRepository)
            }
        }
    }
}
