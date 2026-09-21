package com.ashkb.app.ui.wellness

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.repo.RecipeRepository.RecipeView
import com.ashkb.app.domain.RecipeSeeds
import com.ashkb.app.domain.RecipeSources
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/**
 * B3（v1.0.39）：推荐食谱库（route `recipes`）。
 *
 * 三段交互：
 *  1. 标签 chip 筛选（本地态在 VM），收藏置顶由仓储 SQL 排序保证；
 *  2. 点卡片 → 详情弹层（配料 / 做法 / 出处 / 免责声明），弹层内可编辑或删除；
 *  3. 「新建食谱」→ 表单弹层（标签多选；编辑既有条目时预填）。
 *
 * 出处正文**只在详情里出现**：列表里放题录会把每张卡撑得很长，反而看不清食谱本身。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(vm: RecipesViewModel, onBack: () -> Unit) {
    val all by vm.recipes.collectAsStateWithLifecycle()
    val filterTag by vm.filterTag.collectAsStateWithLifecycle()

    // detailId 而非对象：收藏 / 编辑后 Room 会推新实例，用 id 每次取最新那条，弹层不会停在旧值
    var detailId by remember { mutableStateOf<String?>(null) }
    var formFor by remember { mutableStateOf<RecipeView?>(null) }
    var showForm by remember { mutableStateOf(false) }

    // 筛选：null = 全部；否则只保留带该标签的食谱（收藏置顶顺序保持仓储给的顺序）
    // 先取普通 val：`by` 委托属性无法智能转换，不能直接在 lambda 里当非空用
    val activeTag = filterTag
    val list = remember(all, activeTag) {
        if (activeTag == null) all else all.filter { activeTag in it.tags }
    }
    val detail = detailId?.let { id -> all.firstOrNull { it.recipe.id == id } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.recipes_title), onBack = onBack)

        // 标签筛选行：横向可滚，条目少时不换行（与 KnowledgeScreen 的分类筛选同形态）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = filterTag == null,
                onClick = { vm.setFilter(null) },
                label = { Text(stringResource(R.string.recipes_filter_all)) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            )
            RecipeSeeds.ALL_TAGS.forEach { tag ->
                val label = RecipeSeeds.tagLabel(tag)
                FilterChip(
                    selected = filterTag == tag,
                    // 再点同一标签 = 取消筛选，回到「全部」
                    onClick = { vm.setFilter(if (filterTag == tag) null else tag) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (list.isEmpty()) {
                item(key = "recipes-empty") {
                    SectionCard(title = stringResource(R.string.recipes_title)) {
                        EmptyState(
                            icon = Icons.Rounded.Restaurant,
                            title = stringResource(R.string.recipes_empty),
                            actionLabel = stringResource(R.string.recipes_add),
                            onAction = {
                                formFor = null
                                showForm = true
                            },
                        )
                    }
                }
            } else {
                items(list, key = { it.recipe.id }) { view ->
                    RecipeCard(
                        view = view,
                        onClick = { detailId = view.recipe.id },
                        onToggleFavorite = { vm.toggleFavorite(view) },
                    )
                }
            }
            item(key = "recipes-add") {
                Button(
                    onClick = {
                        formFor = null
                        showForm = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.recipes_add)) }
            }
        }
    }

    detail?.let { view ->
        RecipeDetailSheet(
            view = view,
            // 编辑：先关详情再开表单（多层弹层同时挂在 Composition 上，返回手势会先吃掉一层）
            onEdit = {
                detailId = null
                formFor = view
                showForm = true
            },
            onDelete = {
                vm.delete(view.recipe.id)
                detailId = null
            },
            onDismiss = { detailId = null },
        )
    }

    if (showForm) {
        RecipeFormSheet(
            vm = vm,
            current = formFor,
            onDismiss = {
                showForm = false
                formFor = null
            },
        )
    }
}

/** 标签 → 胶囊配色：三重编码（色 + 文字），不用纯颜色区分意义。 */
private fun tagTone(tag: String): StatusTone = when (tag) {
    RecipeSeeds.TAG_ANTI -> StatusTone.Brand
    RecipeSeeds.TAG_GUT -> StatusTone.Success
    RecipeSeeds.TAG_CALORIE -> StatusTone.Info
    else -> StatusTone.Neutral
}

