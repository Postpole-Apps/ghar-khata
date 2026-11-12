package com.postpole.gharkhata.domain

/** Weekly schedule bitmask helpers: bit0 = Mon … bit6 = Sun; 0 = on-demand only. */
object Schedule {
    const val MON = 1 shl 0
    const val TUE = 1 shl 1
    const val WED = 1 shl 2
    const val THU = 1 shl 3
    const val FRI = 1 shl 4
    const val SAT = 1 shl 5
    const val SUN = 1 shl 6
    const val ALL_DAYS = 0x7F
    const val WEEKDAYS = MON or TUE or WED or THU or FRI

    /** Bit index (0 = Mon … 6 = Sun) of an epoch day. 1970-01-01 was a Thursday (index 3). */
    fun weekdayIndex(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt()

    fun isScheduled(mask: Int, epochDay: Long): Boolean =
        mask and (1 shl weekdayIndex(epochDay)) != 0

    /** Number of scheduled days in the inclusive range [fromEpochDay, toEpochDay]. */
    fun countScheduled(mask: Int, fromEpochDay: Long, toEpochDay: Long): Int {
        if (mask == 0) return 0
        var n = 0
        var d = fromEpochDay
        while (d <= toEpochDay) {
            if (isScheduled(mask, d)) n++
            d++
        }
        return n
    }
}
