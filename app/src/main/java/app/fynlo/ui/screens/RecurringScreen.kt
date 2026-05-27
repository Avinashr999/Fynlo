package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                        FilledTonalIconButton(onClick = { showAddDialog = true }) {
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
                onAction = { showAddDialog = true },
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
            // C22 Stage 2 smoke-surface fix (3.2.48) — add proper FAB to the
            // populated state, matching LendingScreen / DebtScreen / GoalScreen
            // / SpendScreen / PeopleScreen convention. Pre-3.2.48 the only
            // populated-state entry point was the small `+` IconButton in
            // the header — easy to miss. Empty state still uses the inline
            // EmptyState CTA (no FAB) so the audit C07 "no triple entry
            // point" rule still holds.
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring Transaction") },
        text = {
            // C22 Stage 2 smoke fix #3 (3.2.50) — wrap the Column in
            // verticalScroll so the form fits inside the AlertDialog text
            // slot regardless of total content height. Pre-3.2.50 the form
            // had ~10 fields totalling ~700dp but the dialog text slot is
            // only ~400dp tall — bottom items (Frequency segments + Use-
            // last-day + day input + preview) were clipped invisible.
            // AlertDialog doesn't auto-scroll its text slot in this
            // Material 3 + Compose version.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. Monthly Rent)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                // 3.2.11 chip-sweep: 2-option mutually-exclusive toggle → SegmentedButtonRow
                // (matches the Income/Expense toggle at the top of AddTransactionDialog).
                // `icon = {}` per the 3.2.8 lesson — checkmark eats label width
                // unnecessarily when selection is already carried by the filled background.
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

                // C22 Stage 2 smoke fix #2 (3.2.49) — Frequency picker
                // reverted from ExposedDropdownMenuBox to SegmentedButtonRow.
                // The 3.2.10 history said the dropdown was chosen because
                // 4 labels didn't fit in a SegmentedButtonRow, but smoke on
                // 3.2.47 + 3.2.48 showed the dropdown is failing harder:
                // it renders collapsed inside the AlertDialog (label /
                // value / supporting-text all clipped, only the chevron
                // visible). The user couldn't see it was a picker at all.
                //
                // SegmentedButtonRow with full 4 labels fits with the
                // dialog content fillMaxWidth + small fontSize on the
                // labels; if "Yearly" still clips on a 320dp-wide device,
                // it'd at most truncate by 1 char ("Yearl…") which is
                // still legibly a Yearly option. Strictly better than the
                // chevron-only render the dropdown produced.
                Text(
                    "Frequency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    // C22 Stage 2 (3.2.47) — "last day of month" support
                    // (audit §C22 #218). When checked, dayOfMonth is forced
                    // to 31 (sentinel — RecurringWorker clamps it to
                    // today.lengthOfMonth(), so Feb fires the 28th, Apr the
                    // 30th, etc.). When unchecked, the user types a number
                    // 1-31 (was 1-28 pre-C22 due to lack of clamp in worker).
                    val isLastDay = dayOfMonth.toIntOrNull() == 31
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
                    OutlinedTextField(
                        value = dayOfMonth,
                        onValueChange = { newVal ->
                            // Only allow valid 1-31 input; drop anything else.
                            val n = newVal.toIntOrNull()
                            if (newVal.isEmpty() || (n != null && n in 1..31)) {
                                dayOfMonth = newVal
                            }
                        },
                        label = { Text("Day of month (1-31)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLastDay,
                    )
                }

                // C22 Stage 2 (3.2.47) — preview next 3 occurrences (audit
                // §C22 #220). Pure-UI calc; no schema impact. Helps users
                // sanity-check date / frequency before saving — especially
                // useful for "last day of month" so they see how Feb shifts.
                val previewDates = remember(frequency, dayOfMonth) {
                    val today  = java.time.LocalDate.now()
                    val target = dayOfMonth.toIntOrNull() ?: 1
                    val out    = mutableListOf<java.time.LocalDate>()
                    when (frequency) {
                        "Daily" -> for (i in 1..3) out += today.plusDays(i.toLong())
                        "Weekly" -> {
                            // Next 3 Mondays.
                            var d = today.plusDays(1)
                            while (out.size < 3) {
                                if (d.dayOfWeek.value == 1) out += d
                                d = d.plusDays(1)
                            }
                        }
                        "Monthly" -> {
                            // Walk forward month-by-month, clamping target to
                            // each month's length so day-31 still produces a
                            // valid date in shorter months.
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
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
                    Text(
                        "Next occurrences: " + previewDates.joinToString(" · ") { it.format(fmt) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // C17 (3.2.42) — inline reason for the disabled Add button.
                app.fynlo.ui.components.DisabledButtonHint(
                    if (name.isBlank()) "Enter a name to continue" else null
                )
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
                            // C22 Stage 2 — range now 1-31. Worker clamps to
                            // month.lengthOfMonth() at run time so day-31
                            // safely fires on Feb 28 / Apr 30 / etc.
                            dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1
                        ))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}








