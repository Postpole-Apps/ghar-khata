package com.postpole.gharkhata.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class MidnightSchedulerTest {
    private val slack = Duration.ofSeconds(5)

    @Test
    fun normalDay() {
        val now = LocalDateTime.of(2026, 3, 10, 22, 30).atZone(ZoneId.of("Asia/Kolkata"))
        assertEquals(Duration.ofMinutes(90).plus(slack), MidnightScheduler.delayUntilNextMidnight(now))
    }

    @Test
    fun justBeforeMidnight() {
        val now = LocalDateTime.of(2026, 3, 10, 23, 59, 59).atZone(ZoneId.of("Asia/Kolkata"))
        assertEquals(Duration.ofSeconds(1).plus(slack), MidnightScheduler.delayUntilNextMidnight(now))
    }

    @Test
    fun springForwardDayIsShorter() {
        // US DST starts 2026-03-08: a 23-hour day.
        val now = LocalDateTime.of(2026, 3, 8, 0, 0).atZone(ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(23).plus(slack), MidnightScheduler.delayUntilNextMidnight(now))
    }

    @Test
    fun fallBackDayIsLonger() {
        // US DST ends 2026-11-01: a 25-hour day.
        val now = LocalDateTime.of(2026, 11, 1, 0, 0).atZone(ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(25).plus(slack), MidnightScheduler.delayUntilNextMidnight(now))
    }
}
