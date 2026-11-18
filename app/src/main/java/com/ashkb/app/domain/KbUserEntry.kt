package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 知识库**用户自建条目**：字段校验、payload 编解码、导入 / 导出 JSON。
 *
 * ### 为什么用 id 前缀区分，而不是新增 `is_user` 列
 * 新增列要动 Room 表结构：`ASHKB_DB_VERSION` 21 → 22、导出 schema、`ALL_MIGRATIONS` 补一条、
 * 备份恢复按名列表回填、四份文档同步（`verifySpecSync` 会红）。为一件「条目来自谁」的属性
 * 付这么多联动代价不划算，而 id 命名空间本来就是这个库表达「这一类行是什么」的既有手段
 * （`itx-` / `exc-` / `fdg-` / `emr-` / `edu-` 五类种子前缀）。故用户条目统一用 `usr-` 前缀，
 * 与种子前缀天然不冲突。
 *
 * 由此得到两条**结构性保证**（不依赖任何一处调用方自觉）：
 *  · `KbSeedRefresh.plan` 只遍历种子清单，`usr-` 条目永远不会被种子刷新新增 / 覆盖 / 删除；
 *  · 备份恢复仍是整库回填（恢复=整机状态，这是既有语义，不是本次改动）。
 *
 * ### 为什么层级记 `USR` 而不是借用 S1–S4 / SYS
 * 层级表达的是**内容可信度来源**。用户自建内容没有外部来源，借用任何一档都是不实陈述
 * （与 v1.1.3 把无来源条目单列 `SYS` 同一个理由）。`USR` 只在 `KbSourceTiers` 里有释义。
 *
 * ### 为什么 `review_due` 记 `9999-12-31`
 * 复核到期是**种子**的维护机制（外部来源会过期）。用户条目没有外部来源可复核，
 * 若给一个近期日期，它会在知识库页顶出一条「N 条待复核」的假告警，并写入 alerts 表。
 * 记一个永不到期的日期，让既有的 `reviewDue < today` 判定自然为 false，不需要在
 * 任何读取侧加特判。
 */
object KbUserEntry {

    /** 用户自建条目的 id 前缀（与种子前缀 itx-/exc-/fdg-/emr-/edu- 不冲突） */
    const val ID_PREFIX = "usr-"

    /** 用户自建条目的证据层级：既不是 S1–S4，也不是 SYS（无外部来源的系统内置） */
    const val TIER = "USR"

    /** 用户条目没有外部复核义务，用一个永不到期的日期占位 */
    const val REVIEW_DUE_NEVER = "9999-12-31"

    /** 导出 / 导入文件的格式标识与版本 */
    const val EXPORT_FORMAT = "ashkb-kb-user"
    const val EXPORT_FORMAT_VERSION = 1

    // ---- 长度上限：按详情页排版与检索列的实际承载能力定的经验值 ----
    const val MAX_TITLE = 100
    const val MAX_SUMMARY = 300
    const val MAX_CONTENT = 4000
    const val MAX_ITEMS = 20
    const val MAX_ITEM_LENGTH = 300
    const val MAX_SOURCE_NAME = 100
    const val MAX_SOURCE_URL = 500

    /** 单次导入的条目上限：防一个畸形文件把库撑爆（种子 48 条，个人条目量级远低于此） */
    const val MAX_IMPORT_ENTRIES = 200

    /** 导入文件体积上限（字符数）：2M 字符已远超合理体量，超过即判定为选错文件 */
    const val MAX_IMPORT_CHARS = 2_000_000

    /** 允许的分类（与种子五类一致） */
    val CATEGORIES = listOf("interaction", "food_drug", "exercise", "emergency", "edu")

    /** 允许的风险级别（与种子 severity_level 取值一致） */
    val SEVERITIES = listOf("high", "medium", "low")

    fun isUserEntry(id: String): Boolean = id.startsWith(ID_PREFIX)

    /**
     * 编辑态草稿：UI 表单与领域层之间唯一的传参形状。
     *
     * `id` 为 null = 新建；非 null = 编辑既有用户条目（种子条目不允许走到这里）。
     */
    data class Draft(
        val id: String? = null,
        val category: String = "interaction",
        val title: String = "",
        val summary: String = "",
        val severity: String = "low",
        val content: String = "",
        val actions: List<String> = emptyList(),
        val evidence: List<String> = emptyList(),
        val sourceName: String = "",
        val sourceUrl: String = "",
    )

    /** 校验失败项。UI 侧逐项映射成人话（领域层不持有 Context，不能直接给文案）。 */
    enum class Error {
        TITLE_REQUIRED,
        TITLE_TOO_LONG,
        SUMMARY_TOO_LONG,
        CONTENT_REQUIRED,
        CONTENT_TOO_LONG,
        CATEGORY_UNKNOWN,
        SEVERITY_UNKNOWN,
        TOO_MANY_ITEMS,
        ITEM_TOO_LONG,
        SOURCE_NAME_TOO_LONG,
        SOURCE_URL_TOO_LONG,
        SOURCE_URL_INVALID,
    }

