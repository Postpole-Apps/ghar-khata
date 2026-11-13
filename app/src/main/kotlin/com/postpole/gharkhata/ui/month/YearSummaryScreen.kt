package com.postpole.gharkhata.ui.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.domain.MonthSummary
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.pro.rememberIsPro
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Pro: one provider's year, month by month. Per provider only, never a household total. */
@Composable
fun YearSummaryScreen(
    onBack: () -> Unit,
    viewModel: YearSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPro = rememberIsPro()
    if (!isPro) {
        // Not reachable from the UI without Pro; guards a stale back stack after a refund.
        ProSheet(onDismiss = onBack, reason = ProReason.YEAR_SUMMARY)
        return
    }
    YearSummaryContent(state, onBack, viewModel::previousYear, viewModel::nextYear)
}

/** Stateless year summary (also used by the screenshot tests). */
@Composable
internal fun YearSummaryContent(
    state: YearState,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val provider = state.provider
    val summary = state.summary
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant))).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.month_back), tint = gk.onHero)
            }
            Text(
                if (provider != null) stringResource(R.string.year_title, provider.name, state.year) else "",
                style = MaterialTheme.typography.titleLarge, color = gk.onHero, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (provider == null || summary == null) {
            if (state.loaded) Text(stringResource(R.string.month_not_found), color = gk.onHero, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.year_total_billed, state.year).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge, color = gk.onHeroVariant,
                )
                Text(Money.format(summary.totalBilledPaise), style = GharNumerals.XL, color = gk.lime, maxLines = 1)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox(stringResource(R.string.year_total_paid), Money.format(summary.totalPaidPaise), Modifier.weight(1f))
                    StatBox(stringResource(R.string.year_outstanding), Money.format(summary.outstandingPaise), Modifier.weight(1f))
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 560.dp).padding(top = 12.dp).clip(GharShapesExtra.SheetTop)
                    .background(cs.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrev, enabled = state.canGoPrevious) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.year_prev))
                    }
                    Text(
                        "${state.year}", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = onNext, enabled = state.canGoNext) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.year_next))
                    }
                }
                if (summary.months.isEmpty()) {
                    Text(stringResource(R.string.year_no_data), color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                } else {
                    MonthBars(summary.months, provider)
                    Text(
                        stringResource(R.string.year_months_header),
                        style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    summary.months.asReversed().forEach { MonthRow(it) }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    val gk = MaterialTheme.gharColors
    Column(
        modifier.clip(GharShapesExtra.Card).background(gk.onHero.copy(alpha = 0.12f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = gk.onHeroVariant)
        Text(value, style = GharNumerals.M, color = gk.onHero, maxLines = 1)
    }
}

/** Twelve bars, one per month of this provider's year; months with no bill stay as empty slots. */
@Composable
private fun MonthBars(months: List<MonthSummary>, provider: ProviderEntity) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    val year = months.first().yearMonth / 100
    val byMonth = months.associateBy { it.yearMonth % 100 }
    val max = months.maxOf { it.billedPaise }.coerceAtLeast(1L)
    val locale = Locale.getDefault()
    val desc = stringResource(R.string.year_chart_desc, provider.name, year)
    Column(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card).background(cs.surfaceContainerHigh).padding(16.dp)
            .semantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bar = gk.hero
        val paid = cs.primary
        val track = cs.onSurface.copy(alpha = 0.08f)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / 12f
            val barW = slot * 0.6f
            for (m in 1..12) {
                val x = (m - 1) * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                val s = byMonth[m] ?: continue
                val h = (size.height * s.billedPaise / max).coerceAtLeast(barW)
                drawRoundRect(
                    if (s.paidPaise != null) paid else bar, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (m in 1..12) {
                Text(
                    YearMonth.of(year, m).month.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MonthRow(m: MonthSummary) {
    val cs = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val ym = YearMonth.of(m.yearMonth / 100, m.yearMonth % 100)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(BillTextFormatter.monthName(ym, locale), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.year_days_delivered, m.deliveredDays),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(m.billedPaise), style = GharNumerals.S)
            if (m.paidPaise != null) {
                Text(
                    stringResource(R.string.year_paid_amount, Money.format(m.paidPaise)),
                    style = MaterialTheme.typography.labelMedium, color = cs.primary,
                )
            }
        }
    }
}
