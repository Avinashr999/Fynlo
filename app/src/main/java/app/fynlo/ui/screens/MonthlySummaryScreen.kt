package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
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
 * C15d (3.2.32) — Monthly Summary with chart-hero + standardized callouts +
 * linear-regression projection + CSV export per UX_AUDIT §C15d #1–#6.
 *
 * - **#1** `type_chart_hero` with Net-for-current-month number above the
 *   bar chart (same surface), matching the shape established for C15b P&L
 *   Statement and C15c Net Worth History.
 * - **#2** Bar chart extended from 6 months to last 12 months. Income (green)
 *   + expense (red) bars side by side per month.
 * - **#3** Four horizontal grid lines + y-axis amount labels along the left
 *   edge so the user can read magnitudes off the chart.
 * - **#4** Four callout cards: Best Month / Worst Month / Avg/Month / Trend.
 *   Replaces the prior 6M Income / 6M Expense / Net Saved 3-chip row.
 * - **#5** Projection — 3 ghost bars at lower opacity at the right of the
 *   chart, sized via linear regression of the historical 12 months. Months
 *   labelled with a leading "·" so they're visually distinct.
 * - **#6** CSV export button (top-right of the hero block) — shares a CSV
 *   of `Month,Income,Expense,Net` for the 12 historical months.
 */
