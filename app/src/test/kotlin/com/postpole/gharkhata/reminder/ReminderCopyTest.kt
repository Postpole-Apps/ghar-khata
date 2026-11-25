package com.postpole.gharkhata.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.domain.DueProvider
import com.postpole.gharkhata.domain.PrevBillsCue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.YearMonth

class ReminderCopyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val texts = object : ReminderTexts {
        override val defaultTitle = "Mark today"
        override val defaultText = "fallback"
        override fun billsTitle(monthShort: String) = "$monthShort bills ready"
        override fun billsOne(providerName: String, monthLabel: String) = "$providerName's $monthLabel bill"
        override fun billsMany(count: Int, monthLabel: String) = "$count bills from $monthLabel"
        override fun scheduled(count: Int) = "$count scheduled"
    }

    private val today = 20_000L
    private val oct = YearMonth.of(2026, 10)
    private fun cue(vararg names: String) =
        PrevBillsCue(oct, names.mapIndexed { i, n -> DueProvider(i + 1L, n) })

    private fun select(
        isPro: Boolean = true,
        permission: Boolean = true,
        lastOpen: Long? = null,
        scheduled: Int = 0,
        prev: PrevBillsCue? = null,
    ) = ReminderCopy.select(
        isPro, permission, today, lastOpen, scheduled, prev, texts,
        monthShort = { "Oct" },
        monthLabel = { "October 2026" },
    )

    @Test fun skipsWhenNotPro() = assertEquals(ReminderCopyResult.Skip, select(isPro = false))

    @Test fun skipsWithoutPermission() = assertEquals(ReminderCopyResult.Skip, select(permission = false))

    @Test fun skipsWhenAlreadyOpenedToday() =
        assertEquals(ReminderCopyResult.Skip, select(lastOpen = today, scheduled = 2, prev = cue("Ram")))

    @Test fun showsWhenOpenedYesterday() =
        assertEquals(ReminderCopyResult.Show("Mark today", "2 scheduled"), select(lastOpen = today - 1, scheduled = 2))

    @Test fun billsCopyOneProvider() =
        assertEquals(ReminderCopyResult.Show("Oct bills ready", "Ram's October 2026 bill"), select(prev = cue("Ram"), scheduled = 3))

    @Test fun billsCopyManyProvidersHasCountOnly() =
        assertEquals(
            ReminderCopyResult.Show("Oct bills ready", "2 bills from October 2026"),
            select(prev = cue("Ram", "Sita")),
        )

    @Test fun noDueProvidersFallsBackToScheduled() =
        assertEquals(ReminderCopyResult.Show("Mark today", "4 scheduled"), select(prev = null, scheduled = 4))

    @Test fun noScheduledUsesExistingFallback() =
        assertEquals(ReminderCopyResult.Show("Mark today", "fallback"), select())

    @Test fun settingsPersistLastTodayOpen() = runBlocking {
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { File(tmp.root, "s.preferences_pb") })
        assertNull(settings.lastTodayOpenEpochDay.first())
        settings.setLastTodayOpenEpochDay(today)
        assertEquals(today, settings.lastTodayOpenEpochDay.first())
    }
}
