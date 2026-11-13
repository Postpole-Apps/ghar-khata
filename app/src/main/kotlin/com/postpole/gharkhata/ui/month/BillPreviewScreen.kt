package com.postpole.gharkhata.ui.month

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.pro.rememberIsPro
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.domain.BillLine
import com.postpole.gharkhata.domain.BillLineKind
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.ui.provider.labelRes
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import com.postpole.gharkhata.ui.today.formatQty
import java.time.YearMonth
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

/** Bill for one provider-month: line items, totals, text share (ACTION_SEND / wa.me) and Mark paid. */
@Composable
fun BillPreviewScreen(
    onBack: () -> Unit,
    viewModel: ProviderMonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPaid by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    val isPro = rememberIsPro()
    val scope = rememberCoroutineScope()
    val provider = state.provider
    val bill = state.bill
    val formatter = remember(context) { BillTextFormatter(StringSource { id, args -> context.getString(id, *args) }) }
    val shareText = remember(provider, bill, state.settlement) {
        if (provider != null && bill != null) formatter.format(provider, bill, state.settlement) else ""
    }
    val cardBuilder = remember(context) { BillCardBuilder(StringSource { id, args -> context.getString(id, *args) }) }
    fun shareFile(format: BillFileFormat) {
        if (!isPro) { showPro = true; return }
        if (provider == null || bill == null) return
        val data = cardBuilder.build(provider, bill, state.settlement)
        scope.launch {
            val ok = try {
                shareBillFile(context, data, format, "bill-${provider.id}-${state.yearMonth}")
            } catch (e: java.io.IOException) {
                false
            }
            if (ok) viewModel.onShared(format.analytics)
            else android.widget.Toast.makeText(context, R.string.bill_share_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    BillPreviewContent(
        state = state,
        isPro = isPro,
        onBack = onBack,
        onShareImage = { shareFile(BillFileFormat.IMAGE) },
        onSharePdf = { shareFile(BillFileFormat.PDF) },
        onShareText = { shareAsText(context, shareText); viewModel.onShared("text") },
        onShareWhatsApp = {
            BillTextFormatter.whatsAppUrl(provider?.phone, shareText)?.let {
                openUrl(context, it); viewModel.onShared("whatsapp")
            }
        },
        onMarkPaid = { showPaid = true },
    )
    if (showPro) ProSheet(onDismiss = { showPro = false }, reason = ProReason.BILL_IMAGE)
    if (showPaid && provider != null && bill != null) {
        MarkPaidDialog(
            providerName = provider.name,
            yearMonth = state.yearMonthValue,
            duePaise = bill.duePaise,
            todayEpochDay = state.todayEpochDay,
            initialPaidOn = state.settlement?.paidOnEpochDay ?: state.todayEpochDay,
            initialPaidPaise = state.settlement?.paidPaise,
            initialNote = state.settlement?.note,
            onDismiss = { showPaid = false },
            onSave = { paid, on, note -> if (viewModel.markPaid(paid, on, note)) showPaid = false },
        )
    }
}

/** Plain text via the system share sheet. Needs no permission. */
private fun shareAsText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startSafely(context, Intent.createChooser(send, context.getString(R.string.bill_share_chooser)))
}

private fun openUrl(context: Context, url: String) =
    startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun startSafely(context: Context, intent: Intent) {
    try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.bill_share_failed, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Stateless bill preview (also used by the screenshot tests). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BillPreviewContent(
    state: MonthState,
    isPro: Boolean,
    onBack: () -> Unit,
    onShareImage: () -> Unit,
    onSharePdf: () -> Unit,
    onShareText: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val provider = state.provider
    val bill = state.bill
    val locale = Locale.getDefault()
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant))).statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.month_back), tint = gk.onHero)
            }
            Text(
                stringResource(R.string.bill_title, BillTextFormatter.monthLabel(state.yearMonthValue, locale)),
                style = MaterialTheme.typography.titleLarge, color = gk.onHero,
            )
        }
        if (provider == null || bill == null) {
            if (state.loaded) Text(stringResource(R.string.month_not_found), color = gk.onHero, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.bill_hero_label, provider.name, stringResource(provider.type.labelRes())).uppercase(locale),
                        style = MaterialTheme.typography.labelLarge, color = gk.onHeroVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(Money.format(bill.duePaise), style = GharNumerals.L, color = gk.lime, maxLines = 1)
                    val range = state.billedRange
                    Text(
                        stringResource(
                            R.string.bill_total_due_range,
                            if (range != null) BillTextFormatter.rangeLabel(range.first, range.last, locale) + " " + state.yearMonthValue.year
                            else BillTextFormatter.monthLabel(state.yearMonthValue, locale),
                        ),
                        style = MaterialTheme.typography.bodyLarge, color = gk.onHeroVariant,
                    )
                }
                Donut(state, Modifier.size(104.dp))
            }
            FlowRow(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val perUnit = provider.billingMode == BillingMode.PER_UNIT
                HeroChip(
                    stringResource(if (perUnit) R.string.bill_chip_delivered else R.string.bill_chip_present, state.deliveredDays),
                    gk.lime,
                )
                HeroChip(stringResource(R.string.bill_chip_skipped, state.absentDays), cs.error)
                if (perUnit) HeroChip(stringResource(R.string.bill_chip_qty, formatQty(state.totalQty), provider.unitLabel), null)
            }
            Spacer(Modifier.size(12.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(min = 520.dp).clip(GharShapesExtra.SheetTop)
                    .background(cs.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val segments = bill.lines.filter { it.kind == BillLineKind.RATE_SEGMENT }
                segments.forEachIndexed { i, line -> SegmentCard(provider, line, first = i == 0) }
                if (segments.isEmpty()) {
                    Text(stringResource(R.string.bill_nothing_yet), color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
                bill.lines.filter { it.kind != BillLineKind.RATE_SEGMENT && it.kind != BillLineKind.PREVIOUS_BALANCE }.forEach { ExtraLine(it) }

                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AmountRow(
                        stringResource(R.string.bill_month_total, BillTextFormatter.monthName(state.yearMonthValue, locale)),
                        BillTextFormatter.money(bill.totalPaise),
                    )
                    AmountRow(stringResource(R.string.bill_previous_balance), BillTextFormatter.money(bill.previousBalancePaise))
                    AmountRow(stringResource(R.string.bill_due), BillTextFormatter.money(bill.duePaise), bold = true)
                    state.settlement?.let {
                        Text(
                            stringResource(
                                R.string.bill_paid_stamp,
                                BillTextFormatter.dateLabel(it.paidOnEpochDay, locale),
                                BillTextFormatter.money(it.paidPaise),
                            ),
                            style = MaterialTheme.typography.labelLarge, color = gk.saffron,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Text(
                    stringResource(R.string.bill_share_header),
                    style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (BillTextFormatter.whatsAppNumber(provider.phone) != null) {
                        BigButton(
                            stringResource(R.string.bill_share_whatsapp), Icons.AutoMirrored.Outlined.Chat,
                            container = gk.hero, content = gk.lime, modifier = Modifier.weight(1.4f), onClick = onShareWhatsApp,
                        )
                    }
                    BigButton(
                        stringResource(R.string.bill_share_text), Icons.Outlined.Share,
                        container = cs.surfaceContainerHigh, content = cs.onSurface, modifier = Modifier.weight(1f), onClick = onShareText,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        stringResource(R.string.bill_share_image), Icons.Outlined.Image,
                        container = cs.surfaceContainerHigh, content = cs.onSurface, modifier = Modifier.weight(1f), onClick = onShareImage,
                        badge = !isPro,
                    )
                    BigButton(
                        stringResource(R.string.bill_share_pdf), Icons.Outlined.PictureAsPdf,
                        container = cs.surfaceContainerHigh, content = cs.onSurface, modifier = Modifier.weight(1f), onClick = onSharePdf,
                        badge = !isPro,
                    )
                }
                BigButton(
                    stringResource(if (state.settlement != null) R.string.month_update_payment else R.string.month_mark_paid),
                    Icons.Outlined.DoneAll,
                    container = gk.lime, content = gk.onLime, modifier = Modifier.fillMaxWidth(), onClick = onMarkPaid,
                )
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, dot: androidx.compose.ui.graphics.Color?) {
    val gk = MaterialTheme.gharColors
    Row(
        Modifier.heightIn(min = 32.dp).clip(CircleShape).background(gk.onHero.copy(alpha = 0.12f)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Spacer(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = gk.onHero)
    }
}

/** Attendance donut: delivered days vs. counted days, with a red arc for skipped days. */
@Composable
private fun Donut(state: MonthState, modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    val error = MaterialTheme.colorScheme.error
    val counted = state.countedDays.coerceAtLeast(1)
    val delivered = state.deliveredDays.toFloat() / counted
    val skipped = state.absentDays.toFloat() / counted
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(gk.onHero.copy(alpha = 0.12f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(gk.lime, -90f, 360f * delivered, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (skipped > 0f) {
                drawArc(error, -90f + 360f * delivered, 360f * skipped, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${state.deliveredDays}", style = GharNumerals.M, color = gk.onHero)
            Text(
                stringResource(R.string.bill_donut_of_days, state.countedDays),
                style = MaterialTheme.typography.labelMedium, color = gk.onHeroVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SegmentCard(provider: ProviderEntity, line: BillLine, first: Boolean) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val range = BillTextFormatter.rangeLabel(line.fromEpochDay!!, line.toEpochDay!!, locale)
    val title = when (provider.billingMode) {
        BillingMode.PER_UNIT -> stringResource(
            R.string.bill_line_segment_unit, range, "${formatQty(line.quantity)} ${provider.unitLabel}", BillTextFormatter.money(line.ratePaise),
        )
        BillingMode.PER_VISIT -> stringResource(
            R.string.bill_line_segment_visit, range, formatQty(line.quantity), BillTextFormatter.money(line.ratePaise),
        )
        BillingMode.MONTHLY_FIXED -> stringResource(
            R.string.bill_line_segment_monthly, range, formatQty(line.quantity), BillTextFormatter.money(line.ratePaise),
        )
    }
    val bg = if (first) Modifier.background(Brush.linearGradient(listOf(gk.lime, gk.limeDim))) else Modifier.background(cs.surfaceContainerHigh)
    val fg = if (first) gk.onLime else cs.onSurface
    Row(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card).then(bg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
        Text(BillTextFormatter.money(line.amountPaise), style = GharNumerals.S, color = fg, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ExtraLine(line: BillLine) {
    val text = when (line.kind) {
        BillLineKind.ABSENCES -> stringResource(R.string.bill_line_absences)
        BillLineKind.PAID_LEAVES -> stringResource(R.string.bill_line_paid_leaves)
        BillLineKind.DEDUCTION -> stringResource(R.string.bill_line_deduction, formatQty(line.quantity))
        else -> return
    }
    val right = if (line.kind == BillLineKind.DEDUCTION || line.amountPaise != 0L) BillTextFormatter.money(line.amountPaise)
    else formatQty(line.quantity)
    AmountRow(text, right, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun AmountRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = if (bold) GharNumerals.S else MaterialTheme.typography.titleMedium)
    }
}

/**
 * Mark paid: amount (defaults to the total due), date (defaults to today) and a note. A short amount
 * is carried into next month's bill; an overpayment is taken off it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkPaidDialog(
    providerName: String,
    yearMonth: YearMonth,
    duePaise: Long,
    todayEpochDay: Long,
    initialPaidOn: Long,
    initialPaidPaise: Long?,
    initialNote: String?,
    onDismiss: () -> Unit,
    onSave: (paidPaise: Long, paidOnEpochDay: Long, note: String?) -> Unit,
    /** Draw the dialog card in place instead of in a window (the screenshot tests can't capture windows). */
    inline: Boolean = false,
) {
    val gk = MaterialTheme.gharColors
    val locale = Locale.getDefault()
    var amount by remember { mutableStateOf(plainAmount(initialPaidPaise ?: duePaise.coerceAtLeast(0))) }
    var paidOn by remember { mutableLongStateOf(initialPaidOn) }
    var note by remember { mutableStateOf(initialNote.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }
    val paise = Money.parse(amount)?.takeIf { it >= 0 }
    val diff = if (paise == null) 0L else duePaise - paise
    val nextMonth = BillTextFormatter.monthName(yearMonth.plusMonths(1), locale)

    val title: @Composable () -> Unit = {
        Text(stringResource(R.string.paid_title, BillTextFormatter.monthName(yearMonth, locale)), style = MaterialTheme.typography.headlineSmall)
    }
    val body: @Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.paid_due_line, providerName, BillTextFormatter.money(duePaise)))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.paid_amount_label)) },
                    prefix = { Text("₹") },
                    isError = paise == null,
                    supportingText = {
                        when {
                            paise == null -> Text(stringResource(R.string.paid_invalid))
                            diff > 0 -> Text(stringResource(R.string.paid_short, BillTextFormatter.money(diff), nextMonth), color = gk.saffron)
                            diff < 0 -> Text(stringResource(R.string.paid_extra, BillTextFormatter.money(-diff), nextMonth), color = gk.saffron)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = GharNumerals.M,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = BillTextFormatter.dateLabel(paidOn, locale),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.paid_date_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.paid_pick_date))
                        }
                    },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.paid_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.paid_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        
    }
    val confirm: @Composable () -> Unit = {
        Button(
            enabled = paise != null,
            onClick = { onSave(paise ?: return@Button, paidOn, note) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = gk.hero, contentColor = gk.lime),
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.provider_save), style = MaterialTheme.typography.labelLarge)
        }
    }
    val dismiss: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    if (inline) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).clip(GharShapesExtra.ExtraLargeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            title()
            body()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { dismiss(); confirm() }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = GharShapesExtra.ExtraLargeIncreased,
            title = title,
            text = body,
            confirmButton = confirm,
            dismissButton = dismiss,
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = paidOn * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= todayEpochDay
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { paidOn = Math.floorDiv(it, MILLIS_PER_DAY) }
                    showPicker = false
                }) { Text(stringResource(R.string.provider_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** "1700" or "1700.5": no currency symbol or grouping, so it can be edited and re-parsed. */
internal fun plainAmount(paise: Long): String {
    val whole = paise / 100
    val frac = paise % 100
    return if (frac == 0L) "$whole" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}"
}
