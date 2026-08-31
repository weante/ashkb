package com.ashkb.app.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ashkb.app.data.repo.ReportRepository
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

    class Doc(private val title: String, private val footer: String) {
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
                canvas!!.drawText("第 $pageNo 页", PW - M - 40f, y + 8f, smallPaint)
                y += 20f
            }
        }

        private fun finishPage() {
            page?.let { p ->
                canvas!!.drawText("第 $pageNo 页 · $footer", M, PH - 22f, smallPaint)
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
        val d = Doc("AS 复诊报告（患者自备摘要）",
            "由 ASHKB 生成于 $today · 供复诊参考，不构成医疗建议")

        d.h2("基本信息")
        val p = r.profile
        if (p == null) d.line("（未建档——请在「我的」中完善健康档案）")
        else {
            d.kv("姓名", p.displayName)
            d.kv("诊断", p.diagnosis)
            d.kv("确诊年份", p.diagnoseYear?.toString() ?: "未填")
            d.kv("HLA-B27", hla(p.hlaB27))
            d.kv("病情分期", stage(p.diseaseStage))
            p.allergies?.let { d.kv("过敏史", it) }
            p.emergencyBloodType?.let { d.kv("血型", it) }
        }

        d.h2("当前用药（${r.meds.size} 种）")
        if (r.meds.isEmpty()) d.line("（无在用药物记录）")
        r.meds.forEach { m ->
            d.line("· ${m.name}${m.brandName?.let { "（$it）" } ?: ""}｜${m.dose}｜${freq(m.frequency)}" +
                "｜${if (m.route == "injection") "注射" else "口服"}" +
                m.injCycleDays?.let { "｜每 $it 天" } ?: "")
        }

        d.h2("近 30 天依从与症状")
        val o = r.overview
        d.kv("服药打卡", "${o.adherence.medDone} 完成 / ${o.adherence.medPartial} 部分 / ${o.adherence.medSkipped} 跳过")
        d.kv("依从率", "${o.adherence.medRatePct}%")
        d.kv("运动", "${o.exercise.doneCount} 次完成，共 ${o.exercise.totalMinutes} 分钟（跳过 ${o.exercise.skippedCount}）")
        d.kv("症状记录天数", "${o.symptom.daysRecorded} / 30")
        d.kv("平均疼痛", o.symptom.avgPain?.let { "%.1f / 10".format(it) } ?: "未记录")
        d.kv("平均晨僵", o.symptom.avgStiffnessMin?.let { "%.0f 分钟".format(it) } ?: "未记录")
        d.kv("夜间痛天数", "${o.symptom.nightPainDays}")
        d.kv("疲乏均值", o.symptom.avgFatigue?.let { "%.1f / 10".format(it) } ?: "未记录")
        d.kv("眼部症状天数", "${o.symptom.eyeDays}（>0 需眼科评估）", o.symptom.eyeDays > 0)
        d.kv("发热天数", "${o.symptom.feverDays}")
        d.kv("发作次数", "${o.flareCount}" + if (o.flareActive) "（目前处于发作期）" else "")

        d.h2("BASDAI 走势（近 90 天 ${r.basdaiHistory.size} 次）")
        val bas = r.basdaiHistory
        if (bas.isEmpty()) d.line("（期间无 BASDAI 自评记录）")
        else {
            val avg = bas.map { it.total }.average()
            d.kv("最新", "${bas.last().date}：${"%.1f".format(bas.last().total)} / 10")
            d.kv("均值", "%.1f / 10".format(avg))
            o.basdaiDelta?.let { d.kv("环比变化", "%+.1f".format(it)) }
            if (bas.size > 1) {
                bas.takeLast(8).forEach {
                    d.line("  ${it.date}  ${"%.1f".format(it.total)}" +
                        if (it.total >= 4.0) "  ⚠ ≥4.0 活动度高" else "")
                }
            }
        }

        d.h2("近 180 天化验（${r.labs.size} 项）")
        val abnormal = r.labs.filter { !it.abnormal.isNullOrBlank() && it.abnormal != "normal" }
        if (r.labs.isEmpty()) d.line("（无化验记录）")
        else {
            r.labs.take(20).forEach { l ->
                val v = l.value?.let { "%.2f".format(it) } ?: (l.valueText ?: "-")
                val flag = when (l.abnormal) {
                    "high" -> " ↑ 高"; "low" -> " ↓ 低"; "abnormal" -> " ⚠ 异常"; else -> ""
                }
                d.line("  ${l.date} ${l.testName}：$v${l.unit?.let { " $it" } ?: ""}$flag" +
                    (l.refLow?.let { lo -> l.refHigh?.let { hi -> "（参考 $lo–$hi）" } } ?: ""), flag.isNotBlank())
            }
            if (r.labs.size > 20) d.line("  …另有 ${r.labs.size - 20} 项未列出")
        }

        d.h2("近 180 天复诊记录（${r.checkups.size} 次）")
        if (r.checkups.isEmpty()) d.line("（无复诊记录）")
        r.checkups.take(15).forEach { c ->
            d.line("  ${c.date} ${c.itemName}" + (c.hospital?.let { " @$it" } ?: "") +
                (c.conclusion?.let { "：${it.take(60)}" } ?: ""))
        }

        d.h2("下次复诊")
        if (r.nextCheckups.isEmpty()) d.line("（暂无登记的下次复诊日期）")
        r.nextCheckups.forEach { c -> d.line("  ${c.nextDate} ${c.itemName}") }

        d.h2("疫苗记录（${r.vaccines.size} 条）")
        if (r.vaccines.isEmpty()) d.line("（无）")
        r.vaccines.forEach { v ->
            d.line("  ${v.date} ${v.vaccineName}" + (v.nextDueDate?.let { "（下次 $it）" } ?: ""))
        }

        d.gap(10f)
        d.line("说明：本报告由患者个人健康工具 ASHKB 汇总自记录数据，供复诊沟通参考。所有诊疗以主治医师医嘱为准。", warn = true)

        val f = File(File(context.filesDir, "exports"), "ashkb-report-$today.pdf")
        d.close(f)
        return f
    }

    // ======================= 紧急卡打印版（M7） =======================

    fun writeEmergencyCard(context: Context, c: ReportRepository.EmergencyCard): File {
        val today = LocalDate.now().toString()
        val d = Doc("AS 紧急信息卡",
            "ASHKB 生成于 $today · 急救人员参考 · 明文展示为设计例外（协议 §1）")

        d.h2("患者信息")
        val p = c.profile
        if (p == null) d.line("（未建档）")
        else {
            d.bigLine("${p.displayName} · ${p.diagnosis}")
            d.kv("HLA-B27", hla(p.hlaB27))
            d.kv("病情分期", stage(p.diseaseStage))
            p.allergies?.let { d.kv("过敏史", it) }
            p.emergencyBloodType?.let { d.kv("血型", it) }
        }

        d.h2("紧急联系人（${c.contacts.size}）")
        if (c.contacts.isEmpty()) d.line("（未添加）")
        c.contacts.forEach { ct ->
            d.line("· ${ct.name}${ct.relation?.let { "（$it）" } ?: ""}　${ct.phone}" +
                (ct.hospital?.let { "　$it" } ?: "") + if (ct.isDoctor) "　[主治医生]" else "")
        }

        d.h2("应急处理卡（五场景）")
        c.cards.forEach { kb ->
            d.gap(4f)
            d.line("【${kb.title}】")
            d.line(kb.summary, warn = kb.severityLevel == "high")
        }

        d.gap(12f)
        d.line("紧急情况请直接拨打 120 或就近就医。", warn = true)

        val f = File(File(context.filesDir, "exports"), "ashkb-emergency-card-$today.pdf")
        d.close(f)
        return f
    }

    private fun hla(k: String) = when (k) { "positive" -> "阳性"; "negative" -> "阴性"; else -> "未知" }
    private fun stage(k: String) = when (k) { "active" -> "活动期"; "stable" -> "缓解期"; else -> "未评估" }
    private fun freq(k: String) = when (k) {
        "DAILY" -> "每日"; "BID" -> "每日两次"; "Q8H" -> "每8小时"; "WEEKLY" -> "每周一次";
        "Q2W" -> "每两周一次"; "PRN" -> "按需"; else -> k
    }
}
