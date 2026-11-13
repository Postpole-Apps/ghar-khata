package com.postpole.gharkhata.domain

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.RateChangeEntity
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Attendance and bills as one CSV. Column names are fixed English (a spreadsheet format, not UI copy):
 *
 *     record,provider,period,date,status,qty,rate,amount,billed,previous_balance,due,paid,paid_on,note
 *
 * `attendance` rows: one per day from the provider's start to today, with `status` one of
 * present/absent/half/extra/off (not scheduled) and the quantity delivered. `rate` is the rate in effect that day (rupees; the monthly salary for
 * monthly-fixed providers) and `amount` what that day adds to the bill before paid leaves and
 * deductions (qty x rate per unit, the rate per visit, one scheduled day's share of the salary for
 * monthly-fixed); both are empty when they do not apply. `bill` rows: one per month,
 * with amounts in rupees (two decimals), `paid`/`paid_on` when a settlement exists. Rows with quotes,
 * commas or line breaks are escaped per RFC 4180; lines end in CRLF.
 */
object AttendanceCsv {
    const val HEADER = "record,provider,period,date,status,qty,rate,amount,billed,previous_balance,due,paid,paid_on,note"

    fun build(
        providers: List<ProviderEntity>,
        rates: List<RateChangeEntity>,
        marks: List<DayMarkEntity>,
        settlements: List<MonthSettlementEntity>,
        today: Long,
    ): String = buildString {
        append(HEADER).append("\r\n")
        for (p in providers.sortedWith(compareBy({ it.sortOrder }, { it.id }))) {
            val pRates = rates.filter { it.providerId == p.id }
            val pMarks = marks.filter { it.providerId == p.id }
            val pSettlements = settlements.filter { it.providerId == p.id }
            val markByDay = pMarks.associateBy { it.epochDay }
            val sortedRates = pRates.sortedBy { it.effectiveFromEpochDay }

            for (d in p.startEpochDay..today) {
                val mark = markByDay[d]
                val scheduled = Schedule.isScheduled(p.scheduleMask, d)
                val status = when (mark?.status) {
                    null -> if (scheduled) "present" else "off"
                    DayStatus.PRESENT -> "present"
                    DayStatus.ABSENT -> "absent"
                    DayStatus.HALF -> "half"
                    DayStatus.EXTRA -> "extra"
                }
                val qty = when {
                    status == "absent" || status == "off" -> ""
                    status == "half" -> "0.5"
                    else -> qtyText(mark?.qty ?: p.defaultQty)
                }
                val rate = sortedRates.lastOrNull { it.effectiveFromEpochDay <= d }?.amountPaise
                row(
                    "attendance", p.name, period(d), LocalDate.ofEpochDay(d).toString(), status, qty,
                    rate?.let { rupees(it) }.orEmpty(), rate?.let { dayAmount(p, it, d, status, mark?.qty) }.orEmpty(),
                    "", "", "", "", "", mark?.note.orEmpty(),
                )
            }

            var m = YearMonth.from(LocalDate.ofEpochDay(p.startEpochDay))
            val end = YearMonth.from(LocalDate.ofEpochDay(today))
            while (m <= end) {
                val code = m.year * 100 + m.monthValue
                val bill = BillCalculator.compute(p, pRates, pMarks, code, today, pSettlements)
                val s = pSettlements.firstOrNull { it.yearMonth == code }
                row(
                    "bill", p.name, "%04d-%02d".format(m.year, m.monthValue), "", "", "", "", "",
                    rupees(bill.totalPaise), rupees(bill.previousBalancePaise), rupees(bill.duePaise),
                    s?.let { rupees(it.paidPaise) }.orEmpty(),
                    s?.let { LocalDate.ofEpochDay(it.paidOnEpochDay).toString() }.orEmpty(),
                    s?.note.orEmpty(),
                )
                m = m.plusMonths(1)
            }
        }
    }

    /** What one day adds to the bill (rupees), or "" when nothing is billed that day. */
    private fun dayAmount(p: ProviderEntity, ratePaise: Long, epochDay: Long, status: String, markQty: Double?): String {
        if (status == "absent" || status == "off") return ""
        val share = if (status == "half") BigDecimal("0.5") else BigDecimal.ONE
        val rate = BigDecimal(ratePaise)
        val paise = when (p.billingMode) {
            BillingMode.PER_UNIT -> BigDecimal.valueOf(if (status == "half") 0.5 else markQty ?: p.defaultQty) * rate
            BillingMode.PER_VISIT -> share * rate
            BillingMode.MONTHLY_FIXED -> {
                val ym = YearMonth.from(LocalDate.ofEpochDay(epochDay))
                val scheduled = Schedule.countScheduled(p.scheduleMask, ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
                if (scheduled == 0 || !Schedule.isScheduled(p.scheduleMask, epochDay)) return ""
                share * rate.divide(BigDecimal(scheduled), MathContext(30, RoundingMode.HALF_EVEN))
            }
        }
        return rupees(paise.setScale(0, RoundingMode.HALF_UP).longValueExact())
    }

    private fun StringBuilder.row(vararg cells: String) {
        append(cells.joinToString(",") { escape(it) }).append("\r\n")
    }

    private fun period(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { "%04d-%02d".format(it.year, it.monthValue) }

    private fun qtyText(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

    /** 12345 paise -> "123.45"; negatives keep the sign. */
    internal fun rupees(paise: Long): String {
        val a = kotlin.math.abs(paise)
        return (if (paise < 0) "-" else "") + "${a / 100}." + (a % 100).toString().padStart(2, '0')
    }

    internal fun escape(cell: String): String {
        // Cells starting with = + @ can run as formulas when opened in a spreadsheet; names and notes are free text.
        // (A leading minus is left alone: negative amounts are real numbers here.)
        val safe = if (cell.isNotEmpty() && cell[0] in "=+@" ) "'$cell" else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
