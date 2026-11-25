package com.postpole.gharkhata.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.domain.PrevBillsCue
import com.postpole.gharkhata.domain.PreviousMonthBills
import com.postpole.gharkhata.domain.ProviderBillInput
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.provider.ProStatus
import com.postpole.gharkhata.ui.provider.canAddActiveProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One provider on the selected day. [mark] is the stored exception, null = follows the schedule. */
data class TodayRow(
    val provider: ProviderEntity,
    val mark: DayMarkEntity?,
    val scheduled: Boolean,
) {
    /** What the household sees: a scheduled day with no mark is present; EXTRA counts as present. */
    val status: DayStatus get() = mark?.status ?: DayStatus.PRESENT
    val qty: Double get() = mark?.qty ?: provider.defaultQty
    val note: String? get() = mark?.note

    /** Half days exist only for per-visit and monthly providers. */
    val canHalf: Boolean get() = provider.billingMode != BillingMode.PER_UNIT
}

data class TodayState(
    val epochDay: Long,
    val todayEpochDay: Long,
    val rows: List<TodayRow> = emptyList(),
    /** Active providers neither scheduled nor marked on [epochDay]: candidates for "Add extra delivery". */
    val extraCandidates: List<ProviderEntity> = emptyList(),
    val activeCount: Int = 0,
    val isPro: Boolean = false,
    /** Days 1–5 only: providers with a carry from earlier months. Replaces the next-bills chip. */
    val prevBills: PrevBillsCue? = null,
) {
    val noProviders: Boolean get() = activeCount == 0
    val canAddProvider: Boolean get() = canAddActiveProvider(activeCount, isPro)
    val canGoNext: Boolean get() = epochDay < todayEpochDay

    /** Rows that carry a stored mark (anything different from the default). */
    val changes: Int get() = rows.count { it.mark != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: ProviderRepository,
    proStatus: ProStatus,
    private val analytics: Analytics,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val todayEpochDay: Long = LocalDate.now().toEpochDay()
    private val selected = MutableStateFlow(todayEpochDay)

    val state: StateFlow<TodayState> = combine(
        selected,
        repository.observeActive(),
        selected.flatMapLatest { repository.observeMarksForDay(it) },
        proStatus.isPro,
        observePrevBills(),
    ) { day, providers, marks, isPro, prevBills ->
        val byProvider = marks.associateBy { it.providerId }
        val rows = providers.mapNotNull { p ->
            val mark = byProvider[p.id]
            val scheduled = isScheduledOn(p, day)
            if (scheduled || mark != null) TodayRow(p, mark, scheduled) else null
        }.sortedWith(compareBy({ it.provider.sortOrder }, { it.provider.id }))
        val shown = rows.map { it.provider.id }.toSet()
        TodayState(
            epochDay = day,
            todayEpochDay = todayEpochDay,
            rows = rows,
            extraCandidates = providers.filter { it.id !in shown && day >= it.startEpochDay },
            activeCount = providers.size,
            isPro = isPro,
            prevBills = prevBills,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(todayEpochDay, todayEpochDay))

    /** Re-evaluates whenever rates, marks or settlements change, so settling clears the cue without a restart. */
    private fun observePrevBills(): Flow<PrevBillsCue?> {
        if (LocalDate.ofEpochDay(todayEpochDay).dayOfMonth !in PreviousMonthBills.WINDOW_DAYS) return flowOf(null)
        return repository.observeActiveWithRates().flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(null)
            } else {
                combine(
                    list.map { pr ->
                        combine(
                            repository.observeMarks(pr.provider.id, pr.provider.startEpochDay, todayEpochDay),
                            repository.observeSettlements(pr.provider.id),
                        ) { marks, settlements -> ProviderBillInput(pr, marks, settlements) }
                    },
                ) { PreviousMonthBills.compute(todayEpochDay, it.toList()) }
            }
        }
    }

    /** Called when Today is on screen: remembers the real date so the daily reminder can stay quiet. */
    fun recordTodayOpened() {
        val now = LocalDate.now().toEpochDay()
        viewModelScope.launch { settings.setLastTodayOpenEpochDay(now) }
    }

    /** Moves to [epochDay]; future dates are ignored. */
    fun selectDate(epochDay: Long) {
        if (epochDay <= todayEpochDay) selected.value = epochDay
    }

    /** Chip tap: Present → Absent → Half → Present (no Half for per-unit). */
    fun cycleStatus(row: TodayRow) {
        val next = nextStatus(row.status, row.provider.billingMode)
        // An unscheduled day has no default to fall back on, so "present" there is an extra delivery.
        val stored = if (next == DayStatus.PRESENT && !row.scheduled) DayStatus.EXTRA else next
        write(row.provider, row.scheduled, stored, row.mark?.qty, row.mark?.note)
    }

    /** Qty dialog save (per-unit). Delivered, so PRESENT on a scheduled day and EXTRA otherwise. */
    fun setQty(row: TodayRow, qty: Double, note: String?) {
        if (qty <= 0.0) return
        val status = if (row.scheduled) DayStatus.PRESENT else DayStatus.EXTRA
        write(row.provider, row.scheduled, status, qty, note?.trim()?.ifEmpty { null })
    }

    /** Reset: drops the mark so the day follows the schedule and default qty again. */
    fun reset(row: TodayRow) {
        viewModelScope.launch { repository.clearMark(row.provider.id, selected.value) }
    }

    /** Adds an EXTRA delivery (default qty) for a provider that isn't scheduled on the selected day. */
    fun addExtra(provider: ProviderEntity) {
        write(provider, scheduled = false, DayStatus.EXTRA, provider.defaultQty, null)
    }

    private fun write(provider: ProviderEntity, scheduled: Boolean, status: DayStatus, qty: Double?, note: String?) {
        val day = selected.value
        viewModelScope.launch {
            val isDefault = scheduled && status == DayStatus.PRESENT &&
                (qty == null || qty == provider.defaultQty) && note.isNullOrBlank()
            if (isDefault) {
                repository.clearMark(provider.id, day)
            } else {
                repository.setMark(
                    DayMarkEntity(
                        providerId = provider.id,
                        epochDay = day,
                        status = status,
                        qty = qty,
                        note = note,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            analytics.logEvent(AnalyticsEvents.DAY_MARKED, mapOf(AnalyticsParams.STATUS to status.name))
        }
    }

    companion object {
        fun isScheduledOn(p: ProviderEntity, epochDay: Long): Boolean =
            epochDay >= p.startEpochDay && Schedule.isScheduled(p.scheduleMask, epochDay)

        /** Present → Absent → Half → Present; per-unit skips Half. EXTRA behaves like Present. */
        fun nextStatus(current: DayStatus, mode: BillingMode): DayStatus = when (current) {
            DayStatus.PRESENT, DayStatus.EXTRA -> DayStatus.ABSENT
            DayStatus.ABSENT -> if (mode == BillingMode.PER_UNIT) DayStatus.PRESENT else DayStatus.HALF
            DayStatus.HALF -> DayStatus.PRESENT
        }
    }
}
