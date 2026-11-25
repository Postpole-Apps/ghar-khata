package com.postpole.gharkhata.reminder

import com.postpole.gharkhata.domain.PrevBillsCue
import java.time.YearMonth

/** Resolves the reminder's strings (backed by Android resources in the worker, faked in tests). */
interface ReminderTexts {
    val defaultTitle: String
    val defaultText: String
    fun billsTitle(monthShort: String): String
    fun billsOne(providerName: String, monthLabel: String): String
    fun billsMany(count: Int, monthLabel: String): String
    fun scheduled(count: Int): String
}

sealed interface ReminderCopyResult {
    data object Skip : ReminderCopyResult
    data class Show(val title: String, val text: String) : ReminderCopyResult
}

/** Chooses what the single daily reminder says, or whether to stay quiet. Never includes an amount. */
object ReminderCopy {
    fun select(
        isPro: Boolean,
        hasNotificationPermission: Boolean,
        today: Long,
        lastTodayOpenEpochDay: Long?,
        scheduledCount: Int,
        /** Non-null only on days 1–5 with at least one positive previous-month carry (see PreviousMonthBills). */
        prevBills: PrevBillsCue?,
        texts: ReminderTexts,
        monthShort: (YearMonth) -> String,
        monthLabel: (YearMonth) -> String,
    ): ReminderCopyResult {
        if (!isPro || !hasNotificationPermission || lastTodayOpenEpochDay == today) return ReminderCopyResult.Skip
        if (prevBills != null && prevBills.providers.isNotEmpty()) {
            val month = prevBills.previousMonth
            val text = if (prevBills.providers.size == 1) {
                texts.billsOne(prevBills.providers.first().name, monthLabel(month))
            } else {
                texts.billsMany(prevBills.providers.size, monthLabel(month))
            }
            return ReminderCopyResult.Show(texts.billsTitle(monthShort(month)), text)
        }
        val text = if (scheduledCount > 0) texts.scheduled(scheduledCount) else texts.defaultText
        return ReminderCopyResult.Show(texts.defaultTitle, text)
    }
}
