package com.ashkb.app

import com.ashkb.app.AshkbApplication.Companion.KB_SEED_VERSION
import com.ashkb.app.AshkbApplication.Companion.SEED_FILES
import com.ashkb.app.AshkbApplication.Companion.SEED_FILES_EN
import com.ashkb.app.AshkbApplication.Companion.kbSeedNeedsImport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.5：**英文种子与中文种子的对等锁**。
 *
 * ### 为什么需要（与 `MedicalRulingSeedTest` 同一类问题，但更隐蔽）
 * 英文种子是从中文种子逐条译出的，两份必须**同形**。可一旦不同形，症状是静默的：
 *  · 少一条 → 英文用户永远看不到那条内容，界面不报错；
 *  · 受保护字段被顺手译了（`list_type` 译成「红榜」）→ `ExerciseEngine` 把它当未知值，
 *    走保守兜底，**禁忌动作的判定整体偏移**，而没有任何一行日志；
 *  · 字段名被改（`grade_matrix` → `gradeMatrix`）→ 同上，且更难查。
 * 逐一核对是不可靠的：48 条 × 两份，靠人眼比就是等下一次漏。
 *
 * ### 边界
 * 本测试**不**判断译文好坏——那由维护者与其医生决定。它只锁「结构没变、匹配键没变、
 * 中文没漏进不该漏的地方」。译文本身仍可能措辞不准，那是人工评审的事。
 *
 * ### 受保护字段的清单从哪来
 * 每一条都对应一处**逐字消费**该键的代码，写在 [PROTECTED_PAYLOAD_KEYS] 上方。
 * 新增「代码读了某个 payload 键」时必须同步加进来，否则这道门对它无效。
 */
class KbSeedEnglishParityTest {

    /**
     * 顶层里**绝不能因翻译而变**的字段：身份（`id`/`category`）、分级（`severity_level`/
     * `source_tier`）、链接（`source_url`）、日期（`adapted_at`/`review_due`）、`version`。
     */
    private val fixedTopLevelKeys = listOf(
        "id", "category", "severity_level", "source_tier",
        "source_url", "adapted_at", "review_due", "version",
    )

    /**
     * payload 里被 Kotlin 代码**逐字消费**的键 —— 翻译它们会静默改变行为，而不是只改观感。
     *
     *  · `kb_seed_itx.json`：`MedicationRepository.appliesTo` 只读 `drug_a`/`drug_b` 交给
     *    `DrugInteractionKeys.matches`；`risk_level` 与 `positive_interaction` 同属判定语义。
     *  · `kb_seed_exc.json`：`ExerciseEngine.evaluate` 解析 `list_type`（red/black）、`grade`
     *    （L1/L2/L3）、`cervical_condition`（none/cervical_only/always/amplitude_half）、
     *    `grade_matrix`（键 stable/controlled/flare/active → 值 recommend/allow/downgrade/…）、
     *    `block_rule`（`{stage:[…], cervical:bool}`）。**一个字符都不许改**：
     *    改错会让「禁忌动作」被判成可推荐，v1.0.77 的 fail-closed 修复正是为这个。
     *  · `kb_seed_fdg.json`：`payload.food` / `payload.drug_or_class` 是食物-药物匹配键。
     *  · `kb_seed_edu.json`：`payload.key` 是条目身份，`payload.value` 是数值阈值。
     *  · `kb_seed_emr.json`：无（`scene`/`urgency` 等只用于展示）。
     */
    private val protectedPayloadKeys: Map<String, List<String>> = mapOf(
        "kb_seed_itx.json" to listOf("drug_a", "drug_b", "risk_level", "positive_interaction"),
        "kb_seed_exc.json" to listOf(
            "list_type", "grade", "cervical_condition", "grade_matrix", "block_rule",
        ),
        "kb_seed_fdg.json" to listOf("food", "drug_or_class"),
        "kb_seed_edu.json" to listOf("key", "value"),
        "kb_seed_emr.json" to emptyList(),
    )

    /**
     * 允许残留中文的**唯一**两处，且是「引用」而非「漏译」：
     *  · `fdg-004.source_name`：`药智数据`（Yaozhi Data）是中国商业药品库的专名，无公认英文名；
     *  · `emr-004.source_name`：原文引述被更正掉的旧来源名，删掉就丢失了「曾经写错」这个事实。
     *
     * 两者都紧跟英文释义。除此之外英文种子里出现任何中文都算漏译——
     * 这正是「半中半英界面」的成因。
     */
    private val cjkAllowed = setOf(
        "kb_seed_emr.json#emr-004#source_name",
        "kb_seed_fdg.json#fdg-004#source_name",
    )

