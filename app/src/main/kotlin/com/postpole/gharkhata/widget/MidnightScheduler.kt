package com.postpole.gharkhata.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Redraws the widget just after local midnight. Manifest receivers no longer get DATE_CHANGED on
 * Android 8+, so an inexact alarm (no permission needed) to the widget receiver does the job.
 */
object MidnightScheduler {
    const val ACTION_MIDNIGHT = "com.postpole.gharkhata.widget.ACTION_MIDNIGHT"
    private const val WINDOW_MS = 10 * 60 * 1000L
    private const val SLACK_SECONDS = 5L

    /** Time from [now] until the start of the next local day (DST-aware), plus a few seconds of slack. */
    fun delayUntilNextMidnight(now: ZonedDateTime): Duration {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, next).plusSeconds(SLACK_SECONDS)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_MIDNIGHT).setComponent(ComponentName(context, TodayWidgetReceiver::class.java)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Arms (replaces) the next-midnight alarm. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val delay = delayUntilNextMidnight(ZonedDateTime.now(ZoneId.systemDefault()))
        val triggerAt = System.currentTimeMillis() + delay.toMillis()
        alarms.setWindow(AlarmManager.RTC, triggerAt, WINDOW_MS, pendingIntent(app))
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(app))
    }
}
