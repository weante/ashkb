package com.ashkb.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.AttachmentRepository
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.data.repo.RecipeRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.DateProvider
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.KbSeedRefresh
import com.ashkb.app.reminder.BasdaiReminderScheduler
import com.ashkb.app.reminder.CheckupReminderScheduler
import com.ashkb.app.reminder.EmergencyLockscreenPublisher
import com.ashkb.app.reminder.ExerciseReminderScheduler
import com.ashkb.app.reminder.MissedDoseReminder
import com.ashkb.app.reminder.NotificationHelper
import com.ashkb.app.reminder.ReminderScheduler
import com.ashkb.app.reminder.SedentaryReminderScheduler
import com.ashkb.app.reminder.SupplementReminderScheduler
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AshkbApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * v1.0.86（批次 11）：全应用唯一的「今天」来源。
     *
     * 进程级单例：7 个 ViewModel 共用同一个日期流，跨零点只算一次、只通知一次；深睡不可靠的
     * 协程 ticker 被系统日期变更广播取代（设计与理由见 [DateProvider]）。用 [appScope] 而不是
     * 各 VM 自己的 scope——日期本身的寿命与进程相同。
     */
    val dateProvider = DateProvider(appScope)

    val medicationRepository: MedicationRepository by lazy { MedicationRepository(this) }
    val healthRepository: HealthRepository by lazy { HealthRepository(this) }
    val backupRepository: BackupRepository by lazy { BackupRepository(this) }
    val reportRepository: ReportRepository by lazy { ReportRepository(this) }
    /** v10（B10）：复诊附件归档（拍照 / 相册 / PDF） */
    val attachmentRepository: AttachmentRepository by lazy { AttachmentRepository(this) }
    /** v1.0.39（B3）：推荐食谱库 */
    val recipeRepository: RecipeRepository by lazy { RecipeRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // v1.0.40：先装崩溃留档（本地文件 + Logcat）——任何未捕获异常都留痕，便于定位
        CrashLogger.install(this)
        NotificationHelper.ensureChannels(this)
        // v1.0.86（批次 11）：日期变更广播「注册即生效」——注册动作在 onCreate 同步完成，
        // 不等启动期那批 IO（下面 appScope.launch 里的例行工作），否则冷启动恰好跨零点时，
        // 页面可能先读到还没校正的旧日期。
        dateProvider.start(this)
        appScope.launch {
            // v1.0.40：启动期例行工作**整体兜底**。这里的异常发生在协程内（appScope 无
            // CoroutineExceptionHandler），会直接冒泡到线程未捕获处理器并杀进程——必须显式捕获，
            // 否则任何一步失败都会表现为「冷启动闪退」。
            runCatching {
                // A3：分享产物清理——files/exports 下的明文导出物（档案 JSON / 报告 PDF / 紧急卡 PDF）
                // 启动即清空，防止敏感内容在设备上无限期残留（分享动作本身不受影响）
                runCatching {
                    val exports = File(filesDir, "exports")
                    if (exports.isDirectory) exports.listFiles()?.forEach { it.delete() }
                }
                importKbSeedIfNeeded(this@AshkbApplication)
                // v1.0.39：B3 食谱种子（10 条）+ B7 康复计划模板（4/8/12 周）——均按固定 id 幂等，
                // 重复启动不会重复种入；用户自建内容不受影响
                recipeRepository.seedIfMissing()
                healthRepository.seedExercisePlans()
                // 启动即重排未来 7 天闹钟（覆盖跨日 / 杀后台遗漏）
                // v1.0.43：带上「今日已打卡槽位」——rescheduleAll 会重建今日未打卡槽位尚未到时的
                // 升级重查，避免每次冷启动都清掉当天的 +30 / +60 提醒
                val today = LocalDate.now()
                val now = LocalDateTime.now()
                val db = AppDatabase.get(this@AshkbApplication)
                val meds = db.medicationDao().observeActive().first()
                ReminderScheduler.rescheduleAll(
                    this@AshkbApplication, meds, medicationRepository.doneSlotRefs(today),
                )
                // v1.0.77（批次 3b）：**与提醒同批**物化计划槽位快照（今天-1 .. 今天+7）。
                // 挂在这个位置而不是 ReminderScheduler 里：那是提醒层，不该反向依赖 DAO；
                // 而窗口与提醒一致，是为了让「有提醒可发」与「有计划可评判」永远同时成立。
                // 单独 runCatching：物化失败不该连带下面的提醒链与补发通知一起停摆。
                runCatching {
                    val win = medicationRepository.plannedSlotWindow(today)
                    medicationRepository.materializePlannedSlots(meds, win.from, win.to)
                    // 昨天有未记录的剂量 → 一条汇总通知（每天最多一条，判定与去重见 MissedDoseReminder）
                    MissedDoseReminder.checkAndNotify(this@AshkbApplication, today)
                }.onFailure {
                    Log.w("ASHKB", "materialize planned slots failed", it)
                }
                // v1.0.59 B5：三源提醒——各自 runCatching 兜底，互不影响（与 BootReceiver 对称）
                val cfg = ReminderConfigRepository(this@AshkbApplication)
                if (cfg.checkupEnabled()) {
                    runCatching {
                        CheckupReminderScheduler.rescheduleAll(
                            this@AshkbApplication, db.checkupRecordDao().listAll(), today, now,
                        )
                    }
                } else {
                    CheckupReminderScheduler.cancelAllFuture(
                        this@AshkbApplication, db.checkupRecordDao().listAll(),
                    )
                }
                runCatching {
                    BasdaiReminderScheduler.rescheduleAll(
                        this@AshkbApplication, db.basdaiDao().latest(), cfg.basdaiCycleDays(), today, now,
                    )
                }
                if (cfg.exerciseEnabled()) {
                    runCatching {
                        val activePlan = db.exercisePlanDao().active()
                        val hasLogged = db.exerciseLogDao().byDate(today.toString()).isNotEmpty()
                        ExerciseReminderScheduler.rescheduleAll(
                            this@AshkbApplication, activePlan, hasLogged, today, now,
                        )
                    }
                } else {
                    ExerciseReminderScheduler.cancelAllFuture(this@AshkbApplication, today)
                }
                // v1.2.4：补剂提醒——与用药/复诊/BASDAI/运动同批重排。
                // 补剂没有独立开关：频次是「每日」且填了时刻才排得出闹钟，没填时刻自然一条也不排
                // （开关的语义在「有没有时刻」里，不需要第二个状态）。
                runCatching {
                    val sups = db.supplementDao().listActive()
                    val loggedSups = db.supplementLogDao().byDate(today.toString())
                        .mapNotNull { it.supId }.toSet()
                    SupplementReminderScheduler.rescheduleAll(
                        this@AshkbApplication, sups, loggedSups, today, now,
                    )
                }
                // v1.0.66 B6a：锁屏紧急信息——启动时同步一次（兜住"改完数据后没进紧急卡页"的情况）；
                // 开关关闭时该调用内部会撤下通知，故无需外层判断
                runCatching { EmergencyLockscreenPublisher.refresh(this@AshkbApplication) }
                // v1.0.68 C8a：久坐起身提醒——链式单发，启动时重排一次（开关关闭则不排）
                runCatching { SedentaryReminderScheduler.rescheduleAll(this@AshkbApplication, now) }
                // P2 例行检查：发作第 7 天警报 + 知识条目复核到期（insertAlertOnce 幂等）
                healthRepository.checkFlareDayAlert(today)
                healthRepository.checkReviewDue(today.toString())
            }.onFailure {
                // v1.0.41：不能只写 Logcat——v1.0.39/40 的根因正是「首次失败被这里吞掉」，
                // 后续二次触碰才抛 NoClassDefFoundError，导致崩溃留档只看到二次现象。同步留档。
                Log.w("ASHKB", "startup task failed", it)
                CrashLogger.recordNonFatal(this@AshkbApplication, "启动期例行工作", it)
            }
        }
    }

    /**
     * v1.0.86（批次 11）：反注册日期变更广播。
     *
     * 真实设备上进程结束不会走这里（`onTerminate` 只在模拟进程里调用），但那条广播接收器是
     * 应用级的——注册一次、随进程消亡，不构成泄漏；这里补上是为了「注册 / 反注册成对」，
     * 也方便将来进程复用的场景（如多进程 or 测试宿主）。
     */
    override fun onTerminate() {
        dateProvider.stop(this)
        super.onTerminate()
    }

    /**
     * 知识库种子导入 / **增量刷新**（v1.0.44 重写）。
     *
     * 旧实现是 `if (dao.count() > 0) return`——库里只要有任意一条就整体跳过。后果是
     * **任何早于首次导入的设备，此后所有种子增补都永远进不来**（v1.0.20 以来知识条目多次扩充），
     * 而且完全静默、无任何报错。属结构性数据缺口：用户基数越大越难补。
     *
     * 新实现：
     * 1. `KB_SEED_VERSION` 闸门——只在种子包版本提升时做一次核对，不必每次冷启动解析 5 个 JSON；
     * 2. 按 id 比对：缺失的**补入**（固定 id + `INSERT IGNORE`，幂等）；
     * 3. 内容被修订的**更新种子列**，并把 `user_note` 从旧行拷回——
     *    个人备注层永不因种子更新被覆盖（v10「只读种子层 + 个人备注层」双层结构的承诺）。
     */
    private suspend fun importKbSeedIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SEED_VERSION, 0) >= KB_SEED_VERSION) return

        val dao = AppDatabase.get(context).kbEntryDao()
        val existing = dao.listAll().associateBy { it.id }
        val seeds = SEED_FILES.flatMap { parseSeed(context, it) }
        // 解析整体失败（assets 缺失 / JSON 损坏）时**不写闸门**，留给下次启动重试——
        // 否则一次瞬时失败会让种子永久停在旧版本，且无从察觉。
        if (seeds.isEmpty()) return

        // 判定逻辑在 domain/KbSeedRefresh（纯函数，可单测）；这里只负责执行
        val plan = KbSeedRefresh.plan(existing, seeds)
        if (plan.fresh.isNotEmpty()) dao.insertAll(plan.fresh)
        if (plan.revised.isNotEmpty()) dao.updateAll(plan.revised)
        prefs.edit().putInt(KEY_SEED_VERSION, KB_SEED_VERSION).apply()
    }

    private fun parseSeed(context: Context, file: String): List<KbEntry> {
        val json = runCatching {
            context.assets.open(file).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                if (o.getString("id").isBlank()) return@mapNotNull null
                val title = o.getString("title")
                val summary = o.getString("summary")
                val payload = o.getJSONObject("payload").toString()
                KbEntry(
                    id = o.getString("id"),
                    category = o.getString("category"),
                    title = title,
                    summary = summary,
                    severityLevel = o.getString("severity_level"),
                    applicableScene = o.getString("applicable_scene"),
                    sourceName = o.getString("source_name"),
                    sourceUrl = o.getString("source_url"),
                    sourceTier = o.getString("source_tier"),
                    adaptedAt = o.getString("adapted_at"),
                    reviewDue = o.getString("review_due"),
                    version = o.optInt("version", 1),
                    payload = payload,
                    // v9：检索列与迁移 v8→v9 / KbSearch.searchText 同一口径
                    searchText = KbSearch.searchText(title, summary, payload),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 内部可见（不是 `private`）：`KbSeedVersionGateTest` 要读它，把「改种子必须 bump 版本」
     * 这条约定变成一条会红的断言（第四份审查报告 §九）。其余常量仍私有。
     */
    internal companion object {
        /**
         * **知识库种子包版本**。每次增补 / 修订种子内容都必须 +1——否则老设备不会重新核对。
         * 1 = 旧实现（首启导入后永不再看）；2 = v1.0.44 起启用增量刷新；
         * 3 = v1.1.2（批次 18）**补闸**——把闸门补开到「当前内容」；
         * 4 = v1.1.2 修订 `exc-004` 的来源链接（站点更名致原 PDF 404）并同步机构名。
         * 5 = v1.1.3（批次 19）落实维护者的医学内容裁决：`itx-002` 由品牌键改为
         *     TNF 类级键（标题与 `source_name` 同步去掉品牌泛化）、`itx-012` 按 ACR 2022
         *     原文拆成两条事实、`exc-001`/`exc-003` 补「诚实口径」说明、
         *     `exb-004`/`fdg-004` 商业来源降 S4、`edu-th-001`/`edu-th-002` 无来源条目改标 SYS、
         *     `edu-th-002` 文案与 v1.1.2 已实现的行为对齐。五个种子文件全部有改动。
         *
         * 为什么要补这一跳（3 那一跳）：`git log` 显示 v1.0.70 往 `kb_seed_edu.json` 加了 `edu-005`、
         * 另一次提交改写了 `exc-004` 的文案，两次都**没有**动这个常量。
         * 而 `importKbSeedIfNeeded` 见到 `prefs >= 2` 就直接 return ——
         * 于是这批已安装用户至今没跑过那一次核对：`edu-005` 从未进过他们的库，
         * `exc-004` 仍是旧文案（第四份审查报告 §九 预言的失败模式，仓库里已经真实发生过）。
         * 补到 3 后他们会在下次冷启动时被重新核对一次（`KbSeedRefresh` 逐条补入 / 修订，
         * `user_note` 照旧保留）。
         *
         * 4 = 「`exc-004` 的 `source_url` 指向已下线的 Versus Arthritis PDF」：原文链接现在
         * 302 到 `/error/404`，患者点「查看原文」只会看到一个错误页。改指 Internet Archive 上
         * **同一份 PDF** 的存档（内容与链接一一对应，见 `KbSeedVersionGateTest` 的指纹登记）。
         *
         * ⚠️ 今后 bump 之后还必须同步 `KbSeedVersionGateTest.FINGERPRINTS`（那条断言会先红，
         * 提醒你登记新指纹）——两者是同一件事的两半，漏一个都会让已安装用户永久停在旧内容。
         */
        internal const val KB_SEED_VERSION = 12

        private const val PREFS = "app_prefs"
        private const val KEY_SEED_VERSION = "kb_seed_version"

        /**
         * 种子文件清单（48 条 = itx 15 / exc 15（红10+黑5）/ fdg 6 / emr 5 / edu 7（含阈值 2））。
         *
         * 内部可见（不是 `private`）：`KbSeedVersionGateTest` 的内容指纹**必须覆盖同一份清单**，
         * 否则「少改一个文件 / 改了一个没登记的文件」就能绕过那道门——这正是它要防的事。
         */
        internal val SEED_FILES = listOf(
            "kb_seed_itx.json", "kb_seed_exc.json", "kb_seed_fdg.json",
            "kb_seed_emr.json", "kb_seed_edu.json",
        )
    }
}
