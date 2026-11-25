package com.postpole.gharkhata.ui.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.ui.pro.ProBadge
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.pro.rememberIsPro
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.ui.provider.ProviderAvatar
import com.postpole.gharkhata.ui.provider.labelRes
import com.postpole.gharkhata.ui.provider.scheduleLabel
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import com.postpole.gharkhata.ui.today.formatQty
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Per-provider month: hero total, two stat cards, calendar grid, legend and the Bill / Mark paid buttons. */
@Composable
fun ProviderMonthScreen(
    onBack: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onOpenBill: (providerId: Long, yearMonth: Int) -> Unit,
    onOpenYear: (providerId: Long, yearMonth: Int) -> Unit,
    onOverlayChanged: (Boolean) -> Unit = {},
    onSettlementSaved: () -> Unit = {},
    viewModel: ProviderMonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editDay by remember { mutableStateOf<Long?>(null) }
    var showPaid by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.settlementSaved.collect { onSettlementSaved() } }
    // The banner is hidden while DayMarkSheet / ProSheet / the paid dialog is open.
    val overlayOpen = editDay != null || showPaid || showPro
    LaunchedEffect(overlayOpen) { onOverlayChanged(overlayOpen) }
    DisposableEffect(Unit) { onDispose { onOverlayChanged(false) } }
    val isPro = rememberIsPro()
    ProviderMonthContent(
        state = state,
        onBack = onBack,
        onEdit = { onEditProvider(viewModel.providerId) },
        onPrev = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onCellClick = { editDay = it.epochDay },
        onBill = { onOpenBill(viewModel.providerId, state.yearMonth) },
        onMarkPaid = { showPaid = true },
        onYearSummary = { if (isPro) onOpenYear(viewModel.providerId, state.yearMonth) else showPro = true },
        isPro = isPro,
    )
    if (showPro) ProSheet(onDismiss = { showPro = false }, reason = ProReason.YEAR_SUMMARY)
    val provider = state.provider
    editDay?.let { day ->
        if (provider != null) {
            DayMarkSheet(
                provider = provider,
                epochDay = day,
                mark = state.cells.firstOrNull { it.epochDay == day }?.mark,
                onSave = { status, qty, note -> viewModel.saveDay(day, status, qty, note); editDay = null },
                onReset = { viewModel.resetDay(day); editDay = null },
                onDismiss = { editDay = null },
            )
        }
    }
    if (showPaid && provider != null && state.bill != null) {
        MarkPaidDialog(
            providerName = provider.name,
            yearMonth = state.yearMonthValue,
            duePaise = state.bill!!.duePaise,
            todayEpochDay = state.todayEpochDay,
            initialPaidOn = state.settlement?.paidOnEpochDay ?: state.todayEpochDay,
            initialPaidPaise = state.settlement?.paidPaise,
            initialNote = state.settlement?.note,
            onDismiss = { showPaid = false },
            onSave = { paid, on, note -> if (viewModel.markPaid(paid, on, note)) showPaid = false },
        )
    }
}

/** Stateless Month screen (also used by the screenshot tests). */
@Composable
internal fun ProviderMonthContent(
    state: MonthState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCellClick: (DayCell) -> Unit,
    onBill: () -> Unit,
    onMarkPaid: () -> Unit,
    onYearSummary: () -> Unit = {},
    isPro: Boolean = true,
) {
    val gk = MaterialTheme.gharColors
    val provider = state.provider
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant))).statusBarsPadding(),
    ) {
        if (provider == null) {
            TopBar(null, null, onBack, onEdit, onYearSummary, isPro)
            if (state.loaded) {
                Text(
                    stringResource(R.string.month_not_found),
                    color = gk.onHero,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar(provider, state.ratePaise, onBack, onEdit, onYearSummary, isPro)
            Hero(state)
            Column(
                Modifier.fillMaxWidth().heightIn(min = 560.dp).clip(GharShapesExtra.SheetTop)
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthSwitcher(state, onPrev, onNext)
                CalendarGrid(state, onCellClick)
                Legend(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        stringResource(R.string.month_bill_share), Icons.Outlined.Receipt,
                        container = gk.hero, content = gk.lime, modifier = Modifier.weight(1f), onClick = onBill,
                    )
                    BigButton(
                        stringResource(if (state.settlement != null) R.string.month_update_payment else R.string.month_mark_paid),
                        Icons.Outlined.DoneAll,
                        container = gk.lime, content = gk.onLime, modifier = Modifier.weight(1f), onClick = onMarkPaid,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    provider: com.postpole.gharkhata.data.ProviderEntity?,
    ratePaise: Long?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onYearSummary: () -> Unit,
    isPro: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val gk = MaterialTheme.gharColors
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.month_back), tint = gk.onHero)
        }
        if (provider != null) {
            ProviderAvatar(provider.type, container = gk.lime, content = gk.onLime, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = gk.onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    rateSubtitle(provider, ratePaise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = gk.onHeroVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.month_edit_provider), tint = gk.onHero)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.month_overflow), tint = gk.onHero)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.month_year_summary)) },
                        trailingIcon = { if (!isPro) ProBadge() },
                        onClick = { menu = false; onYearSummary() },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** "Milk · ₹60/L · daily", as in the mockup header. */
