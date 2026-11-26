package com.postpole.gharkhata.ui.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.domain.YearSummary
import com.postpole.gharkhata.domain.YearSummaryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class YearState(
    val year: Int,
    val loaded: Boolean = false,
    val provider: ProviderEntity? = null,
    val summary: YearSummary? = null,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

@HiltViewModel
class YearSummaryViewModel internal constructor(
    savedState: SavedStateHandle,
    repository: ProviderRepository,
    private val analytics: Analytics,
    private val todayProvider: () -> Long,
) : ViewModel() {

    @Inject
    constructor(savedState: SavedStateHandle, repository: ProviderRepository, analytics: Analytics) :
        this(savedState, repository, analytics, { LocalDate.now().toEpochDay() })

    private val providerId: Long = savedState.get<Long>("id") ?: 0L
    private val today = todayProvider()
    private val currentYear = LocalDate.ofEpochDay(today).year
    private val selected = MutableStateFlow(
        (savedState.get<Int>("year") ?: currentYear).takeIf { it in 1900..9999 } ?: currentYear,
    )

    val state: StateFlow<YearState> = combine(
        selected,
        repository.observeAll().map { all -> all.firstOrNull { it.id == providerId } },
        repository.observeRates(providerId),
        repository.observeMarks(providerId, Long.MIN_VALUE, Long.MAX_VALUE),
        repository.observeSettlements(providerId),
    ) { year, provider, rates, marks, settlements ->
        if (provider == null) return@combine YearState(year, loaded = true)
        val startYear = LocalDate.ofEpochDay(provider.startEpochDay).year
        YearState(
            year = year,
            loaded = true,
            provider = provider,
            summary = YearSummaryBuilder.build(provider, rates, marks, settlements, year, today),
            canGoPrevious = year > startYear,
            canGoNext = year < currentYear,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearState(selected.value))

    /** Logs one year-text share: enum format plus the number of months included, never names or amounts. */
    fun onShared(monthCount: Int) {
        analytics.logEvent(
            AnalyticsEvents.BILL_SHARED,
            mapOf(AnalyticsParams.FORMAT to "year_text", AnalyticsParams.ITEM_COUNT to monthCount),
        )
    }

    fun previousYear() {
        if (state.value.canGoPrevious) selected.value -= 1
    }

    fun nextYear() {
        if (state.value.canGoNext) selected.value += 1
    }
}
