package com.ashkb.app

import android.app.Application
import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.domain.KbSearch
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

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        appScope.launch {
            // A3：分享产物清理——files/exports 下的明文导出物（档案 JSON / 报告 PDF / 紧急卡 PDF）
            // 启动即清空，防止敏感内容在设备上无限期残留（分享动作本身不受影响）
            runCatching {
                val exports = File(filesDir, "exports")
                if (exports.isDirectory) exports.listFiles()?.forEach { it.delete() }
            }
            importKbSeedIfNeeded(this@AshkbApplication)
            // 启动即重排未来 7 天闹钟（覆盖跨日 / 杀后台遗漏）
            val meds = AppDatabase.get(this@AshkbApplication).medicationDao().observeActive().first()
            ReminderScheduler.rescheduleAll(this@AshkbApplication, meds)
            // P2 例行检查：发作第 7 天警报 + 知识条目复核到期（insertAlertOnce 幂等）
            val today = LocalDate.now()
            healthRepository.checkFlareDayAlert(today)
            healthRepository.checkReviewDue(today.toString())
        }
    }

    /** 首启导入种子：47 条 = itx 15 / exc 15（红10+黑5）/ fdg 6 / emr 5 / edu 6（含阈值 2） */
    private suspend fun importKbSeedIfNeeded(context: Context) {
        val dao = AppDatabase.get(context).kbEntryDao()
        if (dao.count() > 0) return
        val files = listOf(
            "kb_seed_itx.json", "kb_seed_exc.json", "kb_seed_fdg.json",
            "kb_seed_emr.json", "kb_seed_edu.json",
        )
        val entries = files.flatMap { file ->
            parseSeed(context, file)
        }
        if (entries.isNotEmpty()) dao.insertAll(entries)
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
}
