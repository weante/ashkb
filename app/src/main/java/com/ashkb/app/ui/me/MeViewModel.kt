package com.ashkb.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
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
        // v1.0.73（P1-17）：整段移出主线程——重排是「药 × 槽 × 3 次 binder」量级
        // （约 8 天窗口），在保存药物/停药这条热路径上会造成可感知卡顿。
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
            val today = LocalDate.now()
            ReminderScheduler.rescheduleAll(context, list, repo.doneSlotRefs(today))
            // v1.0.77（批次 3b）：与提醒同批物化计划槽位快照。改药单（改时刻 / 换频次 / 新增）与
            // 停药都走这里，新的计划从今天起就能被计划口径的完成度看见（停药时另有清理，见 repo）。
            val win = repo.plannedSlotWindow(today)
            repo.materializePlannedSlots(list, win.from, win.to)
        }
    }

    /** A3（v1.0.43）：按 id 直接查（不限于「在用」）——编辑页预填用，避免在 meds 流上无限等待 */
    suspend fun medicationById(id: String): Medication? = repo.medicationById(id)

    /** v1.0.48：某条药的用药记录流（近 90 天），药单点开查看流水 */
    fun observeLogsForMed(medId: String): Flow<List<MedicationLog>> = repo.observeLogsForMed(medId)

    /**
     * v1.0.77（批次 3b）：某条药近 [days] 天的**计划槽位快照**（弹层里与日志配对算计划口径完成度）。
     *
     * 与 [observeLogsForMed] 同为 90 天窗口、**同一个闭区间**（`[今天-(days-1), 今天]`）——
     * 两个列表窗口若不重合，算出来的「未记录」会凭空多出几天。
     *
     * 已滤掉**今天还没到点**的槽位（[AdherenceCalc.isDue]）：否则每天上午的完成度都会因为
     * 「晚上那剂还没到点」而假性掉一截，随当天陆续打卡再爬回来——那是噪声，不是依从率。
     */
    suspend fun plannedSlotsForMed(medId: String, days: Int): List<PlannedSlot> {
        val to = LocalDate.now()
        val now = LocalDateTime.now()
        return repo.plannedSlotsForMed(medId, to.minusDays((days - 1).toLong()), to)
            .filter { AdherenceCalc.isDue(it, now) }
    }

    /** v1.0.49：手动修正某条用药记录（药单「用药记录」弹层里改） */
    fun updateLog(log: MedicationLog, status: String, reason: String?, injSite: String?, notes: String?) =
        viewModelScope.launch { repo.updateLog(log, status, reason, injSite, notes) }

    /** v1.0.48：已停用药品（含停药原因 / 生效日 / 备注），药单底部「已停用药品」折叠区 */
    val archivedMeds: StateFlow<List<MedicationRepository.ArchivedMedication>> =
        repo.observeArchivedMedications()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * v1.0.71：删除已停用药品（连带其打卡记录与变更记录）。
     *
     * 只在「已停用药品」折叠区提供入口；repo 侧对「仍在用」的药直接拒绝并**不做任何改动**
     * （返回 null），故这里无需再判一次。
     */
    fun deleteArchivedMedication(id: String) {
        viewModelScope.launch { repo.deleteArchivedMedication(id) }
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
