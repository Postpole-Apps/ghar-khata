package com.postpole.gharkhata.ads

/** What the app knows about interstitials shown so far; all times are epoch millis / epoch days. */
data class InterstitialHistory(
    /** 1 for the first app launch after install, 2 for the next, ... */
    val sessionNumber: Int,
    val installTimeMs: Long,
    val shownThisSession: Int,
    val shownToday: Int,
    val lastShownMs: Long?,
)

/** Pure cap logic for the after-bill interstitial: not first session, not first N days, interval, per session / day. */
object InterstitialPolicy {
    private const val MS_PER_DAY = 86_400_000L

    fun allows(config: AdsConfig, history: InterstitialHistory, nowMs: Long): Boolean {
        if (!config.interstitialsAllowed) return false
        if (config.interstitialBlockFirstSession && history.sessionNumber <= 1) return false
        if (nowMs - history.installTimeMs < config.interstitialMinInstallDays * MS_PER_DAY) return false
        if (history.shownThisSession >= config.interstitialMaxPerSession) return false
        if (history.shownToday >= config.interstitialMaxPerDay) return false
        val last = history.lastShownMs
        if (last != null && nowMs - last < config.interstitialMinIntervalSec * 1_000L) return false
        return true
    }
}