/** 列表卡：标题 + 标签 chip + 内置 / 自建徽标 + 收藏按钮（不展示出处正文）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(
    view: RecipeView,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val recipe = view.recipe
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    text = stringResource(
                        if (recipe.isSeed) R.string.recipes_seed_badge else R.string.recipes_custom_badge
                    ),
                    tone = if (recipe.isSeed) StatusTone.Info else StatusTone.Neutral,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                view.tags.forEach { tag ->
                    StatusChip(text = RecipeSeeds.tagLabel(tag), tone = tagTone(tag))
                }
            }
            // 收藏按钮形变不换文案语义：未收藏显示「收藏」，已收藏显示「取消收藏」
            TextButton(onClick = onToggleFavorite, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Icon(
                    imageVector = if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,   // 装饰性：按钮文案已承载语义
                    modifier = Modifier.size(Size.iconSm),
                    tint = if (recipe.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(
                        if (recipe.isFavorite) R.string.recipes_fav_remove else R.string.recipes_fav_add
                    )
                )
            }
        }
    }
}

/**
 * 详情弹层：配料 / 做法逐行展示，出处按 `RecipeSources.of` 展开成「编号 · 题录 + 括号备注」，
 * 末尾固定挂全局免责声明。自建食谱无出处 → 整个出处小节不渲染。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailSheet(
    view: RecipeView,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val recipe = view.recipe
    val sources = RecipeSources.of(view.sources)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetBody {
            Text(recipe.title, style = MaterialTheme.typography.titleLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                StatusChip(
                    text = stringResource(
                        if (recipe.isSeed) R.string.recipes_seed_badge else R.string.recipes_custom_badge
                    ),
                    tone = if (recipe.isSeed) StatusTone.Info else StatusTone.Neutral,
                )
                view.tags.forEach { tag ->
                    StatusChip(text = RecipeSeeds.tagLabel(tag), tone = tagTone(tag))
                }
            }

            LineSection(
                title = stringResource(R.string.recipes_field_ingredients),
                content = recipe.ingredients,
            )
            LineSection(
                title = stringResource(R.string.recipes_field_steps),
                content = recipe.steps,
            )
            recipe.notes?.takeIf { it.isNotBlank() }?.let { note ->
                LineSection(title = stringResource(R.string.recipes_field_notes), content = note)
            }

            if (sources.isNotEmpty()) {
                Text(
                    stringResource(R.string.recipes_sources_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                sources.forEach { source ->
                    Text(
                        stringResource(R.string.recipes_source_line, source.id, source.citation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.recipes_source_note, source.note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 免责声明固定展示：种子与自建一视同仁
            Text(
                RecipeSources.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = Size.touchMin)) {
                    Text(stringResource(R.string.recipes_edit))
                }
                Spacer(Modifier.weight(1f))
                // 破坏性动作：用 error 色与「编辑」拉开距离，避免并排时误触
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = Size.touchMin)) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 自建 / 编辑表单：名称、标签多选、配料、做法、备注。
 * 编辑既有条目时 `current` 非空并预填（`id` 沿用原 id）；配料 / 做法按 `\n` 原样预填与提交。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeFormSheet(
    vm: RecipesViewModel,
    current: RecipeView?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(current?.recipe?.title ?: "") }
    var tags by remember { mutableStateOf(current?.tags.orEmpty()) }
    var ingredients by remember { mutableStateOf(current?.recipe?.ingredients ?: "") }
    var steps by remember { mutableStateOf(current?.recipe?.steps ?: "") }
    var notes by remember { mutableStateOf(current?.recipe?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetBody {
            Text(
                stringResource(if (current == null) R.string.recipes_add else R.string.recipes_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.recipes_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.recipes_field_tags), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                RecipeSeeds.ALL_TAGS.forEach { tag ->
                    FilterChip(
                        selected = tag in tags,
                        // 多选：点一下加入、再点移出
                        onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                        label = { Text(RecipeSeeds.tagLabel(tag)) },
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    )
                }
            }
            OutlinedTextField(
                value = ingredients,
                onValueChange = { ingredients = it },
                label = { Text(stringResource(R.string.recipes_field_ingredients)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it },
                label = { Text(stringResource(R.string.recipes_field_steps)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.recipes_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.save(
                        id = current?.recipe?.id,
                        title = title.trim(),
                        tags = tags,
                        ingredients = ingredients,
                        steps = steps,
                        notes = notes.ifBlank { null },
                    )
                    onDismiss()
                },
                enabled = title.isNotBlank() && ingredients.isNotBlank() && steps.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/** 详情小节：标题 + 内容按 `\n` 拆行逐条列出（配料 / 做法 / 备注共用）。 */
@Composable
private fun LineSection(title: String, content: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    content.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            Text(
                "· $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
}

/** 弹层内容列的统一骨架：横向留白 + 可滚 + 键盘避让（与 WellnessScreen 的表单同形态）。 */
@Composable
private fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}
