package com.postpole.gharkhata.review

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** What the app tells the review policy, and the one question it asks of it. */
interface ReviewPrompter {
    /** The user viewed a closed month that has marked days (the "full month summary"). Remembered until used. */
    suspend fun onFirstMonthSummaryViewed()

    /** A paywall (Pro sheet or Remove ads offer) was on screen: no review request for the rest of the session. */
    fun onPaywallShown()

    /** An interstitial went on screen: no review request for the rest of the session. */
    fun onInterstitialShown()

    /**
     * Asks at a rest point on the main screen whether the Play review flow may be requested now. When true the
     * request is already recorded (90 day cap, once per trigger) and `review_prompt_requested` is logged, so the
     * caller only has to call [launchInAppReview].
     */
    suspend fun claimRequest(autoProPromptShown: Boolean): Boolean
}

/** Reviews off: used where nothing should ever be requested (tests, previews). */
object NoReviewPrompter : ReviewPrompter {
    override suspend fun onFirstMonthSummaryViewed() = Unit
    override fun onPaywallShown() = Unit
    override fun onInterstitialShown() = Unit
    override suspend fun claimRequest(autoProPromptShown: Boolean) = false
}

/** Everything the decision needs. */
data class ReviewContext(
    val nowMs: Long,
    val sessionCount: Int,
    val summaryViewed: Boolean,
    val alreadyRequested: Boolean,
    val lastRequestAt: Long?,
    val paywallShownThisSession: Boolean,
    val interstitialShownThisSession: Boolean,
    val autoProPromptShownThisSession: Boolean,
)

/**
 * The single review policy: one request after the first full month summary, at most once per 90 days, never in
 * the first session and never in a session that showed a paywall, an automatic Pro prompt or an interstitial.
 * There is no pre-question and no gating on sentiment (Play policy).
 */
@Singleton
class ReviewPolicy(
    private val dataStore: DataStore<Preferences>,
    private val analytics: Analytics,
    private val clock: () -> Long,
) : ReviewPrompter {

    @Inject
    constructor(dataStore: DataStore<Preferences>, analytics: Analytics) : this(dataStore, analytics, System::currentTimeMillis)

    @Volatile private var paywallShown = false

    @Volatile private var interstitialShown = false
    private val sessionCounted = AtomicBoolean(false)

    override fun onPaywallShown() {
        paywallShown = true
    }

    override fun onInterstitialShown() {
        interstitialShown = true
    }

    override suspend fun onFirstMonthSummaryViewed() {
        countSession()
        dataStore.edit { prefs -> if (prefs[REQUESTED] != true) prefs[SUMMARY_VIEWED] = true }
    }

    override suspend fun claimRequest(autoProPromptShown: Boolean): Boolean {
        countSession()
        var claimed = false
        dataStore.edit { prefs ->
            val now = clock()
            val ctx = ReviewContext(
                nowMs = now,
                sessionCount = prefs[SESSIONS] ?: 0,
                summaryViewed = prefs[SUMMARY_VIEWED] == true,
                alreadyRequested = prefs[REQUESTED] == true,
                lastRequestAt = prefs[LAST_PROMPT],
                paywallShownThisSession = paywallShown,
                interstitialShownThisSession = interstitialShown,
                autoProPromptShownThisSession = autoProPromptShown,
            )
            if (evaluate(ctx)) {
                claimed = true
                prefs[REQUESTED] = true
                prefs[LAST_PROMPT] = now
            }
        }
        if (claimed) {
            analytics.logEvent(
                AnalyticsEvents.REVIEW_PROMPT_REQUESTED,
                mapOf(AnalyticsParams.TRIGGER to ReviewTrigger.FIRST_MONTH_SUMMARY.analytics),
            )
        }
        return claimed
    }

    private suspend fun countSession() {
        if (!sessionCounted.compareAndSet(false, true)) return
        dataStore.edit { it[SESSIONS] = (it[SESSIONS] ?: 0) + 1 }
    }

    companion object {
        const val MIN_INTERVAL_DAYS = 90L
        private val MIN_INTERVAL_MS = TimeUnit.DAYS.toMillis(MIN_INTERVAL_DAYS)
        private val SESSIONS = intPreferencesKey("review_session_count")
        private val SUMMARY_VIEWED = booleanPreferencesKey("review_first_month_summary_viewed")
        private val REQUESTED = booleanPreferencesKey("review_first_month_summary_requested")
        private val LAST_PROMPT = longPreferencesKey("review_last_prompt")

        /** Pure decision: may the review flow be requested now? */
        fun evaluate(ctx: ReviewContext): Boolean {
            if (!ctx.summaryViewed || ctx.alreadyRequested) return false
            if (ctx.sessionCount <= 1) return false
            if (ctx.paywallShownThisSession || ctx.interstitialShownThisSession || ctx.autoProPromptShownThisSession) return false
            val last = ctx.lastRequestAt
            return last == null || ctx.nowMs - last >= MIN_INTERVAL_MS
        }
    }
}

/** `trigger` values of `review_prompt_requested`. */
enum class ReviewTrigger(val analytics: String) { FIRST_MONTH_SUMMARY("first_month_summary") }

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
