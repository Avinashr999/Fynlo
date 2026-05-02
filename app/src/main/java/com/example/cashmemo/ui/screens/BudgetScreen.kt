package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Budget
import java.time.LocalDate
import java.util.Locale

@Composable
fun BudgetScreen(viewModel: FinanceViewModel) {
    val budgets  by viewModel.budgets.collectAsState()
    val expenses by viewModel.expenseAnalytics.collectAsState()
    val locale   = remember { Locale.getDefault() }
    var showAddDialog by remember { mutableStateOf(false) }

    // Days remaining in current month
    val today         = LocalDate.now()
    val daysInMonth   = today.lengthOfMonth()
    val daysRemaining = daysInMonth - today.dayOfMonth
    val daysPassed    = today.dayOfMonth

    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { budget ->
                viewModel.addBudget(budget)
                showAddDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Budgeting", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        Text("$daysRemaining days remaining in ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Overall summary
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

                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("This Month's Overview", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                OverviewChip("Total Budget", "₹${String.format(locale, "%,.0f", totalLimit)}",
                                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                OverviewChip("Spent", "₹${String.format(locale, "%,.0f", totalSpent)}",
                                    if (totalSpent > totalLimit) Color(0xFFEF4444) else Color(0xFF10B981), Modifier.weight(1f))
                                OverviewChip("Remaining", "₹${String.format(locale, "%,.0f", totalRemain)}",
                                    if (totalRemain < 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            }
                            if (overBudget > 0 || nearLimit > 0) {
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (overBudget > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEF4444).copy(alpha = 0.1f)) {
                                            Text("⚠ $overBudget exceeded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFEF4444),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    if (nearLimit > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF59E0B).copy(alpha = 0.1f)) {
                                            Text("⚡ $nearLimit near limit",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFF59E0B),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
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
                // Sort: exceeded first, then near limit, then under budget
                val sorted = budgets.sortedByDescending { b ->
                    val pct = (expenses[b.category] ?: 0.0) / b.limitAmount
                    pct
                }
                items(sorted) { budget ->
                    val actualSpent = expenses[budget.category] ?: 0.0
                    BudgetCard(budget, actualSpent, daysRemaining, daysPassed, locale,
                        onDelete = { viewModel.deleteBudget(budget) })
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

@Composable
fun BudgetCard(
    budget: Budget,
    actualSpent: Double,
    daysRemaining: Int,
    daysPassed: Int,
    locale: Locale,
    onDelete: () -> Unit
) {
    val progress     = (actualSpent / budget.limitAmount).toFloat().coerceIn(0f, 1f)
    val pct          = (progress * 100).toInt()
    val remaining    = budget.limitAmount - actualSpent
    val isExceeded   = actualSpent > budget.limitAmount
    val isNearLimit  = pct >= 80 && !isExceeded
    val dailyBudget  = budget.limitAmount / (daysPassed + daysRemaining)
    val dailySpent   = if (daysPassed > 0) actualSpent / daysPassed else 0.0
    val projectedEnd = dailySpent * (daysPassed + daysRemaining)

    val barColor = when {
        isExceeded  -> Color(0xFFEF4444)
        isNearLimit -> Color(0xFFF59E0B)
        pct >= 50   -> Color(0xFF10B981)
        else        -> MaterialTheme.colorScheme.primary
    }

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {

            // Header row
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Alert badge
                    if (isExceeded) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                            Text("EXCEEDED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else if (isNearLimit) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF59E0B).copy(alpha = 0.15f)) {
                            Text("⚡ NEAR LIMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF59E0B), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(budget.category, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Color.Red.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = barColor, trackColor = barColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(6.dp))

            // Spent / Limit
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("₹${String.format(locale, "%,.0f", actualSpent)} spent",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                Text("$pct% of ₹${String.format(locale, "%,.0f", budget.limitAmount)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // Info strip
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), Arrangement.SpaceBetween) {
                    InfoItem("Remaining", if (isExceeded) "-₹${String.format(locale, "%,.0f", -remaining)}"
                        else "₹${String.format(locale, "%,.0f", remaining)}",
                        if (isExceeded) Color(0xFFEF4444) else Color(0xFF10B981))
                    InfoItem("Daily Budget", "₹${String.format(locale, "%,.0f", dailyBudget)}",
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Daily Spent", "₹${String.format(locale, "%,.0f", dailySpent)}",
                        if (dailySpent > dailyBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoItem("Projected", "₹${String.format(locale, "%,.0f", projectedEnd)}",
                        if (projectedEnd > budget.limitAmount) Color(0xFFEF4444) else Color(0xFF10B981))
                }
            }

            // Projected overspend warning
            if (projectedEnd > budget.limitAmount && !isExceeded) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ At this rate you'll exceed by ₹${String.format(locale, "%,.0f", projectedEnd - budget.limitAmount)} this month",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
            }
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
    Surface(modifier, RoundedCornerShape(10.dp), color = color.copy(alpha = 0.1f)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
fun AddBudgetDialog(onDismiss: () -> Unit, onConfirm: (Budget) -> Unit) {
    var category by remember { mutableStateOf("") }
    var limit    by remember { mutableStateOf("") }
    val categories = listOf("Food", "Fuel", "Shopping", "Bills", "Entertainment",
        "Health", "Education", "Transport", "Rent", "Other")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Category Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Category quick-select
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories.size) { i ->
                        FilterChip(selected = category == categories[i], onClick = { category = categories[i] },
                            label = { Text(categories[i], style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = category, onValueChange = { category = it },
                    label = { Text("Category Name") }, singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = limit, onValueChange = { limit = it },
                    label = { Text("Monthly Limit (₹)") }, singleLine = true,
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
