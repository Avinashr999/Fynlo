package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
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
import app.fynlo.logic.MoneyTrail
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
    val accountTrail = remember(account, transactions) {
        account?.let { MoneyTrail.account(it, transactions) }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog && account != null) {
        QuickBalanceEditDialog(
            accountName    = accountName,
            currentBalance = account.balance,
            currencySymbol = currencySymbol,
            currencyCode   = currencyCode,
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
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            // Balance — flat hero
            if (account != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.7.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = Emerald500.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = Emerald500,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Current balance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    app.fynlo.logic.pluralize(accountTransactions.size, "transaction"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            CurrencyFormatter.exact(account.balance, currencyCode, locale),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (account.balance >= 0) Emerald500 else SemanticRed,
                        )
                        accountTrail?.let { trail ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AccountTrailMetric(
                                    label = "Opening",
                                    value = CurrencyFormatter.detail(trail.openingBalance, currencyCode, locale),
                                    modifier = Modifier.weight(1f),
                                )
                                AccountTrailMetric(
                                    label = "In",
                                    value = CurrencyFormatter.detail(trail.moneyIn, currencyCode, locale),
                                    modifier = Modifier.weight(1f),
                                    valueColor = Emerald500,
                                )
                                AccountTrailMetric(
                                    label = "Out",
                                    value = CurrencyFormatter.detail(trail.moneyOut, currencyCode, locale),
                                    modifier = Modifier.weight(1f),
                                    valueColor = SemanticRed,
                                )
                            }
                        }
                    }
                }
            }

            if (accountTransactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No transactions for this account yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Money movement",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = FabBottomPadding),
                ) {
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
private fun AccountTrailMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickBalanceEditDialog(
    accountName: String,
    currentBalance: Double,
    currencySymbol: String,
    currencyCode: String,
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
                if (diff >= 0) "+ ${CurrencyFormatter.exact(diff, currencyCode)} will be added"
                else "${CurrencyFormatter.negativeExact(diff, currencyCode)} will be deducted",
                style = MaterialTheme.typography.bodySmall,
                color = if (diff >= 0) Emerald500 else SemanticRed,
            )
        }

        Spacer(Modifier.height(20.dp))
        TemplatePrimaryButton(
            text = "Update Balance",
            onClick = { newBalance?.let { onConfirm(it) } },
            enabled = newBalance != null,
            modifier = Modifier.fillMaxWidth(),
        )
        app.fynlo.ui.components.DisabledButtonHint(
            if (newBalance == null) "Enter a valid amount to continue" else null
        )
    }
}