@Composable
private fun rateSubtitle(p: com.postpole.gharkhata.data.ProviderEntity, ratePaise: Long?): String {
    val type = stringResource(p.type.labelRes())
    val schedule = scheduleLabel(p.scheduleMask)
    val rate = ratePaise?.let {
        if (p.billingMode == BillingMode.MONTHLY_FIXED) stringResource(R.string.providers_rate_monthly, Money.format(it))
        else stringResource(R.string.providers_rate_per_unit, Money.format(it), p.unitLabel)
    } ?: return stringResource(R.string.today_row_subtitle, type, schedule)
    return stringResource(R.string.providers_subtitle, type, rate, schedule)
}

@Composable
private fun Hero(state: MonthState) {
    val gk = MaterialTheme.gharColors
    val provider = state.provider ?: return
    val bill = state.bill
    val locale = Locale.getDefault()
    val range = state.billedRange
    val current = state.yearMonth == LocalDate.ofEpochDay(state.todayEpochDay).let { it.year * 100 + it.monthValue }
    val rangeText = when {
        range == null -> BillTextFormatter.monthLabel(state.yearMonthValue, locale).uppercase(locale)
        else -> BillTextFormatter.rangeLabel(range.first, range.last, locale).uppercase(locale)
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (current) R.string.month_total_due_so_far else R.string.month_total_due, rangeText),
            style = MaterialTheme.typography.labelLarge,
            color = gk.onHeroVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(Money.format(bill?.duePaise ?: 0), style = GharNumerals.XL, color = gk.lime, maxLines = 1)
        if (bill != null) {
            val total = BillTextFormatter.money(bill.totalPaise)
            Text(
                when {
                    bill.previousBalancePaise > 0 -> stringResource(
                        R.string.month_breakdown, total, BillTextFormatter.money(bill.previousBalancePaise),
                        BillTextFormatter.monthShort(state.yearMonthValue.minusMonths(1), locale),
                    )
                    bill.previousBalancePaise < 0 -> stringResource(R.string.month_breakdown_credit, total, BillTextFormatter.money(-bill.previousBalancePaise))
                    else -> stringResource(R.string.month_breakdown_no_previous, total)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = gk.onHeroVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCardLeft(state, Modifier.weight(1f))
            if (provider.billingMode == BillingMode.PER_UNIT) StatCardQty(state, Modifier.weight(1f)) else StatCardLeaves(state, Modifier.weight(1f))
        }
    }
}

private val StatShape = RoundedCornerShape(28.dp)

@Composable
private fun StatCardLeft(state: MonthState, modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    val perUnit = state.provider?.billingMode == BillingMode.PER_UNIT
    Column(
        modifier.heightIn(min = 104.dp).clip(StatShape)
            .background(Brush.linearGradient(listOf(gk.lime, gk.limeDim))).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(if (perUnit) R.string.month_days_delivered else R.string.month_absences),
            style = MaterialTheme.typography.labelLarge,
            color = gk.onLime,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (perUnit) "${state.deliveredDays}" else formatQty(state.absences),
                style = GharNumerals.M, color = gk.onLime,
            )
            if (perUnit) {
                Text(
                    stringResource(R.string.month_days_of, state.countedDays),
                    style = MaterialTheme.typography.titleMedium, color = gk.onLime,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                )
            }
        }
        DayStrip(state)
    }
}

