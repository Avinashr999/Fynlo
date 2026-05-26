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

    private val locale = Locale.getDefault()
    private fun fmt(v: Double) = "₹${String.format(locale, "%,.2f", v)}"

    // A4 at 72 DPI
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE_H = 16f

    // Fynlo emerald palette (Android Color ints)
    private val COLOR_PRIMARY  = Color.rgb(5, 150, 105)   // Emerald500
    private val COLOR_DARK     = Color.rgb(6, 95, 70)     // Emerald700
    private val COLOR_LIGHT_BG = Color.rgb(209, 250, 229) // Emerald100
    private val COLOR_RED      = Color.rgb(239, 68, 68)
    private val COLOR_GREEN    = Color.rgb(16, 185, 129)
    private val COLOR_GRAY     = Color.rgb(107, 114, 128)
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

        fun canvas() = canvas!!

        fun text(text: String, x: Float, paint: Paint) {
            canvas().drawText(text, x, y, paint)
        }

        fun line(color: Int = Color.LTGRAY) {
            y += 4f
            val p = Paint().apply { this.color = color; strokeWidth = 0.5f }
            canvas().drawLine(MARGIN, y, (PAGE_W - MARGIN).toFloat(), y, p)
            y += 6f
        }

        fun nl(amount: Float = LINE_H) { y += amount }

        fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            canvas().drawRect(left, top, right, bottom, fillPaint(color))
        }

        fun save() { pdf.writeTo(os); pdf.close() }
    }

    // ── Row drawing helper ────────────────────────────────────────────────────
    private fun PdfBuilder.drawTableRow(
        cols: List<String>,
        widths: List<Float>,
        isHeader: Boolean = false,
        altBg: Boolean = false,
        colors: List<Int>? = null
    ) {
        val rowH = LINE_H + 8f
        val bgColor = when {
            isHeader -> COLOR_DARK
            altBg    -> Color.rgb(243, 244, 246)
            else     -> COLOR_WHITE
        }
        rect(MARGIN, y - LINE_H + 2f, PAGE_W - MARGIN.toFloat(), y - LINE_H + 2f + rowH, bgColor)

        var xPos = MARGIN + 4f
        cols.forEachIndexed { i, text ->
            val paint = if (isHeader) bodyPaint(COLOR_WHITE, 9f, true)
                        else bodyPaint(colors?.getOrNull(i) ?: COLOR_BLACK, 9f)
            val maxChars = ((widths[i] - 8f) / 5.5f).toInt().coerceAtLeast(4)
            val truncated = if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
            canvas().drawText(truncated, xPos, y + 2f, paint)
            xPos += widths[i]
        }
        y += rowH
        checkBreak()
    }

    // ── Section header ─────────────────────────────────────────────────────────
    private fun PdfBuilder.sectionHeader(title: String) {
        checkBreak(40f)
        nl(8f)
        canvas().drawText(title, MARGIN, y, bodyPaint(COLOR_PRIMARY, 13f, true))
        y += 4f
        val p = Paint().apply { color = COLOR_PRIMARY; strokeWidth = 1f }
        canvas().drawLine(MARGIN, y, (PAGE_W - MARGIN).toFloat(), y, p)
        nl(10f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC: Main Financial Report PDF
    // ═══════════════════════════════════════════════════════════════════════════
    fun generatePDF(
        outputStream: OutputStream,
        summary: FinancialSummary,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ) {
        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()

        // Cover
        b.canvas().drawText("Fynlo", MARGIN, b.y, titlePaint())
        b.nl(26f)
        b.canvas().drawText("Comprehensive Financial Report", MARGIN, b.y, bodyPaint(COLOR_GRAY, 14f))
        b.nl(18f)
        b.canvas().drawText("Generated: ${LocalDate.now()} | Personal Finance Tracker",
            MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
        b.nl(24f)

        // Summary cards — 4 across
        val cardW = (PAGE_W - MARGIN * 2) / 4f
        val cards = listOf(
            Triple("NET WORTH",     fmt(summary.netWorth),         if (summary.netWorth >= 0) COLOR_GREEN else COLOR_RED),
            Triple("TOTAL ASSETS",  fmt(summary.totalAssets),       COLOR_PRIMARY),
            Triple("TOTAL CASH",    fmt(summary.totalCash),         COLOR_PRIMARY),
            Triple("INVEST GROWTH", fmt(summary.investmentGrowth),  COLOR_GREEN)
        )
        val cardTop = b.y - LINE_H + 2f
        cards.forEachIndexed { i, (label, value, color) ->
            val lx = MARGIN + cardW * i
            b.rect(lx, cardTop, lx + cardW - 4f, cardTop + 44f, COLOR_LIGHT_BG)
            b.canvas().drawText(label, lx + 6f, cardTop + 13f, bodyPaint(COLOR_GRAY, 8f))
            b.canvas().drawText(value, lx + 6f, cardTop + 32f, bodyPaint(color, 11f, true))
        }
        b.nl(50f)

        // 1. Accounts
        if (summary.accountBreakdown.isNotEmpty()) {
            b.sectionHeader("1. Account Balances")
            val aw = listOf((PAGE_W - MARGIN * 2) * 0.6f, (PAGE_W - MARGIN * 2) * 0.4f)
            b.drawTableRow(listOf("Account", "Balance"), aw, isHeader = true)
            summary.accountBreakdown.entries.forEachIndexed { i, (name, bal) ->
                b.drawTableRow(listOf(name, fmt(bal)), aw, altBg = i % 2 == 0)
            }
        }

        // 2. Lending
        if (borrowers.isNotEmpty()) {
            b.sectionHeader("2. Lending & Receivables")
            val usable = PAGE_W - MARGIN * 2
            // C21: include `Paid` column so the consolidated PDF reflects
            // the same repayment state users see in-app and in the XLSX
            // export. (Surfaced by the 3.2.2 §3.5 smoke test — a borrower
            // with paid > 0 looked indistinguishable from a fresh loan.)
            val lw = listOf(
                usable*.20f, usable*.13f, usable*.13f, usable*.08f,
                usable*.12f, usable*.10f, usable*.10f, usable*.14f,
            )
            b.drawTableRow(
                listOf("Person","Principal","Paid","Rate","Lent On","Due","Status","Notes"),
                lw, isHeader = true,
            )
            borrowers.forEachIndexed { i, bo ->
                val statusColor = if (bo.status == "Overdue") COLOR_RED else COLOR_BLACK
                b.drawTableRow(
                    listOf(
                        bo.name, fmt(bo.amount), fmt(bo.paid), "${bo.rate}%",
                        bo.date, bo.due.ifBlank{"-"}, bo.status, bo.notes,
                    ),
                    lw, altBg = i % 2 == 0,
                    colors = listOf(
                        COLOR_BLACK, COLOR_BLACK, COLOR_BLACK, COLOR_BLACK,
                        COLOR_BLACK, COLOR_BLACK, statusColor, COLOR_GRAY,
                    ),
                )
            }
        }

        // 3. Investments
        if (investments.isNotEmpty()) {
            b.sectionHeader("3. Investment Portfolio")
            val usable = PAGE_W - MARGIN * 2
            val iw = listOf(usable*.28f, usable*.15f, usable*.17f, usable*.17f, usable*.13f, usable*.10f)
            b.drawTableRow(listOf("Asset","Type","Invested","Current","Growth","Date"), iw, isHeader = true)
            investments.forEachIndexed { i, inv ->
                val growth = inv.currentVal - inv.invested
                b.drawTableRow(
                    listOf(inv.name, inv.type, fmt(inv.invested), fmt(inv.currentVal), fmt(growth), inv.date),
                    iw, altBg = i % 2 == 0,
                    colors = listOf(COLOR_BLACK, COLOR_GRAY, COLOR_BLACK, COLOR_BLACK, if(growth>=0) COLOR_GREEN else COLOR_RED, COLOR_GRAY)
                )
            }
        }

        // 4. Transactions (last 50)
        val recent = transactions.sortedByDescending { it.date }.take(50)
        if (recent.isNotEmpty()) {
            b.sectionHeader("4. Recent Transactions (last 50)")
            val usable = PAGE_W - MARGIN * 2
            val tw = listOf(usable*.13f, usable*.10f, usable*.18f, usable*.27f, usable*.15f, usable*.17f)
            b.drawTableRow(listOf("Date","Type","Category","Description","Amount","Account"), tw, isHeader = true)
            recent.forEachIndexed { i, t ->
                val amtColor = if (t.type.equals("income", ignoreCase = true)) COLOR_GREEN else COLOR_RED
                b.drawTableRow(
                    listOf(t.date, t.type, t.category, t.desc, fmt(t.amount), t.fromAcct.ifBlank{t.toAcct}),
                    tw, altBg = i % 2 == 0,
                    colors = listOf(COLOR_BLACK,COLOR_GRAY,COLOR_BLACK,COLOR_BLACK, amtColor, COLOR_GRAY)
                )
            }
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
    fun generateMoneyFlowPDF(outputStream: OutputStream, flows: List<FlowEntry>) {
        val pdf = PdfDocument()
        val b = PdfBuilder(pdf, outputStream)
        b.startPage()

        b.canvas().drawText("Fynlo", MARGIN, b.y, titlePaint())
        b.nl(26f)
        b.canvas().drawText("Money Flow Report", MARGIN, b.y, bodyPaint(COLOR_GRAY, 14f))
        b.nl(18f)
        b.canvas().drawText("Generated: ${LocalDate.now()}", MARGIN, b.y, bodyPaint(COLOR_GRAY, 10f))
        b.nl(24f)

        val totalIn  = flows.filter { it.flowType == FlowType.INCOME  || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
        val totalOut = flows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY    }.sumOf { it.amount }
        val lentOut  = flows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }
        val net      = totalIn - totalOut

        // Summary cards
        val cardW = (PAGE_W - MARGIN * 2) / 4f
        val cards = listOf(
            Triple("TOTAL INFLOW",  fmt(totalIn),  COLOR_GREEN),
            Triple("TOTAL OUTFLOW", fmt(totalOut), COLOR_RED),
            Triple("LENT OUT",      fmt(lentOut),  COLOR_PRIMARY),
            Triple("NET FLOW",      fmt(net),      if (net >= 0) COLOR_GREEN else COLOR_RED)
        )
        val ct = b.y - LINE_H + 2f
        cards.forEachIndexed { i, (label, value, color) ->
            val lx = MARGIN + cardW * i
            b.rect(lx, ct, lx + cardW - 4f, ct + 44f, COLOR_LIGHT_BG)
            b.canvas().drawText(label, lx + 6f, ct + 13f, bodyPaint(COLOR_GRAY, 8f))
            b.canvas().drawText(value, lx + 6f, ct + 32f, bodyPaint(color, 11f, true))
        }
        b.nl(50f)

        b.sectionHeader("All Flows (${flows.size} entries)")
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
                listOf(f.date, f.flowType.name, f.from, f.to, f.label, fmt(f.amount)),
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
        outstanding: Double
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
        b.nl(24f)

        // Borrower detail table
        b.sectionHeader("Borrower Details")
        val mid = PAGE_W / 2f
        val details = listOf(
            "Borrower Name"   to borrower.name,
            "Phone"           to borrower.phone.ifBlank { "-" },
            "Loan Date"       to borrower.date,
            "Due Date"        to borrower.due.ifBlank { "Not specified" },
            "Principal"       to fmt(borrower.amount),
            "Interest Rate"   to "${borrower.rate}% p.a. (${borrower.type})",
            "Interest Accrued" to fmt(interest),
            "Total Paid"      to fmt(borrower.paid),
            "Outstanding"     to fmt(outstanding)
        )
        details.forEachIndexed { i, (label, value) ->
            if (i % 2 == 0) b.rect(MARGIN, b.y - 12f, PAGE_W - MARGIN.toFloat(), b.y + 4f, Color.rgb(249,250,251))
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
                b.drawTableRow(listOf(t.date, fmt(t.amount), t.notes.ifBlank{"-"}), rw, altBg = i % 2 == 0)
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

    fun generateMoneyFlowCSV(flows: List<FlowEntry>): String {
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
        sb.append("Total Inflow,${fmt(totalIn)}\nTotal Outflow,${fmt(totalOut)}\n")
        sb.append("Lent Out,${fmt(lentOut)}\nNet Flow,${fmt(totalIn - totalOut)}\n")
        return sb.toString()
    }
}
