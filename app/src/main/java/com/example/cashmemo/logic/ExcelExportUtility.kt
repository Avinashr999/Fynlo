package com.example.cashmemo.logic

import com.example.cashmemo.data.model.*
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.Locale

/**
 * Lightweight XLSX writer using native ZIP + OOXML.
 * No Apache POI needed — generates proper .xlsx files that open in Excel and Google Sheets.
 */
object ExcelExportUtility {

    private data class Sheet(val name: String, val rows: List<List<String>>)

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
        val locale = Locale.getDefault()
        fun fmt(v: Double) = String.format(locale, "%.2f", v)

        val sheets = listOf(
            Sheet("Accounts", buildList {
                add(listOf("Name", "Balance", "Type"))
                accounts.forEach { a -> add(listOf(a.name, fmt(a.balance), a.type)) }
            }),
            Sheet("Transactions", buildList {
                add(listOf("Date", "Type", "Amount", "From Account", "To Account", "Category", "Description", "Notes"))
                transactions.sortedByDescending { it.date }.forEach { t ->
                    add(listOf(t.date, t.type, fmt(t.amount), t.fromAcct, t.toAcct, t.category, t.desc, t.notes))
                }
            }),
            Sheet("Lending", buildList {
                add(listOf("Name", "Phone", "Principal", "Rate %", "Interest Type", "Loan Date", "Due Date", "Paid", "Status", "Notes"))
                borrowers.forEach { b ->
                    add(listOf(b.name, b.phone, fmt(b.amount), fmt(b.rate), b.type, b.date, b.due, fmt(b.paid), b.status, b.notes))
                }
            }),
            Sheet("Debts", buildList {
                add(listOf("Name", "Principal", "Rate %", "Interest Type", "Date", "Due Date", "Paid", "Notes"))
                debts.forEach { d ->
                    add(listOf(d.name, fmt(d.amount), fmt(d.rate), d.intType, d.date, d.due, fmt(d.paid), d.notes))
                }
            }),
            Sheet("Investments", buildList {
                add(listOf("Name", "Type", "Invested", "Current Value", "Growth", "Growth %", "Date"))
                investments.forEach { i ->
                    val growth = i.currentVal - i.invested
                    val pct = if (i.invested > 0) growth / i.invested * 100 else 0.0
                    add(listOf(i.name, i.type, fmt(i.invested), fmt(i.currentVal), fmt(growth), fmt(pct) + "%", i.date))
                }
            }),
            Sheet("Loan Repayments", buildList {
                add(listOf("Borrower", "Amount", "Date", "Notes"))
                payments.forEach { p -> add(listOf(p.name, fmt(p.amount), p.date, p.notes)) }
            }),
            Sheet("Debt Repayments", buildList {
                add(listOf("Creditor", "Amount", "Date", "Notes"))
                debtPayments.forEach { p -> add(listOf(p.name, fmt(p.amount), p.date, p.notes)) }
            })
        )

        writeXlsx(outputStream, sheets)
    }

    private fun writeXlsx(out: OutputStream, sheets: List<Sheet>) {
        val zip = ZipOutputStream(out)

        // [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(buildContentTypes(sheets).toByteArray())

        // _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(ROOT_RELS.toByteArray())

        // xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(buildWorkbook(sheets).toByteArray())

        // xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(buildWorkbookRels(sheets).toByteArray())

        // xl/styles.xml
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(STYLES_XML.toByteArray())

        // xl/sharedStrings.xml — collect all strings
        val allStrings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()
        fun strIdx(s: String): Int = stringIndex.getOrPut(s) { allStrings.add(s); allStrings.size - 1 }

        // Pre-index all strings
        sheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell -> strIdx(cell) }
            }
        }

        // Write each worksheet
        sheets.forEachIndexed { i, sheet ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
            zip.write(buildSheet(sheet, i == 0, ::strIdx).toByteArray())
        }

        // Write shared strings
        zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
        zip.write(buildSharedStrings(allStrings).toByteArray())

        zip.finish()
    }

    private fun buildContentTypes(sheets: List<Sheet>): String {
        val overrides = sheets.mapIndexed { i, _ ->
            """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  $overrides
</Types>"""
    }

    private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbook(sheets: List<Sheet>): String {
        val sheetEls = sheets.mapIndexed { i, s ->
            """<sheet name="${xmlEscape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>$sheetEls</sheets>
</workbook>"""
    }

    private fun buildWorkbookRels(sheets: List<Sheet>): String {
        val rels = sheets.mapIndexed { i, _ ->
            """<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
        }.joinToString("\n")
        val ssRel = """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>"""
        val stylesRel = """<Relationship Id="rId${sheets.size + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>"""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $rels
  $ssRel
  $stylesRel
</Relationships>"""
    }

    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></patternFill></fill></fills>
  <borders><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""

    private fun buildSheet(sheet: Sheet, applyHeaderStyle: Boolean, strIdx: (String) -> Int): String {
        val colRef = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O")
        val rows = sheet.rows.mapIndexed { rowIdx, row ->
            val isHeader = rowIdx == 0
            val cells = row.mapIndexed { colIdx, cell ->
                val col = colRef.getOrElse(colIdx) { "A" }
                val ref = "$col${rowIdx + 1}"
                val si = strIdx(cell)
                val style = if (isHeader) """ s="1"""" else ""
                """<c r="$ref" t="s"$style><v>$si</v></c>"""
            }.joinToString("")
            """<row r="${rowIdx + 1}">$cells</row>"""
        }.joinToString("\n")

        val maxCol = colRef.getOrElse((sheet.rows.maxOfOrNull { it.size } ?: 1) - 1) { "A" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:${maxCol}${sheet.rows.size}"/>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun buildSharedStrings(strings: List<String>): String {
        val items = strings.joinToString("") { "<si><t>${xmlEscape(it)}</t></si>" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">$items</sst>"""
    }

    private fun xmlEscape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
