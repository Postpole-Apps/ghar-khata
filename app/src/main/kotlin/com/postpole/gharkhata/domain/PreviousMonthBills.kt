package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderWithRates
import java.time.LocalDate
import java.time.YearMonth

/** A provider with something still due from earlier months. Amounts are deliberately not carried. */
data class DueProvider(val id: Long, val name: String)

/** "Last month's bills are ready": the providers still owing and the previous calendar month. */
data class PrevBillsCue(val previousMonth: YearMonth, val providers: List<DueProvider>) {
    /** yyyymm of [previousMonth], the form `Routes.providerMonth` takes. */
    val previousYearMonth: Int get() = previousMonth.year * 100 + previousMonth.monthValue
}

/** Everything the helper needs for one active provider (all of its marks, from its start date). */
class ProviderBillInput(
    val provider: ProviderWithRates,
    val marks: List<DayMarkEntity>,
    val settlements: List<MonthSettlementEntity>,
)

object PreviousMonthBills {
    /** The cue is only eligible on these days of the month. */
    val WINDOW_DAYS = 1..5

    /** Null outside days 1–5, or when no provider has a positive carry (credit and zero carries are skipped). */
    fun compute(today: Long, inputs: List<ProviderBillInput>): PrevBillsCue? {
        val date = LocalDate.ofEpochDay(today)
        if (date.dayOfMonth !in WINDOW_DAYS) return null
        val current = date.year * 100 + date.monthValue
        val due = inputs.mapNotNull { i ->
            val p = i.provider.provider
            val bill = BillCalculator.compute(p, i.provider.rates, i.marks, current, today, i.settlements)
            if (bill.previousBalancePaise > 0L) DueProvider(p.id, p.name) else null
        }
        if (due.isEmpty()) return null
        return PrevBillsCue(YearMonth.from(date).minusMonths(1), due)
    }
}
