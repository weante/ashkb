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
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.KbSeedRefresh
import com.ashkb.app.reminder.NotificationHelper
import com.ashkb.app.reminder.ReminderScheduler
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AshkbApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                val meds = AppDatabase.get(this@AshkbApplication).medicationDao().observeActive().first()
                ReminderScheduler.rescheduleAll(
                    this@AshkbApplication, meds, medicationRepository.doneSlotRefs(today),
                )
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

    private companion object {
        /**
         * **知识库种子包版本**。每次增补 / 修订种子内容都必须 +1——否则老设备不会重新核对。
         * 1 = 旧实现（首启导入后永不再看）；2 = v1.0.44 起启用增量刷新。
         */
        const val KB_SEED_VERSION = 2

        const val PREFS = "app_prefs"
        const val KEY_SEED_VERSION = "kb_seed_version"

        /** 种子文件清单（47 条 = itx 15 / exc 15（红10+黑5）/ fdg 6 / emr 5 / edu 6（含阈值 2）） */
        val SEED_FILES = listOf(
            "kb_seed_itx.json", "kb_seed_exc.json", "kb_seed_fdg.json",
            "kb_seed_emr.json", "kb_seed_edu.json",
        )
    }
}
