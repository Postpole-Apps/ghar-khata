package com.postpole.gharkhata.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

class ReminderControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeScheduler : ReminderScheduling {
        val scheduled = mutableListOf<Int>()
        var cancelled = 0
        override fun schedule(minutesOfDay: Int) {
            scheduled += minutesOfDay
        }
        override fun cancel() {
            cancelled++
        }
    }

    private lateinit var settings: SettingsRepository
    private val scheduler = FakeScheduler()
    private var pro = false

    @Before
    fun setUp() {
        settings = SettingsRepository(PreferenceDataStoreFactory.create { File(tmp.root, "s.preferences_pb") })
    }

    private fun controller() = ReminderController(settings, scheduler) { pro }

    @Test
    fun nonPro_enableDoesNothing() = runBlocking {
        assertFalse(controller().enable(8 * 60))
        assertNull(settings.reminderMinutes.first())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun pro_enableStoresTimeAndSchedules() = runBlocking {
        pro = true
        assertTrue(controller().enable(7 * 60 + 30))
        assertEquals(450, settings.reminderMinutes.first())
        assertEquals(listOf(450), scheduler.scheduled)
    }

    @Test
    fun disable_clearsTimeAndCancels() = runBlocking {
        pro = true
        controller().enable(600)
        controller().disable()
        assertNull(settings.reminderMinutes.first())
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun initialDelay_isTodayIfAheadElseTomorrow() {
        val now = LocalDateTime.of(2026, 11, 13, 9, 0)
        assertEquals(60 * 60_000L, ReminderScheduler.initialDelayMillis(10 * 60, now))
        assertEquals(23 * 60 * 60_000L, ReminderScheduler.initialDelayMillis(8 * 60, now))
        assertEquals(24 * 60 * 60_000L, ReminderScheduler.initialDelayMillis(9 * 60, now))
    }
}
