package com.postpole.gharkhata.prompt

import java.util.concurrent.TimeUnit

/** The two automatic Pro prompts. [analyticsName] / [surface] are the enum values sent to GA4. */
enum class ProPromptTrigger(val analyticsName: String, val surface: String) {
    /** Inline card on the Providers list once enough distinct days were marked. */
    MARKED_DAYS_MILESTONE("marked_days_milestone", "card"),

    /** Sheet on the Month screen once enough bills were shared / settlements saved. */
    BILL_MILESTONE("bill_milestone", "sheet"),
    ;

    /** `trigger` value carried into pro_screen_view / purchase_started / purchase. */
    val autoName: String get() = "auto_$analyticsName"
}

/** Where a prompt would be drawn. Only [PROVIDER_LIST] may host the card, only [PROVIDER_MONTH] the sheet. */
enum class PromptScreen { TODAY, PROVIDER_LIST, PROVIDER_MONTH, OTHER }

/** Remote Config values; [sanitized] clamps them to the allowed bounds. */
data class ProPromptSettings(
    val enabled: Boolean = true,
    val minSession: Int = 3,
    val markedDays: Int = 7,
    val billMilestones: Int = 2,
) {
    fun sanitized() = copy(
        minSession = minSession.coerceAtLeast(MIN_SESSION_FLOOR),
        markedDays = markedDays.coerceIn(MARKED_DAYS_RANGE),
        billMilestones = billMilestones.coerceIn(BILL_MILESTONES_RANGE),
    )

    companion object {
        const val MIN_SESSION_FLOOR = 2
        val MARKED_DAYS_RANGE = 5..21
        val BILL_MILESTONES_RANGE = 1..5
    }
}

/** What has already happened; persisted in DataStore except the two per-session flags. */
data class ProPromptHistory(
    val sessionCount: Int = 0,
    val installAgeMs: Long = 0,
    val lastSheetAt: Long? = null,
    val sheetDismissals: Int = 0,
    val lastCardAt: Long? = null,
    val cardDismissedAt: Long? = null,
    val cardDismissals: Int = 0,
    val lastHardGateAt: Long? = null,
    val billMilestones: Int = 0,
    val distinctMarkedDays: Int = 0,
    val autoPromptShownThisSession: Boolean = false,
    val interstitialShownThisSession: Boolean = false,
)

/** Who is asking, and where. */
data class ProPromptContext(
    val screen: PromptScreen,
    val now: Long,
    val isPro: Boolean = false,
    val hasRemoveAds: Boolean = false,
    /** The 3/3 FreeLimitCard is on screen. */
    val freeLimitCardVisible: Boolean = false,
)

/** Pure automatic-prompt rules. */
object ProPromptPolicy {
    val HARD_GATE_COOLDOWN_MS = TimeUnit.HOURS.toMillis(48)
    val SHEET_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
    val CARD_INTERVAL_MS = TimeUnit.DAYS.toMillis(3)
    val CARD_TRIGGER_COOLDOWN_MS = TimeUnit.DAYS.toMillis(14)
    val MIN_INSTALL_AGE_MS = TimeUnit.DAYS.toMillis(2)
    const val MAX_SHEET_DISMISSALS = 3
    const val MAX_CARD_DISMISSALS = 3
    const val CARD_MIN_SESSION = 2

    fun evaluate(
        trigger: ProPromptTrigger,
        history: ProPromptHistory,
        settings: ProPromptSettings,
        ctx: ProPromptContext,
    ): Boolean {
        val s = settings.sanitized()
        if (ctx.isPro || !s.enabled) return false
        if (history.sessionCount <= 1) return false
        // One auto prompt per session; a shown interstitial uses up the session's interruption.
        if (history.autoPromptShownThisSession || history.interstitialShownThisSession) return false
        if (history.lastHardGateAt?.let { ctx.now - it < HARD_GATE_COOLDOWN_MS } == true) return false
        return when (trigger) {
            ProPromptTrigger.BILL_MILESTONE ->
                ctx.screen == PromptScreen.PROVIDER_MONTH &&
                    !ctx.hasRemoveAds &&
                    history.sessionCount >= s.minSession &&
                    history.installAgeMs >= MIN_INSTALL_AGE_MS &&
                    history.sheetDismissals < MAX_SHEET_DISMISSALS &&
                    elapsed(history.lastSheetAt, ctx.now, SHEET_INTERVAL_MS) &&
                    history.billMilestones >= s.billMilestones

            ProPromptTrigger.MARKED_DAYS_MILESTONE ->
                ctx.screen == PromptScreen.PROVIDER_LIST &&
                    !ctx.freeLimitCardVisible &&
                    history.sessionCount >= CARD_MIN_SESSION &&
                    history.cardDismissals < MAX_CARD_DISMISSALS &&
                    elapsed(history.lastCardAt, ctx.now, CARD_INTERVAL_MS) &&
                    elapsed(history.cardDismissedAt, ctx.now, CARD_TRIGGER_COOLDOWN_MS) &&
                    history.distinctMarkedDays >= s.markedDays
        }
    }

    private fun elapsed(since: Long?, now: Long, interval: Long) = since == null || now - since >= interval
}
