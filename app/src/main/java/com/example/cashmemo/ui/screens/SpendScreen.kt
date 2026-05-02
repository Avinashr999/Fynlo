package com.example.cashmemo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Transaction
import com.example.cashmemo.ui.components.AddTransactionDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CAT_COLORS = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF84CC16)
)

@Composable
fun SpendScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val budgets      by viewModel.budgets.collectAsState()
    val locale       = remember { Locale.getDefault() }
    var showDialog   by remember { mutableStateOf(false) }

    val thisMonth = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) }
    val expenses  = transactions.filter { it.type.equals("expense", ignoreCase = true) && it.date.startsWith(thisMonth) }
    val total     = expenses.sumOf { it.amount }
    val byCat     = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }

    if (showDialog) {
        AddTransactionDialog(
            onDismiss = { showDialog = false },
            onConfirm = { txn -> viewModel.addTransaction(txn); showDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Expenses", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
            FilledTonalButton(onClick = { showDialog = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }

        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
            CardDefaults.cardColors(Color(0xFFEF4444).copy(alpha = 0.1f))) {
            Column(Modifier.padding(20.dp)) {
                Text("This Month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u20B9 ${String.format(locale, "%,.0f", total)}",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFFEF4444))
                Text("${expenses.size} transactions", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Quick Add", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("100", "200", "500", "1000").forEach { amt ->
                OutlinedButton(onClick = { showDialog = true }, Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("\u20B9$amt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (byCat.isNotEmpty()) {
            Text("Category Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    byCat.forEachIndexed { i, (cat, amt) ->
                        val frac  = if (total > 0) (amt / total).toFloat() else 0f
                        val anim  by animateFloatAsState(frac, tween(600), label = "bar")
                        val color = CAT_COLORS[i % CAT_COLORS.size]
                        val budget = budgets.find { it.category.equals(cat, ignoreCase = true) }
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                                    Text(cat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("\u20B9 ${String.format(locale, "%,.0f", amt)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
                                    if (budget != null) {
                                        val pct = (amt / budget.limitAmount * 100).toInt()
                                        Text("$pct% of budget", style = MaterialTheme.typography.labelSmall,
                                            color = if (pct >= 100) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.15f))) {
                                Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (expenses.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("This month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            expenses.sortedByDescending { it.date }.take(10).forEach { txn ->
                ExpenseRow(txn, locale)
                HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Receipt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("No expenses this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap Add to log your first expense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ExpenseRow(txn: Transaction, locale: Locale) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(40.dp), RoundedCornerShape(12.dp), color = Color(0xFFEF4444).copy(alpha = 0.1f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(txn.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(txn.desc.ifBlank { txn.fromAcct }.ifBlank { txn.date },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("-\u20B9 ${String.format(locale, "%,.0f", txn.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFEF4444))
            Text(txn.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
