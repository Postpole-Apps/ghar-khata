package com.postpole.gharkhata.ui.month

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.postpole.gharkhata.data.BillingMode
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
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillImageRendererTest {
    // PDF output (PdfDocument) is native-backed and does not run under Robolectric, so it is checked by hand.
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val builder = BillCardBuilder(StringSource { id, args -> context.getString(id, *args) }, Locale.ENGLISH)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    private val milk = ProviderEntity(
        id = 1, name = "Raju doodhwala", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = 0x7F, startEpochDay = day(10, 1), createdAt = 0,
    )
    private val rates = listOf(RateChangeEntity(providerId = 1, effectiveFromEpochDay = day(10, 1), amountPaise = 6000))
    private val bill = BillCalculator.compute(milk, rates, emptyList(), 202610, day(12, 1), emptyList())

    @Test fun card_hasSegmentTotalsAndPaidStamp() {
        val settled = MonthSettlementEntity(1, 202610, bill.duePaise, bill.duePaise, day(11, 2))
        val data = builder.build(milk, bill, settled)
        assertEquals("Raju doodhwala", data.providerName)
        assertEquals("₹1,860", data.dueText)
        assertEquals("₹1,860", data.lines.single().value)
        assertEquals(listOf("₹1,860", "₹0", "₹1,860"), data.totals.map { it.value })
        assertEquals("Paid on 2 Nov 2026 · ₹1,860", data.paidStamp)
        assertNull(builder.build(milk, bill, null).paidStamp)
    }

    @Test fun bitmap_isDrawnAtTheComputedHeight() {
        val data = builder.build(milk, bill, null)
        val bmp = BillImageRenderer.toBitmap(data, 540)
        assertEquals(540, bmp.width)
        assertEquals(BillImageRenderer.heightFor(data, 540), bmp.height)
        // Hero block is dark green, the area below is the light surface.
        assertTrue(Color.green(bmp.getPixel(10, 40)) < 100)
        assertTrue(Color.green(bmp.getPixel(10, bmp.height - 2)) > 200)
    }
}
