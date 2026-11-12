package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * November 2026 has 30 days; Nov 1 is a Sunday. Mon–Fri days: 2-6, 9-13, 16-20, 23-27, 30 = 21.
 * Amounts are in paise (5000 = ₹50).
 */
class BillCalculatorTest {
    private val nov = 202611
    private fun day(d: Int, month: Int = 11) = LocalDate.of(2026, month, d).toEpochDay()
    private val afterNov = day(15, 12)
    private val longAgo = day(1, 1)

    private fun provider(
        mode: BillingMode,
        mask: Int,
        defaultQty: Double = 1.0,
        leaves: Int = 0,
        start: Long = longAgo,
    ) = ProviderEntity(
        id = 1, name = "P", type = ProviderType.OTHER, billingMode = mode, unitLabel = "u",
        defaultQty = defaultQty, scheduleMask = mask, paidLeavesPerMonth = leaves,
        startEpochDay = start, createdAt = 0,
    )

    private fun rate(from: Long, paise: Long) = RateChangeEntity(providerId = 1, effectiveFromEpochDay = from, amountPaise = paise)
    private fun mark(d: Long, s: DayStatus, qty: Double? = null) = DayMarkEntity(1, d, s, qty, null, 0)

    private fun compute(
        p: ProviderEntity,
        rates: List<RateChangeEntity>,
        marks: List<DayMarkEntity> = emptyList(),
        today: Long = afterNov,
        settlements: List<MonthSettlementEntity> = emptyList(),
        ym: Int = nov,
    ) = BillCalculator.compute(p, rates, marks, ym, today, settlements)

    // ---- PER_UNIT ----

