package com.ashkb.app.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ashkb.app.R
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.domain.Labels
import java.io.File
import java.time.LocalDate

/**
 * P4 PDF 生成（M9 复诊报告 / M7 紧急卡打印版）——android.graphics.pdf 零依赖实现。
 * A4 纵向（595×842pt），行游标自动分页；中文使用系统默认字体。
 */
object ReportPdfWriter {

    private const val PW = 595   // A4 宽（pt）
    private const val PH = 842   // A4 高（pt）
    private const val M = 48f    // 页边距

    class Doc(
        private val context: Context,
        private val title: String,
        private val footer: String
    ) {
        val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private var pageNo = 0

        private val titlePaint = Paint().apply {
            color = Color.rgb(30, 30, 34); textSize = 22f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val h2Paint = Paint().apply {
            color = Color.rgb(60, 80, 160); textSize = 14f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val textPaint = Paint().apply {
            color = Color.rgb(40, 40, 44); textSize = 11f; isAntiAlias = true
        }
        private val boldPaint = Paint().apply {
            color = Color.rgb(20, 20, 24); textSize = 11f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val smallPaint = Paint().apply {
            color = Color.rgb(110, 110, 118); textSize = 9f; isAntiAlias = true
        }
        private val linePaint = Paint().apply {
            color = Color.rgb(210, 214, 224); strokeWidth = 1f
        }
        private val warnPaint = Paint().apply {
            color = Color.rgb(180, 40, 40); textSize = 11f; isAntiAlias = true
        }
        private val bigPaint = Paint().apply {
            color = Color.rgb(30, 30, 34); textSize = 16f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private fun newPage() {
            finishPage()
            pageNo++
            val info = PdfDocument.PageInfo.Builder(PW, PH, pageNo).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = M
            if (pageNo == 1) {
                canvas!!.drawText(title, M, y + 20f, titlePaint)
                y += 40f
                canvas!!.drawLine(M, y, PW - M, y, linePaint)
                y += 18f
            } else {
                canvas!!.drawText(title, M, y + 8f, smallPaint)
                canvas!!.drawText(context.getString(R.string.pdf_page_no, pageNo), PW - M - 40f, y + 8f, smallPaint)
                y += 20f
            }
        }

        private fun finishPage() {
            page?.let { p ->
                canvas!!.drawText(
                    context.getString(R.string.pdf_page_footer, pageNo, footer),
                    M, PH - 22f, smallPaint
                )
                doc.finishPage(p)
            }
            page = null
        }

        private fun ensure(space: Float) {
            if (canvas == null || y + space > PH - 50f) newPage()
        }

        fun h2(text: String) {
            ensure(34f)
            y += 6f
            canvas!!.drawText(text, M, y + 12f, h2Paint)
            y += 18f
            canvas!!.drawLine(M, y, PW - M, y, linePaint)
            y += 8f
        }

        fun kv(label: String, value: String, warn: Boolean = false) {
            ensure(16f)
            val p = if (warn) warnPaint else textPaint
            canvas!!.drawText(label, M, y + 10f, boldPaint)
            canvas!!.drawText(value, M + 110f, y + 10f, p)
            y += 16f
        }

        fun line(text: String, warn: Boolean = false) {
            val p = if (warn) warnPaint else textPaint
            // 简单折行：按字符宽度估算（中文 11pt ≈ 11 宽，可用宽度 ~499）
            val maxChars = ((PW - 2 * M) / 10.5f).toInt()
            var rest = text
            while (rest.isNotEmpty()) {
                ensure(16f)
                val cut = if (rest.length > maxChars) rest.substring(0, maxChars) else rest
                canvas!!.drawText(cut, M, y + 10f, p)
                y += 16f
                rest = if (rest.length > maxChars) rest.substring(maxChars) else ""
            }
        }

        fun bigLine(text: String) {
            ensure(24f)
            canvas!!.drawText(text, M, y + 14f, bigPaint)
            y += 24f
        }

        fun gap(h: Float = 8f) { y += h }

        fun close(target: File) {
            finishPage()
            target.parentFile?.mkdirs()
            target.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }

    // ======================= 复诊报告（M9） =======================

    fun writeCheckupReport(context: Context, r: ReportRepository.CheckupReport): File {
        val today = LocalDate.now().toString()
        val d = Doc(
            context,
            context.getString(R.string.pdf_checkup_title),
            context.getString(R.string.pdf_checkup_footer, today)
        )

        d.h2(context.getString(R.string.pdf_section_basic_info))
        val p = r.profile
        if (p == null) d.line(context.getString(R.string.pdf_no_profile_full))
        else {
            d.kv(context.getString(R.string.pdf_label_name), p.displayName)
            d.kv(context.getString(R.string.pdf_label_diagnosis), p.diagnosis)
            d.kv(
                context.getString(R.string.pdf_label_diagnose_year),
                p.diagnoseYear?.toString() ?: context.getString(R.string.pdf_value_not_filled)
            )
            d.kv("HLA-B27", Labels.hlaB27(p.hlaB27))
            d.kv(context.getString(R.string.pdf_label_disease_stage), stage(context, p.diseaseStage))
            p.allergies?.let { d.kv(context.getString(R.string.pdf_label_allergies), it) }
            p.emergencyBloodType?.let { d.kv(context.getString(R.string.pdf_label_blood_type), it) }
        }

        d.h2(context.getString(R.string.pdf_section_meds, r.meds.size))
        if (r.meds.isEmpty()) d.line(context.getString(R.string.pdf_no_meds))
        r.meds.forEach { m ->
            // 注：注射周期段必须先落成局部值再接续拼接——`x + y + z?.let{} ?: ""` 会因 `+`
            // 优先级高于 `?:` 而解析为 `(x + y + z?.let{}) ?: ""`，口服药（injCycleDays 为 null）
            // 会把字面量 "null" 拼进 PDF（曾出现「…｜口服null」）
            val injCycle = m.injCycleDays?.let { "｜" + context.getString(R.string.pdf_med_inj_cycle, it) } ?: ""
            d.line("· ${m.name}${m.brandName?.let { "（$it）" } ?: ""}｜${m.dose}｜${freq(context, m.frequency)}" +
                "｜${if (m.route == "injection") context.getString(R.string.pdf_route_injection) else context.getString(R.string.pdf_route_oral)}" +
                injCycle)
        }

        d.h2(context.getString(R.string.pdf_section_adherence))
        val o = r.overview
        d.kv(
            context.getString(R.string.pdf_label_med_checkin),
            context.getString(
                R.string.pdf_value_med_checkin,
                o.adherence.medDone, o.adherence.medPartial, o.adherence.medSkipped
            )
        )
        d.kv(context.getString(R.string.pdf_label_adherence_rate), "${o.adherence.medRatePct}%")
        d.kv(
            context.getString(R.string.pdf_label_exercise),
            context.getString(
                R.string.pdf_value_exercise,
                o.exercise.doneCount, o.exercise.totalMinutes, o.exercise.skippedCount
            )
        )
        d.kv(context.getString(R.string.pdf_label_symptom_days), "${o.symptom.daysRecorded} / 30")
        d.kv(
            context.getString(R.string.pdf_label_avg_pain),
            o.symptom.avgPain?.let { "%.1f / 10".format(it) }
                ?: context.getString(R.string.pdf_value_not_recorded)
        )
        d.kv(
            context.getString(R.string.pdf_label_avg_stiffness),
            o.symptom.avgStiffnessMin?.let {
                context.getString(R.string.pdf_value_minutes, "%.0f".format(it))
            } ?: context.getString(R.string.pdf_value_not_recorded)
        )
        d.kv(context.getString(R.string.pdf_label_night_pain_days), "${o.symptom.nightPainDays}")
        d.kv(
            context.getString(R.string.pdf_label_avg_fatigue),
            o.symptom.avgFatigue?.let { "%.1f / 10".format(it) }
                ?: context.getString(R.string.pdf_value_not_recorded)
        )
        d.kv(
            context.getString(R.string.pdf_label_eye_days),
            context.getString(R.string.pdf_value_eye_days, o.symptom.eyeDays),
            o.symptom.eyeDays > 0
        )
        d.kv(context.getString(R.string.pdf_label_fever_days), "${o.symptom.feverDays}")
        d.kv(
            context.getString(R.string.pdf_label_flare_count),
            "${o.flareCount}" + if (o.flareActive) context.getString(R.string.pdf_value_flare_active) else ""
        )

        d.h2(context.getString(R.string.pdf_section_basdai, r.basdaiHistory.size))
        val bas = r.basdaiHistory
        if (bas.isEmpty()) d.line(context.getString(R.string.pdf_no_basdai))
        else {
            val avg = bas.map { it.total }.average()
            d.kv(
                context.getString(R.string.pdf_label_latest),
                context.getString(R.string.pdf_value_latest, bas.last().date, "%.1f".format(bas.last().total))
            )
            d.kv(context.getString(R.string.pdf_label_average), "%.1f / 10".format(avg))
            o.basdaiDelta?.let { d.kv(context.getString(R.string.pdf_label_change), "%+.1f".format(it)) }
            if (bas.size > 1) {
                bas.takeLast(8).forEach {
                    d.line("  ${it.date}  ${"%.1f".format(it.total)}" +
                        if (it.total >= 4.0) "  " + context.getString(R.string.pdf_basdai_high) else "")
                }
            }
        }

        // U8：复诊报告只列异常项（医生关注点），正常项仅计数
        val abnormal = r.labs.filter { !it.abnormal.isNullOrBlank() && it.abnormal != "normal" }
        d.h2(context.getString(R.string.pdf_section_labs, abnormal.size, r.labs.size))
        if (r.labs.isEmpty()) d.line(context.getString(R.string.pdf_no_labs))
        else if (abnormal.isEmpty()) d.line(context.getString(R.string.pdf_labs_all_normal))
        else {
            abnormal.take(20).forEach { l ->
                val v = l.value?.let { "%.2f".format(it) } ?: (l.valueText ?: "-")
                val flag = when (l.abnormal) {
                    "high" -> " " + context.getString(R.string.pdf_lab_flag_high)
                    "low" -> " " + context.getString(R.string.pdf_lab_flag_low)
                    "abnormal" -> " " + context.getString(R.string.pdf_lab_flag_abnormal)
                    else -> ""
                }
                d.line("  ${l.date} ${l.testName}：$v${l.unit?.let { " $it" } ?: ""}$flag" +
                    (l.refLow?.let { lo ->
                        l.refHigh?.let { hi -> context.getString(R.string.pdf_lab_ref_range, lo, hi) }
                    } ?: ""), flag.isNotBlank())
            }
            if (abnormal.size > 20) {
                d.line("  " + context.getString(R.string.pdf_labs_more, abnormal.size - 20))
            }
        }

        d.h2(context.getString(R.string.pdf_section_checkups, r.checkups.size))
        if (r.checkups.isEmpty()) d.line(context.getString(R.string.pdf_no_checkups))
        r.checkups.take(15).forEach { c ->
            d.line("  ${c.date} ${c.itemName}" + (c.hospital?.let { " @$it" } ?: "") +
                (c.conclusion?.let { "：${it.take(60)}" } ?: ""))
        }

        d.h2(context.getString(R.string.pdf_section_next_checkups))
        if (r.nextCheckups.isEmpty()) d.line(context.getString(R.string.pdf_no_next_checkups))
        r.nextCheckups.forEach { c -> d.line("  ${c.nextDate} ${c.itemName}") }

        d.h2(context.getString(R.string.pdf_section_vaccines, r.vaccines.size))
        if (r.vaccines.isEmpty()) d.line(context.getString(R.string.pdf_none))
        r.vaccines.forEach { v ->
            d.line("  ${v.date} ${v.vaccineName}" +
                (v.nextDueDate?.let { context.getString(R.string.pdf_vaccine_next, it) } ?: ""))
        }

        d.gap(10f)
        d.line(context.getString(R.string.pdf_checkup_disclaimer), warn = true)

        val f = File(File(context.filesDir, "exports"), "ashkb-report-$today.pdf")
        d.close(f)
        return f
    }

    // ======================= 紧急卡打印版（M7） =======================

    fun writeEmergencyCard(context: Context, c: ReportRepository.EmergencyCard): File {
        val today = LocalDate.now().toString()
        val d = Doc(
            context,
            context.getString(R.string.pdf_emergency_title),
            context.getString(R.string.pdf_emergency_footer, today)
        )

        d.h2(context.getString(R.string.pdf_section_patient_info))
        val p = c.profile
        if (p == null) d.line(context.getString(R.string.pdf_no_profile))
        else {
            d.bigLine("${p.displayName} · ${p.diagnosis}")
            d.kv("HLA-B27", Labels.hlaB27(p.hlaB27))
            d.kv(context.getString(R.string.pdf_label_disease_stage), stage(context, p.diseaseStage))
            p.allergies?.let { d.kv(context.getString(R.string.pdf_label_allergies), it) }
            p.emergencyBloodType?.let { d.kv(context.getString(R.string.pdf_label_blood_type), it) }
        }

        d.h2(context.getString(R.string.emergency_meds_section))
        val meds = c.meds
        if (meds.isEmpty) {
            d.line(context.getString(R.string.emergency_meds_empty))
        } else {
            val tag = context.getString(R.string.emergency_meds_tag_immunosuppressant)
            meds.ordered.forEach { e ->
                val suffix = if (e.immunosuppressant) "　$tag" else ""
                d.line(fit("· ${e.name}｜${e.detail}", MED_LINE_CHARS - suffix.length) + suffix)
            }
            if (meds.hiddenCount > 0) {
                d.line(context.getString(R.string.emergency_meds_more, meds.hiddenCount.toString()))
            }
            if (meds.hasImmunosuppressant) {
                d.line(context.getString(R.string.emergency_meds_note))
            }
        }

        d.h2(context.getString(R.string.pdf_section_contacts, c.contacts.size))
        if (c.contacts.isEmpty()) d.line(context.getString(R.string.pdf_no_contacts))
        c.contacts.forEach { ct ->
            d.line("· ${ct.name}${ct.relation?.let { "（$it）" } ?: ""}　${ct.phone}" +
                (ct.hospital?.let { "　$it" } ?: "") +
                if (ct.isDoctor) "　" + context.getString(R.string.pdf_marker_doctor) else "")
        }

        d.h2(context.getString(R.string.pdf_section_emergency_cards))
        c.cards.forEach { kb ->
            d.gap(4f)
            d.line("【${kb.title}】")
            d.line(kb.summary, warn = kb.severityLevel == "high")
        }

        d.gap(12f)
        d.line(context.getString(R.string.pdf_emergency_call), warn = true)

        val f = File(File(context.filesDir, "exports"), "ashkb-emergency-card-$today.pdf")
        d.close(f)
        return f
    }

    /** 紧急卡单行药条字符上限（line() 折行阈值 ≈ (PW - 2M) / 10.5 ≈ 47，留余量给免疫抑制标签）。 */
    private const val MED_LINE_CHARS = 44

    /** 单行截断：超上限以省略号收尾，保证紧急卡一行一药、不折行溢出。 */
    private fun fit(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"

    private fun stage(context: Context, k: String) = when (k) {
        "stable" -> context.getString(R.string.pdf_stage_stable)
        "controlled" -> context.getString(R.string.pdf_stage_controlled)
        "flare" -> context.getString(R.string.pdf_stage_flare)
        else -> context.getString(R.string.pdf_stage_unknown)
    }
    private fun freq(context: Context, k: String) = when (k) {
        "DAILY" -> context.getString(R.string.pdf_freq_daily)
        "BID" -> context.getString(R.string.pdf_freq_bid)
        "Q8H" -> context.getString(R.string.pdf_freq_q8h)
        "WEEKLY" -> context.getString(R.string.pdf_freq_weekly)
        "BIW" -> context.getString(R.string.pdf_freq_biw)
        "Q2W" -> context.getString(R.string.pdf_freq_q2w)
        "PRN" -> context.getString(R.string.pdf_freq_prn)
        else -> k
    }
}
