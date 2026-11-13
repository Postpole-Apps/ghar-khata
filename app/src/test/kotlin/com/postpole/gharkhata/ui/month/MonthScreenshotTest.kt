package com.postpole.gharkhata.ui.month

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.domain.YearSummaryBuilder
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheetContent
import com.postpole.gharkhata.ui.pro.ProSheetState
import com.postpole.gharkhata.ui.provider.RecordingAnalytics
import com.postpole.gharkhata.ui.provider.TestDb
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

/**
 * Roborazzi renders of the G6 and G7 screens (360x800 dp), to compare with design/mockups/04..06.
 * Writes only in record mode:
 *
 *   ./gradlew recordRoborazziDebug --tests "*MonthScreenshotTest"
 *
 * Output goes to design/renders/g6/ and design/renders/g7/. Today is pinned to 12 Nov 2026. Sheets and dialogs
 * live in their own windows, so their bodies are rendered over a dimmed Month screen instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class MonthScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val today = day(2026, 11, 12)

    private val milk = ProviderEntity(
        id = 1, name = "Raju doodhwala", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        defaultQty = 1.0, scheduleMask = Schedule.ALL_DAYS, startEpochDay = day(2026, 10, 1), phone = "98765 43210", createdAt = 0,
    )
    private val milkRate = RateChangeEntity(providerId = 1, effectiveFromEpochDay = day(2026, 10, 1), amountPaise = 6000)
    private val milkMarks = listOf(
        DayMarkEntity(1, day(2026, 11, 5), DayStatus.ABSENT, updatedAt = 0),
        DayMarkEntity(1, day(2026, 11, 8), DayStatus.PRESENT, qty = 2.0, updatedAt = 0),
        DayMarkEntity(1, day(2026, 11, 10), DayStatus.PRESENT, qty = 1.5, note = "Guests at home", updatedAt = 0),
    )

    /** October billed ₹1,860, ₹1,760 paid: the ₹100 carried into November, as in the mockup. */
    private val octSettlement = MonthSettlementEntity(1, 202610, billedPaise = 186_000, paidPaise = 176_000, paidOnEpochDay = day(2026, 11, 2))

    private fun monthState(settled: Boolean = false): MonthState {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            lateinit var state: MonthState
            runTest {
                val db = TestDb()
                db.providers.upsert(milk)
                db.rates.insert(milkRate)
                milkMarks.forEach { db.marks.upsert(it) }
                db.settlements.upsert(octSettlement)
                if (settled) db.settlements.upsert(MonthSettlementEntity(1, 202611, 85_000, 85_000, today, note = "cash"))
                val vm = ProviderMonthViewModel(
                    SavedStateHandle(mapOf<String, Any>("id" to 1L, "yyyymm" to 202611)), db.repository, RecordingAnalytics(), { today },
                )
                backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
                state = vm.state.value
            }
            return state
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Composable
    private fun Month(state: MonthState) = ProviderMonthContent(
        state = state, onBack = {}, onEdit = {}, onPrev = {}, onNext = {}, onCellClick = {}, onBill = {}, onMarkPaid = {},
    )

    private fun themed(dark: Boolean, content: @Composable () -> Unit) =
        compose.setContent { GharKhataTheme(darkTheme = dark, content = content) }

    @Composable
    private fun Dimmed(handle: Boolean = true, behind: @Composable () -> Unit, sheet: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            behind()
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(GharShapesExtra.SheetTop)
                    .background(MaterialTheme.colorScheme.surface).navigationBarsPadding(),
            ) {
                androidx.compose.foundation.layout.Column {
                    if (handle) Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)
                            .size(width = 32.dp, height = 4.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    sheet()
                }
            }
        }
    }

    // ---- G6 -------------------------------------------------------------------------------------

    @Test fun providerMonthLight() {
        val state = monthState()
        themed(false) { Month(state) }
        compose.onRoot().captureRoboImage("../design/renders/g6/04-provider-month.png")
    }

    @Test fun providerMonthDark() {
        val state = monthState()
        themed(true) { Month(state) }
        compose.onRoot().captureRoboImage("../design/renders/g6/04c-provider-month-dark.png")
    }

    @Test fun daySheet() {
        val state = monthState()
        themed(false) {
            Dimmed(behind = { Month(state) }) {
                DayMarkSheetContent(
                    provider = milk, epochDay = day(2026, 11, 10), mark = milkMarks[2], onSave = { _, _, _ -> }, onReset = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g6/04b-day-sheet.png")
    }

    @Test fun billPreview() {
        val state = monthState()
        themed(false) {
            BillPreviewContent(
                state = state, isPro = false, onBack = {}, onShareImage = {}, onSharePdf = {},
                onShareText = {}, onShareWhatsApp = {}, onMarkPaid = {},
            )
        }
        compose.onRoot().captureRoboImage("../design/renders/g6/05-bill-preview.png")
    }

    @Test fun markPaidDialog() {
        val state = monthState()
        themed(false) {
            Month(state)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            MarkPaidDialog(
                providerName = "Raju doodhwala", yearMonth = state.yearMonthValue, duePaise = state.bill!!.duePaise,
                todayEpochDay = today, initialPaidOn = today, initialPaidPaise = null, initialNote = null,
                onDismiss = {}, onSave = { _, _, _ -> }, inline = true,
            )
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g6/05b-mark-paid.png")
    }

    // ---- G7 -------------------------------------------------------------------------------------

    @Test fun proBillImageCard() {
        val state = monthState(settled = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val data = BillCardBuilder(StringSource { id, args -> context.getString(id, *args) }, Locale.ENGLISH)
            .build(state.provider!!, state.bill!!, state.settlement)
        val bitmap = BillImageRenderer.toBitmap(data)
        themed(false) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2B3A2F)).padding(16.dp), contentAlignment = Alignment.Center) {
                Image(
                    bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(MaterialTheme.shapes.large),
                )
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g7/05c-bill-image-card.png")
    }

    @Test fun yearSummary() {
        val start = day(2026, 4, 1)
        val provider = milk.copy(startEpochDay = start)
        val rates = listOf(milkRate.copy(effectiveFromEpochDay = start))
        val marks = (milkMarks + listOf(
            DayMarkEntity(1, day(2026, 6, 9), DayStatus.ABSENT, updatedAt = 0),
            DayMarkEntity(1, day(2026, 6, 10), DayStatus.ABSENT, updatedAt = 0),
            DayMarkEntity(1, day(2026, 8, 14), DayStatus.ABSENT, updatedAt = 0),
        ))
        // Everything up to September paid in full; October short by ₹100.
        val settlements = (4..9).map { m ->
            val billed = YearSummaryBuilder.build(provider, rates, marks, emptyList(), 2026, today).months.single { it.yearMonth == 202600 + m }
            MonthSettlementEntity(1, 202600 + m, billed.billedPaise, billed.billedPaise, day(2026, m + 1, 2))
        } + octSettlement
        val summary = YearSummaryBuilder.build(provider, rates, marks, settlements, 2026, today)
        themed(false) {
            YearSummaryContent(
                YearState(2026, loaded = true, provider = provider, summary = summary, canGoPrevious = false, canGoNext = false),
                onBack = {}, onPrev = {}, onNext = {},
            )
        }
        compose.onRoot().captureRoboImage("../design/renders/g7/08-year-summary.png")
    }

    @Test fun proSheet() {
        val state = monthState()
        themed(false) {
            Dimmed(handle = false, behind = { Month(state) }) {
                Box(Modifier.heightIn(max = 690.dp)) {
                    ProSheetContent(ProSheetState(price = "₹199"), ProReason.PROVIDER_LIMIT, onBuy = {}, onRestore = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("../design/renders/g7/06-pro-sheet.png")
    }
}
