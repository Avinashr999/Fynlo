package com.example.cashmemo.logic

import com.example.cashmemo.data.model.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.util.Locale

object ExcelExportUtility {

    fun generateFullBackup(
        outputStream: OutputStream,
        accounts: List<Account>,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        debts: List<Debt>,
        investments: List<Investment>,
        payments: List<Payment>,
        debtPayments: List<DebtPayment>
    ) {
        val wb     = XSSFWorkbook()
        val locale = Locale.getDefault()

        // Header style
        fun headerStyle(): CellStyle {
            val style = wb.createCellStyle()
            val font  = wb.createFont()
            font.bold      = true
            font.fontHeightInPoints = 11
            style.setFont(font)
            style.fillForegroundColor = IndexedColors.TEAL.index
            style.fillPattern = FillPatternType.SOLID_FOREGROUND
            style.borderBottom = BorderStyle.THIN
            return style
        }

        fun Row.addHeader(vararg cols: String, style: CellStyle) {
            cols.forEachIndexed { i, c -> createCell(i).apply { setCellValue(c); cellStyle = style } }
        }

        fun Sheet.autoSize(count: Int) = repeat(count) { autoSizeColumn(it) }

        // ── Accounts ──────────────────────────────────────────────────────
        wb.createSheet("Accounts").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("ID", "Name", "Balance", "Type", style = hs)
            accounts.forEachIndexed { i, a ->
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(a.id)
                    createCell(1).setCellValue(a.name)
                    createCell(2).setCellValue(a.balance)
                    createCell(3).setCellValue(a.type)
                }
            }
            sheet.autoSize(4)
        }

        // ── Transactions ──────────────────────────────────────────────────
        wb.createSheet("Transactions").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Date", "Type", "Amount", "From", "To", "Category", "Description", "Notes", style = hs)
            transactions.sortedByDescending { it.date }.forEachIndexed { i, t ->
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(t.date)
                    createCell(1).setCellValue(t.type)
                    createCell(2).setCellValue(t.amount)
                    createCell(3).setCellValue(t.fromAcct)
                    createCell(4).setCellValue(t.toAcct)
                    createCell(5).setCellValue(t.category)
                    createCell(6).setCellValue(t.desc)
                    createCell(7).setCellValue(t.notes)
                }
            }
            sheet.autoSize(8)
        }

        // ── Borrowers (Lending) ───────────────────────────────────────────
        wb.createSheet("Lending").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Name", "Phone", "Principal", "Rate%", "Interest Type",
                "Loan Date", "Due Date", "Paid", "Outstanding", "Status", style = hs)
            borrowers.forEachIndexed { i, b ->
                val interest = InterestEngine.calcIntAccrued(b.amount, b.rate, b.date, b.type, b.due)
                val outstanding = InterestEngine.calcOutstanding(b.amount, interest, b.paid)
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(b.name)
                    createCell(1).setCellValue(b.phone)
                    createCell(2).setCellValue(b.amount)
                    createCell(3).setCellValue(b.rate)
                    createCell(4).setCellValue(b.type)
                    createCell(5).setCellValue(b.date)
                    createCell(6).setCellValue(b.due)
                    createCell(7).setCellValue(b.paid)
                    createCell(8).setCellValue(outstanding)
                    createCell(9).setCellValue(b.status)
                }
            }
            sheet.autoSize(10)
        }

        // ── Debts ─────────────────────────────────────────────────────────
        wb.createSheet("Debts").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Name", "Principal", "Rate%", "Interest Type",
                "Date", "Due Date", "Paid", "Notes", style = hs)
            debts.forEachIndexed { i, d ->
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(d.name)
                    createCell(1).setCellValue(d.amount)
                    createCell(2).setCellValue(d.rate)
                    createCell(3).setCellValue(d.intType)
                    createCell(4).setCellValue(d.date)
                    createCell(5).setCellValue(d.due)
                    createCell(6).setCellValue(d.paid)
                    createCell(7).setCellValue(d.notes)
                }
            }
            sheet.autoSize(8)
        }

        // ── Investments ───────────────────────────────────────────────────
        wb.createSheet("Investments").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Name", "Type", "Invested", "Current Value", "Growth", "Growth%", "Date", style = hs)
            investments.forEachIndexed { i, inv ->
                val growth = inv.currentVal - inv.invested
                val growthPct = if (inv.invested > 0) (growth / inv.invested) * 100 else 0.0
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(inv.name)
                    createCell(1).setCellValue(inv.type)
                    createCell(2).setCellValue(inv.invested)
                    createCell(3).setCellValue(inv.currentVal)
                    createCell(4).setCellValue(growth)
                    createCell(5).setCellValue(String.format(locale, "%.2f%%", growthPct))
                    createCell(6).setCellValue(inv.date)
                }
            }
            sheet.autoSize(7)
        }

        // ── Loan Repayments ───────────────────────────────────────────────
        wb.createSheet("Loan Repayments").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Borrower", "Amount", "Date", "Notes", style = hs)
            payments.forEachIndexed { i, p ->
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(p.name)
                    createCell(1).setCellValue(p.amount)
                    createCell(2).setCellValue(p.date)
                    createCell(3).setCellValue(p.notes)
                }
            }
            sheet.autoSize(4)
        }

        // ── Debt Repayments ───────────────────────────────────────────────
        wb.createSheet("Debt Repayments").let { sheet ->
            val hs = headerStyle()
            sheet.createRow(0).addHeader("Creditor", "Amount", "Date", "Notes", style = hs)
            debtPayments.forEachIndexed { i, p ->
                sheet.createRow(i + 1).apply {
                    createCell(0).setCellValue(p.name)
                    createCell(1).setCellValue(p.amount)
                    createCell(2).setCellValue(p.date)
                    createCell(3).setCellValue(p.notes)
                }
            }
            sheet.autoSize(4)
        }

        wb.write(outputStream)
        wb.close()
    }
}
