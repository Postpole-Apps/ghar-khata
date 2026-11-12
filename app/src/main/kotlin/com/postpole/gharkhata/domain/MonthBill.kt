package com.postpole.gharkhata.domain

enum class BillLineKind {
    /** A stretch of days at one rate. quantity = units (PER_UNIT) / visits (PER_VISIT) / scheduled days (MONTHLY_FIXED). */
    RATE_SEGMENT,

    /** Informational: absent days in the billed range (amountPaise = 0). */
    ABSENCES,

    /** PER_VISIT: absences credited as paid leave (amount added). MONTHLY_FIXED: informational (amount 0). */
    PAID_LEAVES,

    /** MONTHLY_FIXED: negative amount for absences beyond the paid-leave allowance. */
    DEDUCTION,

    PREVIOUS_BALANCE,
}

data class BillLine(
    val kind: BillLineKind,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    /** Rate / salary in paise for RATE_SEGMENT; 0 otherwise. */
    val ratePaise: Long = 0,
    /** Units, visits or days depending on [kind]. */
    val quantity: Double = 0.0,
    /** Signed amount in paise (rounded half-up). */
    val amountPaise: Long = 0,
)

data class MonthBill(
    val yearMonth: Int,
    val lines: List<BillLine>,
    /** This month's bill in paise, excluding previous balance. */
    val totalPaise: Long,
    val previousBalancePaise: Long,
    /** totalPaise + previousBalancePaise. */
    val duePaise: Long,
) {
    val totalDisplayRupees: Long get() = Money.toDisplayRupees(totalPaise)
    val previousBalanceDisplayRupees: Long get() = Money.toDisplayRupees(previousBalancePaise)
    val dueDisplayRupees: Long get() = Money.toDisplayRupees(duePaise)
}
