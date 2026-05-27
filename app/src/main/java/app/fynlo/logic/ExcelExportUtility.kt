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
 * **C08 Stage 4 (3.2.18)** fixed the load-bearing "amounts as strings" bug
 * that was preventing Excel from summing/sorting/charting numeric columns.
 *
 * **C21 Stage 4 (3.2.38)** the rest of the audit §C21 XLSX overhaul:
 *  - **#12** Currency-format numeric cells. `Cell.Currency` value type uses
 *    a custom numFmt `[$<sym>-409]#,##0.00;[Red]-[$<sym>-409]#,##0.00` so
 *    amounts read as proper currency in Excel/Sheets with Indian / US /
 *    Euro / etc. symbol per the active currencyCode.
 *  - **#13** Negative-red via the `;[Red]-` half of the same numFmt —
 *    Excel-native; no separate conditional-formatting block needed.
 *  - **#14** Frozen first row + auto-filter on every data sheet. Per-sheet
 *    `freezeHeader` + `autoFilterCols` flags.
 *  - **#15** Totals rows on Accounts / Lending / Debts. Per-sheet
 *    `totalsCols` indicates which columns get a `SUM(...)` formula; the
 *    leftmost label cell shows "Total".
 *  - **#16** "Summary" as the first sheet — KPI rows mirroring the PDF
 *    cards (Net Worth, Total Assets, Total Liabilities, Cash, Investments,
 *    Invest Growth, Monthly Income, Monthly Expense, Net Cash Flow,
 *    Total Lent Out).
 *
 * Non-`Currency` numeric cells (% rates, IDs) continue to use the plain
 * `#,##0.00` numFmt — currency formatting only applies where the value
 * actually is a currency amount.
 *
 * Cell types:
 *   - [Cell.Text]     — strings, shared-string interned.
 *   - [Cell.Number]   — plain numbers (rates, percentages).
 *   - [Cell.Currency] — currency amounts; renders with active currencyCode.
 *
 * Sheet flags:
 *   - [Sheet.freezeHeader] — `<pane ySplit="1" .../>` so the header row stays
 *     visible while scrolling.
 *   - [Sheet.autoFilterCols] — `<autoFilter ref="A1:<col><lastRow>"/>` so
 *     dropdown filters appear on the header.
 *   - [Sheet.totalsCols] — column indices (0-based) to SUM at a final
 *     "Total" row. SUM values are pre-computed and shipped in the `<v>`
 *     element alongside the `<f>` formula so the file reads correctly
 *     before Excel re-evaluates.
 */
object ExcelExportUtility {

    /** Per-cell content tag. */
    sealed class Cell {
        data class Text(val value: String) : Cell()
        data class Number(val value: Double) : Cell()
        /** C21 Stage 4 — currency-formatted amount. Uses style 3 (currency numFmt). */
        data class Currency(val value: Double) : Cell()
    }

    /**
     * Per-sheet definition. Defaults reflect "data sheet" expectations
     * (frozen header + auto-filter); the Summary sheet overrides with
     * `freezeHeader = false, autoFilterCols = 0` because it only has 2
     * columns of metric-name / value rows.
     */
    private data class Sheet(
        val name: String,
        val rows: List<List<Cell>>,
        val freezeHeader: Boolean = true,
        val autoFilterCols: Int = 0,            // 0 = no autofilter
        val totalsCols: List<Int> = emptyList(),// 0-based column indices to SUM
    )

    /**
     * Style XF indices defined in [buildStylesXml]. Reference these from
     * cells via the `s="N"` attribute.
     */
    private const val STYLE_DEFAULT       = 0  // plain text / general
    private const val STYLE_HEADER        = 1  // bold white on emerald
    private const val STYLE_NUMBER        = 2  // plain numeric (#,##0.00)
    private const val STYLE_CURRENCY      = 3  // currency w/ red negative
    private const val STYLE_TOTAL_TEXT    = 4  // bold (totals row label)
    private const val STYLE_TOTAL_CURRENCY = 5 // bold + currency (totals row amount)

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
        // C21 Stage 4 — Summary sheet KPIs + currency-format cells need the
        // active project's financial summary + currency code. Defaults stay
        // tolerant for callers that haven't migrated.
        summary: FinancialSummary = FinancialSummary(),
        currencyCode: String = "INR",
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
        fun t(v: String): Cell = Cell.Text(v)
        fun n(v: Double): Cell = Cell.Number(v)
        fun c(v: Double): Cell = Cell.Currency(v)