    /** 导入失败原因（整份文件级；单条不合规则计入 skipped 而不是整体失败）。 */
    enum class ImportError { TOO_LARGE, BAD_JSON, NO_ENTRIES, TOO_MANY }

    /** 导入解析结果：Ready = 可写入的草稿 + 被跳过条数；Rejected = 整份文件不可用。 */
    sealed interface ImportPlan {
        data class Ready(val drafts: List<Draft>, val skipped: Int) : ImportPlan
        data class Rejected(val error: ImportError) : ImportPlan
    }

    /** 去掉首尾空白与空行（要点 / 依据逐条 trim，空行丢弃）——校验与落库都用规范化后的值 */
    fun normalize(draft: Draft): Draft = draft.copy(
        title = draft.title.trim(),
        summary = draft.summary.trim(),
        content = draft.content.trim(),
        sourceName = draft.sourceName.trim(),
        sourceUrl = draft.sourceUrl.trim(),
        actions = draft.actions.map { it.trim() }.filter { it.isNotEmpty() },
        evidence = draft.evidence.map { it.trim() }.filter { it.isNotEmpty() },
    )

    /** 全量校验；返回空列表即通过。 */
    fun validate(draft: Draft): List<Error> {
        val d = normalize(draft)
        val errors = mutableListOf<Error>()
        when {
            d.title.isEmpty() -> errors += Error.TITLE_REQUIRED
            d.title.length > MAX_TITLE -> errors += Error.TITLE_TOO_LONG
        }
        if (d.summary.length > MAX_SUMMARY) errors += Error.SUMMARY_TOO_LONG
        when {
            d.content.isEmpty() -> errors += Error.CONTENT_REQUIRED
            d.content.length > MAX_CONTENT -> errors += Error.CONTENT_TOO_LONG
        }
        if (d.category !in CATEGORIES) errors += Error.CATEGORY_UNKNOWN
        if (d.severity !in SEVERITIES) errors += Error.SEVERITY_UNKNOWN
        if (d.actions.size + d.evidence.size > MAX_ITEMS) errors += Error.TOO_MANY_ITEMS
        if ((d.actions + d.evidence).any { it.length > MAX_ITEM_LENGTH }) errors += Error.ITEM_TOO_LONG
        if (d.sourceName.length > MAX_SOURCE_NAME) errors += Error.SOURCE_NAME_TOO_LONG
        if (d.sourceUrl.length > MAX_SOURCE_URL) {
            errors += Error.SOURCE_URL_TOO_LONG
        } else if (d.sourceUrl.isNotEmpty() && !isHttpUrl(d.sourceUrl)) {
            errors += Error.SOURCE_URL_INVALID
        }
        return errors
    }

    /**
     * payload 编码：`{"content": "...", "action": ["..."], "evidence": ["..."]}`。
     *
     * 只写详情页 `KbDetailDialog` 已经会渲染的键（`content` / `action` / `evidence`），
     * 不发明新键——这样新增这类条目不需要改详情页的渲染路径，也不会出现「存了但看不见」的字段。
     */
    fun payloadOf(draft: Draft): String {
        val d = normalize(draft)
        val o = JSONObject()
        o.put("content", d.content)
        if (d.actions.isNotEmpty()) o.put("action", JSONArray(d.actions))
        if (d.evidence.isNotEmpty()) o.put("evidence", JSONArray(d.evidence))
        return o.toString()
    }

    /** 从既有条目反解出草稿（编辑态回填）。payload 畸形时按空内容处理，不抛。 */
    fun draftOf(entry: KbEntry): Draft {
        val p = runCatching { JSONObject(entry.payload) }.getOrDefault(JSONObject())
        return Draft(
            id = entry.id,
            category = entry.category,
            title = entry.title,
            summary = entry.summary,
            severity = entry.severityLevel,
            content = p.optString("content"),
            actions = p.stringList("action"),
            evidence = p.stringList("evidence"),
            sourceName = entry.sourceName,
            sourceUrl = entry.sourceUrl,
        )
    }

    /**
     * 草稿 → 实体行。
     *
     * @param id 新建时由调用方用 `Ids.new("usr")` 生成；编辑时传既有 id
     * @param existing 既有行（编辑），用于继承 `userNote` 与版本号递增
     * @param today 创建日期（注入的「今天」，不读系统时钟）
     */
    fun toEntry(draft: Draft, existing: KbEntry?, id: String, today: String): KbEntry {
        val d = normalize(draft)
        val payload = payloadOf(d)
        return KbEntry(
            id = id,
            category = d.category,
            title = d.title,
            summary = d.summary,
            severityLevel = d.severity,
            // 「适用场景」是种子用来描述触发时机的字段（如「开始用药时」），用户条目没有这个概念
            applicableScene = "",
            sourceName = d.sourceName,
            sourceUrl = d.sourceUrl,
            sourceTier = TIER,
            adaptedAt = existing?.adaptedAt ?: today,
            reviewDue = REVIEW_DUE_NEVER,
            version = (existing?.version ?: 0) + 1,
            payload = payload,
            // 与迁移 SQL / 种子导入同一口径：title + 换行 + summary + 换行 + payload
            searchText = KbSearch.searchText(d.title, d.summary, payload),
            userNote = existing?.userNote,
        )
    }

