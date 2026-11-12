package com.postpole.gharkhata.ui.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
 * Roborazzi screenshots (JVM, no device) of the G4 screens, light theme, 360x800 dp, to compare with
 * design/mockups. `captureRoboImage` only writes when Roborazzi is run in record mode, so a normal
 * `testDebugUnitTest` does not touch the repo. To re-record:
 *
 *   ./gradlew recordRoborazziDebug --tests "*ProviderScreenshotTest"
 *
 * Output goes to design/renders/g4/. The date is pinned so the images are reproducible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ProviderScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)

    private fun provider(
        id: Long,
        name: String,
        type: ProviderType,
        mode: BillingMode,
        unit: String,
        mask: Int,
        archivedOn: LocalDate? = null,
    ) = ProviderEntity(
        id = id,
        name = name,
        type = type,
        billingMode = mode,
        unitLabel = unit,
        scheduleMask = mask,
        startEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
        archived = archivedOn != null,
        archivedOnEpochDay = archivedOn?.toEpochDay(),
        createdAt = 0,
    )

    private fun mark(p: ProviderEntity, date: LocalDate, status: DayStatus) =
        DayMarkEntity(providerId = p.id, epochDay = date.toEpochDay(), status = status, updatedAt = 0)

    private fun row(p: ProviderEntity, total: Long, rate: Long, marks: List<DayMarkEntity> = emptyList()) =
        ProviderRow(p, total, rate, ProviderListViewModel.strip(p, marks, today.toEpochDay()))

    @Test
    fun providersListFreeLimitReached() {
        val milk = provider(1, "Raju doodhwala", ProviderType.MILK, BillingMode.PER_UNIT, "L", Schedule.ALL_DAYS)
        val maid = provider(
            2, "Sunita didi", ProviderType.MAID, BillingMode.MONTHLY_FIXED, "day",
            Schedule.ALL_DAYS and Schedule.SUN.inv(),
        )
        val paper = provider(3, "Hindustan Times", ProviderType.NEWSPAPER, BillingMode.PER_UNIT, "copy", Schedule.ALL_DAYS)
        val water = provider(
            4, "Bisleri can", ProviderType.WATER_CAN, BillingMode.PER_UNIT, "can", 0,
            archivedOn = LocalDate.of(2026, 9, 30),
        )
        val state = ProviderListState(
            active = listOf(
                row(
                    milk, 75_000, 6_000,
                    listOf(mark(milk, today.minusDays(8), DayStatus.ABSENT), mark(milk, today.minusDays(4), DayStatus.HALF)),
                ),
                row(maid, 300_000, 300_000, listOf(mark(maid, today.minusDays(6), DayStatus.ABSENT))),
                row(paper, 8_400, 700),
            ),
            archived = listOf(water),
            isPro = false,
        )
        compose.setContent {
            GharKhataTheme(darkTheme = false) {
                ProviderListContent(state, onOpenProvider = {}, onAddProvider = {}, onSeePro = {}, today = today)
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g4/02-providers-free-limit.png")
    }

    private val milkForm = ProviderForm(
        type = ProviderType.MILK,
        name = "Raju doodhwala",
        billingMode = BillingMode.PER_UNIT,
        unitLabel = "L",
        defaultQty = "1",
        rate = "60",
        scheduleMask = Schedule.ALL_DAYS,
        startEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
        phone = "98765 43210",
    )

    @Composable
    private fun EditForm(form: ProviderForm, isNew: Boolean) {
        ProviderEditContent(
            form = form,
            errors = emptySet(),
            isNew = isNew,
            archived = false,
            onUpdate = {},
            onSelectType = {},
            onToggleDay = {},
            onSave = {},
            onClose = {},
            onPickStart = {},
            onArchive = {},
            onDelete = {},
        )
    }

    @Test
    fun addProviderMilkPreset() {
        compose.setContent { GharKhataTheme(darkTheme = false) { EditForm(milkForm, isNew = true) } }
        compose.onRoot().captureRoboImage("../design/renders/g4/03-add-provider-milk.png")
    }

    @Test
    fun editRateChangeSheet() {
        val maidForm = milkForm.copy(
            type = ProviderType.MAID,
            name = "Sunita didi",
            billingMode = BillingMode.MONTHLY_FIXED,
            unitLabel = "day",
            rate = "3500",
            scheduleMask = Schedule.ALL_DAYS and Schedule.SUN.inv(),
            paidLeaves = "2",
            phone = "",
            startEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
        )
        compose.setContent {
            GharKhataTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    EditForm(maidForm, isNew = false)
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RateChangeSurface(
                            monthly = true,
                            providerName = "Sunita didi",
                            oldRatePaise = 300_000,
                            newRatePaise = 350_000,
                            effectiveFromEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                            onPickDate = {},
                            onCancel = {},
                            onSave = {},
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g4/03b-edit-rate-change.png")
    }
}
