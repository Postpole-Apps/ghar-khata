package com.postpole.gharkhata.ui.month

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.postpole.gharkhata.domain.MonthSummary
import com.postpole.gharkhata.domain.YearSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class YearTextFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = YearTextFormatter(StringSource { id, args -> context.getString(id, *args) }, Locale.ENGLISH)

    private fun month(m: Int, billed: Long, paid: Long? = null) = MonthSummary(202600 + m, billed, 30, 0, paid)

    @Test fun emptyYear_usesTitleAndEmptyLine() {
        val text = formatter.format("Raju", YearSummary(2026, emptyList(), 0))
        assertEquals("Raju · 2026\n\nNothing billed in this year.\n\nMade with Ghar Khata", text)
    }

    @Test fun oneMonthBilledUnpaid() {
        val text = formatter.format("Raju", YearSummary(2026, listOf(month(1, 87_000)), 87_000))
        assertEquals(
            "Raju · 2026\n\nBilled in 2026: ₹870\nPaid: ₹0\nStill due: ₹870\n\nJanuary: ₹870\n\nMade with Ghar Khata",
            text,
        )
    }

    @Test fun paidMonth_showsPaidAmount() {
        val text = formatter.format("Raju", YearSummary(2026, listOf(month(1, 87_000, 87_000)), 0))
        assertTrue(text.contains("January: ₹870, paid ₹870"))
        assertTrue(text.contains("Still due: ₹0"))
    }

    @Test fun severalMonths_oneLineEachInOrder_outstandingCarriedIn() {
        val months = listOf(month(1, 80_000, 80_000), month(2, 90_000), month(3, 100_000, 50_000))
        val lines = formatter.format("Raju", YearSummary(2026, months, 60_000)).lines()
        assertEquals("Billed in 2026: ₹2,700", lines[2])
        assertEquals("Paid: ₹1,300", lines[3])
        assertEquals("Still due: ₹600", lines[4])
        assertEquals(listOf("January: ₹800, paid ₹800", "February: ₹900", "March: ₹1,000, paid ₹500"), lines.subList(6, 9))
    }

    @Test fun bodyNamesOnlyTheGivenProvider() {
        val text = formatter.format("Raju", YearSummary(2026, listOf(month(1, 87_000)), 87_000))
        assertEquals(1, Regex("Raju").findAll(text).count())
    }
}
