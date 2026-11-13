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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure bill computation; no Android or Room runtime use.
 *
 * Billed range = [max(month start, provider start), min(month end, today)].
 * MONTHLY_FIXED prorates the salary over that range using scheduled days in the *whole* month as divisor.
 * Previous balance walks the months from the provider's start month up to (not including) the requested one,
 * carrying a running balance: a settled month resets it to `billedPaise - paidPaise` (the snapshot is the
 * month's due, so it already includes whatever was carried in; an overpayment makes it negative, a credit);
 * a month without a settlement row adds its own computed total. Callers must pass all of the provider's marks
 * and rates. Rounding happens once, on each month's exact total; line amounts are cumulatively rounded so
 * they add up to that total.
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
        val own = settlements.filter { it.providerId == provider.id }.associateBy { it.yearMonth }
        var previous = 0L
        val target = YearMonth.of(yearMonth / 100, yearMonth % 100)
        var m = YearMonth.from(LocalDate.ofEpochDay(provider.startEpochDay))
        own.keys.minOrNull()?.let { e -> YearMonth.of(e / 100, e % 100).let { if (it < m) m = it } }
        while (m < target) {
            val code = m.year * 100 + m.monthValue
            val s = own[code]
            previous = if (s != null) s.billedPaise - s.paidPaise
            else previous + month(provider, rates, marks, code, today).totalPaise
            m = m.plusMonths(1)
        }
        val bill = month(provider, rates, marks, yearMonth, today)
        if (previous == 0L) return bill
        val lines = bill.lines + BillLine(BillLineKind.PREVIOUS_BALANCE, amountPaise = previous)
        return MonthBill(yearMonth, lines, bill.totalPaise, previous, bill.totalPaise + previous)
    }

    /** One month on its own (no previous balance). */
    private fun month(
        provider: ProviderEntity,
        rates: List<RateChangeEntity>,
        marks: List<DayMarkEntity>,
        yearMonth: Int,
        today: Long,
    ): MonthBill {
        val ym =YearMonth.of(yearMonth / 100, yearMonth % 100)
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

        // Exact total first, rounded once. Each line is then cumulatively rounded so lines sum to the total.
        val leaveExact = segs.fold(BigDecimal.ZERO) { a, x -> a + x.leaveAmount }
        val deductExact = segs.fold(BigDecimal.ZERO) { a, x -> a + x.deductAmount }
        var exactTotal = segs.fold(BigDecimal.ZERO) { a, x -> a + x.amount }
        if (provider.billingMode == BillingMode.PER_VISIT) exactTotal += leaveExact
        if (provider.billingMode == BillingMode.MONTHLY_FIXED) exactTotal -= deductExact
        val total = exactTotal.roundPaise()

        var cum = BigDecimal.ZERO
        var cumRounded = 0L
        fun step(exact: BigDecimal): Long {
            cum += exact
            val r = cum.roundPaise()
            val line = r - cumRounded
            cumRounded = r
            return line
        }

        val lines = mutableListOf<BillLine>()
        for (s in segs) {
            lines += BillLine(BillLineKind.RATE_SEGMENT, s.from, s.to, s.rate, s.qty.toDouble(), step(s.amount))
        }
        if (absentTotal.signum() > 0) {
            lines += BillLine(BillLineKind.ABSENCES, start, end, quantity = absentTotal.toDouble())
        }
        when (provider.billingMode) {
            BillingMode.PER_UNIT -> Unit
            BillingMode.PER_VISIT -> if (leavesUsed.signum() > 0) {
                lines += BillLine(BillLineKind.PAID_LEAVES, start, end, quantity = leavesUsed.toDouble(), amountPaise = step(leaveExact))
            }
            BillingMode.MONTHLY_FIXED -> {
                if (leavesUsed.signum() > 0) {
                    lines += BillLine(BillLineKind.PAID_LEAVES, start, end, quantity = leavesUsed.toDouble())
                }
                if (chargedTotal.signum() > 0) {
                    lines += BillLine(BillLineKind.DEDUCTION, start, end, quantity = chargedTotal.toDouble(), amountPaise = step(deductExact.negate()))
                }
            }
        }
        return MonthBill(yearMonth, lines, total, 0L, total)
    }

    private fun BigDecimal.roundPaise(): Long = setScale(0, RoundingMode.HALF_UP).longValueExact()
}
