package com.postpole.gharkhata.ui.month

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class MonthCallButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12).toEpochDay()
    private val raju = ProviderEntity(
        id = 1, name = "Raju", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
        defaultQty = 1.0, scheduleMask = Schedule.ALL_DAYS, startEpochDay = today - 40, createdAt = 0,
        phone = "+91 98765-43210",
    )
    private val taps = mutableListOf<String>()
    private val errors = mutableListOf<Exception>()

    private fun show(phone: String?, fontScale: Float = 1f) {
        Locale.setDefault(Locale.ENGLISH)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GharKhataTheme(dynamicColor = false) {
                    ProviderMonthContent(
                        state = MonthState(202611, today, loaded = true, provider = raju.copy(phone = phone)),
                        onBack = {}, onEdit = {}, onPrev = {}, onNext = {}, onCellClick = {}, onBill = {}, onMarkPaid = {},
                        onCallTapped = { taps += "tap" },
                        onDialError = { errors += it },
                    )
                }
            }
        }
    }

    @Test fun callIcon_hiddenWhenNoPhone() {
        show(null)
        compose.onNodeWithContentDescription("Call Raju").assertDoesNotExist()
    }

    @Test fun callIcon_hiddenWhenBlankOrTooShort() {
        show("12")
        compose.onNodeWithContentDescription("Call Raju").assertDoesNotExist()
    }

    @Test fun callIcon_shownWithContentDescription() {
        show(raju.phone)
        compose.onNodeWithContentDescription("Call Raju").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun callIcon_touchTargetAtLeast48dp() {
        show(raju.phone)
        // M3 IconButton lays out at 40 dp and expands its touch bounds to the 48 dp minimum.
        val bounds = compose.onNodeWithContentDescription("Call Raju").fetchSemanticsNode().touchBoundsInRoot
        val density = compose.density
        assertTrue("width ${bounds.width}", bounds.width >= with(density) { 47.5.dp.toPx() })
        assertTrue("height ${bounds.height}", bounds.height >= with(density) { 47.5.dp.toPx() })
    }

    @Test fun callIcon_launchesActionDial() {
        show(raju.phone)
        compose.onNodeWithContentDescription("Call Raju").performClick()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("tel:+919876543210", started.data.toString())
        assertEquals(listOf("tap"), taps)
    }

    @Test fun callIcon_noDialer_showsToastNoCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        show(raju.phone)
        // With activity checks on and no dialer registered, startActivity throws ActivityNotFoundException.
        shadowOf(app).checkActivities(true)
        compose.onNodeWithContentDescription("Call Raju").performClick()
        assertEquals(app.getString(R.string.month_call_no_app), ShadowToast.getTextOfLatestToast())
        assertEquals(listOf("tap"), taps)
        assertTrue("ActivityNotFoundException is expected, so not reported", errors.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun fontScale2_narrowScreen_iconsStayVisible() {
        show(raju.phone, fontScale = 2f)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Raju").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit provider").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
    }
}
