package com.postpole.gharkhata.ui.provider

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.theme.gharColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@StringRes
internal fun ProviderType.labelRes(): Int = when (this) {
    ProviderType.MILK -> R.string.provider_type_milk
    ProviderType.MAID -> R.string.provider_type_maid
    ProviderType.NEWSPAPER -> R.string.provider_type_newspaper
    ProviderType.WATER_CAN -> R.string.provider_type_water_can
    ProviderType.COOK -> R.string.provider_type_cook
    ProviderType.DRIVER -> R.string.provider_type_driver
    ProviderType.OTHER -> R.string.provider_type_other
}

internal fun ProviderType.glyph(): String = when (this) {
    ProviderType.MILK -> "🥛"
    ProviderType.MAID -> "🧹"
    ProviderType.NEWSPAPER -> "📰"
    ProviderType.WATER_CAN -> "💧"
    ProviderType.COOK -> "🍳"
    ProviderType.DRIVER -> "🚗"
    ProviderType.OTHER -> "⋯"
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

private val DAYS = listOf(
    Triple(Schedule.MON, R.string.day_letter_mon, R.string.day_mon),
    Triple(Schedule.TUE, R.string.day_letter_tue, R.string.day_tue),
    Triple(Schedule.WED, R.string.day_letter_wed, R.string.day_wed),
    Triple(Schedule.THU, R.string.day_letter_thu, R.string.day_thu),
    Triple(Schedule.FRI, R.string.day_letter_fri, R.string.day_fri),
    Triple(Schedule.SAT, R.string.day_letter_sat, R.string.day_sat),
    Triple(Schedule.SUN, R.string.day_letter_sun, R.string.day_sun),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(onBack: () -> Unit, viewModel: ProviderEditViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gk = MaterialTheme.gharColors

    var showProSheet by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var askEffectiveFrom by remember { mutableStateOf(false) }
    var effectiveFrom by remember { mutableStateOf(viewModel.defaultEffectiveFrom) }
    var showEffectivePicker by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save(effective: Long?) {
        scope.launch {
            when (viewModel.save(effective)) {
                SaveResult.Saved -> onBack()
                SaveResult.LimitReached -> showProSheet = true
                SaveResult.NeedsEffectiveFrom -> askEffectiveFrom = true
                is SaveResult.Invalid -> Unit
            }
        }
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.provider_close))
            }
            Text(
                stringResource(if (viewModel.isNew) R.string.provider_add_title else R.string.provider_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Box(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).background(gk.hero)
                    .clickable(role = Role.Button) { save(null) }.padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.provider_save), color = gk.lime, style = MaterialTheme.typography.labelLarge)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(R.string.provider_section_type)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4,
            ) {
                ProviderType.entries.forEach { type ->
                    val selected = form.type == type
                    Column(
                        Modifier.weight(1f).heightIn(min = 72.dp).clip(MaterialTheme.shapes.large)
                            .background(if (selected) gk.lime else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .semantics { this.selected = selected }
                            .clickable(role = Role.RadioButton) { viewModel.selectType(type) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(type.glyph(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(type.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) gk.onLime else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.provider_name)) },
                isError = FormError.NAME_REQUIRED in errors,
                supportingText = if (FormError.NAME_REQUIRED in errors) {
                    { Text(stringResource(R.string.provider_name_required)) }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(R.string.provider_section_billing)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BillingMode.PER_UNIT to R.string.billing_per_unit,
                    BillingMode.PER_VISIT to R.string.billing_per_visit,
                    BillingMode.MONTHLY_FIXED to R.string.billing_monthly,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = form.billingMode == mode,
                        onClick = { viewModel.update { it.copy(billingMode = mode) } },
                        label = { Text(stringResource(label)) },
                        leadingIcon = if (form.billingMode == mode) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        shape = CircleShape,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            if (form.billingMode != BillingMode.MONTHLY_FIXED) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.unitLabel,
                        onValueChange = { v -> viewModel.update { it.copy(unitLabel = v) } },
                        label = { Text(stringResource(R.string.provider_unit)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.defaultQty,
                        onValueChange = { v -> viewModel.update { it.copy(defaultQty = v) } },
                        label = { Text(stringResource(R.string.provider_default_qty)) },
                        isError = FormError.QTY_INVALID in errors,
                        supportingText = if (FormError.QTY_INVALID in errors) {
                            { Text(stringResource(R.string.provider_qty_invalid)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OutlinedTextField(
                value = form.rate,
                onValueChange = { v -> viewModel.update { it.copy(rate = v) } },
                label = {
                    Text(
                        stringResource(
                            if (form.billingMode == BillingMode.MONTHLY_FIXED) R.string.provider_monthly_amount else R.string.provider_rate,
                        ),
                    )
                },
                prefix = { Text("₹") },
                isError = FormError.RATE_INVALID in errors,
                supportingText = if (FormError.RATE_INVALID in errors) {
                    { Text(stringResource(R.string.provider_rate_invalid)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.billingMode != BillingMode.PER_UNIT) {
                OutlinedTextField(
                    value = form.paidLeaves,
                    onValueChange = { v -> viewModel.update { it.copy(paidLeaves = v) } },
                    label = { Text(stringResource(R.string.provider_paid_leaves)) },
                    isError = FormError.LEAVES_INVALID in errors,
                    supportingText = if (FormError.LEAVES_INVALID in errors) {
                        { Text(stringResource(R.string.provider_leaves_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(R.string.provider_section_schedule)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAYS.forEach { (bit, letter, full) ->
                    val on = form.scheduleMask and bit != 0
                    val fullName = stringResource(full)
                    Box(
                        // 48 dp touch target around the 40 dp chip.
                        Modifier.size(48.dp).semantics {
                            contentDescription = fullName
                            selected = on
                        }.clickable(role = Role.Checkbox) { viewModel.toggleDay(bit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (on) gk.hero else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(letter),
                                color = if (on) gk.lime else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.provider_schedule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DateField(stringResource(R.string.provider_start_date), form.startEpochDay) { showStartPicker = true }

            OutlinedTextField(
                value = form.phone,
                onValueChange = { v -> viewModel.update { it.copy(phone = v) } },
                label = { Text(stringResource(R.string.provider_phone)) },
                supportingText = { Text(stringResource(R.string.provider_phone_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.provider_notes)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!viewModel.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!archived) {
                        OutlinedButton(
                            onClick = { confirmArchive = true },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.provider_archive)) }
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
            Box(Modifier.size(24.dp))
        }
    }

    if (showStartPicker) {
        DayPickerDialog(form.startEpochDay, onDismiss = { showStartPicker = false }) { day ->
            viewModel.update { it.copy(startEpochDay = day) }
            showStartPicker = false
        }
    }
    if (askEffectiveFrom) {
        AlertDialog(
            onDismissRequest = { askEffectiveFrom = false },
            title = { Text(stringResource(R.string.rate_change_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.rate_change_message))
                    DateField(stringResource(R.string.rate_change_title), effectiveFrom) { showEffectivePicker = true }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askEffectiveFrom = false
                    save(effectiveFrom)
                }) { Text(stringResource(R.string.rate_change_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askEffectiveFrom = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showEffectivePicker) {
        DayPickerDialog(effectiveFrom, onDismiss = { showEffectivePicker = false }) { day ->
            effectiveFrom = day
            showEffectivePicker = false
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.provider_archive_title)) },
            text = { Text(stringResource(R.string.provider_archive_message, form.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    scope.launch {
                        viewModel.archive()
                        onBack()
                    }
                }) { Text(stringResource(R.string.provider_archive)) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.provider_delete_title)) },
            text = { Text(stringResource(R.string.provider_delete_message, form.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        viewModel.delete()
                        onBack()
                    }
                }) { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showProSheet) ProSheet(onDismiss = { showProSheet = false })
}

@Composable
private fun SectionLabel(@StringRes res: Int) {
    Text(
        stringResource(res).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DateField(label: String, epochDay: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDay(epochDay), style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.provider_pick_date))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(epochDay: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(Math.floorDiv(it, MILLIS_PER_DAY)) } ?: onDismiss() }) {
                Text(stringResource(R.string.provider_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

private const val MILLIS_PER_DAY = 86_400_000L
