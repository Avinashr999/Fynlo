package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
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
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // C04 Stage 3: record recency BEFORE handing the txn over to
                // the VM so the next dialog open prefills with this category.
                // Records the FINAL resolved category (custom string when
                // "Custom" was picked, else the chip label).
                viewModel.recordRecurringCategory(r.type == "Income", r.category)
                viewModel.addRecurringTransaction(r)
                viewModel.showFeedback("Recurring entry added")
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
        // C07 fix (UX_AUDIT §C07): the header action row (due-count badge + `+`
        // IconButton) only renders when the list has data. On empty state the
        // shared `EmptyState` composable below is the single unambiguous CTA —
        // hiding the header `+` here prevents the pre-3.2.12 triple-entry-point
        // (header `+` + inline "Add First" + Scaffold FAB) on the empty screen.
        PremiumScreenHeader(
            title = "Recurring",
            subtitle = "Auto-log on schedule",
            action = if (recurringList.isNotEmpty()) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (dueCount > 0) {
                            Surface(shape = RoundedCornerShape(20.dp), color = SemanticAmber) {
                                Text("$dueCount due",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White)
                            }
                        }
                        // 3.2.7 fix: was `IconButton + tint = Color.White` against
                        // the plain surface background of `PremiumScreenHeader` —
                        // invisible in light mode (smoke-test finding on 3.2.6).
                        // `FilledTonalIconButton` paints a theme-aware secondary
                        // container behind a properly-tinted icon, so it stays
                        // legible in both light and dark themes without needing a
                        // hardcoded colour.
                        FilledTonalIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Add, "Add recurring transaction")
                        }
                    }
                }
            } else null,
        )
        Box(modifier = Modifier.weight(1f)) {
        if (recurringList.isEmpty()) {
            // C07 fix: shared EmptyState replaces the bespoke empty layout.
            // Header `+` is hidden (see PremiumScreenHeader action above), so
            // this is the single unambiguous CTA.
            EmptyState(
                icon = Icons.Default.Repeat,
                title = "No recurring transactions",
                subtitle = "Add salary, rent, EMIs to auto-log",
                actionLabel = "Add First Recurring",
                onAction = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = FabBottomPadding)
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
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.triggerDueRecurring()
                                        viewModel.showFeedback("Due recurring entries processed")
                                    },
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
                        onDelete = {
                            viewModel.deleteRecurringTransaction(r)
                            viewModel.showFeedback("Recurring entry deleted")
                        })
                    if (index < recurringList.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
            // C22 Stage 2 smoke-surface fix (3.2.48) — add proper FAB to the
            // populated state, matching LendingScreen / DebtScreen / GoalScreen
            // / SpendScreen / PeopleScreen convention. Pre-3.2.48 the only
            // populated-state entry point was the small `+` IconButton in
            // the header — easy to miss. Empty state still uses the inline
            // EmptyState CTA (no FAB) so the audit C07 "no triple entry
            // point" rule still holds.
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(54.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = Emerald500,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Recurring")
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
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showDeleteConfirm = false
                },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
        Row(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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
                // 3.2.65 — was Color.Red; theme-aware error token.
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    // C22 dialog universalization (3.2.55) — migrated to canonical FormDialog.
    // This is the most complex form (10 fields across conditional branches),
    // so the bold section labels are especially important for scannability.
    app.fynlo.ui.components.FormDialog(
        title = "Add Recurring Transaction",
        onDismiss = onDismiss,
    ) {
        app.fynlo.ui.components.FormSectionLabel("Name")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text("e.g. Monthly Rent") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Type")
        Spacer(Modifier.height(6.dp))
        val typeOptions = listOf("Income", "Expense")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            typeOptions.forEachIndexed { idx, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t },
                    shape = SegmentedButtonDefaults.itemShape(idx, typeOptions.size),
                    icon = {},
                    label = { Text(t) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Amount")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            placeholder = { Text("Leave blank to enter each time") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Category")
        Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customCategory, onValueChange = { customCategory = it },
                placeholder = { Text("Custom category name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Account")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = fromAcct, onValueChange = { fromAcct = it },
            placeholder = { Text("e.g. HDFC Bank") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Frequency")
        Spacer(Modifier.height(6.dp))
        val freqOptions = listOf("Daily", "Weekly", "Monthly", "Yearly")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            freqOptions.forEachIndexed { idx, option ->
                SegmentedButton(
                    selected = frequency == option,
                    onClick = { frequency = option },
                    shape = SegmentedButtonDefaults.itemShape(idx, freqOptions.size),
                    icon = {},
                    label = {
                        Text(
                            option,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                )
            }
        }

        if (frequency == "Monthly" || frequency == "Yearly") {
            // C22 Stage 2 (3.2.47) — "last day of month" support (audit
            // §C22 #218). When checked, dayOfMonth is forced to 31 (sentinel
            // — RecurringWorker clamps it to today.lengthOfMonth()).
            val isLastDay = dayOfMonth.toIntOrNull() == 31
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isLastDay,
                    onCheckedChange = { checked ->
                        dayOfMonth = if (checked) "31" else "1"
                    }
                )
                Text(
                    "Use last day of month",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = dayOfMonth,
                onValueChange = { newVal ->
                    val n = newVal.toIntOrNull()
                    if (newVal.isEmpty() || (n != null && n in 1..31)) {
                        dayOfMonth = newVal
                    }
                },
                placeholder = { Text("Day of month (1-31)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLastDay,
            )
        }

        // C22 Stage 2 (3.2.47) — preview next 3 occurrences (audit §C22 #220).
        val previewDates = remember(frequency, dayOfMonth) {
            val today  = java.time.LocalDate.now()
            val target = dayOfMonth.toIntOrNull() ?: 1
            val out    = mutableListOf<java.time.LocalDate>()
            when (frequency) {
                "Daily" -> for (i in 1..3) out += today.plusDays(i.toLong())
                "Weekly" -> {
                    var d = today.plusDays(1)
                    while (out.size < 3) {
                        if (d.dayOfWeek.value == 1) out += d
                        d = d.plusDays(1)
                    }
                }
                "Monthly" -> {
                    var ym = java.time.YearMonth.from(today)
                    while (out.size < 3) {
                        val day = minOf(target, ym.lengthOfMonth())
                        val date = ym.atDay(day)
                        if (date.isAfter(today)) out += date
                        ym = ym.plusMonths(1)
                    }
                }
                "Yearly" -> {
                    var y = today.year
                    while (out.size < 3) {
                        val ym = java.time.YearMonth.of(y, 1)
                        val day = minOf(target, ym.lengthOfMonth())
                        val date = ym.atDay(day)
                        if (date.isAfter(today)) out += date
                        y += 1
                    }
                }
            }
            out
        }
        if (previewDates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
            Text(
                "Next occurrences: " + previewDates.joinToString(" · ") { it.format(fmt) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) {
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
                        dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    ))
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500),
        ) {
            Text("Add Recurring", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(
            if (name.isBlank()) "Enter a name to continue" else null
        )
    }
}