@Composable
private fun StatCardQty(state: MonthState, modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    val unit = state.provider?.unitLabel.orEmpty()
    Column(
        modifier.heightIn(min = 104.dp).clip(StatShape).background(gk.onHero.copy(alpha = 0.12f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.month_total_qty, stringResource(state.provider!!.type.labelRes()).lowercase(Locale.getDefault())), style = MaterialTheme.typography.labelLarge, color = gk.onHero)
        Text("${formatQty(state.totalQty)} $unit", style = GharNumerals.M, color = gk.onHero)
        DailyBars(state)
    }
}

@Composable
private fun StatCardLeaves(state: MonthState, modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    val allowed = state.provider?.paidLeavesPerMonth ?: 0
    Column(
        modifier.heightIn(min = 104.dp).clip(StatShape).background(gk.onHero.copy(alpha = 0.12f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.month_paid_leaves), style = MaterialTheme.typography.labelLarge, color = gk.onHero)
        Text(
            stringResource(R.string.month_paid_leaves_of, formatQty(state.paidLeavesUsed), allowed),
            style = GharNumerals.M, color = gk.onHero,
        )
    }
}

/** One capsule per counted day: dark = delivered, red = skipped, blue = changed qty, grey = other. */
@Composable
private fun DayStrip(state: MonthState) {
    val cs = MaterialTheme.colorScheme
    val gk = MaterialTheme.gharColors
    val days = state.cells.filter { it.epochDay >= (state.provider?.startEpochDay ?: 0) && it.epochDay <= state.todayEpochDay }
    val dark = gk.onLime
    val red = cs.error
    val blue = cs.tertiary
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        if (days.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (days.size - 1)) / days.size).coerceAtLeast(1f)
        days.forEachIndexed { i, c ->
            val color = when (c.kind) {
                DayCellKind.ABSENT -> red
                DayCellKind.CHANGED, DayCellKind.EXTRA -> blue
                DayCellKind.UNSCHEDULED -> dark.copy(alpha = 0.25f)
                else -> dark
            }
            drawRoundRect(color, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(w / 2))
        }
    }
}

/** Daily quantity bars, scaled to the month's largest day (per-provider only). */
@Composable
private fun DailyBars(state: MonthState) {
    val gk = MaterialTheme.gharColors
    val p = state.provider ?: return
    val days = state.cells.filter { it.epochDay >= p.startEpochDay && it.epochDay <= state.todayEpochDay }
    val qtys = days.map { c ->
        when (c.kind) {
            DayCellKind.PRESENT -> p.defaultQty
            DayCellKind.CHANGED, DayCellKind.EXTRA -> c.qty ?: p.defaultQty
            else -> 0.0
        }
    }
    val max = (qtys.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        if (qtys.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (qtys.size - 1)) / qtys.size).coerceAtLeast(1f)
        qtys.forEachIndexed { i, q ->
            val h = if (q <= 0.0) 2.dp.toPx() else (size.height * (q / max).toFloat()).coerceAtLeast(4.dp.toPx())
            val last = i == qtys.lastIndex
            drawRoundRect(
                if (last) gk.lime else gk.lime.copy(alpha = 0.55f),
                Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2),
            )
        }
    }
}