@Composable
fun MonthlySummaryScreen(viewModel: FinanceViewModel) {
    val transactions       by viewModel.transactions.collectAsState()
    val currentProject     by viewModel.currentProject.collectAsState()
    val financialSummary   by viewModel.financialSummary.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale       = LocalLocale.current.platformLocale
    val context      = LocalContext.current

    // ── Last 12 months — same financing-category exclusion as P&L Statement
    // so financing flows don't inflate income/expense.
    val financingCats = setOf(
        "Debt Received", "Debt Repayment", "Lending",
        "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
    )
    val months = remember(transactions) {
        val today = LocalDate.now()
        (11 downTo 0).map { offset ->
            val date  = today.minusMonths(offset.toLong())
            val label = date.format(DateTimeFormatter.ofPattern("MMM"))
            val key   = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val list  = transactions.filter {
                it.date.startsWith(key) && it.tags != "journal_only" && it.category !in financingCats
            }
            val inc = list.filter { it.type.equals("income",  true) }.sumOf { it.amount }
            val exp = list.filter { it.type.equals("expense", true) }.sumOf { it.amount }
            Triple(label, inc, exp)
        }
    }
    val incomeColor  = Emerald500
    val expenseColor = SemanticRed

    // ── Projection — independent linear regression of income + expense over
    // the 12 historical months. Next 3 months projected; rendered as ghost
    // bars at lower opacity at the right edge of the chart.
    val projected = remember(months) {
        fun project(series: List<Double>): List<Double> {
            val n = series.size
            if (n < 2) return List(3) { series.lastOrNull() ?: 0.0 }
            val xs = (0 until n).map { it.toDouble() }
            val sumX = xs.sum()
            val sumY = series.sum()
            val sumXY = xs.zip(series) { x, y -> x * y }.sum()
            val sumX2 = xs.sumOf { it * it }
            val denom = n * sumX2 - sumX * sumX
            if (denom == 0.0) return List(3) { series.lastOrNull() ?: 0.0 }
            val slope = (n * sumXY - sumX * sumY) / denom
            val intercept = (sumY - slope * sumX) / n
            return (1..3).map { (intercept + slope * (n - 1 + it)).coerceAtLeast(0.0) }
        }
        val incSeries = months.map { it.second }
        val expSeries = months.map { it.third }
        val incProj = project(incSeries)
        val expProj = project(expSeries)
        val today = LocalDate.now()
        (1..3).map { off ->
            val d = today.plusMonths(off.toLong())
            Triple(
                "·${d.format(DateTimeFormatter.ofPattern("MMM"))}",
                incProj[off - 1],
                expProj[off - 1]
            )
        }
    }

    val chartSeries = months + projected
    val maxVal = remember(chartSeries) {
        chartSeries.flatMap { listOf(it.second, it.third) }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    }

    // Idle-fund alert kept from previous implementation (good UX touch outside
    // the audit's scope; survives the redesign).
    val idleRatio = if (financialSummary.totalAssets > 0) financialSummary.totalCash / financialSummary.totalAssets else 0.0
    val isIdle    = idleRatio > 0.6

    // ── Callouts (audit #4)
    val (bestLabel, bestNet) = months.maxByOrNull { it.second - it.third }
        ?.let { it.first to it.second - it.third } ?: ("—" to 0.0)
    val (worstLabel, worstNet) = months.minByOrNull { it.second - it.third }
        ?.let { it.first to it.second - it.third } ?: ("—" to 0.0)
    val avgNet     = if (months.isNotEmpty()) months.sumOf { it.second - it.third } / months.size else 0.0
    // Trend = recent-6 avg net vs prior-6 avg net (delta).
    val recent6Avg = months.takeLast(6).let { if (it.isEmpty()) 0.0 else it.sumOf { m -> m.second - m.third } / it.size }
    val prior6Avg  = months.dropLast(6).let { if (it.isEmpty()) 0.0 else it.sumOf { m -> m.second - m.third } / it.size }
    val trendDelta = recent6Avg - prior6Avg

    fun signed(v: Double) = (if (v >= 0) "+" else "") + CurrencyFormatter.detail(v, currencyCode, locale)

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Monthly Summary", subtitle = "12-month income & expense trend")
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            if (isIdle) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SemanticAmber.copy(alpha = 0.12f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = SemanticAmber)
                    Column {
                        Text(
                            "Idle Fund Alert",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = SemanticAmber
                        )
                        Text(
                            "${(idleRatio * 100).toInt()}% of your wealth is sitting idle in cash. Consider investing or lending to grow your value.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticAmber.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Chart-hero block (audit #1 + #2 + #3 + #5). Current month
            // net number sits above the 12-month bar chart, all in one
            // surface so the hero reads as a single type_chart_hero.
            val today    = LocalDate.now()
            val thisInc  = months.lastOrNull()?.second ?: 0.0
            val thisExp  = months.lastOrNull()?.third ?: 0.0
            val thisNet  = thisInc - thisExp

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Net for ${today.format(DateTimeFormatter.ofPattern("MMMM"))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            signed(thisNet),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (thisNet >= 0) incomeColor else expenseColor
                        )
                        Text(
                            "Income ${CurrencyFormatter.detail(thisInc, currencyCode, locale)} · " +
                            "Expense ${CurrencyFormatter.detail(thisExp, currencyCode, locale)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // CSV export (audit #6). Right-aligned with the hero block.
                    FilledTonalIconButton(
                        onClick = { exportMonthsAsCsv(context, months, currencyCode) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export CSV")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bar chart
                MonthlyBarChart(
                    series       = chartSeries,
                    historicalN  = months.size,
                    maxVal       = maxVal,
                    incomeColor  = incomeColor,
                    expenseColor = expenseColor,
                    currencyCode = currencyCode,
                    locale       = locale,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot(incomeColor, "Income")
                    LegendDot(expenseColor, "Expense")
                    LegendDot(incomeColor.copy(alpha = 0.4f), "Projected")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Callout cards (audit #4)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MSCallout(
                    label = "Best Month",
                    primary = bestLabel,
                    secondary = signed(bestNet),
                    primaryColor = incomeColor,
                    modifier = Modifier.weight(1f)
                )
                MSCallout(
                    label = "Worst Month",
                    primary = worstLabel,
                    secondary = signed(worstNet),
                    primaryColor = if (worstNet >= 0) incomeColor else expenseColor,
                    modifier = Modifier.weight(1f)
                )
                MSCallout(
                    label = "Avg / Month",
                    primary = signed(avgNet),
                    secondary = null,
                    primaryColor = if (avgNet >= 0) incomeColor else expenseColor,
                    modifier = Modifier.weight(1f)
                )
                MSCallout(
                    label = "Trend",
                    primary = signed(trendDelta),
                    secondary = "vs prior 6m",
                    primaryColor = if (trendDelta >= 0) incomeColor else expenseColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Month-by-Month Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))

            months.reversed().forEachIndexed { index, (label, inc, exp) ->
                val savings = inc - exp
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                }
                Row(
                    Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(48.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Income",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.detail(inc, currencyCode, locale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = incomeColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Expense",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.detail(exp, currencyCode, locale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = expenseColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.detail(savings, currencyCode, locale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (savings >= 0) incomeColor else expenseColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * 12-month bar chart with y-axis labels + horizontal reference lines + 3
 * projection bars rendered at lower opacity. `historicalN` is the count of
 * months considered real (everything after is the projection tail).
 */
@Composable
private fun MonthlyBarChart(
    series: List<Triple<String, Double, Double>>,
    historicalN: Int,
    maxVal: Double,
    incomeColor: Color,
    expenseColor: Color,
    currencyCode: String,
    locale: Locale,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            // Y-axis labels column (audit #3). Four labels at 100/75/50/25%
            // of maxVal, top-down so they match the canvas drawing order.
            Column(
                Modifier.width(52.dp).fillMaxHeight().padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { frac ->
                    Text(
                        CurrencyFormatter.listRow(maxVal * frac, currencyCode, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Chart canvas
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val n        = series.size
                if (n == 0) return@Canvas
                val cellW    = size.width / n
                val barWidth = (cellW * 0.32f).coerceAtLeast(2f)
                val chartH   = size.height

                // Horizontal reference grid (audit #3): 4 lines at 25/50/75/100% of maxVal.
                listOf(0.25, 0.5, 0.75, 1.0).forEach { frac ->
                    val y = chartH - (chartH * frac).toFloat()
                    drawLine(
                        gridColor,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                series.forEachIndexed { i, (_, inc, exp) ->
                    val isProj = i >= historicalN
                    val incAlpha = if (isProj) 0.4f else 1f
                    val expAlpha = if (isProj) 0.4f else 1f
                    val baseX = i * cellW
                    val incH  = ((inc / maxVal) * chartH).toFloat().coerceAtLeast(if (inc > 0) 2f else 0f)
                    val expH  = ((exp / maxVal) * chartH).toFloat().coerceAtLeast(if (exp > 0) 2f else 0f)
                    drawRect(
                        incomeColor.copy(alpha = incAlpha),
                        Offset(baseX + cellW * 0.18f, chartH - incH),
                        Size(barWidth, incH)
                    )
                    drawRect(
                        expenseColor.copy(alpha = expAlpha),
                        Offset(baseX + cellW * 0.50f, chartH - expH),
                        Size(barWidth, expH)
                    )
                }

                // Divider between historical and projected zone (audit #5).
                if (historicalN in 1 until n) {
                    val dividerX = historicalN * cellW
                    drawLine(
                        gridColor,
                        Offset(dividerX, 0f),
                        Offset(dividerX, chartH),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
            }
        }
        // Month labels — historical in normal weight; projected dimmed.
        Row(
            Modifier.fillMaxWidth().padding(start = 52.dp, top = 4.dp),
        ) {
            series.forEachIndexed { i, (label, _, _) ->
                val isProj = i >= historicalN
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = if (isProj) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
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

@Composable
private fun MSCallout(
    label: String,
    primary: String,
    secondary: String?,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            primary,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = primaryColor,
            maxLines = 1
        )
        if (secondary != null) {
            Text(
                secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * CSV export — write `Month,Income,Expense,Net` rows for the 12 historical
 * months and share via ACTION_SEND. File goes to the app cache + the
 * existing FileProvider so the share intent gets a content:// URI.
 */
private fun exportMonthsAsCsv(
    context: android.content.Context,
    months: List<Triple<String, Double, Double>>,
    currencyCode: String,
) {
    // C21 Stage 1 — standardized filename: Fynlo_MonthlySummary_<date>_<project>.csv
    // The project name isn't readily available at this helper's call site so
    // the subject defaults to the report type itself; matches the pattern
    // for cases where there's no per-project subject to embed.
    val file = app.fynlo.logic.ExportUtility.exportCacheFile(
        context,
        app.fynlo.logic.ExportUtility.filename("MonthlySummary", "Personal", "csv")
    )
    file.bufferedWriter().use { w ->
        w.appendLine("Month,Income ($currencyCode),Expense ($currencyCode),Net ($currencyCode)")
        // Reverse so the latest month is at the top of the CSV.
        months.reversed().forEach { (label, inc, exp) ->
            val net = inc - exp
            w.appendLine("$label,${"%.2f".format(inc)},${"%.2f".format(exp)},${"%.2f".format(net)}")
        }
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.provider", file
    )
    context.startActivity(
        android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Export Monthly Summary"
        )
    )
}
