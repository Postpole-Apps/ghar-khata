package com.postpole.gharkhata.ui.provider

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyRupee
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
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

private val TYPE_ORDER = listOf(
    ProviderType.MILK, ProviderType.MAID, ProviderType.NEWSPAPER, ProviderType.WATER_CAN,
    ProviderType.COOK, ProviderType.DRIVER, ProviderType.OTHER,
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
internal fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

private val DAYS = listOf(
    Triple(Schedule.MON, R.string.day_letter_mon, R.string.day_mon),
    Triple(Schedule.TUE, R.string.day_letter_tue, R.string.day_tue),
    Triple(Schedule.WED, R.string.day_letter_wed, R.string.day_wed),
    Triple(Schedule.THU, R.string.day_letter_thu, R.string.day_thu),
    Triple(Schedule.FRI, R.string.day_letter_fri, R.string.day_fri),
    Triple(Schedule.SAT, R.string.day_letter_sat, R.string.day_sat),
    Triple(Schedule.SUN, R.string.day_letter_sun, R.string.day_sun),
)

@Composable
fun ProviderEditScreen(onBack: () -> Unit, viewModel: ProviderEditViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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

    ProviderEditContent(
        form = form,
        errors = errors,
        isNew = viewModel.isNew,
        archived = archived,
        onUpdate = viewModel::update,
        onSelectType = viewModel::selectType,
        onToggleDay = viewModel::toggleDay,
        onSave = { save(null) },
        onClose = onBack,
        onPickStart = { showStartPicker = true },
        onArchive = { confirmArchive = true },
        onDelete = { confirmDelete = true },
    )

    if (showStartPicker) {
        DayPickerDialog(form.startEpochDay, onDismiss = { showStartPicker = false }) { day ->
            viewModel.update { it.copy(startEpochDay = day) }
            showStartPicker = false
        }
    }
    if (askEffectiveFrom) {
        val monthly = form.billingMode == BillingMode.MONTHLY_FIXED
        Dialog(
            onDismissRequest = { askEffectiveFrom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            RateChangeSurface(
                monthly = monthly,
                providerName = form.name.trim(),
                oldRatePaise = viewModel.currentRatePaise,
                newRatePaise = Money.parse(form.rate),
                effectiveFromEpochDay = effectiveFrom,
                onPickDate = { showEffectivePicker = true },
                onCancel = { askEffectiveFrom = false },
                onSave = {
                    askEffectiveFrom = false
                    save(effectiveFrom)
                },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
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

/** Stateless Add/Edit form (also used by the screenshot tests). */
@Composable
internal fun ProviderEditContent(
    form: ProviderForm,
    errors: Set<FormError>,
    isNew: Boolean,
    archived: Boolean,
    onUpdate: ((ProviderForm) -> ProviderForm) -> Unit,
    onSelectType: (ProviderType) -> Unit,
    onToggleDay: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onPickStart: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val gk = MaterialTheme.gharColors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.provider_close))
            }
            Text(
                stringResource(if (isNew) R.string.provider_add_title else R.string.provider_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            PillButton(onClick = onSave, container = gk.hero, horizontalPadding = 20.dp) {
                Text(stringResource(R.string.provider_save), color = gk.lime, style = MaterialTheme.typography.labelLarge)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(R.string.provider_section_type)
            // Four tiles per row in the mockup's order; the last row is padded so every tile has one width.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TYPE_ORDER.chunked(4).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTypes.forEach { type ->
                            val selected = form.type == type
                            Column(
                                Modifier.weight(1f).heightIn(min = 72.dp).clip(MaterialTheme.shapes.large)
                                    .background(if (selected) gk.lime else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .semantics { this.selected = selected }
                                    .clickable(role = Role.RadioButton) { onSelectType(type) }
                                    .padding(horizontal = 2.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    type.icon(),
                                    contentDescription = null,
                                    tint = if (selected) gk.onLime else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    stringResource(type.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) gk.onLime else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(4 - rowTypes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> onUpdate { it.copy(name = v) } },
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
                    // FilterChip is 32 dp tall and Material adds the 48 dp minimum touch target around it.
                    FilterChip(
                        selected = form.billingMode == mode,
                        onClick = { onUpdate { it.copy(billingMode = mode) } },
                        label = { Text(stringResource(label)) },
                        leadingIcon = if (form.billingMode == mode) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        shape = CircleShape,
                    )
                }
            }

            if (form.billingMode != BillingMode.MONTHLY_FIXED) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.unitLabel,
                        onValueChange = { v -> onUpdate { it.copy(unitLabel = v) } },
                        label = { Text(stringResource(R.string.provider_unit)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                    StepperField(
                        label = stringResource(R.string.provider_default_qty),
                        value = form.defaultQty,
                        onValueChange = { v -> onUpdate { it.copy(defaultQty = v) } },
                        onMinus = { onUpdate { it.copy(defaultQty = stepQty(it.defaultQty, it.unitLabel, -1)) } },
                        onPlus = { onUpdate { it.copy(defaultQty = stepQty(it.defaultQty, it.unitLabel, +1)) } },
                        error = if (FormError.QTY_INVALID in errors) stringResource(R.string.provider_qty_invalid) else null,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }

            val monthly = form.billingMode == BillingMode.MONTHLY_FIXED
            RateField(
                label = stringResource(if (monthly) R.string.provider_monthly_amount else R.string.provider_rate),
                value = form.rate,
                onValueChange = { v -> onUpdate { it.copy(rate = v) } },
                suffix = when (form.billingMode) {
                    BillingMode.MONTHLY_FIXED -> stringResource(R.string.provider_rate_per_month)
                    BillingMode.PER_VISIT -> stringResource(R.string.provider_rate_per_visit)
                    BillingMode.PER_UNIT -> stringResource(R.string.provider_rate_per_unit, form.unitLabel.trim())
                },
                error = if (FormError.RATE_INVALID in errors) stringResource(R.string.provider_rate_invalid) else null,
            )

            if (form.billingMode != BillingMode.PER_UNIT) {
                OutlinedTextField(
                    value = form.paidLeaves,
                    onValueChange = { v -> onUpdate { it.copy(paidLeaves = v) } },
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
                        }.clickable(role = Role.Checkbox) { onToggleDay(bit) },
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

            DateField(stringResource(R.string.provider_start_date), form.startEpochDay, onPickStart)

            OutlinedTextField(
                value = form.phone,
                onValueChange = { v -> onUpdate { it.copy(phone = v) } },
                label = { Text(stringResource(R.string.provider_phone)) },
                supportingText = { Text(stringResource(R.string.provider_phone_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> onUpdate { it.copy(note = v) } },
                label = { Text(stringResource(R.string.provider_notes)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!archived) {
                        OutlinedButton(
                            onClick = onArchive,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.provider_archive)) }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}

/** Rate shown as a large numeral on a tonal card, with the unit ("per L") at the end. */
@Composable
private fun RateField(label: String, value: String, onValueChange: (String) -> Unit, suffix: String, error: String?) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (error != null) cs.error else cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.currency_symbol),
                style = GharNumerals.M,
                color = cs.onSurface,
                modifier = Modifier.padding(bottom = 6.dp, end = 6.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = GharNumerals.L.copy(color = cs.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            Text(
                suffix,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error)
    }
}

/** Number field with a round − / + pair, used for the usual quantity. Buttons keep 48 dp touch targets. */
@Composable
private fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val gk = MaterialTheme.gharColors
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (error != null) cs.error else cs.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
            StepButton(Icons.Filled.Remove, stringResource(R.string.provider_qty_decrease), onMinus, cs.surfaceContainerHighest, cs.onSurface)
            StepButton(Icons.Filled.Add, stringResource(R.string.provider_qty_increase), onPlus, gk.hero, gk.lime)
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, container: Color, tint: Color) {
    Box(
        Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * "New salary from when?" / "New rate from when?" surface (mockup 03b): icon badge, old rate struck through,
 * arrow, new rate in a lime pill, a helper line, the Effective from field, and Cancel + Save.
 */
@Composable
internal fun RateChangeSurface(
    monthly: Boolean,
    providerName: String,
    oldRatePaise: Long?,
    newRatePaise: Long?,
    effectiveFromEpochDay: Long,
    onPickDate: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val gk = MaterialTheme.gharColors
    val old = oldRatePaise?.let { Money.format(it) }.orEmpty()
    val new = newRatePaise?.let { Money.format(it) }.orEmpty()
    val oldToNew = stringResource(R.string.rate_change_old_new, old, new)
    Column(
        modifier.fillMaxWidth().clip(GharShapesExtra.ExtraLargeIncreased).background(cs.surfaceContainerHigh).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(BadgeShape).background(gk.saffronContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CurrencyRupee, contentDescription = null, tint = gk.onSaffronContainer, modifier = Modifier.size(28.dp))
        }
        Text(
            stringResource(if (monthly) R.string.rate_change_title_salary else R.string.rate_change_title_rate),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.clearAndSetSemantics { contentDescription = oldToNew },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                old,
                style = GharNumerals.M,
                color = cs.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = cs.onSurfaceVariant)
            Text(
                new,
                style = GharNumerals.M,
                color = gk.onLime,
                modifier = Modifier.clip(CircleShape).background(gk.lime).padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
        Text(
            stringResource(if (monthly) R.string.rate_change_helper_salary else R.string.rate_change_helper_rate, providerName),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainerLowest)
                .border(2.dp, cs.primary, MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onPickDate).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_change_field_label), style = MaterialTheme.typography.bodySmall, color = cs.primary)
                Text(formatDay(effectiveFromEpochDay), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.provider_pick_date))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            PillButton(onClick = onSave, container = gk.hero, horizontalPadding = 24.dp) {
                Text(stringResource(R.string.provider_save), color = gk.lime, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
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
        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.provider_pick_date))
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
