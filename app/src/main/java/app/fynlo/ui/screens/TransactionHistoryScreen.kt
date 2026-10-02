package app.fynlo.ui.screens

import app.fynlo.logic.displayFromAcct
import app.fynlo.logic.displayToAcct
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.TransactionOrdering
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.logic.HistoryFilter
import app.fynlo.logic.historyCashTotals
import app.fynlo.logic.matchesHistoryQuery
import app.fynlo.ui.components.FormDialog
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionHistoryScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val allProjectTransactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    // 3.2.81 - account names for the edit dialog's new Account picker (C13 #9).
    val allAccounts by viewModel.accounts.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    var selectedType   by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var showDateFilter by remember { mutableStateOf(false) }
    var fromDate       by rememberSaveable { mutableStateOf("") }
    var toDate         by rememberSaveable { mutableStateOf("") }
    var selectionMode  by remember { mutableStateOf(false) }
    var selectedIds    by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val accountNames = remember(allAccounts) { allAccounts.associate { it.id to it.name } }

    val filteredHistory = remember(allProjectTransactions, searchQuery, accountNames, selectedType, fromDate, toDate) {
        var list = allProjectTransactions.filter {
            selectedType.matches(it) && it.matchesHistoryQuery(searchQuery, accountNames)
        }
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
        TransactionOrdering.newestFirst(list)
    }
    val balanceImpactsByTransaction = remember(allProjectTransactions, allAccounts) {
        buildBalanceImpactsByTransaction(allProjectTransactions, allAccounts)
    }

    val totals = remember(filteredHistory) { historyCashTotals(filteredHistory) }
    val hasActiveFilters = searchQuery.isNotBlank() ||
        selectedType != HistoryFilter.ALL ||
        fromDate.isNotBlank() ||
        toDate.isNotBlank()
    val localDataReady by viewModel.localDataReady.collectAsState()
    val isInitialLoading = !localDataReady &&
        allProjectTransactions.isEmpty() &&
        !hasActiveFilters
    val hairline = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    var bulkDeleteInProgress by remember { mutableStateOf(false) }

    if (showBulkDeleteConfirm) {
        FynloConfirmDialog(
            title = "Delete ${app.fynlo.logic.pluralize(selectedIds.size, "transaction")}?",
            message = "This will permanently delete the selected transactions and reverse their account balances.",
            confirmText = "Delete All",
            destructive = true,
            onDismiss = { showBulkDeleteConfirm = false },
            onConfirm = {
                if (bulkDeleteInProgress) return@FynloConfirmDialog
                bulkDeleteInProgress = true
                val toDelete = filteredHistory.filter { it.id in selectedIds && !it.isGeneratedJournalEntry() }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.deleteTransactions(toDelete)
                selectedIds = emptySet()
                selectionMode = false
                showBulkDeleteConfirm = false
                bulkDeleteInProgress = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Cash movement totals are separate from spending classifications.
        if (selectionMode) {
            Text(
                text = "${selectedIds.size} selected",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(app.fynlo.logic.pluralize(filteredHistory.size, "entry", "entries"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectedType == HistoryFilter.TRANSFERS) {
                HistorySummaryMetric("Transferred", if (isPrivacy) "Hidden" else CurrencyFormatter.exact(totals.transfers, currencyCode, locale), Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HistorySummaryMetric("Money in", if (isPrivacy) "Hidden" else CurrencyFormatter.exact(totals.moneyIn, currencyCode, locale), Modifier.weight(1f))
                    HistorySummaryMetric("Money out", if (isPrivacy) "Hidden" else CurrencyFormatter.exact(totals.moneyOut, currencyCode, locale), Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // -- Selection action bar (flat, emerald) -------------------------------
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                Arrangement.spacedBy(8.dp), Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectionMode = false; selectedIds = emptySet() }) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selectedIds = filteredHistory.filterNot { it.isGeneratedJournalEntry() }.map { it.id }.toSet() }) {
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
            // -- Soft search ----------------------------------------------------
            HistorySoftField(
                value = searchQuery,
                placeholder = "Search history",
                leading = Icons.Default.Search,
                onChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var typeMenu by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.FilterList, null, Modifier.size(18.dp))
                        Text(selectedType.label, Modifier.weight(1f).padding(horizontal = 8.dp))
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        HistoryFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(filter.label) }, onClick = { selectedType = filter; typeMenu = false })
                        }
                    }
                }
                val datesActive = showDateFilter || fromDate.isNotBlank() || toDate.isNotBlank()
                Surface(
                    onClick = { showDateFilter = !showDateFilter },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (datesActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, TemplateBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text(if (datesActive) "Dates set" else "Dates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (showDateFilter) {
                FormDialog(title = "Filter dates", onDismiss = { showDateFilter = false }) {
                val today = java.time.LocalDate.now()
                val displayFmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                Spacer(Modifier.height(12.dp))
                // 3.2.11 chip-sweep: 7 date-preset FilterChips wrapped to 2-3 lines
                // depending on screen width, taking significant vertical space and not
                // always discoverable. Now an ExposedDropdownMenuBox - same widget the
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
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.fynlo.ui.components.DatePickerField(
                        value = fromDate, onValueChange = { fromDate = it },
                        label = "From", optional = true, modifier = Modifier.fillMaxWidth()
                    )
                    app.fynlo.ui.components.DatePickerField(
                        value = toDate, onValueChange = { toDate = it },
                        label = "To", optional = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (fromDate.isNotBlank() || toDate.isNotBlank()) {
                        TextButton(onClick = { fromDate = ""; toDate = "" }) { Text("Clear") }
                    }
                    TextButton(onClick = { showDateFilter = false }) { Text("Done") }
                }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isInitialLoading) {
            Text(
                "Loading history",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            PremiumSkeletonList(rows = 5)
        } else if (filteredHistory.isEmpty()) {
            EmptyTransactionState(
                hasActiveFilters = hasActiveFilters,
                onClearFilters = {
                    viewModel.updateSearchQuery("")
                    selectedType = HistoryFilter.ALL
                    fromDate = ""
                    toDate = ""
                    showDateFilter = false
                },
            )
        } else {
            app.fynlo.ui.components.PullRefresh(viewModel) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = FabBottomPadding)
            ) {
                val byMonth = filteredHistory.groupBy { it.date.take(7) }
                byMonth.keys.sortedByDescending { it }.forEach { month ->
                    val monthTransactions = byMonth[month] ?: emptyList()
                    val monthTotals = historyCashTotals(monthTransactions)
                    val monthLabel   = runCatching {
                        val ym = java.time.YearMonth.parse(month)
                        ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                    }.getOrDefault(month)

                    // -- Flat month header --------------------------------------
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                        ) {
                            Text(monthLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (selectedType == HistoryFilter.TRANSFERS) {
                                    Text(
                                        if (isPrivacy) "Transferred: hidden" else "Transferred ${CurrencyFormatter.exact(monthTotals.transfers, currencyCode, locale)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                val mIncText = if (isPrivacy) "Hidden" else CurrencyFormatter.exact(monthTotals.moneyIn, currencyCode, locale)
                                Text("In $mIncText",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                                val mExpText = if (isPrivacy) "Hidden" else CurrencyFormatter.exact(monthTotals.moneyOut, currencyCode, locale)
                                Text("Out $mExpText",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
                        val dayTxns = TransactionOrdering.newestFirst(byDate[date] ?: emptyList())
                        itemsIndexedTxns(dayTxns) { idx, transaction ->
                            TransactionItem(
                                txn         = transaction,
                                isSelected  = transaction.id in selectedIds,
                                selectionMode = selectionMode,
                                currencyCode = currencyCode,
                                isPrivacy    = isPrivacy,
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectionMode = true; selectedIds = selectedIds + transaction.id
                                },
                                onSelect    = {
                                    selectedIds = if (transaction.id in selectedIds)
                                        selectedIds - transaction.id
                                    else selectedIds + transaction.id
                                },
                                onEdit   = {
                                    viewModel.editTransaction(transaction, it)
                                    viewModel.showFeedback("Transaction updated")
                                },
                                onDelete = {
                                    viewModel.deleteTransaction(transaction)
                                    viewModel.showFeedback("Transaction deleted")
                                },
                                // 3.2.81 - accounts for the edit dialog's new Account picker.
                                bankAccounts = allAccounts.map { it.name },
                                // C03b Stage #1b-2 (3.2.88) - id -> current name
                                // for rename-reflective sub-label.
                                accountIdToName = accountNames,
                                balanceImpacts = balanceImpactsByTransaction[transaction.id].orEmpty(),
                            )
                            Spacer(Modifier.height(if (idx < dayTxns.lastIndex) 8.dp else 2.dp))
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

data class TransactionBalanceImpact(
    val accountName: String,
    val before: Double,
    val after: Double,
    val delta: Double,
)

fun buildBalanceImpactsByTransaction(
    transactions: List<Transaction>,
    accounts: List<Account>,
): Map<String, List<TransactionBalanceImpact>> {
    val nameByKey = buildMap {
        accounts.forEach { account ->
            put(account.id, account.name)
            put(account.name, account.name)
        }
    }
    val mirrorKeyByKey = buildMap {
        accounts.forEach { account ->
            put(account.id, account.name)
            put(account.name, account.id)
        }
    }
    val running = buildMap {
        accounts.forEach { account ->
            put(account.id, account.balance)
            put(account.name, account.balance)
        }
    }.toMutableMap()
    val impacts = mutableMapOf<String, List<TransactionBalanceImpact>>()

    fun keyFor(id: String, name: String): String =
        id.takeIf { it.isNotBlank() } ?: name

    fun MutableMap<String, Double>.addDelta(key: String, delta: Double) {
        if (key.isBlank()) return
        this[key] = (this[key] ?: 0.0) + delta
    }

    val newestFirst = TransactionOrdering.newestFirst(transactions)

    newestFirst.forEach { txn ->
        if (txn.isGeneratedJournalEntry()) return@forEach
        val deltas = mutableMapOf<String, Double>()
        when (txn.type.lowercase()) {
            "expense" -> deltas.addDelta(keyFor(txn.fromAcctId, txn.fromAcct), -txn.amount)
            "income" -> deltas.addDelta(keyFor(txn.toAcctId, txn.toAcct), txn.amount)
            "transfer" -> {
                deltas.addDelta(keyFor(txn.fromAcctId, txn.fromAcct), -txn.amount)
                deltas.addDelta(keyFor(txn.toAcctId, txn.toAcct), txn.amount)
            }
        }

        val rowImpacts = deltas
            .filter { (key, delta) -> key.isNotBlank() && abs(delta) > 0.005 && running.containsKey(key) }
            .map { (key, delta) ->
                val after = running[key] ?: 0.0
                val before = after - delta
                running[key] = before
                val mirrorKey = mirrorKeyByKey[key]
                if (mirrorKey != null) {
                    running[mirrorKey] = before
                }
                TransactionBalanceImpact(
                    accountName = nameByKey[key] ?: key,
                    before = before,
                    after = after,
                    delta = delta,
                )
            }

        if (rowImpacts.isNotEmpty()) {
            impacts[txn.id] = rowImpacts
        }
    }

    return impacts
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySoftField(
    value: String,
    placeholder: String,
    leading: ImageVector,
    onChange: (String) -> Unit
) {
    PremiumSearchField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = leading,
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
    isPrivacy: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelect: () -> Unit = {},
    // C13 #9 (3.2.81) - propagate the user's actual account names so the
    // edit dialog's new Account picker shows real options (not free-text
    // that could orphan the transaction per the 3.2.59 bug pattern).
    // Default emptyList for back-compat call sites that haven't wired it.
    bankAccounts: List<String> = emptyList(),
    // C03b Stage #1b-2 (3.2.88) - id -> current Account.name lookup so
    // the row's sub-label reflects renames immediately (the stored
    // fromAcct/toAcct can be stale; the id is the immutable handle).
    // Empty map means "fall through to stored name" - back-compat for
    // call sites that haven't wired it yet.
    accountIdToName: Map<String, String> = emptyMap(),
    balanceImpacts: List<TransactionBalanceImpact> = emptyList(),
    showTimestamp: Boolean = false,
    compact: Boolean = false,
) {
    val isExpense  = txn.type.lowercase() == "expense"
    val isIncome   = txn.type.lowercase() == "income"
    val rowColor   = when {
        isIncome   -> Emerald500
        isExpense  -> SemanticRed
        else       -> SemanticBlue
    }
    val locale = LocalLocale.current.platformLocale
    var showEditDialog by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showManagedEntry by remember { mutableStateOf(false) }
    var deleteInProgress by remember(txn.id) { mutableStateOf(false) }
    val isManagedEntry = txn.isGeneratedJournalEntry()

    if (showDetails) {
        FormDialog(title = "Transaction details", onDismiss = { showDetails = false }) {
            Text(
                if (isPrivacy) "Hidden" else CurrencyFormatter.exact(txn.amount, currencyCode, locale),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    isManagedEntry -> "Record only - no account movement"
                    isIncome -> "Money received"
                    isExpense -> "Money paid out"
                    txn.type.equals("Transfer", true) -> "Transfer between accounts"
                    else -> "Record only"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HistoryDetailField("Date", DateUtils.formatToDisplay(txn.date))
            if (txn.createdAt > 0L) {
                val recorded = java.time.Instant.ofEpochMilli(txn.createdAt).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", locale))
                HistoryDetailField("Recorded", recorded)
            }
            HistoryDetailField("Category", txn.category.ifBlank { "Not specified" })
            if (txn.person.isNotBlank()) HistoryDetailField("Person", txn.person)
            if (txn.subcat.isNotBlank()) HistoryDetailField("Subcategory", txn.subcat)
            if (txn.desc.isNotBlank()) HistoryDetailField("Description", txn.desc)
            val from = txn.displayFromAcct(accountIdToName)
            val to = txn.displayToAcct(accountIdToName)
            if (from.isNotBlank()) HistoryDetailField("From account", from)
            if (to.isNotBlank()) HistoryDetailField("To account", to)
            if (txn.notes.isNotBlank()) HistoryDetailField("Notes", txn.notes)
            if (!isManagedEntry && balanceImpacts.isEmpty()) {
                Text("Account balance history is unavailable for this entry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            balanceImpacts.forEach { impact ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(impact.accountName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HistoryDetailField("Balance before", if (isPrivacy) "Hidden" else CurrencyFormatter.exact(impact.before, currencyCode, locale))
                HistoryDetailField("Change", if (isPrivacy) "Hidden" else if (impact.delta < 0) CurrencyFormatter.negativeExact(impact.delta, currencyCode, locale) else "+${CurrencyFormatter.exact(impact.delta, currencyCode, locale)}")
                HistoryDetailField("Balance after", if (isPrivacy) "Hidden" else CurrencyFormatter.exact(impact.after, currencyCode, locale))
            }
            Spacer(Modifier.height(12.dp))
            if (isManagedEntry) {
                Text("Change the original loan or debt payment to update this record.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showDetails = false; showEditDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }
                    OutlinedButton(onClick = { showDetails = false; showDeleteConfirm = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showManagedEntry) {
        FynloConfirmDialog(
            title = "Managed entry",
            message = "This entry is generated from a loan or debt action. Edit or delete the original payment so linked totals stay correct.",
            confirmText = "OK",
            showDismissButton = false,
            onDismiss = { showManagedEntry = false },
            onConfirm = { showManagedEntry = false },
        )
    }
    if (showEditDialog && !isManagedEntry) {
        app.fynlo.ui.components.EditTransactionDialog(
            transaction  = txn,
            bankAccounts = bankAccounts,
            currencyCode = currencyCode,
            onDismiss    = { showEditDialog = false },
            onConfirm    = { updated -> onEdit(updated); showEditDialog = false }
        )
    }
    if (showDeleteConfirm && !isManagedEntry) {
        FynloConfirmDialog(
            title = "Delete transaction?",
            message = "Delete ${CurrencyFormatter.detail(txn.amount, currencyCode, locale)} ${txn.category}? This reverses the account balance.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                if (!deleteInProgress) {
                    deleteInProgress = true
                    showDeleteConfirm = false
                    onDelete()
                }
            },
        )
    }

    val swipeState = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipeState.currentValue) {
        when (swipeState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (isManagedEntry) showManagedEntry = true else showEditDialog = true
                swipeState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (isManagedEntry) showManagedEntry = true else showDeleteConfirm = true
                swipeState.reset()
            }
            else -> Unit
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !selectionMode && !isManagedEntry,
        enableDismissFromEndToStart = !selectionMode && !isManagedEntry,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when {
                        selectionMode -> if (!isManagedEntry) onSelect()
                        else -> showDetails = true
                    }
                },
                onLongClick = {
                    if (isManagedEntry) showManagedEntry = true else onLongPress()
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) rowColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isSelected) BorderStroke(1.dp, rowColor) else null,
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Leading checkbox in selection mode, otherwise a neutral category icon.
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
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(getCategoryIcon(txn.category), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            val title = txn.desc.ifBlank { txn.person.ifBlank { txn.category.ifBlank { "Transaction" } } }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // C03b Stage #1b-2: resolve via id (renames take immediate effect);
            // falls back to stored name for legacy orphan rows.
            val sub = txn.category
            if (!compact && sub.isNotBlank() && !sub.equals(title, ignoreCase = true)) {
                Text(sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (showTimestamp) {
                Text(
                    DateUtils.formatToDisplay(txn.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val accountTrace = transactionAccountTrace(txn, accountIdToName)
            if (accountTrace.isNotBlank() && accountTrace != sub) {
                Text(
                    accountTrace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!compact && txn.notes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Note attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        val amountText = if (isPrivacy) "Hidden"
                         else when {
                             isIncome && !isManagedEntry -> "+${CurrencyFormatter.exact(txn.amount, currencyCode, locale)}"
                             isExpense && !isManagedEntry -> CurrencyFormatter.negativeExact(txn.amount, currencyCode, locale)
                             else -> CurrencyFormatter.exact(txn.amount, currencyCode, locale)
                         }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 136.dp)) {
        Text(
            text  = amountText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
        Icon(Icons.Default.ChevronRight, "View transaction details", Modifier.padding(top = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun HistorySummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryDetailField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}


private fun transactionAccountTrace(
    txn: Transaction,
    accountIdToName: Map<String, String>,
): String {
    val from = txn.displayFromAcct(accountIdToName).takeIf { it.isNotBlank() }
    val to = txn.displayToAcct(accountIdToName).takeIf { it.isNotBlank() }
    return when (txn.type.lowercase()) {
        "transfer" -> when {
            from != null && to != null -> "Moved from $from to $to"
            from != null -> "From $from"
            to != null -> "To $to"
            else -> ""
        }
        "expense" -> when {
            from != null && txn.category.equals("Investment", ignoreCase = true) -> "Funded from $from"
            from != null && txn.category.equals("Lending", ignoreCase = true) -> "Lent from $from"
            from != null && txn.category.equals("Debt Repayment", ignoreCase = true) -> "Paid from $from"
            from != null -> "Paid from $from"
            else -> ""
        }
        "income" -> when {
            to != null && txn.category.equals("Loan Repayment", ignoreCase = true) -> "Collected into $to"
            to != null && txn.category.equals("Debt Received", ignoreCase = true) -> "Received into $to"
            to != null -> "Received into $to"
            else -> ""
        }
        else -> ""
    }
}


@Composable
fun EmptyTransactionState(
    hasActiveFilters: Boolean = false,
    onClearFilters: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Text(
                if (hasActiveFilters) "No matching transactions" else "No transactions yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (hasActiveFilters) {
                    "Clear search or date filters to see more entries."
                } else {
                    "Use the + button to add income, expense, or transfer entries."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasActiveFilters) {
                OutlinedButton(onClick = onClearFilters) {
                    Text("Clear filters")
                }
            }
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
        "investment" -> Icons.AutoMirrored.Filled.TrendingUp
        "lending", "loan repayment" -> Icons.Default.Handshake
        "debt", "debt repayment" -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalanceWallet
    }
}
