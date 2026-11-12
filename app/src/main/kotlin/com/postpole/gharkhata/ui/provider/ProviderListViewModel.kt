package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.ProviderWithRates
import com.postpole.gharkhata.domain.BillCalculator
import com.postpole.gharkhata.domain.Schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
)

data class ProviderListState(
    val active: List<ProviderRow> = emptyList(),
    val archived: List<ProviderEntity> = emptyList(),
    val isPro: Boolean = false,
) {
    val canAdd: Boolean get() = canAddActiveProvider(active.size, isPro)
}

const val STRIP_DAYS = 30

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderListViewModel @Inject constructor(
    private val repository: ProviderRepository,
    proStatus: ProStatus,
) : ViewModel() {

    private fun activeRows(today: Long): Flow<List<ProviderRow>> {
        val date = LocalDate.ofEpochDay(today)
        val yearMonth = date.year * 100 + date.monthValue
        val monthStart = date.withDayOfMonth(1).toEpochDay()
        val from = minOf(monthStart, today - (STRIP_DAYS - 1))
        return repository.observeActiveWithRates().flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(list.map { pr -> repository.observeMarks(pr.provider.id, from, today).map { row(pr, it, yearMonth, today) } }) {
                    it.toList()
                }
            }
        }
    }

    private fun row(pr: ProviderWithRates, marks: List<DayMarkEntity>, yearMonth: Int, today: Long): ProviderRow {
        val bill = BillCalculator.compute(pr.provider, pr.rates, marks, yearMonth, today, emptyList())
        val rate = pr.rates.filter { it.effectiveFromEpochDay <= today }.maxByOrNull { it.effectiveFromEpochDay }?.amountPaise
        return ProviderRow(pr.provider, bill.totalPaise, rate, strip(pr.provider, marks, today))
    }

    val state: StateFlow<ProviderListState> = combine(
        activeRows(LocalDate.now().toEpochDay()),
        repository.observeAll().map { all -> all.filter { it.archived } },
        proStatus.isPro,
    ) { active, archived, isPro -> ProviderListState(active, archived, isPro) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderListState())

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
