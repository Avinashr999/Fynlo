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
}