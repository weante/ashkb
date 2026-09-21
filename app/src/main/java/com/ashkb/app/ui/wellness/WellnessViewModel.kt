package com.ashkb.app.ui.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.nowIso
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WellnessViewModel(private val repo: HealthRepository) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value
    private val dateStr: String get() = _date.value.toString()

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

    // ---- 观察 ----
    /** v10（C9）：体重目标区间取自档案，体重卡据此提示是否在区间内 */
    val profile: StateFlow<com.ashkb.app.data.entity.Profile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vitalsToday: StateFlow<Vitals?> = _date.flatMapLatest { repo.observeVitals(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weightToday: StateFlow<WeightLog?> = _date.flatMapLatest { repo.observeWeightToday(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weightRecent: StateFlow<List<WeightLog>> = repo.observeWeightRecent(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyMeasureLatest: StateFlow<BodyMeasure?> = repo.observeBodyMeasureLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supplements: StateFlow<List<Supplement>> = repo.observeSupplements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplementLogsToday: StateFlow<List<SupplementLog>> = _date.flatMapLatest { repo.observeSupplementLogs(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dietProfile: StateFlow<DietProfile?> = repo.observeDietProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val foodAvoidItems: StateFlow<List<FoodAvoidItem>> = repo.observeFoodAvoidAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 体征 ----
    fun saveVitals(
        temperature: Double?,
        bpSys: Int?,
        bpDia: Int?,
        heartRate: Int?,
        notes: String?,
    ) {
        viewModelScope.launch {
            repo.saveVitals(
                Vitals(
                    id = "", date = dateStr, recordedAt = nowIso(),
                    temperature = temperature, bpSys = bpSys, bpDia = bpDia,
                    heartRate = heartRate, notes = notes,
                )
            )
        }
    }

    /** U4 删除今日体征（误录） */
    fun deleteVitalsToday() {
        viewModelScope.launch { vitalsToday.value?.let { repo.deleteVitals(it.id) } }
    }

    // ---- 体重 ----
    fun saveWeight(weightKg: Double, notes: String?) {
        viewModelScope.launch { repo.saveWeight(dateStr, weightKg, notes) }
    }

    /** U4 删除单条体重记录（误录） */
    fun deleteWeight(id: String) {
        viewModelScope.launch { repo.deleteWeight(id) }
    }

    // ---- 身体指标 ----
    fun saveBodyMeasure(heightCm: Double?, waistCm: Double?, hipCm: Double?, bmi: Double?, notes: String?) {
        viewModelScope.launch {
            repo.saveBodyMeasure(
                BodyMeasure(
                    id = "", date = dateStr, recordedAt = nowIso(),
                    heightCm = heightCm, waistCm = waistCm, hipCm = hipCm,
                    bmi = bmi, notes = notes,
                )
            )
        }
    }

    /** U4 删除最新一条身体指标（误录） */
    fun deleteBodyMeasureLatest() {
        viewModelScope.launch { bodyMeasureLatest.value?.let { repo.deleteBodyMeasure(it.id) } }
    }

    // ---- 补剂 ----
    fun saveSupplement(supp: Supplement) {
        viewModelScope.launch { repo.saveSupplement(supp) }
    }

    fun archiveSupplement(id: String) {
        viewModelScope.launch { repo.archiveSupplement(id) }
    }

    fun deleteSupplement(id: String) {
        viewModelScope.launch { repo.deleteSupplement(id) }
    }

    /** U3 单个补剂的服用历史流（近 90 天，仅 done） */
    fun observeSupplementHistory(sup: Supplement) =
        repo.observeSupplementHistory(sup.id, sup.name)

    fun checkInSupplement(supp: Supplement, status: String, reason: String?, notes: String?) {
        viewModelScope.launch {
            repo.checkInSupplement(
                SupplementLog(
                    id = "", date = dateStr, recordedAt = nowIso(),
                    supId = supp.id, supKey = supp.id, supName = supp.name,
                    doseSnapshot = supp.dose, status = status, reason = reason,
                    takenAt = if (status == "done") nowIso() else null,
                    notes = notes,
                )
            )
        }
    }

    // ---- 饮食画像 ----
    fun saveDietProfile(profile: DietProfile) {
        viewModelScope.launch { repo.saveDietProfile(profile) }
    }

    /** U4 清除饮食画像（回到未设置态） */
    fun deleteDietProfile() {
        viewModelScope.launch { repo.deleteDietProfile() }
    }

    // ---- 忌口清单 ----
    fun saveFoodAvoid(item: FoodAvoidItem) {
        viewModelScope.launch { repo.saveFoodAvoid(item) }
    }

    fun deleteFoodAvoid(id: String) {
        viewModelScope.launch { repo.deleteFoodAvoid(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                WellnessViewModel(app.healthRepository)
            }
        }
    }
}
