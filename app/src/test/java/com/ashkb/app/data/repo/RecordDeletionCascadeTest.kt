package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.CheckupAttachment
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.domain.CheckupDeletion
import com.ashkb.app.domain.DerivedAlerts
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.80（批次 6）：**删除 / 修改的连带效果**，全部过真库（Robolectric + Room）。
 *
 * 为什么必须过真库：这一批的每一条规则都是「删了 A，B 也必须消失」这种跨表事实——
 * 纯函数证明不了 SQL 真的删对了表，也证明不了附件的**磁盘文件**真的没了。
 * 「记录没了但派生数据还在」正是本批要消灭的幽灵，而它只在库里看得见。
 *
 * 沿用 [LabAbnormalPriorityTest] 的两条基建约定（原因见那里的注释）：
 *  · 每个用例前清掉 [AppDatabase] 的进程内单例（Robolectric 会重置 SQLite 影子状态，
 *    旧实例连着已失效的连接，第二个用例必撞 `Illegal connection pointer`）；
 *  · 所有库操作走 IO 线程（Room 默认禁止主线程访问，而 Robolectric 的测试线程就是主线程）。
 *
 * 每个用例用**各自独立的 id / 日期**：Robolectric 里库是进程内单例，用例之间会互相看见，
 * 互不重叠的键就不必依赖「每个方法一个干净库」这种前提。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecordDeletionCascadeTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { HealthRepository(ctx) }
    private val attachments by lazy { AttachmentRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    // ---- 复诊记录：级联删除 ----

    @Test
    fun `删复诊记录会级联删掉名下化验影像附件并删除附件文件`() {
        val recId = "crec-cascade-1"
        val keepId = "crec-cascade-keep"
        val file = io { seedCheckupWithChildren(recId, keepId) }

        val counts = io { repo.deleteCheckupRecord(recId) }

        assertEquals("确认框要报出的条数与实际删除不符", CheckupDeletion.Counts(labs = 2, imaging = 1, attachments = 1), counts)
        assertEquals("复诊记录本身没删掉", null, io { db.checkupRecordDao().byId(recId) })
        assertEquals("名下化验没删干净", 0, io { db.labResultDao().countByCheckup(recId) })
        assertEquals("名下影像没删干净", 0, io { db.imagingDao().countByCheckup(recId) })
        assertEquals("附件行没删掉", null, io { db.checkupAttachmentDao().byId("catt-cascade-1") })
        assertFalse("附件**磁盘文件**没删——内部存储里会永久留着一份用户以为已删的化验单照片", file.exists())
    }

    @Test
    fun `级联只删这一次就诊名下的数据`() {
        val recId = "crec-cascade-2"
        val keepId = "crec-cascade-keep-2"
        io { seedCheckupWithChildren(recId, keepId) }

        io { repo.deleteCheckupRecord(recId) }

        assertEquals("别的就诊的化验被误删了", 1, io { db.labResultDao().countByCheckup(keepId) })
        assertNotNull("别的就诊记录被误删了", io { db.checkupRecordDao().byId(keepId) })
    }

    @Test
    fun `删除不存在的复诊记录时零写入`() {
        val keepId = "crec-cascade-keep-3"
        io { seedCheckupWithChildren("crec-cascade-3", keepId) }

        val counts = io { repo.deleteCheckupRecord("crec-不存在") }

        assertNull("不存在的记录不该报告任何删除结果", counts)
        assertNotNull("零写入被破坏：别的记录被删了", io { db.checkupRecordDao().byId(keepId) })
    }

    @Test
    fun `删除前的条数统计与实际级联范围一致`() {
        val recId = "crec-cascade-4"
        io { seedCheckupWithChildren(recId, "crec-cascade-keep-4") }

        val counts = io { repo.checkupDeletionCounts(recId) }

        assertEquals(CheckupDeletion.Counts(labs = 2, imaging = 1, attachments = 1), counts)
        assertEquals(
            "统计不该有副作用（提前把数据删了）",
            2,
            io { db.labResultDao().countByCheckup(recId) },
        )
    }

    // ---- 化验结果：编辑重判 + 删除 ----

    @Test
    fun `改化验数值后按新值加参考范围重判且不改写AI原始标记`() {
        val name = "TEST-批次6-重判"
        val id = io {
            repo.saveLabResult(
                LabResult(
                    id = "", date = "2026-09-30", recordedAt = "2026-09-30T08:00:00",
                    testName = name, value = 15.0, unit = "mm/h",
                    refLow = 0.0, refHigh = 20.0,
                    abnormal = "high", aiAbnormal = "high", // AI 当初标了偏高
                )
            )
            db.labResultDao().observeTrend(name).first().first().id
        }
        assertEquals("本地判读（15 在范围内）应当赢", "normal", labById(id)!!.abnormal)

        // 编辑：只改数值（走 saveLabResult，与用户改完点保存同一条路径）
        val edited = labById(id)!!.copy(value = 25.0)
        io { repo.saveLabResult(edited) }

        val after = labById(id)!!
        assertEquals("改了数值却没重判，异常值会被静默漏掉（v1.0.77 修的就是这一类）", "high", after.abnormal)
        assertEquals("AI 原始标记只读留档，不该被本地判读覆盖", "high", after.aiAbnormal)
        assertEquals("编辑必须沿用原主键，否则会凭空多出一行", id, after.id)
    }

    @Test
    fun `改参考范围也会重判`() {
        val name = "TEST-批次6-改参考范围"
        val id = io {
            repo.saveLabResult(
                LabResult(
                    id = "", date = "2026-09-30", recordedAt = "2026-09-30T08:00:00",
                    testName = name, value = 25.0, refLow = 0.0, refHigh = 20.0,
                )
            )
            db.labResultDao().observeTrend(name).first().first().id
        }
        assertEquals("high", labById(id)!!.abnormal)

        io { repo.saveLabResult(labById(id)!!.copy(refHigh = 30.0)) }

        assertEquals("把参考上限改宽后应回到正常", "normal", labById(id)!!.abnormal)
    }

    @Test
    fun `删单条化验只删这一行`() {
        val keepName = "TEST-批次6-留"
        val delName = "TEST-批次6-删"
        val delId = io {
            repo.saveLabResult(
                LabResult(id = "", date = "2026-09-29", recordedAt = "2026-09-29T08:00:00", testName = delName, value = 1.0)
            )
            repo.saveLabResult(
                LabResult(id = "", date = "2026-09-29", recordedAt = "2026-09-29T08:01:00", testName = keepName, value = 2.0)
            )
            db.labResultDao().observeTrend(delName).first().first().id
        }

        io { repo.deleteLabResult(delId) }

        assertNull(io { db.labResultDao().observeTrend(delName).first() }.firstOrNull())
        assertNotNull(io { db.labResultDao().observeTrend(keepName).first() }.firstOrNull())
    }

    // ---- BASDAI：同日不变量在编辑后仍成立 ----

    @Test
    fun `同日 BASDAI 编辑后仍只保留一条`() {
        val date = "2026-09-28"
        io {
            // 模拟历史遗留的同日重复行（覆盖语义出现之前的旧数据 / 旧备份恢复）
            db.basdaiDao().upsert(basdai("bas-批次6-旧1", date, 2))
            db.basdaiDao().upsert(basdai("bas-批次6-旧2", date, 3))
        }
        assertEquals(2, io { db.basdaiDao().between(date, date) }.size)

        io { repo.saveBasdai(date, 8, 8, 8, 8, 8, 8, notes = null) }

        val rows = io { db.basdaiDao().between(date, date) }
        assertEquals("编辑后同一天必须只剩一条（既有不变量）", 1, rows.size)
        assertEquals("留下的那条必须是本次提交的内容", 8.0, rows.first().total, 0.001)
    }

    @Test
    fun `删 BASDAI 按 id 删且清掉该日派生的活动度警报`() {
        val date = "2026-09-27"
        val id = "bas-批次6-删"
        io {
            db.basdaiDao().upsert(basdai(id, date, 7))
            db.alertDao().insert(
                Alert(
                    id = "alt-批次6-bas", alertType = DerivedAlerts.BASDAI_HIGH, severity = "medium",
                    message = "m", kbRef = null, refDate = date, createdAt = "2026-09-27T08:00:00",
                )
            )
        }
        assertEquals(1, io { db.alertDao().countByRef(DerivedAlerts.BASDAI_HIGH, date) })

        io { repo.deleteBasdai(id) }

        assertTrue("记录没删掉", io { db.basdaiDao().between(date, date) }.isEmpty())
        assertEquals("该日派生的活动度警报成了幽灵", 0, io { db.alertDao().countByRef(DerivedAlerts.BASDAI_HIGH, date) })
    }

    // ---- 症状：删除连带撤销红旗警报 ----

    @Test
    fun `删症状记录会连带清掉该日的红旗警报`() {
        val date = "2026-09-26"
        io {
            repo.saveSymptom(
                SymptomDaily(
                    id = "", date = date, recordedAt = "2026-09-26T20:00:00",
                    feverish = true, feverTemp = 39.2, eyeSymptom = true,
                )
            )
        }
        assertEquals(1, io { db.alertDao().countByRef(DerivedAlerts.SYMPTOM_ABNORMAL, date) })

        io { repo.deleteSymptom(date) }

        assertNull("症状记录没删掉", io { db.symptomDailyDao().byDate(date) })
        assertEquals("红旗警报失去依据却还挂着（症状页会一直提示一件不存在的事）", 0, io { db.alertDao().countByRef(DerivedAlerts.SYMPTOM_ABNORMAL, date) })
    }

    @Test
    fun `已确认的红旗警报不随记录删除而消失`() {
        val date = "2026-09-25"
        io {
            repo.saveSymptom(
                SymptomDaily(
                    id = "", date = date, recordedAt = "2026-09-25T20:00:00",
                    feverish = true, feverTemp = 38.9,
                )
            )
        }
        val alertId = io { db.alertDao().observeUnacked().first() }.first { it.refDate == date }.id
        io { repo.ackAlert(alertId) }

        io { repo.deleteSymptom(date) }

        assertEquals(
            "已确认的警报是用户看过的留痕，且不在未读列表里，不构成幽灵——删除它等于销毁用户已确认的信息",
            1,
            io { db.alertDao().countByRef(DerivedAlerts.SYMPTOM_ABNORMAL, date) },
        )
    }

    // ---- 疫苗：删除 / 编辑后重算安全警报 ----

    @Test
    fun `删疫苗时同一天还有另一针活疫苗待确认则保留警报`() {
        val date = "2026-09-24"
        io {
            repo.saveVaccineRecord(vaccine("vac-批次6-a", date, VaccineType.LIVE, DoctorConfirm.PENDING))
            repo.saveVaccineRecord(vaccine("vac-批次6-b", date, VaccineType.LIVE, DoctorConfirm.PENDING))
        }
        assertEquals(1, io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) })

        io { repo.deleteVaccineRecord("vac-批次6-a") }

        assertEquals("当天还有一针活疫苗待确认——警报必须留着（一刀切删掉就是漏报）", 1, io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) })
    }

    @Test
    fun `删掉最后一条活疫苗待确认记录后警报撤销`() {
        val date = "2026-09-23"
        io { repo.saveVaccineRecord(vaccine("vac-批次6-c", date, VaccineType.LIVE, DoctorConfirm.PENDING)) }
        assertEquals(1, io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) })

        io { repo.deleteVaccineRecord("vac-批次6-c") }

        assertEquals("记录没了，警报不该还挂着", 0, io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) })
    }

    @Test
    fun `把活疫苗改为医生同意后警报撤销`() {
        val date = "2026-09-22"
        io { repo.saveVaccineRecord(vaccine("vac-批次6-d", date, VaccineType.LIVE, DoctorConfirm.PENDING)) }
        assertEquals(1, io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) })

        val row = io { db.vaccineRecordDao().byId("vac-批次6-d") }!!
        io { repo.saveVaccineRecord(row.copy(doctorConfirm = DoctorConfirm.CONFIRMED.name)) }

        assertEquals(
            "编辑撤销警报是正规路径——否则用户只能靠删除记录才能让这条 high 级提示消失",
            0,
            io { db.alertDao().countByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date) },
        )
    }

    // ---- 发作：删除后清掉窗口内的第 7 天警报 ----

    @Test
    fun `删发作会清掉落在它窗口内的第7天警报`() {
        val flareId = "flr-批次6-删"
        io {
            db.flareDao().upsert(
                FlareEvent(
                    id = flareId, recordedAt = "2026-09-01T08:00:00", startDate = "2026-09-01",
                    endDate = null, status = "active", trigger = "UNKNOWN",
                )
            )
            db.alertDao().insert(flareAlert("alt-批次6-flr-in", "2026-09-08"))
            db.alertDao().insert(flareAlert("alt-批次6-flr-out", "2026-08-20"))
        }

        io { repo.deleteFlare(flareId, java.time.LocalDate.parse("2026-09-10")) }

        assertEquals("窗口内的警报应作为派生数据被清掉", 0, io { db.alertDao().countByRef(DerivedAlerts.FLARE_DAY7, "2026-09-08") })
        assertEquals("窗口外的警报属于别的发作，不能顺手删", 1, io { db.alertDao().countByRef(DerivedAlerts.FLARE_DAY7, "2026-08-20") })
    }

    // ---- 运动打卡 ----

    @Test
    fun `删运动打卡只删指定那一条`() {
        val date = "2026-09-21"
        io {
            db.exerciseLogDao().upsert(exercise("elog-批次6-a", date, "快走"))
            db.exerciseLogDao().upsert(exercise("elog-批次6-b", date, "拉伸"))
        }

        io { repo.deleteExerciseLog("elog-批次6-a") }

        val left = io { db.exerciseLogDao().byDate(date) }
        assertEquals(1, left.size)
        assertEquals("elog-批次6-b", left.first().id)
    }

    // ---- 补剂打卡：撤销 ----

    @Test
    fun `撤销补剂打卡会回到未记录态`() {
        val date = "2026-09-18"
        io {
            // 连点两次打卡：slot_key 为 NULL 而 SQLite 里 NULL 互不相等，故会留下两行
            repo.checkInSupplement(supplementLog(date))
            repo.checkInSupplement(supplementLog(date))
        }
        assertEquals(2, io { db.supplementLogDao().byDate(date) }.size)

        io { db.supplementLogDao().byDate(date).forEach { repo.deleteSupplementLog(it.id) } }

        assertTrue(
            "撤销后应回到「今天还没服用」的未记录态（不是 skipped——用户并没有决定不吃）",
            io { db.supplementLogDao().byDate(date) }.isEmpty(),
        )
    }

    // ---- 紧急事件记录 ----

    @Test
    fun `删紧急事件只删指定那一条`() {
        io {
            db.emergencyEventDao().upsert(event("eev-批次6-a", "2026-09-17"))
            db.emergencyEventDao().upsert(event("eev-批次6-b", "2026-09-16"))
        }

        io { repo.deleteEmergencyEvent("eev-批次6-a") }

        assertNull(io { db.emergencyEventDao().byId("eev-批次6-a") })
        assertNotNull("别的事件被误删了", io { db.emergencyEventDao().byId("eev-批次6-b") })
    }

    // ---- 身体围度：同日覆盖 ----

    @Test
    fun `同一天重复保存身体围度只保留一行`() {
        val date = "2026-09-15"
        io {
            repo.saveBodyMeasure(BodyMeasure(id = "", date = date, recordedAt = "${date}T08:00:00", waistCm = 80.0))
            repo.saveBodyMeasure(BodyMeasure(id = "", date = date, recordedAt = "${date}T09:00:00", waistCm = 82.0))
        }

        val rows = io { db.bodyMeasureDao().observeRecent(10).first() }.filter { it.date == date }
        assertEquals("同一天改一次就多一行，用户只能一条条删（旧行为）", 1, rows.size)
        assertEquals(82.0, rows.first().waistCm ?: 0.0, 0.001)
    }

    // ---- 夹具 ----

    private fun labById(id: String): LabResult? = io {
        db.labResultDao().allOrdered().firstOrNull { it.id == id }
    }

    private fun basdai(id: String, date: String, q: Int) = BasdaiRecord(
        id = id, date = date, recordedAt = "${date}T08:00:00",
        q1Fatigue = q, q2SpinePain = q, q3PeripheralPain = q, q4TenderPoints = q,
        q5StiffnessDegree = q, q6StiffnessDuration = q,
        total = BasdaiRecord.total(q, q, q, q, q, q),
    )

    private fun vaccine(id: String, date: String, type: VaccineType, confirm: DoctorConfirm) = VaccineRecord(
        id = id, date = date, recordedAt = "${date}T09:00:00",
        vaccineName = "测试疫苗 $id", vaccineType = type.name, doctorConfirm = confirm.name,
    )

    private fun flareAlert(id: String, refDate: String) = Alert(
        id = id, alertType = DerivedAlerts.FLARE_DAY7, severity = "medium",
        message = "m", kbRef = null, refDate = refDate, createdAt = "${refDate}T09:00:00",
    )

    private fun exercise(id: String, date: String, name: String) = ExerciseLog(
        id = id, date = date, recordedAt = "${date}T07:00:00",
        excId = null, excKey = "exc-test", excName = name, status = "done",
    )

    /** 一次补剂打卡（slot_key 留空 = 补剂打卡的真实形态，见 SupplementLogDao.upsert 的调用点）。 */
    private fun supplementLog(date: String) = SupplementLog(
        id = "", date = date, recordedAt = "${date}T08:00:00",
        supId = "sup-批次6", supKey = "sup-批次6", supName = "测试补剂",
        doseSnapshot = "1 粒", status = "done", takenAt = "${date}T08:00:00",
    )

    private fun event(id: String, date: String) = EmergencyEvent(
        id = id, date = date, recordedAt = "${date}T12:00:00", scene = "INFECTION_FEVER",
    )

    /**
     * 造一条复诊记录 + 它名下的 2 条化验 / 1 条影像 / 1 个附件（**磁盘文件真实存在**），
     * 另造一条「对照组」记录（1 条化验），用于证明级联不会越界。
     *
     * @return 附件在磁盘上的文件（调用方断言它被删掉）
     */
    private suspend fun seedCheckupWithChildren(recId: String, keepId: String): File {
        db.checkupRecordDao().upsert(checkup(recId, "2026-09-20", "抽血复查"))
        db.checkupRecordDao().upsert(checkup(keepId, "2026-09-19", "对照组就诊"))
        db.labResultDao().upsert(lab("lab-$recId-1", recId, "ESR"))
        db.labResultDao().upsert(lab("lab-$recId-2", recId, "CRP"))
        db.labResultDao().upsert(lab("lab-$keepId-1", keepId, "HGB"))
        db.imagingDao().upsert(
            ImagingRecord(
                id = "img-$recId-1", examDate = "2026-09-20", recordedAt = "2026-09-20T10:00:00",
                modality = "MRI", bodyPart = "骶髂关节", checkupId = recId,
            )
        )
        val dir = File(ctx.filesDir, "checkup_attachments").apply { mkdirs() }
        val file = File(dir, "catt-$recId.jpg")
        file.writeBytes(ByteArray(32) { 7 })
        db.checkupAttachmentDao().upsert(
            CheckupAttachment(
                id = "catt-$recId", checkupId = recId, kind = "PHOTO", fileName = file.name,
                mime = "image/jpeg", sizeBytes = file.length(), createdAt = "2026-09-20T11:00:00",
            )
        )
        return file
    }

    private fun checkup(id: String, date: String, name: String) = CheckupRecord(
        id = id, date = date, recordedAt = "${date}T09:00:00", itemName = name, checkType = "LAB",
    )

    private fun lab(id: String, checkupId: String, testName: String) = LabResult(
        id = id, date = "2026-09-20", recordedAt = "2026-09-20T10:00:00",
        checkupId = checkupId, testName = testName, value = 10.0,
    )
}
