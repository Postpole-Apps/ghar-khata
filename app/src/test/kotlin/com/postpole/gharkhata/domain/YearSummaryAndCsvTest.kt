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

/** Milk 1 L daily at ₹50 from 1 Oct 2026; "today" is 5 Dec 2026. Oct = ₹1,550, Nov = ₹1,500, Dec so far = ₹250. */
class YearSummaryAndCsvTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val today = day(2026, 12, 5)

    private val milk = ProviderEntity(
        id = 1, name = "Raju", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = Schedule.ALL_DAYS, startEpochDay = day(2026, 10, 1), createdAt = 0,
    )
    private val rates = listOf(RateChangeEntity(providerId = 1, effectiveFromEpochDay = day(2026, 10, 1), amountPaise = 5000))

    @Test fun year_listsMonthsFromStartToToday() {
        val s = YearSummaryBuilder.build(milk, rates, emptyList(), emptyList(), 2026, today)
        assertEquals(listOf(202610, 202611, 202612), s.months.map { it.yearMonth })
        assertEquals(listOf(155_000L, 150_000L, 25_000L), s.months.map { it.billedPaise })
        assertEquals(330_000L, s.totalBilledPaise)
        assertEquals(31 + 30 + 5, s.totalDeliveredDays)
        assertEquals(330_000L, s.outstandingPaise)
    }

    @Test fun year_absenceAndSettlement() {
        val marks = listOf(DayMarkEntity(1, day(2026, 11, 3), DayStatus.ABSENT, updatedAt = 0))
        val settled = listOf(MonthSettlementEntity(1, 202610, billedPaise = 155_000, paidPaise = 150_000, paidOnEpochDay = day(2026, 11, 2)))
        val s = YearSummaryBuilder.build(milk, rates, marks, settled, 2026, today)
        val nov = s.months.single { it.yearMonth == 202611 }
        assertEquals(145_000L, nov.billedPaise)
        assertEquals(29, nov.deliveredDays)
        assertEquals(1, nov.absentDays)
        assertEquals(150_000L, s.totalPaidPaise)
        // ₹50 short in Oct carries on: 50 + 1450 + 250 still due at the end of December.
        assertEquals(5_000L + 145_000L + 25_000L, s.outstandingPaise)
    }

    @Test fun year_outsideProviderLifetimeIsEmpty() {
        assertTrue(YearSummaryBuilder.build(milk, rates, emptyList(), emptyList(), 2025, today).months.isEmpty())
        assertTrue(YearSummaryBuilder.build(milk, rates, emptyList(), emptyList(), 2027, today).months.isEmpty())
    }

    @Test fun csv_hasAttendanceAndBillRows() {
        val marks = listOf(
            DayMarkEntity(1, day(2026, 12, 2), DayStatus.ABSENT, note = "away, \"trip\"", updatedAt = 0),
            DayMarkEntity(1, day(2026, 12, 3), DayStatus.PRESENT, qty = 1.5, updatedAt = 0),
        )
        val settled = listOf(MonthSettlementEntity(1, 202610, 155_000, 150_000, day(2026, 11, 2), note = "cash"))
        val lines = AttendanceCsv.build(listOf(milk), rates, marks, settled, today).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(AttendanceCsv.HEADER, lines.first())
        val attendance = lines.filter { it.startsWith("attendance,") }
        assertEquals(66, attendance.size) // 1 Oct .. 5 Dec 2026
        assertTrue(attendance.contains("attendance,Raju,2026-12,2026-12-02,absent,,,,,,,\"away, \"\"trip\"\"\""))
        assertTrue(attendance.contains("attendance,Raju,2026-12,2026-12-03,present,1.5,,,,,,"))
        assertTrue(attendance.contains("attendance,Raju,2026-10,2026-10-01,present,1,,,,,,"))
        val bills = lines.filter { it.startsWith("bill,") }
        assertEquals(
            listOf(
                "bill,Raju,2026-10,,,,1550.00,0.00,1550.00,1500.00,2026-11-02,cash",
                "bill,Raju,2026-11,,,,1500.00,50.00,1550.00,,,",
                "bill,Raju,2026-12,,,,225.00,1550.00,1775.00,,,",
            ),
            bills,
        )
    }

    @Test fun csv_escapesFormulaAndQuotes() {
        assertEquals("'=SUM(A1)", AttendanceCsv.escape("=SUM(A1)"))
        assertEquals("\"a,b\"", AttendanceCsv.escape("a,b"))
        assertEquals("-12.50", AttendanceCsv.rupees(-1250))
    }
}
