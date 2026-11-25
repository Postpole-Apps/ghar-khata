package com.postpole.gharkhata.prompt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What view models and screens need from the automatic-prompt state. */
interface ProPromptStore {
    /** The user marked [epochDay] (any status). Counts distinct days. */
    suspend fun recordDayMarked(epochDay: Long)

    /** A bill was shared or a settlement saved. */
    suspend fun recordBillMilestone()

    /** Distinct days marked so far (capped at the highest allowed threshold). */
    val distinctMarkedDays: Flow<Int>

    /** All cooldowns and caps pass for [trigger] on [screen]. */
    suspend fun shouldShow(trigger: ProPromptTrigger, screen: PromptScreen, freeLimitCardVisible: Boolean = false): Boolean

    suspend fun markShown(trigger: ProPromptTrigger)
    suspend fun markDismissed(trigger: ProPromptTrigger)

    /** A hard-gate ProSheet (limit, bill image, CSV, year summary, reminder) was shown. */
    suspend fun markHardGate()

    /** An auto prompt already used this session, so the interstitial must be skipped. */
    val autoPromptShownThisSession: Boolean
    fun markInterstitialShown()
}

/** Store that never prompts and records nothing. */
object NoOpProPromptStore : ProPromptStore {
    override suspend fun recordDayMarked(epochDay: Long) = Unit
    override suspend fun recordBillMilestone() = Unit
    override val distinctMarkedDays: Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
    override suspend fun shouldShow(trigger: ProPromptTrigger, screen: PromptScreen, freeLimitCardVisible: Boolean) = false
    override suspend fun markShown(trigger: ProPromptTrigger) = Unit
    override suspend fun markDismissed(trigger: ProPromptTrigger) = Unit
    override suspend fun markHardGate() = Unit
    override val autoPromptShownThisSession: Boolean = false
    override fun markInterstitialShown() = Unit
}

/** DataStore-backed [ProPromptStore]. One instance per process; a process start is one session. */
class ProPromptRepository(
    private val dataStore: DataStore<Preferences>,
    private val config: ProPromptSettingsSource,
    private val installTimeMs: () -> Long,
    private val isPro: () -> Boolean,
    private val hasRemoveAds: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProPromptStore {

    @Volatile private var autoShown = false
    @Volatile private var interstitialShown = false

    override val autoPromptShownThisSession: Boolean get() = autoShown

    /** Call once from Application.onCreate. */
    suspend fun onSessionStart() {
        config.refresh()
        dataStore.edit { it[SESSION_COUNT] = (it[SESSION_COUNT] ?: 0) + 1 }
    }

    fun refreshConfig() = config.refresh()

    override val distinctMarkedDays: Flow<Int> =
        dataStore.data.map { it[MARKED_DAYS]?.size ?: 0 }.distinctUntilChanged()

    override suspend fun recordDayMarked(epochDay: Long) {
        dataStore.edit { prefs ->
            val days = prefs[MARKED_DAYS].orEmpty()
            if (days.size < MARKED_DAYS_CAP) prefs[MARKED_DAYS] = days + epochDay.toString()
        }
    }

    override suspend fun recordBillMilestone() {
        dataStore.edit { it[BILL_MILESTONES] = (it[BILL_MILESTONES] ?: 0) + 1 }
    }

    override suspend fun shouldShow(trigger: ProPromptTrigger, screen: PromptScreen, freeLimitCardVisible: Boolean): Boolean {
        val prefs = dataStore.data.first()
        val now = clock()
        val history = ProPromptHistory(
            sessionCount = prefs[SESSION_COUNT] ?: 0,
            installAgeMs = now - installTimeMs(),
            lastSheetAt = prefs[LAST_SHEET_AT],
            sheetDismissals = prefs[SHEET_DISMISSALS] ?: 0,
            lastCardAt = prefs[LAST_CARD_AT],
            cardDismissedAt = prefs[cardDismissedAtKey(trigger)],
            cardDismissals = prefs[cardDismissalsKey(trigger)] ?: 0,
            lastHardGateAt = prefs[LAST_HARD_GATE_AT],
            billMilestones = prefs[BILL_MILESTONES] ?: 0,
            distinctMarkedDays = prefs[MARKED_DAYS]?.size ?: 0,
            autoPromptShownThisSession = autoShown,
            interstitialShownThisSession = interstitialShown,
        )
        val ctx = ProPromptContext(screen, now, isPro(), hasRemoveAds(), freeLimitCardVisible)
        return ProPromptPolicy.evaluate(trigger, history, config.settings.value, ctx)
    }

    override suspend fun markShown(trigger: ProPromptTrigger) {
        autoShown = true
        val now = clock()
        dataStore.edit {
            when (trigger) {
                ProPromptTrigger.BILL_MILESTONE -> it[LAST_SHEET_AT] = now
                ProPromptTrigger.MARKED_DAYS_MILESTONE -> it[LAST_CARD_AT] = now
            }
        }
    }

    override suspend fun markDismissed(trigger: ProPromptTrigger) {
        val now = clock()
        dataStore.edit {
            when (trigger) {
                ProPromptTrigger.BILL_MILESTONE -> it[SHEET_DISMISSALS] = (it[SHEET_DISMISSALS] ?: 0) + 1
                ProPromptTrigger.MARKED_DAYS_MILESTONE -> {
                    it[cardDismissedAtKey(trigger)] = now
                    it[cardDismissalsKey(trigger)] = (it[cardDismissalsKey(trigger)] ?: 0) + 1
                }
            }
        }
    }

    override suspend fun markHardGate() {
        val now = clock()
        dataStore.edit { it[LAST_HARD_GATE_AT] = now }
    }

    override fun markInterstitialShown() {
        interstitialShown = true
    }

    private companion object {
        /** Highest `pro_prompt_marked_days` bound: no need to remember more days than that. */
        val MARKED_DAYS_CAP = ProPromptSettings.MARKED_DAYS_RANGE.last
        val SESSION_COUNT = intPreferencesKey("pro_prompt_session_count")
        val LAST_SHEET_AT = longPreferencesKey("pro_prompt_last_sheet_at")
        val SHEET_DISMISSALS = intPreferencesKey("pro_prompt_sheet_dismissals")
        val LAST_CARD_AT = longPreferencesKey("pro_prompt_last_card_at")
        val LAST_HARD_GATE_AT = longPreferencesKey("pro_prompt_last_hard_gate_at")
        val BILL_MILESTONES = intPreferencesKey("pro_prompt_bill_milestones_count")
        val MARKED_DAYS = stringSetPreferencesKey("pro_prompt_marked_days_set")
        fun cardDismissedAtKey(t: ProPromptTrigger) = longPreferencesKey("pro_prompt_card_dismissed_${t.analyticsName}_at")
        fun cardDismissalsKey(t: ProPromptTrigger) = intPreferencesKey("pro_prompt_card_dismissals_${t.analyticsName}")
    }
}
