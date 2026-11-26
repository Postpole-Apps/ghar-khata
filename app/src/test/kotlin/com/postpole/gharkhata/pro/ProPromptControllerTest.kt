package com.postpole.gharkhata.pro

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.analytics.Analytics
import androidx.datastore.preferences.core.edit
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.provider.FakeProviderDao
import com.postpole.gharkhata.ui.provider.FakeRateDao
import kotlinx.coroutines.flow.first
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
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class ProPromptControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 100 * day
    private val installedAt = 10 * day
    private val providers = FakeProviderDao(FakeRateDao())
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
        dataStore = dataStore.value, providerDao = providers, source = settings, analytics = analytics,
        price = MutableStateFlow("price"), isPro = { pro }, hasRemoveAds = { removeAds },
        installTimeMs = { installedAt }, clock = { now }, zone = { ZoneOffset.UTC },
    )

    private suspend fun addProvider(archived: Boolean = false) {
        providers.upsert(
            ProviderEntity(
                name = "Milk", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L",
                startEpochDay = 0, archived = archived, createdAt = 0,
            ),
        )
    }

    /** Puts the stored active-day count at [n] (and an active provider in place), as if used on n days. */
    private suspend fun markDays(n: Int) {
        addProvider()
        dataStore.value.edit {
            it[ProPromptController.ACTIVE_DAYS] = n
            it[ProPromptController.LAST_ACTIVE_DAY] = now / day
        }
    }

    private suspend fun activeDays() = dataStore.value.data.first()[ProPromptController.ACTIVE_DAYS] ?: 0

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

    @Test fun reviewTakingTheSession_blocksLaterCardAndSheet_andInterstitialFlag() = runBlocking {
        markDays(7)
        session().startSession()
        val s = session()
        s.startSession()
        repeat(2) { s.recordBillMilestone() }
        assertTrue(s.takeSessionExclusively())
        assertTrue(s.autoPromptShownThisSession) // the interstitial path checks this too
        assertFalse(s.card())
        assertFalse(s.billSheet())
        assertFalse(s.takeSessionExclusively())
    }

    @Test fun reviewCannotTakeTheSession_afterAnAutoPromptOrInterstitial() = runBlocking {
        markDays(7)
        session().startSession()
        val a = session()
        a.startSession()
        assertTrue(a.card())
        assertFalse(a.takeSessionExclusively())
        val b = session()
        b.startSession()
        b.onInterstitialShown()
        assertFalse(b.takeSessionExclusively())
    }

    @Test fun twoSessionsOnTheSameDayCountOnce() = runBlocking {
        addProvider()
        session().startSession()
        session().startSession()
        session().card()
        assertEquals(1, activeDays())
    }

    @Test fun eachNewDayAddsOne() = runBlocking {
        addProvider()
        session().startSession()
        now += day
        session().startSession()
        assertEquals(2, activeDays())
        // A long-running process crossing midnight counts through its next prompt check.
        val s = session()
        s.startSession()
        now += day
        s.card()
        assertEquals(3, activeDays())
    }

    @Test fun dayWithNoActiveProviderDoesNotCount() = runBlocking {
        session().startSession()
        assertEquals(0, activeDays())
        addProvider(archived = true)
        now += day
        session().startSession()
        assertEquals(0, activeDays())
        addProvider()
        now += day
        session().startSession()
        assertEquals(1, activeDays())
    }

    @Test fun cardBecomesEligibleAtSevenActiveDays() = runBlocking {
        addProvider()
        repeat(5) { session().startSession(); now += day }
        session().startSession()
        assertEquals(6, activeDays())
        assertFalse(session().card())
        now += day
        // The seventh active day is counted by the evaluation itself.
        assertTrue(session().card())
        assertEquals(7, activeDays())
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
