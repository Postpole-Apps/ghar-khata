package com.postpole.gharkhata.prompt

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ProPromptRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 100 * day
    private var isPro = false
    private var removeAds = false
    private val settings = StaticProPromptSettings()

    // One DataStore per file; extra repositories on it stand in for later process starts.
    private val store by lazy { PreferenceDataStoreFactory.create { File(tmp.root, "p.preferences_pb") } }

    private fun repo() = ProPromptRepository(
        store,
        settings,
        installTimeMs = { now - 10 * day },
        isPro = { isPro },
        hasRemoveAds = { removeAds },
        clock = { now },
    )

    private suspend fun ProPromptRepository.sessions(n: Int) = repeat(n) { onSessionStart() }

    @Test fun markedDaysCountDistinctDays() = runBlocking {
        val r = repo()
        listOf(1L, 1L, 2L, 3L, 3L).forEach { r.recordDayMarked(it) }
        assertEquals(3, r.distinctMarkedDays.first())
    }

    @Test fun markedDaysStopGrowingAtTheHighestThreshold() = runBlocking {
        val r = repo()
        (1L..40L).forEach { r.recordDayMarked(it) }
        assertEquals(21, r.distinctMarkedDays.first())
    }

    @Test fun cardNeedsSecondSessionAndSevenMarkedDays() = runBlocking {
        val r = repo()
        (1L..7L).forEach { r.recordDayMarked(it) }
        r.sessions(1)
        assertFalse(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
        r.onSessionStart()
        assertTrue(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
        assertFalse(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.TODAY))
        assertFalse(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST, freeLimitCardVisible = true))
    }

    @Test fun shownCardUsesUpTheSessionAndPersistsCooldown() = runBlocking {
        val r = repo()
        (1L..7L).forEach { r.recordDayMarked(it) }
        r.sessions(2)
        r.markShown(ProPromptTrigger.MARKED_DAYS_MILESTONE)
        assertTrue(r.autoPromptShownThisSession)
        // Same session: blocked by the one-per-session cap.
        assertFalse(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
        // A new process (new repo, same store) is a new session but the 3 day card cooldown holds.
        val next = repo().also { it.onSessionStart() }
        assertFalse(next.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
        now += 3 * day
        assertTrue(next.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
    }

    @Test fun cardDismissalsCooldownThenCap() = runBlocking {
        val r = repo()
        (1L..7L).forEach { r.recordDayMarked(it) }
        r.sessions(2)
        repeat(3) {
            assertTrue(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
            r.markDismissed(ProPromptTrigger.MARKED_DAYS_MILESTONE)
            now += 13 * day
            assertFalse(repo().also { it.sessions(1) }.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
            now += 1 * day
        }
        now += 365 * day
        assertFalse(r.shouldShow(ProPromptTrigger.MARKED_DAYS_MILESTONE, PromptScreen.PROVIDER_LIST))
    }

    @Test fun sheetNeedsMilestonesAndSessionThree() = runBlocking {
        val r = repo()
        r.sessions(2)
        r.recordBillMilestone()
        r.recordBillMilestone()
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        r.onSessionStart()
        assertTrue(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_LIST))
        removeAds = true
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        removeAds = false
        isPro = true
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
    }

    @Test fun threeSheetDismissalsStopSheets() = runBlocking {
        val r = repo()
        r.sessions(3)
        repeat(2) { r.recordBillMilestone() }
        repeat(3) {
            assertTrue(repo().also { it.sessions(1) }.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
            r.markDismissed(ProPromptTrigger.BILL_MILESTONE)
        }
        now += 365 * day
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
    }

    @Test fun hardGateBlocksFor48HoursAndInterstitialBlocksTheSession() = runBlocking {
        val r = repo()
        r.sessions(3)
        repeat(2) { r.recordBillMilestone() }
        r.markHardGate()
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        now += 48 * TimeUnit.HOURS.toMillis(1)
        assertTrue(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        r.markInterstitialShown()
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
    }

    @Test fun remoteConfigOverridesApply() = runBlocking {
        val r = repo()
        r.sessions(3)
        repeat(2) { r.recordBillMilestone() }
        settings.settings.value = ProPromptSettings(billMilestones = 3)
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
        settings.settings.value = ProPromptSettings(enabled = false)
        assertFalse(r.shouldShow(ProPromptTrigger.BILL_MILESTONE, PromptScreen.PROVIDER_MONTH))
    }
}
