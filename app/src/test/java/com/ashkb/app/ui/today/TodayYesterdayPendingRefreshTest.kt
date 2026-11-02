package com.ashkb.app.ui.today

import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.domain.PendingDoses
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.14 真机反馈：停用 / 删除药品后，今日页的「昨天还有 N 剂未记录」补记卡仍挂着那支药。
 *
 * ### 缺陷形态（真机实证，2026-11-02）
 * 在「我的 → 药单管理」里把测试药 `TestDrugB2` 停用并从「已停用药品」永久删除后，回到今日页，
 * 补记卡仍然显示 `TestDrugB2 · 计划 09:00 · 补记已服`；**force-stop 重启进程后才消失**。
 *
 * ### 根因
 * `TodayViewModel._yesterdayPending` 只有三个重算时机：构造、跨日广播、本页打卡/跳过。
 * 「在别的页面改了药单」不在其中。而 `refreshYesterdayPending()` 读的是
 * `medicationDao.listActive()`——它本来就该跟着药单走，只是没人订阅药单的变化。
 *
 * ### 本测试守什么
 * 领域推导本身（`PendingDoses.unsettledOn`）已有 `PendingDosesTest` 覆盖，这里守的是
 * **ViewModel 有没有把「药单变化」接成重算触发**。因为这条链路是「数据对了但界面没刷新」，
 * 纯领域测试抓不到，只能对着源码钉。
 */
class TodayYesterdayPendingRefreshTest {

    private val source = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayViewModel.kt")

    @Test
    fun `init 里订阅了活跃药单并为每次变化重算补记卡`() {
        val initBody = braceBody(source, "    init {")

        assertTrue(
            "init 必须订阅活跃药单（observeMedications）——否则在别的页面停用/删除药品后，" +
                "补记卡会一直挂着那支药，直到重启进程。实际 init：\n$initBody",
            initBody.contains("repo.observeMedications().collect"),
        )
        assertTrue(
            "订阅到新值后必须真的重算补记卡（collect 里要调 refreshYesterdayPending）",
            initBody.contains("collect { refreshYesterdayPending() }"),
        )
    }

    @Test
    fun `重算读的是活跃药单而不是全量药单`() {
        val refreshBody = braceBody(source, "suspend fun refreshYesterdayPending() = withContext(Dispatchers.IO) {")

        assertTrue(
            "补记卡必须按「在用药品」算——停用的药不该继续出现在待补清单里",
            refreshBody.contains("listActive()"),
        )
        assertTrue(
            "不能改成全量读取（observeMedications/observeActive 之外的口子）——那会把已停用的药也算进来",
            !refreshBody.contains("medicationDao().list("),
        )
    }

    @Test
    fun `停用药与删除药都会让活跃药单发出新值`() {
        // 这条守的是「一处订阅盖住两条路径」这个前提：归档用 UPDATE is_archived=1，
        // 删除用 DELETE，两者都写 medications 表，Room 的 InvalidationTracker 都会让
        // observeActive() 重发。若哪天归档换成「另外存一张表」，订阅就漏了。
        val daos = readSource("app/src/main/java/com/ashkb/app/data/db/Daos.kt")
        val activeBody = blockOf(daos, "fun observeActive(): Flow<List<Medication>>")

        assertTrue(
            "observeActive 必须过滤 is_archived = 0（否则停用的药仍算在用）：$activeBody",
            activeBody.contains("is_archived = 0"),
        )
        assertTrue(
            "归档必须是 UPDATE 同一张 medications 表（Room 才会给 observeActive 发新值）",
            daos.contains("UPDATE medications SET is_archived = 1"),
        )
        assertTrue(
            "删除必须是 DELETE FROM medications（同上）",
            daos.contains("DELETE FROM medications WHERE id = :id"),
        )
    }

    @Test
    fun `补记卡只列已到点且未结算的剂量（与既有领域口径一致）`() {
        // 不重复测 PendingDoses 的细节，只确认 VM 传进去的是它而不是自己另算一份。
        assertTrue(
            "refreshYesterdayPending 必须走 PendingDoses.unsettledOn（口径唯一）",
            source.contains("_yesterdayPending.value = PendingDoses.unsettledOn("),
        )

        val med = medication(id = "med-1", startDate = "2026-10-01")
        val date = LocalDate.of(2026, 11, 2)
        val settled = setOf("09:00")

        val pending = PendingDoses.unsettledOn(
            date = date,
            meds = listOf(med),
            now = LocalDateTime.of(2026, 11, 3, 0, 10),
            isSettled = { _, slotKey -> slotKey in settled },
        )
        assertEquals("09:00 已结算，不该再出现在待补清单", emptyList<PendingDoses.Pending>(), pending)
    }

    /** 药单为空时补记卡必须为空——这是「删干净之后卡片该消失」的最小断言。 */
    @Test
    fun `没有在用药品时补记卡为空`() {
        val pending = PendingDoses.unsettledOn(
            date = LocalDate.of(2026, 11, 2),
            meds = emptyList(),
            now = LocalDateTime.of(2026, 11, 3, 0, 10),
            isSettled = { _, _ -> false },
        )
        assertEquals(emptyList<PendingDoses.Pending>(), pending)
    }

    private fun medication(id: String, startDate: String) = Medication(
        id = id,
        name = "TestDrugB2",
        nameKey = "testdrugb2",
        medClass = "NSAID",
        route = "oral",
        dose = "40mg",
        frequency = MedFrequency.DAILY.name,
        startDate = startDate,
        createdAt = "2026-10-01T08:00:00",
        updatedAt = "2026-10-01T08:00:00",
    )

    /**
     * 取某个顶层声明起、到下一个顶层声明前的源码片段；**去掉注释行**。
     *
     * 注释必须去掉：本批修复的论证里就写了 `observeMedications().collect` 这些字样，
     * 留着注释会让「代码里有没有它」的断言语义失真（写注释就能变绿）。
     */
    private fun braceBody(text: String, header: String): String {
        val start = text.indexOf(header)
        if (start < 0) return ""
        val body = text.substring(start)
        val end = body.indexOf("\n    }\n")
        val block = if (end > 0) body.substring(0, end) else body
        return block.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
    }

    /** 取 `Daos.kt` 里某个接口方法的注解 + 函数声明（到下一个 `@Query`/`fun` 前）。 */
    private fun blockOf(text: String, header: String): String {
        val start = text.indexOf(header)
        if (start < 0) return ""
        // 往前找 @Query 注解起点
        val queryStart = text.lastIndexOf("@Query", start).let { if (it >= 0) it else start }
        return text.substring(queryStart, minOf(text.length, start + 400))
    }

    private fun readSource(relative: String): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            val candidate = java.io.File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        return java.io.File("../$relative").readText()
    }
}
