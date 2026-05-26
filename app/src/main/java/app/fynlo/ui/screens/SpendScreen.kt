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
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.components.AddTransactionDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


private val CAT_COLORS = ChartColors

@Composable
fun SpendScreen(viewModel: FinanceViewModel) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Expenses") }
        val haptic = LocalHapticFeedback.current
val transactions by viewModel.transactions.collectAsState()
    val budgets      by viewModel.budgets.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale       = remember { Locale.getDefault() }
    var showDialog   by remember { mutableStateOf(false) }

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
            onConfirm = { txn -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addTransaction(txn); showDialog = false },
            rememberLastCategory = { isIncome -> viewModel.rememberLastTransactionCategory(isIncome) },
            onRecordCategory = { isIncome, cat -> viewModel.recordTransactionCategory(isIncome, cat) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Expenses", "Track where your money goes")
        Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            FilledTonalButton(onClick = { showDialog = true }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(4.dp))

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

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Total — flat hero
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Spent in ${selectedMonth.format(monthFmt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.detail(total, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = SemanticRed)
                    Text("${expenses.size} transactions", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(24.dp))

                // Category breakdown with FIXED budget %
                if (byCat.isNotEmpty()) {
                    Text("Category Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                            Text(CurrencyFormatter.detail(amt, currencyCode, locale),
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
                                                    Text("$budgetPct% of ${CurrencyFormatter.detail(budget!!.limitAmount, currencyCode, locale)}",
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
                                        Text("Budget: ${CurrencyFormatter.detail(budget.limitAmount, currencyCode, locale)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
                        ExpenseRow(txn, currencyCode, locale,
                            onDelete = { viewModel.deleteTransaction(txn) },
                            onEdit   = { viewModel.editTransaction(txn, it) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                } else {
                    app.fynlo.ui.components.EmptyStateIllustration(
                        type        = app.fynlo.ui.components.EmptyStateType.SPENDING,
                        onAction    = if (isCurrentMonth) { { showDialog = true } } else null,
                        actionLabel = "Add First Expense"
                    )
                }
                Spacer(Modifier.height(FabBottomPadding))
            }
    }
    }
}

@Composable
private fun ExpenseRow(
    txn: Transaction,
    currencyCode: String,
    locale: Locale,
    onDelete: () -> Unit = {},
    onEdit: (app.fynlo.data.model.Transaction) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }
    var menuOpen          by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Expense?") },
            text  = { Text("Delete ${CurrencyFormatter.detail(txn.amount, currencyCode, locale)} ${txn.category}? This will reverse the account balance.") },
            confirmButton = {
                Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showEditDialog) {
        app.fynlo.ui.components.EditTransactionDialog(
            transaction = txn,
            onDismiss   = { showEditDialog = false },
            onConfirm   = { updated -> onEdit(updated); showEditDialog = false }
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SemanticRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(getCategoryIcon(txn.category), null, tint = SemanticRed, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(txn.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(txn.desc.ifBlank { txn.fromAcct }.ifBlank { txn.date },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(CurrencyFormatter.negative(txn.amount, currencyCode, locale),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = SemanticRed)
            Text(txn.date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "More", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; showEditDialog = true })
                DropdownMenuItem(text = { Text("Delete", color = SemanticRed) }, onClick = { menuOpen = false; showDeleteConfirm = true })
            }
        }
    }
}









