package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.domain.KbUserEntry
import com.ashkb.app.ui.components.AshkbTextField
import com.ashkb.app.ui.components.FilterChips
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.2.16：个人条目的新建 / 编辑表单（底部弹层）。
 *
 * ### 为什么是弹层而不是新页面
 * 知识库主体永远是「48 条种子 + 少量自己写的」。为偶尔写一条就跳页，会把「浏览 / 检索」
 * 这个主路径变深一层；弹层关掉即回到原来的列表位置，滚动状态不丢。
 *
 * ### 表单校验的两段式
 * 输入过程中**不**实时标红（边打字边报错只会让人烦），点过一次保存之后才打开错误显示；
 * 之后每次输入都会重算，改对一处立刻消一处。真正的拦截在 [KbUserEntry.validate]
 * （domain 纯函数，有单测），UI 只负责把 [KbUserEntry.Error] 翻译成人话。
 *
 * ### 为什么拆成三段
 * detekt `LongMethod` 阈值 80 行。整张表单一屏放不下、又是一整段声明式布局，
 * 拆成「基础 / 详情 / 来源」三段既压住行数，也正好对上用户填写时的三段心理（先写是什么、
 * 再写怎么做、最后补出处）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbEditSheet(
    initial: KbEntry?,
    onDismiss: () -> Unit,
    onSave: (KbUserEntry.Draft) -> Unit,
) {
    // 以条目 id（新建时 null）为 key 重建草稿：切换编辑对象时不会串行。
    val start = remember(initial) {
        initial?.let { KbUserEntry.draftOf(it) } ?: KbUserEntry.Draft()
    }
    val form = remember(start) { DraftForm(start) }
    var showErrors by remember(start) { mutableStateOf(false) }

    val draft = form.toDraft()
    val errors = if (showErrors) KbUserEntry.validate(draft).toSet() else emptySet()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(
                    if (start.id == null) R.string.kb_user_new_title else R.string.kb_user_edit_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            BasicFields(form = form, errors = errors)
            DetailFields(form = form, errors = errors)
            SourceFields(form = form, errors = errors)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.kb_edit_personal_notice),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            Button(
                onClick = {
                    showErrors = true
                    val candidate = form.toDraft()
                    if (KbUserEntry.validate(candidate).isEmpty()) onSave(candidate)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin),
            ) {
                Text(stringResource(R.string.kb_edit_save))
            }
        }
    }
}

/** 表单状态。单独一个类是为了让每个 composable 只接收「一个表单」，而不是十几个 lambda。 */
private class DraftForm(initial: KbUserEntry.Draft) {
    val id: String? = initial.id
    var title by mutableStateOf(initial.title)
    var summary by mutableStateOf(initial.summary)
    var content by mutableStateOf(initial.content)
    var actionsText by mutableStateOf(initial.actions.joinToString("\n"))
    var evidenceText by mutableStateOf(initial.evidence.joinToString("\n"))
    var sourceName by mutableStateOf(initial.sourceName)
    var sourceUrl by mutableStateOf(initial.sourceUrl)
    var category by mutableStateOf(initial.category)
    var severity by mutableStateOf(initial.severity)

    fun toDraft(): KbUserEntry.Draft = KbUserEntry.normalize(
        KbUserEntry.Draft(
            id = id,
            category = category,
            title = title,
            summary = summary,
            severity = severity,
            content = content,
            actions = splitLines(actionsText),
            evidence = splitLines(evidenceText),
            sourceName = sourceName,
            sourceUrl = sourceUrl,
        ),
    )
}

/** 第一段：标题 / 摘要 / 分类 / 风险级别——「这是什么」。 */
@Composable
private fun BasicFields(form: DraftForm, errors: Set<KbUserEntry.Error>) {
    AshkbTextField(
        value = form.title,
        onValueChange = { form.title = it },
        label = { Text(stringResource(R.string.kb_edit_field_title)) },
        placeholder = { Text(stringResource(R.string.kb_edit_hint_title)) },
        singleLine = true,
        isError = errors.any { it.isTitleError() },
        supportingText = supportText(
            errors.firstOrNull { it.isTitleError() },
            KbUserEntry.Error.TITLE_REQUIRED to R.string.kb_edit_err_required,
            KbUserEntry.Error.TITLE_TOO_LONG to R.string.kb_edit_err_title_too_long,
        ),
    )
    AshkbTextField(
        value = form.summary,
        onValueChange = { form.summary = it },
        label = { Text(stringResource(R.string.kb_edit_field_summary)) },
        placeholder = { Text(stringResource(R.string.kb_edit_hint_summary)) },
        maxLines = 3,
        isError = errors.contains(KbUserEntry.Error.SUMMARY_TOO_LONG),
        supportingText = supportText(
            errors.firstOrNull { it == KbUserEntry.Error.SUMMARY_TOO_LONG },
            KbUserEntry.Error.SUMMARY_TOO_LONG to R.string.kb_edit_err_summary_too_long,
        ),
    )
    CategoryPicker(selected = form.category, onSelect = { form.category = it })
    SeverityPicker(selected = form.severity, onSelect = { form.severity = it })
}

