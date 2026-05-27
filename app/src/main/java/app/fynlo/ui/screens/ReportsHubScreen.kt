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
    val locale         = remember { Locale.getDefault() }
    // C21 Stage 3 — net-worth snapshots for the trend-line chart in the
    // exported PDF. The on-screen Reports landing already uses these via
    // the tile preview computation; threading them to Export PDF too.
    val snapshots      by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())

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
        PremiumScreenHeader("Reports", "Open a report to drill in")
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
                FilledTonalButton(
                    onClick = {
                        // C21 Stage 1 — standardized filename + identity row.
                        val file = java.io.File(
                            context.cacheDir,
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
                    Text("Export")
                }
            }

            // Range chip row — drives every tile preview below. Kept the same
            // affordance the old inline summary used; it just feeds tile values
            // now instead of an inline render.
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ranges, key = { it }) { r ->
                        FilterChip(
                            selected = selectedRange == r,
                            onClick  = { selectedRange = r },
                            label    = { Text(r, style = MaterialTheme.typography.labelSmall) },
                            shape    = RoundedCornerShape(12.dp),
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = green.copy(alpha = 0.16f),
                                selectedLabelColor     = green
                            )
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

            // Tile grid — every tile is the same shape (ReportTileCard). The
            // preview value is the load-bearing bit; without it the tiles would
            // be just icons + labels, which doesn't tell the user anything.
            //
            // Layout: 3 tiles per row. Eight tiles total → 3 + 3 + 2 with one
            // spacer to keep the last row's spacing identical to the rows above.
            val plPrev      = (if (agg.plNet >= 0) "+" else "") + preview(agg.plNet)
            val plPrevColor = if (agg.plNet >= 0) green else red
            val nwPrev      = preview(summary.netWorth)
            val mfPrev      = if (agg.flowGross == 0.0) "No activity" else preview(agg.flowGross)
            val intPrev     = preview(agg.interestIncome)
            val msPrev      = (if (agg.monthlyNet >= 0) "+" else "") + preview(agg.monthlyNet)
            val msPrevColor = if (agg.monthlyNet >= 0) green else red
            val dpPrev      = if (totalDebt > 0) preview(totalDebt) else "Debt free"
            val dpPrevColor = if (totalDebt > 0) red else green

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTileCard(
                    label = "P&L Statement",
                    icon  = Icons.AutoMirrored.Filled.List,
                    tint  = green,
                    preview = plPrev,
                    previewColor = plPrevColor,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPL,
                )
                ReportTileCard(
                    label = "Net Worth",
                    icon  = Icons.AutoMirrored.Filled.TrendingUp,
                    tint  = blue,
                    preview = nwPrev,
                    previewColor = if (summary.netWorth >= 0) green else red,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToNetWorth,
                )
                ReportTileCard(
                    label = "Money Flow",
                    icon  = Icons.Default.SwapHoriz,
                    tint  = Carbon500,
                    preview = mfPrev,
                    previewColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMoneyFlow,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTileCard(
                    label = "Interest Income",
                    icon  = Icons.Default.AccountBalance,
                    tint  = green,
                    preview = intPrev,
                    previewColor = green,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToInterest,
                )
                ReportTileCard(
                    label = "Monthly Summary",
                    icon  = Icons.Default.DateRange,
                    tint  = blue,
                    preview = msPrev,
                    previewColor = msPrevColor,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMonthly,
                )
                ReportTileCard(
                    label = "Debt Payoff",
                    icon  = Icons.Default.Schedule,
                    tint  = red,
                    preview = dpPrev,
                    previewColor = dpPrevColor,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToDebtPayoff,
                )
            }
            Spacer(Modifier.height(10.dp))
            // 3.2.17 — EMI Calculator tile (it's a calculator tool, no data
            // preview makes sense). Paired with one Spacer placeholder so the
            // last row's tile width matches the rows above.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTileCard(
                    label = "EMI Calculator",
                    icon  = Icons.Default.Calculate,
                    tint  = Carbon500,
                    preview = "Calculator",
                    previewColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToLoanCalc,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

/**
 * Standard tile shape per audit fix #2 ("Standardize tile sizing and labels").
 * Every grid tile uses this — same height, same icon-circle size, same label
 * typography, same preview-value row underneath the label.
 *
 * Replaces the old `ReportLinkCard`, which was label-only.
 */
@Composable
fun ReportTileCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    preview: String,
    previewColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .heightIn(min = 116.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            preview,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = previewColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
