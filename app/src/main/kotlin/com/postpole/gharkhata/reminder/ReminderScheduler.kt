package com.postpole.gharkhata.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules or cancels the daily reminder. Faked in tests. */
interface ReminderScheduling {
    fun schedule(minutesOfDay: Int)
    fun cancel()
}

/**
 * Daily reminder via WorkManager periodic work. WorkManager runs it inexactly (the system may
 * shift it a little), which is fine for a gentle nudge and needs no exact-alarm permission.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduling {
    override fun schedule(minutesOfDay: Int) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(minutesOfDay, LocalDateTime.now()), TimeUnit.MILLISECONDS)
            .build()
        // UPDATE so a changed time replaces the old schedule.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily_reminder"

        /** Milliseconds from [now] until the next occurrence of [minutesOfDay] (today if still ahead). */
        fun initialDelayMillis(minutesOfDay: Int, now: LocalDateTime): Long {
            var next = now.toLocalDate().atTime(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }
    }
}
