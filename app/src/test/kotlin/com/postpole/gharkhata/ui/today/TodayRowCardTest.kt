package com.postpole.gharkhata.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class TodayRowCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingCard_opensProvider_notCycle() {
        val milk = ProviderEntity(
            id = 7, name = "Raju", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
            scheduleMask = Schedule.ALL_DAYS, startEpochDay = 0, createdAt = 0,
        )
        var opened = 0
        var cycled = 0
        compose.setContent {
            GharKhataTheme(dynamicColor = false) {
                TodayRowCard(TodayRow(milk, null, true), style = 2, onCycle = { cycled++ }, onEditQty = {}, onOpen = { opened++ })
            }
        }
        compose.onNodeWithContentDescription("Open Raju monthly register").performClick()
        assertEquals(1, opened)
        assertEquals(0, cycled)
    }
}
