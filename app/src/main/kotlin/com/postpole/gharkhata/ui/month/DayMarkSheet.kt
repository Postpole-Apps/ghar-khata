package com.postpole.gharkhata.ui.month

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = GharShapesExtra.SheetTop,
        containerColor = cs.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderAvatar(provider.type, container = gk.lime, content = gk.onLime)
                Column {
                    Text(
                        LocalDate.ofEpochDay(epochDay)
                            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
                            .uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                    )
                    Text(provider.name, style = MaterialTheme.typography.headlineSmall)
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
                    label = stringResource(R.string.status_absent),
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
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StepButton(Icons.Outlined.Remove, stringResource(R.string.provider_qty_decrease), cs.surfaceContainerHigh, cs.onSurface) {
                            qty = stepQty(qty, unit, -1)
                        }
                        StepButton(Icons.Outlined.Add, stringResource(R.string.provider_qty_increase), gk.hero, gk.lime) {
                            qty = stepQty(qty, unit, 1)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.today_note)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
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
