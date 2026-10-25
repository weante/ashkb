package com.ashkb.app.ui.checkup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.2.11（缺陷工单 B08）：**AI 导入草稿**。
 *
 * 原实现（`CheckupLabImaging.kt` 的 `AiImportSheet`）把粘贴文本、解析结果、错误全放在普通
 * `remember` 里，于是转屏 / 进程重建后**草稿凭空消失**：用户刚把一份几十行的化验单粘进去、
 * 点完解析，屏幕一转，白忙一场。工单点名要求用 `rememberSaveable` 或 ViewModel 保住它。
 *
 * ### 为什么选 ViewModel 而不是 `rememberSaveable`
 *
 * 工单同时要求「大数据不要放进无界 Bundle」。`rememberSaveable` 的底层就是
 * `onSaveInstanceState` → `Bundle`，而粘贴文本正是**体量无上界**的那一类数据
 * （一份影像报告含所见/结论能到几十 KB）。放进 Bundle 有两重风险：
 * `TransactionTooLargeException`（Bundle 跨进程有 1 MB 上限，且是**整页**共享），
 * 以及进程重建后无法可靠恢复的静默丢稿。
 *
 * [CheckupViewModel] 在 `AppShell` 里是**常驻**的（`:208` 的 `viewModel(...)`），
 * 存进它的 `StateFlow` 既不进 Bundle、也不会因为 sheet 重开而丢。代价是进程被杀时草稿
 * 也会没——这与工单「进程被杀后的超大草稿若不能可靠保存，应明确提示并安全丢弃」一致，
 * 因为本方案根本不会把超大数据写进去（见 [MAX_CHARS]），丢的只是用户自己没提交的草稿。
 *
 * ### 为什么按 [ImportKind] 分开存
 *
 * 化验单与影像报告是两个独立入口（`CheckupScreen.kt:280-289` 各开一个 sheet）。
 * 若只存一份，用户从「化验」切到「影像」就会把刚粘好的化验单冲掉——而这两份草稿
 * 完全没有理由互相覆盖。
 */
internal data class AiImportDraft(
    /** 粘贴的原文；空串 = 没有草稿。 */
    val pasted: String,
) {
    val isBlank: Boolean get() = pasted.isBlank()

    internal companion object {
        /**
         * 单份草稿的字符上限（约 200 KB UTF-16）。
         *
         * 真实报告单远小于此值（一份 MRI 报告含所见/结论约 1–3 KB，整页生化检验单约 5 KB），
         * 这个上限的作用是**兜住异常输入**（误粘整篇文档、剪贴板里带二进制化的内容），
         * 而不是限制正常使用。超过就拒绝写入并提示用户，而不是让一个没有上界的字符串
         * 挂在常驻 ViewModel 上。
         */
        const val MAX_CHARS = 200_000
    }
}

/**
 * 草稿状态机（v1.2.11 / B08）——**从 `CheckupViewModel` 里抽出来，以便直接单测**。
 *
 * 抽出这个类的理由是测试而不是分层：`CheckupViewModel` 的构造参数里有
 * `AshkbApplication`，在 Robolectric 里实例化它要跑完整 `onCreate`（种子导入 + 全量闹钟
 * 重排），既慢又有副作用。而草稿的全部行为——写入 / 超限拒收 / 作废旧预览 / 保存后清空 /
 * 两个入口互不覆盖——都是**纯内存状态迁移**，与 Android 无关。
 *
 * 三条不变量（B08 的验收点就落在它们身上）：
 * 1. **超限拒收**：超过 [AiImportDraft.MAX_CHARS] 的写入被整体拒绝，且**保留原草稿**
 *    （静默截断会解析出缺行的化验单，用户看不出少了什么）。
 * 2. **草稿一变就作废预览**：否则改了几个字却还挂着上一次的预览，用户会以为改动已生效。
 * 3. **入库即清草稿**：重复导入也算「已在库里」，留着草稿会让用户以为还有未保存内容，
 *    再次点保存就真的重复写了（B01 的教训）。
 */
internal class AiImportDraftStore {

    private val _labDraft = MutableStateFlow("")
    private val _imagingDraft = MutableStateFlow("")
    private val _labParsed = MutableStateFlow(false)
    private val _imagingParsed = MutableStateFlow(false)

    val labDraft: StateFlow<String> = _labDraft.asStateFlow()
    val imagingDraft: StateFlow<String> = _imagingDraft.asStateFlow()

    /** 「当前草稿是否已被解析过」——预览是纯函数重算的，只需要记住这个事实。 */
    val labParsed: StateFlow<Boolean> = _labParsed.asStateFlow()
    val imagingParsed: StateFlow<Boolean> = _imagingParsed.asStateFlow()

    fun draftOf(kind: ImportKind): StateFlow<String> =
        if (kind == ImportKind.LAB) _labDraft.asStateFlow() else _imagingDraft.asStateFlow()

    fun parsedOf(kind: ImportKind): StateFlow<Boolean> =
        if (kind == ImportKind.LAB) _labParsed.asStateFlow() else _imagingParsed.asStateFlow()

    /**
     * 写入草稿；超过 [AiImportDraft.MAX_CHARS] 时**拒收并保留原草稿**。
     *
     * @return true = 已收下；false = 超限被拒（调用方据此提示用户）。
     */
    fun setDraft(kind: ImportKind, text: String): Boolean {
        if (text.length > AiImportDraft.MAX_CHARS) return false
        when (kind) {
            ImportKind.LAB -> {
                _labDraft.value = text
                _labParsed.value = false
            }
            ImportKind.IMAGING -> {
                _imagingDraft.value = text
                _imagingParsed.value = false
            }
        }
        return true
    }

    /** 记下「这份草稿已解析」——转屏后据此重建预览。 */
    fun markParsed(kind: ImportKind) {
        when (kind) {
            ImportKind.LAB -> _labParsed.value = true
            ImportKind.IMAGING -> _imagingParsed.value = true
        }
    }

    /** 清空草稿（入库完成 / 用户主动放弃时调用）。 */
    fun clearDraft(kind: ImportKind) {
        when (kind) {
            ImportKind.LAB -> {
                _labDraft.value = ""
                _labParsed.value = false
            }
            ImportKind.IMAGING -> {
                _imagingDraft.value = ""
                _imagingParsed.value = false
            }
        }
    }
}
