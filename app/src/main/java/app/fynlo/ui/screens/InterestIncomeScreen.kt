package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Data ──────────────────────────────────────────────────────────────────────

private data class MonthData(
    val label: String,          // "Jan", "Feb" …
    val yearMonth: YearMonth,
    val interestEarned: Double, // interest accrued this specific month
    val principal: Double       // total outstanding principal at month end
)

private fun buildMonthData(
    borrowers: List<app.fynlo.data.model.Borrower>,
    months: Int = 12
): List<MonthData> {
    val today = LocalDate.now()
    val dbFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val shortFmt = DateTimeFormatter.ofPattern("MMM")

    return (months - 1 downTo 0).map { offset ->
        val ym       = YearMonth.from(today).minusMonths(offset.toLong())
        val startOf  = ym.atDay(1).format(dbFmt)
        val endOf    = ym.atEndOfMonth().format(dbFmt)
        val label    = ym.format(shortFmt)

        var interestForMonth = 0.0
        var principalAtEnd   = 0.0

        borrowers.filter { it.rate > 0 }.forEach { b ->
            // Skip loans that hadn't started yet at this month end
            if (b.date > endOf) return@forEach

            val loanStart = if (b.date < startOf) startOf else b.date

            // Interest accrued from loan start to end of this month
            val intAtEnd  = InterestEngine.calcIntAccrued(b.amount, b.rate, b.date, b.type, b.due, b.paid, asOf = endOf)
            // Interest accrued from loan start to start of this month
            val intAtStart = if (b.date >= startOf) 0.0
            else InterestEngine.calcIntAccrued(b.amount, b.rate, b.date, b.type, b.due, b.paid, asOf = startOf)

            // Interest earned during this specific month = difference
            val monthlyInterest = (intAtEnd - intAtStart).coerceAtLeast(0.0)
            interestForMonth += monthlyInterest

            // Outstanding principal at month end
            val outstanding = (b.amount - b.paid).coerceAtLeast(0.0)
            principalAtEnd += outstanding
        }

        MonthData(label, ym, interestForMonth, principalAtEnd)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestIncomeScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val borrowers by viewModel.borrowers.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale = Locale.getDefault()

    var rangeMonths by remember { mutableIntStateOf(12) }

    val monthData = remember(borrowers, rangeMonths) {
        buildMonthData(borrowers, rangeMonths)
    }

    val totalInterest  = monthData.sumOf { it.interestEarned }
    val avgMonthly     = if (monthData.isNotEmpty()) totalInterest / monthData.size else 0.0
    val peakMonth      = monthData.maxByOrNull { it.interestEarned }
    val currentPrincipal = monthData.lastOrNull()?.principal ?: 0.0

    // Colours
    val barColor  = Emerald500
    val lineColor = SemanticAmber

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interest Income") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Range selector ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(6, 12, 24).forEach { m ->
                    val selected = rangeMonths == m
                    FilterChip(
                        selected = selected,
                        onClick  = { rangeMonths = m },
                        label    = { Text("${m}M") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Summary cards ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InterestStatCard(
                    label = "${rangeMonths}M Interest",
                    value = "$currencySymbol${fmtK(totalInterest, locale)}",
                    color = barColor,
                    modifier = Modifier.weight(1f)
                )
                InterestStatCard(
                    label = "Avg / Month",
                    value = "$currencySymbol${fmtK(avgMonthly, locale)}",
                    color = barColor,
                    modifier = Modifier.weight(1f)
                )
                InterestStatCard(
                    label = "Outstanding",
                    value = "$currencySymbol${fmtK(currentPrincipal, locale)}",
                    color = lineColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Chart card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendDot("Interest earned", barColor)
                        LegendDot("Outstanding principal", lineColor)
                    }
                    Spacer(Modifier.height(12.dp))

                    if (monthData.all { it.interestEarned == 0.0 && it.principal == 0.0 }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No interest-bearing loans yet.\nAdd loans with an interest rate to see income here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val maxInterest = monthData.maxOf { it.interestEarned }.takeIf { it > 0 } ?: 1.0
                        val maxPrincipal = monthData.maxOf { it.principal }.takeIf { it > 0 } ?: 1.0
                        val onSurface = MaterialTheme.colorScheme.onSurface
                        val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            drawInterestChart(
                                monthData    = monthData,
                                maxInterest  = maxInterest,
                                maxPrincipal = maxPrincipal,
                                barColor     = barColor,
                                lineColor    = lineColor,
                                gridColor    = surfaceVariant
                            )
                        }

                        // Month labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val step = when {
                                rangeMonths <= 6  -> 1
                                rangeMonths <= 12 -> 2
                                else              -> 3
                            }
                            monthData.forEachIndexed { idx, m ->
                                if (idx % step == 0 || idx == monthData.lastIndex) {
                                    Text(
                                        m.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Peak month callout ───────────────────────────────────────────
            if (peakMonth != null && peakMonth.interestEarned > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = barColor.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Best month",
                                style = MaterialTheme.typography.labelSmall,
                                color = barColor
                            )
                            Text(
                                peakMonth.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            "$currencySymbol${String.format(locale, "%,.0f", peakMonth.interestEarned)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = barColor
                            )
                        )
                    }
                }
            }

            // ── Monthly breakdown table ──────────────────────────────────────
            Text(
                "Month-by-Month Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            monthData.reversed().forEach { m ->
                MonthRow(
                    month         = m,
                    currencySymbol = currencySymbol,
                    locale        = locale,
                    maxInterest   = monthData.maxOf { it.interestEarned }.takeIf { it > 0 } ?: 1.0
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Canvas chart drawing ──────────────────────────────────────────────────────

private fun DrawScope.drawInterestChart(
    monthData: List<MonthData>,
    maxInterest: Double,
    maxPrincipal: Double,
    barColor: Color,
    lineColor: Color,
    gridColor: Color
) {
    val n        = monthData.size
    if (n == 0) return
    val chartH   = size.height - 12.dp.toPx()
    val chartW   = size.width
    val barW     = (chartW / n) * 0.5f
    val barGap   = (chartW / n)

    // Grid lines (3)
    repeat(3) { i ->
        val y = chartH * (1f - (i + 1) / 3f)
        drawLine(gridColor, Offset(0f, y), Offset(chartW, y), strokeWidth = 0.5.dp.toPx())
    }

    // Bars — interest earned
    monthData.forEachIndexed { idx, m ->
        val barH   = ((m.interestEarned / maxInterest) * chartH).toFloat().coerceAtLeast(if (m.interestEarned > 0) 4f else 0f)
        val left   = idx * barGap + barGap * 0.25f
        val top    = chartH - barH
        drawRoundRect(
            color        = barColor,
            topLeft      = Offset(left, top),
            size         = Size(barW, barH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }

    // Line — outstanding principal (secondary Y-axis)
    val linePts = monthData.mapIndexed { idx, m ->
        val x = idx * barGap + barGap * 0.5f
        val y = chartH - ((m.principal / maxPrincipal) * chartH).toFloat()
        Offset(x, y)
    }

    if (linePts.size >= 2) {
        val path = Path()
        path.moveTo(linePts.first().x, linePts.first().y)
        for (i in 1 until linePts.size) {
            // Smooth curve via cubic bezier
            val prev = linePts[i - 1]
            val curr = linePts[i]
            val cx   = (prev.x + curr.x) / 2f
            path.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
        }
        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap   = androidx.compose.ui.graphics.StrokeCap.Round,
            join  = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
        // Dots on line
        linePts.forEach { pt ->
            drawCircle(lineColor, 3.dp.toPx(), pt)
            drawCircle(Color.White, 1.5.dp.toPx(), pt)
        }
    }
}

// ── Small composables ─────────────────────────────────────────────────────────

@Composable
private fun InterestStatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color      = color
                )
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthRow(
    month: MonthData,
    currencySymbol: String,
    locale: Locale,
    maxInterest: Double
) {
    val progress = if (maxInterest > 0) (month.interestEarned / maxInterest).toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    month.yearMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Interest",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$currencySymbol${String.format(locale, "%,.0f", month.interestEarned)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Emerald500
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Principal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$currencySymbol${String.format(locale, "%,.0f", month.principal)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SemanticAmber
                            )
                        )
                    }
                }
            }
            if (month.interestEarned > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress    = { progress },
                    modifier    = Modifier.fillMaxWidth().height(3.dp),
                    color       = Emerald500,
                    trackColor  = Emerald500.copy(alpha = 0.12f)
                )
            }
        }
    }
}

private fun fmtK(v: Double, locale: Locale): String = when {
    v >= 100_000 -> String.format(locale, "%.1fL", v / 100_000)
    v >= 1_000   -> String.format(locale, "%.1fK", v / 1_000)
    else         -> String.format(locale, "%,.0f", v)
}
