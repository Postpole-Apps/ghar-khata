package com.postpole.gharkhata.ui.month

import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.domain.BillLine
import com.postpole.gharkhata.domain.BillLineKind
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.domain.MonthBill
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Resolves a string resource with format args (a `Context.getString` in the app, in tests too via Robolectric). */
fun interface StringSource {
    fun get(resId: Int, args: Array<out Any>): String
}

/**
 * Builds the plain-text bill used for sharing (ACTION_SEND and wa.me). No Android types beyond string
 * resources, so it is unit-testable. Same text goes to both channels.
 */
class BillTextFormatter(private val strings: StringSource, private val locale: Locale = Locale.getDefault()) {

    private fun s(resId: Int, vararg args: Any) = strings.get(resId, args)

    fun format(
        provider: ProviderEntity,
        bill: MonthBill,
        settlement: MonthSettlementEntity? = null,
    ): String = buildString {
        val ym = YearMonth.of(bill.yearMonth / 100, bill.yearMonth % 100)
        appendLine(s(R.string.billtext_title, provider.name, monthLabel(ym, locale)))
        appendLine()
        for (line in bill.lines) lineText(provider, line)?.let { appendLine(it) }
        appendLine()
        appendLine(s(R.string.billtext_total, monthName(ym, locale), money(bill.totalPaise)))
        if (bill.previousBalancePaise != 0L) {
            appendLine(s(R.string.billtext_previous, money(bill.previousBalancePaise)))
        }
        appendLine(s(R.string.billtext_due, money(bill.duePaise)))
        if (settlement != null) {
            appendLine(s(R.string.billtext_paid_on, dateLabel(settlement.paidOnEpochDay, locale), money(settlement.paidPaise)))
            val left = settlement.billedPaise - settlement.paidPaise
            if (left != 0L) appendLine(s(R.string.billtext_balance_left, money(left)))
        }
        appendLine()
        append(s(R.string.billtext_footer))
    }

    private fun lineText(p: ProviderEntity, line: BillLine): String? = when (line.kind) {
        BillLineKind.RATE_SEGMENT -> {
            val range = rangeLabel(line.fromEpochDay!!, line.toEpochDay!!, locale)
            when (p.billingMode) {
                BillingMode.PER_UNIT -> s(
                    R.string.billtext_segment_unit,
                    range, "${qty(line.quantity)} ${p.unitLabel}", money(line.ratePaise), money(line.amountPaise),
                )
                BillingMode.PER_VISIT -> s(
                    R.string.billtext_segment_visit, range, qty(line.quantity), money(line.ratePaise), money(line.amountPaise),
                )
                BillingMode.MONTHLY_FIXED -> s(
                    R.string.billtext_segment_monthly, range, qty(line.quantity), money(line.ratePaise), money(line.amountPaise),
                )
            }
        }
        BillLineKind.ABSENCES -> s(R.string.billtext_absences, qty(line.quantity))
        BillLineKind.PAID_LEAVES ->
            if (line.amountPaise != 0L) s(R.string.billtext_paid_leaves_amount, qty(line.quantity), money(line.amountPaise))
            else s(R.string.billtext_paid_leaves, qty(line.quantity))
        BillLineKind.DEDUCTION -> s(R.string.billtext_deduction, qty(line.quantity), money(line.amountPaise))
        BillLineKind.PREVIOUS_BALANCE -> null // printed after the month total
    }

    companion object {
        /** "₹870", or "₹870.50" when there are paise. */
        fun money(paise: Long): String = if (paise % 100 == 0L) Money.format(paise) else Money.formatWithPaise(paise)

        fun qty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

        fun monthLabel(ym: YearMonth, locale: Locale): String =
            ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

        /** "Oct". */
        fun monthShort(ym: YearMonth, locale: Locale): String =
            ym.format(DateTimeFormatter.ofPattern("MMM", locale))

        fun monthName(ym: YearMonth, locale: Locale): String =
            ym.format(DateTimeFormatter.ofPattern("MMMM", locale))

        fun dateLabel(epochDay: Long, locale: Locale): String =
            LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

        /** "1–15 Oct" within a month, "28 Oct – 3 Nov" across months. */
        fun rangeLabel(from: Long, to: Long, locale: Locale): String {
            val a = LocalDate.ofEpochDay(from)
            val b = LocalDate.ofEpochDay(to)
            val mon = DateTimeFormatter.ofPattern("MMM", locale)
            return if (a.month == b.month && a.year == b.year) {
                if (a == b) "${a.dayOfMonth} ${a.format(mon)}" else "${a.dayOfMonth}–${b.dayOfMonth} ${b.format(mon)}"
            } else {
                "${a.dayOfMonth} ${a.format(mon)} – ${b.dayOfMonth} ${b.format(mon)}"
            }
        }

        /**
         * Digits-only number for `https://wa.me/<digits>`. A typed country code is kept; a bare 10-digit
         * number gets 91; a leading 0 (trunk prefix) or 00 (international prefix) is dropped. Null when
         * there are too few or too many digits to be a phone number.
         */
        fun whatsAppNumber(phone: String?): String? {
            var d = phone.orEmpty().filter { it.isDigit() }
            if (d.startsWith("00")) d = d.drop(2) else if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
            return when {
                d.length == 10 -> "91$d"
                d.length in 11..15 -> d
                else -> null
            }
        }

        /** `https://wa.me/<digits>?text=<encoded>`; null when [phone] is unusable. */
        fun whatsAppUrl(phone: String?, text: String): String? {
            val number = whatsAppNumber(phone) ?: return null
            return "https://wa.me/$number?text=" + java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        }
    }
}
