package app.fynlo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.pluralize
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.PremiumCard
import app.fynlo.ui.theme.PremiumScreenHeader
import app.fynlo.ui.theme.SemanticBlue
import app.fynlo.ui.theme.SemanticRed
import java.time.LocalDate
import java.util.Locale

@Composable
fun NetWorthHistoryScreen(viewModel: FinanceViewModel) {
    val snapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    val summary by viewModel.financialSummary.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    LaunchedEffect(summary.netWorth) { viewModel.saveSnapshotNow() }

    val sorted = snapshots.sortedBy { it.date }
    val currentSnapshot = sorted.lastOrNull()
    val previousSnapshot = sorted.dropLast(1).lastOrNull()
    val changeFromPrevious = if (currentSnapshot != null && previousSnapshot != null) {
        currentSnapshot.netWorth - previousSnapshot.netWorth
    } else null

    var backfillResult by remember { mutableStateOf<String?>(null) }
    var backfillBusy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Net Worth History", subtitle = "Clear trend of your total wealth")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            NetWorthHeroCard(
                netWorth = summary.netWorth,
                snapshots = sorted.map { it.date to it.netWorth },
                currencyCode = currencyCode,
                locale = locale,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    backfillBusy = true
                    backfillResult = null
                    viewModel.backfillNetWorthHistory { added ->
                        backfillBusy = false
                        backfillResult = when (added) {
                            0 -> "Already up to date - no months to backfill."
                            1 -> "Added 1 month-end snapshot from history."
                            else -> "Added $added month-end snapshots from history."
                        }
                    }
                },
                enabled = !backfillBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.History, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (backfillBusy) "Backfilling..." else "Backfill from history")
            }

            backfillResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (sorted.size >= 2) {
                NetWorthLatestChangeCard(
                    change = changeFromPrevious,
                    previousDate = previousSnapshot?.date,
                    currencyCode = currencyCode,
                    locale = locale,
                )

                Spacer(Modifier.height(12.dp))

                NetWorthCalloutRow(
                    sorted = sorted.map { it.date to it.netWorth },
                    current = summary.netWorth,
                    currencyCode = currencyCode,
                    locale = locale,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Recent history",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))
                sorted.reversed().take(10).forEachIndexed { reverseIndex, snap ->
                    val originalIndex = sorted.lastIndex - reverseIndex
                    val prev = sorted.getOrNull(originalIndex - 1)
                    NetWorthSnapshotCard(
                        date = snap.date,
                        netWorth = snap.netWorth,
                        change = prev?.let { snap.netWorth - it.netWorth },
                        currencyCode = currencyCode,
                        locale = locale,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun NetWorthHeroCard(
    netWorth: Double,
    snapshots: List<Pair<String, Double>>,
    currencyCode: String,
    locale: Locale,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
    ) {
        Text(
            "Current Net Worth",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            CurrencyFormatter.hero(netWorth, currencyCode, locale),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (netWorth >= 0) Emerald500 else SemanticRed,
        )
        Text(
            pluralize(snapshots.size, "snapshot") + " recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "History is a trend view. Account balances change only from saved money entries.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (snapshots.size >= 2) {
            NetWorthLineChart(snapshots = snapshots, lineColor = SemanticBlue)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(
                    DateUtils.formatToDisplay(snapshots.first().first),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateUtils.formatToDisplay(snapshots.last().first),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = SemanticBlue.copy(alpha = 0.5f),
                )
                Text(
                    "Building your history",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "Backfill month-end snapshots from your transactions to see the trend right away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NetWorthLatestChangeCard(
    change: Double?,
    previousDate: String?,
    currencyCode: String,
    locale: Locale,
) {
    val positive = (change ?: 0.0) >= 0.0
    val title = when {
        change == null -> "Waiting for another snapshot"
        change > 0.0 -> "Net worth increased"
        change < 0.0 -> "Net worth decreased"
        else -> "No change from last snapshot"
    }
    val detail = if (change == null || previousDate == null) {
        "Save another snapshot to compare movement clearly."
    } else {
        "Compared with ${DateUtils.formatToDisplay(previousDate)}"
    }
    PremiumCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (change != null) {
                Text(
                    (if (positive) "+" else "") + CurrencyFormatter.listRow(change, currencyCode, locale),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (positive) Emerald500 else SemanticRed,
                )
            }
        }
    }
}

@Composable
private fun NetWorthCalloutRow(
    sorted: List<Pair<String, Double>>,
    current: Double,
    currencyCode: String,
    locale: Locale,
) {
    val today = LocalDate.now()
    val nwAt: (LocalDate) -> Double? = { target ->
        sorted.lastOrNull {
            runCatching { LocalDate.parse(it.first) <= target }.getOrDefault(false)
        }?.second
    }
    val oneMonthAgo = nwAt(today.minusMonths(1))
    val sixMonthAgo = nwAt(today.minusMonths(6))
    val allTimeHigh = sorted.maxOf { it.second }
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun signedPct(now: Double, then: Double?): String {
        if (then == null) return "Need more data"
        if (then == 0.0) return "No base"
        val pct = (now - then) / kotlin.math.abs(then) * 100
        val sign = if (pct >= 0) "+" else ""
        return "$sign${String.format(locale, "%.1f", pct)}%"
    }

    fun changeColor(now: Double, then: Double?): Color =
        if (then == null) neutralColor else if (now >= then) Emerald500 else SemanticRed

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NetWorthCallout(
            label = "1-Month",
            value = signedPct(current, oneMonthAgo),
            valueColor = changeColor(current, oneMonthAgo),
            modifier = Modifier.weight(1f),
        )
        NetWorthCallout(
            label = "6-Month",
            value = signedPct(current, sixMonthAgo),
            valueColor = changeColor(current, sixMonthAgo),
            modifier = Modifier.weight(1f),
        )
        NetWorthCallout(
            label = "Highest",
            value = CurrencyFormatter.listRow(allTimeHigh, currencyCode, locale),
            valueColor = Emerald500,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NetWorthSnapshotCard(
    date: String,
    netWorth: Double,
    change: Double?,
    currencyCode: String,
    locale: Locale,
) {
    val positive = (change ?: 0.0) >= 0.0
    val movement = when {
        change == null -> "First saved point"
        change > 0.0 -> "Up from previous"
        change < 0.0 -> "Down from previous"
        else -> "No change from previous"
    }
    PremiumCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateUtils.formatToDisplay(date),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    movement,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyFormatter.listRow(netWorth, currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (change != null) {
                    Text(
                        (if (positive) "+" else "") + CurrencyFormatter.listRow(change, currencyCode, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (positive) Emerald500 else SemanticRed,
                    )
                }
            }
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
            val y = (size.height - ((nw - minV) / range * size.height).toFloat())
                .coerceIn(0f, size.height)
            Offset(x, y)
        }
        val fill = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.15f))
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, lineColor, style = Stroke(3.dp.toPx()))
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
        )
    }
}
