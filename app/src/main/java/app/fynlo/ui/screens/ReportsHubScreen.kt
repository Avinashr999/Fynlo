package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLocale
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.fynlo.ui.theme.*

/**
 * C15a (3.2.29) — Reports landing as **pure launcher** (UX_AUDIT §C15a).
 *
 * Previous version mixed two responsibilities: it inlined the same data the
 * detail screens render (Income/Expense breakdown, Net Worth Trend mini-chart,
 * "Where Money Went" + "Where Money Came From" category bars) AND also showed
 * the launcher tiles below. That's the "duplicate" the audit calls out — the
 * inline blocks just preview what the user gets if they tap the corresponding
 * tile, so they're redundant on a Home-archetype landing.
 *
 * Now: date-range chip row at the top + a clean 3×N grid of tiles. Each tile
 * carries a one-line preview value computed against the selected range (audit
 * fix #3), so the landing still tells the user what's going on at a glance
 * without rendering the full visualizations.
 *
 * The tiles route to the same detail screens as before; nav surface unchanged.
 */
@Composable
fun ReportsHubScreen(
    viewModel: FinanceViewModel,
    onNavigateToPL: () -> Unit = {},
    onNavigateToNetWorth: () -> Unit = {},
    onNavigateToMoneyFlow: () -> Unit = {},
    onNavigateToInterest: () -> Unit = {},
    onNavigateToMonthly: () -> Unit = {},
    onNavigateToDebtPayoff: () -> Unit = {},
    onNavigateToLoanCalc: () -> Unit = {},
) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Reports") }
    val transactions   by viewModel.transactions.collectAsState()
    val summary        by viewModel.financialSummary.collectAsState()
    // C21 Stage 2 — collect borrowers + debts + investments so the Export
    // PDF button at the top of this hub produces a comprehensive report
    // (was passing emptyList() for each pre-Stage 2; only summary +
    // transactions made it through).
    val borrowers      by viewModel.borrowers.collectAsState()
    val debts          by viewModel.debts.collectAsState()
    val investments    by viewModel.investments.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val locale         = LocalLocale.current.platformLocale
    // C21 Stage 3 — net-worth snapshots for the trend-line chart in the
    // exported PDF. The on-screen Reports landing already uses these via
    // the tile preview computation; threading them to Export PDF too.
    val snapshots      by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    // C11 (3.2.40) — user's Date Format pref for the PDF date columns.
    val dateFormat     by app.fynlo.data.UserPreferences.dateFormat(androidx.compose.ui.platform.LocalContext.current)
        .collectAsState(initial = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN)

    var selectedRange by remember { mutableStateOf("This Month") }
    val ranges = listOf("This Month", "Last Month", "Last 3M", "Last 6M", "This Year", "All Time")

    val today = LocalDate.now()
    val (fromDate, toDate) = remember(selectedRange) {
        when (selectedRange) {
            "This Month"  -> today.withDayOfMonth(1) to today
            "Last Month"  -> today.minusMonths(1).withDayOfMonth(1) to
                             today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())
            "Last 3M"     -> today.minusMonths(3).withDayOfMonth(1) to today
            "Last 6M"     -> today.minusMonths(6).withDayOfMonth(1) to today
            "This Year"   -> today.withDayOfYear(1) to today
            else          -> LocalDate.of(2000, 1, 1) to today
        }
    }
    val fromStr = fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val toStr   = toDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // Same financing-activity exclusion as before — debt received/repaid, lending,
    // investments are balance-sheet movements, not P&L.
    val financingCats = setOf("Debt Received", "Debt Repayment", "Lending",
        "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns")

    // Pre-computed previews — each tile reads one of these. Memoised on
    // [transactions, fromStr, toStr] so range changes recompute in one pass.
    data class RangeAggregate(
        val plNet: Double,
        val flowGross: Double,
        val interestIncome: Double,
        val monthlyNet: Double,
    )
    val agg = remember(transactions, fromStr, toStr) {
        val inRange = transactions.filter {
            it.date in fromStr..toStr && it.tags != "journal_only"
        }
        val plList = inRange.filter { it.category !in financingCats }
        val income  = plList.filter { it.type.equals("income", true)  }.sumOf { it.amount }
        val expense = plList.filter { it.type.equals("expense", true) }.sumOf { it.amount }
        val flow    = inRange.sumOf { it.amount }  // gross movement, finance included
        val interest = inRange
            .filter { it.type.equals("income", true) && it.category.equals("Interest", true) }
            .sumOf { it.amount }
        // Monthly Summary preview is always "this calendar month" net regardless
        // of selectedRange — that screen has its own month picker, so the tile
        // surfaces the most-useful snapshot (current month).
        val mFrom = today.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val mTo   = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val mPl = transactions.filter {
            it.date in mFrom..mTo && it.tags != "journal_only" && it.category !in financingCats
        }
        val mIncome  = mPl.filter { it.type.equals("income",  true) }.sumOf { it.amount }
        val mExpense = mPl.filter { it.type.equals("expense", true) }.sumOf { it.amount }
        RangeAggregate(
            plNet          = income - expense,
            flowGross      = flow,
            interestIncome = interest,
            monthlyNet     = mIncome - mExpense,
        )
    }
    val totalDebt = summary.totalDebtPrincipal + summary.totalDebtInterest

    fun preview(v: Double): String = CurrencyFormatter.listRow(v, currencyCode, locale)

    val green = Emerald500
    val red   = SemanticRed
    val blue  = SemanticBlue

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Reports", subtitle = "Open a report to drill in")
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Export-PDF button kept — top-right utility, shared across detail
            // screens. PDF is generated against the active range below.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                Arrangement.End, Alignment.CenterVertically
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val projectName = currentProject?.name ?: "Personal"
                val periodLabel = if (selectedRange == "All Time") "All time"
                                  else "${fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} – ${toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                Button(
                    onClick = {
                        // C21 Stage 1 — standardized filename + identity row.
                        val file = app.fynlo.logic.ExportUtility.exportCacheFile(
                            context,
                            app.fynlo.logic.ExportUtility.filename("Report", projectName, "pdf")
                        )
                        file.outputStream().use {
                            app.fynlo.logic.ExportUtility.generatePDF(
                                it, summary, transactions, borrowers, investments,
                                currencyCode = currencyCode,
                                projectName  = projectName,
                                userEmail    = app.fynlo.data.AuthManager().userEmail,
                                periodLabel  = periodLabel,
                                debts        = debts,
                                snapshots    = snapshots,
                                dateFormat   = dateFormat,
                            )
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file)
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Export Report"))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Report PDF")
                }
            }

            // Range chip row — drives every tile preview below. Kept the same
            // affordance the old inline summary used; it just feeds tile values
            // now instead of an inline render.
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ranges, key = { it }) { r ->
                        TemplatePill(
                            text = r,
                            selected = selectedRange == r,
                            onClick = { selectedRange = r },
                        )
                    }
                }
                Text(
                    "${fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} → ${toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val plPrev      = (if (agg.plNet >= 0) "+" else "") + preview(agg.plNet)
            val plPrevColor = if (agg.plNet >= 0) green else red
            val nwPrev      = preview(summary.netWorth)
            val mfPrev      = if (agg.flowGross == 0.0) "No activity" else preview(agg.flowGross)
            val intPrev     = preview(agg.interestIncome)
            val msPrev      = (if (agg.monthlyNet >= 0) "+" else "") + preview(agg.monthlyNet)
            val msPrevColor = if (agg.monthlyNet >= 0) green else red
            val dpPrev      = if (totalDebt > 0) preview(totalDebt) else "Debt free"
            val dpPrevColor = if (totalDebt > 0) red else green

            LedgerMetricBand(
                metrics = listOf(
                    LedgerMetric("Net Worth", nwPrev, if (summary.netWorth >= 0) green else red),
                    LedgerMetric("P&L", plPrev, plPrevColor),
                    LedgerMetric("Flow", mfPrev, MaterialTheme.colorScheme.onSurface),
                ),
                modifier = Modifier.padding(bottom = 18.dp),
            )

            ReportGroup(title = "Business health", count = "2") {
                LedgerRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "P&L Statement",
                    subtitle = "Income, expenses, and net profit for the selected period.",
                    value = plPrev,
                    iconTint = green,
                    valueColor = plPrevColor,
                    onClick = onNavigateToPL,
                )
                LedgerRow(
                    icon = Icons.Default.DateRange,
                    title = "Monthly Summary",
                    subtitle = "High-level movement by category for this month.",
                    value = msPrev,
                    iconTint = blue,
                    valueColor = msPrevColor,
                    onClick = onNavigateToMonthly,
                )
            }

            ReportGroup(title = "Wealth", count = "2") {
                LedgerRow(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = "Net Worth",
                    subtitle = "Assets, debts, investments, and trend history.",
                    value = nwPrev,
                    iconTint = blue,
                    valueColor = if (summary.netWorth >= 0) green else red,
                    onClick = onNavigateToNetWorth,
                )
                LedgerRow(
                    icon = Icons.Default.AccountBalance,
                    title = "Interest Income",
                    subtitle = "Collected interest from lending and finance activity.",
                    value = intPrev,
                    iconTint = green,
                    valueColor = green,
                    onClick = onNavigateToInterest,
                )
            }

            ReportGroup(title = "Cash movement", count = "1") {
                LedgerRow(
                    icon = Icons.Default.SwapHoriz,
                    title = "Money Flow",
                    subtitle = "Inflow, outflow, and transfer activity.",
                    value = mfPrev,
                    iconTint = Carbon500,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onNavigateToMoneyFlow,
                )
            }

            ReportGroup(title = "Loans and debt", count = "2") {
                LedgerRow(
                    icon = Icons.Default.Schedule,
                    title = "Debt Payoff",
                    subtitle = "Outstanding debt and payoff planning.",
                    value = dpPrev,
                    iconTint = red,
                    valueColor = dpPrevColor,
                    onClick = onNavigateToDebtPayoff,
                )
                LedgerRow(
                    icon = Icons.Default.Calculate,
                    title = "EMI Calculator",
                    subtitle = "Estimate EMI, interest, and repayment schedule.",
                    value = "Calculator",
                    iconTint = Carbon500,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onNavigateToLoanCalc,
                )
            }


            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ReportGroup(
    title: String,
    count: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        LedgerSectionTitle(title = title, count = count, modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
