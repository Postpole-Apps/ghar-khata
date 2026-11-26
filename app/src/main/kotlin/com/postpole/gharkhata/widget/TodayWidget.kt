package com.postpole.gharkhata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.postpole.gharkhata.MainActivity
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.domain.PrevBillsCue
import com.postpole.gharkhata.ui.month.BillTextFormatter
import com.postpole.gharkhata.ui.theme.DarkColors
import com.postpole.gharkhata.ui.theme.DarkGharColors
import com.postpole.gharkhata.ui.theme.LightColors
import com.postpole.gharkhata.ui.theme.LightGharColors
import com.postpole.gharkhata.ui.today.TodayRow
import com.postpole.gharkhata.ui.today.TodayState
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pair(day: Color, night: Color) = ColorProvider(day = day, night = night)

/** Light/dark pairs from the app theme (GharKhataTheme / GharColors). */
private object WidgetColors {
    val background = pair(LightGharColors.hero, DarkGharColors.hero)
    val onBackground = pair(LightGharColors.onHero, DarkGharColors.onHero)
    val onBackgroundVariant = pair(LightGharColors.onHeroVariant, DarkGharColors.onHeroVariant)
    val lime = pair(LightGharColors.lime, DarkGharColors.lime)
    val onLime = pair(LightGharColors.onLime, DarkGharColors.onLime)
    val absent = pair(LightColors.errorContainer, DarkColors.errorContainer)
    val onAbsent = pair(LightColors.onErrorContainer, DarkColors.onErrorContainer)
    val half = pair(LightGharColors.saffronContainer, DarkGharColors.saffronContainer)
    /** Text-safe saffron for the cue: 7.75:1 on the light hero, 9.64:1 on the dark hero. */
    val saffronText = pair(DarkGharColors.saffron, DarkGharColors.saffron)
    val onHalf =pair(LightGharColors.onSaffronContainer, DarkGharColors.onSaffronContainer)
}

private val COMPACT = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val HEADER_HEIGHT = 64.dp
private val WIDGET_PADDING = 10.dp

/** Free home-screen window onto today's list: header with the Today hero wording, chips to cycle marks. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetRepository()
        val today = LocalDate.now().toEpochDay()
        val providers = repository.observeActive().first()
        val marks = repository.observeMarksForDay(today).first()
        val state = TodayWidgetState.withPrevBills(
            TodayWidgetState.build(providers, marks, today),
            TodayWidgetState.loadPrevBills(repository, today),
        )
        provideContent { TodayWidgetContent(state) }
    }
}

@Composable
private fun TodayWidgetContent(state: TodayState) {
    val context = LocalContext.current
    val open = actionStartActivity(MainActivity.openTodayIntent(context))
    val wide = LocalSize.current.width >= WIDE.width
    Box(
        GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(24.dp)
            .padding(WIDGET_PADDING).clickable(open),
    ) {
        when {
            state.noProviders -> EmptyContent()
            wide -> WideContent(state)
            else -> Header(state, compact = true)
        }
    }
}

@Composable
private fun Header(state: TodayState, compact: Boolean) {
    val context = LocalContext.current
    val total = state.rows.size
    val date = LocalDate.ofEpochDay(state.epochDay)
        .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    val changes = context.resources.getQuantityString(R.plurals.today_changes, state.changes, state.changes)
    val cue = state.prevBills?.takeIf { it.providers.isNotEmpty() }
    val cueFirst = TodayWidgetState.cueFirst(context.resources.configuration.fontScale, cue != null)
    Column(GlanceModifier.fillMaxWidth()) {
        Text(
            date,
            style = TextStyle(color = WidgetColors.onBackgroundVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        if (cueFirst && cue != null) {
            PrevBillsLine(context, cue, compact)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$total",
                style = TextStyle(color = WidgetColors.lime, fontSize = if (compact) 34.sp else 24.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                context.getString(R.string.today_of_marked, total),
                style = TextStyle(color = WidgetColors.onBackgroundVariant, fontSize = if (compact) 14.sp else 13.sp),
                modifier = GlanceModifier.padding(start = 6.dp, bottom = 3.dp),
            )
        }
        if (cue != null) {
            PrevBillsLine(context, cue, compact)
        } else {
            Text(
                changes,
                style = TextStyle(color = WidgetColors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** Saffron bills-ready line that replaces the changes line. Tapping it (like the rest of the widget) opens Today. */
