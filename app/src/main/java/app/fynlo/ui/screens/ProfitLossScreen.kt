package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLocale
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * C15b (3.2.30) — P&L Statement with chart-hero + callout cards per
 * UX_AUDIT §C15b fixes #1–#5.
 *
 * - **#1 + #2** Rolling-12 line chart of monthly income vs expense, sitting
 *   directly under the Net P&L hero number (the `type_chart_hero` shape from
 *   `DESIGN_SYSTEM.md §1.2`).
 * - **#3** Four callout cards: This Month / Last Month / YTD / vs Last Year.
 *   Each shows a signed net P&L for its window, colour-coded green/red.
 * - **#4** "Total Lent Out" was previously `activeBorrowers.sum(amount)` which
 *   was neither lifetime nor outstanding — it excluded written-off loans but
 *   included paid-off principal. Renamed to "Total Lent Out (lifetime)" with
 *   the full historical sum across every borrower; added "Currently Lent Out"
 *   beneath it for the active outstanding principal.
 * - **#5** Static "You are profitable ↑" replaced with a cash-basis subtitle
 *   that shows the actual operating arithmetic (`income X − expenses Y = net`).
 */
@Composable
fun ProfitLossScreen(viewModel: FinanceViewModel) {
    val transactions   by viewModel.transactions.collectAsState()
    val borrowers      by viewModel.borrowers.collectAsState()
    val investments    by viewModel.investments.collectAsState()
    // C21 Stage 2 — debts for the Liabilities & Debts section in the
    // exported PDF (audit #3). Not used in-screen by P&L computations.
    val debts          by viewModel.debts.collectAsState()
    // C21 Stage 3 — snapshots for the net-worth trend chart in the PDF.
    val snapshots      by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    // C11 (3.2.40) — user's Date Format pref for PDF date columns.
    val dateFormat     by app.fynlo.data.UserPreferences.dateFormat(androidx.compose.ui.platform.LocalContext.current)
        .collectAsState(initial = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN)
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode    = currentProject?.currency ?: "INR"
    val summary        by viewModel.financialSummary.collectAsState()
    val locale          = LocalLocale.current.platformLocale
    val context         = androidx.compose.ui.platform.LocalContext.current

    // ── Cash-basis figures — exclude financing activities (debt/lending/investment).
    // Debt received = liability (not income); debt repayment principal = balance sheet
    // (not expense); lending principal = balance sheet too.
    val financingCategories = listOf(
        "Debt Received", "Debt Repayment", "Lending",
        "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
    )
    val cashTxns       = transactions.filter { it.tags != "journal_only" && it.category !in financingCategories }
    val totalIncome    = cashTxns.filter { it.type.equals("income",  ignoreCase = true) }.sumOf { it.amount }
    val totalExpense   = cashTxns.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }

    // ── Interest tracking ───────────────────────────────────────────────────
    // Use paidInterest from borrowers — actual interest collected, not the full
    // Loan Repayment transaction (which includes principal recovery).
    val interestIncome   = borrowers.sumOf { it.paidInterest }
    val interestExpense  = transactions.filter { it.category == "Interest Expense" }.sumOf { it.amount }
    val badDebtWriteOffs = transactions.filter { it.category == "Bad Debt" }.sumOf { it.amount }

    val investGrowth     = investments.sumOf { it.currentVal - (it.invested - it.withdrawn) }
    val investReturns    = transactions.filter { it.category == "Investment Returns" }.sumOf { it.amount }
    val principalIncome  = totalIncome - interestIncome
    val grossRevenue     = totalIncome + investReturns
    val operatingProfit  = grossRevenue - totalExpense
    val netProfit        = operatingProfit - interestExpense - badDebtWriteOffs

    // ── Rolling-12 monthly buckets for the chart (audit #1). One bucket per
    // calendar month ending in the current month. Empty months render as zero.
    val today    = remember { LocalDate.now() }
    val monthly  = remember(transactions, today) {
        val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val byMonth = cashTxns.groupBy { txn ->
            runCatching { YearMonth.parse(txn.date.substring(0, 7)) }.getOrNull()
        }
        months.map { ym ->
            val list = byMonth[ym].orEmpty()
            Triple(
                ym,
                list.filter { it.type.equals("income", true) }.sumOf { it.amount },
                list.filter { it.type.equals("expense", true) }.sumOf { it.amount },
            )
        }
    }

    // ── Callout windows (audit #3). YTD = Jan 1 → today. vs Last Year same
    // window from prior calendar year.
    val callouts = remember(transactions, today) {
        fun netFor(fromKey: String, toKey: String): Double {
            val list = cashTxns.filter { it.date in fromKey..toKey }
            val inc  = list.filter { it.type.equals("income",  true) }.sumOf { it.amount }
            val exp  = list.filter { it.type.equals("expense", true) }.sumOf { it.amount }
            return inc - exp
        }
        val fmtKey = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val mFrom  = today.withDayOfMonth(1).format(fmtKey)
        val mTo    = today.format(fmtKey)
        val lFrom  = today.minusMonths(1).withDayOfMonth(1).format(fmtKey)
        val lTo    = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth()).format(fmtKey)
        val yFrom  = today.withDayOfYear(1).format(fmtKey)
        val pyFrom = today.minusYears(1).withDayOfYear(1).format(fmtKey)
        val pyTo   = today.minusYears(1).format(fmtKey)
        listOf(
            "This Month"   to netFor(mFrom, mTo),
            "Last Month"   to netFor(lFrom, lTo),
            "YTD"          to netFor(yFrom, mTo),
            "vs Last Year" to (netFor(yFrom, mTo) - netFor(pyFrom, pyTo)),
        )
    }

    fun fmt(v: Double, showSign: Boolean = false): String {
        val sign = if (showSign && v > 0) "+" else ""
        return "$sign${CurrencyFormatter.detail(v, currencyCode, locale)}"
    }
    val green = Emerald500
    val red   = SemanticRed

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Profit & Loss", subtitle = "Revenue, expenses & lending P&L")
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "Project: ${currentProject?.name ?: "Personal"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        // C21 Stage 1 — standardized filename + identity row.
                        val projectName = currentProject?.name ?: "Personal"
                        val file = java.io.File(
                            context.cacheDir,
                            app.fynlo.logic.ExportUtility.filename("PL_Report", projectName, "pdf")
                        )
                        file.outputStream().use {
                            app.fynlo.logic.ExportUtility.generatePDF(
                                it, summary, transactions, borrowers, investments,
                                currencyCode = currencyCode,
                                projectName  = projectName,
                                userEmail    = app.fynlo.data.AuthManager().userEmail,
                                periodLabel  = "All time",
                                debts        = debts,
                                snapshots    = snapshots,
                                dateFormat   = dateFormat,
                            )
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Export P&L Report"
                        ))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export PDF")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Chart-hero block (audit #1 + #2). Net P&L number sits directly
            // above the rolling-12 income/expense line chart, both inside one
            // surface so the hero reads as a single unit per type_chart_hero.
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(
                    "Net Profit / Loss",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    fmt(netProfit, showSign = true),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (netProfit >= 0) green else red
                )
                // Audit #5 — replace the static "You are profitable ↑" with the
                // actual cash-basis arithmetic. Stating the numbers means the
                // user sees WHY it's positive (or negative) rather than a vague
                // affirmation that may not match the underlying flow.
                Text(
                    "Cash basis · income ${CurrencyFormatter.detail(grossRevenue, currencyCode, locale)} " +
                    "− expenses ${CurrencyFormatter.detail(totalExpense + interestExpense + badDebtWriteOffs, currencyCode, locale)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (monthly.any { it.second > 0 || it.third > 0 }) {
                    MonthlyPLLineChart(
                        monthly = monthly,
                        incomeColor = green,
                        expenseColor = red,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendDot(green,  "Income")
                        LegendDot(red,    "Expense")
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Log a few transactions to see the rolling-12 trend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Callout cards (audit #3). Four equal-weight tiles in one row.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                callouts.forEach { (label, value) ->
                    PLCallout(
                        label = label,
                        value = fmt(value, showSign = true),
                        valueColor = if (value >= 0) green else red,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── REVENUE ───────────────────────────────────────────────────────────
            PLSection(
                "Revenue",
                listOf(
                    "Interest Income (collected)" to interestIncome,
                    "Other Income"                to principalIncome,
                    "Investment Returns"          to investReturns
                ),
                grossRevenue, green, currencyCode, locale
            )

            Spacer(Modifier.height(12.dp))

            // ── EXPENSES ──────────────────────────────────────────────────────────
            PLSection(
                "Expenses",
                listOf(
                    "Business / Personal Expenses" to totalExpense,
                    "Interest Paid (Cost of Debt)" to interestExpense,
                    "Bad Debt Write-offs"          to badDebtWriteOffs
                ),
                totalExpense + interestExpense + badDebtWriteOffs, red, currencyCode, locale
            )

            Spacer(Modifier.height(12.dp))

            // ── LENDING PERFORMANCE ───────────────────────────────────────────────
            // Audit #4 — "Total Lent Out" used to be `activeBorrowers.sum(amount)`
            // which excluded written-off loans (so not lifetime) but included
            // already-recovered principal (so not outstanding either). Fixed:
            //   • Total Lent Out (lifetime) = every borrower's original amount,
            //     INCLUDING written-off loans (they're part of lifetime activity).
            //   • Currently Lent Out      = active borrowers' outstanding
            //     principal (amount − paidPrincipal).
            val activeBorrowers       = borrowers.filter { it.status != "WrittenOff" }
            val totalLentLifetime     = borrowers.sumOf { it.amount }
            val currentlyLentOut      = activeBorrowers.sumOf { (it.amount - it.paidPrincipal).coerceAtLeast(0.0) }
            val totalRecovered        = activeBorrowers.sumOf { it.paidPrincipal }
            val interestCollected     = activeBorrowers.sumOf { it.paidInterest }
            val defaultedAmt          = borrowers.filter { it.status == "Defaulted" || it.status == "WrittenOff" }
                .sumOf { it.amount - it.paidPrincipal }

            PLSection(
                "Lending Business",
                listOf(
                    "Total Lent Out (lifetime)" to totalLentLifetime,
                    "Currently Lent Out"        to currentlyLentOut,
                    "Principal Recovered"       to totalRecovered,
                    "Interest Collected"        to interestCollected,
                    "Bad / Defaulted Loans"     to -defaultedAmt
                ),
                interestCollected - badDebtWriteOffs, green, currencyCode, locale
            )

            Spacer(Modifier.height(12.dp))

            // ── INVESTMENTS ───────────────────────────────────────────────────────
            // Investments P&L = only gains/returns, NOT the capital itself (balance sheet).
            PLSection(
                "Investments",
                listOf(
                    "Realised Returns (withdrawn)"  to investReturns,
                    "Unrealised Growth (on paper)"  to investGrowth.coerceAtLeast(0.0),
                    "Unrealised Loss (on paper)"    to investGrowth.coerceAtMost(0.0)
                ),
                investGrowth + investReturns,
                if (investGrowth + investReturns >= 0) green else red,
                currencyCode, locale
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Dual-line rolling-12 income vs expense chart. Lightweight Canvas — both
 * series share the same y-axis (max of either series), so the relative
 * spread reads as "did income exceed expense this month or not."
 */
@Composable
private fun MonthlyPLLineChart(
    monthly: List<Triple<YearMonth, Double, Double>>,
    incomeColor: Color,
    expenseColor: Color,
) {
    val maxV = (monthly.maxOfOrNull { maxOf(it.second, it.third) } ?: 0.0).takeIf { it > 0 } ?: 1.0
    val chartHeight = 140.dp

    Column(Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val n = monthly.size
            if (n < 2) return@Canvas
            fun xOf(i: Int) = i.toFloat() / (n - 1) * size.width
            fun yOf(v: Double) = (size.height - (v / maxV * size.height).toFloat()).coerceIn(0f, size.height)

            // Income line
            val incPath = Path().apply {
                moveTo(xOf(0), yOf(monthly[0].second))
                for (i in 1 until n) lineTo(xOf(i), yOf(monthly[i].second))
            }
            drawPath(incPath, incomeColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            monthly.forEachIndexed { i, m -> drawCircle(incomeColor, 3.dp.toPx(), Offset(xOf(i), yOf(m.second))) }

            // Expense line
            val expPath = Path().apply {
                moveTo(xOf(0), yOf(monthly[0].third))
                for (i in 1 until n) lineTo(xOf(i), yOf(monthly[i].third))
            }
            drawPath(expPath, expenseColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            monthly.forEachIndexed { i, m -> drawCircle(expenseColor, 3.dp.toPx(), Offset(xOf(i), yOf(m.third))) }
        }
        // Month axis labels — every third month + the last one to avoid
        // clutter on narrow screens. Compact "MMM" form per the design system.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.SpaceBetween) {
            monthly.forEachIndexed { i, (ym, _, _) ->
                if (i % 3 == 0 || i == monthly.lastIndex) {
                    Text(
                        ym.format(DateTimeFormatter.ofPattern("MMM")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else Spacer(Modifier.width(1.dp))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color
        )
    }
}

/**
 * Single callout-card tile per audit #3. Four of these go in a Row at the
 * top of the screen so the user gets the four headline windows at a glance.
 */
@Composable
private fun PLCallout(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun PLSection(
    title: String,
    items: List<Pair<String, Double>>,
    total: Double,
    color: Color,
    currencyCode: String,
    locale: Locale
) {
    fun fmt(v: Double) = CurrencyFormatter.detail(v, currencyCode, locale)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.width(3.dp).height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = color))
        }
        Spacer(Modifier.height(12.dp))
        items.forEach { (label, value) ->
            val valueColor = when {
                value < 0 -> SemanticRed
                label.contains("Interest Income") || label.contains("Collected") || label.contains("Returned") || label.contains("Growth") -> Emerald500
                label.contains("Interest Paid") || label.contains("Bad Debt") || label.contains("Loss") -> SemanticRed
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fmt(value),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = valueColor
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = color.copy(alpha = 0.15f))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Net", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.1f)) {
                Text(
                    fmt(total),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold, color = color),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
