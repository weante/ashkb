package com.ashkb.app.domain

/**
 * K 知识库检索（v1.0.21 轻量优化）。
 *
 * **为何不迁 FTS4**：SQLite FTS4 内置分词器（simple / unicode61）按「字母数字连续段」切词，
 * 中文没有空格分隔，一整句会变成一个 token——本机 SQLite 3.50 实测：对
 * 「强直性脊柱炎患者用药注意事项」执行 `MATCH '强直'` 命中数为 **0**（现状 LIKE 为 1）。
 * 即迁 FTS4 会让中文子串搜索直接失效（Room 亦只提供 @Fts4，无 @Fts5；FTS5 的 trigram
 * 分词器虽支持 CJK 子串，但 minSdk 26 的 Android 8 上不可靠）。
 * 知识库为固定 47 条种子、无用户新增入口，故保留 LIKE 子串语义，改为
 * 「单列检索文本 + 查询防抖 + 结果上限」把每次检索的开销压下来。
 *
 * **为何检索文本不预先转小写**：SQLite 的 LIKE 默认对 ASCII 大小写不敏感
 * （`case_sensitive_like` 默认 OFF），小写化既无必要，还会引入 Kotlin（Unicode 折叠）
 * 与 SQLite `lower()`（仅 ASCII）的语义漂移。检索文本只做拼接。
 */
object KbSearch {

    /** 检索文本列名——实体、迁移、恢复回填、DAO 四处共用同一口径。 */
    const val COLUMN = "search_text"

    /** 结果上限：知识库规模远小于此，纯安全边界，防查询异常时全量返回。 */
    const val MAX_RESULTS = 200

    /** 查询防抖窗口（毫秒）：输入停顿后才真正查库，替代逐键查库。 */
    const val DEBOUNCE_MS = 250L

    /**
     * 单列检索文本 = 标题 + 摘要 + payload，以换行分隔三行。
     * 必须与迁移 v8→v9 及恢复回填里的 SQL 表达式
     * `title || char(10) || summary || char(10) || payload` 保持完全一致——
     * 分隔符用 char(10) 而非空格，避免跨字段拼出假匹配（如标题结尾 + 摘要开头凑成一个词）。
     */
    fun searchText(title: String, summary: String, payload: String): String =
        title + "\n" + summary + "\n" + payload

    /**
     * 与 SQL `search_text LIKE '%' || :q || '%'` 等价的纯逻辑判定（供单测锁定语义）。
     * 空检索文本（旧备份恢复后未回填）与空查询一律返回 false。
     * ASCII 大小写不敏感与 SQLite LIKE 一致；非 ASCII 带大小写字母（如 É）两者语义略有差异，
     * 本库内容为中文 + ASCII（药名/指标名），不受影响。
     */
    fun matches(searchText: String?, query: String): Boolean {
        if (searchText.isNullOrEmpty()) return false
        val q = query.trim()
        if (q.isEmpty()) return false
        return searchText.contains(q, ignoreCase = true)
    }
}
