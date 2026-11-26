package com.postpole.gharkhata.review

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.pro.NoProPrompts
import com.postpole.gharkhata.pro.ProPrompts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ReviewPolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = TimeUnit.DAYS.toMillis(1_000)
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    /** A new instance is a new process: the in-memory session flags reset, the DataStore file stays. */
    private val store by lazy { PreferenceDataStoreFactory.create { File(tmp.root, "r.preferences_pb") } }

    private fun session() = ReviewPolicy(
        store,
        analytics,
        clock = { now },
    )

    /** Session 1 viewed a closed month; returns the policy of session 2. */
    private fun eligibleSecondSession(): ReviewPolicy {
        runBlocking { session().onFirstMonthSummaryViewed() }
        return session()
    }

    /** The session's auto Pro prompt / interstitial slot, with the same exclusivity as the real controller. */
    private class FakePrompts(var autoShown: Boolean = false, var interstitialShown: Boolean = false) :
        ProPrompts by NoProPrompts {
        override val autoPromptShownThisSession get() = autoShown
        override suspend fun takeSessionExclusively(): Boolean {
            if (autoShown || interstitialShown) return false
            autoShown = true
            interstitialShown = true
            return true
        }
    }

    private fun ReviewPolicy.claim(autoPro: Boolean = false) = claim(FakePrompts(autoShown = autoPro))

    private fun ReviewPolicy.claim(prompts: ProPrompts) = runBlocking { claimRequest(prompts) }

    @Test fun requests_inLaterSession_afterSummaryViewed() {
        assertTrue(eligibleSecondSession().claim())
        assertEquals(
            listOf(AnalyticsEvents.REVIEW_PROMPT_REQUESTED to mapOf<String, Any>(AnalyticsParams.TRIGGER to "first_month_summary")),
            events,
        )
    }

    @Test fun neverWithoutSummaryView() {
        session().claim()
        assertFalse(session().claim())
        assertTrue(events.isEmpty())
    }

    @Test fun neverInFirstSession() {
        val p = session()
        runBlocking { p.onFirstMonthSummaryViewed() }
        assertFalse(p.claim())
        assertTrue(events.isEmpty())
    }

    @Test fun onlyOnce_perTrigger() {
        assertTrue(eligibleSecondSession().claim())
        now += TimeUnit.DAYS.toMillis(400)
        assertFalse(session().claim())
        assertEquals(1, events.size)
    }

    @Test fun ninetyDayCap() {
        assertFalse(ReviewPolicy.evaluate(ctx(lastRequestAt = now - TimeUnit.DAYS.toMillis(89))))
        assertTrue(ReviewPolicy.evaluate(ctx(lastRequestAt = now - TimeUnit.DAYS.toMillis(90))))
        assertTrue(ReviewPolicy.evaluate(ctx(lastRequestAt = null)))
    }

    @Test fun blockedBy_paywallInSameSession() {
        val p = eligibleSecondSession()
        p.onPaywallShown()
        assertFalse(p.claim())
        assertTrue(events.isEmpty())
        assertTrue(session().claim()) // a later session is free of it
    }

    @Test fun blockedBy_interstitialInSameSession() {
        val p = eligibleSecondSession()
        p.onInterstitialShown()
        assertFalse(p.claim())
        assertTrue(session().claim())
    }

    @Test fun blockedBy_autoProPrompt_includingMonthEndCard() {
        val p = eligibleSecondSession()
        assertFalse(p.claim(autoPro = true))
        assertTrue(session().claim(autoPro = false)) // waits for a later eligible session
    }

    @Test fun blockedBy_interstitialTakenThroughPrompts() {
        val p = eligibleSecondSession()
        assertFalse(p.claim(FakePrompts(interstitialShown = true)))
        assertTrue(events.isEmpty())
    }

    @Test fun grantedReview_takesTheSessionSlot_soNoLaterPromptOrInterstitial() {
        val prompts = FakePrompts()
        assertTrue(eligibleSecondSession().claim(prompts))
        assertTrue(prompts.autoShown) // an auto Pro card is refused, and the interstitial path checks it
        assertTrue(prompts.interstitialShown)
        assertFalse(runBlocking { prompts.takeSessionExclusively() })
    }

    @Test fun deniedReview_leavesTheSessionSlotFree() {
        val prompts = FakePrompts()
        assertFalse(session().claim(prompts)) // nothing viewed yet
        assertFalse(prompts.autoShown)
        assertFalse(prompts.interstitialShown)
    }

    @Test fun cardReservedAfterTheSettle_winsAndNoReviewIsRequested() {
        val p = eligibleSecondSession()
        // The flag read for the decision is still false, but the card reserves before the slot is taken.
        val late = object : ProPrompts by NoProPrompts {
            override val autoPromptShownThisSession = false
            override suspend fun takeSessionExclusively() = false
        }
        assertFalse(p.claim(late))
        assertTrue(events.isEmpty())
        assertTrue(session().claim()) // not consumed: a later session still gets its request
    }

    private fun ctx(lastRequestAt: Long?) = ReviewContext(
        nowMs = now,
        sessionCount = 2,
        summaryViewed = true,
        alreadyRequested = false,
        lastRequestAt = lastRequestAt,
        paywallShownThisSession = false,
        interstitialShownThisSession = false,
        autoProPromptShownThisSession = false,
    )
}
