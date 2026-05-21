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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


@Composable
fun RecurringScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val recurringList by viewModel.recurringTransactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val fmt   = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    if (showAddDialog) {
        AddRecurringDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { r -> viewModel.addRecurringTransaction(r); showAddDialog = false }
        )
    }

    fun nextDue(r: RecurringTransaction): LocalDate {
        val last = if (r.lastRun.isBlank()) null else runCatching { LocalDate.parse(r.lastRun, fmt) }.getOrNull()
        return when {
            last == null          -> today
            r.frequency == "Daily"   -> last.plusDays(1)
            r.frequency == "Weekly"  -> last.plusWeeks(1)
            r.frequency == "Monthly" -> last.plusMonths(1)
            r.frequency == "Yearly"  -> last.plusYears(1)
            else -> last.plusMonths(1)
        }
    }

    val dueCount = recurringList.count { rec -> rec.isActive && !today.isBefore(nextDue(rec)) }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "Recurring",
            subtitle = "Auto-log on schedule",
            action = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dueCount > 0) {
                        Surface(shape = RoundedCornerShape(20.dp), color = SemanticAmber) {
                            Text("$dueCount due",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White)
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add", tint = Color.White)
                    }
                }
            }
        )
        Box(modifier = Modifier.weight(1f)) {
        if (recurringList.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Repeat, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("No recurring transactions", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add salary, rent, EMIs to auto-log", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant)
                    FilledTonalButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add First")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
            ) {
                if (dueCount > 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SemanticAmber.copy(alpha = 0.08f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SemanticAmber.copy(alpha = 0.3f))
                        ) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.NotificationImportant, null, Modifier.size(20.dp), tint = SemanticAmber)
                                    Column {
                                        Text("$dueCount due today",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = SemanticAmber)
                                        Text("Will auto-log on next app open",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.triggerDueRecurring() },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Run Now", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                            }
                        }
                    }
                }
                items(recurringList, key = { it.id }) { r ->
                    val nd = nextDue(r)
                    val isDue = !today.isBefore(nd)
                    val daysUntil = ChronoUnit.DAYS.between(today, nd)
                    RecurringCard(r, isDue = isDue, daysUntil = daysUntil,
                        onDelete = { viewModel.deleteRecurringTransaction(r) })
                }
            }
        }
        }
    }
}

@Composable
private fun RecurringCard(r: RecurringTransaction, isDue: Boolean = false, daysUntil: Long = 0, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete recurring entry?") },
            text  = { Text("Stop auto-logging \"${r.name}\"? Past transactions it created are kept. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
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
                    val dueLabel = when {
                        isDue -> "Due today"
                        daysUntil == 1L -> "Due tomorrow"
                        else -> "In $daysUntil days"
                    }
                    Text(dueLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDue) SemanticRed else Emerald500)
                }
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showDeleteConfirm = true }) {
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








