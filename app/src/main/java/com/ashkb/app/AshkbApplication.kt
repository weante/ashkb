package com.ashkb.app

import android.app.Application
import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.reminder.NotificationHelper
import com.ashkb.app.reminder.ReminderScheduler
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
                KbEntry(
                    id = o.getString("id"),
                    category = o.getString("category"),
                    title = o.getString("title"),
                    summary = o.getString("summary"),
                    severityLevel = o.getString("severity_level"),
                    applicableScene = o.getString("applicable_scene"),
                    sourceName = o.getString("source_name"),
                    sourceUrl = o.getString("source_url"),
                    sourceTier = o.getString("source_tier"),
                    adaptedAt = o.getString("adapted_at"),
                    reviewDue = o.getString("review_due"),
                    version = o.optInt("version", 1),
                    payload = o.getJSONObject("payload").toString(),
                )
            }
        }.getOrDefault(emptyList())
    }
}
