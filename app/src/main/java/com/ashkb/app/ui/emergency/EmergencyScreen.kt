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
import androidx.compose.material3.OutlinedTextField
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
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val meds by vm.meds.collectAsStateWithLifecycle()
    val today by vm.date.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var cards by remember { mutableStateOf<List<KbEntry>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<KbEntry?>(null) }
    var showContactForm by remember { mutableStateOf(false) }
    var showEventForm by remember { mutableStateOf(false) }
    // v1.0.80（批次 6）：编辑目标（null = 未打开）。删除入口在行内，编辑走同一张表单回填原值。
    var editContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var editEvent by remember { mutableStateOf<EmergencyEvent?>(null) }
    var showAllEvents by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    // v1.0.66 B6a：锁屏紧急信息开关
    var lockscreenEnabled by remember { mutableStateOf(EmergencyLockscreenStore.enabled(context)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { cards = vm.emergencyCards() }

    // 开关开启时，页面内数据一变就同步锁屏卡（用户正在编辑联系人 / 档案 / 药单）
    LaunchedEffect(lockscreenEnabled, contacts, profile, meds) {
        if (lockscreenEnabled) EmergencyLockscreenPublisher.refresh(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // 外层 AppShell 的 Scaffold 已吃掉系统栏内边距，这里置 0 防止双倍空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
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
        },
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
                // ---- 五应急场景卡（不可折叠，最先看到）----
                items(cards, key = { it.id }) { card ->
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

                // ---- v1.0.66 B6a：锁屏紧急信息开关 ----
                item {
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
                                checked = lockscreenEnabled,
                                onCheckedChange = { v ->
                                    lockscreenEnabled = v
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

                // ---- 紧急联系人 ----
                item {
                    SectionCard(
                        title = stringResource(R.string.emergency_contact_title),
                        subtitle = stringResource(R.string.emergency_call_a11y),
                        action = {
                            TextButton(onClick = { showContactForm = true }) { Text(stringResource(R.string.common_add)) }
                        },
                    ) {
                        if (contacts.isEmpty()) {
                            Text(
                                stringResource(R.string.emergency_no_contacts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // v1.0.90（批次 15）：单条联系人的排版抽成 ContactRow——
                            // 本函数已到 detekt 的 LongMethod / CyclomaticComplexMethod 阈值
                            // （基线只兜住**原始签名**，加一个 @OptIn 就不再匹配），
                            // 而「名称 / 关系 / 改删 / 拨号」本身是独立可读的一件事。
                            DividerList(contacts, key = { it.id }) { c ->
                                ContactRow(
                                    contact = c,
                                    onDial = { context.dial(c.phone) },
                                    onEdit = { editContact = c },
                                    onDelete = { vm.deleteContact(c.id) },
                                )
                            }
                        }
                    }
                }

                // ---- 个人信息（供急救人员参考）----
                item {
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
                        profile?.let { p ->
                            KeyValueRow(stringResource(R.string.profile_name), p.displayName)
                            KeyValueRow(stringResource(R.string.profile_diagnosis), p.diagnosis)
                            KeyValueRow("HLA-B27", Labels.hlaB27(p.hlaB27))
                            p.allergies?.let {
                                KeyValueRow(stringResource(R.string.profile_allergy_history), it, valueTone = StatusTone.Danger)
                            }
                            p.emergencyBloodType?.let { KeyValueRow(stringResource(R.string.profile_blood_type), it) }
                        } ?: Text(
                            stringResource(R.string.profile_not_built),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 当前用药：自动从药单汇总（不依赖用户手工维护），免疫抑制类置顶并标注。
                        // 刻意放在档案之外——用药与健康档案相互独立，未建档时也必须显示（急救场景尤甚）
                        val medsSummary = remember(meds, today) {
                            EmergencyMeds.summarize(meds, today.toString())
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.emergency_meds_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (medsSummary.isEmpty) {
                            Text(
                                stringResource(R.string.emergency_meds_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            medsSummary.ordered.forEach { e ->
                                KeyValueRow(
                                    label = e.name,
                                    value = e.detail,
                                    trailing = if (e.immunosuppressant) {
                                        {
                                            StatusChip(
                                                stringResource(R.string.emergency_meds_tag_immunosuppressant),
                                                StatusTone.Warning,
                                                Icons.Rounded.WarningAmber,
                                            )
                                        }
                                    } else null,
                                )
                            }
                            if (medsSummary.hiddenCount > 0) {
                                Text(
                                    stringResource(R.string.emergency_meds_more, medsSummary.hiddenCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (medsSummary.hasImmunosuppressant) {
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
                    }
                }

                // ---- 紧急事件记录 ----
                item {
                    SectionCard(
                        title = stringResource(R.string.emergency_events_title),
                        action = {
                            TextButton(onClick = { showEventForm = true }) { Text(stringResource(R.string.common_record)) }
                        },
                    ) {
                        if (events.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Schedule,
                                title = stringResource(R.string.emergency_events_empty),
                                body = stringResource(R.string.symptom_events_empty_hint),
                            )
                        } else {
                            val shown = if (showAllEvents) events else events.take(3)
                            DividerList(shown) { e ->
                                Column(Modifier.weight(1f)) {
                                    Text(e.date, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        EmergencyScene.fromKey(e.scene).label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    // v1.0.80（批次 6）：事件记录也能改 / 删（表单内删除）
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        TextButton(
                                            onClick = { editEvent = e },
                                            modifier = Modifier.heightIn(min = Size.touchMin),
                                        ) { Text(stringResource(R.string.common_edit)) }
                                        DestructiveAction(
                                            label = stringResource(R.string.common_delete),
                                            confirmTitle = stringResource(R.string.emergency_delete_event_confirm, e.date),
                                            confirmBody = stringResource(R.string.emergency_delete_event_note) + "\n" +
                                                stringResource(R.string.common_delete_irreversible),
                                            onConfirm = { vm.deleteEmergencyEvent(e.id) },
                                        )
                                    }
                                }
                                if (e.resolvedDate != null) {
                                    StatusChip(stringResource(R.string.symptom_outcome_done), StatusTone.Success, Icons.Rounded.CheckCircle)
                                } else {
                                    StatusChip(stringResource(R.string.symptom_flare_ongoing), StatusTone.Danger, Icons.Rounded.Schedule)
                                }
                            }
                            if (events.size > 3 && !showAllEvents) {
                                TextButton(
                                    onClick = { showAllEvents = true },
                                    modifier = Modifier.padding(top = Spacing.xs),
                                ) { Text("查看全部 ${events.size} 条") }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCard?.let { card ->
        KbDetailDialog(
            entry = card,
            onSaveNote = { vm.saveKbNote(card.id, it) },
            onDismiss = { selectedCard = null },
        )
    }

    if (showContactForm) ContactFormDialog(
        onSave = { vm.saveContact(it); showContactForm = false },
        onDismiss = { showContactForm = false },
    )

    // v1.0.80（批次 6）：编辑已存在的联系人与事件（回填原值、沿用主键）
    editContact?.let { c ->
        ContactFormDialog(
            existing = c,
            onSave = { vm.saveContact(it); editContact = null },
            onDismiss = { editContact = null },
        )
    }
    editEvent?.let { e ->
        EmergencyEventSheet(
            existing = e,
            onSave = { vm.saveEmergencyEvent(it); editEvent = null },
            onDismiss = { editEvent = null },
        )
    }

    if (showEventForm) EmergencyEventSheet(
        onSave = { vm.saveEmergencyEvent(it); showEventForm = false },
        onDismiss = { showEventForm = false },
    )
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
                OutlinedTextField(
                    name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true,
                    isError = attempted && name.isBlank(),
                    supportingText = { if (attempted && name.isBlank()) Text(stringResource(R.string.common_required)) },
                )
                OutlinedTextField(relation, { relation = it }, label = { Text(stringResource(R.string.emergency_relation)) }, singleLine = true)
                OutlinedTextField(
                    phone, { phone = it }, label = { Text(stringResource(R.string.emergency_phone)) }, singleLine = true,
                    isError = attempted && phone.isBlank(),
                    supportingText = { if (attempted && phone.isBlank()) Text(stringResource(R.string.common_required)) },
                )
                OutlinedTextField(hospital, { hospital = it }, label = { Text(stringResource(R.string.imaging_hospital_label)) }, singleLine = true)
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

// ===== 紧急事件表单（9 字段 + 单选 + 条件项，迁 ModalBottomSheet） =====

/**
 * v1.0.80（批次 6）：[existing] 非空 = 编辑（回填原值、沿用主键与记录时间）。
 *
 * `resolvedDate` 由「已缓解」勾选驱动：勾上写今天、取消则清空——保持与新增时同一条口径，
 * 避免出现「已填缓解日期却仍显示进行中」这种自相矛盾的行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyEventSheet(
    existing: EmergencyEvent? = null,
    onSave: (EmergencyEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = EventDraft.of(existing)
    var scene by remember(draft) { mutableStateOf(draft.scene) }
    var date by remember(draft) { mutableStateOf(draft.date) }
    var symptoms by remember(draft) { mutableStateOf(draft.symptoms) }
    var actions by remember(draft) { mutableStateOf(draft.actions) }
    var hospitalVisit by remember(draft) { mutableStateOf(draft.hospitalVisit) }
    var hospitalName by remember(draft) { mutableStateOf(draft.hospitalName) }
    var outcome by remember(draft) { mutableStateOf(draft.outcome) }
    var resolved by remember(draft) { mutableStateOf(draft.resolved) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var attempted by remember { mutableStateOf(false) }
    val dateOk = runCatching { LocalDate.parse(date.trim()) }.getOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(
                    if (existing == null) R.string.emergency_record_event else R.string.emergency_edit_event_title
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            Text(stringResource(R.string.emergency_scenario), style = MaterialTheme.typography.labelLarge)
            EmergencyScenePicker(selected = scene, onSelect = { scene = it })

            OutlinedTextField(
                date, { date = it }, label = { Text(stringResource(R.string.common_date)) }, singleLine = true,
                isError = attempted && !dateOk,
                supportingText = { if (attempted && !dateOk) Text(stringResource(R.string.common_date_format_hint2)) },
            )
            OutlinedTextField(symptoms, { symptoms = it }, label = { Text(stringResource(R.string.symptom_description)) })
            OutlinedTextField(actions, { actions = it }, label = { Text(stringResource(R.string.emergency_actions_taken)) })

            EventToggleRow(
                label = stringResource(R.string.emergency_seek_care),
                checked = hospitalVisit,
                onChange = { hospitalVisit = it },
            )
            if (hospitalVisit) {
                OutlinedTextField(hospitalName, { hospitalName = it }, label = { Text(stringResource(R.string.checkup_hospital_field)) }, singleLine = true)
            }

            EventToggleRow(
                label = stringResource(R.string.symptom_outcome_recovered),
                checked = resolved,
                onChange = { resolved = it },
            )
            if (resolved) {
                OutlinedTextField(outcome, { outcome = it }, label = { Text(stringResource(R.string.symptom_outcome_label)) }, singleLine = true)
            }

            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })

            Button(
                onClick = {
                    attempted = true
                    if (dateOk) {
                        onSave(
                            EventDraft(
                                scene, date, symptoms, actions, hospitalVisit, hospitalName, outcome, resolved, notes,
                            ).toEvent(existing)
                        )
                    }
                },
                enabled = dateOk,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/**
 * 勾选行（从 [EmergencyEventSheet] 抽出）。触摸目标 ≥48dp、整行可点（不只是那个方块），
 * 与联系人表单里两个勾选项同款——急诊时手抖，点方块的容错太低。
 */
@Composable
private fun EventToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 应急场景单选（从 [EmergencyEventSheet] 抽出：表单要控制长度与圈复杂度）。 */
@Composable
private fun EmergencyScenePicker(selected: EmergencyScene, onSelect: (EmergencyScene) -> Unit) {
    EmergencyScene.entries.forEach { s ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchMin)
                .selectable(selected = selected == s, role = Role.RadioButton, onClick = { onSelect(s) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == s, onClick = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(s.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * 紧急事件表单草稿 → 落库行（新增 / 编辑共用，理由同 [ContactDraft]）。
 *
 * `resolvedDate` 由「已缓解」勾选驱动：勾上写今天、取消则清空——与新增时同一口径，
 * 避免出现「填了缓解日期却仍显示进行中」这种自相矛盾的行。
 * 编辑时保留原有的 `severity` / `onsetTime`：它们不在表单里，不该被这次保存抹掉。
 */
private data class EventDraft(
    val scene: EmergencyScene,
    val date: String,
    val symptoms: String,
    val actions: String,
    val hospitalVisit: Boolean,
    val hospitalName: String,
    val outcome: String,
    val resolved: Boolean,
    val notes: String,
) {
    companion object {
        /**
         * 表单初值（新增给默认档、编辑回填原值）。
         *
         * 收进工厂而不是写在 Composable 里：九个字段的安全调用 + Elvis 会一条条叠加圈复杂度，
         * 直接顶穿 detekt 的 `CyclomaticComplexMethod` 阈值（表单里本就有勾选 / 条件项的嵌套分支）。
         */
        fun of(existing: EmergencyEvent?): EventDraft = EventDraft(
            // fromKey(null) 落到 INFECTION_FEVER，与新增时的默认场景一致
            scene = EmergencyScene.fromKey(existing?.scene),
            date = existing?.date ?: LocalDate.now().toString(),
            symptoms = existing?.symptoms ?: "",
            actions = existing?.actionsTaken ?: "",
            hospitalVisit = existing?.hospitalVisit ?: false,
            hospitalName = existing?.hospitalName ?: "",
            outcome = existing?.outcome ?: "",
            resolved = existing?.resolvedDate != null,
            notes = existing?.notes ?: "",
        )
    }

    fun toEvent(existing: EmergencyEvent?): EmergencyEvent = EmergencyEvent(
        id = existing?.id ?: "", date = date.trim(),
        recordedAt = existing?.recordedAt ?: nowIso(),
        scene = scene.name, severity = existing?.severity ?: "high",
        onsetTime = existing?.onsetTime,
        symptoms = symptoms.ifBlank { null },
        actionsTaken = actions.ifBlank { null },
        hospitalVisit = hospitalVisit,
        hospitalName = hospitalName.ifBlank { null },
        outcome = outcome.ifBlank { null },
        resolvedDate = if (resolved) LocalDate.now().toString() else null,
        notes = notes.ifBlank { null },
    )
}
