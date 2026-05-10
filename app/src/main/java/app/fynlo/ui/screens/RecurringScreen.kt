package app.fynlo.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.ui.theme.*

@Composable
fun RecurringScreen(viewModel: FinanceViewModel) {
    val recurringList by viewModel.recurringTransactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddRecurringDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { r ->
                viewModel.addRecurringTransaction(r)
                showAddDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Recurring", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
                Text("Auto-log on schedule", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }

        if (recurringList.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Repeat, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("No recurring transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add salary, rent, EMIs to auto-log them", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)) {
                items(recurringList) { r ->
                    RecurringCard(r, onDelete = { viewModel.deleteRecurringTransaction(r) })
                }
            }
        }
    }
}

@Composable
private fun RecurringCard(r: RecurringTransaction, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(44.dp), RoundedCornerShape(12.dp),
                    color = if (r.type == "Income") Emerald500.copy(0.1f) else SemanticRed.copy(0.1f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (r.type == "Income") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            null, tint = if (r.type == "Income") Emerald500 else SemanticRed,
                            modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("${r.frequency} " + if (r.amount > 0) "• Rs${r.amount.toLong()}" else "• Amount on run",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.category.ifBlank { r.type }, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AddRecurringDialog(onDismiss: () -> Unit, onConfirm: (RecurringTransaction) -> Unit) {
    var name       by remember { mutableStateOf("") }
    var type       by remember { mutableStateOf("Expense") }
    var amount     by remember { mutableStateOf("") }
    var category   by remember { mutableStateOf("") }
    var fromAcct   by remember { mutableStateOf("") }
    var frequency  by remember { mutableStateOf("Monthly") }
    var dayOfMonth by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. Monthly Rent)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Income", "Expense").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(t) }, modifier = Modifier.weight(1f))
                    }
                }

                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Amount (leave blank to enter each time)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                OutlinedTextField(value = category, onValueChange = { category = it },
                    label = { Text("Category") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                OutlinedTextField(value = fromAcct, onValueChange = { fromAcct = it },
                    label = { Text("Account (e.g. HDFC Bank)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Daily","Weekly","Monthly","Yearly").forEach { f ->
                        FilterChip(selected = frequency == f, onClick = { frequency = f },
                            label = { Text(f, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f))
                    }
                }

                if (frequency == "Monthly" || frequency == "Yearly") {
                    OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it },
                        label = { Text("Day of month (1-28)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(RecurringTransaction(
                            name       = name,
                            type       = type,
                            amount     = amount.toDoubleOrNull() ?: 0.0,
                            category   = category,
                            fromAcct   = if (type == "Expense") fromAcct else "",
                            toAcct     = if (type == "Income") fromAcct else "",
                            frequency  = frequency,
                            dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 28) ?: 1
                        ))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}








