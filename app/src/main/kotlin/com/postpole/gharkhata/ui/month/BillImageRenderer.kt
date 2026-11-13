package com.postpole.gharkhata.ui.month

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** One row of the card: a label on the left, an amount (or count) on the right. */
data class BillCardLine(val label: String, val value: String, val bold: Boolean = false)

/** Everything the bill card prints, already formatted. Built by [BillCardBuilder]. */
data class BillCardData(
    val providerName: String,
    val providerType: String,
    val periodLabel: String,
    val dueText: String,
    val dueCaption: String,
    val lines: List<BillCardLine>,
    val totals: List<BillCardLine>,
    val paidStamp: String?,
    val footer: String,
)

/**
 * Draws the bill as a card on a plain [Canvas] (PNG for WhatsApp, or one PDF page), so it needs no view
 * hierarchy and works off the main thread. Colours are the light-theme tokens from design/mockups/styles.css.
 * Layout is arithmetic on [width], so [heightFor] is exact and the same drawing serves bitmap and PDF.
 */
object BillImageRenderer {
    private const val BASE_WIDTH = 1080f
    private const val HERO = 0xFF1E3527.toInt()
    private const val ON_HERO = 0xFFDCF8E2.toInt()
    private const val ON_HERO_VARIANT = 0xFFC0DBC6.toInt()
    private const val LIME = 0xFFCDEA2B.toInt()
    private const val ON_LIME = 0xFF222900.toInt()
    private const val SURFACE = 0xFFF8FBE4.toInt()
    private const val CARD = 0xFFE7EAD4.toInt()
    private const val INK = 0xFF191D0F.toInt()
    private const val INK_VARIANT = 0xFF3F4A38.toInt()
    private const val SAFFRON = 0xFF944A00.toInt()

    private const val HERO_H = 420f
    private const val ROW_H = 96f
    private const val PAD = 56f
    private const val GAP = 32f

    fun heightFor(data: BillCardData, width: Int): Int {
        val s = width / BASE_WIDTH
        val rows = data.lines.size + data.totals.size
        val stamp = if (data.paidStamp != null) 120f else 0f
        return ((HERO_H + 48f + rows * ROW_H + GAP + stamp + 150f) * s).toInt()
    }

    fun toBitmap(data: BillCardData, width: Int = 1080): Bitmap {
        val bitmap = Bitmap.createBitmap(width, heightFor(data, width), Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), data, width)
        return bitmap
    }

    /** One page sized to the card (A4-like width: 595 pt). */
    fun writePdf(data: BillCardData, out: OutputStream) {
        val width = 595
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(width, heightFor(data, width), 1).create())
            draw(page.canvas, data, width)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun draw(canvas: Canvas, data: BillCardData, width: Int) {
        val s = width / BASE_WIDTH
        canvas.save()
        canvas.scale(s, s)
        val w = BASE_WIDTH
        val h = heightFor(data, width) / s
        canvas.drawColor(SURFACE)

        // Hero block with rounded bottom corners
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HERO }
        canvas.drawRoundRect(RectF(0f, -64f, w, HERO_H), 64f, 64f, fill)
        canvas.drawText(
            data.providerName.ellipsize(26),
            PAD, 108f, paint(ON_HERO, 56f, bold = true),
        )
        canvas.drawText(data.providerType + " · " + data.periodLabel, PAD, 164f, paint(ON_HERO_VARIANT, 36f))
        canvas.drawText(data.dueText, PAD, 310f, paint(LIME, 132f, bold = true))
        canvas.drawText(data.dueCaption, PAD, 372f, paint(ON_HERO_VARIANT, 34f))

        var y = HERO_H + 48f
        // First line is the lime highlight card (as on the bill preview).
        data.lines.forEachIndexed { i, line ->
            if (i == 0) {
                canvas.drawRoundRect(RectF(PAD / 2, y, w - PAD / 2, y + ROW_H - 8f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME })
                row(canvas, line, y, w, ON_LIME, ON_LIME)
            } else {
                canvas.drawRoundRect(RectF(PAD / 2, y, w - PAD / 2, y + ROW_H - 8f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
                row(canvas, line, y, w, INK, INK)
            }
            y += ROW_H
        }
        y += GAP
        data.totals.forEach { line ->
            row(canvas, line, y, w, INK, if (line.bold) INK else INK_VARIANT)
            y += ROW_H
        }
        if (data.paidStamp != null) {
            canvas.drawText(data.paidStamp, PAD, y + 64f, paint(SAFFRON, 40f, bold = true))
        }
        canvas.drawText(data.footer, w / 2, h - 64f, paint(INK_VARIANT, 32f, align = Paint.Align.CENTER))
        canvas.restore()
    }

    private fun row(canvas: Canvas, line: BillCardLine, top: Float, w: Float, labelColor: Int, valueColor: Int) {
        val base = top + ROW_H * 0.62f
        canvas.drawText(line.label.ellipsize(34), PAD, base, paint(labelColor, 38f, bold = line.bold))
        canvas.drawText(line.value, w - PAD, base, paint(valueColor, 42f, bold = true, align = Paint.Align.RIGHT))
    }

    private fun String.ellipsize(max: Int) = if (length <= max) this else take(max - 1) + "…"
}
