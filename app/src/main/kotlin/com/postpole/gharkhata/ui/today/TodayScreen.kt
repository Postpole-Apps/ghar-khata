package com.postpole.gharkhata.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Remove
import com.postpole.gharkhata.ui.pro.CrownIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.provider.PILL_HEIGHT
import com.postpole.gharkhata.ui.provider.PillButton
import com.postpole.gharkhata.ui.provider.ProviderAvatar
import com.postpole.gharkhata.ui.provider.icon
import com.postpole.gharkhata.ui.provider.labelRes
import com.postpole.gharkhata.ui.provider.scheduleLabel
import com.postpole.gharkhata.ui.provider.stepQty
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

/** Start destination: one date and the household's marks for it. */
@Composable
fun TodayScreen(
    onAddProvider: () -> Unit,
    onPreset: (ProviderType) -> Unit,
    onOpenBills: () -> Unit,
    onOpenProvider: (Long) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf<ProReason?>(null) }
    TodayContent(
        state = state,
        onSelectDate = viewModel::selectDate,
        onCycle = viewModel::cycleStatus,
        onSetQty = viewModel::setQty,
        onReset = viewModel::reset,
        onAddExtra = viewModel::addExtra,
        onAddProvider = { if (state.canAddProvider) onAddProvider() else showPro = ProReason.PROVIDER_LIMIT },
        onPreset = onPreset,
        onOpenPro = { showPro = ProReason.GENERAL },
        onOpenBills = onOpenBills,
        onOpenProvider = onOpenProvider,
    )
    showPro?.let { ProSheet(onDismiss = { showPro = null }, reason = it) }
}

/** Stateless Today (also used by the screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayContent(
    state: TodayState,
    onSelectDate: (Long) -> Unit,
    onCycle: (TodayRow) -> Unit,
    onSetQty: (TodayRow, Double, String?) -> Unit,
    onReset: (TodayRow) -> Unit,
    onAddExtra: (ProviderEntity) -> Unit,
    onAddProvider: () -> Unit,
    onPreset: (ProviderType) -> Unit,
    onOpenPro: () -> Unit,
    onOpenBills: () -> Unit = {},
    onOpenProvider: (Long) -> Unit = {},
) {
    val gk = MaterialTheme.gharColors
    var qtyRow by remember { mutableStateOf<TodayRow?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showExtra by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant)))
            .statusBarsPadding(),
    ) {
        TopRow(
            canAddExtra = state.extraCandidates.isNotEmpty(),
            onOpenPro = onOpenPro,
            onAddExtra = { showExtra = true },
        )
        if (state.noProviders) {
            EmptyHero(state)
            PresetSheet(onPreset)
        } else {
            Hero(state, onSelectDate, onPickDate = { showPicker = true }, onOpenBills = onOpenBills)
            Sheet(state, onCycle, onEditQty = { qtyRow = it }, onAddProvider = onAddProvider, onOpenProvider = onOpenProvider)
        }
    }

    if (showPicker) {
        val today = state.todayEpochDay
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.epochDay * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= today
                override fun isSelectableYear(year: Int) = year <= LocalDate.ofEpochDay(today).year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelectDate(Math.floorDiv(it, MILLIS_PER_DAY)) }
                    showPicker = false
                }) { Text(stringResource(R.string.provider_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }

    qtyRow?.let { row ->
        QtyDialog(
            row = row,
            onDismiss = { qtyRow = null },
            onSave = { qty, note -> onSetQty(row, qty, note); qtyRow = null },
            onReset = { onReset(row); qtyRow = null },
        )
    }

    if (showExtra) {
        AlertDialog(
            onDismissRequest = { showExtra = false },
            title = { Text(stringResource(R.string.today_extra_title)) },
            text = {
                Column {
                    state.extraCandidates.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onAddExtra(p); showExtra = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProviderAvatar(
                                p.type,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 40.dp,
                            )
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExtra = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TopRow(canAddExtra: Boolean, onOpenPro: () -> Unit, onAddExtra: () -> Unit) {
    val gk = MaterialTheme.gharColors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(gk.lime), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = gk.onLime, modifier = Modifier.size(24.dp))
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = gk.onHero,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenPro) {
            Icon(CrownIcon, contentDescription = stringResource(R.string.nav_pro), tint = gk.saffron)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.today_more), tint = gk.onHero)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_add_extra)) },
                    enabled = canAddExtra,
                    onClick = { menu = false; onAddExtra() },
                )
            }
        }
    }
}

@Composable
private fun dateLabel(epochDay: Long, todayEpochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val text = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())).uppercase(Locale.getDefault())
    return if (epochDay == todayEpochDay) stringResource(R.string.today_label_today, text) else text
}

@Composable
private fun Hero(state: TodayState, onSelectDate: (Long) -> Unit, onPickDate: () -> Unit, onOpenBills: () -> Unit) {
    val gk = MaterialTheme.gharColors
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DayStrip(state, onSelectDate)
        val pickDesc = stringResource(R.string.today_pick_date)
        Text(
            dateLabel(state.epochDay, state.todayEpochDay),
            style = MaterialTheme.typography.labelLarge,
            color = gk.onHeroVariant,
            modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 48.dp)
                .clickable(onClickLabel = pickDesc, role = Role.Button, onClick = onPickDate),
        )
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            val total = state.rows.size
            Text("$total", style = GharNumerals.XL, color = gk.lime)
            Text(
                stringResource(R.string.today_of_marked, total),
                style = MaterialTheme.typography.headlineMedium,
                color = gk.onHeroVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.height(PILL_HEIGHT).clip(CircleShape).background(gk.onHero.copy(alpha = 0.10f)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(gk.saffron))
                Text(
                    pluralStringResource(R.plurals.today_changes, state.changes, state.changes),
                    style = MaterialTheme.typography.labelLarge,
                    color = gk.onHero,
                )
            }
            NextBillsChip(state.todayEpochDay, onOpenBills)
        }
        Text(
            stringResource(R.string.today_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = gk.onHeroVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 20.dp),
        )
    }
}

/** Lime "Next bills · 1 Dec" chip: bills close at month end, so the next ones are due on the 1st. Opens Providers. */
@Composable
private fun NextBillsChip(todayEpochDay: Long, onClick: () -> Unit) {
    val gk = MaterialTheme.gharColors
    val next = LocalDate.ofEpochDay(todayEpochDay).plusMonths(1).withDayOfMonth(1)
    val date = next.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    val desc = stringResource(R.string.today_next_bills_desc, date)
    // The pill is 32 dp tall; the clickable box around it keeps a 48 dp touch target.
    Box(
        Modifier.minimumInteractiveComponentSize().semantics { contentDescription = desc }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(PILL_HEIGHT).clip(CircleShape).background(gk.lime).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = gk.onLime, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.today_next_bills, date), style = MaterialTheme.typography.labelLarge, color = gk.onLime)
        }
    }
}