        // ── C21 Stage 4 — KPI inputs for the Summary sheet. Same cash-basis
        // exclusion + calendar-month window as the P&L Statement.
        val financingCats = setOf(
            "Debt Received", "Debt Repayment", "Lending",
            "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
        )
        val today = java.time.LocalDate.now()
        val monthStart = today.withDayOfMonth(1).toString()
        val monthEnd   = today.toString()
        val monthlyTxn = transactions.filter {
            it.date in monthStart..monthEnd && it.tags != "journal_only" && it.category !in financingCats
        }
        val monthlyIncome  = monthlyTxn.filter { it.type.equals("income",  true) }.sumOf { it.amount }
        val monthlyExpense = monthlyTxn.filter { it.type.equals("expense", true) }.sumOf { it.amount }
        val totalLiabilities = summary.totalDebtPrincipal + summary.totalDebtInterest
        val totalLentOut     = borrowers.sumOf { it.amount }

        val sheets = listOf(
            // ── #16 Summary sheet — first sheet in the workbook so it opens
            // first when the file's launched. Mirrors the PDF KPI cards.
            Sheet(
                "Summary",
                buildList {
                    add(listOf(t("Metric"), t("Value")))
                    add(listOf(t("Net Worth"),         c(summary.netWorth)))
                    add(listOf(t("Total Assets"),      c(summary.totalAssets)))
                    add(listOf(t("Total Liabilities"), c(totalLiabilities)))
                    add(listOf(t("Total Cash"),        c(summary.totalCash)))
                    add(listOf(t("Total Investments"), c(summary.totalInvestments)))
                    add(listOf(t("Invest Growth"),     c(summary.investmentGrowth)))
                    add(listOf(t("Monthly Income"),    c(monthlyIncome)))
                    add(listOf(t("Monthly Expense"),   c(monthlyExpense)))
                    add(listOf(t("Net Cash Flow"),     c(monthlyIncome - monthlyExpense)))
                    add(listOf(t("Total Lent Out"),    c(totalLentOut)))
                },
                freezeHeader = true,
                autoFilterCols = 0,  // 2-column metric/value list; filter not useful
            ),
            // Metadata sheet — kept for the recalc timestamp + audit trail.
            // No totals row (text-only sheet).
            Sheet(
                "Metadata",
                buildList {
                    add(listOf(t("Key"), t("Value")))
                    add(listOf(t("Export type"),     t("Full backup")))
                    add(listOf(t("Generated"),       t(java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)))))
                    add(listOf(t("Recalculated at"), t(recalcText)))
                    add(listOf(t("Currency"),        t(currencyCode)))
                },
                autoFilterCols = 0,
            ),
            Sheet(
                "Accounts",
                buildList {
                    add(listOf(t("Name"), t("Balance"), t("Type")))
                    accounts.forEach { a -> add(listOf(t(a.name), c(a.balance), t(a.type))) }
                },
                autoFilterCols = 3,
                totalsCols = listOf(1),  // SUM the Balance column
            ),
            Sheet(
                "Transactions",
                buildList {
                    add(listOf(t("Date"), t("Type"), t("Amount"), t("From Account"), t("To Account"),
                        t("Category"), t("Description"), t("Notes")))
                    transactions.sortedByDescending { it.date }.forEach { tx ->
                        add(listOf(
                            t(tx.date), t(tx.type), c(tx.amount),
                            t(tx.fromAcct), t(tx.toAcct), t(tx.category),
                            t(tx.desc), t(tx.notes),
                        ))
                    }
                },
                autoFilterCols = 8,
            ),
            Sheet(
                "Lending",
                buildList {
                    add(listOf(t("Name"), t("Phone"), t("Principal"), t("Rate %"), t("Interest Type"),
                        t("Loan Date"), t("Due Date"), t("Paid"), t("Status"), t("Notes")))
                    borrowers.forEach { b ->
                        add(listOf(
                            t(b.name), t(b.phone), c(b.amount), n(b.rate), t(b.type),
                            t(b.date), t(b.due), c(b.paid), t(b.status), t(b.notes),
                        ))
                    }
                },
                autoFilterCols = 10,
                totalsCols = listOf(2, 7),  // SUM Principal + Paid
            ),
            Sheet(
                "Debts",
                buildList {
                    add(listOf(t("Name"), t("Principal"), t("Rate %"), t("Interest Type"),
                        t("Date"), t("Due Date"), t("Paid"), t("Notes")))
                    debts.forEach { d ->
                        add(listOf(
                            t(d.name), c(d.amount), n(d.rate), t(d.intType),
                            t(d.date), t(d.due), c(d.paid), t(d.notes),
                        ))
                    }
                },
                autoFilterCols = 8,
                totalsCols = listOf(1, 6),  // SUM Principal + Paid
            ),
            Sheet(
                "Investments",
                buildList {
                    add(listOf(t("Name"), t("Type"), t("Invested"), t("Current Value"),
                        t("Growth"), t("Growth %"), t("Date")))
                    investments.forEach { i ->
                        val growth = i.currentVal - i.invested
                        val pct = if (i.invested > 0) growth / i.invested * 100 else 0.0
                        add(listOf(
                            t(i.name), t(i.type),
                            c(i.invested), c(i.currentVal), c(growth), n(pct),
                            t(i.date),
                        ))
                    }
                },
                autoFilterCols = 7,
                totalsCols = listOf(2, 3, 4),  // SUM Invested + Current + Growth
            ),
            Sheet(
                "Loan Repayments",
                buildList {
                    add(listOf(t("Borrower"), t("Amount"), t("Date"), t("Notes")))
                    payments.forEach { p -> add(listOf(t(p.name), c(p.amount), t(p.date), t(p.notes))) }
                },
                autoFilterCols = 4,
            ),
            Sheet(
                "Debt Repayments",
                buildList {
                    add(listOf(t("Creditor"), t("Amount"), t("Date"), t("Notes")))
                    debtPayments.forEach { p -> add(listOf(t(p.name), c(p.amount), t(p.date), t(p.notes))) }
                },
                autoFilterCols = 4,
            ),
        )

        writeXlsx(outputStream, sheets, currencyCode)
    }

    private fun writeXlsx(out: OutputStream, sheets: List<Sheet>, currencyCode: String) {
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

        // xl/styles.xml — built per-export so the currency symbol matches
        // the active project's currencyCode.
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(buildStylesXml(currencyCode).toByteArray())

        // xl/sharedStrings.xml — collect text-cell strings only.
        val allStrings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()
        fun strIdx(s: String): Int = stringIndex.getOrPut(s) { allStrings.add(s); allStrings.size - 1 }

        // Pre-index all TEXT cells.
        sheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell is Cell.Text) strIdx(cell.value)
                }
            }
        }
        // Pre-index the "Total" label that appears in totals rows.
        if (sheets.any { it.totalsCols.isNotEmpty() }) strIdx("Total")

        // Write each worksheet
        sheets.forEachIndexed { i, sheet ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
            zip.write(buildSheet(sheet, ::strIdx).toByteArray())
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
     * Styles definition — built per-export so the currency symbol in the
     * numFmt code matches the active project's currencyCode.
     *
     * Style indices ([STYLE_*] constants):
     *   - 0 — default (plain text / general)
     *   - 1 — header (bold white on emerald)
     *   - 2 — plain number (`#,##0.00`)
     *   - 3 — currency (audit #12 + #13) — uses numFmt 165 with `;[Red]-`
     *         negative format so Excel/Sheets render negative amounts in red
     *         natively, no separate conditional-formatting block needed.
     *   - 4 — totals row label (bold)
     *   - 5 — totals row amount (bold + currency)
     *
     * Currency numFmt code shape: `[$<sym>-409]#,##0.00;[Red]-[$<sym>-409]#,##0.00`
     *   - `[$<sym>-409]` — currency symbol + locale identifier (409 = en-US).
     *     OOXML's bilingual locale tag; Excel renders the symbol literally
     *     regardless of the user's locale.
     *   - `;[Red]-...` — negative format with leading minus, red color.
     *
     * Symbols escaped for XML (`&` and `<`) — the rupee sign (₹, U+20B9) is
     * a regular UTF-8 character and rides through fine in OOXML.
     */
    private fun buildStylesXml(currencyCode: String): String {
        val sym = CurrencyUtils.symbolFor(currencyCode).ifBlank { "" }
        val symEsc = xmlEscape(sym)
        // Currency numFmt — the `;[Red]-` half handles negative-amount red
        // formatting natively (audit #13).
        val currencyFmt = if (symEsc.isNotBlank()) {
            "[$$symEsc-409]#,##0.00;[Red]-[$$symEsc-409]#,##0.00"
        } else {
            "#,##0.00;[Red]-#,##0.00"  // fallback when symbol unknown
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="#,##0.00"/>
    <numFmt numFmtId="165" formatCode="$currencyFmt"/>
  </numFmts>
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0"   fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0"   fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="0"   fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="165" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildSheet(sheet: Sheet, strIdx: (String) -> Int): String {
        val colRef = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O")
        val totalLabelIdx = if (sheet.totalsCols.isNotEmpty()) strIdx("Total") else -1

        val rowsXml = sheet.rows.mapIndexed { rowIdx, row ->
            val isHeader = rowIdx == 0
            val cells = row.mapIndexed { colIdx, cell ->
                val col = colRef.getOrElse(colIdx) { "A" }
                val ref = "$col${rowIdx + 1}"
                cellXml(cell, ref, isHeader, strIdx)
            }.joinToString("")
            """<row r="${rowIdx + 1}">$cells</row>"""
        }.toMutableList()

        // C21 Stage 4 (audit #15) — totals row. Pre-compute SUMs so the file
        // reads correctly before Excel re-evaluates formulas; ship a <f>SUM(...)
        // formula too so the value stays live when the user edits.
        if (sheet.totalsCols.isNotEmpty() && sheet.rows.size > 1) {
            val bodyRows = sheet.rows.drop(1)  // skip header
            val totalRowIdx = sheet.rows.size + 1  // 1-based, after all body rows
            val cells = StringBuilder()
            val colCount = (sheet.rows.maxOfOrNull { it.size } ?: 1)
            for (colIdx in 0 until colCount) {
                val col = colRef.getOrElse(colIdx) { "A" }
                val ref = "$col$totalRowIdx"
                val sumColIdx = sheet.totalsCols.indexOf(colIdx)
                when {
                    sumColIdx >= 0 -> {
                        // Pre-computed sum + live formula.
                        val sumValue = bodyRows.sumOf { row ->
                            when (val c = row.getOrNull(colIdx)) {
                                is Cell.Number   -> c.value
                                is Cell.Currency -> c.value
                                else             -> 0.0
                            }
                        }
                        val sumExpr = "SUM($col${2}:$col${sheet.rows.size})"
                        val v = String.format(Locale.US, "%.2f", sumValue)
                        cells.append("""<c r="$ref" t="n" s="$STYLE_TOTAL_CURRENCY"><f>$sumExpr</f><v>$v</v></c>""")
                    }
                    colIdx == 0 -> {
                        // Leftmost cell gets the "Total" label.
                        cells.append("""<c r="$ref" t="s" s="$STYLE_TOTAL_TEXT"><v>$totalLabelIdx</v></c>""")
                    }
                    // Other cells in the totals row stay empty (no cell element).
                }
            }
            rowsXml += """<row r="$totalRowIdx">$cells</row>"""
        }

        val maxCol = colRef.getOrElse((sheet.rows.maxOfOrNull { it.size } ?: 1) - 1) { "A" }
        val lastRow = sheet.rows.size + (if (sheet.totalsCols.isNotEmpty() && sheet.rows.size > 1) 1 else 0)

        // C21 Stage 4 (audit #14) — frozen first row via `<pane ySplit="1" .../>`.
        val sheetViews = if (sheet.freezeHeader && sheet.rows.size > 1) {
            """<sheetViews>
              <sheetView workbookViewId="0">
                <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
              </sheetView>
            </sheetViews>"""
        } else ""

        // C21 Stage 4 (audit #14) — auto-filter on header row. Filter range
        // covers header → last body row (excludes totals row so SUM doesn't
        // get filtered out).
        val autoFilter = if (sheet.autoFilterCols > 0 && sheet.rows.size > 1) {
            val lastFilterCol = colRef.getOrElse(sheet.autoFilterCols - 1) { "A" }
            """<autoFilter ref="A1:$lastFilterCol${sheet.rows.size}"/>"""
        } else ""

        // OOXML element ordering matters: dimension → sheetViews → sheetData → autoFilter.
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:${maxCol}${lastRow}"/>
  $sheetViews
  <sheetData>${rowsXml.joinToString("\n")}</sheetData>
  $autoFilter
</worksheet>"""
    }

    /** Cell XML emitter — picks the right type/style based on cell variant + position. */
    private fun cellXml(cell: Cell, ref: String, isHeader: Boolean, strIdx: (String) -> Int): String =
        when (cell) {
            is Cell.Text -> {
                val si = strIdx(cell.value)
                val style = if (isHeader) " s=\"$STYLE_HEADER\"" else ""
                """<c r="$ref" t="s"$style><v>$si</v></c>"""
            }
            is Cell.Number -> {
                val style = if (isHeader) STYLE_HEADER else STYLE_NUMBER
                val v = String.format(Locale.US, "%.2f", cell.value)
                """<c r="$ref" t="n" s="$style"><v>$v</v></c>"""
            }
            is Cell.Currency -> {
                val style = if (isHeader) STYLE_HEADER else STYLE_CURRENCY
                val v = String.format(Locale.US, "%.2f", cell.value)
                """<c r="$ref" t="n" s="$style"><v>$v</v></c>"""
            }
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
