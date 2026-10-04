package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.domain.DrugInteractionKeys
import com.ashkb.app.domain.KbSearch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.1.2（批次 18）：**用药安全核对链**的回归锁——「知识条目到底能不能到患者眼前」。
 *
 * ### 为什么必须过真库 + 真种子
 * 审查报告 §2 的结论是「大量知识写好了却到不了该看到的患者眼前」，本批次已独立复现。
 * 这类缺陷的**全部证据都在数据与匹配的接缝上**：
 *   · 纯函数测不出「乌帕替尼药单命中 0 条」——那是 `medClassKeys` 与种子 `drug_a` 取值域的关系；
 *   · 手工构造的假种子也测不出「`OTHER` 撞上 itx-015 引文里的英文单词 `other`」——
 *     那是**真实引文**里的一个英文词。
 * 故本文件：① 把 `app/src/main/assets/kb_seed_itx.json` **原文**载入真 Room 库；
 * ② 用 [MedicationRepository.interactionsFor] 跑完整链路（DAO → 结构化判定 → 排序）。
 *
 * ### 改前 / 改后判据
 * 每个用例的 KDoc 都写了两件事：**改前是什么情况下看不到（或看错）**、**改后要看到什么**。
 * 另有 [legacyLikeMatching] —— 它是旧实现（`payload LIKE '%key%'` + 硬编码 `medClassKeys`）
 * 的逐字复刻，用来把「改前确实会命中/漏掉哪条」写成**可执行断言**而不是注释里的转述，
 * 见 [旧实现的失效路径被逐条钉死]。
 *
 * 沿用 [CheckupItemRecordsQueryTest] 的基建约定：每个用例前清掉 [AppDatabase] 的进程内单例；
 * 库操作走 IO 线程（Room 默认禁止主线程访问，而 Robolectric 的测试线程就是主线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MedicationInteractionChainTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val repo by lazy { MedicationRepository(ctx) }
    private val db by lazy { AppDatabase.get(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    // ------------------------------------------------------------------
    // 真种子载入
    // ------------------------------------------------------------------

    /** 单测工作目录是模块目录（`app/`），与 `RegexLiteralGuardTest` 同一约定。 */
    private fun assetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("找不到种子文件 $name（单测工作目录=${File(".").absolutePath}）")
    }

    private fun seedEntries(fileName: String): List<KbEntry> {
        val arr = JSONArray(assetFile(fileName).readText(Charsets.UTF_8))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
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
                searchText = KbSearch.searchText(title, summary, payload),
            )
        }
    }

    private fun seedItx() = io {
        val entries = seedEntries("kb_seed_itx.json")
        assertTrue("种子应当有 15 条 itx 条目，实际 ${entries.size}", entries.size == 15)
        db.kbEntryDao().insertAll(entries)
    }

    private var medSeq = 0
    private fun med(
        name: String,
        nameKey: String,
        medClass: MedClass,
        brand: String? = null,
    ) = Medication(
        id = "med-t-${medSeq++}",
        name = name,
        brandName = brand,
        nameKey = nameKey,
        medClass = medClass.name,
        route = "oral",
        dose = "1 片",
        frequency = MedFrequency.DAILY.name,
        startDate = "2026-01-01",
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
    )

    private fun supplement(name: String, category: SupplementCategory) = Supplement(
        id = "sup-t-${medSeq++}",
        name = name,
        brand = null,
        category = category.name,
        dose = "1 片",
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
    )

    /** 查询并返回命中的条目 id（升序，便于断言）。 */
    private fun hitsOf(med: Medication): List<String> =
        io { repo.interactionsFor(med).map { it.id }.sorted() }

    // ------------------------------------------------------------------
    // 改后的判据：每条药单应当看到什么
    // ------------------------------------------------------------------

    /**
     * **JAK 抑制剂**（审查报告 §2.3①，改前命中 0 条）。
     *
     * 改前：`medClassKeys` 没有 JAK 分支 → 键只有 `upadacitinib` 与 `JAK`，
     * 而 15 条 itx 的 `drug_a` 里两者都不存在 → **零条**（审查报告附录 A 第 8–10 行）。
     * 改后：`upadacitinib` 归一成 {upadacitinib, jak}；条目侧 `adalimumab` 覆盖 `tnf_inhibitor`，
     * `tnf_inhibitor` 又覆盖 adalimumab 等 5 个 TNF 抑制剂 —— **但不覆盖 JAK**，
     * 故 JAK 只命中它真正相关的三条：itx-002（生物制剂类严重感染）、itx-010（活疫苗接种窗口）、
     * itx-012（高剂量激素 — 只在药单上真有激素时才出）。
     *
     * ⚠️ 本条**不**断言 JAK 应当看到什么医学内容——那要由维护者裁决是否补 JAK 专属条目
     * （审查报告 §4③ / §7 P1）。这里锁的是：**同一份种子在旧机制下一条都到不了、在新机制下能到**。
     */
    @Test
    fun `JAK 抑制剂药单从零命中变为可命中生物制剂类条目`() {
        seedItx()
        val hits = hitsOf(med("艾乐明", "upadacitinib", MedClass.JAK, brand = "艾乐明"))
        assertTrue(
            "JAK 药单改后应当能命中 itx-002 / itx-010，实际 $hits",
            hits.containsAll(listOf("itx-002", "itx-010")),
        )
        assertFalse("JAK 不是 TNF 抑制剂，不该被 itx-012 之外的高剂量激素条目命中", hits.contains("itx-015"))
    }

    /**
     * **来氟米特误报**（审查报告 §2.3②，改前收到甲氨蝶呤的致死警告）。
     *
     * 改前：`medClassKeys("csdmard")` 硬编码 `["mtx","methotrexate","sulfasalazine"]` →
     * 来氟米特命中 itx-001 / 005 / 006 / 007 / 011；其中 **itx-001 =
     * 「甲氨蝶呤必须每周一次，误当每日服用可致死」** —— 吃来氟米特的人看到一条甲氨蝶呤的警告。
     * 改后：`leflunomide` 只归一成 {leflunomide, csdmard}，而**没有任何条目的 drug_a 是 csdmard**
     * → 来氟米特**不再命中任何一条 itx**。
     *
     * ⚠️ 这**不是**说「来氟米特没有风险」。审查报告同时指出：来氟米特自己的肝酶监测方案写在
     * `fdg-004`（category = `food_drug`），而 `interactionsFor` 只取 `interaction` 类目，
     * 故它**仍然到不了**药单核对清单——「放宽类目过滤」触及种子/类目语义，本批次**未做**，
     * 已列入第三阶段提案与「未做」清单。
     */
    @Test
    fun `来氟米特药单不再收到甲氨蝶呤的致死警告`() {
        seedItx()
        val hits = hitsOf(med("爱若华", "leflunomide", MedClass.CSDMARD, brand = "爱若华"))
        assertFalse(
            "来氟米特不该命中甲氨蝶呤条目 itx-001（改前正是它）——实际 $hits",
            hits.contains("itx-001"),
        )
        assertTrue("来氟米特改后不该命中任何 itx（种子里没有 leflunomide 侧条目），实际 $hits", hits.isEmpty())
    }

    /** 艾拉莫德同属 csDMARD，改前与来氟米特走同一条硬编码分支，改后同样不得命中甲氨蝶呤条目。 */
    @Test
    fun `艾拉莫德药单同样不再收到甲氨蝶呤条目`() {
        seedItx()
        val hits = hitsOf(med("艾得辛", "iguratimod", MedClass.CSDMARD, brand = "艾得辛"))
        assertTrue("艾拉莫德改后不该命中任何 itx，实际 $hits", hits.isEmpty())
    }

    /**
     * **`OTHER` 键的子串巧合**（审查报告 §2.3③，改前钙剂 / 维生素 D3 收到阿仑膦酸钠的服药规则）。
     *
     * 改前：`MedClass.OTHER.name` = 字符串 `"OTHER"` 被当 LIKE 模式传进去，
     * 而 itx-015 引的 FDA 原文含英文单词 `other` 三次
     * （"…your first food, drink, or **other** medicine…"）→ 全部 OTHER 类药命中 itx-015。
     * 改后：`calcium` / `colecalciferol` / `calcitriol` / `zoledronic` 各自归一成具体键，
     * 只有 `alendronate` 命中 itx-015。
     */
    @Test
    fun `OTHER 类不再因引文里的英文单词 other 而误命中阿仑膦酸钠条目`() {
        seedItx()
        val shouldHit = listOf(
            med("福善美", "alendronate", MedClass.OTHER, brand = "福善美"),
        )
        val shouldMiss = listOf(
            med("钙尔奇", "calcium", MedClass.OTHER),
            med("维生素 D3", "colecalciferol", MedClass.OTHER),
            med("罗盖全", "calcitriol", MedClass.OTHER),
            med("密固达", "zoledronic", MedClass.OTHER),
        )
        assertEquals("阿仑膦酸钠应当命中 itx-015", listOf("itx-015"), hitsOf(shouldHit[0]))
        for (m in shouldMiss) {
            assertFalse(
                "${m.name}（nameKey=${m.nameKey}）不该命中 itx-015，实际 ${hitsOf(m)}",
                hitsOf(m).contains("itx-015"),
            )
        }
    }

    /**
     * **双药条目要求两侧都在药单上**（审查报告附录 B「注意一」）。
     *
     * 改前：`drug_b` 从不参与键构造，条目退化成「单药命中即触发」——
     * 只吃甲氨蝶呤、没吃 NSAID 的人也会看到 itx-011「MTX + NSAIDs 合用需监测」。
     * 改后：itx-011 的 `drug_b = nsaid`，必须药单上真的有 NSAID。
     *
     * 判据：同一支甲氨蝶呤，**药单里没有 NSAID 时看不到 itx-011；加一支萘普生后能看到**。
     */
    @Test
    fun `双药条目只在两药并存时才出现`() {
        seedItx()
        val mtx = med("甲氨蝶呤", "methotrexate", MedClass.CSDMARD)
        io { db.medicationDao().upsert(mtx) }

        val alone = hitsOf(mtx)
        assertFalse("药单里没有 NSAID 时不该出现 itx-011（改前会），实际 $alone", alone.contains("itx-011"))
        assertTrue("单药条目 itx-001 仍应出现，实际 $alone", alone.contains("itx-001"))

        val naproxen = med("萘普生", "naproxen", MedClass.NSAID)
        io { db.medicationDao().upsert(naproxen) }

        val together = hitsOf(mtx)
        assertTrue("萘普生在药单上后应当出现 itx-011，实际 $together", together.contains("itx-011"))
    }

    /**
     * **中文药名可达**（审查报告附录 B「注意二」）。
     *
     * 改前：`interactionsFor` 只查 `payload` 列，而中文药名只出现在 `title` / `summary` 里
     * →手输「华法林」命不中 itx-003、「舍曲林」命不中 itx-008。
     * 改后：词汇表把中文名/商品名一并归一，`华法林` → warfarin、`舍曲林` → ssri。
     */
    @Test
    fun `中文药名能命中 drug_b 侧条目`() {
        seedItx()
        val nsaid = med("萘普生", "naproxen", MedClass.NSAID)
        io { db.medicationDao().upsert(nsaid) }

        val warfarin = med("华法林", "华法林", MedClass.OTHER)
        io { db.medicationDao().upsert(warfarin) }
        assertTrue("手动录入「华法林」应当命中 itx-003，实际 ${hitsOf(nsaid)}", hitsOf(nsaid).contains("itx-003"))

        val ssri = med("舍曲林", "舍曲林", MedClass.OTHER)
        io { db.medicationDao().upsert(ssri) }
        assertTrue("手动录入「舍曲林」应当命中 itx-008，实际 ${hitsOf(nsaid)}", hitsOf(nsaid).contains("itx-008"))
    }

    /**
     * **补剂进药单**：itx-015 的 `drug_b` 是 `food_calcium`，而钙剂 / 维生素 D3 记在**补剂档案**
     * （`supplements` 表）里，不在 `medications` 表。只看药品表会把这一整类漏掉。
     */
    @Test
    fun `补剂档案里的钙剂参与用药核对`() {
        seedItx()
        io { db.supplementDao().upsert(supplement("碳酸钙", SupplementCategory.CALCIUM)) }
        val tokens = io { repo.medicationTokens() }
        assertTrue(
            "补剂「碳酸钙」应当贡献出 calcium 键（tokens=$tokens）",
            DrugInteractionKeys.active("calcium", tokens),
        )
    }

    /**
     * **多药叠加条目**（itx-009，`drug_b = anti_inflammatory_multi`）：
     * 它要求「药单上存在 ≥2 种不同的抗炎药」，不是「随便一支 NSAID」。
     *
     * 改前：任何 NSAID 用户都会看到「同时使用 3–4 种抗炎药 RR 18.0」这条明确针对多药的警告
     * （审查报告附录 B「注意一」）。改后：一支 NSAID 时不出，两支时出。
     */
    @Test
    fun `多药叠加条目要求药单上真有两种以上抗炎药`() {
        seedItx()
        val naproxen = med("萘普生", "naproxen", MedClass.NSAID)
        io { db.medicationDao().upsert(naproxen) }
        assertFalse(
            "只有一支 NSAID 时不该出现 itx-009（改前会），实际 ${hitsOf(naproxen)}",
            hitsOf(naproxen).contains("itx-009"),
        )

        val celecoxib = med("塞来昔布", "celecoxib", MedClass.NSAID)
        io { db.medicationDao().upsert(celecoxib) }
        assertTrue(
            "两支 NSAID 并存时应当出现 itx-009，实际 ${hitsOf(naproxen)}",
            hitsOf(naproxen).contains("itx-009"),
        )
    }

    /** 激素 + NSAID（itx-004）同样要两侧并存；这是改动前最容易误报的组合之一。 */
    @Test
    fun `激素与 NSAID 的组合条目要求两药并存`() {
        seedItx()
        val prednisone = med("泼尼松", "prednisone", MedClass.GLUCOCORTICOID)
        io { db.medicationDao().upsert(prednisone) }
        assertFalse(
            "单用激素时不该出现 itx-004，实际 ${hitsOf(prednisone)}",
            hitsOf(prednisone).contains("itx-004"),
        )

        val naproxen = med("萘普生", "naproxen", MedClass.NSAID)
        io { db.medicationDao().upsert(naproxen) }
        assertTrue(
            "激素 + NSAID 并存时应当出现 itx-004，实际 ${hitsOf(prednisone)}",
            hitsOf(prednisone).contains("itx-004"),
        )
    }

    // ------------------------------------------------------------------
    // 旧实现的失效路径（可执行证据，不是注释转述）
    // ------------------------------------------------------------------

    /**
     * 旧实现（v1.1.1 及以前）的逐字复刻：`Daos.kt` 的
     * `payload LIKE '%'||:key||'%'` + `MedicationRepository.kt:126-132` 的 `medClassKeys`。
     *
     * 放在测试里而不是删掉，是因为**报告 §2 的每一条结论都要能在当前仓库里被重新验证**：
     * 下一个人若怀疑「真有那么糟吗」，跑这条用例即可看到改前到底会递出哪几条。
     * 它**不**参与生产路径（`repo.interactionsFor` 已不再使用子串匹配）。
     */
    private fun legacyLikeMatching(nameKey: String, medClass: MedClass): List<String> {
        val classKeys = when (medClass.name.lowercase()) {
            "nsaid" -> listOf("nsaid")
            "glucocorticoid" -> listOf("glucocorticoid", "steroid")
            "csdmard" -> listOf("mtx", "methotrexate", "sulfasalazine")
            "biologic" -> listOf("biologic", "adalimumab", "tnf")
            else -> emptyList()
        }
        val keys = (listOf(nameKey, medClass.name) + classKeys).filter { it.isNotBlank() }.distinct()
        val entries = seedEntries("kb_seed_itx.json")
        return entries
            .filter { e -> keys.any { k -> e.payload.contains(k, ignoreCase = true) } }
            .map { it.id }
            .sorted()
    }

    /**
     * 把审查报告 §2.3 的三条指控**逐条钉成可执行断言**。
     *
     * 用例的名字就是指控本身；断言写的是「旧实现在这个输入下会命中这些条目」，
     * 以及「新实现在同一个输入下命中这些条目」——两行放在一起，就是改前/改后的判据。
     */
    @Test
    fun `旧实现的失效路径被逐条钉死`() {
        seedItx()

        // ① JAK：旧实现零命中
        assertEquals(
            "旧实现对乌帕替尼应当命中 0 条（报告 §2.3①）",
            emptyList<String>(),
            legacyLikeMatching("upadacitinib", MedClass.JAK),
        )
        assertEquals(
            "旧实现对托法替布应当命中 0 条",
            emptyList<String>(),
            legacyLikeMatching("tofacitinib", MedClass.JAK),
        )

        // ② csDMARD：旧实现把甲氨蝶呤的两个键套给整个类别
        val legacyLeflunomide = legacyLikeMatching("leflunomide", MedClass.CSDMARD)
        assertEquals(
            "旧实现对来氟米特会误报这 5 条（报告 §2.3②）",
            listOf("itx-001", "itx-005", "itx-006", "itx-007", "itx-011"),
            legacyLeflunomide,
        )
        assertTrue("其中 itx-001 是甲氨蝶呤的致死警告", legacyLeflunomide.contains("itx-001"))

        // ③ OTHER：旧实现靠英文单词 other 巧合命中 itx-015
        for (nameKey in listOf("calcium", "colecalciferol", "calcitriol", "zoledronic")) {
            assertTrue(
                "旧实现对 $nameKey 会因引文里的 other 而命中 itx-015（报告 §2.3③）",
                legacyLikeMatching(nameKey, MedClass.OTHER).contains("itx-015"),
            )
        }

        // 新实现：三条同时消失
        assertEquals(
            "新实现对来氟米特应当一条都不命中",
            emptyList<String>(),
            hitsOf(med("爱若华", "leflunomide", MedClass.CSDMARD)),
        )
        assertEquals(
            "新实现对钙剂应当一条都不命中（itx-015 只属于阿仑膦酸钠）",
            emptyList<String>(),
            hitsOf(med("钙尔奇", "calcium", MedClass.OTHER)),
        )
    }

    /**
     * **`itx-015` 里那个英文单词 `other` 必须真的存在**——否则上面「巧合命中」的整条论证失去前提。
     *
     * 这条前置断言是刻意的：种子的引文一旦被改写，`OTHER` 巧合可能自然消失，
     * 那时「改前会误报」的复现就不再成立，本用例会先把这件事说出来，而不是让上面几条静默变成假绿。
     */
    @Test
    fun `itx-015 引文里的英文单词 other 是巧合命中的前提`() {
        val itx015 = seedEntries("kb_seed_itx.json").first { it.id == "itx-015" }
        val occurrences = Regex("other", RegexOption.IGNORE_CASE).findAll(itx015.payload).count()
        assertTrue(
            "itx-015 的 payload 里应至少出现 2 次英文单词 other（实际 $occurrences 次）",
            occurrences >= 2,
        )
        assertTrue("前置条件：payload 含英文单词 other", itx015.payload.contains("or other medicine"))
    }

    // ------------------------------------------------------------------
    // 词汇表覆盖度：防止「种子加了条目、词汇表没跟上」的静默失效
    // ------------------------------------------------------------------

    /**
     * 种子里每一个 `drug_a` / `drug_b` 取值，词汇表都必须**认得**。
     *
     * 这是本次改动最容易复发的地方：种子加一条新的 `drug_a`，而词汇表没有对应词条 →
     * 该条目对**所有**药单静默失效（连「至少能靠 nameKey 撞上」的旧行为都没有了）。
     * 断言方式：键名本身必须已被词汇表登记（[DrugInteractionKeys.isKnownKey]）。
     */
    @Test
    fun `种子里的每个 drug_a 与 drug_b 都被词汇表覆盖`() {
        val declared = seedEntries("kb_seed_itx.json").mapNotNull { e ->
            val p = org.json.JSONObject(e.payload)
            val a = p.optString("drug_a").takeIf { it.isNotBlank() && it != "null" }
            val b = p.optString("drug_b").takeIf { it.isNotBlank() && it != "null" }
            Triple(e.id, a, b)
        }
        assertEquals("itx 条目数", 15, declared.size)

        val unknown = mutableListOf<String>()
        for ((id, a, b) in declared) {
            for (key in listOfNotNull(a, b)) {
                if (!DrugInteractionKeys.isKnownKey(key)) unknown += "$id:$key"
            }
        }
        assertTrue(
            "以下 drug_a/drug_b 取值不在词汇表里（这些条目会对所有药单静默失效）：$unknown",
            unknown.isEmpty(),
        )
    }

    /** 词汇表必须认得「药单上真会出现的写法」——覆盖度不足会让条目到不了，过宽会让条目乱到。 */
    @Test
    fun `词汇表认得药单上的常见写法`() {
        assertEquals(
            "adalimumab 应归一到具体药 + TNF 类别 + 生物制剂",
            setOf("adalimumab", "tnf_inhibitor", "biologic"),
            DrugInteractionKeys.drugOf("adalimumab"),
        )
        assertTrue("中文通用名", DrugInteractionKeys.drugOf("依那西普").contains("tnf_inhibitor"))
        assertTrue("商品名", DrugInteractionKeys.drugOf("恩利").contains("tnf_inhibitor"))
        assertTrue("类别词本身", DrugInteractionKeys.drugOf("biologic").contains("biologic"))
        assertTrue("medClass 枚举名（大写）", DrugInteractionKeys.drugOf("NSAID").contains("nsaid"))
        assertTrue("补剂写法：维生素 D3 带空格", DrugInteractionKeys.drugOf("维生素 D3").contains("colecalciferol"))
        assertTrue("补剂写法：维生素d3 无空格", DrugInteractionKeys.drugOf("维生素d3").contains("colecalciferol"))
        assertTrue("品牌名钙尔奇 → calcium", DrugInteractionKeys.drugOf("钙尔奇").contains("calcium"))
        assertTrue("大小写不敏感", DrugInteractionKeys.drugOf("Etanercept").contains("tnf_inhibitor"))
    }

    /** 未收录的词不得凭空命中任何键——「不知道」就必须是 emptySet，不能兜底成 `other`。 */
    @Test
    fun `未收录的药名不产生任何键`() {
        assertTrue(DrugInteractionKeys.drugOf("zzz不存在的药").isEmpty())
        assertTrue(DrugInteractionKeys.drugOf("").isEmpty())
        assertTrue(DrugInteractionKeys.drugOf("   ").isEmpty())
    }
}
