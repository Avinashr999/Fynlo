package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Investment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.ui.components.AddInvestmentDialog

import app.fynlo.ui.components.InvestmentSaveRequest
import java.util.Locale
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(viewModel: FinanceViewModel) {
    val investments    by viewModel.investments.collectAsState()
    val accounts       by viewModel.accounts.collectAsState()
    val debts          by viewModel.debts.collectAsState()
        val haptic = LocalHapticFeedback.current
val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)

    var editingInvest  by remember { mutableStateOf<Investment?>(null) }
    var updatingInvest by remember { mutableStateOf<Investment?>(null) }

    var deletingInvest  by remember { mutableStateOf<Investment?>(null) }
    var withdrawingInvest by remember { mutableStateOf<Investment?>(null) }
    var viewingHistory by remember { mutableStateOf<Investment?>(null) }

    // ── Edit dialog ────────────────────────────────────────────────────────────
    if (editingInvest != null) {
        AddInvestmentDialog(
            accounts = accounts,
            debts    = debts,
            currencyCode = currencyCode,
            onDismiss = { editingInvest = null },
            onConfirm = { req: InvestmentSaveRequest ->
                if (editingInvest?.id?.isNotBlank() == true) {
                    viewModel.updateInvestment(req.investment)
                } else {
                    when (req.sourceType) {
                        "account"       -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addInvestmentFundedByAccount(req.investment, req.sourceAccountName) }
                        "existing_debt" -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); req.sourceDebt?.let { viewModel.addInvestmentFundedByExistingDebt(req.investment, it) } }
                        "new_loan"      -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); req.newLoan?.let { viewModel.addInvestmentFundedByNewLoan(req.investment, it) } }
                        else            -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addInvestmentWithSource(req.investment, req.sourceAccountName) }
                    }
                }
                editingInvest = null
            },
            initialInvestment = editingInvest
        )
    }

    // ── Update value dialog ────────────────────────────────────────────────────
    if (updatingInvest != null) {
        UpdateInvestmentValueDialog(
            investment = updatingInvest!!,
            currencySymbol = currencySymbol,
            currencyCode = currencyCode,
            onDismiss  = { updatingInvest = null },
            onConfirm  = { newVal, date, notes ->
                viewModel.addValuation(
                    app.fynlo.data.model.InvestmentValuation(
                        id = UUID.randomUUID().toString(),
                        investmentId = updatingInvest!!.id,
                        date = DateUtils.parseInput(date),
                        value = newVal,
                        notes = notes
                    )
                )
                updatingInvest = null
            }
        )
    }


    // ── Smart delete confirmation — shows options based on sourceType ──────────
    // ── Withdraw Dialog ─────────────────────────────────────────────────────
    if (withdrawingInvest != null) {
        val inv = withdrawingInvest!!
        var withdrawAmt by remember { mutableStateOf("") }
        var withdrawExpanded by remember { mutableStateOf(false) }
        val withdrawAccountOptions = if (accounts.isNotEmpty()) accounts
        else listOf(app.fynlo.data.model.Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))
        var withdrawAccount by remember { mutableStateOf(withdrawAccountOptions.first()) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { withdrawingInvest = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Withdraw from Investment", style = MaterialTheme.typography.headlineSmall)
                    Text("${inv.name}  •  Current Value: ${CurrencyFormatter.detail(inv.currentVal, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawAmt, onValueChange = { withdrawAmt = it },
                        label = { Text("Withdrawal Amount") }, prefix = { Text(currencySymbol) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(expanded = withdrawExpanded, onExpandedChange = { withdrawExpanded = !withdrawExpanded }) {
                        OutlinedTextField(
                            value = withdrawAccount.name, onValueChange = {}, readOnly = true,
                            label = { Text("Credit to account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = withdrawExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = withdrawExpanded, onDismissRequest = { withdrawExpanded = false }) {
                            withdrawAccountOptions.forEach { acct ->
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.fillMaxWidth(),
                                            androidx.compose.foundation.layout.Arrangement.SpaceBetween
                                        ) {
                                            Text(acct.name)
                                            Text(CurrencyFormatter.detail(acct.balance, currencyCode),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = app.fynlo.ui.theme.Emerald500)
                                        }
                                    },
                                    onClick = { withdrawAccount = acct; withdrawExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { withdrawingInvest = null }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amt = withdrawAmt.toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.withdrawFromInvestment(inv, amt, withdrawAccount.name)
                                    withdrawingInvest = null
                                }
                            },
                            enabled = withdrawAmt.toDoubleOrNull().let { it != null && it > 0.0 }
                        ) { Text("Withdraw") }
                    }
                }
            }
        }
    }

    if (deletingInvest != null) {
        val inv = deletingInvest!!
        AlertDialog(
            onDismissRequest = { deletingInvest = null },
            title = { Text("Delete Investment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${inv.name}  •  ${CurrencyFormatter.detail(inv.invested, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    when (inv.sourceType) {
                        "account"  -> Text(
                            "This was funded from ${inv.fundingSource}. Do you want to restore ${CurrencyFormatter.detail(inv.invested, currencyCode)} back to that account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        "new_loan" -> Text(
                            "This investment has a linked loan (${inv.fundingSource}). Do you want to delete the loan record too?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            "This will permanently remove the investment record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Action buttons inside text area to avoid overlap
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (inv.sourceType) {
                            "account" -> Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteInvestmentAndReverseAccount(inv); deletingInvest = null },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Delete + Restore ${CurrencyFormatter.detail(inv.invested, currencyCode)} to ${inv.fundingSource.take(14)}")
                            }
                            "new_loan" -> Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteInvestmentAndLinkedLoan(inv); deletingInvest = null },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete Investment + Loan")
                            }
                        }
                        OutlinedButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteInvestment(inv); deletingInvest = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete Record Only")
                        }
                        TextButton(
                            onClick = { deletingInvest = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            },
            confirmButton = {},  // all actions in text area
            dismissButton = {}
        )
    }

    if (viewingHistory != null) {
        ValuationHistoryDialog(
            investment = viewingHistory!!,
            currencyCode = currencyCode,
            valuations = viewModel.getValuationsForInvestment(viewingHistory!!.id).collectAsState(initial = emptyList()).value,
            onDismiss = { viewingHistory = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PremiumScreenHeader("My Investments", "Portfolio & returns tracker")

        Box(modifier = Modifier.weight(1f)) {
        if (investments.isEmpty()) {
            EmptyInvestState(onAdd = { editingInvest = Investment(id = "", name = "", type = "", invested = 0.0, currentVal = 0.0, date = "", notes = "", projectId = "") })
        } else {
            app.fynlo.ui.components.PullRefresh(viewModel) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = FabBottomPadding)
            ) {
                itemsIndexed(investments, key = { _, i -> i.id }) { index, invest ->
                    InvestmentCard(
                        invest   = invest,
                        currencyCode = currencyCode,
                        onDelete    = { deletingInvest = invest },
                        onWithdraw  = { withdrawingInvest = invest },
                        onEdit   = { editingInvest = invest },
                        onUpdate = { updatingInvest = invest },
                        onViewHistory = { viewingHistory = invest }
                    )
                    if (index < investments.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
            }
        }
        FloatingActionButton(
            onClick = { editingInvest = Investment(id = "", name = "", type = "", invested = 0.0, currentVal = 0.0, date = "", notes = "", projectId = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, contentDescription = "Add Investment") }
        }
    }
}

@Composable
fun InvestmentCard(invest: Investment, currencyCode: String = "INR", onDelete: () -> Unit, onEdit: () -> Unit, onUpdate: () -> Unit, onViewHistory: () -> Unit, onWithdraw: () -> Unit = {}) {
    val growth = invest.currentVal - (invest.invested - invest.withdrawn)
    val growthPercent = if (invest.invested > 0) (growth / invest.invested) * 100 else 0.0
    val isProfit = growth >= 0
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            // ── Header: name + type badge + action icons ──────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(Emerald500.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TrendingUp, null, Modifier.size(18.dp), tint = Emerald500)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(invest.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(invest.type, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Growth badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isProfit) Emerald500.copy(alpha = 0.1f) else SemanticRed.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "${if (isProfit) "+" else ""}${String.format(Locale.getDefault(), "%.1f", growthPercent)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isProfit) Emerald500 else SemanticRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Box {
                        IconButton(onClick = { menuOpen = true }, Modifier.size(30.dp)) {
                            Icon(Icons.Default.MoreVert, "More", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Valuation History") }, onClick = { menuOpen = false; onViewHistory() })
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", color = SemanticRed) }, onClick = { menuOpen = false; onDelete() })
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            // ── Key metrics row ──────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text("Invested", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.detail(invest.invested, currencyCode),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Since ${DateUtils.formatToDisplay(invest.date)}",
                        style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gain / Loss", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (isProfit) "+${CurrencyFormatter.hero(growth, currencyCode)}"
                        else          CurrencyFormatter.negative(growth, currencyCode),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isProfit) Emerald500 else SemanticRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Value", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.detail(invest.currentVal, currencyCode),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold,
                            color = if (isProfit) Emerald500 else SemanticRed))
                }
            }

            if (invest.withdrawn > 0) {
                Spacer(Modifier.height(6.dp))
                Text("Withdrawn: ${CurrencyFormatter.detail(invest.withdrawn, currencyCode)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (invest.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(13.dp), tint = Color.Gray)
                    Spacer(Modifier.width(6.dp))
                    Text(invest.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // ── Action buttons row ───────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Update, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Update Value", style = MaterialTheme.typography.labelSmall)
                }
                if (invest.currentVal > 0) {
                    Button(
                        onClick = onWithdraw,
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.CallMade, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Withdraw", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
}

@Composable
fun EmptyInvestState(onAdd: () -> Unit = {}) {
    app.fynlo.ui.components.EmptyStateIllustration(
        type        = app.fynlo.ui.components.EmptyStateType.INVESTMENTS,
        onAction    = onAdd,
        actionLabel = "Add First Investment"
    )
}

@Composable
fun UpdateInvestmentValueDialog(
    investment: Investment,
    currencySymbol: String = "₹",
    currencyCode: String = "INR",
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var newValue by remember { mutableStateOf(investment.currentVal.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }

    val parsed   = newValue.toDoubleOrNull()
    val growth   = parsed?.let { it - investment.invested }
    val locale   = java.util.Locale.getDefault()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log New Valuation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(investment.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                OutlinedTextField(
                    value = newValue, onValueChange = { newValue = it },
                    label = { Text("New Market Value ($currencySymbol)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )

                app.fynlo.ui.components.DatePickerField(value = date, onValueChange = { date = it }, label = "Valuation Date")

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )

                if (growth != null) {
                    val pct = if (investment.invested > 0) (growth / investment.invested) * 100 else 0.0
                    val growthStr = if (growth >= 0) "+${CurrencyFormatter.detail(growth, currencyCode, locale)}"
                                    else            CurrencyFormatter.negative(growth, currencyCode, locale)
                    Text(
                        "Total Growth: $growthStr (${String.format(locale, "%.1f", pct)}%)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (growth >= 0) Emerald500 else SemanticRed
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let { onConfirm(it, date, notes) } }, enabled = parsed != null) {
                Text("Log Valuation")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ValuationHistoryDialog(
    investment: Investment,
    currencyCode: String = "INR",
    valuations: List<app.fynlo.data.model.InvestmentValuation>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valuation History") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(investment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                
                if (valuations.isEmpty()) {
                    Text("No records found", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(valuations) { v ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(DateUtils.formatToDisplay(v.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    if (v.notes.isNotBlank()) {
                                        Text(v.notes, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                Text(CurrencyFormatter.detail(v.value, currencyCode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
