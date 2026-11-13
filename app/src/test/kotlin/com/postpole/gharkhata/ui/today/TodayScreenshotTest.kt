package com.postpole.gharkhata.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Roborazzi screenshots of Today (light, dark, empty), 360x800 dp, to compare with design/mockups/01-today*.
 * Writes only in record mode:
 *
 *   ./gradlew recordRoborazziDebug --tests "*TodayScreenshotTest"
 *
 * Output goes to design/renders/g5/. The date is pinned so the images are reproducible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class TodayScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 12).toEpochDay()

    private fun provider(id: Long, name: String, type: ProviderType, mode: BillingMode, unit: String, mask: Int) =
        ProviderEntity(
            id = id, name = name, type = type, billingMode = mode, unitLabel = unit, scheduleMask = mask,
            startEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(), createdAt = 0,
        )

    private val milk = provider(1, "Raju doodhwala", ProviderType.MILK, BillingMode.PER_UNIT, "L", Schedule.ALL_DAYS)
    private val maid = provider(
        2, "Sunita didi", ProviderType.MAID, BillingMode.MONTHLY_FIXED, "day", Schedule.ALL_DAYS and Schedule.SUN.inv(),
    )
    private val paper = provider(3, "Hindustan Times", ProviderType.NEWSPAPER, BillingMode.PER_UNIT, "copy", Schedule.ALL_DAYS)

    private val filled = TodayState(
        epochDay = day,
        todayEpochDay = day,
        rows = listOf(
            TodayRow(milk, null, true),
            TodayRow(maid, DayMarkEntity(2, day, DayStatus.HALF, updatedAt = 0), true),
            TodayRow(paper, null, true),
        ),
        activeCount = 3,
    )

    private fun capture(state: TodayState, dark: Boolean, file: String) {
        compose.setContent {
            GharKhataTheme(darkTheme = dark) {
                TodayContent(
                    state = state,
                    onSelectDate = {},
                    onCycle = {},
                    onSetQty = { _, _, _ -> },
                    onReset = {},
                    onAddExtra = {},
                    onAddProvider = {},
                    onPreset = {},
                    onOpenPro = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g5/$file")
    }

    @Test
    fun todayLight() = capture(filled, dark = false, file = "01-today.png")

    @Test
    fun todayDark() = capture(filled, dark = true, file = "01c-today-dark.png")

    @Test
    fun todayEmpty() = capture(TodayState(epochDay = day, todayEpochDay = day), dark = false, file = "01b-today-empty.png")
}