    /**
     * 导出为 JSON 文本。只导出用户条目（种子条目随 App 版本分发，不该混进用户的搬运文件）。
     */
    fun exportJson(entries: List<KbEntry>, exportedAt: String): String {
        val arr = JSONArray()
        entries.filter { isUserEntry(it.id) }.forEach { e ->
            val d = draftOf(e)
            val o = JSONObject()
            o.put("category", d.category)
            o.put("title", d.title)
            o.put("summary", d.summary)
            o.put("severity", d.severity)
            o.put("content", d.content)
            if (d.actions.isNotEmpty()) o.put("action", JSONArray(d.actions))
            if (d.evidence.isNotEmpty()) o.put("evidence", JSONArray(d.evidence))
            o.put("source_name", d.sourceName)
            o.put("source_url", d.sourceUrl)
            o.put("created_at", e.adaptedAt)
            arr.put(o)
        }
        val root = JSONObject()
        root.put("format", EXPORT_FORMAT)
        root.put("format_version", EXPORT_FORMAT_VERSION)
        root.put("exported_at", exportedAt)
        root.put("count", arr.length())
        root.put("entries", arr)
        return root.toString()
    }

    /**
     * 解析导入文件。
     *
     * 宽容度刻意放在「整份文件」层级，而**不是**放到单条：顶层既接受
     * `{"format":"ashkb-kb-user","entries":[...]}` 也接受裸数组（便于用户用文本编辑器手写），
     * 但单条必须过 [validate]，不合规的条目只计入 `skipped`，不阻断其余条目——
     * 一次导入里有一条脏数据就整份失败，对批量搬运是灾难性的体验。
     */
    fun parseExport(text: String): ImportPlan {
        if (text.length > MAX_IMPORT_CHARS) return ImportPlan.Rejected(ImportError.TOO_LARGE)
        val trimmed = text.trim()
        val array = runCatching {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("entries")
        }.getOrNull() ?: return ImportPlan.Rejected(ImportError.BAD_JSON)
        if (array.length() == 0) return ImportPlan.Rejected(ImportError.NO_ENTRIES)
        if (array.length() > MAX_IMPORT_ENTRIES) return ImportPlan.Rejected(ImportError.TOO_MANY)
        val drafts = mutableListOf<Draft>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val draft = array.optJSONObject(i)?.let { draftOfJson(it) }
            if (draft == null || validate(draft).isNotEmpty()) skipped++ else drafts += draft
        }
        if (drafts.isEmpty()) return ImportPlan.Rejected(ImportError.NO_ENTRIES)
        return ImportPlan.Ready(drafts, skipped)
    }

    /**
     * 去重键：同分类 + 同标题 + 同正文 = 同一条。
     *
     * 为什么不做「同标题就算重复」：知识库里同一个标题下分不同场景各写一条是正常用法
     * （比如「运动前热身」在稳定期和活动期各一份），只按标题去重会把它们吃掉。
     * 为什么不做内容模糊比对：宁可留下两条几乎一样的，也不要静默丢掉一条用户写的东西。
     */
    fun dedupeKey(draft: Draft): String = draft.category + "\u0000" + draft.title + "\u0000" + draft.content

    /**
     * 与存量个人条目比对去重（v1.2.18）。
     *
     * 场景：用户把同一份导出文件导入两次、或从两台设备互导——此前每次都整批新增，
     * 列表里就会出现成对的重复条目，且没有批量删除入口，只能一条条删。
     *
     * 只与**存量个人条目**比对（种子条目的正文在 payload 的 `content` 字段，`draftOf` 同样能还原，
     * 但种子条目 id 不是 `usr-`，用户不会把它当作「自己的条目」，所以这里不参与比对）。
     *
     * @return 保留下来的草稿 与 被判为重复而跳过的条数
     */
    fun dropDuplicates(drafts: List<Draft>, existing: List<KbEntry>): Pair<List<Draft>, Int> {
        val seen = existing
            .filter { isUserEntry(it.id) }
            .map { dedupeKey(draftOf(it)) }
            .toMutableSet()
        var duplicates = 0
        val kept = ArrayList<Draft>(drafts.size)
        drafts.forEach { draft ->
            if (seen.add(dedupeKey(draft))) kept += draft else duplicates++
        }
        return kept to duplicates
    }

    /** 单条 JSON（导入用）：缺分类 / 风险级别时给默认值，其余交给 [validate] 裁决 */
    private fun draftOfJson(o: JSONObject): Draft = Draft(
        id = null,
        category = o.optString("category").ifBlank { CATEGORIES.first() },
        title = o.optString("title"),
        summary = o.optString("summary"),
        severity = o.optString("severity").ifBlank { SEVERITIES.last() },
        content = o.optString("content"),
        actions = o.stringList("action"),
        evidence = o.stringList("evidence"),
        sourceName = o.optString("source_name"),
        sourceUrl = o.optString("source_url"),
    )

    private fun JSONObject.stringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    private fun isHttpUrl(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")
}
