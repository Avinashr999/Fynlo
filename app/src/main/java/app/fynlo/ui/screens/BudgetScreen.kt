package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.LocalDate
import java.util.Locale
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@Composable
fun BudgetScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val budgets  by viewModel.budgets.collectAsState()
    val expenses by viewModel.expenseAnalytics.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale   = remember { Locale.getDefault() }
    var showAddDialog by remember { mutableStateOf(false) }

    val today         = LocalDate.now()
    val daysInMonth   = today.lengthOfMonth()
    val daysRemaining = daysInMonth - today.dayOfMonth
    val daysPassed    = today.dayOfMonth

    if (showAddDialog) {
        AddBudgetDialog(
            currencySymbol = currencySymbol,
            onDismiss = { showAddDialog = false },
            onConfirm = { budget ->
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
            contentPadding = PaddingValues(bottom = 100.dp)
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
                    val nearLimit    = budgets.count { b ->
                        val pct = (expenses[b.category] ?: 0.0) / b.limitAmount
                        pct >= 0.8 && pct < 1.0
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
                                OverviewChip("Total Budget", "$currencySymbol${String.format(locale, "%,.0f", totalLimit)}",
                                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                OverviewChip("Spent", "$currencySymbol${String.format(locale, "%,.0f", totalSpent)}",
                                    if (totalSpent > totalLimit) SemanticRed else Emerald500, Modifier.weight(1f))
                                OverviewChip("Remaining", "$currencySymbol${String.format(locale, "%,.0f", totalRemain)}",
                                    if (totalRemain < 0) SemanticRed else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            }
                            if (overBudget > 0 || nearLimit > 0) {
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (overBudget > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticRed.copy(alpha = 0.1f)) {
                                            Text("⚠ $overBudget exceeded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SemanticRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    if (nearLimit > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticAmber.copy(alpha = 0.1f)) {
                                            Text("⚡ $nearLimit near limit",
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

            if (budgets.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant)
                            Text("No budgets set yet", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap + to add a spending limit for any category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(sorted, key = { _, b -> b.category }) { index, budget ->
                    val actualSpent = expenses[budget.category] ?: 0.0
                    BudgetCard(budget, actualSpent, daysRemaining, daysPassed, currencySymbol, locale,
                        onDelete = { viewModel.deleteBudget(budget) })
                    if (index < sorted.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Budget")
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
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    val progress     = (actualSpent / budget.limitAmount).toFloat().coerceIn(0f, 1f)
    val pct          = (progress * 100).toInt()
    val remaining    = budget.limitAmount - actualSpent
    val isExceeded   = actualSpent > budget.limitAmount
    val isNearLimit  = pct >= 80 && !isExceeded
    val dailyBudget  = budget.limitAmount / (daysPassed + daysRemaining)
    val dailySpent   = if (daysPassed > 0) actualSpent / daysPassed else 0.0
    val projectedEnd = dailySpent * (daysPassed + daysRemaining)

    val barColor = when {
        isExceeded  -> SemanticRed
        isNearLimit -> SemanticAmber
        pct >= 50   -> Emerald500
        else        -> MaterialTheme.colorScheme.primary
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isExceeded) {
                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticRed.copy(alpha = 0.15f)) {
                            Text("EXCEEDED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SemanticRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else if (isNearLimit) {
                        Surface(shape = RoundedCornerShape(8.dp), color = SemanticAmber.copy(alpha = 0.15f)) {
                            Text("⚡ NEAR LIMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SemanticAmber, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(budget.category, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Color.Red.copy(alpha = 0.5f))
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
                Text("$currencySymbol${String.format(locale, "%,.0f", actualSpent)} spent",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                Text("$pct% of $currencySymbol${String.format(locale, "%,.0f", budget.limitAmount)}",
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
                    InfoItem("Remaining", if (isExceeded) "-$currencySymbol${String.format(locale, "%,.0f", -remaining)}"
                        else "$currencySymbol${String.format(locale, "%,.0f", remaining)}",
                        if (isExceeded) SemanticRed else Emerald500)
                    InfoItem("Daily Budget", "$currencySymbol${String.format(locale, "%,.0f", dailyBudget)}",
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Daily Spent", "$currencySymbol${String.format(locale, "%,.0f", dailySpent)}",
                        if (dailySpent > dailyBudget) SemanticRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Projected", "$currencySymbol${String.format(locale, "%,.0f", projectedEnd)}",
                        if (projectedEnd > budget.limitAmount) SemanticRed else Emerald500)
            }

            if (projectedEnd > budget.limitAmount && !isExceeded) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ At this rate you'll exceed by $currencySymbol${String.format(locale, "%,.0f", projectedEnd - budget.limitAmount)} this month",
                    style = MaterialTheme.typography.labelSmall, color = SemanticAmber)
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

@Composable
fun AddBudgetDialog(currencySymbol: String, onDismiss: () -> Unit, onConfirm: (Budget) -> Unit) {
    var category by remember { mutableStateOf("") }
    var limit    by remember { mutableStateOf("") }
    // C05: source from the shared expense list rather than a local hardcode.
    // Set Category Limit is expense-only by design — budgets gate outflows,
    // not inflows — so there's no Income/Expense toggle to bleed across.
    val categories = app.fynlo.data.Categories.EXPENSE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Category Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories.size, key = { it }) { i ->
                        FilterChip(selected = category == categories[i], onClick = { category = categories[i] },
                            label = { Text(categories[i], style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = category, onValueChange = { category = it },
                    label = { Text("Category Name") }, singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = limit, onValueChange = { limit = it },
                    label = { Text("Monthly Limit ($currencySymbol)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(Budget(category, limit.toDoubleOrNull() ?: 0.0)) },
                enabled = category.isNotBlank() && (limit.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
