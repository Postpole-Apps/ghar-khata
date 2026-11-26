package com.postpole.gharkhata.ui.month

import com.postpole.gharkhata.R
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.domain.YearSummary
import java.time.YearMonth
import java.util.Locale

/**
 * Plain-text year summary for ONE provider (Pro share). Never takes or prints other providers
 * and never adds a household total.
 */
class YearTextFormatter(private val strings: StringSource, private val locale: Locale = Locale.getDefault()) {

    private fun s(resId: Int, vararg args: Any) = strings.get(resId, args)

    fun format(providerName: String, summary: YearSummary): String = buildString {
        appendLine(s(R.string.yeartext_title, providerName, summary.year))
        appendLine()
        if (summary.months.isEmpty()) {
            appendLine(s(R.string.yeartext_empty))
        } else {
            appendLine(s(R.string.yeartext_billed, summary.year, Money.format(summary.totalBilledPaise)))
            appendLine(s(R.string.yeartext_paid, Money.format(summary.totalPaidPaise)))
            appendLine(s(R.string.yeartext_outstanding, Money.format(summary.outstandingPaise)))
            appendLine()
            for (m in summary.months) {
                val name = BillTextFormatter.monthName(YearMonth.of(m.yearMonth / 100, m.yearMonth % 100), locale)
                val paid = m.paidPaise
                appendLine(
                    if (paid != null) s(R.string.yeartext_month_paid, name, Money.format(m.billedPaise), Money.format(paid))
                    else s(R.string.yeartext_month, name, Money.format(m.billedPaise)),
                )
            }
        }
        appendLine()
        append(s(R.string.billtext_footer))
    }
}
