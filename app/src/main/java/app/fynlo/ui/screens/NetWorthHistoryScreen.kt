package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import app.fynlo.ui.theme.*

/**
 * C15c (3.2.31) — Net Worth History with chart-hero shape + backfill +
 * standardized callout cards per UX_AUDIT §C15c #1–#6.
 *
 * - **#1 + #2** Current Net Worth number sits in the same surface as the
 *   line chart so they read as a single `type_chart_hero` per DESIGN_SYSTEM
 *   §1.2 (same shape as C15b's P&L Statement).
 * - **#3** Callouts standardized to `1-Month Change` / `6-Month Change` /
 *   `All-Time High` (was Highest / Lowest / Change %).
 * - **#4** "X snapshots recorded" pluralization fixed locally (covered also
 *   by C10 cluster-wide but right thing to fix on touch).
 * - **#5** "Backfill from history" action added — auto-generates month-end
 *   snapshots from cash-flow data via `viewModel.backfillNetWorthHistory`.
 *   Always reachable; primary CTA in empty state.
 * - **#6** "Open the app daily to track net worth trends" nag removed from
 *   the empty state. Backfill CTA replaces it.
 */
@Composable
fun NetWorthHistoryScreen(viewModel: FinanceViewModel) {
    val snapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    val summary   by viewModel.financialSummary.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val locale         = remember { Locale.getDefault() }

    // Keep the daily auto-save (it's invisible to the user and accumulates the
    // real history). Only the user-facing "open daily" message is gone per
    // audit #6 — see empty state below.
    LaunchedEffect(summary.netWorth) { viewModel.saveSnapshotNow() }

    val sorted    = snapshots.sortedBy { it.date }
    val lineColor = SemanticBlue

    // Backfill status — null = idle, true = running, Int = last run's added count.
    var backfillResult by remember { mutableStateOf<String?>(null) }
    var backfillBusy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Net Worth History", "Your wealth over time")
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Chart-hero block (audit #1 + #2). Hero number + line chart
            // share one surface so they read as a single unit.
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(
                    "Current Net Worth",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    CurrencyFormatter.hero(summary.netWorth, currencyCode, locale),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (summary.netWorth >= 0) Emerald500 else SemanticRed
                )
                Text(
                    // Audit #4 — singular/plural handled locally.
                    pluralCount(sorted.size, "snapshot", "snapshots") + " recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (sorted.size >= 2) {
                    NetWorthLineChart(
                        snapshots = sorted.map { it.date to it.netWorth },
                        lineColor = lineColor,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(
                            DateUtils.formatToDisplay(sorted.first().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            DateUtils.formatToDisplay(sorted.last().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Audit #6 — no "open daily" nag. Just the empty illustration
                    // + the backfill CTA so the user can populate history from
                    // their existing transactions in one tap.
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = lineColor.copy(alpha = 0.5f)
                        )
                        Text(
                            "Building your history",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Backfill month-end snapshots from your transactions to see the trend right away.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Backfill action row — always visible. Status text appears below
            // after a run so the user sees the result without a Toast/Snackbar.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        backfillBusy = true
                        backfillResult = null
                        viewModel.backfillNetWorthHistory { added ->
                            backfillBusy = false
                            backfillResult = when (added) {
                                0    -> "Already up to date — no months to backfill."
                                1    -> "Added 1 month-end snapshot from history."
                                else -> "Added $added month-end snapshots from history."
                            }
                        }
                    },
                    enabled = !backfillBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (backfillBusy) "Backfilling…" else "Backfill from history")
                }
            }
            if (backfillResult != null) {
                Text(
                    backfillResult!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            if (sorted.size >= 2) {
                // ── Callout cards (audit #3). Three equal-weight tiles.
                val today    = LocalDate.now()
                val current  = summary.netWorth
                val nwAt: (LocalDate) -> Double? = { target ->
                    // Use the snapshot closest to (but not after) the target date.
                    sorted.lastOrNull { runCatching { LocalDate.parse(it.date) <= target }.getOrDefault(false) }
                        ?.netWorth
                }
                val oneMonthAgo = nwAt(today.minusMonths(1))
                val sixMonthAgo = nwAt(today.minusMonths(6))
                val allTimeHigh = sorted.maxOf { it.netWorth }

                fun signedPct(now: Double, then: Double?): String? {
                    if (then == null) return null
                    if (then == 0.0)  return "—"
                    val pct = (now - then) / kotlin.math.abs(then) * 100
                    val sign = if (pct >= 0) "+" else ""
                    return "$sign${String.format(locale, "%.1f", pct)}%"
                }
                // Hoist composable-derived color so the helper below is pure.
                val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                fun changeColor(now: Double, then: Double?): Color =
                    if (then == null) neutralColor
                    else if (now >= then) Emerald500 else SemanticRed

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NetWorthCallout(
                        label = "1-Month Change",
                        value = signedPct(current, oneMonthAgo) ?: "Need more data",
                        valueColor = changeColor(current, oneMonthAgo),
                        modifier = Modifier.weight(1f)
                    )
                    NetWorthCallout(
                        label = "6-Month Change",
                        value = signedPct(current, sixMonthAgo) ?: "Need more data",
                        valueColor = changeColor(current, sixMonthAgo),
                        modifier = Modifier.weight(1f)
                    )
                    NetWorthCallout(
                        label = "All-Time High",
                        value = CurrencyFormatter.listRow(allTimeHigh, currencyCode, locale),
                        valueColor = Emerald500,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Recent snapshots list (kept from prior implementation).
                Text(
                    "Recent Snapshots",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                sorted.reversed().take(10).forEach { snap ->
                    val prev = sorted.getOrNull(sorted.indexOf(snap) - 1)
                    val diff = if (prev != null) snap.netWorth - prev.netWorth else 0.0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            DateUtils.formatToDisplay(snap.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (prev != null) {
                                Text(
                                    (if (diff >= 0) "+" else "") +
                                        CurrencyFormatter.listRow(diff, currencyCode, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (diff >= 0) Emerald500 else SemanticRed
                                )
                            }
                            Text(
                                CurrencyFormatter.listRow(snap.netWorth, currencyCode, locale),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun NetWorthLineChart(
    snapshots: List<Pair<String, Double>>,
    lineColor: Color,
) {
    val maxV = snapshots.maxOfOrNull { it.second } ?: 1.0
    val minV = snapshots.minOfOrNull { it.second } ?: 0.0
    val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val n = snapshots.size
        if (n < 2) return@Canvas
        val pts = snapshots.mapIndexed { i, (_, nw) ->
            val x = i.toFloat() / (n - 1) * size.width
            val y = (size.height - ((nw - minV) / range * size.height).toFloat()).coerceIn(0f, size.height)
            Offset(x, y)
        }
        // Area fill under the line
        val fill = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height); close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.15f))
        // Line
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, lineColor, style = Stroke(3.dp.toPx()))
        // Dots
        pts.forEach { drawCircle(lineColor, 3.dp.toPx(), it) }
    }
}

@Composable
private fun NetWorthCallout(
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

/** Local pluralization helper — C15c audit #4. */
private fun pluralCount(n: Int, singular: String, plural: String): String =
    if (n == 1) "1 $singular" else "$n $plural"