@Composable
private fun MonthSwitcher(state: MonthState, onPrev: () -> Unit, onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = state.canGoPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.month_prev),
                tint = if (state.canGoPrevious) cs.onSurface else cs.onSurface.copy(alpha = 0.3f),
            )
        }
        Text(
            BillTextFormatter.monthLabel(state.yearMonthValue, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.month_next),
                tint = if (state.canGoNext) cs.onSurface else cs.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(state: MonthState, onCellClick: (DayCell) -> Unit) {
    val first = state.cells.firstOrNull() ?: return
    val lead = com.postpole.gharkhata.domain.Schedule.weekdayIndex(first.epochDay)
    val names = listOf(
        R.string.day_short_mon, R.string.day_short_tue, R.string.day_short_wed, R.string.day_short_thu,
        R.string.day_short_fri, R.string.day_short_sat, R.string.day_short_sun,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            names.forEach {
                Text(
                    stringResource(it).take(1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val slots: List<DayCell?> = List(lead) { null } + state.cells
        slots.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val cell = week.getOrNull(i)
                    if (cell == null) Spacer(Modifier.weight(1f)) else {
                        DayCellView(cell, state, Modifier.weight(1f), onClick = { onCellClick(cell) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, state: MonthState, modifier: Modifier, onClick: () -> Unit) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val isToday = cell.epochDay == state.todayEpochDay
    val (bg, fg) = when (cell.kind) {
        DayCellKind.PRESENT -> gk.lime to gk.onLime
        DayCellKind.ABSENT -> cs.errorContainer to cs.onErrorContainer
        DayCellKind.HALF -> gk.saffronContainer to gk.onSaffronContainer
        DayCellKind.CHANGED, DayCellKind.EXTRA -> cs.tertiaryContainer to cs.onTertiaryContainer
        DayCellKind.UNSCHEDULED -> cs.surfaceContainer to cs.onSurface
        DayCellKind.FUTURE, DayCellKind.BEFORE_START -> cs.surfaceContainerHigh.copy(alpha = 0.5f) to cs.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val (finalBg, finalFg) = if (isToday && cell.editable) gk.hero to gk.lime else bg to fg
    val symbol = when (cell.kind) {
        DayCellKind.PRESENT -> "✓"
        DayCellKind.ABSENT -> "✗"
        DayCellKind.HALF -> "½"
        DayCellKind.CHANGED -> cell.qty?.let { "${formatQty(it)}${state.provider?.unitLabel.orEmpty()}" }.orEmpty()
        DayCellKind.EXTRA -> "+"
        else -> ""
    }
    val dom = LocalDate.ofEpochDay(cell.epochDay).dayOfMonth
    val desc = buildString {
        append(LocalDate.ofEpochDay(cell.epochDay).format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())))
        append(", ")
        append(
            when (cell.kind) {
                DayCellKind.BEFORE_START -> stringResource(R.string.month_cell_before_start)
                DayCellKind.FUTURE -> stringResource(R.string.month_cell_future)
                DayCellKind.UNSCHEDULED -> stringResource(R.string.month_cell_not_scheduled)
                DayCellKind.PRESENT -> stringResource(R.string.status_present)
                DayCellKind.ABSENT -> stringResource(R.string.status_absent)
                DayCellKind.HALF -> stringResource(R.string.status_half)
                DayCellKind.CHANGED, DayCellKind.EXTRA -> symbol
            },
        )
    }
    val editLabel = stringResource(R.string.month_cell_edit)
    Column(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(finalBg)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .clickable(enabled = cell.editable, onClickLabel = editLabel, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(dom.toString(), style = MaterialTheme.typography.labelLarge, color = finalFg, maxLines = 1)
        if (symbol.isNotEmpty()) {
            Text(
                symbol,
                style = MaterialTheme.typography.labelMedium,
                color = finalFg,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(state: MonthState) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val perUnit = state.provider?.billingMode == BillingMode.PER_UNIT
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendChip(gk.lime, stringResource(if (perUnit) R.string.month_legend_delivered else R.string.month_legend_present))
        LegendChip(cs.error, stringResource(R.string.month_legend_skipped))
        if (!perUnit) LegendChip(gk.saffron, stringResource(R.string.month_legend_half))
        if (perUnit) LegendChip(cs.tertiary, stringResource(R.string.month_legend_changed))
        LegendChip(null, stringResource(R.string.month_legend_extra))
    }
}

@Composable
private fun LegendChip(dot: Color?, text: String) {
    Row(
        Modifier.heightIn(min = 32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun BigButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier.heightIn(min = 56.dp).clip(CircleShape).background(container)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = if (badge) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(if (badge) 6.dp else 8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        if (badge) {
            Spacer(Modifier.width(6.dp))
            ProBadge()
        }
    }
}
