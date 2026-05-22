package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.fynlo.ui.theme.*

@Composable
fun MonthlySummaryScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale       = remember { Locale.getDefault() }

    // Build last 6 months data
    val months = remember(transactions) {
        val today = LocalDate.now()
        (5 downTo 0).map { offset ->
            val date  = today.minusMonths(offset.toLong())
            val label = date.format(DateTimeFormatter.ofPattern("MMM"))
            val key   = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val inc   = transactions.filter { it.type.equals("income",  ignoreCase = true) && it.date.startsWith(key) }.sumOf { it.amount }
            val exp   = transactions.filter { it.type.equals("expense", ignoreCase = true) && it.date.startsWith(key) }.sumOf { it.amount }
            Triple(label, inc, exp)
        }
    }

    val maxVal = remember(months) { months.flatMap { listOf(it.second, it.third) }.maxOrNull()?.takeIf { it > 0 } ?: 1.0 }
    val incomeColor  = Emerald500
    val expenseColor = SemanticRed

    val financialSummary by viewModel.financialSummary.collectAsState()

    val idleRatio = if (financialSummary.totalAssets > 0) financialSummary.totalCash / financialSummary.totalAssets else 0.0
    val isIdle    = idleRatio > 0.6 // Alert if > 60% is in cash

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Monthly Summary", "6-month income & expense analysis")
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

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
                    Text("Idle Fund Alert", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = SemanticAmber)
                    Text("${(idleRatio * 100).toInt()}% of your wealth is sitting idle in cash. Consider investing or lending to grow your value.",
                        style = MaterialTheme.typography.labelSmall, color = SemanticAmber.copy(alpha = 0.8f))
                }
            }
        }

        // 6-month totals
        val totalIncome  = months.sumOf { it.second }
        val totalExpense = months.sumOf { it.third }
        val netSavings   = totalIncome - totalExpense

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryChip("6M Income",  "$currencySymbol${String.format(locale, "%,.0f", totalIncome)}",  incomeColor,  Modifier.weight(1f))
            SummaryChip("6M Expense", "$currencySymbol${String.format(locale, "%,.0f", totalExpense)}", expenseColor, Modifier.weight(1f))
            SummaryChip("Net Saved",  "$currencySymbol${String.format(locale, "%,.0f", netSavings)}",
                if (netSavings >= 0) incomeColor else expenseColor, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text("Income vs Expense — Last 6 Months",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))

        // Bar Chart
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    listOf("Income" to incomeColor, "Expense" to expenseColor).forEach { (label, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp, 10.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(10.dp)) { drawRect(color) }
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    val barWidth  = size.width / (months.size * 3f)
                    val chartH    = size.height - 24.dp.toPx()
                    months.forEachIndexed { i, (_, inc, exp) ->
                        val x = i * (size.width / months.size)
                        val incH  = ((inc  / maxVal) * chartH).toFloat().coerceAtLeast(2f)
                        val expH  = ((exp / maxVal) * chartH).toFloat().coerceAtLeast(2f)
                        drawRect(incomeColor,  Offset(x + barWidth * 0.2f, chartH - incH), Size(barWidth, incH))
                        drawRect(expenseColor, Offset(x + barWidth * 1.4f, chartH - expH), Size(barWidth, expH))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    months.forEach { (label, _, _) ->
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
        }

        Spacer(Modifier.height(20.dp))
        Text("Month-by-Month Breakdown",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))

        months.reversed().forEachIndexed { index, (label, inc, exp) ->
            val savings = inc - exp
            if (index > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
            Row(Modifier.padding(vertical = 14.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Income", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format(locale, "%,.0f", inc)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = incomeColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format(locale, "%,.0f", exp)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = expenseColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format(locale, "%,.0f", savings)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (savings >= 0) incomeColor else expenseColor)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}
