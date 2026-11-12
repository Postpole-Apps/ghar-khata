package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.RateChangeEntity
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Pure bill computation; no Android or Room runtime use.
 *
 * Billed range = [max(month start, provider start), min(month end, today)].
 * MONTHLY_FIXED prorates the salary over that range using scheduled days in the *whole* month as divisor.
 * Previous balance only counts earlier months whose settlement is short-paid (billed > paid);
 * months with no settlement row cannot be priced here, so callers wanting them included must pass
 * a synthetic settlement.
 */
object BillCalculator {
    private val MC = MathContext(30, RoundingMode.HALF_EVEN)
    private val HALF = BigDecimal("0.5")

    private class Seg(val rate: Long, val from: Long) {
        var to = from
        var qty: BigDecimal = BigDecimal.ZERO
        var amount: BigDecimal = BigDecimal.ZERO
        var leaveAmount: BigDecimal = BigDecimal.ZERO
        var deductAmount: BigDecimal = BigDecimal.ZERO
    }

    /** @param yearMonth yyyymm, @param today epoch day of "today". */
    fun compute(
        provider: ProviderEntity,
        rates: List<RateChangeEntity>,
        marks: List<DayMarkEntity>,
        yearMonth: Int,
        today: Long,
        settlements: List<MonthSettlementEntity>,
    ): MonthBill {
        val ym = YearMonth.of(yearMonth / 100, yearMonth % 100)
        val monthStart = ym.atDay(1).toEpochDay()
        val monthEnd = ym.atEndOfMonth().toEpochDay()
        val start = maxOf(monthStart, provider.startEpochDay)
        val end = minOf(monthEnd, today)

        val sortedRates = rates.sortedBy { it.effectiveFromEpochDay }
        val markByDay = marks.associateBy { it.epochDay }
        val mask = provider.scheduleMask
        val monthScheduled = Schedule.countScheduled(mask, monthStart, monthEnd)
        val defaultQty = BigDecimal.valueOf(provider.defaultQty)

        val segs = mutableListOf<Seg>()
        var leavesLeft = BigDecimal(provider.paidLeavesPerMonth)
        var absentTotal = BigDecimal.ZERO
        var leavesUsed = BigDecimal.ZERO
        var chargedTotal = BigDecimal.ZERO

        var d = start
        while (d <= end) {
            val rate = sortedRates.lastOrNull { it.effectiveFromEpochDay <= d }?.amountPaise ?: 0L
            var seg = segs.lastOrNull()
            if (seg == null || seg.rate != rate) {
                seg = Seg(rate, d)
                segs += seg
            }
            seg.to = d

            val scheduled = Schedule.isScheduled(mask, d)
            val mark = markByDay[d]
            // qty = units delivered; presence = share of a visit/day; absence = share of a scheduled day missed
            var qty = BigDecimal.ZERO
            var presence = BigDecimal.ZERO
            var absence = BigDecimal.ZERO
            when (mark?.status) {
                null -> if (scheduled) {
                    qty = defaultQty
                    presence = BigDecimal.ONE
                }
                DayStatus.ABSENT -> if (scheduled) absence = BigDecimal.ONE
                DayStatus.HALF -> {
                    qty = HALF
                    presence = HALF
                    if (scheduled) absence = HALF
                }
                DayStatus.EXTRA, DayStatus.PRESENT -> {
                    qty = mark.qty?.let { BigDecimal.valueOf(it) } ?: defaultQty
                    presence = BigDecimal.ONE
                }
            }
            val rateBd = BigDecimal(rate)

            // Paid-leave credit on this day's absence, applied chronologically.
            val credit = absence.min(leavesLeft)
            leavesLeft -= credit
            leavesUsed += credit
            absentTotal += absence
            val charged = absence - credit
            chargedTotal += charged

            when (provider.billingMode) {
                BillingMode.PER_UNIT -> {
                    seg.qty += qty
                    seg.amount += qty * rateBd
                }
                BillingMode.PER_VISIT -> {
                    seg.qty += presence
                    seg.amount += presence * rateBd
                    seg.leaveAmount += credit * rateBd
                }
                BillingMode.MONTHLY_FIXED -> if (monthScheduled > 0) {
                    val perDay = rateBd.divide(BigDecimal(monthScheduled), MC)
                    if (scheduled) {
                        seg.qty += BigDecimal.ONE
                        seg.amount += perDay
                    }
                    seg.deductAmount += charged * perDay
                }
            }
            d++
        }

        val lines = mutableListOf<BillLine>()
        var total = 0L
        for (s in segs) {
            val amt = s.amount.roundPaise()
            total += amt
            lines += BillLine(BillLineKind.RATE_SEGMENT, s.from, s.to, s.rate, s.qty.toDouble(), amt)
        }
        if (absentTotal.signum() > 0) {
            lines += BillLine(BillLineKind.ABSENCES, start, end, quantity = absentTotal.toDouble())
        }
        when (provider.billingMode) {
            BillingMode.PER_UNIT -> Unit
            BillingMode.PER_VISIT -> if (leavesUsed.signum() > 0) {
                val amt = segs.fold(BigDecimal.ZERO) { a, s -> a + s.leaveAmount }.roundPaise()
                total += amt
                lines += BillLine(BillLineKind.PAID_LEAVES, start, end, quantity = leavesUsed.toDouble(), amountPaise = amt)
            }
            BillingMode.MONTHLY_FIXED -> {
                if (leavesUsed.signum() > 0) {
                    lines += BillLine(BillLineKind.PAID_LEAVES, start, end, quantity = leavesUsed.toDouble())
                }
                if (chargedTotal.signum() > 0) {
                    val amt = segs.fold(BigDecimal.ZERO) { a, s -> a + s.deductAmount }.roundPaise()
                    total -= amt
                    lines += BillLine(BillLineKind.DEDUCTION, start, end, quantity = chargedTotal.toDouble(), amountPaise = -amt)
                }
            }
        }

        val previous = settlements
            .filter { it.providerId == provider.id && it.yearMonth < yearMonth }
            .sumOf { maxOf(0L, it.billedPaise - it.paidPaise) }
        if (previous != 0L) {
            lines += BillLine(BillLineKind.PREVIOUS_BALANCE, amountPaise = previous)
        }
        return MonthBill(yearMonth, lines, total, previous, total + previous)
    }

    private fun BigDecimal.roundPaise(): Long = setScale(0, RoundingMode.HALF_UP).longValueExact()
}