/** 第二段：正文 / 要点 / 证据——「怎么做、凭据是什么」。 */
@Composable
private fun DetailFields(form: DraftForm, errors: Set<KbUserEntry.Error>) {
    AshkbTextField(
        value = form.content,
        onValueChange = { form.content = it },
        label = { Text(stringResource(R.string.kb_edit_field_content)) },
        placeholder = { Text(stringResource(R.string.kb_edit_hint_content)) },
        minLines = 6,
        isError = errors.any { it.isContentError() },
        supportingText = supportText(
            errors.firstOrNull { it.isContentError() },
            KbUserEntry.Error.CONTENT_REQUIRED to R.string.kb_edit_err_required,
            KbUserEntry.Error.CONTENT_TOO_LONG to R.string.kb_edit_err_content_too_long,
        ),
    )
    AshkbTextField(
        value = form.actionsText,
        onValueChange = { form.actionsText = it },
        label = { Text(stringResource(R.string.kb_edit_field_actions)) },
        minLines = 3,
        isError = errors.any { it.isItemError() },
        supportingText = supportText(
            errors.firstOrNull { it.isItemError() },
            KbUserEntry.Error.TOO_MANY_ITEMS to R.string.kb_edit_err_too_many_items,
            KbUserEntry.Error.ITEM_TOO_LONG to R.string.kb_edit_err_item_too_long,
        ),
    )
    AshkbTextField(
        value = form.evidenceText,
        onValueChange = { form.evidenceText = it },
        label = { Text(stringResource(R.string.kb_edit_field_evidence)) },
        minLines = 2,
    )
}

/** 第三段：来源名称 / 链接——都可留空。 */
@Composable
private fun SourceFields(form: DraftForm, errors: Set<KbUserEntry.Error>) {
    AshkbTextField(
        value = form.sourceName,
        onValueChange = { form.sourceName = it },
        label = { Text(stringResource(R.string.kb_edit_field_source_name)) },
        placeholder = { Text(stringResource(R.string.kb_edit_hint_source_name)) },
        singleLine = true,
        isError = errors.contains(KbUserEntry.Error.SOURCE_NAME_TOO_LONG),
        supportingText = supportText(
            errors.firstOrNull { it == KbUserEntry.Error.SOURCE_NAME_TOO_LONG },
            KbUserEntry.Error.SOURCE_NAME_TOO_LONG to R.string.kb_edit_err_source_name_too_long,
        ),
    )
    AshkbTextField(
        value = form.sourceUrl,
        onValueChange = { form.sourceUrl = it },
        label = { Text(stringResource(R.string.kb_edit_field_source_url)) },
        placeholder = { Text(stringResource(R.string.kb_edit_hint_source_url)) },
        singleLine = true,
        isError = errors.any { it.isSourceUrlError() },
        supportingText = supportText(
            errors.firstOrNull { it.isSourceUrlError() },
            KbUserEntry.Error.SOURCE_URL_TOO_LONG to R.string.kb_edit_err_source_url_too_long,
            KbUserEntry.Error.SOURCE_URL_INVALID to R.string.kb_edit_err_source_url_invalid,
        ),
    )
}

/** 分类：与列表页同一个五分类口径（`vm_kb_category_*`）。 */
@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    val categories = KbUserEntry.CATEGORIES
    Column {
        Text(
            text = stringResource(R.string.kb_edit_field_category),
            style = MaterialTheme.typography.labelMedium,
        )
        FilterChips(
            labels = categories.map { stringResource(categoryLabelRes(it)) },
            selectedIndex = categories.indexOf(selected).coerceAtLeast(0),
            onSelect = { onSelect(categories[it]) },
        )
    }
}

/** 风险级别：复用既有的 severity_* 文案（high / medium / low）。 */
@Composable
private fun SeverityPicker(selected: String, onSelect: (String) -> Unit) {
    val severities = KbUserEntry.SEVERITIES
    Column {
        Text(
            text = stringResource(R.string.kb_edit_field_severity),
            style = MaterialTheme.typography.labelMedium,
        )
        FilterChips(
            labels = severities.map { stringResource(severityLabelRes(it)) },
            selectedIndex = severities.indexOf(selected).coerceAtLeast(0),
            onSelect = { onSelect(severities[it]) },
        )
    }
}

/** 取第一处命中的错误翻成字符串；没有错误返回 null（AshkbTextField 就不渲染 supportingText）。 */
@Composable
private fun supportText(
    error: KbUserEntry.Error?,
    vararg messages: Pair<KbUserEntry.Error, Int>,
): (@Composable () -> Unit)? {
    val res = messages.firstOrNull { it.first == error }?.second ?: return null
    return { Text(stringResource(res), color = MaterialTheme.colorScheme.error) }
}

/** 多行输入 → 条目列表：按行切、逐条 trim、丢掉空行。 */
private fun splitLines(text: String): List<String> =
    text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

private fun KbUserEntry.Error.isTitleError(): Boolean =
    this == KbUserEntry.Error.TITLE_REQUIRED || this == KbUserEntry.Error.TITLE_TOO_LONG

private fun KbUserEntry.Error.isContentError(): Boolean =
    this == KbUserEntry.Error.CONTENT_REQUIRED || this == KbUserEntry.Error.CONTENT_TOO_LONG

private fun KbUserEntry.Error.isItemError(): Boolean =
    this == KbUserEntry.Error.TOO_MANY_ITEMS || this == KbUserEntry.Error.ITEM_TOO_LONG

private fun KbUserEntry.Error.isSourceUrlError(): Boolean =
    this == KbUserEntry.Error.SOURCE_URL_TOO_LONG || this == KbUserEntry.Error.SOURCE_URL_INVALID

private fun categoryLabelRes(category: String): Int = when (category) {
    "interaction" -> R.string.vm_kb_category_interaction
    "food_drug" -> R.string.vm_kb_category_food_drug
    "exercise" -> R.string.vm_kb_category_exercise
    "emergency" -> R.string.vm_kb_category_emergency
    else -> R.string.vm_kb_category_edu
}

private fun severityLabelRes(severity: String): Int = when (severity) {
    "high" -> R.string.severity_high
    "medium" -> R.string.severity_moderate
    else -> R.string.severity_low
}