@Composable
private fun PrevBillsLine(context: Context, cue: PrevBillsCue, compact: Boolean) {
    val month = BillTextFormatter.monthShort(cue.previousMonth, Locale.getDefault())
    val single = cue.providers.singleOrNull()
    val label: String
    val desc: String
    if (single != null) {
        val name = TodayWidgetState.cueName(single.name)
        label = context.getString(R.string.today_prev_bills_one, name, month)
        desc = context.getString(R.string.widget_prev_bills_cd_one, name, month)
    } else {
        label = context.getString(R.string.today_prev_bills_many, cue.providers.size, month)
        desc = context.getString(R.string.widget_prev_bills_cd_many, cue.providers.size, month)
    }
    Text(
        label,
        maxLines = if (compact) 2 else 1,
        style = TextStyle(color = WidgetColors.saffronText, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = desc },
    )
}

@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            context.getString(R.string.widget_empty_title),
            style = TextStyle(color = WidgetColors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        Box(
            GlanceModifier.height(48.dp).cornerRadius(24.dp).background(WidgetColors.lime).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                context.getString(R.string.widget_open_today),
                style = TextStyle(color = WidgetColors.onLime, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun WideContent(state: TodayState) {
    val context = LocalContext.current
    val available = LocalSize.current.height.value - HEADER_HEIGHT.value - WIDGET_PADDING.value * 2
    val shown = TodayWidgetState.visibleRows(state.rows.size, available)
    val hidden = state.rows.size - shown
    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT)) { Header(state, compact = false) }
        state.rows.take(shown).forEach { WidgetRow(it, state.epochDay) }
        if (hidden > 0) {
            Box(
                GlanceModifier.fillMaxWidth().height(TodayWidgetState.ROW_HEIGHT_DP.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    context.resources.getQuantityString(R.plurals.widget_more_in_app, hidden, hidden),
                    style = TextStyle(color = WidgetColors.onBackgroundVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun WidgetRow(row: TodayRow, epochDay: Long) {
    val context = LocalContext.current
    val label = statusLabel(context, row)
    val bg = when (row.status) {
        DayStatus.ABSENT -> WidgetColors.absent
        DayStatus.HALF -> WidgetColors.half
        else -> WidgetColors.lime
    }
    val fg = when (row.status) {
        DayStatus.ABSENT -> WidgetColors.onAbsent
        DayStatus.HALF -> WidgetColors.onHalf
        else -> WidgetColors.onLime
    }
    val chipText = if (row.provider.billingMode == BillingMode.PER_UNIT && row.status != DayStatus.ABSENT) {
        "${formatQty(row.qty)} ${row.provider.unitLabel}"
    } else {
        label
    }
    Row(
        GlanceModifier.fillMaxWidth().height(TodayWidgetState.ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.provider.name,
            maxLines = 1,
            style = TextStyle(color = WidgetColors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Box(
            GlanceModifier.height(48.dp).padding(vertical = 8.dp)
                .semantics { contentDescription = context.getString(R.string.widget_chip_cd, row.provider.name, label) }
                .clickable(
                    actionRunCallback<CycleChipAction>(
                        actionParametersOf(
                            CycleChipAction.ProviderKey to row.provider.id,
                            CycleChipAction.DayKey to epochDay,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                GlanceModifier.height(32.dp).cornerRadius(16.dp).background(bg).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    chipText,
                    maxLines = 1,
                    style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

private fun statusLabel(context: Context, row: TodayRow): String = when (row.status) {
    DayStatus.ABSENT -> context.getString(R.string.status_absent)
    DayStatus.HALF -> context.getString(R.string.status_half)
    DayStatus.EXTRA -> context.getString(R.string.status_extra)
    DayStatus.PRESENT ->
        if (row.provider.billingMode == BillingMode.PER_UNIT) context.getString(R.string.status_delivered)
        else context.getString(R.string.status_present)
}

private fun formatQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

/** Chip tap: cycles today's mark for one provider through the repository, then redraws the widget. */
class CycleChipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val providerId = parameters[ProviderKey] ?: return
        val repository = context.widgetRepository()
        val today = LocalDate.now().toEpochDay()
        // A widget left on yesterday's list must not edit yesterday: just redraw on today.
        if (parameters[DayKey] == today) {
            val providers = repository.observeActive().first()
            val marks = repository.observeMarksForDay(today).first()
            val row = TodayWidgetState.build(providers, marks, today).rows.firstOrNull { it.provider.id == providerId }
            if (row != null) {
                when (val write = TodayWidgetState.cycle(row, today, System.currentTimeMillis())) {
                    is ChipWrite.Clear -> repository.clearMark(write.providerId, write.epochDay)
                    is ChipWrite.Set -> repository.setMark(write.mark)
                }
            }
        }
        TodayWidgetUpdater.refresh(context)
    }

    companion object {
        val ProviderKey = ActionParameters.Key<Long>("providerId")
        val DayKey = ActionParameters.Key<Long>("epochDay")
    }
}
