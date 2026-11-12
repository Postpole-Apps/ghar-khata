package com.postpole.gharkhata.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyScheduleTest {
    @Test fun formatsEnInGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,234", Money.format(123_400))
        assertEquals("₹12,345", Money.format(1_234_500))
        assertEquals("₹1,23,456", Money.format(12_345_600))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("-₹1,234", Money.format(-123_400))
    }

    @Test fun displayRoundsHalfUp() {
        assertEquals(150L, Money.toDisplayRupees(14_950))
        assertEquals(149L, Money.toDisplayRupees(14_949))
        assertEquals("₹150", Money.format(14_950))
    }

    @Test fun paiseFormatAndParse() {
        assertEquals("₹1,234.05", Money.formatWithPaise(123_405))
        assertEquals(123_450L, Money.parse("₹1,234.5"))
        assertEquals(12_345_600L, Money.parse("1,23,456"))
        assertNull(Money.parse("abc"))
        assertEquals(5_000L, Money.fromRupees(50))
    }

    @Test fun scheduleBits() {
        val mon = LocalDate.of(2026, 11, 2).toEpochDay()
        val sun = LocalDate.of(2026, 11, 1).toEpochDay()
        assertEquals(0, Schedule.weekdayIndex(mon))
        assertEquals(6, Schedule.weekdayIndex(sun))
        assertTrue(Schedule.isScheduled(Schedule.MON, mon))
        assertFalse(Schedule.isScheduled(Schedule.MON, sun))
        assertTrue(Schedule.isScheduled(Schedule.SUN, sun))
        assertFalse(Schedule.isScheduled(0, mon))
    }

    @Test fun countScheduledInRange() {
        val from = LocalDate.of(2026, 11, 1).toEpochDay()
        val to = LocalDate.of(2026, 11, 30).toEpochDay()
        assertEquals(21, Schedule.countScheduled(Schedule.WEEKDAYS, from, to))
        assertEquals(30, Schedule.countScheduled(Schedule.ALL_DAYS, from, to))
        assertEquals(0, Schedule.countScheduled(0, from, to))
        assertEquals(5, Schedule.countScheduled(Schedule.MON, from, to))
    }
}
