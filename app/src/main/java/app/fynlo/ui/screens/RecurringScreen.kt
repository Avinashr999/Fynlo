package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.Categories
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
            onConfirm = { r ->
                // C04 Stage 3: record recency BEFORE handing the txn over to
                // the VM so the next dialog open prefills with this category.
                // Records the FINAL resolved category (custom string when
                // "Custom" was picked, else the chip label).
                viewModel.recordRecurringCategory(r.type == "Income", r.category)
                viewModel.addRecurringTransaction(r)
                showAddDialog = false
            },
            rememberLastCategory = { isIncome -> viewModel.rememberLastRecurringCategory(isIncome) },
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
            ) {
                if (dueCount > 0) {
                    item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SemanticAmber.copy(alpha = 0.08f))
                                    .padding(14.dp),
                                Arrangement.SpaceBetween, Alignment.CenterVertically
                            ) {
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
                itemsIndexed(recurringList, key = { _, it -> it.id }) { index, r ->
                    val nd = nextDue(r)
                    val isDue = !today.isBefore(nd)
                    val daysUntil = ChronoUnit.DAYS.between(today, nd)
                    RecurringCard(r, isDue = isDue, daysUntil = daysUntil,
                        onDelete = { viewModel.deleteRecurringTransaction(r) })
                    if (index < recurringList.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
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
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRecurringDialog(
    onDismiss: () -> Unit,
    onConfirm: (RecurringTransaction) -> Unit,
    // C04 Stage 3: optional recency hook. Default no-op keeps the dialog
    // testable / previewable in isolation; the production call site wires
    // `viewModel::rememberLastRecurringCategory`. Boolean arg mirrors the
    // AddTransactionDialog contract so the tracker scopes recency by type.
    rememberLastCategory: suspend (Boolean) -> String? = { null },
) {
    var name       by remember { mutableStateOf("") }
    var type       by remember { mutableStateOf("Expense") }
    var amount     by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var fromAcct   by remember { mutableStateOf("") }
    var frequency  by remember { mutableStateOf("Monthly") }
    var dayOfMonth by remember { mutableStateOf("1") }

    // C04 Stage 3: chip-picker category list, driven by the Income/Expense
    // toggle. Mirrors AddTransactionDialog: curated list from `Categories`
    // plus a trailing "Custom" sentinel for user-supplied values. Recomputed
    // via `remember(type)` so toggling Income/Expense swaps the chip set
    // immediately.
    val isIncome = type == "Income"
    val categories = remember(isIncome) {
        (if (isIncome) Categories.INCOME else Categories.EXPENSE) + "Custom"
    }
    var selectedCategory by remember { mutableStateOf("") }

    // C04 Stage 3: prefill the chip from recency on initial open and on every
    // Income/Expense toggle flip. Three-case `when` mirrors Stage 2.5 logic
    // from AddTransactionDialog:
    //   1. null (no recency yet) → clear both fields, let the user pick fresh.
    //   2. recent value is in the curated chip list → pre-select chip, clear
    //      any lingering custom string from a prior toggle flip.
    //   3. recent value is a user-typed Custom string → select "Custom" AND
    //      restore the typed value into `customCategory` so it re-renders in
    //      the text input below the chip row.
    LaunchedEffect(isIncome) {
        val recent = rememberLastCategory(isIncome)
        when {
            recent == null -> {
                selectedCategory = ""
                customCategory = ""
            }
            recent in categories -> {
                selectedCategory = recent
                customCategory = ""
            }
            else -> {
                selectedCategory = "Custom"
                customCategory = recent
            }
        }
    }

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

                // C04 Stage 3: replaced free-text "Category" OutlinedTextField
                // with a chip-picker. "Custom" sentinel reveals the freeform
                // input only when the user opts in, matching the AddTransaction
                // dialog UX and keeping curated categories the default path.
                Text("Category", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                if (selectedCategory == "Custom") {
                    OutlinedTextField(value = customCategory, onValueChange = { customCategory = it },
                        label = { Text("Custom category name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }

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
                        // C04 Stage 3: resolve the final category — chip label
                        // for curated picks, the user-typed string for Custom.
                        // The outer call site uses this final value for both
                        // the RecurringTransaction insert AND the recency
                        // record so e.g. "Charity" (not "Custom") is what
                        // re-prefills next time.
                        val finalCategory =
                            if (selectedCategory == "Custom") customCategory else selectedCategory
                        onConfirm(RecurringTransaction(
                            name       = name,
                            type       = type,
                            amount     = amount.toDoubleOrNull() ?: 0.0,
                            category   = finalCategory,
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








