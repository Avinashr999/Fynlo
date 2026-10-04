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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.fynlo.logic.NetWorthHistoryPolicy
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.PremiumCard
import app.fynlo.ui.theme.PremiumScreenHeader
import app.fynlo.ui.theme.SemanticBlue
import app.fynlo.ui.theme.SemanticRed
import java.time.LocalDate
import java.util.Locale

@Composable
fun NetWorthHistoryScreen(viewModel: FinanceViewModel) {
    val summary by viewModel.financialSummary.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val historyFlow = remember(viewModel, currentProject?.id) { viewModel.getAllNetWorthHistory() }
    val snapshots by historyFlow.collectAsState(initial = emptyList())
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    LaunchedEffect(currentProject?.id, summary.netWorth, summary.totalAssets, summary.totalDebtPrincipal, summary.totalDebtInterest) {
        viewModel.saveSnapshotNow()
    }

    val sorted = NetWorthHistoryPolicy.trusted(snapshots)
    var showOriginals by remember { mutableStateOf(false) }
    val currentSnapshot = sorted.lastOrNull()
    val previousSnapshot = sorted.dropLast(1).lastOrNull()
    val changeFromPrevious = if (currentSnapshot != null && previousSnapshot != null) {
        currentSnapshot.netWorth - previousSnapshot.netWorth
    } else null

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Net Worth History", subtitle = "Current totals and saved history")
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

            NetWorthCompositionCard(
                totalAssets = summary.totalAssets,
                totalDebt = summary.totalDebtPrincipal + summary.totalDebtInterest,
                netWorth = summary.netWorth,
                currencyCode = currencyCode,
                locale = locale,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Only complete totals are compared. Unavailable dates are not estimated. Changes in net worth are not the same as income or investment returns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    current = NetWorthHistoryPolicy.at(sorted, LocalDate.now())?.netWorth,
                    currencyCode = currencyCode,
                    locale = locale,
                )

                Spacer(Modifier.height(16.dp))

            }

            Text("Daily history", style = MaterialTheme.typography.titleMedium)
            Text("Last 30 days", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            (0L..29L).forEach { daysAgo ->
                val day = LocalDate.now().minusDays(daysAgo)
                val snap = NetWorthHistoryPolicy.at(sorted, day)
                if (snap != null) {
                    NetWorthSnapshotCard(
                        date = snap.date,
                        netWorth = snap.netWorth,
                        change = NetWorthHistoryPolicy.change(sorted, day.minusDays(1), day),
                        currencyCode = currencyCode,
                        locale = locale,
                    )
                    if (snap.captureSource == NetWorthHistoryPolicy.RECOVERED) {
                        Text("Recovered from dated backup", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DateUtils.formatToDisplay(day.toString()))
                        Text("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = { showOriginals = !showOriginals }) {
                Text(if (showOriginals) "Hide saved records" else "Saved records and original history")
            }
            if (showOriginals) {
                Text("Unverified originals are preserved for reference and are never used in comparisons.",
                    style = MaterialTheme.typography.bodySmall)
                snapshots.sortedByDescending { it.date }.forEach { row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(DateUtils.formatToDisplay(row.date), fontWeight = FontWeight.SemiBold)
                        Text(if (NetWorthHistoryPolicy.isTrusted(row))
                            "${CurrencyFormatter.exact(row.netWorth, currencyCode, locale)} - complete total"
                        else "Unavailable - original saved total is unverified")
                        if (!NetWorthHistoryPolicy.isTrusted(row)) {
                            Text("Original: ${CurrencyFormatter.exact(row.netWorth, currencyCode, locale)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (row.originalSnapshotJson.isNotBlank()) {
                            val original = runCatching {
                                kotlinx.serialization.json.Json.decodeFromString<app.fynlo.data.model.NetWorthSnapshot>(row.originalSnapshotJson)
                            }.getOrNull()
                            Text(original?.let { "Preserved original (unverified): ${CurrencyFormatter.exact(it.netWorth, currencyCode, locale)}" }
                                ?: "Original record preserved", style = MaterialTheme.typography.bodySmall)
                        }
                        if (NetWorthHistoryPolicy.isTrusted(row)) {
                            Text(if (row.captureSource == NetWorthHistoryPolicy.RECOVERED)
                                "Source: backup dated ${DateUtils.formatToDisplay(row.date)}"
                            else "Saved from complete records", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun NetWorthCompositionCard(
    totalAssets: Double,
    totalDebt: Double,
    netWorth: Double,
    currencyCode: String,
    locale: Locale,
) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Why assets move",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "Assets include accounts, investments, and money receivable. Debts reduce net worth separately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NetWorthCompositionRow("Assets", totalAssets, Emerald500, currencyCode, locale)
            NetWorthCompositionRow("Debts owed", totalDebt, SemanticRed, currencyCode, locale)
            NetWorthCompositionRow("Net worth", netWorth, if (netWorth >= 0) Emerald500 else SemanticRed, currencyCode, locale)
            Text(
                "If you repay a debt from cash, assets can reduce because cash leaves an account. The debt also reduces, so net worth changes only by the real difference.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetWorthCompositionRow(
    label: String,
    value: Double,
    color: Color,
    currencyCode: String,
    locale: Locale,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            CurrencyFormatter.exact(value, currencyCode, locale),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = color,
        )
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
            CurrencyFormatter.exact(netWorth, currencyCode, locale),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = if (netWorth >= 0) Emerald500 else SemanticRed,
        )
        Text(
            pluralize(snapshots.size, "snapshot") + " recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Saved totals are separate from your account transaction history.",
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
                    "New daily totals are saved when you open your ledger. Earlier dates are not estimated.",
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
        change > 0.0 -> "Saved total is higher"
        change < 0.0 -> "Saved total is lower"
        else -> "No change from last snapshot"
    }
    val detail = if (change == null || previousDate == null) {
        "Another saved total is needed for comparison."
    } else {
        "Compared with ${DateUtils.formatToDisplay(previousDate)}"
    }
    PremiumCard {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
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
                    (if (positive) "+" else "") + CurrencyFormatter.exact(change, currencyCode, locale),
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
    current: Double?,
    currencyCode: String,
    locale: Locale,
) {
    val today = LocalDate.now()
    val nwAt: (LocalDate) -> Double? = { target ->
        sorted.lastOrNull {
            it.first == target.toString()
        }?.second
    }
    val oneMonthAgo = nwAt(today.minusMonths(1))
    val sixMonthAgo = nwAt(today.minusMonths(6))
    val allTimeHigh = sorted.maxOf { it.second }
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun signedPct(now: Double?, then: Double?): String {
        if (then == null || now == null) return "Unavailable"
        if (then == 0.0) return "No base"
        val pct = (now - then) / kotlin.math.abs(then) * 100
        val sign = if (pct >= 0) "+" else ""
        return "$sign${String.format(locale, "%.1f", pct)}%"
    }

    fun changeColor(now: Double?, then: Double?): Color =
        if (then == null || now == null) neutralColor else if (now >= then) Emerald500 else SemanticRed

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NetWorthCallout(
            label = "1-month saved",
            value = signedPct(current, oneMonthAgo),
            valueColor = changeColor(current, oneMonthAgo),
            modifier = Modifier.weight(1f),
        )
        NetWorthCallout(
            label = "6-month saved",
            value = signedPct(current, sixMonthAgo),
            valueColor = changeColor(current, sixMonthAgo),
            modifier = Modifier.weight(1f),
        )
      }
        NetWorthCallout(
            label = "Highest saved",
            value = CurrencyFormatter.exact(allTimeHigh, currencyCode, locale),
            valueColor = Emerald500,
            modifier = Modifier.fillMaxWidth(),
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
        change == null -> "Previous day unavailable"
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
                    CurrencyFormatter.exact(netWorth, currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (change != null) {
                    Text(
                        (if (positive) "+" else "") + CurrencyFormatter.exact(change, currencyCode, locale),
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
        val firstDay = LocalDate.parse(snapshots.first().first).toEpochDay()
        val span = (LocalDate.parse(snapshots.last().first).toEpochDay() - firstDay).coerceAtLeast(1)
        val pts = snapshots.map { (date, nw) ->
            val x = (LocalDate.parse(date).toEpochDay() - firstDay).toFloat() / span * size.width
            val y = (size.height - ((nw - minV) / range * size.height).toFloat())
                .coerceIn(0f, size.height)
            Offset(x, y)
        }
        // Gaps have no connecting line: no implied values on unsaved dates.
        pts.zipWithNext().forEachIndexed { index, (a, b) ->
            if (LocalDate.parse(snapshots[index].first).plusDays(1).toString() == snapshots[index + 1].first) {
                drawLine(lineColor, a, b, strokeWidth = 3.dp.toPx())
            }
        }
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
