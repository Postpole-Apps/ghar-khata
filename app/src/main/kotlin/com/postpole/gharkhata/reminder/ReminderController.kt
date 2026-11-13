package com.postpole.gharkhata.reminder

import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Applies the Pro rule to the daily reminder: stores the time and (un)schedules the work. */
@Singleton
class ReminderController internal constructor(
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduling,
    private val isPro: suspend () -> Boolean,
) {
    @Inject
    constructor(
        settings: SettingsRepository,
        scheduler: ReminderScheduling,
        billing: BillingRepository,
    ) : this(settings, scheduler, { billing.isProStored() })

    /** Returns false (and does nothing) when the user is not Pro. */
    suspend fun enable(minutesOfDay: Int): Boolean {
        if (!isPro()) return false
        settings.setReminderMinutes(minutesOfDay)
        scheduler.schedule(minutesOfDay)
        return true
    }

    suspend fun disable() {
        settings.setReminderMinutes(null)
        scheduler.cancel()
    }

    /**
     * At app start: keep the schedule in line with the stored time. Without Pro the schedule is
     * cancelled (the stored time is kept, so buying Pro again restores the reminder).
     */
    suspend fun reconcile() {
        val minutes = settings.reminderMinutes.first()
        if (minutes == null || !isPro()) scheduler.cancel() else scheduler.schedule(minutes)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    abstract fun bindScheduling(impl: ReminderScheduler): ReminderScheduling
}
