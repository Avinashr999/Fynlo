package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Debt
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DebtPayoffPlanner
import app.fynlo.logic.InterestEngine
import java.util.Locale
import kotlin.math.ceil
import app.fynlo.ui.theme.*

@Composable
fun DebtPayoffScreen(viewModel: FinanceViewModel) {
    val debts  by viewModel.debts.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = remember { Locale.getDefault() }

    val activeDebts = debts.filter { it.status != "Cleared" && it.amount > it.paid }

    if (activeDebts.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No active debts!", style = MaterialTheme.typography.titleLarge, color = Emerald500)
                Text("You are debt-free.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val totalOwed = remember(activeDebts) {
        activeDebts.sumOf { d ->
            val interest = InterestEngine.calcIntAccrued(d.amount, d.rate, d.date, d.intType, d.due, d.paid)
            d.amount + interest - d.paid
        }
    }

    // 3.2.62 — planner state moves out so it survives the LazyColumn
    // rewrite (key prevents stuck inputs when the debts list churns).
    val planInputs = remember(activeDebts) {
        activeDebts.map { d ->
            val interest = InterestEngine.calcIntAccrued(d.amount, d.rate, d.date, d.intType, d.due, d.paid)
            val outstanding = (d.amount + interest - d.paid).coerceAtLeast(0.0)
            DebtPayoffPlanner.DebtInput(
                id = d.id, name = d.name,
                outstandingBalance = outstanding,
                annualRatePct = d.rate,
            )
        }
    }
    val avgMonthlyPaid = remember(activeDebts) {
        val today = java.time.LocalDate.now()
        activeDebts.sumOf { d ->
            val loanDate = runCatching {
                java.time.LocalDate.parse(d.date, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }.getOrDefault(today)
            val months = (java.time.temporal.ChronoUnit.DAYS.between(loanDate, today) / 30.0).coerceAtLeast(1.0)
            d.paid / months
        }.coerceAtLeast(1.0)
    }
    var budgetText by remember(avgMonthlyPaid) {
        mutableStateOf(((avgMonthlyPaid * 2).toLong()).toString())
    }
    var strategy by remember { mutableStateOf(DebtPayoffPlanner.Strategy.AVALANCHE) }
    // 3.2.62 — planner is opt-in. Default OFF so the screen lands on the
    // familiar tracker; flip the Switch to expand the Snowball/Avalanche
    // surface. Survives recomposition; not persisted across launches.
    var showPlanner by remember { mutableStateOf(false) }

    // 3.2.62 — single LazyColumn so EVERYTHING scrolls: header, hero,
    // toggle, planner (when expanded), divider, per-debt cards. Pre-3.2.62
    // the outer Column kept the header / hero / planner fixed and only
    // the inner LazyColumn (debts) scrolled, which clipped the planner
    // and the per-debt cards on shorter screens.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Text("Debt Payoff Tracker",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
        }

        item("hero") {
            Column(Modifier.fillMaxWidth()) {
                Text("Total Remaining", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (isPrivacy) "••••" else CurrencyFormatter.detail(totalOwed, currencyCode, locale),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = SemanticRed)
                Text("Across ${app.fynlo.logic.pluralize(activeDebts.size, "active debt")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 3.2.62 — toggle row. Always visible so the planner is
        // discoverable; default OFF so the screen isn't dominated by it
        // for users who just want the tracker. Tap row OR switch to flip.
        item("planner-toggle") {
            Surface(
                onClick = { showPlanner = !showPlanner },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show payoff plan",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Snowball vs Avalanche, with your budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 3.2.63 — explicit unchecked colors. The Material 3 default
                    // unchecked track is `surfaceVariant` with a `surface` thumb,
                    // which on light mode renders as off-white-on-off-white sitting
                    // inside our row's `surfaceVariant.copy(alpha=0.4f)` background
                    // → invisible. Force a visibly-darker thumb + a clearly-tinted
                    // track so both states read at a glance regardless of theme.
                    Switch(
                        checked = showPlanner,
                        onCheckedChange = { showPlanner = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = Color.White,
                            checkedTrackColor   = Emerald500,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
            }
        }

        if (showPlanner) {
            item("planner") {
                PayoffPlanSection(
                    inputs       = planInputs,
                    strategy     = strategy,
                    onStrategy   = { strategy = it },
                    budgetText   = budgetText,
                    onBudget     = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                    currencyCode = currencyCode,
                    locale       = locale,
                    isPrivacy    = isPrivacy
                )
            }
        }

        item("divider") {
            HorizontalDivider(thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }

        itemsIndexed(activeDebts, key = { _, d -> d.id }) { index, debt ->
            DebtPayoffCard(debt, currencyCode, locale, isPrivacy)
            if (index < activeDebts.lastIndex) {
                HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
        }
    }
}

/**
 * C22 (3.2.60) — Snowball vs Avalanche planner surface. Runs the
 * planner for both strategies so the comparison card can show "X saves Y
 * months / Z interest vs the other one".
 */
@Composable
private fun PayoffPlanSection(
    inputs:       List<DebtPayoffPlanner.DebtInput>,
    strategy:     DebtPayoffPlanner.Strategy,
    onStrategy:   (DebtPayoffPlanner.Strategy) -> Unit,
    budgetText:   String,
    onBudget:     (String) -> Unit,
    currencyCode: String,
    locale:       Locale,
    isPrivacy:    Boolean = false
) {
    val budget = budgetText.toDoubleOrNull() ?: 0.0
    val snow = remember(inputs, budget) { DebtPayoffPlanner.plan(inputs, budget, DebtPayoffPlanner.Strategy.SNOWBALL) }
    val aval = remember(inputs, budget) { DebtPayoffPlanner.plan(inputs, budget, DebtPayoffPlanner.Strategy.AVALANCHE) }
    val active = if (strategy == DebtPayoffPlanner.Strategy.SNOWBALL) snow else aval

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Payoff plan", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

        // Strategy toggle.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = strategy == DebtPayoffPlanner.Strategy.SNOWBALL,
                onClick = { onStrategy(DebtPayoffPlanner.Strategy.SNOWBALL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = {},
            ) { Text("Snowball", style = MaterialTheme.typography.labelSmall) }
            SegmentedButton(
                selected = strategy == DebtPayoffPlanner.Strategy.AVALANCHE,
                onClick = { onStrategy(DebtPayoffPlanner.Strategy.AVALANCHE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = {},
            ) { Text("Avalanche", style = MaterialTheme.typography.labelSmall) }
        }

        OutlinedTextField(
            value = if (isPrivacy) "••••" else budgetText,
            onValueChange = onBudget,
            label = { Text("Monthly budget (${app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)})") },
            singleLine = true,
            readOnly = isPrivacy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        if (!active.feasible) {
            Text(
                "Budget too small — interest is growing faster than you're paying it down. " +
                "Try a higher monthly amount.",
                style = MaterialTheme.typography.labelSmall,
                color = SemanticRed,
            )
        } else {
            // Hero stats for the active strategy.
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                PayoffStat(
                    label = "Debt-free in",
                    value = "${active.totalMonths} mo",
                    color = Emerald500,
                    modifier = Modifier.weight(1f),
                )
                PayoffStat(
                    label = "Total interest",
                    value = if (isPrivacy) "••••" else CurrencyFormatter.detail(active.totalInterestPaid, currencyCode, locale),
                    color = SemanticAmber,
                    modifier = Modifier.weight(1f),
                )
            }

            // Comparison line — only render when the two strategies disagree
            // enough to surface a number; if they tie (e.g. one debt) hide it.
            val monthsDiff   = (snow.totalMonths - aval.totalMonths).coerceAtLeast(0)
            val interestDiff = (snow.totalInterestPaid - aval.totalInterestPaid).coerceAtLeast(0.0)
            if (snow.feasible && aval.feasible && (monthsDiff > 0 || interestDiff > 1.0)) {
                val interestDiffText = if (isPrivacy) "••••" else CurrencyFormatter.detail(interestDiff, currencyCode, locale)
                val winnerNote = if (strategy == DebtPayoffPlanner.Strategy.AVALANCHE) {
                    "Avalanche saves $interestDiffText " +
                    "vs Snowball${if (monthsDiff > 0) " and clears $monthsDiff mo sooner" else ""}."
                } else {
                    "Snowball costs $interestDiffText more " +
                    "than Avalanche but clears small debts first."
                }
                Text(winnerNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Payoff order — surfaces who gets cleared first / last.
            Spacer(Modifier.height(4.dp))
            Text("Order under ${if (strategy == DebtPayoffPlanner.Strategy.AVALANCHE) "Avalanche" else "Snowball"}:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            active.perDebt.forEachIndexed { idx, plan ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("${idx + 1}. ${plan.name}",
                        style = MaterialTheme.typography.labelMedium)
                    Text(if (plan.payoffMonth > 0) "month ${plan.payoffMonth}" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PayoffStat(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = color)
        }
    }
}

@Composable
private fun DebtPayoffCard(debt: Debt, currencyCode: String, locale: Locale, isPrivacy: Boolean = false) {
    val interest    = InterestEngine.calcIntAccrued(debt.amount, debt.rate, debt.date, debt.intType, debt.due, debt.paid)
    val outstanding = (debt.amount + interest - debt.paid).coerceAtLeast(0.0)
    val monthlyRate = debt.rate / 100.0 / 12.0

    val today = java.time.LocalDate.now()
    val loanDate = runCatching { java.time.LocalDate.parse(debt.date, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(today)
    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(loanDate, today).toDouble().coerceAtLeast(1.0)
    val monthsElapsed = daysBetween / 30.0
    val avgMonthlyPayment = if (monthsElapsed > 0) debt.paid / monthsElapsed else 0.0

    val monthsRemaining = when {
        avgMonthlyPayment <= 0 -> null
        monthlyRate == 0.0     -> ceil(outstanding / avgMonthlyPayment).toInt()
        else -> {
            val ratio = monthlyRate * outstanding / avgMonthlyPayment
            if (ratio >= 1.0) null
            else ceil(-Math.log(1.0 - ratio) / Math.log(1.0 + monthlyRate)).toInt()
        }
    }

    val progress = if (debt.amount > 0) (debt.paid / (debt.amount + interest)).toFloat().coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(debt.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(color = SemanticRed.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
                    Text(if (isPrivacy) "••••" else CurrencyFormatter.detail(outstanding, currencyCode, locale),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SemanticRed, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                color = Emerald500)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}% paid", style = MaterialTheme.typography.labelSmall, color = Emerald500)
                Text("${if (isPrivacy) "••••" else CurrencyFormatter.detail(debt.paid, currencyCode, locale)} paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                InfoPill("Rate", "${debt.rate}%", Modifier.weight(1f))
                InfoPill("Interest", if (isPrivacy) "••••" else CurrencyFormatter.detail(interest, currencyCode, locale), Modifier.weight(1f))
                if (monthsRemaining != null) {
                    InfoPill("Est. Payoff", "$monthsRemaining mo", Modifier.weight(1f))
                } else {
                    val avgText = if (isPrivacy) "••••" else if (avgMonthlyPayment > 0) CurrencyFormatter.detail(avgMonthlyPayment, currencyCode, locale) else "No payments yet"
                    InfoPill("Avg/Month", avgText, Modifier.weight(1f))
                }
            }
    }
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
