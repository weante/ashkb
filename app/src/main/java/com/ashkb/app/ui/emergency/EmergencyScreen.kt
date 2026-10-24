package com.ashkb.app.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.ashkb.app.ui.components.AshkbTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.EmergencyScene
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.EmergencyLockscreenStore
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.EmergencyMeds
import com.ashkb.app.domain.Labels
import com.ashkb.app.domain.XiaomiCompat
import com.ashkb.app.reminder.EmergencyLockscreenPublisher
import com.ashkb.app.reminder.SystemSetupGuides
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.knowledge.KbDetailDialog
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun Context.dial(phone: String) {
    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        .onFailure { GlobalMessages.post(getString(R.string.emergency_dial_fail, it.message)) }
}

/**
 * 紧急卡（方案 §10.8 反向设计）：疼痛中、慌乱中、单手操作——
 * 字要大、按钮要大、信息要少。120 快拨固定在底部，不随内容滚动。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(vm: EmergencyViewModel, onBack: () -> Unit) {
    // v1.2.10（A 组剩余 / 审查报告第 30 条）：本函数**不再收任何数据流**。
    // 原先 contacts / events / profile / meds / today 五条流都收在这里，于是任何一条发射
    // 都会整页重组（含 5 张场景卡、联系人列表、档案与用药汇总）。现在每条流由真正
    // 用到它的那一个组合件自己收，重组面收敛到该卡本身。
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // 外层 AppShell 的 Scaffold 已吃掉系统栏内边距，这里置 0 防止双倍空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { EmergencyCallBar() },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenTopBar(title = stringResource(R.string.emergency_card_title), onBack = onBack)

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg,
                    top = Spacing.md, bottom = Spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { EmergencySceneCards(vm) }
                item { EmergencyLockscreenCard() }
                item { EmergencyContactsCard(vm) }
                item { EmergencyProfileCard(vm) }
                item { EmergencyEventsCard(vm) }
            }
        }
    }
}

/**
 * 底部固定的 120 快拨。
 *
 * 刻意留在根函数之外：它不依赖任何数据流，抽出来让 [EmergencyScreen] 只剩版式骨架，
 * 也避免以后往根函数里加东西时把这块一起卷进重组。
 */
