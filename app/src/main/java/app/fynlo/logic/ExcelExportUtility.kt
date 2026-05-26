package app.fynlo.logic

import app.fynlo.data.model.*
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.Locale

/**
 * Lightweight XLSX writer using native ZIP + OOXML.
 * No Apache POI needed — generates proper .xlsx files that open in Excel and Google Sheets.
 *
 * C08 Stage 4 (3.2.18) — fixed the load-bearing "amounts as strings" bug
 * that was preventing Excel from summing/sorting/charting numeric columns.
 *
 *   - Before: every cell emitted as `<c t="s"><v>idx</v></c>` — shared
 *     string lookup. Account balances looked like "15000.00" but Excel
 *     saw them as text, so SUM(B:B) returned 0 and sorting was alphabetic.
 *   - After:  numeric cells emit as `<c t="n" s="2"><v>15000.00</v></c>` —
 *     raw double value with a number-format style. SUM works, sorting is
 *     numeric, charts pick up the column correctly.
 *
 * The migration introduces a small [Cell] sealed class — `Cell.Text` for
 * strings (still goes through shared-string interning), `Cell.Number` for
 * amounts. The per-sheet builders pick the right Cell type for each column.
 */
object ExcelExportUtility {

    /**
     * Per-cell content tag. Stage 4 changed the row model from
     * `List<String>` to `List<Cell>` so we can distinguish numbers from
     * text at the cell-emission level.
     */
    sealed class Cell {
        data class Text(val value: String) : Cell()
        data class Number(val value: Double) : Cell()
    }

    private data class Sheet(val name: String, val rows: List<List<Cell>>)

    /**
     * Style XF indices defined in [STYLES_XML]. Reference these from
     * cells via the `s="N"` attribute.
     */
    private const val STYLE_DEFAULT = 0      // plain text / general
    private const val STYLE_HEADER = 1       // bold white on emerald (header rows)
    private const val STYLE_NUMBER = 2       // numeric, 2 decimal places, comma grouping

    fun generateFullBackup(
        outputStream: OutputStream,
        accounts: List<Account>,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        debts: List<Debt>,
        investments: List<Investment>,
        payments: List<Payment>,
        debtPayments: List<DebtPayment>,
        // C02 step 5: surface when the figures below were last recalculated.
        // 0L = no recalc has ever run; rendered as "—".
        lastRecalcAt: Long = 0L,
    ) {
        // C02: human-readable timestamp for the Metadata sheet.
        val recalcText = if (lastRecalcAt > 0L) {
            val zone = java.time.ZoneId.systemDefault()
            val dt   = java.time.Instant.ofEpochMilli(lastRecalcAt).atZone(zone)
            val fmtR = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
            dt.format(fmtR)
        } else {
            "—"
        }

        // ── Short-hand constructors so the per-sheet builders read cleanly ──
        // T("foo")  →  Cell.Text("foo")
        // N(15000.0) →  Cell.Number(15000.0)
        fun t(v: String): Cell = Cell.Text(v)
        fun n(v: Double): Cell = Cell.Number(v)

        val sheets = listOf(
            Sheet("Metadata", buildList {
                add(listOf(t("Key"), t("Value")))
                add(listOf(t("Export type"),     t("Full backup")))
                add(listOf(t("Generated"),       t(java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)))))
                add(listOf(t("Recalculated at"), t(recalcText)))
            }),
            Sheet("Accounts", buildList {
                add(listOf(t("Name"), t("Balance"), t("Type")))
                accounts.forEach { a -> add(listOf(t(a.name), n(a.balance), t(a.type))) }
            }),
            Sheet("Transactions", buildList {
                add(listOf(t("Date"), t("Type"), t("Amount"), t("From Account"), t("To Account"),
                    t("Category"), t("Description"), t("Notes")))
                transactions.sortedByDescending { it.date }.forEach { tx ->
                    add(listOf(
                        t(tx.date), t(tx.type), n(tx.amount),
                        t(tx.fromAcct), t(tx.toAcct), t(tx.category),
                        t(tx.desc), t(tx.notes),
                    ))
                }
            }),
            Sheet("Lending", buildList {
                add(listOf(t("Name"), t("Phone"), t("Principal"), t("Rate %"), t("Interest Type"),
                    t("Loan Date"), t("Due Date"), t("Paid"), t("Status"), t("Notes")))
                borrowers.forEach { b ->
                    add(listOf(
                        t(b.name), t(b.phone), n(b.amount), n(b.rate), t(b.type),
                        t(b.date), t(b.due), n(b.paid), t(b.status), t(b.notes),
                    ))
                }
            }),
            Sheet("Debts", buildList {
                add(listOf(t("Name"), t("Principal"), t("Rate %"), t("Interest Type"),
                    t("Date"), t("Due Date"), t("Paid"), t("Notes")))
                debts.forEach { d ->
                    add(listOf(
                        t(d.name), n(d.amount), n(d.rate), t(d.intType),
                        t(d.date), t(d.due), n(d.paid), t(d.notes),
                    ))
                }
            }),
            Sheet("Investments", buildList {
                add(listOf(t("Name"), t("Type"), t("Invested"), t("Current Value"),
                    t("Growth"), t("Growth %"), t("Date")))
                investments.forEach { i ->
                    val growth = i.currentVal - i.invested
                    val pct = if (i.invested > 0) growth / i.invested * 100 else 0.0
                    add(listOf(
                        t(i.name), t(i.type),
                        n(i.invested), n(i.currentVal), n(growth), n(pct),
                        t(i.date),
                    ))
                }
            }),
            Sheet("Loan Repayments", buildList {
                add(listOf(t("Borrower"), t("Amount"), t("Date"), t("Notes")))
                payments.forEach { p -> add(listOf(t(p.name), n(p.amount), t(p.date), t(p.notes))) }
            }),
            Sheet("Debt Repayments", buildList {
                add(listOf(t("Creditor"), t("Amount"), t("Date"), t("Notes")))
                debtPayments.forEach { p -> add(listOf(t(p.name), n(p.amount), t(p.date), t(p.notes))) }
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

        // xl/sharedStrings.xml — collect text-cell strings only.
        // Number cells emit their value directly into <v>; they don't
        // need shared-string interning.
        val allStrings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()
        fun strIdx(s: String): Int = stringIndex.getOrPut(s) { allStrings.add(s); allStrings.size - 1 }

        // Pre-index all TEXT cells (skip Number cells — they're not strings).
        sheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell is Cell.Text) strIdx(cell.value)
                }
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

