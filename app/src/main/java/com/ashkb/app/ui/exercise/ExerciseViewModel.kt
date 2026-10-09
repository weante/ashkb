package com.ashkb.app.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.DateProvider
import com.ashkb.app.domain.ExerciseEngine
import com.ashkb.app.domain.Lifestyle
import com.ashkb.app.domain.LifestylePrescription
import com.ashkb.app.domain.MorningWarmup
import com.ashkb.app.reminder.ExerciseReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** M4 运动模块 UI 状态：当日处方（红榜）+ 黑榜拦截区 + 颈椎受累标志 */
data class ExerciseUiState(
    val plan: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val blocked: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val stage: String = "unknown",
    val cervicalInvolved: Boolean = false,
    /** v1.0.64 B13：生活方式画像驱动的个性化提示（不改处方本身，只随处方展示）。i18n：`ResText` 由 UI 落地。 */
    val lifestyleNotes: List<com.ashkb.app.domain.ResText> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(
    private val repo: HealthRepository,
    private val app: AshkbApplication,
    /** v1.0.86（批次 11）：全应用唯一的「今天」来源，取代此前的跨零点 ticker。 */
    private val dateProvider: DateProvider,
) : ViewModel() {

    /**
     * 语义与批次 10 完全一致：**今天**（今日打卡流、昨日症状 / 反馈都相对它取窗口）。
     *
     * v1.0.86（批次 11）：底层改为进程级日期流（系统跨日广播驱动）——原 ticker 走
     * `Handler.postDelayed`（uptimeMillis，深睡不计时），夜里跨零点不触发。
     */
    private val date: LocalDate get() = dateProvider.today.value

    /**
     * v1.2.7（批次 14 / R8）：给知识卡详情弹窗用。
     *
     * 弹窗里的「已过期」小胶囊原先自己读系统时钟，与页面顶部日期可能差一天（跨零点前后）。
     * 现在一律用这里注入的日期源——语义与 `SymptomViewModel.today` 一致。
     */
    val today: LocalDate get() = dateProvider.today.value

    private val library = MutableStateFlow<List<KbEntry>>(emptyList())
    private val feedbackRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch { library.value = repo.exerciseLibrary() }
    }

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R27 矩阵：分期切换（active↔stable）当日处方即时重算 */
    // todayPlan 含 filter + sort，KB 扩容后放 Default 调度器避免阻塞主线程
    val uiState: StateFlow<ExerciseUiState> =
        combine(library, profile) { lib, p ->
            val (plan, blocked) = ExerciseEngine.todayPlan(lib, p?.diseaseStage, p?.spineMobility)
            ExerciseUiState(
                plan = plan, blocked = blocked,
                stage = p?.diseaseStage ?: "unknown",
                cervicalInvolved = ExerciseEngine.cervicalInvolved(p?.spineMobility),
                // v1.0.64 B13：生活方式 → 个性化提示（纯函数；未登记则为空列表）
                lifestyleNotes = LifestylePrescription.advice(Lifestyle.fromJson(p?.lifestyle)),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseUiState())

    val todayLogs: StateFlow<List<ExerciseLog>> =
        dateProvider.today.flatMapLatest { repo.observeExerciseLogs(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 昨日症状（疼痛 / 晨僵 / 体温）——处方 hero 的判读依据 */
    val yesterdaySymptom: StateFlow<com.ashkb.app.data.entity.SymptomDaily?> =
        dateProvider.today.flatMapLatest { repo.observeSymptom(it.minusDays(1).toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * v1.0.69 C8b：晨僵时长驱动的**起床热身序列**——把此前只当判读依据展示的晨僵接进处方。
     * 动作取当日处方里的 L1 轻柔项，本流只决定「要不要提示、提示什么」。
     */
    val warmup: StateFlow<MorningWarmup.Sequence?> =
        combine(uiState, yesterdaySymptom) { ui, y ->
            MorningWarmup.build(y?.morningStiffnessMin, ui.plan)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R21：昨日已完成但未反馈的打卡（combine 取昨日日期，单层 flatMapLatest 更直白） */
    val feedbackPending: StateFlow<List<ExerciseLog>> =
        combine(dateProvider.today, feedbackRefresh) { d, _ -> d.minusDays(1).toString() }
            .flatMapLatest { yesterday -> flow { emit(repo.pendingFeedbackLogs(yesterday)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** M4 打卡：写入当日 exercise_logs（exc 快照字段齐备，可自持） */
    fun checkIn(card: ExerciseEngine.ExerciseCard, durationMin: Int?, intensity: String?, notes: String?) {
        viewModelScope.launch {
            repo.checkInExercise(
                ExerciseLog(
                    id = Ids.new("elog"), date = date.toString(), recordedAt = nowIso(),
                    excId = card.entry.id, excKey = card.entry.id, excName = card.entry.title,
                    grade = card.grade, durationMin = durationMin, intensity = intensity,
                    status = "done", notes = notes,
                )
            )
            // v1.0.59 B5：运动打卡后即时重排提醒（今日已完成 → slotsFor 返回空，自然清掉今日升级）
            rescheduleExerciseReminders()
        }
    }

    /** v1.0.59 B5：重排运动提醒（按当前 active plan + 今日打卡状态） */
    private suspend fun rescheduleExerciseReminders() {
        val cfg = ReminderConfigRepository(app)
        val db = com.ashkb.app.data.db.AppDatabase.get(app)
        if (cfg.exerciseEnabled()) {
            runCatching {
                val activePlan = db.exercisePlanDao().active()
                val hasLogged = db.exerciseLogDao().byDate(LocalDate.now().toString()).isNotEmpty()
                ExerciseReminderScheduler.rescheduleAll(
                    app, activePlan, hasLogged, LocalDate.now(), LocalDateTime.now(),
                )
            }
        } else {
            ExerciseReminderScheduler.cancelAllFuture(app, LocalDate.now())
        }
    }

    /**
     * v1.0.80（批次 6）：修改一条运动打卡（时长 / 强度 / 备注）。
     *
     * 只改内容，不动日期与动作快照（理由见 `ExerciseLogEditSheet`）。
     * 改完仍走一次提醒重排：完成度口径与提醒同源，保持在同一条收尾路径上（幂等，代价只有几条闹钟读写）。
     */
    fun updateLog(log: ExerciseLog, durationMin: Int?, intensity: String?, notes: String?) {
        viewModelScope.launch {
            repo.checkInExercise(
                log.copy(durationMin = durationMin, intensity = intensity, notes = notes)
            )
            rescheduleExerciseReminders()
        }
    }

    /**
     * v1.0.80（批次 6）：删除一条运动打卡（误录）。
     *
     * **必须重排提醒**：运动提醒的排程直接以「今天有没有打卡」为输入（`ExerciseReminderScheduler`
     * 的 `hasLogged`）——删掉今天唯一一条打卡后，今日提醒应当重新出现；不重排的话，
     * 用户今天再也收不到提醒，而他刚刚才把那条误录的打卡删掉。
     */
    fun deleteLog(log: ExerciseLog) {
        viewModelScope.launch {
            repo.deleteExerciseLog(log.id)
            rescheduleExerciseReminders()
        }
    }

    fun saveFeedback(
        logId: String,
        painChange: String?,
        stiffnessChange: String?,
        isMuscleSoreness: Boolean?,
        note: String?,
    ) {
        viewModelScope.launch {
            repo.saveExerciseFeedback(logId, painChange, stiffnessChange, isMuscleSoreness, note)
            feedbackRefresh.value += 1
        }
    }

    /** v10（B2）：运动处方里点开的运动知识条目也能写个人备注（与知识库同一列） */
    fun saveKbNote(id: String, note: String?) {
        viewModelScope.launch { repo.saveKbNote(id, note) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                ExerciseViewModel(app.healthRepository, app, app.dateProvider)
            }
        }
    }
}
