package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.RateChangeEntity
import java.time.LocalDate
import java.time.YearMonth

/** One month of a provider's year. [billedPaise] is that month's own bill (no carried balance). */
data class MonthSummary(
    val yearMonth: Int,
    val billedPaise: Long,
    val deliveredDays: Int,
    val absentDays: Int,
    val paidPaise: Long?,
)

/** A provider's calendar year, month by month. Per provider only: nothing here adds providers together. */
data class YearSummary(
    val year: Int,
    val months: List<MonthSummary>,
    /** Still owed at the end of the last billed month of the year (previous balance included). */
    val outstandingPaise: Long,
) {
    val totalBilledPaise: Long get() = months.sumOf { it.billedPaise }
    val totalPaidPaise: Long get() = months.sumOf { it.paidPaise ?: 0L }
    val totalDeliveredDays: Int get() = months.sumOf { it.deliveredDays }
}

object YearSummaryBuilder {
    /** Months of [year] from the provider's start (or January) up to [today]'s month (or December). */
    fun build(
        provider: ProviderEntity,
        rates: List<RateChangeEntity>,
        marks: List<DayMarkEntity>,
        settlements: List<MonthSettlementEntity>,
        year: Int,
        today: Long,
    ): YearSummary {
        val first = maxOf(YearMonth.of(year, 1), YearMonth.from(LocalDate.ofEpochDay(provider.startEpochDay)))
        val last = minOf(YearMonth.of(year, 12), YearMonth.from(LocalDate.ofEpochDay(today)))
        if (first > last) return YearSummary(year, emptyList(), 0)
        val markByDay = marks.associateBy { it.epochDay }
        val bySettlement = settlements.filter { it.providerId == provider.id }.associateBy { it.yearMonth }

        val months = mutableListOf<MonthSummary>()
        var lastDue = 0L
        var m = first
        while (m <= last) {
            val code = m.year * 100 + m.monthValue
            val bill = BillCalculator.compute(provider, rates, marks, code, today, settlements)
            val from = maxOf(m.atDay(1).toEpochDay(), provider.startEpochDay)
            val to = minOf(m.atEndOfMonth().toEpochDay(), today)
            var delivered = 0
            var absent = 0
            for (d in from..to) {
                val status = markByDay[d]?.status ?: if (Schedule.isScheduled(provider.scheduleMask, d)) DayStatus.PRESENT else null
                when (status) {
                    DayStatus.PRESENT, DayStatus.HALF, DayStatus.EXTRA -> delivered++
                    DayStatus.ABSENT -> if (Schedule.isScheduled(provider.scheduleMask, d)) absent++
                    null -> Unit
                }
            }
            val settled = bySettlement[code]
            months += MonthSummary(code, bill.totalPaise, delivered, absent, settled?.paidPaise)
            lastDue = bill.duePaise - (settled?.paidPaise ?: 0L)
            m = m.plusMonths(1)
        }
        return YearSummary(year, months, lastDue)
    }
}
