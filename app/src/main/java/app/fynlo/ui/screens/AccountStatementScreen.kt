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
import app.fynlo.FinanceViewModel
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
    val accounts     by viewModel.allAccountsUnfiltered.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val account      = accounts.find { it.name == accountName }
    val locale       = remember { Locale.getDefault() }

    val accountTransactions = transactions
        .filter { it.fromAcct == accountName || it.toAcct == accountName }
        .sortedByDescending { it.date }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog && account != null) {
        QuickBalanceEditDialog(
            accountName    = accountName,
            currentBalance = account.balance,
            currencySymbol = currencySymbol,
            onDismiss      = { showEditDialog = false },
            onConfirm      = { newBalance ->
                viewModel.quickEditBalance(accountName, newBalance, account.balance)
                showEditDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$accountName") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Balance")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Balance card
            if (account != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = if (account.balance >= 0)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color(0xFFFFEBEE).copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Balance", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$currencySymbol ${String.format(locale, "%,.2f", account.balance)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (account.balance >= 0) MaterialTheme.colorScheme.primary else SemanticRed
                        )
                        Text("${accountTransactions.size} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (accountTransactions.isEmpty()) {
                Text("No transactions found for this account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accountTransactions) { txn ->
                        TransactionItem(
                            txn            = txn,
                            currencySymbol = currencySymbol,
                            onDelete       = { viewModel.deleteTransaction(txn) },
                            onEdit         = { viewModel.editTransaction(txn, it) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set the correct balance for $accountName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it },
                    label         = { Text("New Balance ($currencySymbol)") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp)
                )
                if (newBalance != null) {
                    val diff = newBalance - currentBalance
                    Text(
                        if (diff >= 0) "+ $currencySymbol${"%.2f".format(diff)} will be added"
                        else "- $currencySymbol${"%.2f".format(-diff)} will be deducted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff >= 0) Emerald500 else SemanticRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { newBalance?.let { onConfirm(it) } },
                enabled  = newBalance != null
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