    /**
     * CJK 与全角标点。
     *
     * ⚠️ 刻意**不含** `U+FE00–U+FE0F`：那是变体选择符，`⚠️`（U+26A0 U+FE0F）
     * 第二个码位就落在里面，用 `\u2E80-\uFFEF` 这种宽范围会把纯英文条目误报成中文。
     */
    private val cjk = Regex("[\\u3000-\\u303F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\uFE30-\\uFE4F\\uFF00-\\uFFEF]")

    // ── 读盘 ────────────────────────────────────────────────────────────────

    /** 单测工作目录是模块目录（`app/`），与 `KbSeedVersionGateTest` 同一约定。 */
    private fun assetFile(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("找不到种子文件 $name（单测工作目录=${File(".").absolutePath}）")

    private fun entries(name: String): List<JSONObject> {
        val arr = JSONArray(assetFile(name).readText(Charsets.UTF_8))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    // ── 真正解析一遍 ─────────────────────────────────────────────────────────

    /**
     * 把真实文件内容喂给**生产解析器**（`AshkbApplication.parseSeedJson`）。
     *
     * 上面几条测试都是用 `org.json` 自己读的：它们能证明「结构与中文一致」，
     * 却证明不了「`parseSeed` 读得动」。而后者失败时是**完全静默**的——
     * `importKbSeedIfNeeded` 见到 `seeds.isEmpty()` 直接 `return`，不写闸门、不报错，
     * 用户只是永远看到中文知识库。字段名写错、某个值写成 JSON `null`、
     * 漏掉 `review_due` 都属于这一类。这就是抽取 [AshkbApplication.parseSeedJson] 的唯一理由。
     */
    @Test
    fun `中英种子都能被生产解析器读出全部条目`() {
        val expected = SEED_FILES.sumOf { entries(it).size }
        assertTrue("种子文件读出来是空的，测试本身没跑对", expected > 0)

        val zh = SEED_FILES.flatMap { AshkbApplication.parseSeedJson(assetFile(it).readText(Charsets.UTF_8)) }
        val en = SEED_FILES_EN.flatMap { AshkbApplication.parseSeedJson(assetFile(it).readText(Charsets.UTF_8)) }

        assertEquals("中文种子没被完整解析（生产解析器读不动，且失败是静默的）", expected, zh.size)
        assertEquals("英文种子没被完整解析——英文用户会静默退回中文知识库", expected, en.size)
        assertEquals("中英解析出的 id 序列不一致", zh.map { it.id }, en.map { it.id })

        for (e in en) {
            assertTrue("${e.id} 的标题为空", e.title.isNotBlank())
            assertTrue("${e.id} 的检索文本为空，知识库搜不到它", e.searchText.orEmpty().isNotBlank())
            assertTrue("${e.id} 的 payload 为空", e.payload.orEmpty().length > 2)
        }
    }

    // ── 结构工具 ────────────────────────────────────────────────────────────

    /** 递归展开成 `路径=字符串` 列表；数组下标写成 `[i]`。 */
    private fun flatten(value: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (value) {
            is JSONObject -> value.keys().forEach { flatten(value.get(it), "$path.$it", out) }
            is JSONArray -> (0 until value.length()).forEach { flatten(value.get(it), "$path[$it]", out) }
            else -> out += path to value.toString()
        }
    }

    /** 字段名 + 顺序 + 数组长度的递归签名；**不排序**，所以「字段被换位」也会被发现。 */
    private fun shape(value: Any?): String = when (value) {
        is JSONObject ->
            "{" + value.keys().asSequence().joinToString(",") { "$it:${shape(value.get(it))}" } + "}"
        is JSONArray -> "[" + (0 until value.length()).joinToString(",") { shape(value.get(it)) } + "]"
        is Boolean -> "bool"
        is Number -> "num"
        else -> "str"
    }

    /** 路径是否命中受保护键（`.drug_a`、`.grade_matrix.stable`、`.block_rule.stage[0]` 都算）。 */
    private fun isProtected(path: String, keys: List<String>): Boolean =
        keys.any { k -> path == ".$k" || path.startsWith(".$k.") || path.startsWith(".$k[") }

    // ── 测试 ────────────────────────────────────────────────────────────────

    @Test
    fun `英文种子清单与中文同源且文件都在`() {
        assertEquals(
            "SEED_FILES_EN 必须由 SEED_FILES 派生（否则会出现「某文件永远不被英文用户看到」）",
            SEED_FILES.map { "en/$it" },
            SEED_FILES_EN,
        )
        for (name in SEED_FILES_EN) assetFile(name) // 缺文件即在此大声失败
    }

    @Test
    fun `中英种子条目数相同且 id 顺序一致`() {
        for (name in SEED_FILES) {
            val zh = entries(name).map { it.getString("id") }
            val en = entries("en/$name").map { it.getString("id") }
            assertEquals("$name 条目数不一致", zh.size, en.size)
            assertEquals("$name 的 id 序列必须与中文完全一致（顺序也一致）", zh, en)
        }
    }

    @Test
    fun `英文种子的固定字段与中文逐字节一致`() {
        for (name in SEED_FILES) {
            val zh = entries(name)
            val en = entries("en/$name")
            for (i in zh.indices) {
                for (key in fixedTopLevelKeys) {
                    assertEquals(
                        "$name 第 $i 条（${zh[i].getString("id")}）的 $key 被改动了",
                        zh[i].get(key).toString(),
                        en[i].get(key).toString(),
                    )
                }
            }
        }
    }

    @Test
    fun `英文种子的 payload 与中文结构同形`() {
        for (name in SEED_FILES) {
            val zh = entries(name)
            val en = entries("en/$name")
            for (i in zh.indices) {
                assertEquals(
                    "$name 第 $i 条（${zh[i].getString("id")}）的 payload 字段名/顺序/数组长度与中文不同",
                    shape(zh[i].getJSONObject("payload")),
                    shape(en[i].getJSONObject("payload")),
                )
            }
        }
    }

    @Test
    fun `英文种子的受保护字段与中文逐字节一致`() {
        for (name in SEED_FILES) {
            val keys = protectedPayloadKeys.getValue(name)
            if (keys.isEmpty()) continue
            val zh = entries(name)
            val en = entries("en/$name")
            for (i in zh.indices) {
                val id = zh[i].getString("id")
                val a = mutableListOf<Pair<String, String>>()
                val b = mutableListOf<Pair<String, String>>()
                flatten(zh[i].getJSONObject("payload"), "", a)
                flatten(en[i].getJSONObject("payload"), "", b)
                val pa = a.filter { isProtected(it.first, keys) }
                val pb = b.filter { isProtected(it.first, keys) }
                assertEquals(
                    "$name 的 $id：受保护键（${keys.joinToString("/")}）在中英两侧对不上。" +
                        "它们是代码逐字消费的匹配键，翻译会静默改变行为。" +
                        "若确实要改这些键，必须同时改解析它们的 Kotlin 代码。",
                    pa,
                    pb,
                )
            }
        }
    }

    @Test
    fun `英文种子除引用的中文来源名外没有中文`() {
        val found = SEED_FILES_EN.flatMap { cjkPathsIn(it) }.toSet()
        // 白名单不许腐烂：列进去的必须真的还有中文，否则说明它早就不需要豁免了
        for (allowed in cjkAllowed) {
            assertTrue(
                "白名单里的 $allowed 已经不含中文了——把它从 cjkAllowed 删掉（否则豁免范围在悄悄扩大）",
                found.contains(allowed),
            )
        }
        assertEquals(
            "英文种子里出现了中文（漏译）。若确属「引述中文专名/旧来源名」，" +
                "请连同理由加进 cjkAllowed。",
            cjkAllowed,
            found,
        )
    }

    /** 一个英文种子里所有含 CJK 的叶子，写成 `文件名#id#路径`。 */
    private fun cjkPathsIn(name: String): List<String> {
        val short = name.removePrefix("en/")
        return entries(name).flatMap { entry ->
            val flat = mutableListOf<Pair<String, String>>()
            flatten(entry, "", flat)
            val id = entry.getString("id")
            flat.filter { cjk.containsMatchIn(it.second) }
                .map { "$short#$id#${it.first.removePrefix(".")}" }
        }
    }

    /**
     * 闸门真值表。两条分句各自对应一个真实发生过的静默缺口（见 [kbSeedNeedsImport] 的 KDoc）。
     */
    @Test
    fun `种子闸门在版本或语言变化时必须放行`() {
        assertFalse("版本与语言都一致时不该重复核对", kbSeedNeedsImport(KB_SEED_VERSION, "zh", "zh"))
        assertFalse(kbSeedNeedsImport(KB_SEED_VERSION, "en", "en"))
        assertTrue("切到英文必须重新核对", kbSeedNeedsImport(KB_SEED_VERSION, "zh", "en"))
        assertTrue("切回中文也必须重新核对", kbSeedNeedsImport(KB_SEED_VERSION, "en", "zh"))
        assertTrue("老设备没有语言记录时必须核对一次", kbSeedNeedsImport(KB_SEED_VERSION, null, "zh"))
        assertTrue("版本落后必须核对", kbSeedNeedsImport(KB_SEED_VERSION - 1, "zh", "zh"))
        assertTrue("从未导入过必须核对", kbSeedNeedsImport(0, null, "zh"))
        assertFalse("降级安装（记录的版本更高）时跳过，且不把已装内容往回滚",
            kbSeedNeedsImport(KB_SEED_VERSION + 1, "en", "en"))
    }
}
