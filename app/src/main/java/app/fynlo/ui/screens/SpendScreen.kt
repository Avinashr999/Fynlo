package app.fynlo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Transaction
import app.fynlo.ui.components.AddTransactionDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.fynlo.ui.theme.*

private val CAT_COLORS = listOf(
    SemanticBlue, Emerald500, SemanticAmber,
    SemanticRed, Carbon500, Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF84CC16)
)

@Composable
fun SpendScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val budgets      by viewModel.budgets.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale       = remember { Locale.getDefault() }
    var showDialog   by remember { mutableStateOf(false) }
    var selectedTab  by remember { mutableIntStateOf(0) }

    // Month navigation
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val monthFmt      = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val keyFmt        = remember { DateTimeFormatter.ofPattern("yyyy-MM") }
    val monthKey      = selectedMonth.format(keyFmt)
    val isCurrentMonth = selectedMonth == YearMonth.now()

    val allExpenses = remember(transactions) {
        transactions.filter { it.type.equals("expense", ignoreCase = true) }
    }
    val expenses = remember(allExpenses, monthKey) {
        allExpenses.filter { it.date.startsWith(monthKey) }
    }
    val total    = remember(expenses) { expenses.sumOf { it.amount } }
    val byCat    = remember(expenses) {
        expenses.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    if (showDialog) {
        AddTransactionDialog(
            onDismiss = { showDialog = false },
            onConfirm = { txn -> viewModel.addTransaction(txn); showDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Expenses", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
            FilledTonalButton(onClick = { showDialog = true }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }

        // Tab row: Expenses | History
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Expenses", Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("All Transactions", Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            // Month selector
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(selectedMonth.format(monthFmt),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                IconButton(onClick = { if (!isCurrentMonth) selectedMonth = selectedMonth.plusMonths(1) },
                    enabled = !isCurrentMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                        tint = if (!isCurrentMonth) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Total card
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
                    CardDefaults.cardColors(SemanticRed.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(20.dp)) {
                        Text(selectedMonth.format(monthFmt), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol ${String.format(locale, "%,.0f", total)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = SemanticRed)
                        Text("${expenses.size} transactions", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Category breakdown with FIXED budget %
                if (byCat.isNotEmpty()) {
                    Text("Category Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            byCat.forEachIndexed { i, (cat, amt) ->
                                val budget     = budgets.find { it.category.equals(cat, ignoreCase = true) }
                                // Fix: use budget limit as max, not total spending
                                val barMax     = budget?.limitAmount?.coerceAtLeast(amt) ?: amt
                                val frac       = if (barMax > 0) (amt / barMax).toFloat().coerceIn(0f, 1f) else 0f
                                val anim       by animateFloatAsState(frac, tween(600), label = "bar")
                                val color      = CAT_COLORS[i % CAT_COLORS.size]
                                val budgetPct  = budget?.let { ((amt / it.limitAmount) * 100).toInt() }

                                Column {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                                            Text(cat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("$currencySymbol ${String.format(locale, "%,.0f", amt)}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = color)
                                            if (budgetPct != null) {
                                                val pctColor = when {
                                                    budgetPct >= 100 -> SemanticRed
                                                    budgetPct >= 80  -> SemanticAmber
                                                    else             -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                                Surface(color = pctColor.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(4.dp)) {
                                                    Text("$budgetPct% of $currencySymbol${String.format(locale, "%,.0f", budget!!.limitAmount)}",
                                                        style = MaterialTheme.typography.labelSmall, color = pctColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                        .background(color.copy(alpha = 0.15f))) {
                                        Box(Modifier.fillMaxWidth(anim).fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp)).background(
                                                if (budgetPct != null && budgetPct >= 100) SemanticRed else color))
                                    }
                                    // Budget limit line label
                                    if (budget != null) {
                                        Text("Budget: $currencySymbol${String.format(locale, "%,.0f", budget.limitAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Recent expenses
                if (expenses.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(8.dp))
                    expenses.sortedByDescending { it.date }.take(15).forEach { txn ->
                        ExpenseRow(txn, currencySymbol, locale)
                        HorizontalDivider(Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Receipt, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant)
                            Text("No expenses in ${selectedMonth.format(monthFmt)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isCurrentMonth) {
                                FilledTonalButton(onClick = { showDialog = true }) { Text("Add First Expense") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        } else {
            // All Transactions tab — reuse TransactionHistoryScreen logic inline
            TransactionHistoryScreen(viewModel = viewModel)
        }
    }
}

@Composable
private fun ExpenseRow(txn: Transaction, currencySymbol: String, locale: Locale) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(40.dp), RoundedCornerShape(12.dp),
            color = SemanticRed.copy(alpha = 0.1f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = SemanticRed, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(txn.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(txn.desc.ifBlank { txn.fromAcct }.ifBlank { txn.date },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("-$currencySymbol ${String.format(locale, "%,.0f", txn.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = SemanticRed)
            Text(txn.date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}









