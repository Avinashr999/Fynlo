package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Transaction
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionHistoryScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = Locale.getDefault()

    var selectedType   by remember { mutableStateOf("All") }
    var showDateFilter by remember { mutableStateOf(false) }
    var fromDate       by remember { mutableStateOf("") }
    var toDate         by remember { mutableStateOf("") }
    var selectionMode  by remember { mutableStateOf(false) }
    var selectedIds    by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val types = listOf("All", "Income", "Expense")

    val filteredHistory = remember(transactions, selectedType, fromDate, toDate) {
        var list = if (selectedType == "All") transactions
                   else transactions.filter { it.type.equals(selectedType, ignoreCase = true) }
        if (fromDate.isNotBlank()) {
            val from = runCatching {
                val d = java.time.LocalDate.parse(fromDate, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }.getOrDefault("")
            if (from.isNotBlank()) list = list.filter { it.date >= from }
        }
        if (toDate.isNotBlank()) {
            val to = runCatching {
                val d = java.time.LocalDate.parse(toDate, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }.getOrDefault("")
            if (to.isNotBlank()) list = list.filter { it.date <= to }
        }
        list
    }

    val totalIncome  = filteredHistory.filter { it.type.equals("income", true) }.sumOf { it.amount }
    val totalExpense = filteredHistory.filter { it.type.equals("expense", true) }.sumOf { it.amount }
    val net = totalIncome - totalExpense
    val hairline = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} transactions?") },
            text  = { Text("This will permanently delete the selected transactions and reverse their account balances.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = filteredHistory.filter { it.id in selectedIds }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteTransactions(toDelete)
                        selectedIds = emptySet()
                        selectionMode = false
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticRed),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Hero: net total + entry count (flat, on background) ────────────────
        if (selectionMode) {
            Text(
                text = "${selectedIds.size} selected",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (net < 0) CurrencyFormatter.negative(net, currencyCode, locale)
                       else "+${CurrencyFormatter.detail(net, currencyCode, locale)}",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (net < 0) SemanticRed else Emerald500
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            ) {
                Text("${filteredHistory.size} entries",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("▲ ${CurrencyFormatter.detail(totalIncome, currencyCode, locale)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Emerald500)
                Text("▼ ${CurrencyFormatter.detail(totalExpense, currencyCode, locale)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SemanticRed)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Selection action bar (flat, emerald) ───────────────────────────────
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                Arrangement.spacedBy(8.dp), Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectionMode = false; selectedIds = emptySet() }) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selectedIds = filteredHistory.map { it.id }.toSet() }) {
                    Text("Select all", color = Emerald500)
                }
                Button(
                    onClick = { if (selectedIds.isNotEmpty()) showBulkDeleteConfirm = true },
                    enabled = selectedIds.isNotEmpty(),
                    colors  = ButtonDefaults.buttonColors(containerColor = SemanticRed),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete (${selectedIds.size})")
                }
            }
        } else {
            // ── Soft search ────────────────────────────────────────────────────
            HistorySoftField(
                value = searchQuery,
                placeholder = "Search transactions…",
                leading = Icons.Default.Search,
                onChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(Modifier.height(12.dp))

            // 3.2.11 chip-sweep: type filter chips → SegmentedButtonRow.
            // The Dates affordance is semantically a TOGGLE (show / hide the
            // date filter panel), not a "pick one of N" — so it becomes a
            // FilledTonalButton with the DateRange leading icon, which is the
            // M3 affordance for "tap to open a panel" actions. The button's
            // tonal weight communicates the active state when the panel is
            // open OR a date is currently selected, replacing the chip's
            // selected-colour treatment.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    types.forEachIndexed { idx, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            shape = SegmentedButtonDefaults.itemShape(idx, types.size),
                            icon = {},
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Emerald500.copy(alpha = 0.16f),
                                activeContentColor = Emerald500,
                            ),
                            label = { Text(type) },
                        )
                    }
                }
                val datesActive = showDateFilter || fromDate.isNotBlank()
                FilledTonalButton(
                    onClick = { showDateFilter = !showDateFilter },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = if (datesActive) ButtonDefaults.filledTonalButtonColors(
                        containerColor = Emerald500.copy(alpha = 0.16f),
                        contentColor = Emerald500,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dates", style = MaterialTheme.typography.labelMedium)
                }
            }

            // ── Flat date filter (no card) ─────────────────────────────────────
            if (showDateFilter) {
                val today = java.time.LocalDate.now()
                val displayFmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                Spacer(Modifier.height(12.dp))
                // 3.2.11 chip-sweep: 7 date-preset FilterChips wrapped to 2-3 lines
                // depending on screen width, taking significant vertical space and not
                // always discoverable. Now an ExposedDropdownMenuBox — same widget the
                // C04 currency picker uses, same widget the AddRecurring frequency
                // picker uses (3.2.10). One row, always fits. Selecting a preset still
                // populates fromDate/toDate so the existing custom-range DatePickerFields
                // below show the resolved dates (and the user can edit them from there).
                val datePresets = listOf(
                    "Today"      to (today to today),
                    "Yesterday"  to (today.minusDays(1) to today.minusDays(1)),
                    "Last 7d"    to (today.minusDays(6) to today),
                    "Last 30d"   to (today.minusDays(29) to today),
                    "This Month" to (today.withDayOfMonth(1) to today),
                    "Last Month" to (today.minusMonths(1).withDayOfMonth(1) to today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())),
                    "This Year"  to (today.withDayOfYear(1) to today),
                )
                val activePresetLabel = datePresets.firstOrNull { (_, range) ->
                    fromDate == range.first.format(displayFmt) && toDate == range.second.format(displayFmt)
                }?.first
                var presetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = !presetExpanded },
                ) {
                    OutlinedTextField(
                        value = activePresetLabel ?: "Custom range",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quick select") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false },
                    ) {
                        datePresets.forEach { (label, range) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    fromDate = range.first.format(displayFmt)
                                    toDate = range.second.format(displayFmt)
                                    presetExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.fynlo.ui.components.DatePickerField(
                        value = fromDate, onValueChange = { fromDate = it },
                        label = "From", optional = true, modifier = Modifier.weight(1f)
                    )
                    app.fynlo.ui.components.DatePickerField(
                        value = toDate, onValueChange = { toDate = it },
                        label = "To", optional = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (fromDate.isNotBlank() || toDate.isNotBlank()) {
                        TextButton(onClick = { fromDate = ""; toDate = "" }) { Text("Clear") }
                    }
                    TextButton(onClick = { showDateFilter = false }) { Text("Done", color = Emerald500) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredHistory.isEmpty()) {
            EmptyTransactionState()
        } else {
            app.fynlo.ui.components.PullRefresh(viewModel) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = FabBottomPadding)
            ) {
                val byMonth = filteredHistory.groupBy { it.date.substring(0, 7) }
                byMonth.keys.sortedByDescending { it }.forEach { month ->
                    val monthTransactions = byMonth[month] ?: emptyList()
                    val monthIncome  = monthTransactions.filter { it.type.equals("income", true) }.sumOf { it.amount }
                    val monthExpense = monthTransactions.filter { it.type.equals("expense", true) }.sumOf { it.amount }
                    val monthLabel   = runCatching {
                        val ym = java.time.YearMonth.parse(month)
                        ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                    }.getOrDefault(month)

                    // ── Flat month header ──────────────────────────────────────
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Text(monthLabel.uppercase(locale),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("+${CurrencyFormatter.detail(monthIncome, currencyCode, locale)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Emerald500)
                                Text(CurrencyFormatter.negative(monthExpense, currencyCode, locale),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SemanticRed)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = hairline)
                    }

                    val byDate = monthTransactions.groupBy { it.date }
                    byDate.keys.sortedByDescending { it }.forEach { date ->
                        item {
                            Text(DateUtils.formatToDisplay(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                        }
                        val dayTxns = byDate[date] ?: emptyList()
                        itemsIndexedTxns(dayTxns) { idx, transaction ->
                            TransactionItem(
                                txn         = transaction,
                                isSelected  = transaction.id in selectedIds,
                                selectionMode = selectionMode,
                                currencyCode = currencyCode,
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectionMode = true; selectedIds = selectedIds + transaction.id
                                },
                                onSelect    = {
                                    selectedIds = if (transaction.id in selectedIds)
                                        selectedIds - transaction.id
                                    else selectedIds + transaction.id
                                },
                                onEdit   = { viewModel.editTransaction(transaction, it) },
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                            if (idx < dayTxns.lastIndex) {
                                HorizontalDivider(thickness = 0.5.dp, color = hairline,
                                    modifier = Modifier.padding(start = 52.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/** Small wrapper so we get the index inside a LazyListScope.forEach loop. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedTxns(
    list: List<Transaction>,
    content: @Composable (Int, Transaction) -> Unit
) {
    items(list.size, key = { list[it].id }) { i -> content(i, list[i]) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySoftField(
    value: String,
    placeholder: String,
    leading: ImageVector,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(leading, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedLeadingIconColor = Emerald500,
            cursorColor = Emerald500
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    txn: Transaction,
    onEdit: (Transaction) -> Unit = {},
    onDelete: () -> Unit = {},
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    currencyCode: String = "INR",
    onLongPress: () -> Unit = {},
    onSelect: () -> Unit = {}
) {
    val isExpense  = txn.type.lowercase() == "expense"
    val isIncome   = txn.type.lowercase() == "income"
    val rowColor   = when {
        isIncome   -> Emerald500
        isExpense  -> SemanticRed
        else       -> SemanticBlue
    }
    val locale = java.util.Locale.getDefault()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showEditDialog) {
        app.fynlo.ui.components.EditTransactionDialog(
            transaction = txn,
            onDismiss   = { showEditDialog = false },
            onConfirm   = { updated -> onEdit(updated); showEditDialog = false }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transaction?") },
            text  = { Text("Delete ${CurrencyFormatter.detail(txn.amount, currencyCode, locale)} ${txn.category}? This reverses the account balance.") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticRed),
                    shape = RoundedCornerShape(14.dp)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { showEditDialog = true; false }
                SwipeToDismissBoxValue.EndToStart -> { showDeleteConfirm = true; false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val dir = swipeState.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> SemanticBlue.copy(alpha = 0.18f)
                SwipeToDismissBoxValue.EndToStart -> SemanticRed.copy(alpha = 0.18f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            Box(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Default.Edit, "Edit", tint = SemanticBlue)
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Default.Delete, "Delete", tint = SemanticRed)
                    else -> {}
                }
            }
        }
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else showEditDialog = true },
                onLongClick = onLongPress
            )
            .background(
                if (isSelected) rowColor.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.background
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading: checkbox in selection mode, else colored category chip
        if (selectionMode) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Box(Modifier.size(28.dp).background(rowColor, CircleShape), Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape))
                }
            }
        } else {
            Box(
                modifier = Modifier.size(40.dp).background(rowColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(getCategoryIcon(txn.category), null, tint = rowColor, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(txn.category,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            val sub = txn.desc.ifBlank { if (isExpense) txn.fromAcct else txn.toAcct }
            if (sub.isNotBlank()) {
                Text(sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            if (txn.notes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(txn.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text  = when {
                isIncome  -> "+${CurrencyFormatter.detail(txn.amount, currencyCode, locale)}"
                isExpense -> CurrencyFormatter.negative(txn.amount, currencyCode, locale)
                else      -> "↔${CurrencyFormatter.detail(txn.amount, currencyCode, locale)}"
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = rowColor
        )
    }
    }
}

@Composable
fun EmptyTransactionState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No transactions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "food" -> Icons.Default.Restaurant
        "fuel" -> Icons.Default.LocalGasStation
        "shopping" -> Icons.Default.ShoppingCart
        "salary" -> Icons.Default.Payments
        "medical" -> Icons.Default.MedicalServices
        "bills" -> Icons.Default.Receipt
        "investment" -> Icons.Default.TrendingUp
        "lending", "loan repayment" -> Icons.Default.Handshake
        "debt", "debt repayment" -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalanceWallet
    }
}
