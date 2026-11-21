package com.postpole.gharkhata.widget

import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.ui.today.TodayRow
import com.postpole.gharkhata.ui.today.TodayState
import com.postpole.gharkhata.ui.today.TodayViewModel

/** What a chip tap does to storage. */
sealed interface ChipWrite {
    /** Back to the schedule default: drop the stored mark. */
    data class Clear(val providerId: Long, val epochDay: Long) : ChipWrite
    data class Set(val mark: DayMarkEntity) : ChipWrite
}

/** Pure mapping from stored data to the widget's view of Today, reusing the Today rules. */
object TodayWidgetState {

    /** Same row set, ordering and counts as [TodayViewModel]: scheduled today, plus anything already marked. */
    fun build(providers: List<ProviderEntity>, marks: List<DayMarkEntity>, epochDay: Long): TodayState {
        val byProvider = marks.filter { it.epochDay == epochDay }.associateBy { it.providerId }
        val rows = providers.mapNotNull { p ->
            val mark = byProvider[p.id]
            val scheduled = TodayViewModel.isScheduledOn(p, epochDay)
            if (scheduled || mark != null) TodayRow(p, mark, scheduled) else null
        }.sortedWith(compareBy({ it.provider.sortOrder }, { it.provider.id }))
        return TodayState(
            epochDay = epochDay,
            todayEpochDay = epochDay,
            rows = rows,
            activeCount = providers.size,
        )
    }

    /** Chip tap: same cycle as Today. Returning to the scheduled default clears the mark; unscheduled present is EXTRA. */
    fun cycle(row: TodayRow, epochDay: Long, nowMillis: Long): ChipWrite {
        val p = row.provider
        val next = TodayViewModel.nextStatus(row.status, p.billingMode)
        val stored = if (next == DayStatus.PRESENT && !row.scheduled) DayStatus.EXTRA else next
        val qty = row.mark?.qty
        val note = row.mark?.note
        val isDefault = row.scheduled && stored == DayStatus.PRESENT &&
            (qty == null || qty == p.defaultQty) && note.isNullOrBlank()
        return if (isDefault) {
            ChipWrite.Clear(p.id, epochDay)
        } else {
            ChipWrite.Set(DayMarkEntity(p.id, epochDay, stored, qty, note, nowMillis))
        }
    }

    /**
     * How many provider rows to draw in [availableHeightDp]. When some rows do not fit, one slot is
     * given to a "more in the app" line, so the cut is obvious.
     */
    fun visibleRows(total: Int, availableHeightDp: Float, rowHeightDp: Float = ROW_HEIGHT_DP): Int {
        val fit = (availableHeightDp / rowHeightDp).toInt().coerceAtLeast(0)
        return if (total <= fit) total else (fit - 1).coerceAtLeast(0)
    }

    const val ROW_HEIGHT_DP = 48f
}
