package com.postpole.gharkhata.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.ThemeMode
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.provider.ProviderListContent
import com.postpole.gharkhata.ui.provider.ProviderListState
import com.postpole.gharkhata.ui.provider.ProviderListViewModel
import com.postpole.gharkhata.ui.provider.ProviderRow
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import com.postpole.gharkhata.ui.today.TodayContent
import com.postpole.gharkhata.ui.today.TodayRow
import com.postpole.gharkhata.ui.today.TodayState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Roborazzi renders for G9: Settings (free and Pro, light and dark) to compare with
 * design/mockups/07-settings.png, plus a very long provider name on the Providers list and Today.
 * Only written in record mode:
 *
 *   ./gradlew recordRoborazziDebug --tests "*SettingsScreenshotTest"
 *
 * Output goes to design/renders/g9/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun settings(isPro: Boolean, dark: Boolean, file: String) {
        compose.setContent {
            GharKhataTheme(darkTheme = dark, dynamicColor = false) {
                SettingsContent(
                    isPro = isPro,
                    themeMode = ThemeMode.SYSTEM,
                    dynamicColor = false,
                    currency = "INR",
                    reminderMinutes = if (isPro) 8 * 60 else null,
                )
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g9/$file")
    }

    @Test fun settingsFreeLight() = settings(isPro = false, dark = false, file = "07-settings-free-light.png")

    @Test fun settingsFreeDark() = settings(isPro = false, dark = true, file = "07-settings-free-dark.png")

    @Test fun settingsProLight() = settings(isPro = true, dark = false, file = "07-settings-pro-light.png")

    @Test fun settingsProDark() = settings(isPro = true, dark = true, file = "07-settings-pro-dark.png")

    private val today = LocalDate.of(2026, 11, 12)
    private val longName = "Shri Ram Lal Gupta Doodh Dairy and General Stores, Sector 21 Market Road"

    private fun provider(id: Long, name: String, archived: Boolean = false) = ProviderEntity(
        id = id, name = name, type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        scheduleMask = Schedule.ALL_DAYS, startEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
        archived = archived, archivedOnEpochDay = if (archived) LocalDate.of(2026, 9, 30).toEpochDay() else null,
        createdAt = 0,
    )

    @Test
    fun providersListLongName() {
        val long = provider(1, longName)
        val short = provider(2, "Raju doodhwala")
        val state = ProviderListState(
            active = listOf(
                ProviderRow(long, 75_000, 6_000, ProviderListViewModel.strip(long, emptyList(), today.toEpochDay())),
                ProviderRow(short, 60_000, 6_000, ProviderListViewModel.strip(short, emptyList(), today.toEpochDay())),
            ),
            archived = listOf(provider(3, longName, archived = true)),
            isPro = true,
        )
        compose.setContent {
            GharKhataTheme(darkTheme = false, dynamicColor = false) {
                ProviderListContent(state, onOpenProvider = {}, onAddProvider = {}, onSeePro = {}, today = today)
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g9/02b-providers-long-name.png")
    }

    @Test
    fun todayLongName() {
        val long = provider(1, longName)
        val day = today.toEpochDay()
        val state = TodayState(
            epochDay = day,
            todayEpochDay = day,
            rows = listOf(
                TodayRow(long, null, true),
                TodayRow(provider(2, "Raju doodhwala"), DayMarkEntity(2, day, DayStatus.HALF, updatedAt = 0), true),
            ),
            activeCount = 2,
        )
        compose.setContent {
            GharKhataTheme(darkTheme = false, dynamicColor = false) {
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
        compose.onRoot().captureRoboImage("../design/renders/g9/01d-today-long-name.png")
    }
}
