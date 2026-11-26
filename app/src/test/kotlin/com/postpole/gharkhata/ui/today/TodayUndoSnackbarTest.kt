package com.postpole.gharkhata.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class TodayUndoSnackbarTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 12, 3).toEpochDay()
    private val maid = ProviderEntity(
        id = 7, name = "Sunita didi", type = ProviderType.MAID, billingMode = BillingMode.MONTHLY_FIXED, unitLabel = "day",
        scheduleMask = Schedule.ALL_DAYS, startEpochDay = 0, createdAt = 0,
    )
    private val calls = mutableListOf<String>()

    private fun show(name: String = maid.name, fontScale: Float = 1f) {
        Locale.setDefault(Locale.ENGLISH)
        val undo = UndoableMark(maid.id, name, day, previous = null, newStatus = DayStatus.ABSENT)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GharKhataTheme(dynamicColor = false) {
                    TodayContent(
                        state = TodayState(day, day, rows = listOf(TodayRow(maid.copy(name = name), null, true)), activeCount = 1),
                        onSelectDate = {}, onCycle = {}, onSetQty = { _, _, _ -> }, onReset = {}, onAddExtra = {},
                        onAddProvider = {}, onPreset = {}, onOpenPro = {},
                        pendingUndo = undo,
                        onUndo = { calls += "undo" },
                        onClearUndo = { calls += "clear" },
                    )
                }
            }
        }
    }

    /** Touch bounds include the minimum-interactive-size padding, which is what a finger actually hits. */
    private fun SemanticsNodeInteraction.assertTouchHeightIsAtLeast(min: androidx.compose.ui.unit.Dp) {
        val node = fetchSemanticsNode()
        val heightDp = node.touchBoundsInRoot.height / node.layoutInfo.density.density
        assertTrue("touch height $heightDp dp < $min", heightDp >= min.value - 0.01f)
    }

    @Test
    fun snackbar_showsStatusFirst_andUndoCallsCallback() {
        show()
        compose.onNodeWithText("Absent · Sunita didi").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(listOf("undo"), calls)
    }

    @Test
    fun snackbar_undoAndDismiss_haveAtLeast48dpTouchTargets() {
        show()
        compose.onNodeWithText("Undo").assertTouchHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Dismiss").assertTouchHeightIsAtLeast(48.dp)
    }

    @Test
    fun snackbar_fontScale2_messageWrapsAndActionVisible() {
        show(name = "Sunita didi from the second floor flat, the one with the blue gate near the park", fontScale = 2f)
        compose.onNodeWithText("Undo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
        compose.onNodeWithText("Absent", substring = true).assertIsDisplayed()
    }
}
