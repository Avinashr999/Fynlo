package com.example.cashmemo.logic

import com.example.cashmemo.data.model.*
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import java.io.OutputStream
import java.util.Locale

object ExportUtility {

    fun generateCSV(
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ): String {
        val builder = StringBuilder()
        
        // Helper to escape CSV values
        fun escape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

        // Master Ledger with high detail
        builder.append("Date,Type,Name/Asset,Category,Amount/Principal,Rate(%),Due Date,Current Value,Notes\n")
        
        transactions.forEach { txn ->
            builder.append("${escape(txn.date)},${escape(txn.type)},\"\",${escape(txn.category)},${txn.amount},\"\",\"\",\"\",${escape(txn.notes)}\n")
        }
        
        borrowers.forEach { b ->
            builder.append("${escape(b.date)},\"Lending\",${escape(b.name)},\"Money Lending\",${b.amount},${b.rate},${escape(b.due)},\"\",${escape(b.notes)}\n")
        }

        investments.forEach { i ->
            builder.append("${escape(i.date)},\"Investment\",${escape(i.name)},${escape(i.type)},${i.invested},\"\",\"\",${i.currentVal},${escape(i.notes)}\n")
        }

        return builder.toString()
    }

    fun generatePDF(
        outputStream: OutputStream,
        summary: FinancialSummary,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        investments: List<Investment>
    ) {
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        document.add(Paragraph("Cash Memo - Comprehensive Financial Report").setBold().setFontSize(18f))
        document.add(Paragraph("Generated on: ${java.time.LocalDate.now()}"))
        
        // 1. Summary
        document.add(Paragraph("\n1. FINANCIAL SNAPSHOT").setBold().setFontSize(14f))
        document.add(Paragraph("Net Worth: ₹ ${String.format(Locale.getDefault(), "%,.2f", summary.netWorth)}"))
        document.add(Paragraph("Total Liquid Cash: ₹ ${String.format(Locale.getDefault(), "%,.2f", summary.totalCash)}"))
        document.add(Paragraph("Investment ROI: ₹ ${String.format(Locale.getDefault(), "%,.2f", summary.investmentGrowth)}"))

        // 2. Lending Table
        document.add(Paragraph("\n2. LENDING & RECEIVABLES").setBold().setFontSize(14f))
        val lendingTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 15f, 15f, 15f, 30f))).useAllAvailableWidth()
        lendingTable.addHeaderCell("Person")
        lendingTable.addHeaderCell("Principal")
        lendingTable.addHeaderCell("Rate")
        lendingTable.addHeaderCell("Lent Date")
        lendingTable.addHeaderCell("Purpose/Notes")
        borrowers.forEach { b ->
            lendingTable.addCell(b.name)
            lendingTable.addCell("₹${b.amount.toInt()}")
            lendingTable.addCell("${b.rate}%")
            lendingTable.addCell(b.date)
            lendingTable.addCell(b.notes)
        }
        document.add(lendingTable)

        // 3. Investment Table
        document.add(Paragraph("\n3. ASSET ALLOCATION (INVESTMENTS)").setBold().setFontSize(14f))
        val investTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 20f, 20f, 30f))).useAllAvailableWidth()
        investTable.addHeaderCell("Asset Name")
        investTable.addHeaderCell("Type")
        investTable.addHeaderCell("Current Value")
        investTable.addHeaderCell("Details/Notes")
        investments.forEach { i ->
            investTable.addCell(i.name)
            investTable.addCell(i.type)
            investTable.addCell("₹${i.currentVal.toInt()}")
            investTable.addCell(i.notes)
        }
        document.add(investTable)

        // 4. Transactions Table
        document.add(Paragraph("\n4. RECENT TRANSACTIONS").setBold().setFontSize(14f))
        val transTable = Table(UnitValue.createPercentArray(floatArrayOf(20f, 20f, 40f, 20f))).useAllAvailableWidth()
        transTable.addHeaderCell("Date")
        transTable.addHeaderCell("Category")
        transTable.addHeaderCell("Description")
        transTable.addHeaderCell("Amount")
        transactions.take(30).forEach { txn ->
            transTable.addCell(txn.date)
            transTable.addCell(txn.category)
            transTable.addCell(txn.desc)
            transTable.addCell("₹${txn.amount.toInt()}")
        }
        document.add(transTable)
        
        document.close()
    }
}