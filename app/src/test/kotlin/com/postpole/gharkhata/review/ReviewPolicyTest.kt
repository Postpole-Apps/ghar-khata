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

    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000 * day

    private var sessions = 2
    private var autoPrompt = false
    private var paywall = false
    private var interstitial = false
    private var reviewRequestedCalls = 0

    private val prompts = object : ProPrompts by NoProPrompts {
        override val autoPromptShownThisSession get() = autoPrompt
        override val paywallShownThisSession get() = paywall
        override val interstitialShownThisSession get() = interstitial
        override suspend fun sessionCount() = sessions
        override fun onReviewRequested() { reviewRequestedCalls++ }
    }

    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private val dataStore = lazy { PreferenceDataStoreFactory.create { File(tmp.root, "r.preferences_pb") } }

    private fun policy() = ReviewPolicy(dataStore.value, prompts, analytics, TimeUnit.DAYS.toMillis(90), { now })

    @Test fun nothingToRequest_untilAMonthSummaryWasViewed() = runBlocking {
        assertFalse(policy().requestIfEligible())
    }

    @Test fun requests_afterSummaryViewed_andLogsEvent() = runBlocking {
        val p = policy()
        p.onMonthSummaryViewed()
        assertTrue(p.requestIfEligible())
        assertEquals(1, reviewRequestedCalls)
        assertEquals(
            listOf(AnalyticsEvents.REVIEW_PROMPT_REQUESTED to mapOf<String, Any>(AnalyticsParams.TRIGGER to "first_month_summary")),
            events,
        )
    }

    @Test fun requestedOnce_evenMuchLater() = runBlocking {
        val p = policy()
        p.onMonthSummaryViewed()
        assertTrue(p.requestIfEligible())
        assertFalse(p.requestIfEligible())
        now += 365 * day
        p.onMonthSummaryViewed()
        assertFalse(p.requestIfEligible())
        assertEquals(1, events.size)
    }

    @Test fun blockedInFirstSession_thenWaitsForLaterSession() = runBlocking {
        val p = policy()
        sessions = 1
        p.onMonthSummaryViewed()
        assertFalse(p.requestIfEligible())
        assertEquals(0, reviewRequestedCalls)
        sessions = 2
        assertTrue(p.requestIfEligible())
    }

    @Test fun blockedByAutoProPrompt_includingMonthEndCard() = runBlocking {
        val p = policy()
        p.onMonthSummaryViewed()
        autoPrompt = true
        assertFalse(p.requestIfEligible())
        autoPrompt = false
        assertTrue(p.requestIfEligible())
    }

    @Test fun blockedByPaywall() = runBlocking {
        val p = policy()
        p.onMonthSummaryViewed()
        paywall = true
        assertFalse(p.requestIfEligible())
        paywall = false
        assertTrue(p.requestIfEligible())
    }

    @Test fun blockedByInterstitial() = runBlocking {
        val p = policy()
        p.onMonthSummaryViewed()
        interstitial = true
        assertFalse(p.requestIfEligible())
        interstitial = false
        assertTrue(p.requestIfEligible())
    }

    @Test fun canRequest_ninetyDayCap() {
        val cap = TimeUnit.DAYS.toMillis(90)
        assertTrue(ReviewPolicy.canRequest(2, false, null, 0, cap))
        assertFalse(ReviewPolicy.canRequest(2, false, 0L, cap - 1, cap))
        assertTrue(ReviewPolicy.canRequest(2, false, 0L, cap, cap))
    }

    @Test fun canRequest_firstSessionAndSessionBlock() {
        assertFalse(ReviewPolicy.canRequest(1, false, null, 0, 1))
        assertFalse(ReviewPolicy.canRequest(0, false, null, 0, 1))
        assertFalse(ReviewPolicy.canRequest(5, true, null, 0, 1))
    }
}
