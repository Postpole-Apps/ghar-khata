package com.postpole.gharkhata.domain

import kotlin.math.abs

/** Money helpers. All amounts are Long paise; display uses en-IN digit grouping (12,34,567). */
object Money {
    private const val RUPEE = "₹"
    private val NUMBER = Regex("""\d+(\.\d{1,2})?""")

    /** Whole rupees, rounded half-up (half away from zero for negatives). */
    fun toDisplayRupees(paise: Long): Long {
        val r = (abs(paise) + 50) / 100
        return if (paise < 0) -r else r
    }

    fun fromRupees(rupees: Long): Long = rupees * 100

    /** "₹1,234" (whole rupees). */
    fun format(paise: Long): String = formatRupees(toDisplayRupees(paise))

    /** "₹1,234" from a whole-rupee amount. */
    fun formatRupees(rupees: Long): String =
        (if (rupees < 0) "-" else "") + RUPEE + group(abs(rupees).toString())

    /** "₹1,234.50" (always two decimals). */
    fun formatWithPaise(paise: Long): String {
        val a = abs(paise)
        return (if (paise < 0) "-" else "") + RUPEE + group((a / 100).toString()) +
            "." + (a % 100).toString().padStart(2, '0')
    }

    /** Parses "₹1,234", "1234.5", "1,23,456.75" into paise; null if invalid. */
    fun parse(text: String): Long? {
        val s = text.replace(RUPEE, "").replace(",", "").trim()
        if (!NUMBER.matches(s)) return null
        val parts = s.split('.')
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').toLong() else 0L
        return parts[0].toLong() * 100 + frac
    }

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val pairs = mutableListOf<String>()
        var i = head.length
        while (i > 0) {
            pairs.add(0, head.substring(maxOf(0, i - 2), i))
            i -= 2
        }
        return pairs.joinToString(",") + "," + tail
    }
}
