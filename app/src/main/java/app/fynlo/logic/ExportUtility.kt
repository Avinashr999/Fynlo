package app.fynlo.logic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.fynlo.BuildConfig
import app.fynlo.data.model.*
import app.fynlo.ui.screens.FlowEntry
import app.fynlo.ui.screens.FlowType
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * PDF export using Android's native PdfDocument — no third-party dependencies.
 * Replaces iText7 (which was AGPL-licensed and incompatible with closed-source apps).
 *
 * Page size: A4 at 72 DPI = 595 × 842 points
 */
object ExportUtility {

    /**
     * C21 Stage 1 (3.2.35) — standardized filename pattern per audit §C21 #2.
     * Replaces the prior `report_<epoch>.pdf` / `report_<date>.pdf` ad-hoc
     * names with a consistent `Fynlo_<ReportType>_<yyyy-MM-dd>_<Subject>.<ext>`
     * shape. Subject is sanitized to alphanumeric + underscore so filenames
     * survive Windows / Android Files / Drive / email attachments without
     * URL-encoding artefacts.
     *
     * Examples:
     *  - `Fynlo_Report_2026-05-27_Personal.pdf`
     *  - `Fynlo_LoanStatement_2026-05-27_Mohan_Rao.pdf`
     *  - `Fynlo_MoneyFlow_2026-05-27_Personal.csv`
     */
    fun filename(reportType: String, subject: String, ext: String): String {
        val safeSubject = subject.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "Untitled" }
        val date = LocalDate.now()
        return "Fynlo_${reportType}_${date}_$safeSubject.$ext"
    }

    /**
     * C21 Stage 1 — header info row rendered at the top of every PDF cover.
     * Audit §C21 #8 + #9: "Project: ... | User: ... | Period: ... | Currency: ..."
     *
     * Defaults are tolerant — User segment is omitted entirely when blank
     * (anonymous / fresh-install case), Period defaults to "All time" so
     * generators that don't have a date filter still produce a sensible
     * line.
     */
    private fun headerInfoLine(
        projectName: String,
        userEmail: String,
        periodLabel: String,
        currencyCode: String,
    ): String {
        val parts = mutableListOf<String>()
        parts += "Project: ${projectName.ifBlank { "Personal" }}"
        if (userEmail.isNotBlank()) parts += "User: $userEmail"
        parts += "Period: ${periodLabel.ifBlank { "All time" }}"
        val sym = CurrencyUtils.symbolFor(currencyCode)
        parts += "Currency: $currencyCode${if (sym.isNotBlank()) " ($sym)" else ""}"
        return parts.joinToString(" | ")
    }

    /**
     * C21 Stage 1 — Android PdfDocument framework limitation note (audit #18
     * "Set PDF metadata: Title / Author / Subject"). Accepted limitation:
     * `android.graphics.pdf.PdfDocument` does NOT expose an info-dictionary
     * setter — Title / Author / Subject can't be written through the public
     * API. Migrating to a different PDF library (iText / PDFBox / OpenPDF)
     * for the sake of metadata would be a bigger dependency change than
     * the user-visible benefit warrants; the header info row inside the
     * PDF cover (above) carries the same Title / Project / Period data
     * onto a page that opens directly when the user views the file.
     */
    private const val PDF_METADATA_LIMITATION_NOTE = "" // documented only

    /**
     * C08 Stage 4 (3.2.18) — currency-aware number formatting for PDF cells.
     * Pre-3.2.18 this was a private `fmt(v) = "₹${String.format(locale, "%,.2f", v)}"`
     * helper that hardcoded ₹ and `.2f` precision regardless of project
     * currency. Now delegates to [CurrencyFormatter.detail] which gives:
     *   - Indian lakh-crore grouping (`₹2,41,663`) for INR/NPR/LKR/BDT.
     *   - Western thousand-comma (`$241,663`) for other currencies.
     *   - No decimals (audit Detail spec).
     *
     * [currencyCode] threads through every public PDF generator function
     * so the PDF reflects the project's currency, not a hardcoded `₹`.
     */
    private fun fmt(v: Double, currencyCode: String) =
        CurrencyFormatter.detail(v, currencyCode, Locale.getDefault())

    // A4 at 72 DPI
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE_H = 16f

    // Fynlo emerald palette (Android Color ints)
    private val COLOR_PRIMARY  = Color.rgb(4, 120, 87)    // Emerald600
    private val COLOR_DARK     = Color.rgb(6, 78, 59)     // Emerald800
    private val COLOR_LIGHT_BG = Color.rgb(236, 253, 245) // Emerald50
    private val COLOR_RED      = Color.rgb(220, 38, 38)
    private val COLOR_GREEN    = Color.rgb(5, 150, 105)
    private val COLOR_GRAY     = Color.rgb(82, 82, 91)
    private val COLOR_WHITE    = Color.WHITE
    private val COLOR_BLACK    = Color.BLACK

    // ── Paint helpers ─────────────────────────────────────────────────────────
    private fun titlePaint(color: Int = COLOR_PRIMARY, size: Float = 22f) = Paint().apply {
        this.color = color; textSize = size; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private fun bodyPaint(color: Int = COLOR_BLACK, size: Float = 10f, bold: Boolean = false) = Paint().apply {
        this.color = color; textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; isAntiAlias = true
    }
    private fun fillPaint(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }
    private fun strokePaint(color: Int) = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.5f }

    private data class TableRowSpec(
        val cols: List<String>,
        val colors: List<Int>? = null,
    )

    private data class MeasuredTableRow(
        val cells: List<Pair<List<String>, Paint>>,
        val height: Float,
    )

    // ── Page management ───────────────────────────────────────────────────────
    private class PdfBuilder(private val pdf: PdfDocument, private val os: OutputStream) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNum = 0
        var y = MARGIN + 10f

        fun startPage() {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create()
            page = pdf.startPage(info)
            canvas = page!!.canvas
            y = MARGIN + 10f
        }

        fun finishPage() {
            page?.let { pdf.finishPage(it) }
            page = null; canvas = null
        }

        fun checkBreak(needed: Float = LINE_H * 2) {
            if (y + needed > PAGE_H - MARGIN) { finishPage(); startPage() }
        }

        fun keepTogether(needed: Float) = checkBreak(needed)

        fun keepSectionStartTogether() = checkBreak(72f)

        fun canvas() = canvas!!

        fun text(text: String, x: Float, paint: Paint) {
            canvas().drawText(text, x, y, paint)
        }

        fun line(color: Int = Color.LTGRAY) {
            y += 4f
            val p = Paint().apply { this.color = color; strokeWidth = 0.5f }
            canvas().drawLine(MARGIN, y, PAGE_W - MARGIN, y, p)
            y += 6f
        }

        fun nl(amount: Float = LINE_H) { y += amount }

        fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            canvas().drawRect(left, top, right, bottom, fillPaint(color))
        }

        fun save() { pdf.writeTo(os); pdf.close() }
    }

    // ── Row drawing helper ────────────────────────────────────────────────────
    //
    // C21 Stage 2 (3.2.36) — replaced character-count truncation with proper
    // word-wrap per audit §C21 #4 ("auto-size or wrap columns; never truncate").
    // Pre-Stage 2 this counted `widths[i] / 5.5f` characters and slapped an
    // ellipsis on overflow — that hid useful data ("Salary Transf…" instead
    // of "Salary Transfer") and the per-character estimate was wildly wrong
    // for narrow letters ("ill") or wide ones ("WWW"). Now: split into words,
    // measure each candidate line via `Paint.measureText`, wrap to a new line
    // when the next word wouldn't fit. Single words too long for their column
    // are broken at the character level (filenames / very long category names
    // are the practical case).
    //
    // Row height grows to fit the cell with the most wrap lines.
    private fun measureTableRow(
        cols: List<String>,
        widths: List<Float>,
        isHeader: Boolean = false,
        colors: List<Int>? = null
    ): MeasuredTableRow {
        val perCell = cols.mapIndexed { i, text ->
            val paint = if (isHeader) bodyPaint(COLOR_WHITE, 9f, true)
                        else bodyPaint(colors?.getOrNull(i) ?: COLOR_BLACK, 9f)
            val avail = widths[i] - 8f   // 4-dp padding on each side
            wrapText(text, paint, avail) to paint
        }
        val tableLineH = 12f
        val rowH = tableLineH * (perCell.maxOfOrNull { it.first.size } ?: 1).coerceAtLeast(1) + 7f
        return MeasuredTableRow(perCell, rowH)
    }

    private fun PdfBuilder.drawMeasuredTableRow(
        row: MeasuredTableRow,
        widths: List<Float>,
        isHeader: Boolean = false,
        altBg: Boolean = false,
    ) {
        checkBreak(row.height + 6f)
        val bgColor = when {
            isHeader -> COLOR_DARK
            altBg    -> Color.rgb(250, 250, 250)
            else     -> COLOR_WHITE
        }
        val rowTop = y
        rect(MARGIN, rowTop, PAGE_W - MARGIN, rowTop + row.height, bgColor)

        var xPos = MARGIN + 4f
        row.cells.forEachIndexed { i, (lines, paint) ->
            lines.forEachIndexed { lineIdx, line ->
                canvas().drawText(line, xPos, rowTop + 13f + lineIdx * 12f, paint)
            }
            xPos += widths[i]
        }
        y += row.height
        checkBreak()
    }

    private fun PdfBuilder.drawTableRow(
        cols: List<String>,
        widths: List<Float>,
        isHeader: Boolean = false,
        altBg: Boolean = false,
        colors: List<Int>? = null
    ) {
        drawMeasuredTableRow(
            measureTableRow(cols, widths, isHeader, colors),
            widths,
            isHeader,
            altBg,
        )
    }

    private fun PdfBuilder.drawTableSection(
        title: String,
        widths: List<Float>,
        headers: List<String>,
        rows: List<TableRowSpec>,
    ) {
        val header = measureTableRow(headers, widths, isHeader = true)
        val firstRow = rows.firstOrNull()?.let { measureTableRow(it.cols, widths, colors = it.colors) }
        val firstBlockHeight = 48f + header.height + (firstRow?.height ?: 0f) + 8f
        keepTogether(firstBlockHeight)
        sectionHeader(title)
        drawMeasuredTableRow(header, widths, isHeader = true)
        rows.forEachIndexed { i, row ->
            drawMeasuredTableRow(
                measureTableRow(row.cols, widths, colors = row.colors),
                widths,
                altBg = i % 2 == 0,
            )
        }
    }

    /**
     * Word-wrap [text] so each returned line fits within [maxWidth] when
     * measured with [paint]. Splits on whitespace; when a single word doesn't
     * fit (long filenames, hash-style notes), falls back to per-character
     * breaks so no line ever exceeds the column.
     *
     * Empty input → single empty line so the caller still gets a valid row.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val out = mutableListOf<String>()
        val words = text.split(' ')
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current.clear(); current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.clear()
                }
                // Word longer than column — break by character.
                if (paint.measureText(word) > maxWidth) {
                    var remaining = word
                    while (paint.measureText(remaining) > maxWidth) {
                        var idx = remaining.length
                        while (idx > 0 && paint.measureText(remaining.substring(0, idx)) > maxWidth) idx--
                        if (idx == 0) idx = 1
                        out.add(remaining.substring(0, idx))
                        remaining = remaining.substring(idx)
                    }
                    current.append(remaining)
                } else {
                    current.append(word)
                }
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out.ifEmpty { listOf("") }
    }

    /**
     * Dynamic borrower-status helper per UX_AUDIT §C21 #5. Pre-Stage 2 the
     * PDF rendered `borrower.status` raw — that field is a stored value that
     * can lag reality (a borrower with `due < today` and `paid < amount`
     * could be stored as "Active" even though they're overdue). Now derived
     * from due-date + paid:
     *  - WrittenOff → "Written Off" (terminal state, status field wins)
     *  - paid >= amount → "Closed"
     *  - due past today and not closed → "Overdue"
     *  - otherwise → "Active"
     */
    private fun computeBorrowerStatus(b: Borrower, today: String): String = when {
        b.status == "WrittenOff" -> "Written Off"
        b.paid >= b.amount        -> "Closed"
        b.due.isNotBlank() && b.due < today -> "Overdue"
        else -> "Active"
    }

    /** Same logic for debts (audit #5 extension for the new Debts section). */
    private fun computeDebtStatus(d: Debt, today: String): String = when {
        d.paid >= d.amount        -> "Closed"
        d.due.isNotBlank() && d.due < today -> "Overdue"
        else -> "Active"
    }

    // ── Chart helpers (C21 Stage 3, audit #10) ────────────────────────────────
    //
    // All three charts share the same panel shape: a section-header style
    // title rendered in primary green + a fixed-height drawing area below.
    // Page-breaks are handled by checkBreak() before each panel.

    private val CHART_PANEL_TITLE_H = 22f
    private val CHART_PANEL_PAD     = 6f

    /**
     * Asset allocation donut — slices of `totalAssets` by Cash / Investments
     * / Receivables. Hidden when totalAssets is 0 so a fresh-install PDF
     * doesn't render an empty circle.
     *
     * Layout: small donut on the left (radius 50dp), legend rows aligned
     * to its right with category name + amount + percentage.
     */
    private fun PdfBuilder.drawAssetAllocationDonut(
        summary: FinancialSummary,
        currencyCode: String,
    ) {
        val slices = listOf(
            Triple("Cash",        summary.totalCash,        COLOR_PRIMARY),
            Triple("Investments", summary.totalInvestments, COLOR_GREEN),
            Triple("Receivables", summary.totalReceivables, Color.rgb(59, 130, 246)),  // blue-ish
        ).filter { it.second > 0 }
        val total = slices.sumOf { it.second }
        if (total <= 0.0) return

        checkBreak(128f)
        canvas().drawText("Asset Allocation", MARGIN, y, bodyPaint(COLOR_PRIMARY, 12f, true))
        y += CHART_PANEL_TITLE_H

        val panelTop = y
        val cx = MARGIN + 52f
        val cy = panelTop + 52f
        val r  = 42f
        val rect = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)

        var startAngle = -90f
        slices.forEach { (_, value, color) ->
            val sweep = (value / total * 360.0).toFloat()
            val p = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas().drawArc(rect, startAngle, sweep, true, p)
            startAngle += sweep
        }
        // Donut hole — white circle ~55% of outer radius.
        canvas().drawCircle(cx, cy, r * 0.55f, fillPaint(COLOR_WHITE))

        // Legend to the right of the donut
        val legendX = cx + r + 16f
        var legendY = panelTop + 20f
        slices.forEach { (label, value, color) ->
            canvas().drawRect(legendX, legendY - 8f, legendX + 10f, legendY + 2f, fillPaint(color))
            val pct = (value / total * 100).toInt()
            val text = "$label   ${fmt(value, currencyCode)}   ($pct%)"
            canvas().drawText(text, legendX + 16f, legendY, bodyPaint(COLOR_BLACK, 10f))
            legendY += 16f
        }
        // Advance below the donut (whichever's taller — donut or legend).
        y = panelTop + maxOf(r * 2 + CHART_PANEL_PAD, legendY - panelTop)
        nl(18f)
    }

    /**
     * Monthly income vs expense bar chart — last 12 months, dual bars per
     * month. Income green, expense red. Y-axis labels along the left at
     * 0/50/100% of max. Same cash-basis exclusion as the P&L Statement
     * (financingCats excluded so debt receipts don't inflate income).
     */
    private fun PdfBuilder.drawMonthlyBarChart(
        transactions: List<Transaction>,
        currencyCode: String,
        financingCats: Set<String>,
    ) {
        val today = LocalDate.now()
        val months = (11 downTo 0).map { off ->
            val date = today.minusMonths(off.toLong())
            val key  = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            val list = transactions.filter {
                it.date.startsWith(key) && it.tags != "journal_only" && it.category !in financingCats
            }
            val label = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM"))
            Triple(
                label,
                list.filter { it.type.equals("income",  true) }.sumOf { it.amount },
                list.filter { it.type.equals("expense", true) }.sumOf { it.amount },
            )
        }
        val maxVal = months.flatMap { listOf(it.second, it.third) }.maxOrNull()?.takeIf { it > 0 } ?: 0.0
        if (maxVal <= 0.0) return  // no activity → skip the chart

        checkBreak(142f)
        canvas().drawText("Monthly Income vs Expense (last 12 months)", MARGIN, y, bodyPaint(COLOR_PRIMARY, 12f, true))
        y += CHART_PANEL_TITLE_H

        val chartLeft   = MARGIN + 48f  // leave 48dp for y-axis labels
        val chartRight  = PAGE_W - MARGIN
        val chartTop    = y
        val chartH      = 82f
        val chartBottom = chartTop + chartH
        val chartW      = chartRight - chartLeft

        // Reference grid lines + y-axis labels at 0/50/100% of max.
        val gridPaint = Paint().apply { color = Color.rgb(229, 231, 235); strokeWidth = 0.5f }
        listOf(0.0, 0.5, 1.0).forEach { frac ->
            val yy = chartBottom - (chartH * frac).toFloat()
            canvas().drawLine(chartLeft, yy, chartRight, yy, gridPaint)
            canvas().drawText(
                fmt(maxVal * frac, currencyCode),
                MARGIN, yy + 3f, bodyPaint(COLOR_GRAY, 7f)
            )
        }

        val cellW    = chartW / months.size
        val barWidth = cellW * 0.32f
        months.forEachIndexed { i, (_, inc, exp) ->
            val baseX = chartLeft + i * cellW
            val incH  = ((inc / maxVal) * chartH).toFloat()
            val expH  = ((exp / maxVal) * chartH).toFloat()
            if (incH > 0) {
                canvas().drawRect(
                    baseX + cellW * 0.15f, chartBottom - incH,
                    baseX + cellW * 0.15f + barWidth, chartBottom,
                    fillPaint(COLOR_GREEN)
                )
            }
            if (expH > 0) {
                canvas().drawRect(
                    baseX + cellW * 0.50f, chartBottom - expH,
                    baseX + cellW * 0.50f + barWidth, chartBottom,
                    fillPaint(COLOR_RED)
                )
            }
        }
        // Month labels along the bottom.
        val labelY = chartBottom + 12f
        months.forEachIndexed { i, (label, _, _) ->
            val baseX = chartLeft + i * cellW
            canvas().drawText(label, baseX + cellW * 0.25f, labelY, bodyPaint(COLOR_GRAY, 7f))
        }
        // Tiny legend.
        canvas().drawRect(chartLeft, labelY + 8f, chartLeft + 8f, labelY + 16f, fillPaint(COLOR_GREEN))
        canvas().drawText("Income", chartLeft + 12f, labelY + 15f, bodyPaint(COLOR_GREEN, 8f))
        canvas().drawRect(chartLeft + 60f, labelY + 8f, chartLeft + 68f, labelY + 16f, fillPaint(COLOR_RED))
        canvas().drawText("Expense", chartLeft + 72f, labelY + 15f, bodyPaint(COLOR_RED, 8f))

        y = labelY + 40f
        nl(8f)
    }

    /**
     * Net worth trend line — connects snapshot points chronologically.
     * Renders an empty-state hint when fewer than 2 snapshots are available
     * (one point doesn't make a trend).
     */
    private fun PdfBuilder.drawNetWorthTrendLine(
        snapshots: List<NetWorthSnapshot>,
        currencyCode: String,
    ) {
        val sorted = snapshots.sortedBy { it.date }
        checkBreak(88f)
        canvas().drawText("Net Worth Trend", MARGIN, y, bodyPaint(COLOR_PRIMARY, 12f, true))
        y += CHART_PANEL_TITLE_H

        if (sorted.size < 2) {
            canvas().drawText(
                "Not enough snapshots yet — open the app a few more days, or use the in-app Backfill action.",
                MARGIN, y + 12f, bodyPaint(COLOR_GRAY, 9f)
            )
            y += 28f
            return
        }

        val chartLeft   = MARGIN + 48f
        val chartRight  = PAGE_W - MARGIN
        val chartTop    = y
        val chartH      = 48f
        val chartBottom = chartTop + chartH
        val chartW      = chartRight - chartLeft

        val maxNW = sorted.maxOf { it.netWorth }
        val minNW = sorted.minOf { it.netWorth }
        val range = (maxNW - minNW).takeIf { it > 0 } ?: 1.0

        // Reference grid + y-axis labels at min/mid/max.
        val gridPaint = Paint().apply { color = Color.rgb(229, 231, 235); strokeWidth = 0.5f }
        listOf(0.0, 0.5, 1.0).forEach { frac ->
            val yy = chartBottom - (chartH * frac).toFloat()
            canvas().drawLine(chartLeft, yy, chartRight, yy, gridPaint)
            val label = minNW + range * frac
            canvas().drawText(fmt(label, currencyCode), MARGIN, yy + 3f, bodyPaint(COLOR_GRAY, 7f))
        }

        val points = sorted.mapIndexed { i, s ->
            val xx = chartLeft + (i.toFloat() / (sorted.size - 1)) * chartW
            val yy = chartBottom - ((s.netWorth - minNW) / range * chartH).toFloat()
            xx to yy.coerceIn(chartTop, chartBottom)
        }
        // Area fill under the line
        val fillPath = android.graphics.Path().apply {
            moveTo(points.first().first, chartBottom)
            points.forEach { (xx, yy) -> lineTo(xx, yy) }
            lineTo(points.last().first, chartBottom); close()
        }
        canvas().drawPath(
            fillPath,
            Paint().apply { color = Color.argb(40, 5, 150, 105); style = Paint.Style.FILL; isAntiAlias = true }
        )
        // Line itself
        val linePath = android.graphics.Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { (xx, yy) -> lineTo(xx, yy) }
        }
        canvas().drawPath(
            linePath,
            Paint().apply {
                color = COLOR_PRIMARY; style = Paint.Style.STROKE
                strokeWidth = 2f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
            }
        )
        // Endpoint date labels.
        canvas().drawText(sorted.first().date, chartLeft, chartBottom + 12f, bodyPaint(COLOR_GRAY, 7f))
        val lastLabel = sorted.last().date
        val lastPaint = bodyPaint(COLOR_GRAY, 7f).apply { textAlign = Align.RIGHT }
        canvas().drawText(lastLabel, chartRight, chartBottom + 12f, lastPaint)

        y = chartBottom + 28f
        nl(12f)
    }

    // ── Section header ─────────────────────────────────────────────────────────
    private fun PdfBuilder.sectionHeader(title: String) {
        checkBreak(40f)
        nl(8f)
        canvas().drawText(title, MARGIN, y, bodyPaint(COLOR_PRIMARY, 13f, true))
        y += 4f
        val p = Paint().apply { color = COLOR_PRIMARY; strokeWidth = 1f }
        canvas().drawLine(MARGIN, y, PAGE_W - MARGIN, y, p)
        nl(18f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC: Main Financial Report PDF
    // ═══════════════════════════════════════════════════════════════════════════
    fun generatePDF(
        outputStream: OutputStream,
        summary: FinancialSummary,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>,
        // C02 step 5: timestamp the data so anyone reading the PDF can see
        // when the numbers were last computed (not just when the file was
        // produced). 0L = no recalc has ever run; rendered as "—".
        lastRecalcAt: Long = 0L,
        // C08 Stage 4: project currency so amounts render in the user's
        // configured format (INR → ₹2,41,663, USD → $241,663, etc.).
        // Default INR preserves backwards-compat with any caller that
        // hasn't migrated.
        currencyCode: String = "INR",
        // C21 Stage 1 (audit #8 + #9): identity + period + currency on the
        // cover. Defaults stay tolerant — callers can opt in incrementally.
        projectName: String = "Personal",
        userEmail: String = "",
        periodLabel: String = "All time",
        // C21 Stage 2 (audit #3): Debts list for the new Liabilities &
        // Debts section. Default empty for callers that haven't migrated.
        debts: List<Debt> = emptyList(),
        // C21 Stage 3 (audit #10): net-worth snapshots for the trend-line
        // chart. Default empty → chart shows an empty-state instead of an
        // axis with nothing on it.
        snapshots: List<NetWorthSnapshot> = emptyList(),
        // C11 (3.2.40): user's Date Format preference from Settings. PDF
        // date cells render in this pattern instead of raw ISO. Default
        // dd-MM-yyyy matches the in-app default.
        dateFormat: String = DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()

        // Cover
        b.canvas().drawText("Fynlo", MARGIN, b.y, titlePaint())
        b.nl(26f)
        b.canvas().drawText("Comprehensive Financial Report", MARGIN, b.y, bodyPaint(COLOR_GRAY, 14f))
        b.nl(18f)
        // C21 Stage 1 — identity row (Project | User | Period | Currency).
        b.canvas().drawText(
            headerInfoLine(projectName, userEmail, periodLabel, currencyCode),
            MARGIN, b.y, bodyPaint(COLOR_BLACK, 10f, bold = true)
        )
        b.nl(14f)
        b.canvas().drawText("Generated: ${LocalDate.now()} | Personal Finance Tracker",
            MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
        b.nl(12f)
        // C02: "Recalculated: <when>" — proves the figures below reflect the
        // post-recalc state, not whatever was stale in memory.
        val recalcLabel = if (lastRecalcAt > 0L) {
            val zone = java.time.ZoneId.systemDefault()
            val dt = java.time.Instant.ofEpochMilli(lastRecalcAt).atZone(zone)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH)
            "Recalculated: ${dt.format(fmt)}"
        } else {
            "Recalculated: —" // em dash for "never"
        }
        b.canvas().drawText(recalcLabel, MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
        b.nl(24f)

        // ── KPI cards (audit C21 #11) — 2 rows of cards.
        // Row 1 (balance sheet): NET WORTH | TOTAL ASSETS | TOTAL LIABILITIES | TOTAL CASH | INVEST GROWTH
        // Row 2 (activity):      MONTHLY INCOME | MONTHLY EXPENSE | NET CASH FLOW | TOTAL LENT OUT
        // Total Lent Out = lifetime principal (matches C15b's audit-#4 fix
        // for the P&L Statement screen). Monthly Income / Monthly Expense
        // are calendar-month, financing-categories-excluded (same exclusion
        // as the P&L Statement so a debt receipt doesn't inflate income).
        val financingCats = setOf(
            "Debt Received", "Debt Repayment", "Lending",
            "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
        )
        val nowDate    = LocalDate.now()
        val monthStart = nowDate.withDayOfMonth(1).toString()
        val monthEnd   = nowDate.toString()
        val monthlyTxn = transactions.filter {
            it.date in monthStart..monthEnd && it.tags != "journal_only" && it.category !in financingCats
        }
        val monthlyIncome  = monthlyTxn.filter { it.type.equals("income",  true) }.sumOf { it.amount }
        val monthlyExpense = monthlyTxn.filter { it.type.equals("expense", true) }.sumOf { it.amount }
        val netCashFlow    = monthlyIncome - monthlyExpense
        val totalLiabilities = summary.totalDebtPrincipal + summary.totalDebtInterest
        val totalLentOut     = borrowers.sumOf { it.amount }  // lifetime (incl. WrittenOff)

        val row1 = listOf(
            Triple("NET WORTH",         fmt(summary.netWorth, currencyCode),      if (summary.netWorth >= 0) COLOR_GREEN else COLOR_RED),
            Triple("TOTAL ASSETS",      fmt(summary.totalAssets, currencyCode),   COLOR_PRIMARY),
            Triple("TOTAL LIABILITIES", fmt(totalLiabilities, currencyCode),      if (totalLiabilities > 0) COLOR_RED else COLOR_GRAY),
            Triple("TOTAL CASH",        fmt(summary.totalCash, currencyCode),     COLOR_PRIMARY),
            Triple("INVEST GROWTH",     fmt(summary.investmentGrowth, currencyCode), if (summary.investmentGrowth >= 0) COLOR_GREEN else COLOR_RED),
        )
        val row2 = listOf(
            Triple("MONTHLY INCOME",  fmt(monthlyIncome,  currencyCode), COLOR_GREEN),
            Triple("MONTHLY EXPENSE", fmt(monthlyExpense, currencyCode), COLOR_RED),
            Triple("NET CASH FLOW",   fmt(netCashFlow, currencyCode),    if (netCashFlow >= 0) COLOR_GREEN else COLOR_RED),
            Triple("TOTAL LENT OUT",  fmt(totalLentOut, currencyCode),   COLOR_PRIMARY),
        )

        val cardW = (PAGE_W - MARGIN * 2) / 3f
        val cardH = 44f
        fun drawKpiRow(cards: List<Triple<String, String, Int>>, top: Float) {
            cards.forEachIndexed { i, (label, value, color) ->
                val lx = MARGIN + cardW * i
                b.rect(lx, top, lx + cardW - 4f, top + cardH, COLOR_LIGHT_BG)
                b.canvas().drawText(label, lx + 6f, top + 13f, bodyPaint(COLOR_GRAY, 8f))
                b.canvas().drawText(value, lx + 6f, top + 32f, bodyPaint(color, 10f, true))
            }
        }
        val cardTop1 = b.y - LINE_H + 2f
        drawKpiRow(row1.take(3), cardTop1)
        val cardTop2 = cardTop1 + cardH + 6f
        drawKpiRow(row1.drop(3), cardTop2)
        val cardTop3 = cardTop2 + cardH + 6f
        drawKpiRow(row2.take(3), cardTop3)
        val cardTop4 = cardTop3 + cardH + 6f
        drawKpiRow(row2.drop(3), cardTop4)
        // Advance past all card rows.
        b.nl(cardH * 4 + 26f)

        // ── Charts (audit C21 #10) — three panels stacked under KPIs:
        //   (1) Asset allocation donut       — breakdown of totalAssets
        //   (2) Monthly income vs expense    — last 12 months, bar chart
        //   (3) Net worth trend              — snapshots over time, line
        // Each panel has its own checkBreak() so the chart starts on a fresh
        // page if it doesn't fit in the remaining cover space.
        b.drawAssetAllocationDonut(summary, currencyCode)
        b.drawMonthlyBarChart(transactions, currencyCode, financingCats)
        b.drawNetWorthTrendLine(snapshots, currencyCode)

        // 1. Accounts
        if (summary.accountBreakdown.isNotEmpty()) {
            val aw = listOf((PAGE_W - MARGIN * 2) * 0.6f, (PAGE_W - MARGIN * 2) * 0.4f)
            b.drawTableSection(
                "1. Account Balances",
                aw,
                listOf("Account", "Balance"),
                summary.accountBreakdown.entries.map { (name, bal) ->
                    TableRowSpec(listOf(name, fmt(bal, currencyCode)))
                },
            )
        }

        // 2. Lending  — audit #5: dynamic Status computed from due+paid, not
        //   read raw from the stored field which can lag reality.
        val today = LocalDate.now().toString()
        if (borrowers.isNotEmpty()) {
            val usable = PAGE_W - MARGIN * 2
            val lw = listOf(
                usable*.20f, usable*.13f, usable*.11f, usable*.07f,
                usable*.13f, usable*.13f, usable*.09f, usable*.14f,
            )
            val rows = borrowers.map { bo ->
                val status = computeBorrowerStatus(bo, today)
                val statusColor = when (status) {
                    "Overdue", "Written Off" -> COLOR_RED
                    "Closed"                 -> COLOR_GREEN
                    else                     -> COLOR_BLACK
                }
                TableRowSpec(
                    listOf(
                        bo.name, fmt(bo.amount, currencyCode), fmt(bo.paid, currencyCode), "${bo.rate}%",
                        DateUtils.format(bo.date, DateUtils.Style.Compact, dateFormat),
                        if (bo.due.isBlank()) "-" else DateUtils.format(bo.due, DateUtils.Style.Compact, dateFormat),
                        status, bo.notes,
                    ),
                    colors = listOf(
                        COLOR_BLACK, COLOR_BLACK, COLOR_BLACK, COLOR_BLACK,
                        COLOR_BLACK, COLOR_BLACK, statusColor, COLOR_GRAY,
                    ),
                )
            }
            b.drawTableSection(
                "2. Lending & Receivables",
                lw,
                listOf("Person","Principal","Paid","Rate","Lent On","Due","Status","Notes"),
                rows,
            )
        }

        // 3. Liabilities & Debts — audit #3 (new section, was missing entirely
        // pre-Stage 2 so financial advisors got a misleading "you have no
        // liabilities" view). Same shape as the Lending table for visual
        // parity; Status computed dynamically per audit #5.
        if (debts.isNotEmpty()) {
            val usable = PAGE_W - MARGIN * 2
            val dw = listOf(
                usable*.21f, usable*.13f, usable*.10f, usable*.07f,
                usable*.13f, usable*.13f, usable*.09f, usable*.14f,
            )
            val rows = debts.map { d ->
                val status = computeDebtStatus(d, today)
                val statusColor = when (status) {
                    "Overdue" -> COLOR_RED
                    "Closed"  -> COLOR_GREEN
                    else      -> COLOR_BLACK
                }
                TableRowSpec(
                    listOf(
                        d.name, fmt(d.amount, currencyCode), fmt(d.paid, currencyCode), "${d.rate}%",
                        DateUtils.format(d.date, DateUtils.Style.Compact, dateFormat),
                        if (d.due.isBlank()) "-" else DateUtils.format(d.due, DateUtils.Style.Compact, dateFormat),
                        status, d.intType.ifBlank{"-"},
                    ),
                    colors = listOf(
                        COLOR_BLACK, COLOR_BLACK, COLOR_BLACK, COLOR_BLACK,
                        COLOR_BLACK, COLOR_BLACK, statusColor, COLOR_GRAY,
                    ),
                )
            }
            b.drawTableSection(
                "3. Liabilities & Debts",
                dw,
                listOf("Creditor","Principal","Paid","Rate","Borrowed","Due","Status","Type"),
                rows,
            )
        }

        // 4. Investments
        if (investments.isNotEmpty()) {
            val usable = PAGE_W - MARGIN * 2
            val iw = listOf(usable*.26f, usable*.13f, usable*.16f, usable*.16f, usable*.14f, usable*.15f)
            val rows = investments.map { inv ->
                val growth = inv.currentVal - inv.invested
                TableRowSpec(
                    listOf(inv.name, inv.type, fmt(inv.invested, currencyCode), fmt(inv.currentVal, currencyCode), fmt(growth, currencyCode), DateUtils.format(inv.date, DateUtils.Style.Compact, dateFormat)),
                    colors = listOf(COLOR_BLACK, COLOR_GRAY, COLOR_BLACK, COLOR_BLACK, if(growth>=0) COLOR_GREEN else COLOR_RED, COLOR_GRAY)
                )
            }
            b.drawTableSection(
                "4. Investment Portfolio",
                iw,
                listOf("Asset","Type","Invested","Current","Growth","Date"),
                rows,
            )
        }

        // 5. Transactions — audit #6: title now reflects what's actually
        //   shown. If the user has ≤50 transactions the section is "All
        //   Transactions (N)"; otherwise "Most Recent 50 of N".
        //   Audit #7: Type column widened from 10% → 12% so "Transfer" no
        //   longer wraps in narrow runs.
        val recent = transactions.sortedByDescending { it.date }.take(50)
        if (recent.isNotEmpty()) {
            val title = if (transactions.size <= 50)
                "5. All Transactions (${transactions.size})"
            else
                "5. Most Recent 50 of ${transactions.size} Transactions"
            val usable = PAGE_W - MARGIN * 2
            val tw = listOf(usable*.13f, usable*.12f, usable*.17f, usable*.26f, usable*.15f, usable*.17f)
            val rows = recent.map { t ->
                val amtColor = if (t.type.equals("income", ignoreCase = true)) COLOR_GREEN else COLOR_RED
                TableRowSpec(
                    listOf(DateUtils.format(t.date, DateUtils.Style.Compact, dateFormat), t.type, t.category, t.desc, fmt(t.amount, currencyCode), t.fromAcct.ifBlank{t.toAcct}),
                    colors = listOf(COLOR_BLACK,COLOR_GRAY,COLOR_BLACK,COLOR_BLACK, amtColor, COLOR_GRAY)
                )
            }
            b.drawTableSection(
                title,
                tw,
                listOf("Date","Type","Category","Description","Amount","Account"),
                rows,
            )
        }

        // Footer
        b.nl(12f); b.checkBreak()
        val footerPaint = bodyPaint(COLOR_GRAY, 9f).apply { textAlign = Align.CENTER }
        // FY022 (LINT_RULES.md): version string must come from BuildConfig,
        // not a hardcoded literal — the 3.2.2 smoke test caught this still
        // saying "v3.1" on an actually-3.2.2 build.
        b.canvas().drawText("Generated by Fynlo v${BuildConfig.VERSION_NAME} | ${LocalDate.now()}", PAGE_W / 2f, b.y, footerPaint)

        b.finishPage()
        b.save()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC: Money Flow PDF
    // ═══════════════════════════════════════════════════════════════════════════
    fun generateMoneyFlowPDF(
        outputStream: OutputStream,
        flows: List<FlowEntry>,
        currencyCode: String = "INR",
        // C21 Stage 1 — same identity row as the main PDF for consistency.
        projectName: String = "Personal",
        userEmail: String = "",
        periodLabel: String = "All time",
        // C11 (3.2.40) — user's Date Format preference; default matches in-app.
        dateFormat: String = DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()

        b.canvas().drawText("Fynlo", MARGIN, b.y, titlePaint())
        b.nl(26f)
        b.canvas().drawText("Money Flow Report", MARGIN, b.y, bodyPaint(COLOR_GRAY, 14f))
        b.nl(18f)
        b.canvas().drawText(
            headerInfoLine(projectName, userEmail, periodLabel, currencyCode),
            MARGIN, b.y, bodyPaint(COLOR_BLACK, 10f, bold = true)
        )
        b.nl(14f)
        b.canvas().drawText("Generated: ${LocalDate.now()}", MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
        b.nl(24f)

        val totalIn  = flows.filter { it.flowType == FlowType.INCOME  || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
        val totalOut = flows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY    }.sumOf { it.amount }
        val lentOut  = flows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }
        val net      = totalIn - totalOut

        // Summary cards
        val cardW = (PAGE_W - MARGIN * 2) / 2f
        val cards = listOf(
            Triple("TOTAL INFLOW",  fmt(totalIn, currencyCode),  COLOR_GREEN),
            Triple("TOTAL OUTFLOW", fmt(totalOut, currencyCode), COLOR_RED),
            Triple("LENT OUT",      fmt(lentOut, currencyCode),  COLOR_PRIMARY),
            Triple("NET FLOW",      fmt(net, currencyCode),      if (net >= 0) COLOR_GREEN else COLOR_RED)
        )
        val ct = b.y - LINE_H + 2f
        cards.forEachIndexed { i, (label, value, color) ->
            val lx = MARGIN + cardW * (i % 2)
            val top = ct + (i / 2) * 50f
            b.rect(lx, top, lx + cardW - 4f, top + 44f, COLOR_LIGHT_BG)
            b.canvas().drawText(label, lx + 6f, top + 13f, bodyPaint(COLOR_GRAY, 8f))
            b.canvas().drawText(value, lx + 6f, top + 32f, bodyPaint(color, 11f, true))
        }
        b.nl(100f)

        b.sectionHeader("All Flows (${pluralize(flows.size, "entry", "entries")})")
        val usable = PAGE_W - MARGIN * 2
        val fw = listOf(usable*.14f, usable*.14f, usable*.22f, usable*.22f, usable*.18f, usable*.10f)
        b.drawTableRow(listOf("Date","Type","From","To","Label","Amount"), fw, isHeader = true)
        flows.forEachIndexed { i, f ->
            val color = when (f.flowType) {
                FlowType.INCOME       -> COLOR_GREEN
                FlowType.EXPENSE      -> COLOR_RED
                FlowType.DEBT_REPAY   -> COLOR_RED
                else                  -> COLOR_PRIMARY
            }
            b.drawTableRow(
                listOf(DateUtils.format(f.date, DateUtils.Style.Compact, dateFormat), f.flowType.name, f.from, f.to, f.label, fmt(f.amount, currencyCode)),
                fw, altBg = i % 2 == 0,
                colors = listOf(COLOR_BLACK,COLOR_GRAY,COLOR_BLACK,COLOR_BLACK,COLOR_BLACK, color)
            )
        }

        b.finishPage()
        b.save()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC: Loan Statement PDF
    // ═══════════════════════════════════════════════════════════════════════════
    fun generateLoanStatementPDF(
        outputStream: OutputStream,
        borrower: Borrower,
        repayments: List<Transaction>,
        interest: Double,
        outstanding: Double,
        currencyCode: String = "INR",
        // C21 Stage 1 — borrower name carries identity; project + user added
        // for parity. periodLabel defaults to "All time" since loan statements
        // span the borrower's full loan history.
        projectName: String = "Personal",
        userEmail: String = "",
        // C11 (3.2.40) — user's Date Format preference.
        dateFormat: String = DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()

        // Header
        val centerPaint = bodyPaint(COLOR_BLACK, 20f, true).apply { textAlign = Align.CENTER }
        b.canvas().drawText("LOAN STATEMENT", PAGE_W / 2f, b.y, centerPaint)
        b.nl(22f)
        val subCenter = bodyPaint(COLOR_GRAY, 10f).apply { textAlign = Align.CENTER }
        b.canvas().drawText("Fynlo | ${LocalDate.now()}", PAGE_W / 2f, b.y, subCenter)
        b.nl(18f)
        // Audit #8/#9 — identity row, left-aligned under the centered title.
        b.canvas().drawText(
            headerInfoLine(projectName, userEmail, "All time", currencyCode),
            MARGIN, b.y, bodyPaint(COLOR_BLACK, 10f, bold = true)
        )
        b.nl(20f)

        // Borrower detail table
        b.sectionHeader("Borrower Details")
        val mid = PAGE_W / 2f
        // Audit C21 #17 — no silent Interest Type default. If borrower.intType
        // is blank, render "Not specified" rather than silently producing
        // "${rate}% p.a. ()" which falsely suggests Simple Interest.
        val typeLabel = borrower.intType.ifBlank { "Not specified" }
        val details = listOf(
            "Borrower Name"   to borrower.name,
            "Phone"           to borrower.phone.ifBlank { "-" },
            "Lent On"         to DateUtils.format(borrower.date, DateUtils.Style.Compact, dateFormat),
            "Due Date"        to (if (borrower.due.isBlank()) "Not specified" else DateUtils.format(borrower.due, DateUtils.Style.Compact, dateFormat)),
            "Principal"       to fmt(borrower.amount, currencyCode),
            "Interest Rate"   to "${borrower.rate}% p.a. ($typeLabel)",
            "Interest Accrued" to fmt(interest, currencyCode),
            "Total Paid"      to fmt(borrower.paid, currencyCode),
            "Outstanding"     to fmt(outstanding, currencyCode)
        )
        details.forEachIndexed { i, (label, value) ->
            if (i % 2 == 0) b.rect(MARGIN, b.y - 12f, PAGE_W - MARGIN, b.y + 4f, Color.rgb(249,250,251))
            b.canvas().drawText(label, MARGIN + 4f, b.y, bodyPaint(COLOR_GRAY, 10f, true))
            val valueColor = if (label == "Outstanding") (if (outstanding > 0) COLOR_RED else COLOR_GREEN) else COLOR_BLACK
            b.canvas().drawText(value, mid, b.y, bodyPaint(valueColor, 10f))
            b.nl(LINE_H)
        }

        // Repayment history
        if (repayments.isNotEmpty()) {
            b.sectionHeader("Repayment History")
            val usable = PAGE_W - MARGIN * 2
            val rw = listOf(usable * 0.25f, usable * 0.25f, usable * 0.50f)
            b.drawTableRow(listOf("Date", "Amount", "Notes"), rw, isHeader = true)
            repayments.sortedByDescending { it.date }.forEachIndexed { i, t ->
                b.drawTableRow(listOf(DateUtils.format(t.date, DateUtils.Style.Compact, dateFormat), fmt(t.amount, currencyCode), t.notes.ifBlank{"-"}), rw, altBg = i % 2 == 0)
            }
        } else {
            b.canvas().drawText("No repayments recorded yet.", MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
            b.nl(LINE_H)
        }

        b.nl(16f)
        val footerPaint = bodyPaint(COLOR_GRAY, 8f).apply { textAlign = Align.CENTER }
        b.canvas().drawText("This is a computer-generated statement.", PAGE_W / 2f, b.y, footerPaint)

        b.finishPage()
        b.save()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CSV functions (unchanged — no third-party dependency)
    // ═══════════════════════════════════════════════════════════════════════════
    fun generateCSV(
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ): String {
        val sb = StringBuilder()
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""

        sb.append("Fynlo - FINANCIAL EXPORT\n")
        sb.append("Generated: ${LocalDate.now()}\n\n")

        sb.append("--- TRANSACTIONS ---\n")
        sb.append("Date,Type,Category,Amount,Account,Description,Notes\n")
        transactions.forEach {
            sb.append("${esc(it.date)},${esc(it.type)},${esc(it.category)},${it.amount},${esc(it.fromAcct.ifBlank { it.toAcct })},${esc(it.desc)},${esc(it.notes)}\n")
        }

        sb.append("\n--- LENDING ---\n")
        sb.append("Name,Principal,Rate(%),Date Lent,Due Date,Status,Notes\n")
        borrowers.forEach {
            sb.append("${esc(it.name)},${it.amount},${it.rate},${esc(it.date)},${esc(it.due)},${esc(it.status)},${esc(it.notes)}\n")
        }

        sb.append("\n--- INVESTMENTS ---\n")
        sb.append("Name,Type,Invested,Current Value,Growth,Date,Notes\n")
        investments.forEach {
            sb.append("${esc(it.name)},${esc(it.type)},${it.invested},${it.currentVal},${it.currentVal - it.invested},${esc(it.date)},${esc(it.notes)}\n")
        }

        return sb.toString()
    }

    fun generateDataExportCSV(
        scope: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        debts: List<Debt>,
        investments: List<Investment>,
        people: List<Person>,
        budgets: List<Budget>,
        goals: List<Goal>,
    ): String {
        val normalized = scope.lowercase(Locale.ENGLISH)
        val sb = StringBuilder()
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
        fun wants(key: String) = normalized == "whole" || normalized == key

        sb.append("Fynlo - DATA EXPORT\n")
        sb.append("Scope,${esc(labelForDataExportScope(scope))}\n")
        sb.append("Generated,${LocalDate.now()}\n\n")

        if (wants("transactions")) {
            sb.append("--- TRANSACTIONS ---\n")
            sb.append("Date,Type,Amount,From Account,To Account,Category,Description,Notes\n")
            transactions.sortedByDescending { it.date }.forEach {
                sb.append("${esc(it.date)},${esc(it.type)},${it.amount},${esc(it.fromAcct)},${esc(it.toAcct)},${esc(it.category)},${esc(it.desc)},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("accounts")) {
            sb.append("--- ACCOUNTS ---\n")
            sb.append("Name,Type,Balance,Notes\n")
            accounts.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${esc(it.type)},${it.balance},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("lending")) {
            sb.append("--- LENDING ---\n")
            sb.append("Name,Phone,Principal,Rate,Interest Type,Lent On,Due Date,Paid,Status,Notes\n")
            borrowers.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${esc(it.phone)},${it.amount},${it.rate},${esc(it.intType)},${esc(it.date)},${esc(it.due)},${it.paid},${esc(it.status)},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("debts")) {
            sb.append("--- DEBTS ---\n")
            sb.append("Name,Phone,Principal,Rate,Interest Type,Date,Due Date,Paid,Status,Notes\n")
            debts.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${esc(it.phone)},${it.amount},${it.rate},${esc(it.intType)},${esc(it.date)},${esc(it.due)},${it.paid},${esc(it.status)},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("investments")) {
            sb.append("--- INVESTMENTS ---\n")
            sb.append("Name,Type,Invested,Current Value,Growth,Date,Notes\n")
            investments.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${esc(it.type)},${it.invested},${it.currentVal},${it.currentVal - it.invested},${esc(it.date)},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("people")) {
            sb.append("--- PEOPLE ---\n")
            sb.append("Name,Phone,Type,Notes\n")
            people.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${esc(it.phone)},${esc(it.type)},${esc(it.notes)}\n")
            }
            sb.append('\n')
        }
        if (wants("budgets")) {
            sb.append("--- BUDGETS ---\n")
            sb.append("Category,Limit,Period,Alert Threshold %\n")
            budgets.sortedBy { it.category }.forEach {
                sb.append("${esc(it.category)},${it.limitAmount},${esc(it.period)},${it.alertThresholdPct}\n")
            }
            sb.append('\n')
        }
        if (wants("goals")) {
            sb.append("--- GOALS ---\n")
            sb.append("Name,Target,Saved,Deadline,Linked Account,Notes\n")
            goals.sortedBy { it.name }.forEach {
                sb.append("${esc(it.name)},${it.targetAmount},${it.savedAmount},${esc(it.deadline)},${esc(it.linkedAccount)},${esc(it.notes)}\n")
            }
        }

        return sb.toString()
    }

    fun generateDataExportPDF(
        outputStream: OutputStream,
        scope: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        debts: List<Debt>,
        investments: List<Investment>,
        people: List<Person>,
        budgets: List<Budget>,
        goals: List<Goal>,
        currencyCode: String = "INR",
        projectName: String = "Personal",
        userEmail: String = "",
        dateFormat: String = DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val normalized = scope.lowercase(Locale.ENGLISH)
        fun wants(key: String) = normalized == "whole" || normalized == key
        fun dt(iso: String) = if (iso.isBlank()) "-" else DateUtils.format(iso, DateUtils.Style.Compact, dateFormat)

        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()
        b.text("Fynlo ${labelForDataExportScope(scope)} Export", MARGIN, titlePaint(size = 20f))
        b.nl(20f)
        b.text(headerInfoLine(projectName, userEmail, "All time", currencyCode), MARGIN, bodyPaint(COLOR_GRAY, 9f))
        b.nl(18f)

        fun section(title: String, rows: List<List<String>>, widths: List<Float>) {
            if (rows.isEmpty()) return
            b.checkBreak(LINE_H * 5)
            b.sectionHeader(title)
            rows.forEachIndexed { index, row ->
                b.drawTableRow(row, widths, isHeader = index == 0, altBg = index % 2 == 0)
            }
            b.nl(8f)
        }

        if (wants("transactions")) section(
            "Transactions",
            listOf(listOf("Date", "Type", "Amount", "Account", "Category")) +
                transactions.sortedByDescending { it.date }.map {
                    listOf(dt(it.date), it.type, fmt(it.amount, currencyCode), it.fromAcct.ifBlank { it.toAcct }, it.category)
                },
            listOf(70f, 70f, 90f, 155f, 130f),
        )
        if (wants("accounts")) section(
            "Accounts",
            listOf(listOf("Name", "Type", "Balance")) +
                accounts.sortedBy { it.name }.map { listOf(it.name, it.type, fmt(it.balance, currencyCode)) },
            listOf(220f, 120f, 175f),
        )
        if (wants("lending")) section(
            "Lending",
            listOf(listOf("Name", "Principal", "Rate", "Paid", "Status")) +
                borrowers.sortedBy { it.name }.map {
                    listOf(it.name, fmt(it.amount, currencyCode), "${it.rate}%", fmt(it.paid, currencyCode), it.status)
                },
            listOf(160f, 110f, 65f, 100f, 80f),
        )
        if (wants("debts")) section(
            "Debts",
            listOf(listOf("Name", "Principal", "Rate", "Paid", "Status")) +
                debts.sortedBy { it.name }.map {
                    listOf(it.name, fmt(it.amount, currencyCode), "${it.rate}%", fmt(it.paid, currencyCode), it.status)
                },
            listOf(160f, 110f, 65f, 100f, 80f),
        )
        if (wants("investments")) section(
            "Investments",
            listOf(listOf("Name", "Type", "Invested", "Current", "Growth")) +
                investments.sortedBy { it.name }.map {
                    listOf(it.name, it.type, fmt(it.invested, currencyCode), fmt(it.currentVal, currencyCode), fmt(it.currentVal - it.invested, currencyCode))
                },
            listOf(130f, 85f, 100f, 100f, 100f),
        )
        if (wants("people")) section(
            "People",
            listOf(listOf("Name", "Phone", "Type", "Notes")) +
                people.sortedBy { it.name }.map { listOf(it.name, it.phone, it.type, it.notes) },
            listOf(150f, 115f, 100f, 150f),
        )
        if (wants("budgets")) section(
            "Budgets",
            listOf(listOf("Category", "Limit", "Period", "Alert")) +
                budgets.sortedBy { it.category }.map { listOf(it.category, fmt(it.limitAmount, currencyCode), it.period, "${it.alertThresholdPct}%") },
            listOf(170f, 130f, 110f, 105f),
        )
        if (wants("goals")) section(
            "Goals",
            listOf(listOf("Name", "Target", "Saved", "Deadline")) +
                goals.sortedBy { it.name }.map { listOf(it.name, fmt(it.targetAmount, currencyCode), fmt(it.savedAmount, currencyCode), dt(it.deadline)) },
            listOf(185f, 120f, 120f, 90f),
        )

        b.finishPage()
        b.save()
    }

    fun labelForDataExportScope(scope: String): String = when (scope.lowercase(Locale.ENGLISH)) {
        "transactions" -> "Transactions"
        "accounts" -> "Accounts"
        "lending" -> "Lending"
        "debts" -> "Debts"
        "investments" -> "Investments"
        "people" -> "People"
        "budgets" -> "Budgets"
        "goals" -> "Goals"
        else -> "Whole Data"
    }

    fun generateMoneyFlowCSV(
        flows: List<FlowEntry>,
        currencyCode: String = "INR",
    ): String {
        val sb = StringBuilder()
        sb.append("Fynlo - MONEY FLOW REPORT\n")
        sb.append("Generated: ${LocalDate.now()}\n\n")
        sb.append("Date,Type,From,To,Label,Amount\n")
        flows.forEach { f ->
            fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
            sb.append("${esc(f.date)},${f.flowType},${esc(f.from)},${esc(f.to)},${esc(f.label)},${f.amount}\n")
        }
        val totalIn  = flows.filter { it.flowType == FlowType.INCOME  || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
        val totalOut = flows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY    }.sumOf { it.amount }
        val lentOut  = flows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }
        sb.append("\n--- SUMMARY ---\n")
        sb.append("Total Inflow,${fmt(totalIn, currencyCode)}\nTotal Outflow,${fmt(totalOut, currencyCode)}\n")
        sb.append("Lent Out,${fmt(lentOut, currencyCode)}\nNet Flow,${fmt(totalIn - totalOut, currencyCode)}\n")
        return sb.toString()
    }
}