/** Mon–Sun strip around the selected day. Future days are disabled. */
@Composable
private fun DayStrip(state: TodayState, onSelectDate: (Long) -> Unit) {
    val gk = MaterialTheme.gharColors
    val selected = LocalDate.ofEpochDay(state.epochDay)
    val monday = selected.with(DayOfWeek.MONDAY).toEpochDay()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val day = monday + i
            val date = LocalDate.ofEpochDay(day)
            val isSel = day == state.epochDay
            val enabled = day <= state.todayEpochDay
            val name = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val fg = if (isSel) gk.onLime else gk.onHero
            Column(
                Modifier.weight(1f).heightIn(min = 88.dp).clip(RoundedCornerShape(50))
                    .then(
                        if (isSel) Modifier.background(gk.lime)
                        else if (enabled) Modifier.background(gk.onHero.copy(alpha = 0.10f))
                        else Modifier.border(1.dp, gk.onHero.copy(alpha = 0.16f), RoundedCornerShape(50)),
                    )
                    .clickable(enabled = enabled, role = Role.Button) { onSelectDate(day) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                val alpha = if (enabled) 1f else 0.45f
                Text(name, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = alpha))
                Text(date.dayOfMonth.toString(), style = GharNumerals.S, color = fg.copy(alpha = alpha))
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (enabled) {
                        Box(
                            Modifier.size(16.dp).clip(CircleShape).background(if (isSel) gk.hero else gk.onHero.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check, contentDescription = null,
                                tint = if (isSel) gk.lime else gk.lime, modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(
    state: TodayState,
    onCycle: (TodayRow) -> Unit,
    onEditQty: (TodayRow) -> Unit,
    onAddProvider: () -> Unit,
    onOpenProvider: (Long) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().clip(GharShapesExtra.SheetTop).background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.epochDay == state.todayEpochDay) {
                    stringResource(R.string.today_scheduled_today, state.rows.size)
                } else {
                    stringResource(R.string.today_scheduled_day, state.rows.size)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                onClick = onAddProvider,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                horizontalPadding = 16.dp,
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.provider_add_title), style = MaterialTheme.typography.labelLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_nothing_scheduled),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(state.rows, key = { it.provider.id }) { row ->
                val index = state.rows.indexOf(row)
                TodayRowCard(
                    row,
                    style = index % 3,
                    onCycle = { onCycle(row) },
                    onEditQty = { onEditQty(row) },
                    onOpen = { onOpenProvider(row.provider.id) },
                )
            }
        }
    }
}

private fun statusIcon(s: DayStatus): ImageVector = when (s) {
    DayStatus.ABSENT -> Icons.Filled.Close
    DayStatus.HALF -> Icons.Filled.Contrast
    else -> Icons.Filled.Check
}

@Composable
private fun statusLabel(row: TodayRow): String = when (row.status) {
    DayStatus.ABSENT -> stringResource(R.string.status_absent)
    DayStatus.HALF -> stringResource(R.string.status_half)
    DayStatus.EXTRA -> stringResource(R.string.status_extra)
    DayStatus.PRESENT ->
        if (row.provider.billingMode == BillingMode.PER_UNIT) stringResource(R.string.status_delivered)
        else stringResource(R.string.status_present)
}

internal fun formatQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

@Composable
internal fun TodayRowCard(row: TodayRow, style: Int, onCycle: () -> Unit, onEditQty: () -> Unit, onOpen: () -> Unit = {}) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val bg: Modifier = when (style) {
        0 -> Modifier.background(
            Brush.linearGradient(listOf(gk.lime, if (gk.limeContainer.luminance() > 0.4f) gk.limeContainer else gk.limeDim)),
        )
        1 -> Modifier.background(gk.hero)
        else -> Modifier.background(cs.surfaceContainerHigh)
    }
    val fg = when (style) { 0 -> gk.onLime; 1 -> gk.onHero; else -> cs.onSurface }
    val (avatarBg, avatarFg) = when (style) {
        0 -> gk.onLime to gk.lime
        1 -> fg.copy(alpha = 0.12f) to fg
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
    val (chipBg, chipFg) = when (row.status) {
        DayStatus.ABSENT -> cs.errorContainer to cs.onErrorContainer
        DayStatus.HALF -> gk.saffronContainer to gk.onSaffronContainer
        else -> if (style == 1) gk.lime to gk.onLime else gk.hero to gk.onHero
    }
    val p = row.provider
    Row(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card).then(bg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Only the name/rate area opens the month; the chip and qty pills keep their own click targets.
        val openDesc = stringResource(R.string.today_open_provider_cd, p.name)
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(onClickLabel = openDesc, role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = openDesc },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        ProviderAvatar(p.type, container = avatarBg, content = avatarFg)
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.today_row_subtitle, stringResource(p.type.labelRes()), scheduleLabel(p.scheduleMask)),
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }
        Column(horizontalAlignment = Alignment.End) {
            PillButton(onClick = onCycle, container = chipBg, horizontalPadding = 14.dp) {
                Icon(statusIcon(row.status), contentDescription = null, tint = chipFg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(statusLabel(row), color = chipFg, style = MaterialTheme.typography.labelLarge)
            }
            if (p.billingMode == BillingMode.PER_UNIT && row.status != DayStatus.ABSENT) {
                val qtyDesc = stringResource(R.string.today_edit_qty)
                PillButton(
                    onClick = onEditQty,
                    container = fg.copy(alpha = 0.12f),
                    horizontalPadding = 14.dp,
                ) {
                    Text("${formatQty(row.qty)} ${p.unitLabel}", color = fg, style = GharNumerals.S.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = qtyDesc, tint = fg, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun QtyDialog(row: TodayRow, onDismiss: () -> Unit, onSave: (Double, String?) -> Unit, onReset: () -> Unit) {
    val unit = row.provider.unitLabel
    var qty by remember { mutableStateOf(formatQty(row.qty)) }
    var note by remember { mutableStateOf(row.note.orEmpty()) }
    val value = qty.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.provider.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = { qty = stepQty(qty, unit, -1) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.provider_qty_decrease))
                    }
                    Text("$qty $unit", style = GharNumerals.M, modifier = Modifier.padding(horizontal = 16.dp))
                    IconButton(onClick = { qty = stepQty(qty, unit, 1) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.provider_qty_increase))
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.today_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0.0, onClick = { onSave(value ?: return@TextButton, note) }) {
                Text(stringResource(R.string.provider_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(stringResource(R.string.today_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

// ---- Empty state ----------------------------------------------------------------------------------

@Composable
private fun EmptyHero(state: TodayState) {
    val gk = MaterialTheme.gharColors
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Doorstep(Modifier.padding(top = 8.dp).size(width = 320.dp, height = 170.dp))
        Text(
            dateLabel(state.epochDay, state.todayEpochDay).let { it + " " + LocalDate.ofEpochDay(state.epochDay).year },
            style = MaterialTheme.typography.labelLarge,
            color = gk.onHeroVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Doorstep(modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ground = h - 14.dp.toPx()
        // doorstep
        drawRoundRect(gk.onHero.copy(alpha = 0.12f), Offset(0f, ground), Size(w, 14.dp.toPx()), CornerRadius(7.dp.toPx()))
        // arch + door
        val dw = 110.dp.toPx()
        val left = (w - dw) / 2
        drawRoundRect(gk.onHero.copy(alpha = 0.14f), Offset(left - 14.dp.toPx(), 12.dp.toPx()), Size(dw + 28.dp.toPx(), ground - 12.dp.toPx()), CornerRadius(70.dp.toPx()))
        drawRoundRect(gk.lime, Offset(left, 28.dp.toPx()), Size(dw, ground - 28.dp.toPx()), CornerRadius(55.dp.toPx(), 55.dp.toPx()))
        drawRect(gk.lime, Offset(left, ground - 40.dp.toPx()), Size(dw, 40.dp.toPx()))
        drawLine(gk.limeDim, Offset(w / 2, 40.dp.toPx()), Offset(w / 2, ground), 3.dp.toPx())
        drawCircle(gk.onLime, 3.dp.toPx(), Offset(w / 2 - 12.dp.toPx(), 80.dp.toPx()))
        drawCircle(gk.onLime, 3.dp.toPx(), Offset(w / 2 + 12.dp.toPx(), 80.dp.toPx()))
        // milk jar
        val jx = left - 80.dp.toPx()
        drawRoundRect(gk.onHero.copy(alpha = 0.9f), Offset(jx, ground - 62.dp.toPx()), Size(40.dp.toPx(), 62.dp.toPx()), CornerRadius(10.dp.toPx()))
        drawRoundRect(gk.onHeroVariant, Offset(jx + 4.dp.toPx(), ground - 74.dp.toPx()), Size(32.dp.toPx(), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
        // broom
        val bx = left + dw + 40.dp.toPx()
        drawLine(gk.lime, Offset(bx + 28.dp.toPx(), 20.dp.toPx()), Offset(bx + 6.dp.toPx(), ground - 34.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
        drawRoundRect(gk.limeDim, Offset(bx - 6.dp.toPx(), ground - 36.dp.toPx()), Size(30.dp.toPx(), 34.dp.toPx()), CornerRadius(8.dp.toPx()))
        // sparkle
        drawCircle(gk.lime, 5.dp.toPx(), Offset(left - 90.dp.toPx(), 50.dp.toPx()))
        drawCircle(gk.saffron, 4.dp.toPx(), Offset(bx + 36.dp.toPx(), 70.dp.toPx()))
    }
}

@Composable
private fun PresetSheet(onPreset: (ProviderType) -> Unit) {
    Column(
        Modifier.fillMaxSize().clip(GharShapesExtra.SheetTop).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.today_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        PresetCard(ProviderType.MILK, R.string.provider_type_milk, R.string.today_preset_milk, 0, onPreset)
        PresetCard(ProviderType.MAID, R.string.provider_type_maid, R.string.today_preset_maid, 1, onPreset)
        PresetCard(ProviderType.NEWSPAPER, R.string.provider_type_newspaper, R.string.today_preset_newspaper, 2, onPreset)
    }
}

@Composable
private fun PresetCard(type: ProviderType, title: Int, subtitle: Int, style: Int, onPreset: (ProviderType) -> Unit) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val bg: Modifier = when (style) {
        0 -> Modifier.background(
            Brush.linearGradient(listOf(gk.lime, if (gk.limeContainer.luminance() > 0.4f) gk.limeContainer else gk.limeDim)),
        )
        1 -> Modifier.background(gk.hero)
        else -> Modifier.background(cs.surfaceContainerHigh)
    }
    val fg = when (style) { 0 -> gk.onLime; 1 -> gk.onHero; else -> cs.onSurface }
    val (avatarBg, avatarFg) = when (style) {
        0 -> gk.onLime to gk.lime
        1 -> fg.copy(alpha = 0.12f) to fg
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(GharShapesExtra.Card).then(bg)
            .clickable(role = Role.Button) { onPreset(type) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderAvatar(type, container = avatarBg, content = avatarFg)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = fg)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = fg)
    }
}
