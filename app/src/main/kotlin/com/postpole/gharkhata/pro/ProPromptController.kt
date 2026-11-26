package com.postpole.gharkhata.pro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.data.ProviderDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What screens and view models use of the automatic Pro prompts. */
interface ProPrompts {
    /** Localized Pro price from Play Billing; null until loaded. Never hard-coded. */
    val price: StateFlow<String?>

    /** An auto prompt was shown (or reserved) this session: no other one may show, and no interstitial. */
    val autoPromptShownThisSession: Boolean

    /** A bill share or a saved settlement. */
    suspend fun recordBillMilestone()

    /** A hard-gate ProSheet (provider limit, bill image, CSV, year summary, reminder) opened. */
    suspend fun recordHardGate()

    /**
     * Asks whether [trigger] may show on [screen] now. When it may, the session's one auto-prompt slot is
     * taken, so a second call (or the other trigger) answers false until the next session.
     */
    suspend fun reserve(trigger: ProPromptTrigger, screen: ProPromptScreen, freeLimitCardShowing: Boolean = false): Boolean

    /**
     * Takes the session's auto-prompt and interstitial slots for something else that must not share the session
     * with them (the Play review flow). Atomic with [reserve]: false when an auto prompt or interstitial already
     * took the session, otherwise true and both stay off until the next session.
     */
    suspend fun takeSessionExclusively(): Boolean

    /** The prompt is on screen: starts its cooldown and logs `pro_prompt_shown`. */
    suspend fun onShown(trigger: ProPromptTrigger)

    suspend fun onDismissed(trigger: ProPromptTrigger, method: ProDismissMethod)

    fun onCtaTap(trigger: ProPromptTrigger)

    /** An interstitial went on screen: auto prompts are off for the rest of the session. */
    fun onInterstitialShown()
}

/** Prompts off: used where nothing should ever be offered (tests, previews). */
object NoProPrompts : ProPrompts {
    override val price: StateFlow<String?> = MutableStateFlow(null)
    override val autoPromptShownThisSession = false
    override suspend fun recordBillMilestone() = Unit
    override suspend fun recordHardGate() = Unit
    override suspend fun reserve(trigger: ProPromptTrigger, screen: ProPromptScreen, freeLimitCardShowing: Boolean) = false
    override suspend fun takeSessionExclusively() = true
    override suspend fun onShown(trigger: ProPromptTrigger) = Unit
    override suspend fun onDismissed(trigger: ProPromptTrigger, method: ProDismissMethod) = Unit
    override fun onCtaTap(trigger: ProPromptTrigger) = Unit
    override fun onInterstitialShown() = Unit
}

