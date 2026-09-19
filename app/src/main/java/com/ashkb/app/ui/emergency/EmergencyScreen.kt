package com.ashkb.app.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.EmergencyMeds
import com.ashkb.app.domain.Labels
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.AlertBanner
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
    var showAllEvents by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { cards = vm.emergencyCards() }

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
                            DividerList(contacts, key = { it.id }) { c ->
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                ) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    c.relation?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { context.dial(c.phone) },
                                    modifier = Modifier.heightIn(min = Size.touchComfort),
                                ) {
                                    Icon(
                                        Icons.Rounded.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(Size.iconSm),
                                    )
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(c.phone)
                                }
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

    selectedCard?.let { KbDetailDialog(entry = it, onDismiss = { selectedCard = null }) }

    if (showContactForm) ContactFormDialog(
        onSave = { vm.saveContact(it); showContactForm = false },
        onDismiss = { showContactForm = false },
    )

    if (showEventForm) EmergencyEventSheet(
        onSave = { vm.saveEmergencyEvent(it); showEventForm = false },
        onDismiss = { showEventForm = false },
    )
}

// ===== 联系人表单（字段少，保留 AlertDialog；校验错误可见 + 可读） =====
@Composable
private fun ContactFormDialog(onSave: (EmergencyContact) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isEmergency by remember { mutableStateOf(true) }
    var isDoctor by remember { mutableStateOf(false) }
    var hospital by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_add_contact)) },
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
                            EmergencyContact(
                                id = "", name = name.trim(),
                                relation = relation.ifBlank { null },
                                phone = phone.trim(), isEmergency = isEmergency,
                                isDoctor = isDoctor,
                                hospital = hospital.ifBlank { null },
                                createdAt = nowIso(), updatedAt = nowIso(),
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ===== 紧急事件表单（9 字段 + 单选 + 条件项，迁 ModalBottomSheet） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyEventSheet(
    onSave: (EmergencyEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var scene by remember { mutableStateOf(EmergencyScene.INFECTION_FEVER) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var symptoms by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf("") }
    var hospitalVisit by remember { mutableStateOf(false) }
    var hospitalName by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var resolved by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
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
            Text(stringResource(R.string.emergency_record_event), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.emergency_scenario), style = MaterialTheme.typography.labelLarge)
            EmergencyScene.entries.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.touchMin)
                        .selectable(selected = scene == s, role = Role.RadioButton, onClick = { scene = s }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = scene == s, onClick = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(s.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            OutlinedTextField(
                date, { date = it }, label = { Text(stringResource(R.string.common_date)) }, singleLine = true,
                isError = attempted && !dateOk,
                supportingText = { if (attempted && !dateOk) Text(stringResource(R.string.common_date_format_hint2)) },
            )
            OutlinedTextField(symptoms, { symptoms = it }, label = { Text(stringResource(R.string.symptom_description)) })
            OutlinedTextField(actions, { actions = it }, label = { Text(stringResource(R.string.emergency_actions_taken)) })

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin)
                    .toggleable(value = hospitalVisit, role = Role.Checkbox, onValueChange = { hospitalVisit = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hospitalVisit, onCheckedChange = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.emergency_seek_care), style = MaterialTheme.typography.bodyLarge)
            }
            if (hospitalVisit) {
                OutlinedTextField(hospitalName, { hospitalName = it }, label = { Text(stringResource(R.string.checkup_hospital_field)) }, singleLine = true)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin)
                    .toggleable(value = resolved, role = Role.Checkbox, onValueChange = { resolved = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = resolved, onCheckedChange = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.symptom_outcome_recovered), style = MaterialTheme.typography.bodyLarge)
            }
            if (resolved) {
                OutlinedTextField(outcome, { outcome = it }, label = { Text(stringResource(R.string.symptom_outcome_label)) }, singleLine = true)
            }

            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })

            Button(
                onClick = {
                    attempted = true
                    if (dateOk) {
                        onSave(
                            EmergencyEvent(
                                id = "", date = date.trim(), recordedAt = nowIso(),
                                scene = scene.name, severity = "high",
                                symptoms = symptoms.ifBlank { null },
                                actionsTaken = actions.ifBlank { null },
                                hospitalVisit = hospitalVisit,
                                hospitalName = hospitalName.ifBlank { null },
                                outcome = outcome.ifBlank { null },
                                resolvedDate = if (resolved) LocalDate.now().toString() else null,
                                notes = notes.ifBlank { null },
                            )
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
