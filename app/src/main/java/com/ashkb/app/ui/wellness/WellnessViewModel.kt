package com.ashkb.app.ui.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.MedicationRepository
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
class WellnessViewModel(
    private val repo: HealthRepository,
    /** v1.0.38（B11）：药单仓储——补剂「时间错开」提醒与合并时间表需要今日服药时刻 */
    private val medRepo: MedicationRepository,
) : ViewModel() {
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

    /** v1.0.38（B11）：在用药单（已归档过滤），供补剂错开提醒与合并时间表使用 */
    val medications: StateFlow<List<Medication>> = medRepo.observeMedications()
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

    /**
     * v1.0.81（批次 7）：删除整个补剂条目。
     *
     * 语义边界（文案上必须与详情里的逐条删除区分开）：**连带删除它名下的全部服用记录**，
     * 理由与确认框报数口径见 `domain/SupplementDeletion`。
     */
    fun deleteSupplement(id: String) {
        viewModelScope.launch { repo.deleteSupplement(id) }
    }

    /**
     * v1.0.81（批次 7）：删除整个补剂时，确认框要报出的「将一并删除的服用记录条数」。
     *
     * 打开确认框时查一次；失败返回 null，UI 据此**禁用确认按钮**——
     * 宁可让用户再点一次，也不能让他删掉自己还没看清条数的一批记录（与批次 6 的复诊删除同款）。
     */
    suspend fun supplementLogCount(id: String): Int? =
        runCatching { repo.countSupplementLogs(id) }.getOrNull()

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

    /**
     * v1.0.80（批次 6）：撤销今天的补剂打卡（误点）。
     *
     * 删**当天该补剂的全部**打卡行而不是只删第一条：`supplement_logs` 的唯一索引含 `slot_key`，
     * 而补剂打卡的 slot_key 恒为 NULL（SQLite 里 NULL 互不相等），所以连点几次就会留下几行——
     * 只删一行的话，卡片上的「已服用」胶囊不会消失，用户会以为撤销没生效。
     */
    fun undoSupplementCheckIn(supp: Supplement) {
        viewModelScope.launch {
            supplementLogsToday.value
                .filter { it.supId == supp.id }
                .forEach { repo.deleteSupplementLog(it.id) }
        }
    }

    /**
     * v1.0.81（批次 7）：在补剂详情里**只删这一条**服用记录（某一天记错了）。
     *
     * 与 [undoSupplementCheckIn] 的分工：那个只面向「今天」、且要删掉当天的**全部**行
     * （撤销打卡 = 回到未记录态，删一行救不回卡片上的「已服用」胶囊）；这里是用户在详情里
     * 指着某一行点删除，删的就是那一行——即使同一天因连点留下多行，其余行也不是他要删的。
     *
     * 无派生数据：补剂打卡不参与排程与警报，删完不需要重排提醒或重算警报。
     */
    fun deleteSupplementLog(id: String) {
        viewModelScope.launch { repo.deleteSupplementLog(id) }
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
                WellnessViewModel(app.healthRepository, app.medicationRepository)
            }
        }
    }
}
