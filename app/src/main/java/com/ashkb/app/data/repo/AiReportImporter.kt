package com.ashkb.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ashkb.app.R
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.ReportImportBatch
import com.ashkb.app.domain.ImportFingerprint
import com.ashkb.app.domain.ImportResult
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.LabImport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * v1.2.11（缺陷工单 B01 + 结构整理）：AI 报告导入——**同一份报告只写一次**。
 *
 * 为什么从 [HealthRepository] 里搬出来：
 * · 工单 B01 要求「指纹检查 + 批次登记 + 数据行写入必须在同一事务」，这三件事构成一个
 *   完整且独立的职责，混在千行仓库里既读不清也测不开；
 * · `HealthRepository` 因此被 detekt `LargeClass` 判超阈值——它不是「有坏味道」，
 *   而是把「导入一份外部报告」这件事塞进了本来就最大的类里。
 *
 * 判重口径（工单明确要求**不要**按日期+指标+数值全局去重）：
 * 指纹是「报告类型 + 规范化后的完整导入内容」的哈希，见 [ImportFingerprint]。同日同值的
 * 两份**不同**报告（例如两次抽血的肌酐都是 80）指纹不同，都能导入；同一份报告重复拖进来
 * 则整批跳过。
 */
internal class AiReportImporter(
    private val repo: HealthRepository,
    private val context: Context,
) {
    private val db = AppDatabase.get(context)

    private fun nowIso(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * 化验单导入：逐行写入 `lab_results`。
     *
     * 指纹与数据行在同一事务里：查重与写入分开做的话，进程死在中间会留下「行写了一半、
     * 批次没登记」，下次导入又从头写一遍。
     */
    suspend fun lab(import: LabImport): ImportResult = db.withTransaction {
        val fingerprint = ImportFingerprint.lab(import)
        if (db.reportImportBatchDao().countByFingerprint(fingerprint) > 0) {
            return@withTransaction ImportResult.AlreadyImported
        }
        val date = import.date ?: LocalDate.now().toString()
        val note = listOfNotNull(
            import.hospital?.let { context.getString(R.string.ui_import_hospital_prefix, it) },
            import.note,
        ).joinToString(" · ").ifBlank { null }
        import.rows.forEach { row ->
            repo.saveLabResult(
                LabResult(
                    id = "", date = date, recordedAt = nowIso(), backfill = date != LocalDate.now().toString(),
                    checkupId = null, testName = row.testName,
                    value = row.value, valueText = row.valueText,
                    unit = row.unit, refLow = row.refLow, refHigh = row.refHigh,
                    // 两列都写：`aiAbnormal` 留 AI 原值（并列展示），`abnormal` 交给
                    // `saveLabResult` 的本地判读覆盖 / 无参考范围时兜底。
                    abnormal = row.abnormal, aiAbnormal = row.aiAbnormal, notes = note,
                    refLowInclusive = row.refLowInclusive, refHighInclusive = row.refHighInclusive,
                )
            )
        }
        db.reportImportBatchDao().insert(
            ReportImportBatch(
                id = Ids.new("rib"),
                kind = ImportFingerprint.KIND_LAB,
                fingerprint = fingerprint,
                rowCount = import.rows.size,
                importedAt = nowIso(),
            )
        )
        ImportResult.Imported(import.rows.size)
    }

    /**
     * 影像报告导入：一次只落一行，但仍要包事务——批次登记与那一行必须同时成立，
     * 否则重复导入的防护会在「行已写、批次未登记」的窗口里失效。
     */
    suspend fun imaging(import: ImagingImport): ImportResult = db.withTransaction {
        val fingerprint = ImportFingerprint.imaging(import)
        if (db.reportImportBatchDao().countByFingerprint(fingerprint) > 0) {
            return@withTransaction ImportResult.AlreadyImported
        }
        val examDate = import.date ?: LocalDate.now().toString()
        val notesParts = buildList {
            import.compare?.takeIf { it.isNotBlank() && it != "无" }
                ?.let { add(context.getString(R.string.ui_import_compare_prefix, it)) }
        }
        db.imagingDao().upsert(
            ImagingRecord(
                id = Ids.new("img"), examDate = examDate, recordedAt = nowIso(),
                backfill = examDate != LocalDate.now().toString(),
                modality = import.modality, bodyPart = import.bodyPart,
                hospital = import.hospital, findings = import.findings,
                conclusion = import.conclusion,
                notes = notesParts.joinToString("\n").ifBlank { null },
            )
        )
        db.reportImportBatchDao().insert(
            ReportImportBatch(
                id = Ids.new("rib"),
                kind = ImportFingerprint.KIND_IMAGING,
                fingerprint = fingerprint,
                rowCount = 1,
                importedAt = nowIso(),
            )
        )
        ImportResult.Imported(1)
    }
}
