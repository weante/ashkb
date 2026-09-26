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
import com.ashkb.app.domain.ExerciseEngine
import com.ashkb.app.domain.Lifestyle
import com.ashkb.app.domain.LifestylePrescription
import com.ashkb.app.reminder.ExerciseReminderScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    /** v1.0.64 B13：生活方式画像驱动的个性化提示（不改处方本身，只随处方展示）。 */
    val lifestyleNotes: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(
    private val repo: HealthRepository,
    private val app: AshkbApplication,
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

    private val library = MutableStateFlow<List<KbEntry>>(emptyList())
    private val feedbackRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch { library.value = repo.exerciseLibrary() }
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
                _date.value = LocalDate.now()
            }
        }
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
        _date.flatMapLatest { repo.observeExerciseLogs(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 昨日症状（疼痛 / 晨僵 / 体温）——处方 hero 的判读依据 */
    val yesterdaySymptom: StateFlow<com.ashkb.app.data.entity.SymptomDaily?> =
        _date.flatMapLatest { repo.observeSymptom(it.minusDays(1).toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R21：昨日已完成但未反馈的打卡（combine 取昨日日期，单层 flatMapLatest 更直白） */
    val feedbackPending: StateFlow<List<ExerciseLog>> =
        combine(_date, feedbackRefresh) { d, _ -> d.minusDays(1).toString() }
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
                ExerciseViewModel(app.healthRepository, app)
            }
        }
    }
}