/** DataStore backed [ProPrompts]. A session is an app process, as for the interstitial. */
@Singleton
class ProPromptController(
    private val dataStore: DataStore<Preferences>,
    private val providerDao: ProviderDao,
    private val source: ProPromptSettingsSource,
    private val analytics: Analytics,
    override val price: StateFlow<String?>,
    private val isPro: suspend () -> Boolean,
    private val hasRemoveAds: suspend () -> Boolean,
    private val installTimeMs: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ProPrompts {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        providerDao: ProviderDao,
        source: ProPromptSettingsSource,
        analytics: Analytics,
        billing: BillingRepository,
    ) : this(
        dataStore, providerDao, source, analytics, billing.price,
        isPro = billing::isProStored,
        hasRemoveAds = billing::hasRemoveAdsStored,
        installTimeMs = { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime },
    )

    private val lock = Mutex()
    private var sessionCounted = false

    @Volatile override var autoPromptShownThisSession = false
        private set

    @Volatile private var interstitialShownThisSession = false

    /** Call once from Application.onCreate (also done lazily by the first evaluation). */
    suspend fun startSession() = lock.withLock {
        countSession()
        countActiveDay()
    }

    fun refreshSettings() = source.refresh()

    private suspend fun countSession() {
        if (sessionCounted) return
        sessionCounted = true
        dataStore.edit { it[SESSIONS] = (it[SESSIONS] ?: 0) + 1 }
    }

    /** Counts today once, and only when at least one active provider exists. Call with [lock] held. */
    private suspend fun countActiveDay() {
        if (providerDao.countActive().first() < 1) return
        val today = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toEpochDay()
        dataStore.edit {
            if (it[LAST_ACTIVE_DAY] != today) {
                it[LAST_ACTIVE_DAY] = today
                it[ACTIVE_DAYS] = (it[ACTIVE_DAYS] ?: 0) + 1
            }
        }
    }

    override fun onInterstitialShown() {
        interstitialShownThisSession = true
    }

    override suspend fun recordBillMilestone() {
        dataStore.edit { it[BILL_MILESTONES] = (it[BILL_MILESTONES] ?: 0) + 1 }
    }

    override suspend fun recordHardGate() {
        val now = clock()
        dataStore.edit { it[LAST_HARD_GATE] = now }
    }

    override suspend fun reserve(trigger: ProPromptTrigger, screen: ProPromptScreen, freeLimitCardShowing: Boolean): Boolean =
        lock.withLock {
            countSession()
            countActiveDay()
            val now = clock()
            val ok = ProPromptPolicy.evaluate(
                trigger = trigger,
                screen = screen,
                settings = source.settings.value,
                history = history(now),
                ctx = ProPromptContext(
                    nowMs = now,
                    isPro = isPro(),
                    hasRemoveAds = hasRemoveAds(),
                    autoPromptShownThisSession = autoPromptShownThisSession,
                    interstitialShownThisSession = interstitialShownThisSession,
                    freeLimitCardShowing = freeLimitCardShowing,
                ),
            )
            if (ok) autoPromptShownThisSession = true
            ok
        }

    override suspend fun takeSessionExclusively(): Boolean = lock.withLock {
        if (autoPromptShownThisSession || interstitialShownThisSession) return@withLock false
        autoPromptShownThisSession = true
        interstitialShownThisSession = true
        true
    }

    override suspend fun onShown(trigger: ProPromptTrigger) {
        val now = clock()
        dataStore.edit { if (trigger.surface == ProPromptSurface.SHEET) it[LAST_SHEET] = now else it[LAST_CARD] = now }
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, params(trigger))
    }

    override suspend fun onDismissed(trigger: ProPromptTrigger, method: ProDismissMethod) {
        val now = clock()
        dataStore.edit {
            if (trigger.surface == ProPromptSurface.SHEET) {
                it[SHEET_DISMISSALS] = (it[SHEET_DISMISSALS] ?: 0) + 1
            } else {
                val count = cardDismissalsKey(trigger)
                it[count] = (it[count] ?: 0) + 1
                it[cardDismissedAtKey(trigger)] = now
            }
        }
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_DISMISSED, params(trigger) + (AnalyticsParams.METHOD to method.analytics))
    }

    override fun onCtaTap(trigger: ProPromptTrigger) {
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, params(trigger))
    }

    private fun params(trigger: ProPromptTrigger): Map<String, Any> = mapOf(
        AnalyticsParams.TRIGGER to trigger.analytics,
        AnalyticsParams.SURFACE to trigger.surface.analytics,
    )

    private suspend fun history(now: Long): ProPromptHistory {
        val prefs = dataStore.data.first()
        val card = ProPromptTrigger.entries.filter { it.surface == ProPromptSurface.CARD }
        return ProPromptHistory(
            sessionCount = prefs[SESSIONS] ?: 0,
            installAgeDays = TimeUnit.MILLISECONDS.toDays((now - installTimeMs()).coerceAtLeast(0)),
            lastSheetAt = prefs[LAST_SHEET],
            sheetDismissals = prefs[SHEET_DISMISSALS] ?: 0,
            lastCardAt = prefs[LAST_CARD],
            cardDismissedAt = card.mapNotNull { t -> prefs[cardDismissedAtKey(t)]?.let { t to it } }.toMap(),
            cardDismissals = card.associateWith { prefs[cardDismissalsKey(it)] ?: 0 },
            lastHardGateAt = prefs[LAST_HARD_GATE],
            billMilestones = prefs[BILL_MILESTONES] ?: 0,
            distinctMarkedDays = prefs[ACTIVE_DAYS] ?: 0,
        )
    }

    internal companion object {
        val SESSIONS = intPreferencesKey("pro_prompt_session_count")
        val LAST_SHEET = longPreferencesKey("pro_prompt_last_sheet_at")
        val SHEET_DISMISSALS = intPreferencesKey("pro_prompt_sheet_dismissals")
        val LAST_CARD = longPreferencesKey("pro_prompt_last_card_at")
        val LAST_HARD_GATE = longPreferencesKey("pro_prompt_last_hard_gate_at")
        val ACTIVE_DAYS = intPreferencesKey("pro_prompt_active_days_count")
        val LAST_ACTIVE_DAY = longPreferencesKey("pro_prompt_last_active_day")
        val BILL_MILESTONES = intPreferencesKey("pro_prompt_bill_milestones_count")
        fun cardDismissedAtKey(t: ProPromptTrigger) = longPreferencesKey("pro_prompt_card_dismissed_" + t.analytics + "_at")
        fun cardDismissalsKey(t: ProPromptTrigger) = intPreferencesKey("pro_prompt_card_dismissals_" + t.analytics)
    }
}
