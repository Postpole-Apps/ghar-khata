package com.postpole.gharkhata.ui.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.domain.BillCalculator
import com.postpole.gharkhata.domain.BillLineKind
import com.postpole.gharkhata.domain.MonthBill
import com.postpole.gharkhata.pro.NoProPrompts
import com.postpole.gharkhata.pro.ProPromptScreen
import com.postpole.gharkhata.pro.ProPromptTrigger
import com.postpole.gharkhata.pro.ProPrompts
import com.postpole.gharkhata.ui.today.TodayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class DayCellKind { BEFORE_START, FUTURE, UNSCHEDULED, PRESENT, ABSENT, HALF, CHANGED, EXTRA }

/** One calendar day. [qty] is set for CHANGED and EXTRA cells. */
data class DayCell(
    val epochDay: Long,
    val kind: DayCellKind,
    val scheduled: Boolean,
    val mark: DayMarkEntity?,
    val qty: Double? = null,
) {
    val editable: Boolean get() = kind != DayCellKind.BEFORE_START && kind != DayCellKind.FUTURE
    val delivered: Boolean
        get() = kind == DayCellKind.PRESENT || kind == DayCellKind.CHANGED ||
            kind == DayCellKind.EXTRA || kind == DayCellKind.HALF
}

data class MonthState(
    val yearMonth: Int,
    val todayEpochDay: Long,
    val loaded: Boolean = false,
    val provider: ProviderEntity? = null,
    val bill: MonthBill? = null,
    val cells: List<DayCell> = emptyList(),
    val settlement: MonthSettlementEntity? = null,
    /** Days from the billing start to today (or month end) that were scheduled or marked. */
    val countedDays: Int = 0,
    val deliveredDays: Int = 0,
    val absentDays: Int = 0,
    val totalQty: Double = 0.0,
    val absences: Double = 0.0,
    val paidLeavesUsed: Double = 0.0,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    /** Rate or salary in force today, for the header subtitle. Null before any rate exists. */
    val ratePaise: Long? = null,
) {
    val yearMonthValue: YearMonth get() = YearMonth.of(yearMonth / 100, yearMonth % 100)

    /** First and last billed day, or null when the month is entirely before the start / in the future. */
    val billedRange: LongRange?
        get() {
            val p = provider ?: return null
            val ym = yearMonthValue
            val from = maxOf(ym.atDay(1).toEpochDay(), p.startEpochDay)
            val to = minOf(ym.atEndOfMonth().toEpochDay(), todayEpochDay)
            return if (from <= to) from..to else null
        }
}

