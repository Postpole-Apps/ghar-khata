package com.postpole.gharkhata.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class BackupNudgeCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 12, 3).toEpochDay()
    private val milk = ProviderEntity(
        id = 7, name = "Raju", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = Schedule.ALL_DAYS, startEpochDay = 0, createdAt = 0,
    )
    private val calls = mutableListOf<String>()

    private fun show(nudge: Boolean) {
        Locale.setDefault(Locale.ENGLISH)
        compose.setContent {
            GharKhataTheme(dynamicColor = false) {
                TodayContent(
                    state = TodayState(day, day, rows = listOf(TodayRow(milk, null, true)), activeCount = 1, showBackupNudge = nudge),
                    onSelectDate = {}, onCycle = {}, onSetQty = { _, _, _ -> }, onReset = {}, onAddExtra = {},
                    onAddProvider = {}, onPreset = {}, onOpenPro = {},
                    onOpenSettings = { calls += "settings" },
                    onDismissBackupNudge = { calls += "dismiss" },
                )
            }
        }
    }

    @Test
    fun hiddenWhenNotRequested() {
        show(false)
        compose.onNodeWithText("Back up now").assertDoesNotExist()
    }

    @Test
    fun actionsFireCallbacks() {
        show(true)
        compose.onNodeWithText("Back up now").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(listOf("settings", "dismiss"), calls)
    }
}
