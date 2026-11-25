package com.postpole.gharkhata.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.postpole.gharkhata.MainActivity
import com.postpole.gharkhata.R
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.domain.PreviousMonthBills
import com.postpole.gharkhata.domain.ProviderBillInput
import com.postpole.gharkhata.ui.month.BillTextFormatter
import com.postpole.gharkhata.ui.today.TodayViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

/** Posts the local "mark today" reminder. Tapping it opens the app on Today, on cold and warm starts (via [MainActivity.EXTRA_GO_TO_TODAY]). */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val billing: BillingRepository,
    private val providers: ProviderRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // Checked below.
    override suspend fun doWork(): Result {
        // Pro refunded or revoked since scheduling: stay quiet.
        if (!billing.isPro.value) return Result.success()
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()

        val today = LocalDate.now().toEpochDay()
        val active = providers.observeActiveWithRates().first()
        val prevBills = if (LocalDate.ofEpochDay(today).dayOfMonth in PreviousMonthBills.WINDOW_DAYS) {
            PreviousMonthBills.compute(
                today,
                active.map { pr ->
                    ProviderBillInput(
                        pr,
                        providers.observeMarks(pr.provider.id, pr.provider.startEpochDay, today).first(),
                        providers.observeSettlements(pr.provider.id).first(),
                    )
                },
            )
        } else {
            null
        }
        val locale = Locale.getDefault()
        val texts = object : ReminderTexts {
            override val defaultTitle = context.getString(R.string.reminder_notification_title)
            override val defaultText = context.getString(R.string.reminder_notification_text)
            override fun billsTitle(monthShort: String) =
                context.getString(R.string.reminder_notification_title_bills, monthShort)
            override fun billsOne(providerName: String, monthLabel: String) =
                context.getString(R.string.reminder_notification_text_bills_one, providerName, monthLabel)
            override fun billsMany(count: Int, monthLabel: String) =
                context.getString(R.string.reminder_notification_text_bills_many, count, monthLabel)
            override fun scheduled(count: Int) =
                context.resources.getQuantityString(R.plurals.reminder_notification_text_scheduled, count, count)
        }
        val show = ReminderCopy.select(
            isPro = true,
            hasNotificationPermission = true,
            today = today,
            lastTodayOpenEpochDay = settings.lastTodayOpenEpochDay.first(),
            scheduledCount = active.count { TodayViewModel.isScheduledOn(it.provider, today) },
            prevBills = prevBills,
            texts = texts,
            monthShort = { BillTextFormatter.monthShort(it, locale) },
            monthLabel = { BillTextFormatter.monthLabel(it, locale) },
        ) as? ReminderCopyResult.Show ?: return Result.success()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openTodayIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ghar_khata)
            .setContentTitle(show.title)
            .setContentText(show.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
    }
}