@HiltViewModel
class ProviderMonthViewModel internal constructor(
    savedState: SavedStateHandle,
    private val repository: ProviderRepository,
    private val analytics: Analytics,
    private val todayProvider: () -> Long,
    private val prompts: ProPrompts = NoProPrompts,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        repository: ProviderRepository,
        analytics: Analytics,
        prompts: ProPrompts,
    ) : this(savedState, repository, analytics, { LocalDate.now().toEpochDay() }, prompts)

    private val _autoProSheet = MutableStateFlow(false)

    /** The automatic Pro sheet (bill milestone) is open on this screen. */
    val autoProSheet: StateFlow<Boolean> = _autoProSheet

    fun openAutoProSheet() { _autoProSheet.value = true }

    fun closeAutoProSheet() { _autoProSheet.value = false }

    val providerId: Long = savedState.get<Long>("id") ?: 0L
    private val todayEpochDay: Long = todayProvider()
    private val currentMonth: Int = LocalDate.ofEpochDay(todayEpochDay).let { it.year * 100 + it.monthValue }
    private val selected = MutableStateFlow(
        (savedState.get<Int>("yyyymm") ?: 0).takeIf { it in 190001..999912 && it % 100 in 1..12 } ?: currentMonth,
    )

    val state: StateFlow<MonthState> = combine(
        selected,
        repository.observeAll().map { all -> all.firstOrNull { it.id == providerId } },
        repository.observeRates(providerId),
        repository.observeMarks(providerId, Long.MIN_VALUE, Long.MAX_VALUE),
        repository.observeSettlements(providerId),
    ) { ym, provider, rates, marks, settlements ->
        if (provider == null) return@combine MonthState(ym, todayEpochDay, loaded = true)
        build(provider, rates, marks, settlements, ym)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthState(selected.value, todayEpochDay))

    private fun build(
        provider: ProviderEntity,
        rates: List<com.postpole.gharkhata.data.RateChangeEntity>,
        marks: List<DayMarkEntity>,
        settlements: List<MonthSettlementEntity>,
        ym: Int,
    ): MonthState {
        val bill = BillCalculator.compute(provider, rates, marks, ym, todayEpochDay, settlements)
        val month = YearMonth.of(ym / 100, ym % 100)
        val markByDay = marks.associateBy { it.epochDay }
        val cells = (1..month.lengthOfMonth()).map { dom ->
            cellFor(provider, month.atDay(dom).toEpochDay(), markByDay[month.atDay(dom).toEpochDay()], todayEpochDay)
        }
        val inRange = cells.filter { it.epochDay >= provider.startEpochDay && it.epochDay <= todayEpochDay }
        val counted = inRange.filter { it.scheduled || it.mark != null }
        val startMonth = YearMonth.from(LocalDate.ofEpochDay(provider.startEpochDay))
        return MonthState(
            yearMonth = ym,
            todayEpochDay = todayEpochDay,
            loaded = true,
            provider = provider,
            bill = bill,
            cells = cells,
            settlement = settlements.firstOrNull { it.yearMonth == ym },
            countedDays = counted.size,
            deliveredDays = counted.count { it.delivered },
            absentDays = counted.count { it.kind == DayCellKind.ABSENT },
            totalQty = bill.lines.filter { it.kind == BillLineKind.RATE_SEGMENT }.sumOf { it.quantity },
            absences = bill.lines.firstOrNull { it.kind == BillLineKind.ABSENCES }?.quantity ?: 0.0,
            paidLeavesUsed = bill.lines.firstOrNull { it.kind == BillLineKind.PAID_LEAVES }?.quantity ?: 0.0,
            canGoPrevious = month > startMonth,
            canGoNext = ym < currentMonth,
            ratePaise = rates.filter { it.effectiveFromEpochDay <= todayEpochDay }
                .maxByOrNull { it.effectiveFromEpochDay }?.amountPaise,
        )
    }

    fun previousMonth() {
        val s = state.value
        if (s.canGoPrevious) selected.value = s.yearMonthValue.minusMonths(1).let { it.year * 100 + it.monthValue }
    }

    fun nextMonth() {
        val s = state.value
        if (s.canGoNext) selected.value = s.yearMonthValue.plusMonths(1).let { it.year * 100 + it.monthValue }
    }

    /**
     * Stores the day. A scheduled day that ends up PRESENT at the default quantity with no note is the
     * default again, so its row is removed. PRESENT on an unscheduled day is stored as EXTRA.
     */
    fun saveDay(epochDay: Long, status: DayStatus, qty: Double?, note: String?) {
        val provider = state.value.provider ?: return
        if (epochDay > todayEpochDay) return
        val scheduled = TodayViewModel.isScheduledOn(provider, epochDay)
        val stored = if (status == DayStatus.PRESENT && !scheduled) DayStatus.EXTRA else status
        val cleanNote = note?.trim()?.ifEmpty { null }
        val storedQty = if (provider.billingMode == BillingMode.PER_UNIT && stored != DayStatus.ABSENT) qty else null
        viewModelScope.launch {
            val isDefault = scheduled && stored == DayStatus.PRESENT &&
                (storedQty == null || storedQty == provider.defaultQty) && cleanNote == null
            if (isDefault) {
                repository.clearMark(provider.id, epochDay)
            } else {
                repository.setMark(DayMarkEntity(provider.id, epochDay, stored, storedQty, cleanNote, System.currentTimeMillis()))
            }
            analytics.logEvent(AnalyticsEvents.DAY_MARKED, mapOf(AnalyticsParams.STATUS to stored.name))
        }
    }

    /** Drops the day's mark so it follows the schedule and default quantity again. */
    fun resetDay(epochDay: Long) {
        viewModelScope.launch { repository.clearMark(providerId, epochDay) }
    }

    /**
     * Records a payment for the selected month. `billedPaise` is a snapshot of the month's total due at
     * this moment (including what was carried in), `paidPaise` what was paid. The difference, positive
     * (short) or negative (overpaid), carries into the next month's previous balance via [BillCalculator].
     * Returns false when nothing was saved (no provider yet, or a negative amount).
     * [offerPro] is true only for the Month screen: once the settlement is saved the automatic Pro sheet may
     * open there. Bill preview passes false and shows it on return instead.
     */
    fun markPaid(paidPaise: Long, paidOnEpochDay: Long, note: String?, offerPro: Boolean = false): Boolean {
        val s = state.value
        val bill = s.bill ?: return false
        if (s.provider == null || paidPaise < 0) return false
        val settlement = MonthSettlementEntity(
            providerId = providerId,
            yearMonth = s.yearMonth,
            billedPaise = bill.duePaise,
            paidPaise = paidPaise,
            paidOnEpochDay = paidOnEpochDay,
            note = note?.trim()?.ifEmpty { null },
        )
        viewModelScope.launch {
            repository.saveSettlement(settlement)
            analytics.logEvent(AnalyticsEvents.SETTLEMENT_SAVED, mapOf(AnalyticsParams.STATUS to if (paidPaise < bill.duePaise) "short" else "full"))
            prompts.recordBillMilestone()
            if (offerPro && prompts.reserve(ProPromptTrigger.BILL_MILESTONE, ProPromptScreen.PROVIDER_MONTH)) {
                _autoProSheet.value = true
            }
        }
        return true
    }

    /**
     * Removes the visible month's payment record (a correction, not a settle event: no analytics, no Pro
     * offer). No-op when there is nothing to clear. The bill recomputes from the settlements flow.
     */
    fun clearPayment() {
        val s = state.value
        if (s.provider == null || s.settlement == null) return
        val ym = s.yearMonth
        viewModelScope.launch { repository.deleteSettlement(providerId, ym) }
    }

    fun onShared(channel: String) {
        analytics.logEvent(AnalyticsEvents.BILL_SHARED, mapOf(AnalyticsParams.FORMAT to channel))
        viewModelScope.launch { prompts.recordBillMilestone() }
    }

    companion object {
        /** What a day looks like in the calendar grid. */
        fun cellFor(p: ProviderEntity, epochDay: Long, mark: DayMarkEntity?, today: Long): DayCell {
            val scheduled = TodayViewModel.isScheduledOn(p, epochDay)
            if (epochDay < p.startEpochDay) return DayCell(epochDay, DayCellKind.BEFORE_START, scheduled, mark)
            if (epochDay > today) return DayCell(epochDay, DayCellKind.FUTURE, scheduled, mark)
            val kind = when (mark?.status) {
                null -> if (scheduled) DayCellKind.PRESENT else DayCellKind.UNSCHEDULED
                DayStatus.ABSENT -> DayCellKind.ABSENT
                DayStatus.HALF -> DayCellKind.HALF
                DayStatus.EXTRA -> DayCellKind.EXTRA
                DayStatus.PRESENT ->
                    if (p.billingMode == BillingMode.PER_UNIT && mark.qty != null && mark.qty != p.defaultQty) DayCellKind.CHANGED
                    else DayCellKind.PRESENT
            }
            val qty = if (kind == DayCellKind.CHANGED || kind == DayCellKind.EXTRA) mark?.qty ?: p.defaultQty else null
            return DayCell(epochDay, kind, scheduled, mark, qty)
        }
    }
}
