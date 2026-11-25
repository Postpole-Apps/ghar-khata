package com.postpole.gharkhata.pro

import java.util.concurrent.TimeUnit

/** The two automatic Pro prompts. [analytics] is the enum-only value logged as `trigger`. */
enum class ProPromptTrigger(val analytics: String, val surface: ProPromptSurface, val screen: ProPromptScreen) {
    MARKED_DAYS_MILESTONE("marked_days_milestone", ProPromptSurface.CARD, ProPromptScreen.PROVIDER_LIST),
    BILL_MILESTONE("bill_milestone", ProPromptSurface.SHEET, ProPromptScreen.PROVIDER_MONTH),
    ;

    /** `trigger` value carried into `pro_screen_view`, `purchase_started` and `purchase`. */
    val autoAnalytics: String get() = "auto_" + analytics
}

/** Where a prompt shows; `surface` param of the prompt events. */
enum class ProPromptSurface(val analytics: String) { CARD("card"), SHEET("sheet") }

/** Screen asking the policy. Only the screen a trigger belongs to may show it. */
enum class ProPromptScreen { TODAY, PROVIDER_LIST, PROVIDER_MONTH, OTHER }

/** How an auto prompt was dismissed. A scrim tap or system back press is [SWIPE]. */
enum class ProDismissMethod(val analytics: String) { CLOSE("close"), NOT_NOW("not_now"), SWIPE("swipe") }

/** Remote Config keys. */
object ProPromptKeys {
    const val ENABLED = "pro_auto_prompt_enabled"
    const val MIN_SESSION = "pro_auto_prompt_min_session"
    const val MARKED_DAYS = "pro_prompt_marked_days"
    const val BILL_MILESTONES = "pro_prompt_bill_milestones"
}

/** Remote Config values after clamping to their allowed bounds. Use [of] to build one from raw values. */
data class ProPromptSettings(
    val enabled: Boolean = true,
    val minSession: Int = DEFAULT_MIN_SESSION,
    val markedDays: Int = DEFAULT_MARKED_DAYS,
    val billMilestones: Int = DEFAULT_BILL_MILESTONES,
) {
    companion object {
        const val DEFAULT_MIN_SESSION = 3
        const val DEFAULT_MARKED_DAYS = 7
        const val DEFAULT_BILL_MILESTONES = 2
        val MARKED_DAYS_RANGE = 5..21
        val BILL_MILESTONES_RANGE = 1..5
        const val MIN_SESSION_FLOOR = 2
        val DEFAULT = ProPromptSettings()

        fun of(enabled: Boolean, minSession: Int, markedDays: Int, billMilestones: Int) = ProPromptSettings(
            enabled = enabled,
            minSession = minSession.coerceAtLeast(MIN_SESSION_FLOOR),
            markedDays = markedDays.coerceIn(MARKED_DAYS_RANGE),
            billMilestones = billMilestones.coerceIn(BILL_MILESTONES_RANGE),
        )
    }
}

/** What is persisted about earlier prompts and usage. A null timestamp means never. */
data class ProPromptHistory(
    /** App process starts, 1 = first ever. */
    val sessionCount: Int = 0,
    val installAgeDays: Long = 0,
    val lastSheetAt: Long? = null,
    val sheetDismissals: Int = 0,
    val lastCardAt: Long? = null,
    val cardDismissedAt: Map<ProPromptTrigger, Long> = emptyMap(),
    val cardDismissals: Map<ProPromptTrigger, Int> = emptyMap(),
    val lastHardGateAt: Long? = null,
    val billMilestones: Int = 0,
    val distinctMarkedDays: Int = 0,
)

/** Live state of the moment: who the user is and what already happened this session. */
data class ProPromptContext(
    val nowMs: Long,
    val isPro: Boolean = false,
    val hasRemoveAds: Boolean = false,
    val autoPromptShownThisSession: Boolean = false,
    val interstitialShownThisSession: Boolean = false,
    /** The 3/3 FreeLimitCard is on screen (Providers). */
    val freeLimitCardShowing: Boolean = false,
)

/** Pure decision: may [trigger] be shown now on [screen]? Every confirmed automatic-prompt rule lives here. */
object ProPromptPolicy {
    private val DAY_MS = TimeUnit.DAYS.toMillis(1)
    private val HARD_GATE_COOLDOWN_MS = TimeUnit.HOURS.toMillis(48)
    private const val SHEET_MIN_INSTALL_DAYS = 2L
    private const val CARD_MIN_SESSION = 2
    private const val MAX_SHEET_DISMISSALS = 3
    private const val MAX_CARD_DISMISSALS = 3
    private val SHEET_COOLDOWN_MS = 7 * DAY_MS
    private val CARD_COOLDOWN_MS = 3 * DAY_MS
    private val CARD_DISMISS_COOLDOWN_MS = 14 * DAY_MS

    fun evaluate(
        trigger: ProPromptTrigger,
        screen: ProPromptScreen,
        settings: ProPromptSettings,
        history: ProPromptHistory,
        ctx: ProPromptContext,
    ): Boolean {
        if (!settings.enabled || ctx.isPro) return false
        if (screen != trigger.screen) return false
        if (history.sessionCount <= 1) return false
        // An interstitial already shown this session, or another auto prompt, ends it.
        if (ctx.autoPromptShownThisSession || ctx.interstitialShownThisSession) return false
        if (within(ctx.nowMs, history.lastHardGateAt, HARD_GATE_COOLDOWN_MS)) return false
        return when (trigger) {
            ProPromptTrigger.BILL_MILESTONE ->
                !ctx.hasRemoveAds &&
                    history.sessionCount >= settings.minSession.coerceAtLeast(ProPromptSettings.MIN_SESSION_FLOOR) &&
                    history.installAgeDays >= SHEET_MIN_INSTALL_DAYS &&
                    history.sheetDismissals < MAX_SHEET_DISMISSALS &&
                    !within(ctx.nowMs, history.lastSheetAt, SHEET_COOLDOWN_MS) &&
                    history.billMilestones >= settings.billMilestones.coerceIn(ProPromptSettings.BILL_MILESTONES_RANGE)
            ProPromptTrigger.MARKED_DAYS_MILESTONE ->
                history.sessionCount >= CARD_MIN_SESSION &&
                    (history.cardDismissals[trigger] ?: 0) < MAX_CARD_DISMISSALS &&
                    !within(ctx.nowMs, history.lastCardAt, CARD_COOLDOWN_MS) &&
                    !within(ctx.nowMs, history.cardDismissedAt[trigger], CARD_DISMISS_COOLDOWN_MS) &&
                    history.distinctMarkedDays >= settings.markedDays.coerceIn(ProPromptSettings.MARKED_DAYS_RANGE) &&
                    !ctx.freeLimitCardShowing
        }
    }

    /** True while less than [windowMs] has passed since [at]. */
    private fun within(now: Long, at: Long?, windowMs: Long) = at != null && now - at < windowMs
}