@Composable
private fun EmergencyCallBar() {
    val context = LocalContext.current
    Button(
        onClick = { context.dial("120") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .height(Size.emergencyCallHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(
            Icons.Rounded.Call,
            contentDescription = null,   // 文字已承载语义
            modifier = Modifier.size(Size.iconLg),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.emergency_call_120), style = MaterialTheme.typography.headlineSmall)
    }
}

/**
 * 五张应急场景卡（不可折叠，最先看到）。
 *
 * 自持 `cards` 与「点开哪张」两个状态：场景卡只在进入页面时取一次数，
 * 把它留在根函数会让根函数为了一个一次性加载持有 `LaunchedEffect`。
 */
@Composable
private fun EmergencySceneCards(vm: EmergencyViewModel) {
    var cards by remember { mutableStateOf<List<KbEntry>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<KbEntry?>(null) }
    val today by vm.date.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { cards = vm.emergencyCards() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        cards.forEach { card ->
            AlertBanner(
                tone = StatusTone.Danger,
                icon = Icons.Rounded.WarningAmber,
                title = card.title,
                body = card.summary,
                actionLabel = stringResource(R.string.common_view_details),
                onAction = { selectedCard = card },
                titleStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }

    selectedCard?.let { card ->
        KbDetailDialog(
            entry = card,
            today = today.toString(),
            onSaveNote = { vm.saveKbNote(card.id, it) },
            onDismiss = { selectedCard = null },
        )
    }
}

/**
 * 锁屏紧急信息开关（v1.0.66 B6a）。
 *
 * 开关状态是本页唯一一份「与数据无关的本地状态」，故自持；
 * 数据侧的同步（联系人 / 档案 / 药单变了就刷新锁屏卡）由各卡片自己触发，
 * 见 [EmergencyContactsCard] / [EmergencyProfileCard]。
 */
@Composable
private fun EmergencyLockscreenCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(EmergencyLockscreenStore.enabled(context)) }

    SectionCard(
        title = stringResource(R.string.emergency_lockscreen_title),
        subtitle = stringResource(R.string.emergency_lockscreen_body),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.emergency_lockscreen_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    scope.launch {
                        if (v) {
                            EmergencyLockscreenStore.setEnabled(context, true)
                            EmergencyLockscreenPublisher.refresh(context)
                        } else {
                            EmergencyLockscreenPublisher.disable(context)
                        }
                    }
                },
            )
        }
        Text(
            stringResource(R.string.emergency_lockscreen_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // v1.0.72：小米 / HyperOS 专属提示。官方《适配常见问题》§9：MIUI **默认不允许
        // 应用在锁屏上显示内容/Activity**，需用户主动授予「锁屏显示」；§10：该权限
        // **没有查询接口** → 只能给文字 + 跳转按钮，不做状态行。
        if (XiaomiCompat.isXiaomi(Build.BRAND, Build.MANUFACTURER)) {
            Text(
                stringResource(R.string.emergency_lockscreen_miui_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            OutlinedButton(
                onClick = {
                    if (!SystemSetupGuides.openMiuiPermissionEditor(context)) {
                        GlobalMessages.post(context.getString(R.string.settings_open_failed))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            ) { Text(stringResource(R.string.reminder_miui_open)) }
        }
    }
}

/**
 * 紧急联系人卡。
 *
 * 自持「新增 / 编辑」两个表单状态——原先它们连同四个对话框都挂在根函数上，
 * 每次联系人数据变化都会带着整页一起重组。
 *
 * v1.0.90（批次 15）：单条联系人的排版抽成 [ContactRow]——原函数已到 detekt 的
 * LongMethod / CyclomaticComplexMethod 阈值（基线只兜住**原始签名**，加一个 @OptIn
 * 就不再匹配），而「名称 / 关系 / 改删 / 拨号」本身是独立可读的一件事。
 */
@Composable
private fun EmergencyContactsCard(vm: EmergencyViewModel) {
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmergencyContact?>(null) }

    // 联系人变了就同步锁屏卡（开关关闭时 refresh 自身是空操作，但这里先判一次省一次 IO）
    LaunchedEffect(contacts) {
        if (EmergencyLockscreenStore.enabled(context)) EmergencyLockscreenPublisher.refresh(context)
    }

    SectionCard(
        title = stringResource(R.string.emergency_contact_title),
        subtitle = stringResource(R.string.emergency_call_a11y),
        action = {
            TextButton(onClick = { showForm = true }) { Text(stringResource(R.string.common_add)) }
        },
    ) {
        if (contacts.isEmpty()) {
            Text(
                stringResource(R.string.emergency_no_contacts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DividerList(contacts, key = { it.id }) { c ->
                ContactRow(
                    contact = c,
                    onDial = { context.dial(c.phone) },
                    onEdit = { editing = c },
                    onDelete = { vm.deleteContact(c.id) },
                )
            }
        }
    }

    if (showForm) ContactFormDialog(
        onSave = { vm.saveContact(it); showForm = false },
        onDismiss = { showForm = false },
    )

    // v1.0.80（批次 6）：编辑已存在的联系人（回填原值、沿用主键）
    editing?.let { c ->
        ContactFormDialog(
            existing = c,
            onSave = { vm.saveContact(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/**
 * 个人信息卡（供急救人员参考）+ 当前用药汇总。
 *
 * 自持 `profile` / `meds` / `today` 三条流与导出状态。用药汇总**刻意放在档案之外**：
 * 用药与健康档案相互独立，未建档时也必须显示（急救场景尤甚）。
 */
@Composable
private fun EmergencyProfileCard(vm: EmergencyViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val meds by vm.meds.collectAsStateWithLifecycle()
    val today by vm.date.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }

    // 档案 / 药单变了就同步锁屏卡
    LaunchedEffect(profile, meds) {
        if (EmergencyLockscreenStore.enabled(context)) EmergencyLockscreenPublisher.refresh(context)
    }

    val medsSummary = remember(meds, today, context) {
        // v1.2.5（i18n）：remember 的 lambda 不是 @Composable，取不到 stringResource，
        // 故注入 context::getString（急救卡按 KDoc 用 plainRes）
        EmergencyMeds.summarize(
            meds, today.toString(),
            freqLabel = { context.getString(it.plainRes) },
            // v1.2.10（审查 §6.9）：读不出来的频次原样印出存库值（不印「每日」）
            unknownFreqLabel = { context.getString(R.string.ui_emergency_meds_unknown_freq, it) },
            // v1.2.6（i18n）：注射周期文案同样按系统语言解析
            injCycleLabel = { context.getString(R.string.ui_emergency_meds_inj_cycle, it) },
            // v1.2.6（i18n）：商品名括号随语言换全角 / 半角
            brandParen = { context.getString(R.string.ui_brand_paren, it) },
        )
    }

    SectionCard(
        title = stringResource(R.string.me_my_info),
        subtitle = stringResource(R.string.emergency_for_paramedics),
        action = {
            val shareTitle = stringResource(R.string.emergency_share_card)
            TextButton(
                onClick = {
                    if (!exporting) {
                        exporting = true
                        vm.exportCardPdf(
                            onReady = { intent ->
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(intent, shareTitle)
                                    )
                                }
                                exporting = false
                            },
                            onError = {
                                GlobalMessages.post(it)
                                exporting = false
                            },
                        )
                    }
                },
                enabled = !exporting,
            ) {
                Icon(
                    Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(Size.iconSm),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(if (exporting) stringResource(R.string.backup_generating) else stringResource(R.string.emergency_export_print))
            }
        },
    ) {
        ProfileKeyValues(profile)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.emergency_meds_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmergencyMedsList(medsSummary)
    }
}

/** 档案四行（姓名 / 诊断 / HLA-B27 / 过敏史 / 血型），未建档时给一句说明。 */
@Composable
private fun ProfileKeyValues(p: com.ashkb.app.data.entity.Profile?) {
    p?.let { profile ->
        KeyValueRow(stringResource(R.string.profile_name), profile.displayName)
        KeyValueRow(stringResource(R.string.profile_diagnosis), profile.diagnosis)
        KeyValueRow("HLA-B27", stringResource(Labels.hlaB27(profile.hlaB27)))
        profile.allergies?.let {
            KeyValueRow(stringResource(R.string.profile_allergy_history), it, valueTone = StatusTone.Danger)
        }
        profile.emergencyBloodType?.let {
            KeyValueRow(stringResource(R.string.profile_blood_type), it)
        }
    } ?: Text(
        stringResource(R.string.profile_not_built),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 当前用药汇总的渲染（免疫抑制类置顶 + 尾部警示）。
 *
 * 从 [EmergencyProfileCard] 抽出：那段「空态 / 列表 / 隐藏计数 / 免疫抑制提示」四分支
 * 合起来已超出 LongMethod 阈值，而它本身就是独立可读的一件事。
 */
@Composable
private fun EmergencyMedsList(s: EmergencyMeds.Summary) {
    if (s.isEmpty) {
        Text(
            stringResource(R.string.emergency_meds_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    s.ordered.forEach { e ->
        // ⚠️ v1.2.2：这里**不能**用带 `trailing` 的 [KeyValueRow]。
        //
        // 实测（维护者截图）：药名「依那西普（恩利）」+ 剂量
        // 「25mg · 每周两次 · 每 14 天」+ 胶囊「免疫抑制」三者同排时，
        // 剂量列被压到**每行只剩 4–5 个字**（竖排成柱状），右侧却空着一大片。
        // `KeyValueRow` 的 KDoc 把「胶囊优先、极端情况下数值列可能被很长
        // 的标签吃到 0 宽」写成**有意的取舍**（v1.1.1），但那条是为
        // **320dp 窄屏 + 2.0× 字号**写的；这里在**正常字号**下就已不可读，
        // 说明它不是那个极端情形，而是这一行的**固有矛盾**：
        // 三个无界文本（药名 / 剂量 / 标记）塞不进一行。
        //
        // 改法：胶囊与**药名同行**（两者都短且有界），
        // 剂量**独占整行**并在必要时折行 —— 剂量是急救时真正要读的
        // 数字，它不该为布局让步。
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    e.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (e.immunosuppressant) {
                    StatusChip(
                        stringResource(R.string.emergency_meds_tag_immunosuppressant),
                        StatusTone.Warning,
                        Icons.Rounded.WarningAmber,
                    )
                }
            }
            Text(
                e.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (s.hiddenCount > 0) {
        Text(
            stringResource(R.string.emergency_meds_more, s.hiddenCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (s.hasImmunosuppressant) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,   // 装饰性：正文已承载语义
                tint = StatusTone.Warning.accent(),
                modifier = Modifier.size(Size.iconSm),
            )
            Text(
                stringResource(R.string.emergency_meds_note),
                style = MaterialTheme.typography.bodySmall,
                color = StatusTone.Warning.accent(),
            )
        }
    }
}

/**
 * 紧急事件记录卡（默认只展开 3 条）。
 *
 * 自持「新增 / 编辑 / 展开全部」三个状态。
 *
 * v1.0.92（批次 17）：单条事件抽成 [EventRow]——理由与 v1.0.90 的 [ContactRow] 相同：
 * 原函数已在 detekt 基线里（`LongMethod` / `CyclomaticComplexMethod`，按
 * 「文件 + 完整签名」记账），而 FlowRow 需要的 `@OptIn(ExperimentalLayoutApi::class)`
 * 一旦加到那个函数上，签名就不再匹配基线。
 */
@Composable
private fun EmergencyEventsCard(vm: EmergencyViewModel) {
    val events by vm.events.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmergencyEvent?>(null) }
    var showAll by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.emergency_events_title),
        action = {
            TextButton(onClick = { showForm = true }) { Text(stringResource(R.string.common_record)) }
        },
    ) {
        if (events.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Schedule,
                title = stringResource(R.string.emergency_events_empty),
                body = stringResource(R.string.symptom_events_empty_hint),
            )
        } else {
            val shown = if (showAll) events else events.take(3)
            DividerList(shown) { e ->
                EventRow(
                    event = e,
                    onEdit = { editing = e },
                    onDelete = { vm.deleteEmergencyEvent(e.id) },
                )
            }
            if (events.size > 3 && !showAll) {
                TextButton(
                    onClick = { showAll = true },
                    modifier = Modifier.padding(top = Spacing.xs),
                ) { Text(stringResource(R.string.ui_emergency_view_all_events, events.size)) }
            }
        }
    }

    if (showForm) EmergencyEventSheet(
        onSave = { vm.saveEmergencyEvent(it); showForm = false },
        onDismiss = { showForm = false },
    )

    // v1.0.80（批次 6）：编辑已存在的事件（回填原值、沿用主键）
    editing?.let { e ->
        EmergencyEventSheet(
            existing = e,
            onSave = { vm.saveEmergencyEvent(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/**
 * 单条紧急事件（从 [EmergencyScreen] 抽出，理由与 [ContactRow] 相同：那边已在 detekt 基线里，
 * 而本行需要 `@OptIn(ExperimentalLayoutApi::class)`，加到那个函数上就不再匹配基线签名）。
 *
 * v1.0.92（批次 17）：改 / 删由 `Row` 改 `FlowRow`。
 *
 * **本卡真正的挤压点在这一层，不在外层。** 外层「正文列 `weight(1f)`（默认 `fill = true`）
 * + 尾部 `StatusChip`（无权重）」本身是安全的：`Row` 先测无权重子项，尾部胶囊因此恒拿得到
 * 自己的固有宽（本卡「已转归」/「进行中」≈77dp @1.0×、≈116dp @2.0×），正文列被钉在
 * 「行宽 − 间距 − 胶囊宽」上、放不下时自己折行。危险的是正文列**内部**这一行：它的宽就是
 * 上面那份剩余宽，而两个按钮都无权重——放不下时 `Row` 按顺序分剩余宽，后一个按钮会被压到
 * `Size.touchMin` 之下并逐字折行（与 v1.0.90 修好的联系人行完全同形）。
 *
 * 最窄宽度（算术模型，字宽按 CJK 1em 估；本模块**无 Compose UI 测试依赖**，v1.0.90 实测过
 * Robolectric 的字体度量不可用，故这里是算式而非运行时测量）：
 *  · 改前：两个按钮并排需要 `btn + Spacing.xs + Size.touchMin`（`btn = max(58dp, 2×15sp + 24dp)`），
 *    加上页面/卡片边距与尾部胶囊，安全下限 = `72 + (38 + 39×fontScale) + btn + 52`
 *    → 默认字号 259dp、1.5× 289.5dp、2.0× **324dp**（320dp 屏 + 2.0× 字号时「删除」只剩 44dp）。
 *  · 改后：`FlowRow` 放不下就把整项折到下一行，每项保底自己的固有宽（≥ 58dp ≥ `Size.touchMin`），
 *    安全下限降到 `72 + (38 + 39×fontScale) + 58` → 默认字号 207dp、2.0× 246dp
 *    （真实设备最窄 320dp 下 2.0× 仍有 74dp 余量）。
 *
 * 尾部胶囊保持无权重：状态是这一行最需要一眼看到的信息，不为排版让位。
 * 刻意**不**改用 [com.ashkb.app.ui.components.WeightedTrailingRow]：该组件的 `leading` 是
 * 必填项且 `leading` 与 `content` 之间恒插一个固定间距，本卡没有前缀——传空 `leading` 会给
 * 每条事件行凭空加 16dp 左缩进（可见的版式变化）；而且它只管一层行，够不到这里真正出问题的
 * 嵌套按钮行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowScope.EventRow(
    event: EmergencyEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.weight(1f)) {
        Text(event.date, style = MaterialTheme.typography.titleSmall)
        // v1.2.10（审查 §6.9）：场景读不出来时印**原始存库值**，不退回「感染发热」。
        // 退回等于把一支读不懂的值显示成一个明确诊断——用户会以为记录本来就是感染发热。
        val sceneLabel = EmergencyScene.fromKeyOrNull(event.scene)?.labelRes
        Text(
            if (sceneLabel != null) stringResource(sceneLabel) else event.scene,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // v1.0.80（批次 6）：事件记录也能改 / 删（表单内删除）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_edit)) }
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                // v1.0.92（批次 17）：与「编辑」同款保底。`TextButton` 自身的高度下限只有 40dp，
                // 并排时被「编辑」的 48dp 盖住看不出问题，折到单独一行时就露出来了（同 ContactRow）。
                modifier = Modifier.heightIn(min = Size.touchMin),
                confirmTitle = stringResource(R.string.emergency_delete_event_confirm, event.date),
                confirmBody = stringResource(R.string.emergency_delete_event_note) + "\n" +
                    stringResource(R.string.common_delete_irreversible),
                onConfirm = onDelete,
            )
        }
    }
    if (event.resolvedDate != null) {
        StatusChip(stringResource(R.string.symptom_outcome_done), StatusTone.Success, Icons.Rounded.CheckCircle)
    } else {
        StatusChip(stringResource(R.string.symptom_flare_ongoing), StatusTone.Danger, Icons.Rounded.Schedule)
    }
}

/**
 * 单条紧急联系人（从 [EmergencyScreen] 抽出：那边已到 detekt 的 `LongMethod` /
 * `CyclomaticComplexMethod` 阈值，而「名称 / 关系 / 改删 / 拨号」是独立可读的一件事）。
 *
 * v1.0.90（批次 15）：改 / 删由 `Row` 改 `FlowRow`。本列宽是外层 `Row` 里 `weight(1f)` 的
 * **剩余宽**，右边「拨号」按钮按 `c.phone` 的固有宽度先测（长号码格式 `+86 138 0013 8000`
 * ≈196dp）→ 本列只剩 ≈44dp，`Row` 会把「编辑」压到 28dp、「删除」压到 12dp 逐字折行——
 * 两个按钮都掉到 48dp 触达区之下（不可点）。`FlowRow` 放不下就整项折到下一行，每项保底
 * `Size.touchMin`。电话按钮保持无权重：号码是这一行最有用的信息，不能为排版让位；
 * 它的文字本身可折行，缩不到零宽。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowScope.ContactRow(
    contact: EmergencyContact,
    onDial: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(contact.name, style = MaterialTheme.typography.titleMedium)
        contact.relation?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // v1.0.80（批次 6）：改 / 删联系人。
        // 此前这一区只有「添加」与「拨号」——号码录错一位就只能再加一条，
        // 而 deleteContact 明明已经在仓库层躺了很久，界面上却没有入口。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_edit)) }
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                modifier = Modifier.heightIn(min = Size.touchMin),
                confirmTitle = stringResource(R.string.emergency_delete_contact_confirm, contact.name),
                confirmBody = stringResource(R.string.emergency_delete_contact_note) + "\n" +
                    stringResource(R.string.common_delete_irreversible),
                onConfirm = onDelete,
            )
        }
    }
    FilledTonalButton(
        onClick = onDial,
        modifier = Modifier.heightIn(min = Size.touchComfort),
    ) {
        Icon(
            Icons.Rounded.Call,
            contentDescription = null,
            modifier = Modifier.size(Size.iconSm),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(contact.phone)
    }
}

// ===== 联系人表单（字段少，保留 AlertDialog；校验错误可见 + 可读） =====
/**
 * v1.0.80（批次 6）：[existing] 非空 = 编辑（回填原值并沿用主键 / 创建时间）。
 * 删除入口在联系人行内（`DestructiveAction`），不在这张表单里——急诊场景下的表格越短越好，
 * 而删除是低频动作（与药品「停用」放行内同款安排）。
 */
@Composable
private fun ContactFormDialog(
    existing: EmergencyContact? = null,
    onSave: (EmergencyContact) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = ContactDraft.of(existing)
    var name by remember(draft) { mutableStateOf(draft.name) }
    var relation by remember(draft) { mutableStateOf(draft.relation) }
    var phone by remember(draft) { mutableStateOf(draft.phone) }
    var isEmergency by remember(draft) { mutableStateOf(draft.isEmergency) }
    var isDoctor by remember(draft) { mutableStateOf(draft.isDoctor) }
    var hospital by remember(draft) { mutableStateOf(draft.hospital) }
    var attempted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.emergency_add_contact else R.string.emergency_edit_contact_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AshkbTextField(
                    name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true,
                    isError = attempted && name.isBlank(),
                    supportingText = { if (attempted && name.isBlank()) Text(stringResource(R.string.common_required)) },
                )
                AshkbTextField(
                    relation, { relation = it }, label = { Text(stringResource(R.string.emergency_relation)) },
                    singleLine = true,
                )
                AshkbTextField(
                    phone, { phone = it }, label = { Text(stringResource(R.string.emergency_phone)) }, singleLine = true,
                    isError = attempted && phone.isBlank(),
                    supportingText = { if (attempted && phone.isBlank()) Text(stringResource(R.string.common_required)) },
                )
                AshkbTextField(
                    hospital, { hospital = it }, label = { Text(stringResource(R.string.imaging_hospital_label)) },
                    singleLine = true,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.touchMin)
                        .toggleable(value = isEmergency, role = Role.Checkbox, onValueChange = { isEmergency = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isEmergency, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.emergency_contact_title), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.touchMin)
                        .toggleable(value = isDoctor, role = Role.Checkbox, onValueChange = { isDoctor = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isDoctor, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.profile_primary_doctor), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onSave(
                            ContactDraft(name, relation, phone, isEmergency, isDoctor, hospital)
                                .toContact(existing)
                        )
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * 联系人表单草稿 → 落库行（新增 / 编辑共用）。
 *
 * 抽出来是为了两件事：① 「编辑沿用原主键与创建时间」只有一处实现；
 * ② 这些 `?:` / `ifBlank` 兜底会给 Composable 叠加圈复杂度，撞上 detekt 的
 * `CyclomaticComplexMethod`（表单里本就一堆 isError/supportingText 分支）。
 */
private data class ContactDraft(
    val name: String,
    val relation: String,
    val phone: String,
    val isEmergency: Boolean,
    val isDoctor: Boolean,
    val hospital: String,
) {
    companion object {
        /** 表单初值（新增给默认值、编辑回填原值）。收进工厂的理由同 [EventDraft.of]。 */
        fun of(existing: EmergencyContact?): ContactDraft = ContactDraft(
            name = existing?.name ?: "",
            relation = existing?.relation ?: "",
            phone = existing?.phone ?: "",
            isEmergency = existing?.isEmergency ?: true,
            isDoctor = existing?.isDoctor ?: false,
            hospital = existing?.hospital ?: "",
        )
    }

    fun toContact(existing: EmergencyContact?): EmergencyContact = EmergencyContact(
        id = existing?.id ?: "", name = name.trim(),
        relation = relation.ifBlank { null },
        phone = phone.trim(), isEmergency = isEmergency,
        isDoctor = isDoctor,
        hospital = hospital.ifBlank { null },
        createdAt = existing?.createdAt ?: nowIso(), updatedAt = nowIso(),
    )
}