    // Worked example (PER_UNIT): daily milk 1 L, rate ₹50 (5000), all 30 days, no marks.
    // 30 days × 1 L × 5000 = 150000.
    @Test fun perUnit_defaultPresentFullMonth() {
        val bill = compute(provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS), listOf(rate(longAgo, 5000)))
        assertEquals(150_000L, bill.totalPaise)
        assertEquals(150_000L, bill.duePaise)
        assertEquals(1_500L, bill.dueDisplayRupees)
    }

    // 30 days. Absent 3,4 (-2 L). HALF on 5 (0.5 instead of 1 → -0.5). PRESENT qty 2 on 6 (+1).
    // Units = 30 - 2 - 0.5 + 1 = 28.5 → 28.5 × 5000 = 142500.
    @Test fun perUnit_absentHalfAndChangedQty() {
        val marks = listOf(
            mark(day(3), DayStatus.ABSENT), mark(day(4), DayStatus.ABSENT),
            mark(day(5), DayStatus.HALF), mark(day(6), DayStatus.PRESENT, 2.0),
        )
        val bill = compute(provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS), listOf(rate(longAgo, 5000)), marks)
        assertEquals(142_500L, bill.totalPaise)
    }

    // Mon–Fri schedule: 21 scheduled days. EXTRA on Sat 7 with qty 2 → 21 + 2 = 23 units × 5000 = 115000.
    @Test fun perUnit_extraOnUnscheduledDay() {
        val bill = compute(
            provider(BillingMode.PER_UNIT, Schedule.WEEKDAYS), listOf(rate(longAgo, 5000)),
            listOf(mark(day(7), DayStatus.EXTRA, 2.0)),
        )
        assertEquals(115_000L, bill.totalPaise)
    }

    // defaultQty 0.5 L/day: 30 × 0.5 = 15 L × 5000 = 75000.
    @Test fun perUnit_fractionalDefaultQty() {
        val bill = compute(provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS, defaultQty = 0.5), listOf(rate(longAgo, 5000)))
        assertEquals(75_000L, bill.totalPaise)
    }

    // Rate 5000 up to Nov 15, 6000 from Nov 16. 15 × 5000 + 15 × 6000 = 75000 + 90000 = 165000. Two segments.
    @Test fun perUnit_midMonthRateChange() {
        val bill = compute(
            provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS),
            listOf(rate(longAgo, 5000), rate(day(16), 6000)),
        )
        assertEquals(165_000L, bill.totalPaise)
        val segs = bill.lines.filter { it.kind == BillLineKind.RATE_SEGMENT }
        assertEquals(2, segs.size)
        assertEquals(75_000L, segs[0].amountPaise)
        assertEquals(90_000L, segs[1].amountPaise)
    }

    // Provider starts Nov 21 → days 21..30 = 10 × 5000 = 50000 (earlier days excluded).
    @Test fun providerStartMidMonth_excludesEarlierDays() {
        val p = provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS, start = day(21))
        assertEquals(50_000L, compute(p, listOf(rate(day(21), 5000))).totalPaise)
        // A mark before the start date is ignored too.
        assertEquals(
            50_000L,
            compute(p, listOf(rate(day(21), 5000)), listOf(mark(day(5), DayStatus.PRESENT, 9.0))).totalPaise,
        )
    }

    // Current month, today = Nov 10: only days 1..10 → 10 × 5000 = 50000. Future month bills nothing.
    @Test fun currentMonthCountsOnlyUpToToday() {
        val p = provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        assertEquals(50_000L, compute(p, listOf(rate(longAgo, 5000)), today = day(10)).totalPaise)
        assertEquals(0L, compute(p, listOf(rate(longAgo, 5000)), today = day(25, 10)).totalPaise)
    }

    // On-demand (mask 0), default qty 1: EXTRA Nov 3 qty 2 → 2; PRESENT Nov 10 no qty → 1; ABSENT Nov 12 → 0.
    // 3 units × 5000 = 15000. Unmarked days bill nothing.
    @Test fun onDemand_billsOnlyMarkedDays() {
        val marks = listOf(
            mark(day(3), DayStatus.EXTRA, 2.0), mark(day(10), DayStatus.PRESENT), mark(day(12), DayStatus.ABSENT),
        )
        val bill = compute(provider(BillingMode.PER_UNIT, 0), listOf(rate(longAgo, 5000)), marks)
        assertEquals(15_000L, bill.totalPaise)
    }

    // Rounding half-up: provider starts Nov 30, qty 0.5 × 5 paise = 2.5 paise → 3 paise.
    @Test fun roundingHalfUpToPaise() {
        val p = provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS, defaultQty = 0.5, start = day(30))
        val bill = compute(p, listOf(rate(day(30), 5)))
        assertEquals(3L, bill.totalPaise)
        // 2.4 → 2 (qty 0.5 × 4.8 not representable; use 0.4 × 6 = 2.4)
        val p2 = provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS, defaultQty = 0.4, start = day(30))
        assertEquals(2L, compute(p2, listOf(rate(day(30), 6))).totalPaise)
    }

    // ---- PER_VISIT ----

    // Worked example (PER_VISIT): maid Mon–Fri, ₹200/visit (20000), 2 paid leaves, absent Nov 4,5,6 (3 days).
    // Visits = 21 - 3 = 18 → 360000. Leave credit = min(3, 2) = 2 → 40000 (absences over allowance: 1 unpaid).
    // Total = 400000.
    @Test fun perVisit_absencesOverAllowance() {
        val marks = listOf(4, 5, 6).map { mark(day(it), DayStatus.ABSENT) }
        val bill = compute(provider(BillingMode.PER_VISIT, Schedule.WEEKDAYS, leaves = 2), listOf(rate(longAgo, 20_000)), marks)
        assertEquals(400_000L, bill.totalPaise)
        val leaveLine = bill.lines.single { it.kind == BillLineKind.PAID_LEAVES }
        assertEquals(2.0, leaveLine.quantity, 0.0)
        assertEquals(40_000L, leaveLine.amountPaise)
    }

    // Same, but only 2 absences (within allowance): 19 visits = 380000 + 2 credits = 40000 → 420000 (= full month).
    @Test fun perVisit_absencesWithinAllowance() {
        val marks = listOf(4, 5).map { mark(day(it), DayStatus.ABSENT) }
        val bill = compute(provider(BillingMode.PER_VISIT, Schedule.WEEKDAYS, leaves = 2), listOf(rate(longAgo, 20_000)), marks)
        assertEquals(420_000L, bill.totalPaise)
    }

    // No leaves, HALF on Mon Nov 3: 20 full visits + 0.5 = 20.5 × 20000 = 410000.
    // A PRESENT with qty 3 still counts as one visit; EXTRA on Sat 7 adds 1 visit: +20000 → 430000.
    @Test fun perVisit_halfAndExtra() {
        val marks = listOf(
            mark(day(3), DayStatus.HALF), mark(day(7), DayStatus.EXTRA), mark(day(9), DayStatus.PRESENT, 3.0),
        )
        val bill = compute(provider(BillingMode.PER_VISIT, Schedule.WEEKDAYS), listOf(rate(longAgo, 20_000)), marks)
        assertEquals(430_000L, bill.totalPaise)
    }

    // ---- MONTHLY_FIXED ----

    // Worked example (MONTHLY_FIXED): cook all 30 days, salary ₹3000 (300000), 1 paid leave, absent Nov 10,11,12.
    // Per scheduled day = 300000 / 30 = 10000. Chargeable absences = max(0, 3 - 1) = 2 → deduction 20000.
    // Total = 300000 - 20000 = 280000.
    @Test fun monthlyFixed_absencesOverAllowance() {
        val marks = listOf(10, 11, 12).map { mark(day(it), DayStatus.ABSENT) }
        val bill = compute(provider(BillingMode.MONTHLY_FIXED, Schedule.ALL_DAYS, leaves = 1), listOf(rate(longAgo, 300_000)), marks)
        assertEquals(280_000L, bill.totalPaise)
        assertEquals(-20_000L, bill.lines.single { it.kind == BillLineKind.DEDUCTION }.amountPaise)
    }

    // Absences within the allowance (1 absent, 1 leave): no deduction → 300000.
    @Test fun monthlyFixed_absencesWithinAllowance() {
        val bill = compute(
            provider(BillingMode.MONTHLY_FIXED, Schedule.ALL_DAYS, leaves = 1), listOf(rate(longAgo, 300_000)),
            listOf(mark(day(10), DayStatus.ABSENT)),
        )
        assertEquals(300_000L, bill.totalPaise)
        assertTrue(bill.lines.none { it.kind == BillLineKind.DEDUCTION })
    }

    // Mon–Fri cook, salary 210000, 21 scheduled days → 10000/day. Absent Mon Nov 2 and HALF Nov 3, no leaves.
    // Deduction = (1 + 0.5) × 10000 = 15000 → 195000. (Absence on an unscheduled Sunday Nov 1 is not an absence.)
    @Test fun monthlyFixed_divisorIsScheduledDaysInMonth() {
        val marks = listOf(
            mark(day(2), DayStatus.ABSENT), mark(day(3), DayStatus.HALF), mark(day(1), DayStatus.ABSENT),
        )
        val bill = compute(provider(BillingMode.MONTHLY_FIXED, Schedule.WEEKDAYS), listOf(rate(longAgo, 210_000)), marks)
        assertEquals(195_000L, bill.totalPaise)
    }

    // Salary 300000 until Nov 15, 600000 from Nov 16; all 30 days scheduled.
    // Gross = 300000 × 15/30 + 600000 × 15/30 = 150000 + 300000 = 450000.
    // Absent Nov 20 (no leaves) at the new rate: 600000/30 = 20000 → 430000.
    @Test fun monthlyFixed_midMonthRateChangeProrates() {
        val rates = listOf(rate(longAgo, 300_000), rate(day(16), 600_000))
        val p = provider(BillingMode.MONTHLY_FIXED, Schedule.ALL_DAYS)
        assertEquals(450_000L, compute(p, rates).totalPaise)
        assertEquals(430_000L, compute(p, rates, listOf(mark(day(20), DayStatus.ABSENT))).totalPaise)
    }

    // Provider starts Nov 21: 10 of 30 days → 300000 × 10/30 = 100000.
    @Test fun monthlyFixed_startMidMonthProrates() {
        val p = provider(BillingMode.MONTHLY_FIXED, Schedule.ALL_DAYS, start = day(21))
        assertEquals(100_000L, compute(p, listOf(rate(day(21), 300_000))).totalPaise)
    }

    // On-demand monthly provider has no scheduled days: bills 0, no divide-by-zero.
    @Test fun monthlyFixed_onDemandBillsNothing() {
        val bill = compute(provider(BillingMode.MONTHLY_FIXED, 0), listOf(rate(longAgo, 300_000)))
        assertEquals(0L, bill.totalPaise)
    }

    // ---- Balances ----

    // Oct settlement billed 100000, paid 60000 → 40000 short. Sep fully paid (ignored).
    // Dec short-paid (later month, ignored). Nov total 150000 → due 190000.
    @Test fun previousBalance_fromShortPaidEarlierMonths() {
        val s = listOf(
            MonthSettlementEntity(1, 202610, 100_000, 60_000, 0),
            MonthSettlementEntity(1, 202609, 80_000, 80_000, 0),
            MonthSettlementEntity(1, 202612, 50_000, 0, 0),
        )
        val bill = compute(provider(BillingMode.PER_UNIT, Schedule.ALL_DAYS), listOf(rate(longAgo, 5000)), settlements = s)
        assertEquals(150_000L, bill.totalPaise)
        assertEquals(40_000L, bill.previousBalancePaise)
        assertEquals(190_000L, bill.duePaise)
        assertEquals(1_900L, bill.dueDisplayRupees)
        assertTrue(bill.lines.any { it.kind == BillLineKind.PREVIOUS_BALANCE && it.amountPaise == 40_000L })
    }
}
