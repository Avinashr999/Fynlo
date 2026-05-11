package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.NetWorthSnapshot
import java.util.Locale
import app.fynlo.ui.theme.*

@Composable
fun NetWorthHistoryScreen(viewModel: FinanceViewModel) {
    val snapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    val summary   by viewModel.financialSummary.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale    = remember { Locale.getDefault() }

    // Save today's snapshot on screen open
    LaunchedEffect(summary.netWorth) {
        viewModel.saveSnapshotNow()
    }

    val sorted    = snapshots.sortedBy { it.date }
    val maxNW     = sorted.maxOfOrNull { it.netWorth }?.takeIf { it > 0 } ?: 1.0
    val minNW     = sorted.minOfOrNull { it.netWorth } ?: 0.0
    val range     = (maxNW - minNW).takeIf { it > 0 } ?: 1.0
    val lineColor = SemanticBlue

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        PremiumScreenHeader("Net Worth History", "Your wealth over time")

        // Current net worth card
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
            CardDefaults.cardColors(SemanticBlue.copy(alpha = 0.1f))) {
            Column(Modifier.padding(20.dp)) {
                Text("Current Net Worth", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currencySymbol ${String.format(locale, "%,.2f", summary.netWorth)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (summary.netWorth >= 0) Emerald500 else SemanticRed)
                Text("${sorted.size} snapshots recorded", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (sorted.size < 2) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Building your history...", style = MaterialTheme.typography.titleMedium)
                        Text("Open the app daily to track net worth trends",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("Net Worth Trend", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(Modifier.padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        val pts = sorted.mapIndexed { i, snap ->
                            val x = i.toFloat() / (sorted.size - 1) * size.width
                            val y = size.height - ((snap.netWorth - minNW) / range * size.height).toFloat()
                            Offset(x, y.coerceIn(0f, size.height))
                        }
                        // Fill
                        val fillPath = Path().apply {
                            moveTo(pts.first().x, size.height)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, size.height)
                            close()
                        }
                        drawPath(fillPath, lineColor.copy(alpha = 0.15f))
                        // Line
                        val linePath = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx()))
                        // Dots
                        pts.forEach { drawCircle(lineColor, 4.dp.toPx(), it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(sorted.first().date.takeLast(5), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sorted.last().date.takeLast(5), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Change stats
            val first = sorted.first().netWorth
            val last  = sorted.last().netWorth
            val change = last - first
            val changePct = if (first != 0.0) (change / Math.abs(first)) * 100 else 0.0

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                StatCard("Highest", "$currencySymbol${String.format(locale, "%,.0f", sorted.maxOf { it.netWorth })}", Emerald500, Modifier.weight(1f))
                StatCard("Lowest",  "$currencySymbol${String.format(locale, "%,.0f", sorted.minOf { it.netWorth })}", SemanticRed, Modifier.weight(1f))
                StatCard("Change",  "${if (change >= 0) "+" else ""}${String.format(locale, "%.1f", changePct)}%",
                    if (change >= 0) Emerald500 else SemanticRed, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // Recent snapshots list
            Text("Recent Snapshots", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            sorted.reversed().take(10).forEach { snap ->
                val prev = sorted.getOrNull(sorted.indexOf(snap) - 1)
                val diff = if (prev != null) snap.netWorth - prev.netWorth else 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(snap.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (prev != null) {
                            Text("${if (diff >= 0) "+" else ""}$currencySymbol${String.format(locale, "%,.0f", diff)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (diff >= 0) Emerald500 else SemanticRed)
                        }
                        Text("$currencySymbol${String.format(locale, "%,.0f", snap.netWorth)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, RoundedCornerShape(12.dp), CardDefaults.cardColors(color.copy(alpha = 0.1f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}
