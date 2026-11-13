package com.postpole.gharkhata.ui.month

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.domain.BillLineKind
import com.postpole.gharkhata.domain.MonthBill
import com.postpole.gharkhata.ui.provider.labelRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.util.Locale

/** Builds the formatted rows the Pro bill image / PDF prints. Pure apart from string lookups. */
class BillCardBuilder(private val strings: StringSource, private val locale: Locale = Locale.getDefault()) {

    private fun s(resId: Int, vararg args: Any) = strings.get(resId, args)

    fun build(provider: ProviderEntity, bill: MonthBill, settlement: MonthSettlementEntity?): BillCardData {
        val ym = YearMonth.of(bill.yearMonth / 100, bill.yearMonth % 100)
        val money = BillTextFormatter::money
        val qty = BillTextFormatter::qty
        val lines = bill.lines.mapNotNull { line ->
            when (line.kind) {
                BillLineKind.RATE_SEGMENT -> {
                    val range = BillTextFormatter.rangeLabel(line.fromEpochDay!!, line.toEpochDay!!, locale)
                    val label = when (provider.billingMode) {
                        BillingMode.PER_UNIT -> s(R.string.bill_line_segment_unit, range, "${qty(line.quantity)} ${provider.unitLabel}", money(line.ratePaise))
                        BillingMode.PER_VISIT -> s(R.string.bill_line_segment_visit, range, qty(line.quantity), money(line.ratePaise))
                        BillingMode.MONTHLY_FIXED -> s(R.string.bill_line_segment_monthly, range, qty(line.quantity), money(line.ratePaise))
                    }
                    BillCardLine(label, money(line.amountPaise))
                }
                BillLineKind.ABSENCES -> BillCardLine(s(R.string.bill_line_absences), qty(line.quantity))
                BillLineKind.PAID_LEAVES ->
                    BillCardLine(s(R.string.bill_line_paid_leaves), if (line.amountPaise != 0L) money(line.amountPaise) else qty(line.quantity))
                BillLineKind.DEDUCTION -> BillCardLine(s(R.string.bill_line_deduction, qty(line.quantity)), money(line.amountPaise))
                BillLineKind.PREVIOUS_BALANCE -> null
            }
        }
        val totals = buildList {
            add(BillCardLine(s(R.string.bill_month_total, BillTextFormatter.monthName(ym, locale)), money(bill.totalPaise)))
            add(BillCardLine(s(R.string.bill_previous_balance), money(bill.previousBalancePaise)))
            add(BillCardLine(s(R.string.bill_due), money(bill.duePaise), bold = true))
        }
        return BillCardData(
            providerName = provider.name,
            providerType = s(provider.type.labelRes()),
            periodLabel = BillTextFormatter.monthLabel(ym, locale),
            dueText = money(bill.duePaise),
            dueCaption = s(R.string.bill_total_due_range, BillTextFormatter.monthLabel(ym, locale)),
            lines = lines,
            totals = totals,
            paidStamp = settlement?.let {
                s(R.string.bill_paid_stamp, BillTextFormatter.dateLabel(it.paidOnEpochDay, locale), money(it.paidPaise))
            },
            footer = s(R.string.billtext_footer),
        )
    }
}

enum class BillFileFormat(val extension: String, val mime: String, val analytics: String) {
    IMAGE("png", "image/png", "image"),
    PDF("pdf", "application/pdf", "pdf"),
}

/**
 * Renders [data] to a file in the app cache and hands it to the share sheet through FileProvider.
 * No storage permission; the receiving app only gets read access to that one file. Returns false if no
 * app could handle the share.
 */
suspend fun shareBillFile(context: Context, data: BillCardData, format: BillFileFormat, name: String): Boolean {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only the newest few files are kept; they are regenerated on every share.
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(8)?.forEach { it.delete() }
        File(dir, "$name.${format.extension}").also { f ->
            f.outputStream().use { out ->
                when (format) {
                    BillFileFormat.IMAGE -> {
                        val bmp = BillImageRenderer.toBitmap(data)
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        bmp.recycle()
                    }
                    BillFileFormat.PDF -> BillImageRenderer.writePdf(data, out)
                }
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = format.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.bill_share_chooser)).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
