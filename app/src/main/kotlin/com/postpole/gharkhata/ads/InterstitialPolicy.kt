package com.postpole.gharkhata.ads

/** What has already been shown. [sessionNumber] counts app process starts, 1 = first ever. */
data class InterstitialHistory(
    val sessionNumber: Int,
    val shownThisSession: Int = 0,
    val lastShownMs: Long = 0L,
    /** Local epoch day that [shownOnDay] counts for. */
    val day: Long = -1L,
    val shownOnDay: Int = 0,
) {
    fun shownToday(today: Long) = if (day == today) shownOnDay else 0

    fun recordShown(nowMs: Long, today: Long) = copy(
        shownThisSession = shownThisSession + 1,
        lastShownMs = nowMs,
        day = today,
        shownOnDay = shownToday(today) + 1,
    )
}

/** Interstitial frequency caps: not first session, not first N days, min interval, per session, per day. */
object InterstitialPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun allowed(
        settings: AdsSettings,
        history: InterstitialHistory,
        installAgeMs: Long,
        nowMs: Long,
        today: Long,
    ): Boolean = when {
        !settings.adsEnabled || !settings.interstitialEnabled -> false
        settings.interstitialBlockFirstSession && history.sessionNumber <= 1 -> false
        installAgeMs < settings.interstitialMinInstallDays * DAY_MS -> false
        history.shownThisSession >= settings.interstitialMaxPerSession -> false
        history.shownToday(today) >= settings.interstitialMaxPerDay -> false
        history.lastShownMs > 0 && nowMs - history.lastShownMs < settings.interstitialMinIntervalSec * 1000L -> false
        else -> true
    }
}
