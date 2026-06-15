package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Budget
import app.fynlo.logic.CurrencyFormatter
import java.time.LocalDate
import java.util.Locale
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale


@Composable
fun BudgetScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val budgets  by viewModel.budgets.collectAsState()
    val expenses by viewModel.expenseAnalytics.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    val locale   = LocalLocale.current.platformLocale
    var showAddDialog by remember { mutableStateOf(false) }

    val today         = LocalDate.now()
    val daysInMonth   = today.lengthOfMonth()
    val daysRemaining = daysInMonth - today.dayOfMonth
    val daysPassed    = today.dayOfMonth

    if (showAddDialog) {
        AddBudgetDialog(
            currencySymbol = currencySymbol,
            // C04 Stage 3: chained-fallback prefill. The audit's
            // BudgetScreen criterion is the highest-uncapped-spend
            // heuristic, NOT pure recency — "the category most likely
            // to need a budget is the one with the biggest unmanaged
            // spend, not the one most recently used." Recency is the
            // fallback for when every spent-on category already has a
            // budget (or there are no expenses), and blank is the
            // last-resort for a fresh install. The dialog calls these
            // suspend / sync hooks in a single LaunchedEffect(Unit) so
            // the chip row renders pre-selected on first composition.
            suggestCategory      = { viewModel.suggestBudgetCategory() },
            rememberLastCategory = { viewModel.rememberLastBudgetCategory() },
            onDismiss = { showAddDialog = false },
            onConfirm = { budget ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // Record the final pick on the recency layer so the
                // fallback stays populated for the eventual case where
                // every uncapped category gets a budget and the
                // heuristic returns null.
                viewModel.recordBudgetCategory(budget.category)
                viewModel.addBudget(budget)
                showAddDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Budgeting", "Monthly spending limits")
        val sorted = remember(budgets, expenses) {
            budgets.sortedByDescending { b ->
                val pct = (expenses[b.category] ?: 0.0) / b.limitAmount
                pct
            }
        }
        Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = FabBottomPadding)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                                                Text("$daysRemaining days remaining in ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (budgets.isNotEmpty()) {
                item {
                    val totalLimit   = budgets.sumOf { it.limitAmount }
                    val totalSpent   = budgets.sumOf { expenses[it.category] ?: 0.0 }
                    val totalRemain  = totalLimit - totalSpent
                    val overBudget   = budgets.count { (expenses[it.category] ?: 0.0) > it.limitAmount }
                    // C22 (3.2.57) — per-budget threshold replaces the
                    // hardcoded 0.8. Each budget's own `alertThresholdPct`
                    // gates whether it counts toward the "near limit"
                    // total in the overview chip.
                    val nearLimit    = budgets.count { b ->
                        val pct = (expenses[b.category] ?: 0.0) / b.limitAmount * 100
                        pct >= b.alertThresholdPct && pct < 100
                    }

                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                            Text("This Month's Overview", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                OverviewChip("Total Budget", CurrencyFormatter.detail(totalLimit, currencyCode, locale),
                                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                OverviewChip("Spent", CurrencyFormatter.detail(totalSpent, currencyCode, locale),
                                    if (totalSpent > totalLimit) SemanticRed else Emerald500, Modifier.weight(1f))
                                OverviewChip("Remaining",
                                    if (totalRemain < 0) CurrencyFormatter.negative(totalRemain, currencyCode, locale)
                                    else CurrencyFormatter.detail(totalRemain, currencyCode, locale),
                                    if (totalRemain < 0) SemanticRed else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            }
                            if (overBudget > 0 || nearLimit > 0) {
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (overBudget > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticRed.copy(alpha = 0.1f)) {
                                            Text("$overBudget exceeded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SemanticRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    if (nearLimit > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticAmber.copy(alpha = 0.1f)) {
                                            Text("$nearLimit near limit",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SemanticAmber,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                }
            }

            if (!budgets.isEmpty()) {
                itemsIndexed(sorted, key = { _, b -> b.category }) { index, budget ->
                    val actualSpent = expenses[budget.category] ?: 0.0
                    BudgetCard(budget, actualSpent, daysRemaining, daysPassed, currencySymbol, currencyCode, locale,
                        onDelete = { viewModel.deleteBudget(budget) })
                    if (index < sorted.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
        // C07 fix (UX_AUDIT §C07): on empty state show ONLY the shared
        // EmptyState CTA overlaid on the outer Box, hiding the FAB so the
        // user sees one unambiguous "Add First Budget" entry point — not the
        // pre-3.2.12 mix of FAB + "Tap + to add" subtitle (which referred to
        // a now-hidden Scaffold FAB) + a now-missing inline action button.
        // The LazyColumn above stays mounted so its header card (with the
        // days-remaining counter etc.) keeps rendering even when empty.
        if (budgets.isEmpty()) {
            EmptyState(
                icon = Icons.Default.AccountBalanceWallet,
                title = "No budgets set yet",
                subtitle = "Add a spending limit for any category",
                actionLabel = "Add First Budget",
                onAction = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
            )
        } else {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
        }
    }
}

@Composable
fun BudgetCard(
    budget: Budget,
    actualSpent: Double,
    daysRemaining: Int,
    daysPassed: Int,
    currencySymbol: String,
    currencyCode: String,
    locale: Locale,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete budget?") },
            text  = { Text("Remove the \"${budget.category}\" budget? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showDeleteConfirm = false
                },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    val progress     = (actualSpent / budget.limitAmount).toFloat().coerceIn(0f, 1f)
    val pct          = (progress * 100).toInt()
    val remaining    = budget.limitAmount - actualSpent
    val isExceeded   = actualSpent > budget.limitAmount
    // C22 (3.2.57) — per-budget warning threshold (default 80; user can
    // tighten to 50 for "must not slip" categories or loosen to 95 for
    // "rough cap" ones).
    val isNearLimit  = pct >= budget.alertThresholdPct && !isExceeded
    val dailyBudget  = budget.limitAmount / (daysPassed + daysRemaining)
    val dailySpent   = if (daysPassed > 0) actualSpent / daysPassed else 0.0
    val projectedEnd = dailySpent * (daysPassed + daysRemaining)

    val barColor = when {
        isExceeded  -> SemanticRed
        isNearLimit -> SemanticAmber
        pct >= 50   -> Emerald500
        else        -> MaterialTheme.colorScheme.primary
    }

    Column(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isExceeded) {
                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticRed.copy(alpha = 0.15f)) {
                            Text("EXCEEDED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SemanticRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else if (isNearLimit) {
                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticAmber.copy(alpha = 0.15f)) {
                            Text("NEAR LIMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SemanticAmber, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(budget.category, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    // 3.2.65 — was Color.Red; theme-aware error token so the
                    // delete tint stays balanced in both light and dark.
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = barColor, trackColor = barColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${CurrencyFormatter.detail(actualSpent, currencyCode, locale)} spent",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                Text("$pct% of ${CurrencyFormatter.detail(budget.limitAmount, currencyCode, locale)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                Arrangement.SpaceBetween
            ) {
                    InfoItem("Remaining", if (isExceeded) CurrencyFormatter.negative(remaining, currencyCode, locale)
                        else CurrencyFormatter.detail(remaining, currencyCode, locale),
                        if (isExceeded) SemanticRed else Emerald500)
                    InfoItem("Daily Budget", CurrencyFormatter.detail(dailyBudget, currencyCode, locale),
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Daily Spent", CurrencyFormatter.detail(dailySpent, currencyCode, locale),
                        if (dailySpent > dailyBudget) SemanticRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Projected", CurrencyFormatter.detail(projectedEnd, currencyCode, locale),
                        if (projectedEnd > budget.limitAmount) SemanticRed else Emerald500)
            }

            if (projectedEnd > budget.limitAmount && !isExceeded) {
                Spacer(Modifier.height(4.dp))
                Text("At this rate you'll exceed by ${CurrencyFormatter.detail(projectedEnd - budget.limitAmount, currencyCode, locale)} this month",
                    style = MaterialTheme.typography.labelSmall, color = SemanticAmber)
            }
            // C22 (3.2.57) — surface the user's chosen warning threshold.
            // Default 80 stays hidden (no point telling the user about a
            // setting they didn't change). Non-default values show as a
            // muted bottom-line so the user can see why a particular
            // budget tripped (or didn't trip) the NEAR LIMIT state at the
            // percentage it did.
            if (budget.alertThresholdPct != 80) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Warning set at ${budget.alertThresholdPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
    }
}

@Composable
private fun InfoItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun OverviewChip(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBudgetDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Budget) -> Unit,
    // C04 Stage 3: optional smart-default hooks. Defaults are no-ops so
    // the dialog stays previewable / testable without a ViewModel. The
    // production wiring above passes
    //   viewModel::suggestBudgetCategory  (heuristic-first)
    //   viewModel::rememberLastBudgetCategory  (recency fallback)
    // and they're chained on dialog open inside the LaunchedEffect.
    suggestCategory: () -> String? = { null },
    rememberLastCategory: suspend () -> String? = { null },
) {
    // C05: source from the shared expense list rather than a local hardcode.
    // Set Category Limit is expense-only by design — budgets gate outflows,
    // not inflows — so there's no Income/Expense toggle to bleed across.
    // "Custom" is appended as the trailing chip so users can budget a
    // user-defined category that isn't on the curated list (e.g. "Charity").
    val categories = remember { app.fynlo.data.Categories.EXPENSE + "Custom" }

    var selectedCategory by remember { mutableStateOf("") }
    var customCategory   by remember { mutableStateOf("") }
    var limit            by remember { mutableStateOf("") }
    // C22 (3.2.57) — per-budget warning threshold (50..95 in 5% steps,
    // default 80). The slider lives below the limit field so it reads as
    // "set the cap, then tune when you want to be warned".
    var thresholdPct     by remember { mutableStateOf(80) }

    // C04 Stage 3: chained-fallback prefill, applied once on dialog open.
    //   1. Try `suggestCategory()` — the highest-uncapped-spend heuristic.
    //   2. If null (everything's already budgeted, or no expenses),
    //      fall back to `rememberLastCategory()` (pure recency).
    //   3. If still null (fresh install), leave blank.
    // The audit's reasoning (UX_AUDIT §C04): "the category most likely to
    // need a budget is the one with the biggest unmanaged spend, not the
    // one most recently used." Recency is correct only as a *fallback*.
    //
    // Handles three mapping cases for the resolved value, the same way
    // AddTransactionDialog does:
    //   - blank   → leave both fields empty so the user picks fresh
    //   - in curated list → select the chip, clear customCategory
    //   - otherwise (a previously-recorded Custom-typed value) →
    //     select the "Custom" chip and restore the typed string so
    //     the text input below the chip row re-renders it
    LaunchedEffect(Unit) {
        val resolved = suggestCategory() ?: rememberLastCategory()
        when {
            resolved.isNullOrBlank() -> {
                selectedCategory = ""
                customCategory = ""
            }
            resolved in categories -> {
                selectedCategory = resolved
                customCategory = ""
            }
            else -> {
                selectedCategory = "Custom"
                customCategory = resolved
            }
        }
    }

    val finalCategory = if (selectedCategory == "Custom") customCategory else selectedCategory

    // C22 dialog universalization (3.2.54) — migrated to canonical FormDialog.
    app.fynlo.ui.components.FormDialog(
        title = "Set Category Limit",
        onDismiss = onDismiss,
    ) {
        val limitNum = limit.toDoubleOrNull() ?: 0.0
        val disabledReason: String? = when {
            finalCategory.isBlank() -> "Pick a category to continue"
            limitNum <= 0.0         -> "Enter a positive monthly limit to continue"
            else                    -> null
        }

        app.fynlo.ui.components.FormSectionLabel("Category")
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        if (selectedCategory == "Custom") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customCategory,
                onValueChange = { customCategory = it },
                placeholder = { Text("Custom category name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Monthly limit ($currencySymbol)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = limit,
            onValueChange = { limit = it },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        // C22 (3.2.57) — discrete warning-threshold slider (50..95 in 5%
        // steps). Material 3 Slider takes a continuous float; we coerce
        // to the nearest 5% on change. 9 steps means 10 anchor points
        // including the endpoints. Default 80 matches the pre-3.2.57
        // hardcoded ratio so AddBudget-and-Save reproduces existing UX.
        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Warn me at $thresholdPct% of limit")
        Spacer(Modifier.height(4.dp))
        Slider(
            value = thresholdPct.toFloat(),
            onValueChange = { f ->
                val snapped = (f / 5).toInt() * 5
                thresholdPct = snapped.coerceIn(50, 95)
            },
            valueRange = 50f..95f,
            steps = 8,  // 10 anchors (50, 55, ..., 95) → 8 intermediate
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("50%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
            Text("95%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onConfirm(Budget(finalCategory, limitNum, alertThresholdPct = thresholdPct)) },
            enabled = disabledReason == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Save Budget", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(disabledReason)
    }
}
