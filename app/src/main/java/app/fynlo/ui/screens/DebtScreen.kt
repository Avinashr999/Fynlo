package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddDebtDialog
import app.fynlo.ui.components.PayDebtDialog
import java.util.Locale
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(viewModel: FinanceViewModel) {
        val haptic = LocalHapticFeedback.current
val debts by viewModel.debts.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    var searchQuery by remember { mutableStateOf("") }
    val filteredDebts = remember(debts, searchQuery) {
        if (searchQuery.isBlank()) debts
        else debts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true)
        }
    }
    var editingDebt   by remember { mutableStateOf<Debt?>(null) }
    var payingDebt    by remember { mutableStateOf<Debt?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog || editingDebt != null) {
        AddDebtDialog(
            viewModel = viewModel,
            onDismiss = { editingDebt = null; showAddDialog = false },
            onConfirm = { debt, dest ->
                if (editingDebt?.id?.isNotBlank() == true) {
                    viewModel.updateDebt(debt)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addDebtWithDestination(debt, dest)
                }
                editingDebt = null
                showAddDialog = false
            },
            initialDebt = editingDebt
        )
    }

    if (payingDebt != null) {
        PayDebtDialog(
            debt = payingDebt!!,
            accounts = accounts,
            onDismiss = { payingDebt = null },
            onConfirm = { payment: DebtPayment, source: String ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.payDebt(payment, source)
                payingDebt = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("My Debts", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 0.dp))
            FilledTonalButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Debt", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Total debt summary card ─────────────────────────────────────────
        if (debts.isNotEmpty()) {
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val totalPrincipal = debts.sumOf { maxOf(0.0, it.amount - it.paid) }
            val overdueCount   = debts.count { it.due.isNotBlank() && it.due < today && it.paid < it.amount }
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(SemanticRed.copy(alpha = 0.08f))) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Total Outstanding", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", totalPrincipal)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = SemanticRed)
                        Text("${debts.size} debt${if (debts.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (overdueCount > 0) {
                        Surface(color = SemanticRed.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text("$overdueCount OVERDUE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = SemanticRed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }

        val suggestions = remember(searchQuery, debts) {
            if (searchQuery.length < 1) emptyList()
            else debts.filter { it.name.contains(searchQuery, ignoreCase = true) }.map { it.name }.distinct().take(5)
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded && suggestions.isNotEmpty(),
            onExpandedChange = {},
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it; expanded = true },
                label         = { Text("Search debts...") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = ""; expanded = false }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                shape         = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
                suggestions.forEach { name ->
                    DropdownMenuItem(
                        text    = { Text(name) },
                        onClick = { searchQuery = name; expanded = false }
                    )
                }
            }
        }

        if (filteredDebts.isEmpty()) {
            EmptyDebtState(onAdd = { showAddDialog = true })
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(filteredDebts) { debt ->
                    DebtCard(
                        debt = debt,
                        currencySymbol = currencySymbol,
                        onEdit = { editingDebt = debt },
                        onDelete = { viewModel.deleteDebt(debt) },
                        onPay = { payingDebt = debt }
                    )
                }
            }
        }
    }
}

@Composable
fun DebtCard(debt: Debt, currencySymbol: String = "₹", onEdit: () -> Unit, onDelete: () -> Unit, onPay: () -> Unit) {
    val locale = Locale.getDefault()
    val today  = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val interestAccrued = InterestEngine.calcIntAccrued(
        amount    = debt.amount,
        rate      = debt.rate,
        loanDate  = debt.date,
        intType   = debt.intType,
        dueDate   = debt.due,
        totalPaid = debt.paid
    )
    val daysElapsed    = InterestEngine.daysBetween(debt.date, today)
    val perDayInterest = if (daysElapsed > 0) interestAccrued / daysElapsed else 0.0
    val bothPortions   = if (debt.intType == "Both") InterestEngine.calcBothPortions(
        debt.amount, debt.rate, debt.date, debt.due, debt.paid
    ) else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CreditCard, 
                        contentDescription = null, 
                        tint = SemanticRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        debt.name, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Badge(
                        containerColor = if (debt.status == "Active") Emerald500.copy(alpha = 0.12f) else SemanticRed.copy(alpha = 0.1f),
                        contentColor   = if (debt.status == "Active") Emerald500 else SemanticRed
                    ) {
                        Text(debt.status, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                    }
                    IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Edit", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = SemanticRed.copy(alpha = 0.7f))
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Borrowed Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", debt.amount)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Borrowed: ${DateUtils.formatToDisplay(debt.date)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    if (debt.due.isNotBlank()) {
                        val isOverdue = debt.due < java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        Text("Due: ${DateUtils.formatToDisplay(debt.due)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isOverdue) SemanticRed else Color.Gray)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Interest (${debt.rate}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(locale, "%,.0f", interestAccrued)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = SemanticRed))
                    Text("Type: ${debt.intType}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // For "Both" type show SI + CI split
            if (bothPortions != null) {
                Spacer(Modifier.height(6.dp))
                Surface(color = Color(0xFFFFEBEE).copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ SI (until due date)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol ${String.format(locale, "%,.0f", bothPortions.first)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ CI (after due date)", style = MaterialTheme.typography.labelSmall, color = SemanticRed)
                            Text("$currencySymbol ${String.format(locale, "%,.0f", bothPortions.second)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SemanticRed)
                        }
                    }
                }
            }

            // Days elapsed strip
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFFFFEBEE).copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Days Elapsed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$daysElapsed days", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Per Day Interest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol ${String.format(locale, "%,.2f", perDayInterest)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = SemanticRed)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paid So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol ${String.format(locale, "%,.0f", debt.paid)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Pay button — full width at bottom
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pay Instalment", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }

            if (debt.notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(debt.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}



@Composable
fun EmptyDebtState(onAdd: () -> Unit = {}) {
    app.fynlo.ui.components.EmptyStateIllustration(
        type        = app.fynlo.ui.components.EmptyStateType.DEBTS,
        onAction    = onAdd,
        actionLabel = "Add First Debt"
    )
}








