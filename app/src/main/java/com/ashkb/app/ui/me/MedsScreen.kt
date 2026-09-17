package com.ashkb.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.StopReason
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/** 药单管理（route `meds`）：从「我的」页拆出的独立二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(
    vm: MeViewModel,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val meds by vm.meds.collectAsState()
    val context = LocalContext.current
    var stopTarget by remember { mutableStateOf<Medication?>(null) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.med_manage_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd, modifier = Modifier.size(Size.touchMin)) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.med_add_new))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            if (meds.isEmpty()) {
                SectionCard(title = stringResource(R.string.nav_meds)) {
                    EmptyState(
                        icon = Icons.Rounded.Medication,
                        title = stringResource(R.string.med_empty_hint),
                        body = stringResource(R.string.med_add_plan_note),
                        actionLabel = stringResource(R.string.med_add_medication),
                        onAction = onAdd,
                    )
                }
            } else {
                SectionCard(title = stringResource(R.string.me_meds_count, meds.size)) {
                    meds.forEachIndexed { index, med ->
                        MedRow(
                            med = med,
                            onStop = { stopTarget = med },
                        )
                        if (index != meds.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                Modifier.padding(vertical = Spacing.sm),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().height(Size.touchComfort),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.med_add_medication))
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    stopTarget?.let { med ->
        StopMedDialog(
            medName = "${med.name} ${med.dose}",
            isBiologic = med.medClass.equals("BIOLOGIC", true),
            onConfirm = { reason, note ->
                vm.stopMedication(context, med, reason, note)
                stopTarget = null
            },
            onDismiss = { stopTarget = null },
        )
    }
}

@Composable
private fun MedRow(med: Medication, onStop: () -> Unit) {
    val needsCheck = !med.checkDoctorTold || !med.checkLeafletRead
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("${med.name} ${med.dose}", style = MaterialTheme.typography.bodyLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    buildString {
                        append(MedFrequency.fromKey(med.frequency).label)
                        if (med.route == "injection") append(stringResource(R.string.med_injection_suffix))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needsCheck) {
                    StatusChip(stringResource(R.string.med_verify_todo_tag), StatusTone.Warning, Icons.Rounded.WarningAmber)
                }
            }
        }
        TextButton(onClick = onStop) { Text(stringResource(R.string.med_deactivate)) }
    }
}

/** R17 停药原因分类：self_stopped / side_effect 弹警示（D-2 §7） */
@Composable
private fun StopMedDialog(
    medName: String,
    isBiologic: Boolean,
    onConfirm: (reason: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(StopReason.DOCTOR_SCHEDULED) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_med_disable, medName)) },
        text = {
            Column {
                Text(stringResource(R.string.med_deactivate_reason_note), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.xs))
                StopReason.entries.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (reason == StopReason.OTHER) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text(stringResource(R.string.med_deactivate_reason_field)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                reason.warning?.let { w ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        w,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isBiologic && reason == StopReason.SELF_STOPPED) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.med_bio_stop_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.name.lowercase(), note.ifBlank { null }) },
                enabled = reason != StopReason.OTHER || note.isNotBlank(),
            ) { Text(stringResource(R.string.med_deactivate_and_record)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
