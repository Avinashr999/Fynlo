package app.fynlo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.fynlo.ui.theme.*

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
    val transactions  by viewModel.transactions.collectAsState()
    val summary       by viewModel.financialSummary.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    val snapshots     by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    val locale        = remember { Locale.getDefault() }

    var selectedRange by remember { mutableStateOf("This Month") }
    val ranges = listOf("This Month", "Last Month", "Last 3M", "Last 6M", "This Year", "All Time")

    val today  = LocalDate.now()
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
    val finalTo = toDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // Exclude financing activities — debt received/repaid, lending, investments
    // are balance sheet movements, not income or expenses
    val financingCats = setOf("Debt Received", "Debt Repayment", "Lending",
        "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns")

    val filtered = remember(transactions, fromStr, finalTo) {
        transactions.filter { it.date in fromStr..finalTo && it.tags != "journal_only" && it.category !in financingCats }
    }
    val income   = filtered.filter { it.type.equals("income",  true) }.sumOf { it.amount }
    val expense  = filtered.filter { it.type.equals("expense", true) }.sumOf { it.amount }
    val netFlow  = income - expense
    val savings  = if (income > 0) (netFlow / income * 100) else 0.0

    val expByCat = filtered.filter { it.type.equals("expense", true) }
        .groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .entries.sortedByDescending { it.value }

    val incByCat = filtered.filter { it.type.equals("income", true) }
        .groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .entries.sortedByDescending { it.value }

    val sortedSnaps = snapshots.sortedBy { it.date }

    val green = Emerald500
    val red   = SemanticRed
    val blue  = SemanticBlue

    fun fmt(v: Double) = CurrencyFormatter.hero(v, currencyCode, locale)

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Reports", "Income, expenses & net worth")
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
            Arrangement.End, Alignment.CenterVertically) {
            val context = androidx.compose.ui.platform.LocalContext.current
            FilledTonalButton(
                onClick = {
                    val file = java.io.File(context.cacheDir, "report_${today}.pdf")
                    file.outputStream().use {
                        app.fynlo.logic.ExportUtility.generatePDF(
                            it, summary, transactions, emptyList(), emptyList())
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(green))
                    Text("Income", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = green)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(income, label = "income",
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { v ->
                    Text(fmt(v), style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(red))
                    Text("Expenses", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = red)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(expense, label = "expense",
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { v ->
                    Text(fmt(v), style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Net Cash Flow", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(netFlow),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (netFlow < 0) red else green)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Savings Rate", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${String.format(locale, "%.1f", savings.coerceAtLeast(0.0))}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = green)
            }
        }

        Spacer(Modifier.height(20.dp))

        PremiumSectionLabel("Net Worth Trend")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(16.dp)) {
                if (sortedSnaps.size >= 2) {
                    val minV = sortedSnaps.minOf { it.netWorth }
                    val maxV = sortedSnaps.maxOf { it.netWorth }
                    val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
                    val currentLineColor = if (sortedSnaps.last().netWorth >= sortedSnaps.first().netWorth) green else red
                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val pts = sortedSnaps.mapIndexed { i, s ->
                            Offset(
                                i.toFloat() / (sortedSnaps.size - 1) * size.width,
                                (size.height - ((s.netWorth - minV) / range * size.height).toFloat()).coerceIn(0f, size.height)
                            )
                        }
                        drawPath(Path().apply {
                            moveTo(pts.first().x, size.height)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, size.height); close()
                        }, currentLineColor.copy(alpha = 0.12f))
                        drawPath(Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }, currentLineColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        pts.forEach { drawCircle(currentLineColor, 4.dp.toPx(), it) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(sortedSnaps.first().date, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.hero(summary.netWorth, currencyCode, locale),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = green)
                        Text(sortedSnaps.last().date, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        Text("Open daily to build trend data",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
        }

        Spacer(Modifier.height(20.dp))

        if (expByCat.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Where Money Went",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(color = red.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(fmt(expense),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = red,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            val expColors = ChartColors
            val maxExp = expByCat.firstOrNull()?.value ?: 1.0
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    expByCat.take(6).forEachIndexed { i, (cat, amt) ->
                        val frac = (amt / maxExp).toFloat().coerceIn(0f, 1f)
                        val color = expColors[i % expColors.size]
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                    Text(cat, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(fmt(amt), style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold), color = color)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.15f))) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(color))
                            }
                        }
                    }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (incByCat.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Where Money Came From",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(color = green.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(fmt(income),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = green,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            val incColors = listOf(green, SemanticBlue, Emerald500, Emerald500)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    incByCat.take(5).forEachIndexed { i, (cat, amt) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape)
                                    .background(incColors[i % incColors.size]))
                                Text(cat, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(fmt(amt), style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold), color = incColors[i % incColors.size])
                        }
                    }
            }
            Spacer(Modifier.height(20.dp))
        }

        Text("Detailed Reports", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportLinkCard("P&L Statement", Icons.Default.List, green, Modifier.weight(1f), onNavigateToPL)
            ReportLinkCard("Net Worth", Icons.AutoMirrored.Filled.TrendingUp, blue, Modifier.weight(1f), onNavigateToNetWorth)
            ReportLinkCard("Money Flow", Icons.Default.SwapHoriz, Carbon500, Modifier.weight(1f), onNavigateToMoneyFlow)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportLinkCard("Interest Income", Icons.Default.AccountBalance, green, Modifier.weight(1f), onNavigateToInterest)
            ReportLinkCard("Monthly Summary", Icons.Default.DateRange, blue, Modifier.weight(1f), onNavigateToMonthly)
            ReportLinkCard("Debt Payoff", Icons.Default.Schedule, SemanticRed, Modifier.weight(1f), onNavigateToDebtPayoff)
        }
        Spacer(Modifier.height(10.dp))
        // 3.2.17 — EMI Calculator tile added for cross-discovery. Also
        // reachable from the side drawer's Finance Tools section.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportLinkCard("EMI Calculator", Icons.Default.Calculate, Carbon500, Modifier.weight(1f), onNavigateToLoanCalc)
            // Placeholder weights so the single tile doesn't go full-width and
            // look out of pattern with the rest of the 3-per-row grid above.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(100.dp))
    }
    }
}

@Composable
fun ReportLinkCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
