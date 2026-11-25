package com.postpole.gharkhata.ui.today

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.DueProvider
import com.postpole.gharkhata.domain.PrevBillsCue
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class TodayPrevBillsChipTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 12, 3).toEpochDay()
    private val nov = YearMonth.of(2026, 11)
    private val milk = ProviderEntity(
        id = 7, name = "Raju", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = Schedule.ALL_DAYS, startEpochDay = 0, createdAt = 0,
    )

    private val bills = mutableListOf<String>()
    private val months = mutableListOf<Pair<Long, Int>>()

    private fun show(cue: PrevBillsCue?) {
        Locale.setDefault(Locale.ENGLISH)
        compose.setContent {
            GharKhataTheme(dynamicColor = false) {
                TodayContent(
                    state = TodayState(day, day, rows = listOf(TodayRow(milk, null, true)), activeCount = 1, prevBills = cue),
                    onSelectDate = {}, onCycle = {}, onSetQty = { _, _, _ -> }, onReset = {}, onAddExtra = {},
                    onAddProvider = {}, onPreset = {}, onOpenPro = {},
                    onOpenBills = { bills += "bills" },
                    onOpenProviderMonth = { id, ym -> months += id to ym },
                )
            }
        }
    }

    @Test
    fun noCue_showsNextBillsChip() {
        show(null)
        compose.onNodeWithText("Next bills · 1 Jan").assertHasClickAction()
    }

    @Test
    fun singleProvider_replacesNextChip_andOpensMonth() {
        show(PrevBillsCue(nov, listOf(DueProvider(7, "Raju"))))
        compose.onNodeWithText("Next bills · 1 Jan").assertDoesNotExist()
        compose.onNodeWithText("Raju · Nov bill is ready").assertExists()
        compose.onNodeWithContentDescription("Open Raju monthly register for Nov").assertHasClickAction().performClick()
        assertEquals(listOf(7L to 202611), months)
        assertEquals(0, bills.size)
    }

    @Test
    fun manyProviders_showsCount_andOpensProviders() {
        show(PrevBillsCue(nov, listOf(DueProvider(7, "Raju"), DueProvider(8, "Sunita"))))
        compose.onNodeWithText("2 bills from Nov are ready").assertExists()
        compose.onNodeWithContentDescription("Open Providers to see 2 bills from Nov").performClick()
        assertEquals(listOf("bills"), bills)
        assertEquals(0, months.size)
    }
}
