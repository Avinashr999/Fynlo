package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import app.fynlo.logic.CurrencyFormatter
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
fun DebtScreen(
    viewModel: FinanceViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    showHeader: Boolean = true
) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Debts") }
        val haptic = LocalHapticFeedback.current
val debts by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = Locale.getDefault()
    var searchQuery by remember { mutableStateOf("") }
    // C12 Stage 2 (3.2.27) — Active/Overdue/Closed segmented filter for parity
    // with LendingScreen. Active = paid < amount; Overdue = active AND due
    // date past today; Closed = paid >= amount (fully repaid).
    var statusFilter by remember { mutableStateOf("Active") }
    val todayKey = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

    val searched = remember(debts, searchQuery) {
        if (searchQuery.isBlank()) debts
        else debts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true)
        }
    }
    val activeDebts  = remember(searched) { searched.filter { it.paid < it.amount } }
    val overdueDebts = remember(activeDebts, todayKey) {
        activeDebts.filter { it.due.isNotBlank() && it.due < todayKey }
    }
    val closedDebts  = remember(searched) { searched.filter { it.paid >= it.amount } }
    val filteredDebts = when (statusFilter) {
        "Overdue" -> overdueDebts
        "Closed"  -> closedDebts
        else      -> activeDebts
    }
    var showAddDialog by remember { mutableStateOf(false) }

    // C12 Stage 3 (3.2.28) — Edit + Pay flows moved to DebtDetailScreen per
    // audit §C12 #6/#7 (no per-row action icons). Only the Add flow stays
    // here; tapping a row navigates to DebtDetailScreen which hosts all
    // per-debt actions as proper buttons.
    if (showAddDialog) {
        AddDebtDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { debt, dest ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addDebtWithDestination(debt, dest)
                showAddDialog = false
            },
            initialDebt = null
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) PremiumScreenHeader("My Debts", "Loans you owe to others")
        Box(modifier = Modifier.weight(1f)) {
        app.fynlo.ui.components.PullRefresh(viewModel) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding(),
            contentPadding = PaddingValues(bottom = FabBottomPadding)
        ) {
        item {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), Arrangement.End, Alignment.CenterVertically) {
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

        // C12 Stage 2 — Total Outstanding card removed; LoansHubScreen's
        // C12 Stage 1 hero (3.2.25) now owns that read for the Owed tab,
        // making the in-screen summary redundant.

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
                placeholder   = { Text("Search debts…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = ""; expanded = false }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Color.Transparent,
                    focusedLeadingIconColor = Emerald500,
                    cursorColor = Emerald500
                )
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

        }
        // C12 Stage 2 — Active/Overdue/Closed segmented filter, parity with
        // LendingScreen's filter. Counts in each segment so the user sees
        // status distribution at a glance ("Active 3 · Overdue 1 · Closed 0").
        if (debts.isNotEmpty()) {
            item {
                val filters = listOf(
                    "Active"  to activeDebts.size,
                    "Overdue" to overdueDebts.size,
                    "Closed"  to closedDebts.size,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    filters.forEachIndexed { idx, (label, count) ->
                        SegmentedButton(
                            selected = statusFilter == label,
                            onClick = { statusFilter = label },
                            shape = SegmentedButtonDefaults.itemShape(idx, filters.size),
                            icon = {},
                            label = { Text("$label  ·  $count", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        if (filteredDebts.isEmpty()) {
            item {
                if (debts.isEmpty()) {
                    EmptyDebtState(onAdd = { showAddDialog = true })
                } else {
                    val msg = when (statusFilter) {
                        "Overdue" -> "No overdue debts — you're on track 🎉"
                        "Closed"  -> "No closed debts yet"
                        else      -> "No active debts"
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(filteredDebts, key = { _, d -> d.id }) { index, debt ->
                    DebtCard(
                        debt = debt,
                        currencyCode = currencyCode,
                        onClick = { onNavigateToDetail(debt.id) }
                    )
                    if (index < filteredDebts.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
        }
    }
}

}
/**
 * C12 Stage 3 (3.2.28) — DebtCard is now a clickable row per audit §C12 #6:
 * "icon + name + amount + chevron. One row tap → detail screen. NO action
 * icons in row." All per-debt actions (Pay / Edit / Delete) moved to
 * [DebtDetailScreen]. Matches [LendingCard] visually per audit #5 ("standardize
 * Lent vs Owed rows to identical visual structure").
 */
@Composable
fun DebtCard(
    debt: Debt,
    currencyCode: String = "INR",
    onClick: () -> Unit,
) {
    val locale = Locale.getDefault()
    val today  = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    val isOverdue = debt.due.isNotBlank() && debt.due < today && debt.paid < debt.amount

    val interest = InterestEngine.calcIntAccrued(
        amount = debt.amount, rate = debt.rate,
        loanDate = debt.date, intType = debt.intType,
        dueDate = debt.due, totalPaid = debt.paid
    )
    val outstanding = InterestEngine.calcOutstanding(debt.amount, interest, debt.paid)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SemanticRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CreditCard, null, tint = SemanticRed, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    debt.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (isOverdue) {
                    Surface(color = SemanticRed.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "OVERDUE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SemanticRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val sub = if (debt.due.isNotBlank()) "Due ${DateUtils.formatToDisplay(debt.due)}"
                      else "Borrowed ${DateUtils.formatToDisplay(debt.date)}"
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                CurrencyFormatter.detail(outstanding, currencyCode, locale),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isOverdue) SemanticRed else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                "Outstanding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
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