    /**
     * Styles definition. Indices used in cell `s="N"` attribute:
     *
     *   - `s="0"` ([STYLE_DEFAULT])  — plain text / general
     *   - `s="1"` ([STYLE_HEADER])   — header row: bold white text on emerald fill
     *   - `s="2"` ([STYLE_NUMBER])   — numeric, 2 decimals, comma grouping
     *                                  (`#,##0.00`)
     *
     * The custom numFmt `164` (`#,##0.00`) is what unlocks Excel's
     * sum/sort/chart behaviour on amount columns. We deliberately use the
     * locale-neutral Western thousand-comma grouping for the cell format —
     * if the user wants Indian lakh-crore grouping in their spreadsheet
     * they can apply a custom number format from Excel's UI. The
     * load-bearing change is that the underlying value is a NUMBER, not
     * a STRING; once that's right, format is the user's preference.
     */
    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00"/>
  </numFmts>
  <fonts><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
  <fills><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></patternFill></fill></fills>
  <borders><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""

    private fun buildSheet(sheet: Sheet, applyHeaderStyle: Boolean, strIdx: (String) -> Int): String {
        val colRef = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O")
        val rows = sheet.rows.mapIndexed { rowIdx, row ->
            val isHeader = rowIdx == 0
            val cells = row.mapIndexed { colIdx, cell ->
                val col = colRef.getOrElse(colIdx) { "A" }
                val ref = "$col${rowIdx + 1}"
                when (cell) {
                    is Cell.Text -> {
                        // Shared-string lookup; header rows get the emerald
                        // header style, body rows use the default style.
                        val si = strIdx(cell.value)
                        val style = if (isHeader) " s=\"$STYLE_HEADER\"" else ""
                        """<c r="$ref" t="s"$style><v>$si</v></c>"""
                    }
                    is Cell.Number -> {
                        // Raw numeric value, NOT through shared strings.
                        // Headers can't be numbers in practice (column
                        // labels), but the conditional is defensive:
                        // a Number in row 0 still gets number formatting.
                        val style = if (isHeader) STYLE_HEADER else STYLE_NUMBER
                        // Format the double with US locale so the decimal
                        // separator is always `.` regardless of device
                        // locale — required by the OOXML spec for `t="n"`.
                        val v = String.format(Locale.US, "%.2f", cell.value)
                        """<c r="$ref" t="n" s="$style"><v>$v</v></c>"""
                    }
                }
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
