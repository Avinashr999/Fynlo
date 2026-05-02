package com.example.cashmemo.logic

import com.example.cashmemo.data.model.*
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

object ExportUtility {

    private val locale = Locale.getDefault()
    private fun fmt(v: Double) = "Rs ${String.format(locale, "%,.2f", v)}"
    private val PRIMARY = DeviceRgb(59, 130, 246)    // #3b82f6
    private val LIGHT   = DeviceRgb(239, 246, 255)   // light blue bg
    private val RED     = DeviceRgb(239, 68, 68)      // #ef4444
    private val GREEN   = DeviceRgb(16, 185, 129)     // #10b981

    fun generateCSV(
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ): String {
        val sb = StringBuilder()
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""

        sb.append("CASH MEMO - FINANCIAL EXPORT\n")
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

    fun generatePDF(
        outputStream: OutputStream,
        summary: FinancialSummary,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ) {
        val writer   = PdfWriter(outputStream)
        val pdf      = PdfDocument(writer)
        val document = Document(pdf)

        // ── Cover header ─────────────────────────────────────────────────────
        document.add(
            Paragraph("Cash Memo")
                .setBold().setFontSize(28f)
                .setFontColor(PRIMARY)
        )
        document.add(
            Paragraph("Comprehensive Financial Report")
                .setFontSize(14f).setFontColor(ColorConstants.DARK_GRAY)
        )
        document.add(
            Paragraph("Generated: ${LocalDate.now()} | Personal Finance Tracker")
                .setFontSize(10f).setFontColor(ColorConstants.GRAY)
        )
        document.add(Paragraph("\n"))

        // ── Net Worth Summary ─────────────────────────────────────────────────
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        fun summaryCell(label: String, value: String, color: DeviceRgb): Cell {
            val cell = Cell().setBackgroundColor(LIGHT).setPadding(8f)
            cell.add(Paragraph(label).setFontSize(9f).setFontColor(ColorConstants.GRAY))
            cell.add(Paragraph(value).setBold().setFontSize(12f).setFontColor(color))
            return cell
        }
        summaryTable.addCell(summaryCell("NET WORTH",      fmt(summary.netWorth),         if (summary.netWorth >= 0) GREEN else RED))
        summaryTable.addCell(summaryCell("TOTAL ASSETS",   fmt(summary.totalAssets),      PRIMARY))
        summaryTable.addCell(summaryCell("TOTAL CASH",     fmt(summary.totalCash),        PRIMARY))
        summaryTable.addCell(summaryCell("INVEST. GROWTH", fmt(summary.investmentGrowth), GREEN))
        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // ── Helper for section headers ────────────────────────────────────────
        fun sectionHeader(title: String) = Paragraph(title)
            .setBold().setFontSize(13f).setFontColor(PRIMARY)
            .setMarginTop(12f).setMarginBottom(6f)

        fun headerCell(text: String) = Cell()
            .setBackgroundColor(PRIMARY)
            .add(Paragraph(text).setBold().setFontSize(10f).setFontColor(ColorConstants.WHITE))
            .setPadding(6f)

        fun dataCell(text: String) = Cell()
            .add(Paragraph(text).setFontSize(10f))
            .setPadding(5f)

        // ── 1. Account Balances ───────────────────────────────────────────────
        if (summary.accountBreakdown.isNotEmpty()) {
            document.add(sectionHeader("1. Account Balances"))
            val tbl = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            tbl.addHeaderCell(headerCell("Account"))
            tbl.addHeaderCell(headerCell("Balance"))
            summary.accountBreakdown.forEach { (name, bal) ->
                tbl.addCell(dataCell(name))
                tbl.addCell(dataCell(fmt(bal)))
            }
            document.add(tbl)
        }

        // ── 2. Lending ────────────────────────────────────────────────────────
        if (borrowers.isNotEmpty()) {
            document.add(sectionHeader("2. Lending & Receivables"))
            val tbl = Table(UnitValue.createPercentArray(floatArrayOf(22f, 15f, 10f, 13f, 12f, 12f, 16f))).useAllAvailableWidth()
            listOf("Person", "Principal", "Rate", "Lent On", "Due Date", "Status", "Notes").forEach { tbl.addHeaderCell(headerCell(it)) }
            borrowers.forEach { b ->
                tbl.addCell(dataCell(b.name))
                tbl.addCell(dataCell(fmt(b.amount)))
                tbl.addCell(dataCell("${b.rate}%"))
                tbl.addCell(dataCell(b.date))
                tbl.addCell(dataCell(b.due.ifBlank { "-" }))
                val statusCell = dataCell(b.status)
                if (b.status == "Overdue") statusCell.setFontColor(RED)
                tbl.addCell(statusCell)
                tbl.addCell(dataCell(b.notes.take(40)))
            }
            document.add(tbl)
        }

        // ── 3. Investments ────────────────────────────────────────────────────
        if (investments.isNotEmpty()) {
            document.add(sectionHeader("3. Investment Portfolio"))
            val tbl = Table(UnitValue.createPercentArray(floatArrayOf(28f, 15f, 17f, 17f, 13f, 10f))).useAllAvailableWidth()
            listOf("Asset", "Type", "Invested", "Current Value", "Growth", "Date").forEach { tbl.addHeaderCell(headerCell(it)) }
            investments.forEach { i ->
                val growth = i.currentVal - i.invested
                tbl.addCell(dataCell(i.name))
                tbl.addCell(dataCell(i.type))
                tbl.addCell(dataCell(fmt(i.invested)))
                tbl.addCell(dataCell(fmt(i.currentVal)))
                val growthCell = dataCell(fmt(growth))
                growthCell.setFontColor(if (growth >= 0) GREEN else RED)
                tbl.addCell(growthCell)
                tbl.addCell(dataCell(i.date))
            }
            document.add(tbl)
        }

        // ── 4. Recent Transactions ────────────────────────────────────────────
        val recent = transactions.sortedByDescending { it.date }.take(50)
        if (recent.isNotEmpty()) {
            document.add(sectionHeader("4. Recent Transactions (last 50)"))
            val tbl = Table(UnitValue.createPercentArray(floatArrayOf(15f, 12f, 22f, 28f, 13f, 10f))).useAllAvailableWidth()
            listOf("Date", "Type", "Category", "Description", "Amount", "Account").forEach { tbl.addHeaderCell(headerCell(it)) }
            recent.forEach { t ->
                tbl.addCell(dataCell(t.date))
                tbl.addCell(dataCell(t.type))
                tbl.addCell(dataCell(t.category))
                tbl.addCell(dataCell(t.desc.take(35)))
                val amtCell = dataCell(fmt(t.amount))
                amtCell.setFontColor(if (t.type.equals("income", ignoreCase = true)) GREEN else RED)
                tbl.addCell(amtCell)
                tbl.addCell(dataCell(t.fromAcct.ifBlank { t.toAcct }))
            }
            document.add(tbl)
        }

        // ── Footer ────────────────────────────────────────────────────────────
        document.add(Paragraph("\n"))
        document.add(
            Paragraph("Generated by Cash Memo v2.2 | ${LocalDate.now()}")
                .setFontSize(9f).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
        )

        document.close()
    }

    fun generateMoneyFlowCSV(flows: List<com.example.cashmemo.ui.screens.FlowEntry>): String {
        val sb = StringBuilder()
        sb.append("CASH MEMO - MONEY FLOW REPORT\n")
        sb.append("Generated: ${LocalDate.now()}\n\n")
        sb.append("Date,Type,From,To,Label,Amount\n")
        flows.forEach { f ->
            fun esc(v: String) = "\"${v.replace("\"", "\"\"\"")}\""
            sb.append("${esc(f.date)},${f.flowType},${esc(f.from)},${esc(f.to)},${esc(f.label)},${f.amount}\n")
        }
        // Summary
        val totalIn  = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.INCOME || it.flowType == com.example.cashmemo.ui.screens.FlowType.DEBT_RECEIVED }.sumOf { it.amount }
        val totalOut = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.EXPENSE || it.flowType == com.example.cashmemo.ui.screens.FlowType.DEBT_REPAY }.sumOf { it.amount }
        val lentOut  = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.LENDING }.sumOf { it.amount }
        sb.append("\n--- SUMMARY ---\n")
        sb.append("Total Inflow,${fmt(totalIn)}\n")
        sb.append("Total Outflow,${fmt(totalOut)}\n")
        sb.append("Lent Out,${fmt(lentOut)}\n")
        sb.append("Net Flow,${fmt(totalIn - totalOut)}\n")
        return sb.toString()
    }

    fun generateMoneyFlowPDF(outputStream: OutputStream, flows: List<com.example.cashmemo.ui.screens.FlowEntry>) {
        val writer   = PdfWriter(outputStream)
        val pdf      = PdfDocument(writer)
        val document = Document(pdf)

        document.add(Paragraph("Cash Memo").setBold().setFontSize(24f).setFontColor(PRIMARY))
        document.add(Paragraph("Money Flow Report").setFontSize(14f).setFontColor(ColorConstants.DARK_GRAY))
        document.add(Paragraph("Generated: ${LocalDate.now()}").setFontSize(10f).setFontColor(ColorConstants.GRAY))
        document.add(Paragraph("\n"))

        // Summary
        val totalIn  = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.INCOME || it.flowType == com.example.cashmemo.ui.screens.FlowType.DEBT_RECEIVED }.sumOf { it.amount }
        val totalOut = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.EXPENSE || it.flowType == com.example.cashmemo.ui.screens.FlowType.DEBT_REPAY }.sumOf { it.amount }
        val lentOut  = flows.filter { it.flowType == com.example.cashmemo.ui.screens.FlowType.LENDING }.sumOf { it.amount }
        val net      = totalIn - totalOut

        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        fun summaryCell(label: String, value: String, color: DeviceRgb): Cell {
            val cell = Cell().setBackgroundColor(LIGHT).setPadding(8f)
            cell.add(Paragraph(label).setFontSize(9f).setFontColor(ColorConstants.GRAY))
            cell.add(Paragraph(value).setBold().setFontSize(12f).setFontColor(color))
            return cell
        }
        summaryTable.addCell(summaryCell("TOTAL INFLOW",  fmt(totalIn),  GREEN))
        summaryTable.addCell(summaryCell("TOTAL OUTFLOW", fmt(totalOut), RED))
        summaryTable.addCell(summaryCell("LENT OUT",      fmt(lentOut),  PRIMARY))
        summaryTable.addCell(summaryCell("NET FLOW",      fmt(net),      if (net >= 0) GREEN else RED))
        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // Flow table
        document.add(Paragraph("All Flows (${flows.size} entries)").setBold().setFontSize(13f).setFontColor(PRIMARY).setMarginBottom(6f))
        val tbl = Table(UnitValue.createPercentArray(floatArrayOf(14f, 14f, 22f, 22f, 18f, 10f))).useAllAvailableWidth()
        listOf("Date", "Type", "From", "To", "Label", "Amount").forEach { h ->
            tbl.addHeaderCell(Cell().setBackgroundColor(PRIMARY).add(Paragraph(h).setBold().setFontSize(10f).setFontColor(ColorConstants.WHITE)).setPadding(5f))
        }
        flows.forEach { f ->
            val color = when (f.flowType) {
                com.example.cashmemo.ui.screens.FlowType.INCOME        -> GREEN
                com.example.cashmemo.ui.screens.FlowType.EXPENSE       -> RED
                com.example.cashmemo.ui.screens.FlowType.DEBT_REPAY    -> RED
                else -> PRIMARY
            }
            tbl.addCell(Cell().add(Paragraph(f.date).setFontSize(9f)).setPadding(4f))
            tbl.addCell(Cell().add(Paragraph(f.flowType.name).setFontSize(9f)).setPadding(4f))
            tbl.addCell(Cell().add(Paragraph(f.from.take(25)).setFontSize(9f)).setPadding(4f))
            tbl.addCell(Cell().add(Paragraph(f.to.take(25)).setFontSize(9f)).setPadding(4f))
            tbl.addCell(Cell().add(Paragraph(f.label.take(25)).setFontSize(9f)).setPadding(4f))
            tbl.addCell(Cell().add(Paragraph(fmt(f.amount)).setFontSize(9f).setFontColor(color)).setPadding(4f))
        }
        document.add(tbl)
        document.add(Paragraph("\nGenerated by Cash Memo | ${LocalDate.now()}").setFontSize(9f).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.close()
    }

    fun generateLoanStatementPDF(
        outputStream: OutputStream,
        borrower: com.example.cashmemo.data.model.Borrower,
        repayments: List<com.example.cashmemo.data.model.Transaction>,
        interest: Double,
        outstanding: Double
    ) {
        val writer   = PdfWriter(outputStream)
        val pdf      = PdfDocument(writer)
        val document = Document(pdf)
        val locale   = java.util.Locale.getDefault()

        // Header
        document.add(Paragraph("LOAN STATEMENT").setBold().setFontSize(20f).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("Cash Memo | ${LocalDate.now()}").setFontSize(10f).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("\n"))

        // Borrower details
        val detailTable = Table(floatArrayOf(1f, 1f)).setWidth(UnitValue.createPercentValue(100f))
        fun addRow(label: String, value: String) {
            detailTable.addCell(Cell().add(Paragraph(label).setBold().setFontSize(10f)).setBorder(Border.NO_BORDER))
            detailTable.addCell(Cell().add(Paragraph(value).setFontSize(10f)).setBorder(Border.NO_BORDER))
        }
        addRow("Borrower Name", borrower.name)
        addRow("Phone", borrower.phone.ifBlank { "-" })
        addRow("Loan Date", borrower.date)
        addRow("Due Date", borrower.due.ifBlank { "Not specified" })
        addRow("Principal", "\u20B9 ${String.format(locale, "%,.2f", borrower.amount)}")
        addRow("Interest Rate", "${borrower.rate}% p.a. (${borrower.type})")
        addRow("Interest Accrued", "\u20B9 ${String.format(locale, "%,.2f", interest)}")
        addRow("Total Paid", "\u20B9 ${String.format(locale, "%,.2f", borrower.paid)}")
        addRow("Outstanding", "\u20B9 ${String.format(locale, "%,.2f", outstanding)}")
        document.add(detailTable)
        document.add(Paragraph("\n"))

        // Repayment history
        if (repayments.isNotEmpty()) {
            document.add(Paragraph("Repayment History").setBold().setFontSize(14f))
            val tbl = Table(floatArrayOf(2f, 2f, 3f)).setWidth(UnitValue.createPercentValue(100f))
            listOf("Date", "Amount", "Notes").forEach {
                tbl.addHeaderCell(Cell().add(Paragraph(it).setBold().setFontSize(10f)).setBackgroundColor(ColorConstants.LIGHT_GRAY))
            }
            repayments.sortedByDescending { it.date }.forEach { t ->
                tbl.addCell(Cell().add(Paragraph(t.date).setFontSize(9f)))
                tbl.addCell(Cell().add(Paragraph("\u20B9 ${String.format(locale, "%,.2f", t.amount)}").setFontSize(9f)))
                tbl.addCell(Cell().add(Paragraph(t.notes.ifBlank { "-" }).setFontSize(9f)))
            }
            document.add(tbl)
        } else {
            document.add(Paragraph("No repayments recorded yet.").setFontSize(10f).setFontColor(ColorConstants.GRAY))
        }

        document.add(Paragraph("\nThis is a computer-generated statement.").setFontSize(8f).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.close()
    }
}