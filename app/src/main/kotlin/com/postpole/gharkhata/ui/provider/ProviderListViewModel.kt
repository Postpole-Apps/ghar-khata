package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.NoOpAnalytics
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.ProviderWithRates
import com.postpole.gharkhata.domain.BillCalculator
import com.postpole.gharkhata.domain.Schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.postpole.gharkhata.pro.NoProPrompts
import com.postpole.gharkhata.pro.ProDismissMethod
import com.postpole.gharkhata.pro.ProPromptScreen
import com.postpole.gharkhata.pro.ProPromptTrigger
import com.postpole.gharkhata.pro.ProPrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One cell of the per-provider 30-day delivery strip. */
enum class StripDay { DELIVERED, ABSENT, HALF, NONE }

data class ProviderRow(
    val provider: ProviderEntity,
    /** This month's running total through today, in paise (BillCalculator). */
    val monthTotalPaise: Long,
    /** Rate (or monthly amount) in force today, in paise; null if none. */
    val ratePaise: Long?,
    /** Last [STRIP_DAYS] days ending today, oldest first. */
    val strip: List<StripDay>,
    /** Carried balance from earlier months (positive = due, negative = credit), this provider only. */
    val previousBalancePaise: Long = 0L,
    /** monthTotalPaise + previousBalancePaise - paid in this month's settlement. */
    val netDuePaise: Long = monthTotalPaise,
    /** This month has a settlement and nothing is left due. */
    val isCurrentMonthSettled: Boolean = false,
)

data class ProviderListState(
    val active: List<ProviderRow> = emptyList(),
    val archived: List<ProviderEntity> = emptyList(),
    val isPro: Boolean = false,
    /** The first emission with real data has arrived. */
    val loaded: Boolean = false,
) {
    val canAdd: Boolean get() = canAddActiveProvider(active.size, isPro)
}

const val STRIP_DAYS = 30

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderListViewModel @Inject constructor(
    private val repository: ProviderRepository,
    proStatus: ProStatus,
    private val prompts: ProPrompts = NoProPrompts,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {

    /**
     * Persists [idsInOrder] (active providers, top to bottom) as sortOrder 0..n-1. Does nothing, and logs
     * nothing, when it matches the order currently shown.
     */
    fun saveOrder(idsInOrder: List<Long>) {
        val current = state.value.active.map { it.provider.id }
        if (idsInOrder == current) return
        viewModelScope.launch {
            repository.reorder(idsInOrder)
            analytics.logEvent(AnalyticsEvents.PROVIDERS_REORDERED)
        }
    }

    private fun activeRows(today: Long): Flow<List<ProviderRow>> {
        val date = LocalDate.ofEpochDay(today)
        val yearMonth = date.year * 100 + date.monthValue
        val monthStart = date.withDayOfMonth(1).toEpochDay()
        val from = minOf(monthStart, today - (STRIP_DAYS - 1))
        return repository.observeActiveWithRates().flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    list.map { pr ->
                        combine(
                            repository.observeMarks(pr.provider.id, from, today),
                            repository.observeSettlements(pr.provider.id),
                        ) { marks, settlements -> row(pr, marks, settlements, yearMonth, today) }
                    },
                ) { it.toList() }
            }
        }
    }

    private fun row(
        pr: ProviderWithRates,
        marks: List<DayMarkEntity>,
        settlements: List<MonthSettlementEntity>,
        yearMonth: Int,
        today: Long,
    ): ProviderRow {
        val bill = BillCalculator.compute(pr.provider, pr.rates, marks, yearMonth, today, settlements)
        val rate = pr.rates.filter { it.effectiveFromEpochDay <= today }.maxByOrNull { it.effectiveFromEpochDay }?.amountPaise
        val current = settlements.firstOrNull { it.yearMonth == yearMonth }
        val netDue = bill.totalPaise + bill.previousBalancePaise - (current?.paidPaise ?: 0L)
        return ProviderRow(
            provider = pr.provider,
            monthTotalPaise = bill.totalPaise,
            ratePaise = rate,
            strip = strip(pr.provider, marks, today),
            previousBalancePaise = bill.previousBalancePaise,
            netDuePaise = netDue,
            isCurrentMonthSettled = current != null && netDue == 0L,
        )
    }

    val state: StateFlow<ProviderListState> = combine(
        activeRows(LocalDate.now().toEpochDay()),
        repository.observeAll().map { all -> all.filter { it.archived } },
        proStatus.isPro,
    ) { active, archived, isPro -> ProviderListState(active, archived, isPro, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderListState())

    /** Play Billing price for the card; null until loaded. */
    val price: StateFlow<String?> = prompts.price

    private val cardGranted = MutableStateFlow(false)
    private val cardClosed = MutableStateFlow(false)
    private var cardShownLogged = false

    init {
        // Once per visit to the screen's view model: ask the policy with the real provider count.
        viewModelScope.launch {
            val s = state.first { it.loaded }
            val atFreeLimit = s.active.size >= FREE_ACTIVE_PROVIDER_LIMIT
            cardGranted.value = !s.isPro && prompts.reserve(
                ProPromptTrigger.MARKED_DAYS_MILESTONE, ProPromptScreen.PROVIDER_LIST, freeLimitCardShowing = atFreeLimit,
            )
        }
    }

    /** The marked-days card shows: granted by the policy, not closed, and never next to the 3/3 FreeLimitCard. */
    val showCard: StateFlow<Boolean> = combine(cardGranted, cardClosed, state) { granted, closed, s ->
        granted && !closed && !s.isPro && s.active.size < FREE_ACTIVE_PROVIDER_LIMIT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The card was drawn: starts its cooldown. Logged once per view model. */
    fun onCardShown() {
        if (cardShownLogged) return
        cardShownLogged = true
        viewModelScope.launch { prompts.onShown(ProPromptTrigger.MARKED_DAYS_MILESTONE) }
    }

    /** "See Pro": opens the sheet; the card goes away for this visit without counting as a dismissal. */
    fun onCardCta() {
        prompts.onCtaTap(ProPromptTrigger.MARKED_DAYS_MILESTONE)
        cardClosed.value = true
    }

    fun onCardDismissed(method: ProDismissMethod) {
        cardClosed.value = true
        viewModelScope.launch { prompts.onDismissed(ProPromptTrigger.MARKED_DAYS_MILESTONE, method) }
    }

    companion object {
        internal fun strip(provider: ProviderEntity, marks: List<DayMarkEntity>, today: Long): List<StripDay> {
            val byDay = marks.associateBy { it.epochDay }
            return (today - (STRIP_DAYS - 1)..today).map { d ->
                if (d < provider.startEpochDay) {
                    StripDay.NONE
                } else {
                    when (byDay[d]?.status) {
                        DayStatus.ABSENT -> StripDay.ABSENT
                        DayStatus.HALF -> StripDay.HALF
                        DayStatus.PRESENT, DayStatus.EXTRA -> StripDay.DELIVERED
                        null -> if (Schedule.isScheduled(provider.scheduleMask, d)) StripDay.DELIVERED else StripDay.NONE
                    }
                }
            }
        }
    }
}
