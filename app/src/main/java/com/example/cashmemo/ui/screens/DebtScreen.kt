package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Debt
import com.example.cashmemo.data.model.DebtPayment
import com.example.cashmemo.logic.DateUtils
import com.example.cashmemo.logic.InterestEngine
import com.example.cashmemo.ui.components.AddDebtDialog
import com.example.cashmemo.ui.components.PayDebtDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(viewModel: FinanceViewModel) {
    val debts by viewModel.debts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filteredDebts = remember(debts, searchQuery) {
        if (searchQuery.isBlank()) debts
        else debts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true)
        }
    }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var payingDebt by remember { mutableStateOf<Debt?>(null) }

    if (editingDebt != null) {
        AddDebtDialog(
            viewModel = viewModel,
            onDismiss = { editingDebt = null },
            onConfirm = { debt, dest ->
                viewModel.addDebtWithDestination(debt, dest)
                editingDebt = null
            },
            initialDebt = editingDebt
        )
    }

    if (payingDebt != null) {
        PayDebtDialog(
            debt = payingDebt!!,
            onDismiss = { payingDebt = null },
            onConfirm = { payment: DebtPayment, source: String ->
                viewModel.payDebt(payment, source)
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
        Text(
            "My Debts & Loans", 
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 16.dp)
        )

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
            EmptyDebtState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredDebts) { debt ->
                    DebtCard(
                        debt = debt,
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
fun DebtCard(debt: Debt, onEdit: () -> Unit, onDelete: () -> Unit, onPay: () -> Unit) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        debt.name, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.6f))
                    }
                    Button(
                        onClick = onPay,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Pay", style = MaterialTheme.typography.labelSmall)
                    }
                    Badge(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFD32F2F)) {
                        Text(debt.status, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Borrowed Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹ ${String.format(Locale.getDefault(), "%,.0f", debt.amount)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Date: ${DateUtils.formatToDisplay(debt.date)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Interest (${debt.rate}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹ ${String.format(locale, "%,.0f", interestAccrued)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)))
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
                            Text("₹ ${String.format(locale, "%,.0f", bothPortions.first)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ CI (after due date)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                            Text("₹ ${String.format(locale, "%,.0f", bothPortions.second)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFD32F2F))
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
                        Text("₹ ${String.format(locale, "%,.2f", perDayInterest)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFD32F2F))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paid So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹ ${String.format(locale, "%,.0f", debt.paid)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (debt.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
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
fun EmptyDebtState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text("No active debts", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
    }
}