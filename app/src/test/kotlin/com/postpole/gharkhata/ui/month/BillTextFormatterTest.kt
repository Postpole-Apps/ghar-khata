package com.postpole.gharkhata.ui.month

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.domain.BillCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class BillTextFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = BillTextFormatter(StringSource { id, args -> context.getString(id, *args) }, Locale.ENGLISH)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    private val milk = ProviderEntity(
        id = 1, name = "Raju doodhwala", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = 0x7F, startEpochDay = day(1, 1), createdAt = 0,
    )
    private val rates = listOf(
        RateChangeEntity(providerId = 1, effectiveFromEpochDay = day(1, 1), amountPaise = 5800),
        RateChangeEntity(providerId = 1, effectiveFromEpochDay = day(10, 16), amountPaise = 6000),
    )
    private val marks = listOf(
        DayMarkEntity(1, day(10, 4), DayStatus.ABSENT, updatedAt = 0),
        DayMarkEntity(1, day(10, 20), DayStatus.ABSENT, updatedAt = 0),
    )

    // Every month before October is settled in full, so nothing is carried in.
    private val settled = (1..9).map { MonthSettlementEntity(1, 202600 + it, 100_000, 100_000, 0) }

    private fun bill(vararg extra: MonthSettlementEntity) =
        BillCalculator.compute(milk, rates, marks, 202610, day(11, 5), settled + extra)

    @Test fun plainTextBill_hasNameMonthLineItemsTotalAndDue() {
        val text = formatter.format(milk, bill())
        // Oct 1-15: 15 days, 1 absent → 14 × ₹58 = 812; Oct 16-31: 16 days, 1 absent → 15 × ₹60 = 900.
        assertEquals(
            """
            Raju doodhwala · October 2026

            1–15 Oct: 14 L × ₹58 = ₹812
            16–31 Oct: 15 L × ₹60 = ₹900
            Absences: 2

            October total: ₹1,712
            Total due: ₹1,712

            Made with Ghar Khata
            """.trimIndent(),
            text,
        )
    }

    @Test fun paidStamp_andPreviousBalance() {
        val s = MonthSettlementEntity(1, 202610, 171_200, 150_000, day(11, 2))
        val withPrev = BillCalculator.compute(
            milk, rates, marks, 202610, day(11, 5),
            (1..8).map { MonthSettlementEntity(1, 202600 + it, 0, 0, 0) } + MonthSettlementEntity(1, 202609, 20_000, 10_000, 0) + s,
        )
        val text = formatter.format(milk, withPrev, s)
        assertTrue(text, text.contains("Previous balance: ₹100"))
        assertTrue(text, text.contains("Total due: ₹1,812"))
        assertTrue(text, text.contains("Paid on 2 Nov 2026: ₹1,500"))
        assertTrue(text, text.contains("Balance carried forward: ₹212"))
    }

    @Test fun formatIsStable() {
        assertEquals(formatter.format(milk, bill()), formatter.format(milk, bill()))
    }

    @Test fun whatsAppNumbers() {
        assertEquals("919876543210", BillTextFormatter.whatsAppNumber("98765 43210"))
        assertEquals("919876543210", BillTextFormatter.whatsAppNumber("+91 98765-43210"))
        assertEquals("919876543210", BillTextFormatter.whatsAppNumber("09876543210"))
        assertEquals("919876543210", BillTextFormatter.whatsAppNumber("0091 9876543210"))
        assertEquals("14155550123", BillTextFormatter.whatsAppNumber("+1 (415) 555-0123"))
        assertNull(BillTextFormatter.whatsAppNumber("12345"))
        assertNull(BillTextFormatter.whatsAppNumber(null))
        assertNull(BillTextFormatter.whatsAppNumber(""))
    }

    @Test fun whatsAppUrl_encodesText() {
        val url = BillTextFormatter.whatsAppUrl("9876543210", "Total ₹1,712\nBye")!!
        assertTrue(url, url.startsWith("https://wa.me/919876543210?text="))
        assertTrue(url, url.contains("%0A"))
        assertTrue(url, !url.contains(" ") && !url.contains("+"))
        assertNull(BillTextFormatter.whatsAppUrl("abc", "x"))
    }

    @Test fun rangeLabels() {
        assertEquals("1–15 Oct", BillTextFormatter.rangeLabel(day(10, 1), day(10, 15), Locale.ENGLISH))
        assertEquals("5 Oct", BillTextFormatter.rangeLabel(day(10, 5), day(10, 5), Locale.ENGLISH))
        assertEquals("28 Oct – 3 Nov", BillTextFormatter.rangeLabel(day(10, 28), day(11, 3), Locale.ENGLISH))
    }

    @Test fun plainAmountRoundTrips() {
        assertEquals("1700", plainAmount(170_000))
        assertEquals("1700.5", plainAmount(170_050))
        assertEquals("1700.05", plainAmount(170_005))
    }
}
