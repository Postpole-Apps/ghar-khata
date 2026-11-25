package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.ProviderWithRates
import com.postpole.gharkhata.data.RateChangeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Monthly-fixed providers at ₹3000/month, so every month's bill is a known 300000 paise. */
class PreviousMonthBillsTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val dec3 = day(2026, 12, 3)

    private fun input(
        id: Long,
        name: String,
        start: Long = day(2026, 10, 1),
        settlements: List<MonthSettlementEntity> = emptyList(),
    ): ProviderBillInput {
        val p = ProviderEntity(
            id = id, name = name, type = ProviderType.MAID, billingMode = BillingMode.MONTHLY_FIXED, unitLabel = "day",
            scheduleMask = Schedule.ALL_DAYS, startEpochDay = start, createdAt = 0,
        )
        val rates = listOf(RateChangeEntity(providerId = id, effectiveFromEpochDay = start, amountPaise = 300_000))
        return ProviderBillInput(ProviderWithRates(p, rates), emptyList(), settlements)
    }

    private fun settled(id: Long, ym: Int, billed: Long, paid: Long) =
        MonthSettlementEntity(id, ym, billed, paid, paidOnEpochDay = 0)

    @Test
    fun dueProvider_yieldsCueForPreviousMonth() {
        val cue = PreviousMonthBills.compute(dec3, listOf(input(1, "Sunita")))!!
        assertEquals(YearMonth.of(2026, 11), cue.previousMonth)
        assertEquals(202611, cue.previousYearMonth)
        assertEquals(listOf(DueProvider(1, "Sunita")), cue.providers)
    }

    @Test
    fun fullySettledPreviousMonths_noCue() {
        val s = listOf(settled(1, 202610, 300_000, 300_000), settled(1, 202611, 300_000, 300_000))
        assertNull(PreviousMonthBills.compute(dec3, listOf(input(1, "Sunita", settlements = s))))
    }

    @Test
    fun creditProvider_isSkipped() {
        val s = listOf(settled(1, 202610, 300_000, 300_000), settled(1, 202611, 300_000, 500_000))
        assertNull(PreviousMonthBills.compute(dec3, listOf(input(1, "Sunita", settlements = s))))
    }

    @Test
    fun partialPayment_stillDue() {
        val s = listOf(settled(1, 202610, 300_000, 300_000), settled(1, 202611, 300_000, 100_000))
        assertEquals(1, PreviousMonthBills.compute(dec3, listOf(input(1, "Sunita", settlements = s)))!!.providers.size)
    }

    @Test
    fun startedThisMonth_hasNoCarry() {
        assertNull(PreviousMonthBills.compute(dec3, listOf(input(1, "New", start = day(2026, 12, 1)))))
    }

    @Test
    fun windowIsDays1to5Only() {
        val inputs = listOf(input(1, "Sunita"))
        assertEquals(202611, PreviousMonthBills.compute(day(2026, 12, 1), inputs)!!.previousYearMonth)
        assertEquals(202611, PreviousMonthBills.compute(day(2026, 12, 5), inputs)!!.previousYearMonth)
        assertNull(PreviousMonthBills.compute(day(2026, 12, 6), inputs))
        assertNull(PreviousMonthBills.compute(day(2026, 11, 30), inputs))
    }

    @Test
    fun januaryLooksBackToDecember() {
        val cue = PreviousMonthBills.compute(day(2027, 1, 2), listOf(input(1, "Sunita")))!!
        assertEquals(202612, cue.previousYearMonth)
    }

    @Test
    fun manyProviders_onlyDueOnesListed() {
        val settledAll = listOf(settled(3, 202610, 300_000, 300_000), settled(3, 202611, 300_000, 300_000))
        val cue = PreviousMonthBills.compute(
            dec3, listOf(input(1, "A"), input(2, "B"), input(3, "C", settlements = settledAll)),
        )!!
        assertEquals(listOf(1L, 2L), cue.providers.map { it.id })
    }
}
