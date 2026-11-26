package com.postpole.gharkhata.review

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.analytics.ReviewTriggers
import com.postpole.gharkhata.pro.ProPrompts
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Decides when to ask for a Play review: once, after the first full month summary, at a rest point. */
interface ReviewPrompter {
    /** The user viewed a closed month with marked days. Remembered until a request is allowed. */
    suspend fun onMonthSummaryViewed()

    /**
     * Called at a rest point (back on the main screen). Returns true when the review flow should be launched
     * now; the request is then recorded and logged, so the caller only launches the flow.
     */
    suspend fun requestIfEligible(): Boolean
}

/** No review prompts: tests and previews. */
object NoReviewPrompter : ReviewPrompter {
    override suspend fun onMonthSummaryViewed() = Unit
    override suspend fun requestIfEligible() = false
}

@Singleton
class ReviewPolicy(
    private val dataStore: DataStore<Preferences>,
    private val prompts: ProPrompts,
    private val analytics: Analytics,
    private val minIntervalMs: Long,
    private val clock: () -> Long,
) : ReviewPrompter {

    @Inject
    constructor(dataStore: DataStore<Preferences>, prompts: ProPrompts, analytics: Analytics) : this(
        dataStore, prompts, analytics,
        minIntervalMs = TimeUnit.DAYS.toMillis(MIN_INTERVAL_DAYS),
        clock = System::currentTimeMillis,
    )

    override suspend fun onMonthSummaryViewed() {
        dataStore.edit { if (it[REQUESTED_FIRST_SUMMARY] != true) it[SUMMARY_VIEWED] = true }
    }

    override suspend fun requestIfEligible(): Boolean {
        val sessions = prompts.sessionCount()
        val blocked = prompts.autoPromptShownThisSession ||
            prompts.paywallShownThisSession ||
            prompts.interstitialShownThisSession
        var granted = false
        dataStore.edit { prefs ->
            val now = clock()
            if (prefs[SUMMARY_VIEWED] == true && prefs[REQUESTED_FIRST_SUMMARY] != true &&
                canRequest(sessions, blocked, prefs[LAST_REQUEST], now, minIntervalMs)
            ) {
                granted = true
                prefs[LAST_REQUEST] = now
                prefs[REQUESTED_FIRST_SUMMARY] = true
                prefs[SUMMARY_VIEWED] = false
            }
        }
        if (granted) {
            prompts.onReviewRequested()
            analytics.logEvent(
                AnalyticsEvents.REVIEW_PROMPT_REQUESTED,
                mapOf(AnalyticsParams.TRIGGER to ReviewTriggers.FIRST_MONTH_SUMMARY),
            )
        }
        return granted
    }

    companion object {
        const val MIN_INTERVAL_DAYS = 90L
        private val SUMMARY_VIEWED = booleanPreferencesKey("review_month_summary_viewed")
        private val REQUESTED_FIRST_SUMMARY = booleanPreferencesKey("review_requested_first_month_summary")
        private val LAST_REQUEST = longPreferencesKey("review_last_request_at")

        /**
         * Pure rules: never in the first session, never in a session that already showed a paywall, an auto
         * Pro prompt or an interstitial ([sessionBlocked]), and at most once per [minIntervalMs].
         */
        internal fun canRequest(
            sessionCount: Int,
            sessionBlocked: Boolean,
            lastRequestAt: Long?,
            nowMs: Long,
            minIntervalMs: Long,
        ): Boolean {
            if (sessionCount <= 1) return false
            if (sessionBlocked) return false
            return lastRequestAt == null || nowMs - lastRequestAt >= minIntervalMs
        }
    }
}

/** Shows the Play In-App Review sheet. Play decides whether it actually appears (quota); failures are ignored. */
suspend fun launchInAppReview(activity: Activity, onError: (Throwable) -> Unit = {}) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
