package com.postpole.gharkhata.pro

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.ui.provider.FakeMarkDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ProPromptControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 100 * day
    private val installedAt = 10 * day
    private val marks = FakeMarkDao()
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }
    private val settings = object : ProPromptSettingsSource {
        override val settings: StateFlow<ProPromptSettings> = MutableStateFlow(ProPromptSettings.DEFAULT)
        override fun refresh() = Unit
    }
    private var pro = false
    private var removeAds = false
    private val dataStore = lazy { PreferenceDataStoreFactory.create { File(tmp.root, "p.preferences_pb") } }

    /** A new controller on the same DataStore is a new session (app process). */
    private fun session(): ProPromptController = ProPromptController(
        dataStore = dataStore.value, markDao = marks, source = settings, analytics = analytics,
        price = MutableStateFlow("price"), isPro = { pro }, hasRemoveAds = { removeAds },
        installTimeMs = { installedAt }, clock = { now },
    )

    private suspend fun markDays(n: Int) = repeat(n) {
        marks.upsert(DayMarkEntity(1, it.toLong(), DayStatus.ABSENT, null, null, 0))
    }

    private suspend fun ProPromptController.billSheet() =
        reserve(ProPromptTrigger.BILL_MILESTONE, ProPromptScreen.PROVIDER_MONTH)

    private suspend fun ProPromptController.card(atLimit: Boolean = false) =
        reserve(ProPromptTrigger.MARKED_DAYS_MILESTONE, ProPromptScreen.PROVIDER_LIST, atLimit)

    @Test fun neverInTheFirstSession() = runBlocking {
        markDays(7)
        val s = session()
        repeat(2) { s.recordBillMilestone() }
        assertFalse(s.card())
        assertFalse(s.billSheet())
    }

    @Test fun cardFromSecondSession_oncePerSession() = runBlocking {
        markDays(7)
        session().startSession()
        val s = session()
        s.startSession()
        assertFalse(s.card(atLimit = true))
        assertTrue(s.card())
        assertTrue(s.autoPromptShownThisSession)
        // Second ask in the same session, and the other trigger, are refused.
        assertFalse(s.card())
        repeat(2) { s.recordBillMilestone() }
        assertFalse(s.billSheet())
    }

    @Test fun cardNeedsTheMarkedDaysFromTheDao() = runBlocking {
        markDays(6)
        session().startSession()
        val s = session()
        assertFalse(s.card())
        marks.upsert(DayMarkEntity(1, 99, DayStatus.HALF, null, null, 0))
        assertTrue(s.card())
    }

    @Test fun sheetNeedsThirdSessionTwoMilestonesAndInstallAge() = runBlocking {
        repeat(2) { session().startSession() }
        val s = session() // third
        repeat(2) { s.recordBillMilestone() }
        assertTrue(s.billSheet())
    }

    @Test fun sheetNotInSecondSession() = runBlocking {
        session().startSession()
        val s = session()
        repeat(2) { s.recordBillMilestone() }
        assertFalse(s.billSheet())
    }

    @Test fun freshInstallBlocksTheSheetForTwoDays() = runBlocking {
        repeat(2) { session().startSession() }
        val s = session()
        repeat(2) { s.recordBillMilestone() }
        now = installedAt + day + 1
        assertFalse(s.billSheet())
        now = installedAt + 2 * day
        assertTrue(s.billSheet())
    }

    @Test fun removeAdsOwnerGetsCardOnly() = runBlocking {
        markDays(7)
        removeAds = true
        repeat(2) { session().startSession() }
        val s = session()
        repeat(2) { s.recordBillMilestone() }
        assertFalse(s.billSheet())
        assertTrue(s.card())
    }

    @Test fun proGetsNothing() = runBlocking {
        markDays(7)
        pro = true
        repeat(2) { session().startSession() }
        val s = session()
        repeat(2) { s.recordBillMilestone() }
        assertFalse(s.billSheet())
        assertFalse(s.card())
    }

    @Test fun hardGateSilencesPromptsFor48Hours() = runBlocking {
        markDays(7)
        repeat(2) { session().startSession() }
        val s = session()
        s.recordHardGate()
        now += TimeUnit.HOURS.toMillis(47)
        assertFalse(s.card())
        now += TimeUnit.HOURS.toMillis(1)
        assertTrue(s.card())
    }

    @Test fun shownInterstitialSuppressesPromptsForTheSession() = runBlocking {
        markDays(7)
        repeat(2) { session().startSession() }
        val s = session()
        s.onInterstitialShown()
        assertFalse(s.card())
    }

    @Test fun shownCardStartsItsThreeDayCooldown() = runBlocking {
        markDays(7)
        repeat(2) { session().startSession() }
        session().let { assertTrue(it.card()); it.onShown(ProPromptTrigger.MARKED_DAYS_MILESTONE) }
        now += 2 * day
        assertFalse(session().card())
        now += day
        assertTrue(session().card())
    }

    @Test fun cardDismissalWaitsFourteenDaysAndThreeStopIt() = runBlocking {
        markDays(7)
        repeat(2) { session().startSession() }
        repeat(3) {
            val s = session()
            assertTrue("dismissal #$it", s.card())
            s.onShown(ProPromptTrigger.MARKED_DAYS_MILESTONE)
            s.onDismissed(ProPromptTrigger.MARKED_DAYS_MILESTONE, ProDismissMethod.NOT_NOW)
            now += 13 * day
            assertFalse(session().card())
            now += day
        }
        now += 365 * day
        assertFalse(session().card())
    }

    @Test fun sheetDismissalsPersistAndThreeStopSheets() = runBlocking {
        repeat(2) { session().startSession() }
        repeat(3) {
            val s = session()
            repeat(2) { s.recordBillMilestone() }
            assertTrue(s.billSheet())
            s.onShown(ProPromptTrigger.BILL_MILESTONE)
            s.onDismissed(ProPromptTrigger.BILL_MILESTONE, ProDismissMethod.SWIPE)
            now += 7 * day
        }
        assertFalse(session().billSheet())
    }

    @Test fun promptEventsAreEnumOnly() = runBlocking {
        val s = session()
        s.onShown(ProPromptTrigger.MARKED_DAYS_MILESTONE)
        s.onCtaTap(ProPromptTrigger.MARKED_DAYS_MILESTONE)
        s.onDismissed(ProPromptTrigger.BILL_MILESTONE, ProDismissMethod.CLOSE)
        assertEquals(
            listOf(
                "pro_prompt_shown" to mapOf("trigger" to "marked_days_milestone", "surface" to "card"),
                "pro_prompt_cta_tap" to mapOf("trigger" to "marked_days_milestone", "surface" to "card"),
                "pro_prompt_dismissed" to mapOf("trigger" to "bill_milestone", "surface" to "sheet", "method" to "close"),
            ),
            events,
        )
    }
}
