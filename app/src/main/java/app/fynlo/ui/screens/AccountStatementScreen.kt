package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLocale
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.TransactionOrdering
import app.fynlo.logic.matchesAccount
import java.util.Locale
import app.fynlo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountStatementScreen(
    accountName: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val accounts     by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    val account      = accounts.find { it.name == accountName }
    val locale       = LocalLocale.current.platformLocale

    // C03b Stage #1b-2 (3.2.88) — match by immutable account id first
    // (rename-safe), fall back to stored name for legacy orphan rows.
    val accountTransactions = transactions
        .filter { it.matchesAccount(accountId = account?.id ?: "", accountName = accountName) }
        .let(TransactionOrdering::newestFirst)
    val balanceImpactsByTransaction = remember(transactions, accounts) {
        buildBalanceImpactsByTransaction(transactions, accounts)
    }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog && account != null) {
        QuickBalanceEditDialog(
            accountName    = accountName,
            currentBalance = account.balance,
            currencySymbol = currencySymbol,
            onDismiss      = { showEditDialog = false },
            onConfirm      = { newBalance ->
                viewModel.quickEditBalance(accountName, newBalance, account.balance, account.id)
                showEditDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            LedgerDetailTopBar(
                title = accountName,
                subtitle = "Account statement",
                onNavigateBack = onNavigateBack,
            ) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Balance")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Balance — flat hero
            if (account != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)) {
                    Text("Current Balance", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CurrencyFormatter.hero(account.balance, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (account.balance >= 0) Emerald500 else SemanticRed
                    )
                    Text(app.fynlo.logic.pluralize(accountTransactions.size, "transaction"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (accountTransactions.isEmpty()) {
                Text("No transactions found for this account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accountTransactions, key = { it.id }) { txn ->
                        val focusedImpacts = balanceImpactsByTransaction[txn.id]
                            .orEmpty()
                            .filter { it.accountName == accountName }
                            .ifEmpty { balanceImpactsByTransaction[txn.id].orEmpty() }
                        TransactionItem(
                            txn            = txn,
                            currencyCode   = currencyCode,
                            onEdit         = {
                                viewModel.editTransaction(txn, it)
                                viewModel.showFeedback("Transaction updated")
                            },
                            onDelete       = {
                                viewModel.deleteTransaction(txn)
                                viewModel.showFeedback("Transaction deleted")
                            },
                            // 3.2.81 — propagate account names so the edit
                            // dialog's new Account picker shows real options.
                            bankAccounts   = accounts.map { it.name },
                            // C03b Stage #1b-2 (3.2.88) — id → current name
                            // for rename-reflective sub-label.
                            accountIdToName = accounts.associate { it.id to it.name },
                            balanceImpacts = focusedImpacts,
                            showTimestamp = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBalanceEditDialog(
    accountName: String,
    currentBalance: Double,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var input by remember { mutableStateOf(currentBalance.toBigDecimal().stripTrailingZeros().toPlainString()) }
    val newBalance = input.toDoubleOrNull()

    // C22 dialog universalization (3.2.53) — migrated from AlertDialog to
    // the canonical FormDialog pattern.
    app.fynlo.ui.components.FormDialog(
        title = "Edit Balance",
        onDismiss = onDismiss,
    ) {
        Text(
            "Set the correct balance for $accountName.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("New balance ($currencySymbol)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = input,
            onValueChange = { input = it },
            placeholder   = { Text("0") },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )
        if (newBalance != null) {
            Spacer(Modifier.height(8.dp))
            val diff = newBalance - currentBalance
            Text(
                if (diff >= 0) "+ $currencySymbol${"%.2f".format(diff)} will be added"
                else "− $currencySymbol${"%.2f".format(-diff)} will be deducted",
                style = MaterialTheme.typography.bodySmall,
                color = if (diff >= 0) Emerald500 else SemanticRed,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { newBalance?.let { onConfirm(it) } },
            enabled = newBalance != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Update Balance", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(
            if (newBalance == null) "Enter a valid amount to continue" else null
        )
    }
}
