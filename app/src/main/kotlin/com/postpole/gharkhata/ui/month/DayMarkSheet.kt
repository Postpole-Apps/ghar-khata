package com.postpole.gharkhata.ui.month

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.R
import com.postpole.gharkhata.ads.SuppressBanner
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.ui.provider.ProviderAvatar
import com.postpole.gharkhata.ui.provider.stepQty
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import com.postpole.gharkhata.ui.today.formatQty
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet to edit one day of one provider: status, quantity (per-unit) and a note.
 * [mark] is the stored exception, null when the day follows the schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMarkSheet(
    provider: ProviderEntity,
    epochDay: Long,
    mark: DayMarkEntity?,
    onSave: (status: DayStatus, qty: Double?, note: String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    SuppressBanner() // never an ad next to the day cells while marking a day
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = GharShapesExtra.SheetTop,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DayMarkSheetContent(provider, epochDay, mark, onSave, onReset)
    }
}

/** Sheet body, separate from the window so the screenshot tests can render it. */
@Composable
internal fun DayMarkSheetContent(
    provider: ProviderEntity,
    epochDay: Long,
    mark: DayMarkEntity?,
    onSave: (status: DayStatus, qty: Double?, note: String?) -> Unit,
    onReset: () -> Unit,
) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val perUnit = provider.billingMode == BillingMode.PER_UNIT
    var status by remember { mutableStateOf(if (mark?.status == DayStatus.EXTRA || mark == null) DayStatus.PRESENT else mark.status) }
    var qty by remember { mutableStateOf(formatQty(mark?.qty ?: provider.defaultQty)) }
    var note by remember { mutableStateOf(mark?.note.orEmpty()) }
    val value = qty.toDoubleOrNull()
    val canSave = status == DayStatus.ABSENT || !perUnit || (value != null && value > 0.0)
    val unit = provider.unitLabel
    val step = if (unit.trim().equals("L", ignoreCase = true)) "0.5" else "1"

    run {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderAvatar(provider.type, container = gk.lime, content = gk.onLime)
                Column(Modifier.weight(1f)) {
                    Text(
                        LocalDate.ofEpochDay(epochDay)
                            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
                            .uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                    )
                    Text(provider.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusOption(
                    selected = status == DayStatus.PRESENT,
                    icon = Icons.Filled.Check,
                    label = stringResource(if (perUnit) R.string.status_delivered else R.string.status_present),
                    modifier = Modifier.weight(1f),
                ) { status = DayStatus.PRESENT }
                if (!perUnit) {
                    StatusOption(
                        selected = status == DayStatus.HALF,
                        icon = Icons.Filled.Contrast,
                        label = stringResource(R.string.status_half),
                        modifier = Modifier.weight(1f),
                    ) { status = DayStatus.HALF }
                }
                StatusOption(
                    selected = status == DayStatus.ABSENT,
                    icon = Icons.Filled.Close,
                    label = stringResource(if (perUnit) R.string.status_skipped else R.string.status_absent),
                    modifier = Modifier.weight(1f),
                ) { status = DayStatus.ABSENT }
            }

            if (perUnit && status != DayStatus.ABSENT) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(qty, style = GharNumerals.L)
                        Text(unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                    }
                    Text(
                        stringResource(R.string.daysheet_usual, "${formatQty(provider.defaultQty)} $unit", step),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    // Ruler between the buttons; the - / + buttons stay as the accessible, non-gesture way to change it.
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StepButton(Icons.Outlined.Remove, stringResource(R.string.provider_qty_decrease), cs.surfaceContainerHigh, cs.onSurface) {
                            qty = stepQty(qty, unit, -1)
                        }
                        QtyRuler(
                            value = value ?: 0.0,
                            step = step.toDouble(),
                            unit = unit,
                            onValueChange = { qty = formatQty(it) },
                            modifier = Modifier.weight(1f),
                        )
                        StepButton(Icons.Outlined.Add, stringResource(R.string.provider_qty_increase), gk.hero, gk.lime) {
                            qty = stepQty(qty, unit, 1)
                        }
                    }
                }
            }

            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.today_note)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (mark != null) {
                    Row(
                        Modifier.heightIn(min = 48.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
                            .clickable(role = Role.Button) { onReset() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.daysheet_reset), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape)
                        .background(if (canSave) gk.hero else cs.surfaceContainerHigh)
                        .clickable(enabled = canSave, role = Role.Button) {
                            onSave(status, if (perUnit && status != DayStatus.ABSENT) value else null, note)
                        }
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.provider_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSave) gk.lime else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusOption(selected: Boolean, icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.heightIn(min = 48.dp).clip(CircleShape)
            .background(if (selected) gk.hero else cs.surfaceContainerHigh)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) gk.lime else cs.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) gk.lime else cs.onSurface)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.minimumInteractiveComponentSize().size(56.dp).clip(MaterialTheme.shapes.extraLarge).background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = content)
    }
}

/**
 * Horizontal ruler: swipe sideways to change the quantity, one tick per [step], a longer tick every second
 * step. The dark marker in the middle is the current value. Exposed to accessibility as an adjustable
 * value (set-progress), and the sheet keeps its -/+ buttons next to it.
 */
@Composable
private fun QtyRuler(
    value: Double,
    step: Double,
    unit: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tickGap = 14.dp
    val description = stringResource(R.string.daysheet_ruler)
    val current by rememberUpdatedState(value)
    val emit by rememberUpdatedState(onValueChange)
    var carry by remember { mutableStateOf(0f) }
    val tickPx = with(LocalDensity.current) { tickGap.toPx() }
    val minor = cs.onSurfaceVariant.copy(alpha = 0.55f)
    val major = cs.onSurfaceVariant
    val marker = MaterialTheme.gharColors.hero
    Box(
        modifier.height(56.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
            .semantics {
                contentDescription = description
                stateDescription = "${formatQty(value)} $unit"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), step.toFloat()..MAX_QTY.toFloat())
                setProgress { target ->
                    emit((Math.round(target / step) * step).coerceIn(step, MAX_QTY))
                    true
                }
            }
            .pointerInput(step, tickPx) {
                detectHorizontalDragGestures(onDragEnd = { carry = 0f }, onDragCancel = { carry = 0f }) { change, drag ->
                    change.consume()
                    // Dragging left moves the ruler left, i.e. the value goes up (like sliding a tape measure).
                    carry -= drag
                    val steps = (carry / tickPx).toInt()
                    if (steps != 0) {
                        carry -= steps * tickPx
                        emit((current + steps * step).coerceIn(step, MAX_QTY))
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = size.width / 2
            val index = Math.round(current / step).toInt()
            val half = (centre / tickPx).toInt() + 2
            for (i in -half..half) {
                val tickValue = index + i
                if (tickValue < 1) continue
                val x = centre + i * tickPx - carry
                if (x < 0f || x > size.width) continue
                val isMajor = tickValue % 2 == 0
                val h = size.height * if (isMajor) 0.5f else 0.3f
                drawLine(
                    if (isMajor) major else minor, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
                val mid = x + tickPx / 2
                if (mid < size.width) {
                    val hh = size.height * 0.18f
                    drawLine(minor.copy(alpha = 0.35f), Offset(mid, (size.height - hh) / 2), Offset(mid, (size.height + hh) / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            drawLine(marker, Offset(centre, size.height * 0.15f), Offset(centre, size.height * 0.85f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

private const val MAX_QTY = 99.0
